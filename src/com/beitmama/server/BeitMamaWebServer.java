package com.beitmama.server;

import com.beitmama.model.*;
import com.beitmama.service.BeitMamaService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public class BeitMamaWebServer {
    private static final Logger LOGGER = Logger.getLogger(BeitMamaWebServer.class.getName());
    private final int port;
    private final BeitMamaService service;
    private final Map<String, User> sessions = new ConcurrentHashMap<>();
    private final Map<String, Long> oauthStates = new ConcurrentHashMap<>();
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private HttpServer server;

    public BeitMamaWebServer(int port, BeitMamaService service) {
        this.port = port;
        this.service = service;
    }

    public void start() throws IOException {
        Set<String> corsOrigins = corsOrigins();
        boolean crossSiteCookies = Boolean.parseBoolean(System.getenv("BEITMAMA_CROSS_SITE_COOKIES"));
        if (crossSiteCookies && corsOrigins.isEmpty()) {
            throw new IllegalStateException(
                    "BEITMAMA_CORS_ORIGIN is required when BEITMAMA_CROSS_SITE_COOKIES is enabled.");
        }
        for (String corsOrigin : corsOrigins) {
            URI origin = URI.create(corsOrigin);
            if (origin.getScheme() == null || origin.getHost() == null
                    || origin.getRawPath() != null && !origin.getRawPath().isEmpty()
                    || origin.getRawQuery() != null || origin.getRawFragment() != null) {
                throw new IllegalStateException("BEITMAMA_CORS_ORIGIN must contain only an origin URL.");
            }
        }
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/api/", new ApiHandler());
        server.createContext("/", new StaticFileHandler());
        server.setExecutor(null);
        server.start();
        System.out.println("Beit Mama Server running at http://localhost:" + port + "/");
    }

    public void stop() {
        if (server != null) server.stop(0);
    }

    private String publicBaseUrl() {
        String configuredUrl = System.getenv("BEITMAMA_PUBLIC_URL");
        if (configuredUrl != null && !configuredUrl.isBlank()) {
            return configuredUrl.replaceAll("/+$", "");
        }
        String renderUrl = System.getenv("RENDER_EXTERNAL_URL");
        return renderUrl == null || renderUrl.isBlank()
                ? "http://localhost:" + port : renderUrl.replaceAll("/+$", "");
    }

    private String frontendBaseUrl() {
        String configuredUrl = System.getenv("BEITMAMA_FRONTEND_URL");
        return configuredUrl == null || configuredUrl.isBlank()
                ? publicBaseUrl() : configuredUrl.replaceAll("/+$", "");
    }

    private Set<String> corsOrigins() {
        String configured = System.getenv("BEITMAMA_CORS_ORIGIN");
        if (configured == null || configured.isBlank()) return Set.of();
        return Arrays.stream(configured.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private String sessionCookie(String token, boolean expire) {
        boolean crossSite = Boolean.parseBoolean(System.getenv("BEITMAMA_CROSS_SITE_COOKIES"));
        String cookie = "BEITMAMA_SESSION=" + token + "; Path=/; HttpOnly; SameSite="
                + (crossSite ? "None" : "Strict");
        if (crossSite || Boolean.parseBoolean(System.getenv("BEITMAMA_SECURE_COOKIES"))) {
            cookie += "; Secure";
        }
        if (expire) {
            cookie += "; Max-Age=0";
        }
        return cookie;
    }

    private record ApiResponse(boolean success, String message, Object data) {}

    private class ApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            try {
                if (!handleCors(exchange)) return;
                switch (path) {
                    case "/api/login" -> login(exchange);
                    case "/api/auth/google", "/api/auth/google/start" -> googleStart(exchange);
                    case "/api/auth/google/callback" -> googleCallback(exchange);
                    case "/api/logout" -> logout(exchange);
                    case "/api/me" -> getCurrentUser(exchange);
                    case "/api/profile" -> updateCustomerProfile(exchange);
                    case "/api/profile-image" -> uploadCustomerProfileImage(exchange);
                    case "/api/register/customer" -> registerCustomer(exchange);
                    case "/api/register/mother" -> registerMother(exchange);
                    case "/api/categories" -> categories(exchange);
                    case "/api/mothers/locations" -> motherLocations(exchange);
                    case "/api/mothers/search" -> motherSearch(exchange);
                    case "/api/mothers/profile" -> ownMotherProfile(exchange);
                    case "/api/mothers/like" -> motherLike(exchange);
                    case "/api/mothers/reviews" -> motherReview(exchange);
                    case "/api/follows" -> follows(exchange);
                    case "/api/mothers/videos" -> motherVideos(exchange);
                    case "/api/mothers/profile-image" -> uploadMotherProfileImage(exchange);
                    case "/api/products/image" -> uploadProductImage(exchange);
                    case "/api/products" -> products(exchange);
                    case "/api/orders" -> orders(exchange);
                    case "/api/orders/status" -> updateOrderStatus(exchange);
                    case "/api/reviews" -> reviews(exchange);
                    case "/api/account/password" -> changePassword(exchange);
                    case "/api/admin/stats" -> adminStats(exchange);
                    case "/api/admin/invitations" -> adminInvitations(exchange);
                    case "/api/admin/mothers" -> adminMothers(exchange);
                    case "/api/admin/mothers/status" -> updateMotherStatus(exchange);
                    case "/api/admin/customers" -> adminCustomers(exchange);
                    case "/api/admin/users/search" -> adminUserSearch(exchange);
                    default -> {
                        if (path.matches("/api/mothers/[0-9]+")) motherProfile(exchange, path);
                        else sendJson(exchange, 404, new ApiResponse(false, "الواجهة غير موجودة", null));
                    }
                }
            } catch (IllegalArgumentException e) {
                sendJson(exchange, 400, new ApiResponse(false, e.getMessage(), null));
            } catch (RequestHandledException ignored) {
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "API request failed: " + path, e);
                sendJson(exchange, 500, new ApiResponse(false, "حدث خطأ داخلي أثناء تنفيذ الطلب", null));
            } finally {
                exchange.close();
            }
        }

        private boolean handleCors(HttpExchange exchange) throws IOException {
            Set<String> allowedOrigins = corsOrigins();
            String requestOrigin = exchange.getRequestHeaders().getFirst("Origin");
            if (allowedOrigins.isEmpty()) {
                if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                    exchange.sendResponseHeaders(403, -1);
                    return false;
                }
                return true;
            }
            if (requestOrigin != null && !allowedOrigins.contains(requestOrigin)) {
                exchange.sendResponseHeaders(403, -1);
                return false;
            }
            if (requestOrigin != null) {
                exchange.getResponseHeaders().set("Access-Control-Allow-Origin", requestOrigin);
                exchange.getResponseHeaders().set("Access-Control-Allow-Credentials", "true");
                exchange.getResponseHeaders().set("Access-Control-Allow-Methods",
                        "GET, POST, PUT, DELETE, OPTIONS");
                exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
                exchange.getResponseHeaders().set("Vary", "Origin");
            }
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(requestOrigin != null && allowedOrigins.contains(requestOrigin)
                        ? 204 : 403, -1);
                return false;
            }
            return true;
        }

        private void redirectToFrontend(HttpExchange exchange, String page) throws IOException {
            exchange.getResponseHeaders().set("Location", frontendBaseUrl() + "/" + page);
            exchange.sendResponseHeaders(302, -1);
        }

        private void login(HttpExchange exchange) throws IOException {
            requireMethod(exchange, "POST");
            Map<String, String> body = formBody(exchange);
            User user = service.login(body.get("email"), body.get("password"));
            if (user == null) {
                sendJson(exchange, 401, new ApiResponse(false, "البريد أو كلمة المرور غير صحيحة", null));
                return;
            }
            String token = UUID.randomUUID() + UUID.randomUUID().toString().replace("-", "");
            sessions.put(token, user);
            exchange.getResponseHeaders().add("Set-Cookie", sessionCookie(token, false));

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("user", user);
            sendJson(exchange, 200, response);
        }

        private void logout(HttpExchange exchange) throws IOException {
            requireMethod(exchange, "POST");
            String token = sessionToken(exchange);
            if (token != null) sessions.remove(token);
            exchange.getResponseHeaders().add("Set-Cookie", sessionCookie("", true));
            sendJson(exchange, 200, new ApiResponse(true, null, null));
        }

        private static final String GOOGLE_CLIENT_ID = Objects.toString(System.getenv("GOOGLE_CLIENT_ID"), "");
        private static final String GOOGLE_CLIENT_SECRET = Objects.toString(System.getenv("GOOGLE_CLIENT_SECRET"), "");

        private void googleStart(HttpExchange exchange) throws IOException {
            requireMethod(exchange, "GET");
            if (GOOGLE_CLIENT_ID.isBlank()) {
                redirectToFrontend(exchange, "login.html");
                return;
            }
            String redirectUri = publicBaseUrl() + "/api/auth/google/callback";
            String state = UUID.randomUUID().toString().replace("-", "");
            oauthStates.put(state, System.currentTimeMillis());
            String url = "https://accounts.google.com/o/oauth2/v2/auth?response_type=code"
                    + "&client_id=" + URLEncoder.encode(GOOGLE_CLIENT_ID, StandardCharsets.UTF_8)
                    + "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8)
                    + "&scope=" + URLEncoder.encode("openid email profile", StandardCharsets.UTF_8)
                    + "&state=" + state
                    + "&prompt=select_account";
            exchange.getResponseHeaders().set("Location", url);
            exchange.sendResponseHeaders(302, -1);
        }

        private void googleCallback(HttpExchange exchange) throws IOException {
            requireMethod(exchange, "GET");
            Map<String, String> params = queryParams(exchange);
            String code = params.get("code");
            String state = params.get("state");
            String error = params.get("error");
            if (error != null || code == null || state == null || oauthStates.remove(state) == null) {
                redirectToFrontend(exchange, "login.html");
                return;
            }
            if (GOOGLE_CLIENT_SECRET.isBlank()) {
                redirectToFrontend(exchange, "login.html");
                return;
            }
            try {
                String tokenBody = "code=" + URLEncoder.encode(code, StandardCharsets.UTF_8)
                        + "&client_id=" + URLEncoder.encode(GOOGLE_CLIENT_ID, StandardCharsets.UTF_8)
                        + "&client_secret=" + URLEncoder.encode(GOOGLE_CLIENT_SECRET, StandardCharsets.UTF_8)
                        + "&redirect_uri=" + URLEncoder.encode(publicBaseUrl() + "/api/auth/google/callback", StandardCharsets.UTF_8)
                        + "&grant_type=authorization_code";
                String tokenResponse = httpPost("https://oauth2.googleapis.com/token", tokenBody);
                String accessToken = extractJsonString(tokenResponse, "access_token");
                if (accessToken == null) throw new IllegalStateException("لم يتم استلام توكن من جوجل");

                String userInfo = httpGet("https://www.googleapis.com/oauth2/v2/userinfo", accessToken);
                String email = extractJsonString(userInfo, "email");
                String name = extractJsonString(userInfo, "name");
                if (email == null) throw new IllegalStateException("لم يتم استلام البريد من جوجل");
                User user = service.loginOrRegisterWithGoogle(email, name, null);
                String token = UUID.randomUUID() + UUID.randomUUID().toString().replace("-", "");
                sessions.put(token, user);
                exchange.getResponseHeaders().add("Set-Cookie", sessionCookie(token, false));
                redirectToFrontend(exchange, "profile.html");
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Google login failed", e);
                redirectToFrontend(exchange, "login.html");
            }
        }

        private void getCurrentUser(HttpExchange exchange) throws IOException {
            requireMethod(exchange, "GET");
            User user = authenticatedUser(exchange);
            if (user == null) {
                sendJson(exchange, 401, new ApiResponse(false, "سجّل الدخول أولاً", null));
                return;
            }
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("user", user);
            sendJson(exchange, 200, response);
        }

        private void updateCustomerProfile(HttpExchange exchange) throws IOException {
            requireMethod(exchange, "POST");
            User user = authenticatedUser(exchange);
            if (user == null) {
                sendJson(exchange, 401, new ApiResponse(false, "سجّل الدخول أولاً", null));
                return;
            }
            if (!"CUSTOMER".equals(user.getRole())) {
                sendJson(exchange, 403, new ApiResponse(false, "تعديل الملف الشخصي متاح لحساب العميل فقط", null));
                return;
            }
            Map<String, String> body = formBody(exchange);
            User updated = service.updateCustomerProfile(user.getId(), body.get("name"), body.get("phone"),
                    body.get("alternate_phone"), body.get("address"), body.get("preferred_contact"));
            if (updated == null) {
                sendJson(exchange, 404, new ApiResponse(false, "الحساب غير موجود", null));
                return;
            }
            String token = sessionToken(exchange);
            if (token != null) sessions.put(token, updated);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("user", updated);
            sendJson(exchange, 200, response);
        }

        private void registerCustomer(HttpExchange exchange) throws Exception {
            requireMethod(exchange, "POST");
            Map<String, String> body = formBody(exchange);
            User user = service.registerCustomer(body.get("name"), body.get("email"),
                    body.get("phone"), body.get("password"));
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("userId", user.getId());
            sendJson(exchange, 201, response);
        }

        private void registerMother(HttpExchange exchange) throws Exception {
            requireMethod(exchange, "POST");
            Map<String, String> body = formBody(exchange);
            service.registerMotherWithInvitation(body.get("token"), body.get("name"), body.get("email"),
                    body.get("phone"), body.get("password"), body.get("region"), body.get("bio"),
                    body.get("pickup_address"), body.get("pickup_instructions"), body.get("location_url"));
            sendJson(exchange, 201, new ApiResponse(true, "تم تسجيل الحساب وهو قيد مراجعة واعتماد الإدارة.", null));
        }

        private void categories(HttpExchange exchange) throws IOException {
            requireMethod(exchange, "GET");
            sendJson(exchange, 200, service.getProductCategories());
        }

        private void motherLocations(HttpExchange exchange) throws IOException {
            requireMethod(exchange, "GET");
            sendJson(exchange, 200, service.getApprovedMotherLocations());
        }

        private void motherSearch(HttpExchange exchange) throws IOException {
            requireMethod(exchange, "GET");
            String query = queryParams(exchange).getOrDefault("q", "").trim();
            if (query.length() < 2) {
                sendJson(exchange, 200, List.of());
                return;
            }
            sendJson(exchange, 200, service.searchApprovedMothers(query));
        }

        private void ownMotherProfile(HttpExchange exchange) throws IOException {
            requireMethod(exchange, "GET");
            User user = authenticatedUser(exchange);
            if (user == null || !"MOTHER".equals(user.getRole())) {
                sendJson(exchange, 403, new ApiResponse(false, "الخدمة للأمهات فقط", null));
                return;
            }
            sendJson(exchange, 200, service.getSellerProfile(user.getId()));
        }

        private void motherProfile(HttpExchange exchange, String path) throws IOException {
            requireMethod(exchange, "GET");
            int sellerId = parseInt(path.substring("/api/mothers/".length()), "معرف الأم");
            User viewer = authenticatedUser(exchange);
            MotherProfile profile = service.getMotherProfile(sellerId,
                    viewer != null && "CUSTOMER".equals(viewer.getRole()) ? viewer.getId() : null);
            if (profile == null) {
                sendJson(exchange, 404, new ApiResponse(false, "الأم غير موجودة أو لم يتم اعتماد حسابها", null));
                return;
            }
            sendJson(exchange, 200, profile);
        }

        private void motherLike(HttpExchange exchange) throws IOException {
            requireMethod(exchange, "POST");
            User user = authenticatedUser(exchange);
            if (user == null || !"CUSTOMER".equals(user.getRole())) {
                sendJson(exchange, 401, new ApiResponse(false, "سجّل الدخول بحساب عميل لإضافة إعجاب", null));
                return;
            }
            int sellerId = parseInt(formBody(exchange).get("seller_id"), "معرف الأم");
            boolean liked = service.toggleMotherLike(user.getId(), sellerId);
            MotherProfile profile = service.getMotherProfile(sellerId, user.getId());
            sendJson(exchange, 200, new ApiResponse(true, null, Map.of(
                    "liked", liked,
                    "rating", profile.getRating(),
                    "ratingCount", profile.getRatingCount(),
                    "heartCount", profile.getHeartCount())));
        }

        private void motherReview(HttpExchange exchange) throws IOException {
            requireMethod(exchange, "POST");
            User user = authenticatedUser(exchange);
            if (user == null || !"CUSTOMER".equals(user.getRole())) {
                sendJson(exchange, 401, new ApiResponse(false, "سجّل الدخول بحساب عميل لإضافة تعليق", null));
                return;
            }
            Map<String, String> body = formBody(exchange);
            int sellerId = parseInt(body.get("seller_id"), "معرف الأم");
            int rating = parseInt(body.get("rating"), "التقييم");
            boolean added = service.addMotherReview(user.getId(), sellerId, rating, body.get("comment"));
            sendJson(exchange, added ? 201 : 400,
                    new ApiResponse(added, added ? null : "تعذر إضافة التعليق", null));
        }

        private void follows(HttpExchange exchange) throws IOException {
            User user = authenticatedUser(exchange);
            if (user == null || !"CUSTOMER".equals(user.getRole())) {
                sendJson(exchange, 401, new ApiResponse(false, "سجّل الدخول بحساب عميل للمتابعة", null));
                return;
            }
            if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 200, service.getFollowedMothers(user.getId()));
                return;
            }
            requireMethod(exchange, "POST");
            int sellerId = parseInt(formBody(exchange).get("seller_id"), "معرف الأم");
            boolean success = service.toggleFollow(user.getId(), sellerId);
            sendJson(exchange, 200, new ApiResponse(success, null, Map.of("followed", success)));
        }

        private void motherVideos(HttpExchange exchange) throws IOException {
            if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                String sellerIdStr = queryParams(exchange).get("seller_id");
                if (sellerIdStr == null) {
                    sendJson(exchange, 400, new ApiResponse(false, "معرف الأم مطلوب", null));
                    return;
                }
                int sellerId = parseInt(sellerIdStr, "معرف الأم");
                sendJson(exchange, 200, service.getMotherVideos(sellerId));
                return;
            }
            requireMethod(exchange, "POST");
            sendJson(exchange, 503, new ApiResponse(false,
                    "رفع الفيديو غير متاح على الاستضافة المجانية لأن مساحة الملفات مؤقتة.", null));
        }

        private void uploadProductImage(HttpExchange exchange) throws IOException {
            uploadImage(exchange);
        }

        private void uploadMotherProfileImage(HttpExchange exchange) throws IOException {
            uploadImage(exchange);
        }

        private void uploadCustomerProfileImage(HttpExchange exchange) throws IOException {
            uploadImage(exchange);
        }

        private void uploadImage(HttpExchange exchange) throws IOException {
            sendJson(exchange, 503,
                    new ApiResponse(false, "رفع الصور غير متاح في النسخة التي لا تستخدم Firebase Storage.", null));
        }

        private byte[] readBounded(InputStream input, int maxBytes) throws IOException {
            try (input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int total = 0;
                int read;
                while ((read = input.read(buffer)) != -1) {
                    total += read;
                    if (total > maxBytes) throw new IllegalArgumentException("حجم الطلب أكبر من المسموح");
                    output.write(buffer, 0, read);
                }
                return output.toByteArray();
            }
        }

        private void products(HttpExchange exchange) throws IOException {
            User user = authenticatedUser(exchange);
            switch (exchange.getRequestMethod().toUpperCase()) {
                case "GET" -> sendJson(exchange, 200, user != null && "MOTHER".equals(user.getRole())
                        ? service.getMotherProducts(user.getId()) : service.getAvailableProducts());
                case "POST", "PUT" -> {
                    if (user == null || !"MOTHER".equals(user.getRole())) {
                        sendJson(exchange, 403, new ApiResponse(false, "الخدمة للأمهات المسجلات فقط", null));
                        return;
                    }
                    Map<String, String> body = formBody(exchange);
                    Product product = productFromForm(body, user.getId());
                    boolean success;
                    if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                        success = service.addProduct(product);
                    } else {
                        product.setId(parseInt(body.get("id"), "معرف الوجبة"));
                        success = service.updateProduct(product);
                    }
                    sendJson(exchange, success ? 200 : 400, new ApiResponse(success, null, null));
                }
                case "DELETE" -> {
                    if (user == null || !"MOTHER".equals(user.getRole())) {
                        sendJson(exchange, 403, new ApiResponse(false, "الخدمة للأمهات المسجلات فقط", null));
                        return;
                    }
                    int id = parseInt(formBody(exchange).get("id"), "معرف الوجبة");
                    boolean success = service.deleteProduct(id, user.getId());
                    sendJson(exchange, success ? 200 : 404, new ApiResponse(success, null, null));
                }
                default -> sendJson(exchange, 405, new ApiResponse(false, "الطريقة غير مسموحة", null));
            }
        }

        private Product productFromForm(Map<String, String> body, int sellerId) {
            Product product = new Product();
            product.setSellerId(sellerId);
            product.setCategoryId(parseInt(body.getOrDefault("category_id", "1"), "التصنيف"));
            product.setName(body.get("name"));
            product.setDescription(body.get("description"));
            BigDecimal basePrice;
            try {
                basePrice = new BigDecimal(body.get("price"));
            } catch (RuntimeException e) {
                throw new IllegalArgumentException("السعر غير صحيح");
            }
            product.setPrice(basePrice);
            product.setCustomerPrice(calculateCustomerPrice(basePrice));
            product.setQuantityAvailable(parseInt(body.get("quantity"), "الكمية"));
            product.setAvailable(Boolean.parseBoolean(body.getOrDefault("available", "true")));
            product.setImageUrl(body.get("image_url"));
            product.setAvailableUntil(body.get("available_until"));
            product.setIngredients(body.get("ingredients"));
            product.setPreparationMinutes(parseInt(body.getOrDefault("preparation_minutes", "0"), "وقت التحضير"));
            product.setFreshlyCooked(Boolean.parseBoolean(body.getOrDefault("freshly_cooked", "false")));
            return product;
        }

        private BigDecimal calculateCustomerPrice(BigDecimal basePrice) {
            BigDecimal markupRate = new BigDecimal("1.25");
            return basePrice.multiply(markupRate).setScale(2, RoundingMode.HALF_UP);
        }

        private void orders(HttpExchange exchange) throws Exception {
            User user = authenticatedUser(exchange);
            if (user == null) {
                sendJson(exchange, 401, new ApiResponse(false, "سجّل الدخول لمتابعة الطلبات", null));
                return;
            }
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                if (!"CUSTOMER".equals(user.getRole())) {
                    sendJson(exchange, 403, new ApiResponse(false, "الطلب متاح بحساب العميل فقط", null));
                    return;
                }
                if (user.getAddress() == null || user.getAddress().isBlank()
                        || user.getAlternatePhone() == null || user.getAlternatePhone().isBlank()) {
                    sendJson(exchange, 409, new ApiResponse(false, "أكمل العنوان بالتفصيل ورقم الهاتف الإضافي قبل إتمام الطلب", null));
                    return;
                }
                Map<String, String> body = formBody(exchange);
                int sellerId = parseInt(body.get("seller_id"), "معرف الأم");
                List<OrderItem> items = parseItems(body);
                Order order = service.placeOrder(user.getId(), sellerId, body.get("pickup_time"),
                        body.get("proposed_delivery_time"), body.getOrDefault("delivery_method", "pickup"), user.getAddress(),
                        user.getAlternatePhone(), user.getPreferredContact(), body.get("payment_method"), items);
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("orderId", order.getId());
                response.put("total", order.getTotalAmount());
                sendJson(exchange, 201, response);
                return;
            }
            requireMethod(exchange, "GET");
            List<Order> orders = switch (user.getRole()) {
                case "CUSTOMER" -> service.getCustomerOrders(user.getId());
                case "MOTHER" -> service.getMotherOrders(user.getId());
                case "ADMIN" -> service.getAllOrders();
                default -> List.of();
            };
            sendJson(exchange, 200, orders);
        }

        private List<OrderItem> parseItems(Map<String, String> body) {
            List<OrderItem> items = new ArrayList<>();
            String encodedItems = body.get("items");
            if (encodedItems == null) {
                OrderItem item = new OrderItem();
                item.setProductId(parseInt(body.get("product_id"), "معرف الوجبة"));
                item.setQuantity(parseInt(body.get("quantity"), "الكمية"));
                items.add(item);
                return items;
            }
            for (String pair : encodedItems.split(",")) {
                String[] values = pair.split(":", 2);
                if (values.length != 2) throw new IllegalArgumentException("بيانات السلة غير صحيحة");
                OrderItem item = new OrderItem();
                item.setProductId(parseInt(values[0], "معرف الوجبة"));
                item.setQuantity(parseInt(values[1], "الكمية"));
                items.add(item);
            }
            return items;
        }

        private void updateOrderStatus(HttpExchange exchange) throws IOException {
            requireMethod(exchange, "POST");
            User user = authenticatedUser(exchange);
            if (user == null || !Set.of("MOTHER", "ADMIN").contains(user.getRole())) {
                sendJson(exchange, 403, new ApiResponse(false, "غير مصرح بتحديث الطلب", null));
                return;
            }
            Map<String, String> body = formBody(exchange);
            int orderId = parseInt(body.get("order_id"), "معرف الطلب");
            String status = body.get("status");
            if (!Set.of("confirmed", "preparing", "ready", "completed", "rejected", "cancelled").contains(status)) {
                throw new IllegalArgumentException("حالة الطلب غير صالحة");
            }
            boolean updated = service.updateOrderStatus(orderId, status, user.getId(), user.getRole());
            sendJson(exchange, updated ? 200 : 409, new ApiResponse(updated, updated ? null : "تعذر تحديث الطلب أو أن حالته لا تسمح بهذا الإجراء", null));
        }

        private void reviews(HttpExchange exchange) throws IOException {
            if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                Map<String, String> params = queryParams(exchange);
                int sellerId = parseInt(params.get("sellerId"), "معرف الأم");
                sendJson(exchange, 200, service.getSellerReviews(sellerId));
                return;
            }
            requireMethod(exchange, "POST");
            User user = authenticatedUser(exchange);
            if (user == null || !"CUSTOMER".equals(user.getRole())) {
                sendJson(exchange, 403, new ApiResponse(false, "سجّل الدخول بحساب عميل لإضافة تقييم", null));
                return;
            }
            Map<String, String> body = formBody(exchange);
            boolean added = service.addReview(parseInt(body.get("order_id"), "معرف الطلب"), user.getId(),
                    parseInt(body.get("seller_id"), "معرف الأم"),
                    parseInt(body.get("rating"), "التقييم"), body.get("comment"));
            sendJson(exchange, added ? 201 : 400, new ApiResponse(added, added ? null : "التقييم متاح مرة واحدة بعد إتمام الطلب فقط", null));
        }

        private void changePassword(HttpExchange exchange) throws IOException {
            requireMethod(exchange, "POST");
            User user = authenticatedUser(exchange);
            if (user == null) {
                sendJson(exchange, 401, new ApiResponse(false, "سجّل الدخول أولاً", null));
                return;
            }
            Map<String, String> body = formBody(exchange);
            service.changePassword(user.getId(), body.get("current_password"), body.get("new_password"));
            sendJson(exchange, 200, new ApiResponse(true, null, null));
        }

        private void adminStats(HttpExchange exchange) throws IOException {
            requireMethod(exchange, "GET");
            User user = authenticatedUser(exchange);
            if (user == null || !"ADMIN".equals(user.getRole())) {
                sendJson(exchange, 403, new ApiResponse(false, "هذه الخدمة للإدارة فقط", null));
                return;
            }
            sendJson(exchange, 200, JsonParser.parseString(service.getAdminStatsJson()));
        }

        private User requireAdmin(HttpExchange exchange) throws IOException {
            User user = authenticatedUser(exchange);
            if (user == null || !"ADMIN".equals(user.getRole())) {
                sendJson(exchange, 403, new ApiResponse(false, "هذه الخدمة للإدارة فقط", null));
                return null;
            }
            return user;
        }

        private void adminInvitations(HttpExchange exchange) throws Exception {
            User admin = requireAdmin(exchange);
            if (admin == null) return;
            if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 200, service.getAllInvitations());
            } else if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                Map<String, String> body = formBody(exchange);
                int days = parseInt(body.getOrDefault("days", "7"), "مدة الدعوة");
                if (days < 1 || days > 90) throw new IllegalArgumentException("مدة الدعوة بين يوم و90 يوماً");
                String target = Objects.toString(body.get("target"), "").trim();
                if (target.isEmpty()) throw new IllegalArgumentException("أدخل البريد الإلكتروني أو رقم الهاتف");
                Invitation invitation = service.createInvitation(admin.getId(), target, days);
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("id", invitation.getId());
                response.put("token", invitation.getToken());
                response.put("expiresAt", invitation.getExpiresAt().toString());
                sendJson(exchange, 201, response);
            } else if ("DELETE".equalsIgnoreCase(exchange.getRequestMethod())) {
                int id = parseInt(formBody(exchange).get("id"), "معرف الدعوة");
                boolean revoked = service.revokeInvitation(id);
                sendJson(exchange, revoked ? 200 : 404, new ApiResponse(revoked, null, null));
            } else {
                sendJson(exchange, 405, new ApiResponse(false, "الطريقة غير مسموحة", null));
            }
        }

        private void adminMothers(HttpExchange exchange) throws IOException {
            if (requireAdmin(exchange) == null) return;
            requireMethod(exchange, "GET");
            List<User> mothers = service.getAllMothers();
            List<Map<String, Object>> result = new ArrayList<>();
            for (User mother : mothers) {
                SellerProfile profile = service.getSellerProfile(mother.getId());
                Map<String, Object> m = new HashMap<>();
                m.put("id", mother.getId());
                m.put("name", mother.getName());
                m.put("email", mother.getEmail());
                m.put("phone", mother.getPhone());
                m.put("status", mother.getStatus());
                m.put("approval", profile == null ? "PENDING" : profile.getApprovalStatus());
                result.add(m);
            }
            sendJson(exchange, 200, result);
        }

        private void updateMotherStatus(HttpExchange exchange) throws Exception {
            if (requireAdmin(exchange) == null) return;
            requireMethod(exchange, "POST");
            Map<String, String> body = formBody(exchange);
            service.setMotherApproval(parseInt(body.get("mother_id"), "معرف الأم"), body.get("status"));
            sendJson(exchange, 200, new ApiResponse(true, null, null));
        }

        private void adminCustomers(HttpExchange exchange) throws IOException {
            if (requireAdmin(exchange) == null) return;
            requireMethod(exchange, "GET");
            sendJson(exchange, 200, service.getAllCustomers());
        }

        private void adminUserSearch(HttpExchange exchange) throws IOException {
            if (requireAdmin(exchange) == null) return;
            requireMethod(exchange, "GET");
            String query = queryParams(exchange).getOrDefault("q", "").trim();
            if (query.isEmpty()) throw new IllegalArgumentException("أدخل رقم الهاتف أو المعرف للبحث");
            List<User> results = service.searchUsersByPhone(query);
            List<Map<String, Object>> resultList = new ArrayList<>();
            for (User u : results) {
                Map<String, Object> userMap = new HashMap<>();
                userMap.put("id", u.getId());
                userMap.put("name", u.getName());
                userMap.put("email", u.getEmail());
                userMap.put("phone", u.getPhone());
                userMap.put("role", u.getRole());
                userMap.put("status", u.getStatus());
                SellerProfile profile = service.getSellerProfile(u.getId());
                userMap.put("profile", profile);
                resultList.add(userMap);
            }
            sendJson(exchange, 200, resultList);
        }
    }

    private class StaticFileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                    exchange.sendResponseHeaders(405, -1);
                    return;
                }
                String path = exchange.getRequestURI().getPath();
                if ("/".equals(path) || path.isEmpty()) path = "/index.html";
                Path root = Path.of("web").toAbsolutePath().normalize();
                Path requested = root.resolve(path.substring(1)).normalize();
                if (!requested.startsWith(root) || !Files.isRegularFile(requested)) {
                    exchange.sendResponseHeaders(404, -1);
                    return;
                }
                byte[] bytes = Files.readAllBytes(requested);
                exchange.getResponseHeaders().set("Content-Type", contentType(requested));
                exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
                exchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream output = exchange.getResponseBody()) {
                    output.write(bytes);
                }
            } finally {
                exchange.close();
            }
        }

        private String contentType(Path file) {
            String name = file.getFileName().toString().toLowerCase();
            if (name.endsWith(".css")) return "text/css; charset=UTF-8";
            if (name.endsWith(".js")) return "application/javascript; charset=UTF-8";
            if (name.endsWith(".png")) return "image/png";
            if (name.endsWith(".jpg") || name.endsWith(".jpeg")) return "image/jpeg";
            if (name.endsWith(".webp")) return "image/webp";
            if (name.endsWith(".svg")) return "image/svg+xml";
            return "text/html; charset=UTF-8";
        }
    }

    private User authenticatedUser(HttpExchange exchange) {
        String token = sessionToken(exchange);
        return token == null ? null : sessions.get(token);
    }

    private String sessionToken(HttpExchange exchange) {
        List<String> cookies = exchange.getRequestHeaders().get("Cookie");
        if (cookies == null) return null;
        for (String header : cookies) {
            for (String cookie : header.split(";")) {
                String[] parts = cookie.trim().split("=", 2);
                if (parts.length == 2 && parts[0].equals("BEITMAMA_SESSION")) return parts[1];
            }
        }
        return null;
    }

    private Map<String, String> formBody(HttpExchange exchange) throws IOException {
        StringBuilder data = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) data.append(line);
        }
        Map<String, String> result = new HashMap<>();
        for (String pair : data.toString().split("&")) {
            if (pair.isEmpty()) continue;
            String[] values = pair.split("=", 2);
            result.put(URLDecoder.decode(values[0], StandardCharsets.UTF_8),
                    values.length == 2 ? URLDecoder.decode(values[1], StandardCharsets.UTF_8) : "");
        }
        return result;
    }

    private Map<String, String> queryParams(HttpExchange exchange) {
        Map<String, String> result = new HashMap<>();
        String query = exchange.getRequestURI().getRawQuery();
        if (query == null) return result;
        for (String pair : query.split("&")) {
            String[] values = pair.split("=", 2);
            result.put(URLDecoder.decode(values[0], StandardCharsets.UTF_8),
                    values.length == 2 ? URLDecoder.decode(values[1], StandardCharsets.UTF_8) : "");
        }
        return result;
    }

    private int parseInt(String value, String fieldName) {
        try {
            return Integer.parseInt(value);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException(fieldName + " غير صحيح");
        }
    }

    private void requireMethod(HttpExchange exchange, String expected) throws IOException {
        if (!expected.equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, new ApiResponse(false, "الطريقة غير مسموحة", null));
            throw new RequestHandledException();
        }
    }

    private void sendJson(HttpExchange exchange, int status, Object response) throws IOException {
        String json = gson.toJson(response);
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private void sendHtml(HttpExchange exchange, int status, String html) throws IOException {
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private static String httpPost(String targetUrl, String body) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(targetUrl).openConnection();
        try {
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(10_000);
            connection.setReadTimeout(10_000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
            try (OutputStream output = connection.getOutputStream()) {
                output.write(body.getBytes(StandardCharsets.UTF_8));
            }
            return readHttpResponse(connection);
        } finally {
            connection.disconnect();
        }
    }

    private static String httpGet(String targetUrl, String accessToken) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(targetUrl).openConnection();
        try {
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(10_000);
            connection.setReadTimeout(10_000);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Authorization", "Bearer " + accessToken);
            return readHttpResponse(connection);
        } finally {
            connection.disconnect();
        }
    }

    private static String readHttpResponse(HttpURLConnection connection) throws IOException {
        int status = connection.getResponseCode();
        if (status < 200 || status >= 300) {
            throw new IOException("OAuth HTTP request failed with status " + status);
        }
        StringBuilder response = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
        }
        return response.toString();
    }

    private static String extractJsonString(String json, String key) {
        JsonElement value = JsonParser.parseString(json).getAsJsonObject().get(key);
        return value == null || value.isJsonNull() || !value.isJsonPrimitive() ? null : value.getAsString();
    }

    private static final class RequestHandledException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }
}
