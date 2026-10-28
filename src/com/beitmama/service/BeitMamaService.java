package com.beitmama.service;

import com.beitmama.dao.*;
import com.beitmama.model.*;
import com.beitmama.util.SecurityUtil;

import java.util.List;
import java.util.UUID;

public class BeitMamaService {
    private final UserDao userDao = new UserDao();
    private final InvitationDao invitationDao = new InvitationDao();
    private final ProductDao productDao = new ProductDao();
    private final OrderDao orderDao = new OrderDao();
    private final ReviewDao reviewDao = new ReviewDao();

    // 1. تسجيل الدخول
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

    // 2. تسجيل عميل جديد
    public User registerCustomer(String name, String email, String phone, String password) throws Exception {
        validateAccount(name, email, phone, password);
        if (userDao.findByEmail(email) != null) {
            throw new IllegalArgumentException("البريد الإلكتروني مسجل مسبقاً");
        }
        return userDao.registerUser(name, email, phone, password, "CUSTOMER", "ACTIVE");
    }

    // 2-م. دخول/تسجيل عبر جوجل (Google Sign-In)
    public User loginOrRegisterWithGoogle(String email, String name, String picture) {
        if (email == null || !email.contains("@")) {
            throw new IllegalArgumentException("البريد الإلكتروني من جوجل غير صالح");
        }
        User user = userDao.findByEmail(email);
        if (user == null) {
            try {
                // حساب عميل جديد مرتبط بجوجل — كلمة مرور عشوائية غير معروفة للمستخدم
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

    // 3. نظام الدعوات للأمهات
    public Invitation createInvitation(int adminId, String emailOrPhone, int days) throws Exception {
        return invitationDao.createInvitation(adminId, emailOrPhone, days);
    }

    public List<Invitation> getAllInvitations() {
        return invitationDao.listAllInvitations();
    }

    public boolean revokeInvitation(int invitationId) {
        return invitationDao.revokeInvitation(invitationId);
    }

    // 4. تسجيل أم بواسطة دعوة صالحة
    public synchronized User registerMotherWithInvitation(String token, String name, String email, String phone,
                                                          String password, String region, String bio,
                                                          String pickupAddress, String instructions) throws Exception {
        validateAccount(name, email, phone, password);
        if (pickupAddress == null || pickupAddress.isBlank()) {
            throw new IllegalArgumentException("عنوان الاستلام مطلوب");
        }
        if (region == null || region.isBlank() || region.trim().length() > 100) {
            throw new IllegalArgumentException("اسم المنطقة مطلوب وبحد أقصى 100 حرف");
        }
        Invitation invitation = invitationDao.findByToken(token);
        if (invitation == null || !invitation.isValid()) {
            throw new IllegalArgumentException("رمز الدعوة غير صالح، مستخدم مسبقاً أو منتهي الصلاحية.");
        }
        if (userDao.findByEmail(email) != null) {
            throw new IllegalArgumentException("البريد الإلكتروني مسجل مسبقاً");
        }

        // تسجيل الأم كـ PENDING بانتظار اعتماد المدير
        User user = userDao.registerUser(name, email, phone, password, "MOTHER", "PENDING");
        if (user != null) {
            // استهلاك الدعوة فورياً
            boolean consumed = invitationDao.consumeInvitation(invitation.getId(), user.getId());
            if (!consumed) {
                throw new IllegalStateException("فشل استهلاك الدعوة، ربما تم استخدامها الآن من مصدر آخر.");
            }
            SellerProfile profile = new SellerProfile(user.getId(), bio, pickupAddress, region.trim(),
                    "PENDING", instructions);
            userDao.saveSellerProfile(profile);
        }
        return user;
    }

    // 5. قبول أو رفض الأم
    public void setMotherApproval(int userId, String status) throws Exception {
        if (!List.of("APPROVED", "REJECTED", "SUSPENDED").contains(status)) {
            throw new IllegalArgumentException("حالة الاعتماد غير صحيحة");
        }
        userDao.updateSellerApproval(userId, status);
        userDao.updateUserStatus(userId, "APPROVED".equals(status) ? "ACTIVE" : "SUSPENDED");
    }

    // 6. منتجات وأكلات
    public List<Product> getAvailableProducts() {
        return productDao.findAllAvailable();
    }

    public List<Product> getMotherProducts(int sellerId) {
        return productDao.findBySeller(sellerId);
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
                || product.getCategoryId() < 1 || product.getCategoryId() > 4) {
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

    public boolean deleteProduct(int id, int sellerId) {
        return productDao.deleteProduct(id, sellerId);
    }

    public User updateCustomerProfile(int userId, String name, String phone, String address, String preferredContact) {
        if (name == null || name.isBlank() || name.length() > 150
                || phone == null || phone.isBlank() || phone.length() > 30
                || address == null || address.length() > 500
                || (!"phone".equals(preferredContact) && !"whatsapp".equals(preferredContact))) {
            throw new IllegalArgumentException("أدخل الاسم والهاتف والعنوان واختر وسيلة تواصل صحيحة");
        }
        try {
            return userDao.updateCustomerProfile(userId, name.trim(), phone.trim(), address.trim(), preferredContact);
        } catch (java.sql.SQLException e) {
            throw new IllegalStateException("تعذر حفظ بيانات الحساب", e);
        }
    }

    // 7. الطلبات
    public Order placeOrder(int customerId, int sellerId, String pickupTime, String deliveryMethod,
                            String deliveryAddress, String customerPhone, String contactMethod,
                            String paymentMethod, List<OrderItem> items) throws Exception {
        return orderDao.createOrder(customerId, sellerId, pickupTime, deliveryMethod, deliveryAddress,
                customerPhone, contactMethod, paymentMethod, items);
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

    // 8. التقييمات
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

    // 9. استرجاع المستخدمين للإدارة
    public List<User> getAllMothers() {
        return userDao.findAllMothers();
    }

    // بحث بالهاتف (جزئي) عن عملاء وأمهات — للإدارة
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
