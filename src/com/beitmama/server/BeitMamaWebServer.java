package com.beitmama.server;

import com.beitmama.model.Order;
import com.beitmama.model.OrderItem;
import com.beitmama.model.Product;
import com.beitmama.model.Review;
import com.beitmama.model.Invitation;
import com.beitmama.model.SellerProfile;
import com.beitmama.model.User;
import com.beitmama.service.BeitMamaService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public class BeitMamaWebServer {
    private static final Logger LOGGER = Logger.getLogger(BeitMamaWebServer.class.getName());
    private final int port;
    private final BeitMamaService service;
    private final Map<String, User> sessions = new ConcurrentHashMap<>();
    private final Map<String, Long> oauthStates = new ConcurrentHashMap<>();
    private HttpServer server;

    public BeitMamaWebServer(int port, BeitMamaService service) {
        this.port = port;
        this.service = service;
    }

    public void start() throws IOException {
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

    private class ApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            try {
                switch (path) {
                    case "/api/login" -> login(exchange);
                    case "/api/auth/google/start" -> googleStart(exchange);
                    case "/api/auth/google/callback" -> googleCallback(exchange);
                    case "/api/logout" -> logout(exchange);
                    case "/api/me" -> getCurrentUser(exchange);
                    case "/api/profile" -> updateCustomerProfile(exchange);
                    case "/api/register/customer" -> registerCustomer(exchange);
                    case "/api/register/mother" -> registerMother(exchange);
                    case "/api/products" -> products(exchange);
                    case "/api/orders" -> orders(exchange);
                    case "/api/orders/status" -> updateOrderStatus(exchange);
                    case "/api/reviews" -> reviews(exchange);
                    case "/api/account/password" -> changePassword(exchange);
                    case "/api/admin/stats" -> adminStats(exchange);
                    case "/api/admin/invitations" -> adminInvitations(exchange);
                    case "/api/admin/mothers" -> adminMothers(exchange);
                    case "/api/admin/mothers/status" -> updateMotherStatus(exchange);
                    case "/api/admin/users/search" -> adminUserSearch(exchange);
                    default -> sendJson(exchange, 404, "{\"success\":false,\"message\":\"الواجهة غير موجودة\"}");
                }
            } catch (IllegalArgumentException e) {
                sendJson(exchange, 400, errorJson(e.getMessage()));
            } catch (RequestHandledException ignored) {
                // The appropriate HTTP response was already sent.
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "API request failed: " + path, e);
                sendJson(exchange, 500, "{\"success\":false,\"message\":\"حدث خطأ داخلي أثناء تنفيذ الطلب\"}");
            } finally {
                exchange.close();
            }
        }

        private void login(HttpExchange exchange) throws IOException {
            requireMethod(exchange, "POST");
            Map<String, String> body = formBody(exchange);
            User user = service.login(body.get("email"), body.get("password"));
            if (user == null) {
                sendJson(exchange, 401, "{\"success\":false,\"message\":\"البريد أو كلمة المرور غير صحيحة\"}");
                return;
            }
            String token = UUID.randomUUID() + UUID.randomUUID().toString().replace("-", "");
            sessions.put(token, user);
            exchange.getResponseHeaders().add("Set-Cookie",
                    "BEITMAMA_SESSION=" + token + "; Path=/; HttpOnly; SameSite=Strict");
            sendJson(exchange, 200, userJson(user));
        }

        private void logout(HttpExchange exchange) throws IOException {
            requireMethod(exchange, "POST");
            String token = sessionToken(exchange);
            if (token != null) sessions.remove(token);
            exchange.getResponseHeaders().add("Set-Cookie",
                    "BEITMAMA_SESSION=; Path=/; HttpOnly; SameSite=Strict; Max-Age=0");
            sendJson(exchange, 200, "{\"success\":true}");
        }

        // ===== تسجيل الدخول بحساب جوجل (OAuth 2.0) =====
        private static final String GOOGLE_CLIENT_ID =
                Objects.toString(System.getenv("GOOGLE_CLIENT_ID"), "");
        private static final String GOOGLE_CLIENT_SECRET =
                Objects.toString(System.getenv("GOOGLE_CLIENT_SECRET"), "");

        private void googleStart(HttpExchange exchange) throws IOException {
            requireMethod(exchange, "GET");
            if (GOOGLE_CLIENT_ID.isBlank()) {
                sendHtml(exchange, 500, "<script>alert('تسجيل جوجل غير مفعّل: حدد GOOGLE_CLIENT_ID و GOOGLE_CLIENT_SECRET في بيئة السيرفر');location='login.html'</script>");
                return;
            }
            String redirectUri = "http://localhost:" + port + "/api/auth/google/callback";
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
                sendHtml(exchange, 400, "<script>alert('فشل تسجيل الدخول بجوجل، حاول مرة أخرى');location='login.html'</script>");
                return;
            }
            if (GOOGLE_CLIENT_SECRET.isBlank()) {
                sendHtml(exchange, 500, "<script>alert('تسجيل جوجل غير مفعّل على السيرفر');location='login.html'</script>");
                return;
            }
            try {
                // تبديل الكود بـ access_token من جوجل
                String tokenBody = "code=" + URLEncoder.encode(code, StandardCharsets.UTF_8)
                        + "&client_id=" + URLEncoder.encode(GOOGLE_CLIENT_ID, StandardCharsets.UTF_8)
                        + "&client_secret=" + URLEncoder.encode(GOOGLE_CLIENT_SECRET, StandardCharsets.UTF_8)
                        + "&redirect_uri=" + URLEncoder.encode("http://localhost:" + port + "/api/auth/google/callback", StandardCharsets.UTF_8)
                        + "&grant_type=authorization_code";
                String tokenResponse = httpPost("https://oauth2.googleapis.com/token", tokenBody);
                String accessToken = extractJsonString(tokenResponse, "access_token");
                if (accessToken == null) throw new IllegalStateException("لم يتم استلام توكن من جوجل");
                // جلب بيانات الملف الشخصي
                String userInfo = httpGet("https://www.googleapis.com/oauth2/v2/userinfo", accessToken);
                String email = extractJsonString(userInfo, "email");
                String name = extractJsonString(userInfo, "name");
                if (email == null) throw new IllegalStateException("لم يتم استلام البريد من جوجل");
                User user = service.loginOrRegisterWithGoogle(email, name, null);
                String token = UUID.randomUUID() + UUID.randomUUID().toString().replace("-", "");
                sessions.put(token, user);
                exchange.getResponseHeaders().add("Set-Cookie",
                        "BEITMAMA_SESSION=" + token + "; Path=/; HttpOnly; SameSite=Strict");
                sendHtml(exchange, 200, "<script>location='index.html'</script>");
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Google login failed", e);
                sendHtml(exchange, 400, "<script>alert('فشل تسجيل الدخول بجوجل: "
                        + escapeJson(Objects.toString(e.getMessage(), "خطأ")) + "');location='login.html'</script>");
            }
        }

        private String httpPost(String url, String body) throws IOException {
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            try (OutputStream out = conn.getOutputStream()) {
                out.write(body.getBytes(StandardCharsets.UTF_8));
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                return sb.toString();
            }
        }

        private String httpGet(String url, String bearerToken) throws IOException {
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestProperty("Authorization", "Bearer " + bearerToken);
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                return sb.toString();
            }
        }

        /** استخراج قيمة نصية بسيطة من JSON بدون مكتبات خارجية */
        private String extractJsonString(String json, String key) {
            if (json == null) return null;
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("\"" + key + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")
                    .matcher(json);
            if (!m.find()) return null;
            return m.group(1).replace("\\\"", "\"").replace("\\\\", "\\");
        }

        private void sendHtml(HttpExchange exchange, int status, String html) throws IOException {
            byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        }

        private void getCurrentUser(HttpExchange exchange) throws IOException {
            requireMethod(exchange, "GET");
            User user = authenticatedUser(exchange);
            if (user == null) {
                sendJson(exchange, 401, "{\"success\":false,\"message\":\"سجّل الدخول أولاً\"}");
                return;
            }
            sendJson(exchange, 200, userJson(user));
        }

        private void updateCustomerProfile(HttpExchange exchange) throws IOException {
            requireMethod(exchange, "POST");
            User user = authenticatedUser(exchange);
            if (user == null) {
                sendJson(exchange, 401, "{\"success\":false,\"message\":\"سجّل الدخول أولاً\"}");
                return;
            }
            if (!"CUSTOMER".equals(user.getRole())) {
                sendJson(exchange, 403, "{\"success\":false,\"message\":\"تعديل الملف الشخصي متاح لحساب العميل فقط\"}");
                return;
            }
            Map<String, String> body = formBody(exchange);
            User updated = service.updateCustomerProfile(user.getId(), body.get("name"), body.get("phone"),
                    body.get("address"), body.get("preferred_contact"));
            if (updated == null) {
                sendJson(exchange, 404, "{\"success\":false,\"message\":\"الحساب غير موجود\"}");
                return;
            }
            String token = sessionToken(exchange);
            if (token != null) sessions.put(token, updated);
            sendJson(exchange, 200, userJson(updated));
        }

        private void registerCustomer(HttpExchange exchange) throws Exception {
            requireMethod(exchange, "POST");
            Map<String, String> body = formBody(exchange);
            User user = service.registerCustomer(body.get("name"), body.get("email"),
                    body.get("phone"), body.get("password"));
            sendJson(exchange, 201, "{\"success\":true,\"userId\":" + user.getId() + "}");
        }

        private void registerMother(HttpExchange exchange) throws Exception {
            requireMethod(exchange, "POST");
            Map<String, String> body = formBody(exchange);
            service.registerMotherWithInvitation(body.get("token"), body.get("name"), body.get("email"),
                    body.get("phone"), body.get("password"), body.get("region"), body.get("bio"),
                    body.get("pickup_address"), body.get("pickup_instructions"));
            sendJson(exchange, 201,
                    "{\"success\":true,\"message\":\"تم تسجيل الحساب وهو قيد مراجعة واعتماد الإدارة.\"}");
        }

        private void products(HttpExchange exchange) throws IOException {
            User user = authenticatedUser(exchange);
            switch (exchange.getRequestMethod().toUpperCase()) {
                case "GET" -> sendProducts(exchange, user != null && "MOTHER".equals(user.getRole())
                        ? service.getMotherProducts(user.getId()) : service.getAvailableProducts());
                case "POST", "PUT" -> {
                    if (user == null || !"MOTHER".equals(user.getRole())) {
                        sendJson(exchange, 403, "{\"success\":false,\"message\":\"الخدمة للأمهات المسجلات فقط\"}");
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
                    sendJson(exchange, success ? 200 : 400, "{\"success\":" + success + "}");
                }
                case "DELETE" -> {
                    if (user == null || !"MOTHER".equals(user.getRole())) {
                        sendJson(exchange, 403, "{\"success\":false,\"message\":\"الخدمة للأمهات المسجلات فقط\"}");
                        return;
                    }
                    int id = parseInt(formBody(exchange).get("id"), "معرف الوجبة");
                    boolean success = service.deleteProduct(id, user.getId());
                    sendJson(exchange, success ? 200 : 404, "{\"success\":" + success + "}");
                }
                default -> sendJson(exchange, 405, "{\"success\":false,\"message\":\"الطريقة غير مسموحة\"}");
            }
        }

        private Product productFromForm(Map<String, String> body, int sellerId) {
            Product product = new Product();
            product.setSellerId(sellerId);
            product.setCategoryId(parseInt(body.getOrDefault("category_id", "1"), "التصنيف"));
            product.setName(body.get("name"));
            product.setDescription(body.get("description"));
            try {
                product.setPrice(new BigDecimal(body.get("price")));
            } catch (RuntimeException e) {
                throw new IllegalArgumentException("السعر غير صحيح");
            }
            product.setQuantityAvailable(parseInt(body.get("quantity"), "الكمية"));
            product.setAvailable(Boolean.parseBoolean(body.getOrDefault("available", "true")));
            product.setImageUrl(body.get("image_url"));
            product.setAvailableUntil(body.get("available_until"));
            return product;
        }

        private void orders(HttpExchange exchange) throws Exception {
            User user = authenticatedUser(exchange);
            if (user == null) {
                sendJson(exchange, 401, "{\"success\":false,\"message\":\"سجّل الدخول لمتابعة الطلبات\"}");
                return;
            }
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                if (!"CUSTOMER".equals(user.getRole())) {
                    sendJson(exchange, 403, "{\"success\":false,\"message\":\"الطلب متاح بحساب العميل فقط\"}");
                    return;
                }
                Map<String, String> body = formBody(exchange);
                int sellerId = parseInt(body.get("seller_id"), "معرف الأم");
                List<OrderItem> items = parseItems(body);
                Order order = service.placeOrder(user.getId(), sellerId, body.get("pickup_time"),
                        body.getOrDefault("delivery_method", "pickup"), body.get("delivery_address"),
                        body.get("customer_phone"), body.get("contact_method"), body.get("payment_method"), items);
                sendJson(exchange, 201, "{\"success\":true,\"orderId\":" + order.getId()
                        + ",\"total\":" + order.getTotalAmount() + "}");
                return;
            }
            requireMethod(exchange, "GET");
            List<Order> orders = switch (user.getRole()) {
                case "CUSTOMER" -> service.getCustomerOrders(user.getId());
                case "MOTHER" -> service.getMotherOrders(user.getId());
                case "ADMIN" -> service.getAllOrders();
                default -> List.of();
            };
            sendJson(exchange, 200, ordersJson(orders, user));
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
                sendJson(exchange, 403, "{\"success\":false,\"message\":\"غير مصرح بتحديث الطلب\"}");
                return;
            }
            Map<String, String> body = formBody(exchange);
            int orderId = parseInt(body.get("order_id"), "معرف الطلب");
            String status = body.get("status");
            if (!Set.of("confirmed", "preparing", "ready", "completed", "rejected", "cancelled").contains(status)) {
                throw new IllegalArgumentException("حالة الطلب غير صالحة");
            }
            boolean updated = service.updateOrderStatus(orderId, status, user.getId(), user.getRole());
            sendJson(exchange, updated ? 200 : 409, "{\"success\":" + updated
                    + ",\"message\":\"تعذر تحديث الطلب أو أن حالته لا تسمح بهذا الإجراء\"}");
        }

        private void reviews(HttpExchange exchange) throws IOException {
            if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                Map<String, String> params = queryParams(exchange);
                int sellerId = parseInt(params.get("sellerId"), "معرف الأم");
                sendJson(exchange, 200, reviewsJson(service.getSellerReviews(sellerId)));
                return;
            }
            requireMethod(exchange, "POST");
            User user = authenticatedUser(exchange);
            if (user == null || !"CUSTOMER".equals(user.getRole())) {
                sendJson(exchange, 403, "{\"success\":false,\"message\":\"سجّل الدخول بحساب عميل لإضافة تقييم\"}");
                return;
            }
            Map<String, String> body = formBody(exchange);
            boolean added = service.addReview(parseInt(body.get("order_id"), "معرف الطلب"), user.getId(),
                    parseInt(body.get("seller_id"), "معرف الأم"),
                    parseInt(body.get("rating"), "التقييم"), body.get("comment"));
            sendJson(exchange, added ? 201 : 400,
                    "{\"success\":" + added + ",\"message\":\"التقييم متاح مرة واحدة بعد إتمام الطلب فقط\"}");
        }

        private void changePassword(HttpExchange exchange) throws IOException {
            requireMethod(exchange, "POST");
            User user = authenticatedUser(exchange);
            if (user == null) {
                sendJson(exchange, 401, "{\"success\":false,\"message\":\"سجّل الدخول أولاً\"}");
                return;
            }
            Map<String, String> body = formBody(exchange);
            service.changePassword(user.getId(), body.get("current_password"), body.get("new_password"));
            sendJson(exchange, 200, "{\"success\":true}");
        }

        private void adminStats(HttpExchange exchange) throws IOException {
            requireMethod(exchange, "GET");
            User user = authenticatedUser(exchange);
            if (user == null || !"ADMIN".equals(user.getRole())) {
                sendJson(exchange, 403, "{\"success\":false,\"message\":\"هذه الخدمة للإدارة فقط\"}");
                return;
            }
            sendJson(exchange, 200, service.getAdminStatsJson());
        }

        private User requireAdmin(HttpExchange exchange) throws IOException {
            User user = authenticatedUser(exchange);
            if (user == null || !"ADMIN".equals(user.getRole())) {
                sendJson(exchange, 403, "{\"success\":false,\"message\":\"هذه الخدمة للإدارة فقط\"}");
                return null;
            }
            return user;
        }

        private void adminInvitations(HttpExchange exchange) throws Exception {
            User admin = requireAdmin(exchange);
            if (admin == null) return;
            if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                List<Invitation> invitations = service.getAllInvitations();
                StringBuilder json = new StringBuilder("[");
                for (int i = 0; i < invitations.size(); i++) {
                    Invitation invitation = invitations.get(i);
                    if (i > 0) json.append(",");
                    json.append(String.format("{\"id\":%d,\"target\":\"%s\",\"expiresAt\":\"%s\",\"used\":%s,\"valid\":%s}",
                            invitation.getId(), escapeJson(invitation.getEmailOrPhone()),
                            escapeJson(Objects.toString(invitation.getExpiresAt(), "")),
                            invitation.getUsedAt() != null, invitation.isValid()));
                }
                sendJson(exchange, 200, json.append("]").toString());
            } else if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                Map<String, String> body = formBody(exchange);
                int days = parseInt(body.getOrDefault("days", "7"), "مدة الدعوة");
                if (days < 1 || days > 90) throw new IllegalArgumentException("مدة الدعوة بين يوم و90 يوماً");
                String target = Objects.toString(body.get("target"), "").trim();
                if (target.isEmpty()) throw new IllegalArgumentException("أدخل البريد الإلكتروني أو رقم الهاتف");
                Invitation invitation = service.createInvitation(admin.getId(), target, days);
                sendJson(exchange, 201, String.format("{\"success\":true,\"id\":%d,\"token\":\"%s\",\"expiresAt\":\"%s\"}",
                        invitation.getId(), escapeJson(invitation.getToken()), escapeJson(invitation.getExpiresAt().toString())));
            } else if ("DELETE".equalsIgnoreCase(exchange.getRequestMethod())) {
                int id = parseInt(formBody(exchange).get("id"), "معرف الدعوة");
                boolean revoked = service.revokeInvitation(id);
                sendJson(exchange, revoked ? 200 : 404, "{\"success\":" + revoked + "}");
            } else {
                sendJson(exchange, 405, "{\"success\":false,\"message\":\"الطريقة غير مسموحة\"}");
            }
        }

        private void adminMothers(HttpExchange exchange) throws IOException {
            if (requireAdmin(exchange) == null) return;
            requireMethod(exchange, "GET");
            StringBuilder json = new StringBuilder("[");
            List<User> mothers = service.getAllMothers();
            for (int i = 0; i < mothers.size(); i++) {
                User mother = mothers.get(i);
                SellerProfile profile = service.getSellerProfile(mother.getId());
                if (i > 0) json.append(",");
                json.append(String.format("{\"id\":%d,\"name\":\"%s\",\"email\":\"%s\",\"phone\":\"%s\",\"status\":\"%s\",\"approval\":\"%s\"}",
                        mother.getId(), escapeJson(mother.getName()), escapeJson(mother.getEmail()),
                        escapeJson(mother.getPhone()),
                        escapeJson(mother.getStatus()), escapeJson(profile == null ? "PENDING" : profile.getApprovalStatus())));
            }
            sendJson(exchange, 200, json.append("]").toString());
        }

        private void updateMotherStatus(HttpExchange exchange) throws Exception {
            if (requireAdmin(exchange) == null) return;
            requireMethod(exchange, "POST");
            Map<String, String> body = formBody(exchange);
            service.setMotherApproval(parseInt(body.get("mother_id"), "معرف الأم"), body.get("status"));
            sendJson(exchange, 200, "{\"success\":true}");
        }

        // بحث عن مستخدم (عميل أو أم) برقم الهاتف أو جزء منه — للإدارة فقط
        private void adminUserSearch(HttpExchange exchange) throws IOException {
            if (requireAdmin(exchange) == null) return;
            requireMethod(exchange, "GET");
            String query = queryParams(exchange).getOrDefault("q", "").trim();
            if (query.isEmpty()) throw new IllegalArgumentException("أدخل رقم الهاتف للبحث");
            List<User> results = service.searchUsersByPhone(query);
            StringBuilder json = new StringBuilder("[");
            for (int i = 0; i < results.size(); i++) {
                User u = results.get(i);
                if (i > 0) json.append(",");
                json.append(String.format(
                        "{\"id\":%d,\"name\":\"%s\",\"email\":\"%s\",\"phone\":\"%s\",\"role\":\"%s\",\"status\":\"%s\"}",
                        u.getId(), escapeJson(u.getName()), escapeJson(u.getEmail()),
                        escapeJson(u.getPhone()), escapeJson(u.getRole()), escapeJson(u.getStatus())));
            }
            sendJson(exchange, 200, json.append("]").toString());
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
                String cType = contentType(requested);
                exchange.getResponseHeaders().set("Content-Type", cType);
                exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
                if (cType.contains("vnd.android.package-archive")) {
                    exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"MomHouse.apk\"");
                }
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
            if (name.endsWith(".json")) return "application/json; charset=UTF-8";
            if (name.endsWith(".webmanifest")) return "application/manifest+json; charset=UTF-8";
            if (name.endsWith(".apk")) return "application/vnd.android.package-archive";
            if (name.endsWith(".png")) return "image/png";
            if (name.endsWith(".jpg") || name.endsWith(".jpeg")) return "image/jpeg";
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
            sendJson(exchange, 405, "{\"success\":false,\"message\":\"الطريقة غير مسموحة\"}");
            throw new RequestHandledException();
        }
    }

    private void sendProducts(HttpExchange exchange, List<Product> products) throws IOException {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < products.size(); i++) {
            Product p = products.get(i);
            if (i > 0) json.append(",");
            json.append(String.format("{\"id\":%d,\"sellerId\":%d,\"name\":\"%s\",\"description\":\"%s\",\"price\":%s,\"quantity\":%d,\"available\":%s,\"availableUntil\":\"%s\",\"imageUrl\":\"%s\",\"categoryId\":%d,\"sellerName\":\"%s\",\"sellerRegion\":\"%s\",\"categoryName\":\"%s\"}",
                    p.getId(), p.getSellerId(), escapeJson(p.getName()), escapeJson(p.getDescription()),
                    p.getPrice(), p.getQuantityAvailable(), p.isAvailable(), escapeJson(p.getAvailableUntil()),
                    escapeJson(p.getImageUrl()), p.getCategoryId(), escapeJson(p.getSellerName()),
                    escapeJson(p.getSellerRegion()), escapeJson(p.getCategoryName())));
        }
        sendJson(exchange, 200, json.append("]").toString());
    }

    private String ordersJson(List<Order> orders, User user) {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < orders.size(); i++) {
            Order order = orders.get(i);
            if (i > 0) json.append(",");
            boolean canSeePickupAddress = !"pending".equals(order.getStatus())
                    && !"delivery".equals(order.getDeliveryMethod())
                    && ("ADMIN".equals(user.getRole()) || "MOTHER".equals(user.getRole()));
            if ("CUSTOMER".equals(user.getRole())) {
                canSeePickupAddress = !"pending".equals(order.getStatus())
                        && !"delivery".equals(order.getDeliveryMethod());
            }
            String items = orderItemsJson(order.getItems());
            json.append(String.format("{\"id\":%d,\"sellerId\":%d,\"sellerName\":\"%s\",\"sellerAddress\":\"%s\",\"deliveryMethod\":\"%s\",\"deliveryAddress\":\"%s\",\"customerName\":\"%s\",\"customerPhone\":\"%s\",\"contactMethod\":\"%s\",\"paymentMethod\":\"%s\",\"total\":%s,\"pickupTime\":\"%s\",\"status\":\"%s\",\"items\":%s}",
                    order.getId(), order.getSellerId(), escapeJson(order.getSellerName()),
                    escapeJson(canSeePickupAddress ? Objects.toString(order.getSellerAddress(), "") : ""),
                    escapeJson(order.getDeliveryMethod()),
                    escapeJson(order.getDeliveryAddress()),
                    escapeJson(order.getCustomerName()), escapeJson(order.getCustomerPhone()),
                    escapeJson(order.getContactMethod()), escapeJson(order.getPaymentMethod()),
                    order.getTotalAmount(), escapeJson(order.getPickupTime()), escapeJson(order.getStatus()), items));
        }
        return json.append("]").toString();
    }

    private String orderItemsJson(List<OrderItem> items) {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < items.size(); i++) {
            OrderItem item = items.get(i);
            if (i > 0) json.append(",");
            json.append(String.format("{\"name\":\"%s\",\"quantity\":%d,\"subtotal\":%s}",
                    escapeJson(item.getProductName()), item.getQuantity(), item.getSubtotal()));
        }
        return json.append("]").toString();
    }

    private String reviewsJson(List<Review> reviews) {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < reviews.size(); i++) {
            Review review = reviews.get(i);
            if (i > 0) json.append(",");
            json.append(String.format("{\"name\":\"%s\",\"rating\":%d,\"comment\":\"%s\"}",
                    escapeJson(review.getCustomerName()), review.getRating(), escapeJson(review.getComment())));
        }
        return json.append("]").toString();
    }

    private String userJson(User user) {
        return String.format("{\"success\":true,\"user\":{\"id\":%d,\"name\":\"%s\",\"email\":\"%s\",\"phone\":\"%s\",\"address\":\"%s\",\"preferredContact\":\"%s\",\"role\":\"%s\",\"status\":\"%s\"}}",
                user.getId(), escapeJson(user.getName()), escapeJson(user.getEmail()),
                escapeJson(user.getPhone()), escapeJson(user.getAddress()), escapeJson(user.getPreferredContact()),
                escapeJson(user.getRole()), escapeJson(user.getStatus()));
    }

    private String errorJson(String message) {
        return "{\"success\":false,\"message\":\"" + escapeJson(Objects.toString(message, "البيانات غير صحيحة")) + "\"}";
    }

    private String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    private void sendJson(HttpExchange exchange, int status, String response) throws IOException {
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private static final class RequestHandledException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }
}
