package com.beitmama.service;

import com.beitmama.dao.*;
import com.beitmama.model.*;
import com.beitmama.util.SecurityUtil;

import java.util.List;
import java.util.UUID;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BeitMamaService {
    private final UserDao userDao = new UserDao();
    private final InvitationDao invitationDao = new InvitationDao();
    private final ProductDao productDao = new ProductDao();
    private final OrderDao orderDao = new OrderDao();
    private final ReviewDao reviewDao = new ReviewDao();
    private final VideoDao videoDao = new VideoDao();


    public User login(String email, String password) {
        User user = userDao.findByEmail(email);
        if (user == null) return null;
        if (!"ACTIVE".equalsIgnoreCase(user.getStatus())) {
            throw new IllegalStateException("الحساب غير نشط بعد، يرجى التواصل مع الإدارة");
        }
        String calculated = SecurityUtil.hashPassword(password, user.getSalt());
        if (calculated.equals(user.getPasswordHash())) {
            return user;
        }
        return null;
    }


    public User registerCustomer(String name, String email, String phone, String password) throws Exception {
        validateAccount(name, email, phone, password);
        if (userDao.findByEmail(email) != null) {
            throw new IllegalArgumentException("البريد الإلكتروني مسجل مسبقاً");
        }
        return userDao.registerUser(name, email, phone, password, "CUSTOMER", "ACTIVE");
    }


    public User loginOrRegisterWithGoogle(String email, String name, String picture) {
        if (email == null || !email.contains("@")) {
            throw new IllegalArgumentException("البريد الإلكتروني من جوجل غير صالح");
        }
        User user = userDao.findByEmail(email);
        if (user == null) {
            try {

                String randomPassword = UUID.randomUUID().toString();
                String phone = "google-" + UUID.randomUUID().toString().substring(0, 8);
                user = userDao.registerUser(name == null || name.isBlank() ? "مستخدم جوجل" : name,
                        email, phone, randomPassword, "CUSTOMER", "ACTIVE");
            } catch (Exception e) {
                throw new IllegalStateException("تعذر إنشاء حساب جوجل", e);
            }
        }
        if (!"ACTIVE".equalsIgnoreCase(user.getStatus())) {
            throw new IllegalStateException("الحساب غير نشط بعد، يرجى التواصل مع الإدارة");
        }
        return user;
    }


    public Invitation createInvitation(int adminId, String emailOrPhone, int days) throws Exception {
        return invitationDao.createInvitation(adminId, emailOrPhone, days);
    }

    public List<Invitation> getAllInvitations() {
        return invitationDao.listAllInvitations();
    }

    public boolean revokeInvitation(int invitationId) {
        return invitationDao.revokeInvitation(invitationId);
    }


    public synchronized User registerMotherWithInvitation(String token, String name, String email, String phone,
                                                          String password, String region, String bio,
                                                          String pickupAddress, String instructions,
                                                          String locationUrl) throws Exception {
        validateAccount(name, email, phone, password);
        if (pickupAddress == null || pickupAddress.isBlank()) {
            throw new IllegalArgumentException("عنوان الاستلام مطلوب");
        }
        if (region == null || region.isBlank() || region.trim().length() > 100) {
            throw new IllegalArgumentException("اسم المنطقة مطلوب وبحد أقصى 100 حرف");
        }
        String normalizedName = name.trim().replaceAll("\\s+", " ");
        double[] coordinates = parseGoogleMapsCoordinates(locationUrl);
        Invitation invitation = invitationDao.findByToken(token);
        if (invitation == null || !invitation.isValid()) {
            throw new IllegalArgumentException("رمز الدعوة غير صالح، مستخدم مسبقاً أو منتهي الصلاحية.");
        }
        if (userDao.motherNameExists(normalizedName)) {
            throw new IllegalArgumentException("اسم الأم مستخدم بالفعل. أضيفي اسم العائلة أو اسم المنطقة لتمييز حسابك.");
        }
        if (userDao.findByEmail(email) != null) {
            throw new IllegalArgumentException("البريد الإلكتروني مسجل مسبقاً");
        }

        User user = userDao.registerUser(normalizedName, email, phone, password, "MOTHER", "PENDING");
        if (user != null) {

            boolean consumed = invitationDao.consumeInvitation(invitation.getId(), user.getId());
            if (!consumed) {
                throw new IllegalStateException("فشل استهلاك الدعوة، ربما تم استخدامها الآن من مصدر آخر.");
            }
            SellerProfile profile = new SellerProfile(user.getId(), bio, pickupAddress, region.trim(),
                    "PENDING", instructions, locationUrl.trim(), coordinates[0], coordinates[1]);
            userDao.saveSellerProfile(profile);
        }
        return user;
    }


    public boolean toggleFollow(int customerId, int sellerId) {
        try {
            return userDao.toggleFollow(customerId, sellerId);
        } catch (java.sql.SQLException e) {
            throw new IllegalStateException("تعذر تحديث المتابعة", e);
        }
    }

    public List<MotherProfile> getFollowedMothers(int customerId) {
        return userDao.getFollowedMothers(customerId);
    }

    public List<String> getMotherVideos(int sellerId) {
        return videoDao.findVideosBySeller(sellerId);
    }

    public boolean addMotherVideo(int sellerId, String videoUrl) {
        try {
            return videoDao.addVideo(sellerId, videoUrl);
        } catch (java.sql.SQLException e) {
            throw new IllegalStateException("تعذر حفظ الفيديو", e);
        }
    }

    public void setMotherApproval(int userId, String status) throws Exception {
        if (!List.of("APPROVED", "REJECTED", "SUSPENDED").contains(status)) {
            throw new IllegalArgumentException("حالة الاعتماد غير صحيحة");
        }
        userDao.updateSellerApproval(userId, status);
        userDao.updateUserStatus(userId, "APPROVED".equals(status) ? "ACTIVE" : "SUSPENDED");
    }


    public List<Product> getAvailableProducts() {
        return productDao.findAllAvailable();
    }

    public List<Product> getMotherProducts(int sellerId) {
        return productDao.findBySeller(sellerId);
    }

    public List<Category> getProductCategories() {
        return productDao.findCategories();
    }

    public List<MotherLocation> getApprovedMotherLocations() {
        return userDao.findApprovedMotherLocations();
    }

    public List<MotherProfile> searchApprovedMothers(String query) {
        return userDao.searchApprovedMothers(query, 8);
    }

    public MotherProfile getMotherProfile(int sellerId, Integer customerId) {
        SellerProfile sellerProfile = userDao.findApprovedMotherProfile(sellerId);
        User mother = userDao.findById(sellerId);
        if (sellerProfile == null || mother == null) return null;
        MotherProfile profile = new MotherProfile();
        profile.setId(mother.getId());
        profile.setName(mother.getName());
        profile.setRegion(sellerProfile.getRegion());
        profile.setBio(sellerProfile.getBio());
        profile.setLocationUrl(sellerProfile.getLocationUrl());
        profile.setProfileImageUrl(sellerProfile.getProfileImageUrl());
        profile.setLatitude(sellerProfile.getLatitude());
        profile.setLongitude(sellerProfile.getLongitude());
        profile.setProducts(productDao.findAvailableBySeller(sellerId));
        profile.setReviews(reviewDao.findLatestMotherReviews(sellerId));
        ReviewDao.MotherRatingSummary rating = reviewDao.motherRatingSummary(sellerId);
        profile.setRating(rating.average());
        profile.setRatingCount(rating.ratingCount());
        profile.setHeartCount(rating.likeCount());
        profile.setLiked(customerId != null && reviewDao.customerLikedMother(customerId, sellerId));
        return profile;
    }

    public boolean toggleMotherLike(int customerId, int sellerId) {
        requireApprovedMother(sellerId);
        return reviewDao.toggleMotherLike(customerId, sellerId);
    }

    public boolean addMotherReview(int customerId, int sellerId, int rating, String comment) {
        requireApprovedMother(sellerId);
        return reviewDao.addMotherReview(customerId, sellerId, rating, comment);
    }

    public void updateMotherProfileImage(int sellerId, String imageUrl) {
        if (imageUrl == null || !imageUrl.startsWith("/uploads/") || imageUrl.length() > 255) {
            throw new IllegalArgumentException("رابط صورة الملف الشخصي غير صالح");
        }
        try {
            userDao.updateMotherProfileImage(sellerId, imageUrl);
        } catch (java.sql.SQLException e) {
            throw new IllegalStateException("تعذر حفظ صورة الملف الشخصي", e);
        }
    }

    private void requireApprovedMother(int sellerId) {
        if (userDao.findApprovedMotherProfile(sellerId) == null) {
            throw new IllegalArgumentException("الأم غير موجودة أو لم يتم اعتماد حسابها");
        }
    }

    public boolean addProduct(Product p) {
        validateProduct(p);
        return productDao.addProduct(p);
    }

    public boolean updateProduct(Product p) {
        validateProduct(p);
        return productDao.updateProduct(p);
    }

    private void validateAccount(String name, String email, String phone, String password) {
        if (name == null || name.isBlank() || email == null || !email.contains("@")
                || phone == null || phone.isBlank() || password == null || password.length() < 8) {
            throw new IllegalArgumentException("أدخل الاسم والبريد والهاتف، واجعل كلمة المرور 8 أحرف على الأقل");
        }
    }

    private void validateProduct(Product product) {
        if (product.getName() == null || product.getName().isBlank() || product.getPrice() == null
                || product.getPrice().signum() <= 0 || product.getQuantityAvailable() < 0
                || product.getCategoryId() < 1
                || product.getPreparationMinutes() < 0
                || (product.getIngredients() != null && product.getIngredients().length() > 2000)) {
            throw new IllegalArgumentException("تحقق من اسم الوجبة والسعر والكمية والتصنيف");
        }
        if (product.getAvailableUntil() != null && !product.getAvailableUntil().isBlank()) {
            try {
                java.time.LocalDate.parse(product.getAvailableUntil());
            } catch (java.time.format.DateTimeParseException e) {
                throw new IllegalArgumentException("تاريخ انتهاء التوفر غير صحيح");
            }
        }
    }

    private double[] parseGoogleMapsCoordinates(String locationUrl) {
        if (locationUrl == null || locationUrl.isBlank() || locationUrl.length() > 2000) {
            throw new IllegalArgumentException("رابط موقعك على خرائط Google مطلوب");
        }
        try {
            URI uri = URI.create(locationUrl.trim());
            String host = uri.getHost();
            if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null
                    || !(host.equalsIgnoreCase("google.com") || host.endsWith(".google.com")
                    || host.equalsIgnoreCase("maps.app.goo.gl") || host.equalsIgnoreCase("goo.gl"))) {
                throw new IllegalArgumentException("أدخل رابط موقع صالحاً من خرائط Google");
            }
            String decoded = URLDecoder.decode(locationUrl, StandardCharsets.UTF_8);
            Matcher degreeCoordinates = Pattern.compile("!3d(-?\\d+(?:\\.\\d+)?)!4d(-?\\d+(?:\\.\\d+)?)").matcher(decoded);
            Matcher pairCoordinates = Pattern.compile("(-?\\d{1,2}(?:\\.\\d+)?)\\s*[,/]\\s*(-?\\d{1,3}(?:\\.\\d+)?)").matcher(decoded);
            double latitude;
            double longitude;
            if (degreeCoordinates.find()) {
                latitude = Double.parseDouble(degreeCoordinates.group(1));
                longitude = Double.parseDouble(degreeCoordinates.group(2));
            } else if (pairCoordinates.find()) {
                latitude = Double.parseDouble(pairCoordinates.group(1));
                longitude = Double.parseDouble(pairCoordinates.group(2));
            } else {
                throw new IllegalArgumentException("الرابط لا يحتوي إحداثيات موقع واضحة. اختاري الموقع على الخريطة ثم انسخي رابط الدبوس.");
            }
            if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
                throw new IllegalArgumentException("إحداثيات الموقع خارج النطاق الصحيح");
            }
            return new double[] { latitude, longitude };
        } catch (IllegalArgumentException e) {
            if (e.getMessage() != null && (e.getMessage().contains("رابط")
                    || e.getMessage().contains("إحداثيات")
                    || e.getMessage().contains("الإحداثيات"))) throw e;
            throw new IllegalArgumentException("رابط موقع خرائط Google غير صالح");
        }
    }

    public boolean deleteProduct(int id, int sellerId) {
        return productDao.deleteProduct(id, sellerId);
    }

    public User updateCustomerProfile(int userId, String name, String phone, String alternatePhone, String address, String preferredContact) {
        if (name == null || name.isBlank() || name.length() > 150
                || phone == null || phone.isBlank() || phone.length() > 30
                || alternatePhone == null || alternatePhone.isBlank() || alternatePhone.length() > 30
                || address == null || address.isBlank() || address.length() > 500
                || (!"phone".equals(preferredContact) && !"whatsapp".equals(preferredContact))) {
            throw new IllegalArgumentException("أدخل الاسم والهاتف البديل والعنوان واختر وسيلة تواصل صحيحة");
        }
        try {
            return userDao.updateCustomerProfile(userId, name.trim(), phone.trim(), alternatePhone.trim(), address.trim(), preferredContact);
        } catch (java.sql.SQLException e) {
            throw new IllegalStateException("تعذر حفظ بيانات الحساب", e);
        }
    }

    public User updateCustomerProfileImage(int userId, String imageUrl) {
        if (imageUrl == null || !imageUrl.startsWith("/uploads/") || imageUrl.length() > 255) {
            throw new IllegalArgumentException("رابط صورة الملف الشخصي غير صالح");
        }
        try {
            return userDao.updateCustomerProfileImage(userId, imageUrl);
        } catch (java.sql.SQLException e) {
            throw new IllegalStateException("تعذر حفظ صورة الملف الشخصي", e);
        }
    }


    public Order placeOrder(int customerId, int sellerId, String pickupTime, String proposedDeliveryTime, String deliveryMethod,
                            String deliveryAddress, String customerPhone, String contactMethod,
                            String paymentMethod, List<OrderItem> items) throws Exception {
        User customer = userDao.findById(customerId);
        if (customer == null) throw new IllegalArgumentException("العميل غير موجود");

        return orderDao.createOrder(customerId, sellerId, pickupTime, proposedDeliveryTime, deliveryMethod,
                                    deliveryAddress, customer.getName(), customerPhone, customer.getAlternatePhone(), customer.getAddress(), contactMethod,
                                    paymentMethod, items);
    }

    public List<Order> getCustomerOrders(int customerId) {
        return orderDao.findByCustomer(customerId);
    }

    public List<Order> getMotherOrders(int sellerId) {
        return orderDao.findBySeller(sellerId);
    }

    public List<Order> getAllOrders() {
        return orderDao.findAllOrders();
    }

    public boolean updateOrderStatus(int orderId, String newStatus, int actorId, String actorRole) {
        return orderDao.updateOrderStatus(orderId, newStatus, actorId, actorRole);
    }


    public boolean addReview(int orderId, int customerId, int sellerId, int rating, String comment) {
        return reviewDao.addReview(orderId, customerId, sellerId, rating, comment);
    }

    public List<Review> getSellerReviews(int sellerId) {
        return reviewDao.findBySeller(sellerId);
    }

    public void changePassword(int userId, String currentPassword, String newPassword) {
        if (currentPassword == null || newPassword == null || newPassword.length() < 8) {
            throw new IllegalArgumentException("كلمة المرور الجديدة يجب ألا تقل عن 8 أحرف");
        }
        try {
            if (!userDao.changePassword(userId, currentPassword, newPassword)) {
                throw new IllegalArgumentException("كلمة المرور الحالية غير صحيحة");
            }
        } catch (java.sql.SQLException e) {
            throw new IllegalStateException("تعذر تغيير كلمة المرور", e);
        }
    }

    public String getAdminStatsJson() {
        return orderDao.getAdminStatsJson();
    }


    public List<User> getAllMothers() {
        return userDao.findAllMothers();
    }


    public List<User> searchUsersByPhone(String phoneQuery) {
        return userDao.searchByPhone(phoneQuery);
    }

    public List<User> getAllCustomers() {
        return userDao.findAllCustomers();
    }

    public SellerProfile getSellerProfile(int userId) {
        return userDao.getSellerProfile(userId);
    }
}
