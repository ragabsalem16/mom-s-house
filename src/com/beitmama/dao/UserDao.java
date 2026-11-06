package com.beitmama.dao;

import com.beitmama.model.MotherLocation;
import com.beitmama.model.MotherProfile;
import com.beitmama.model.SellerProfile;
import com.beitmama.model.User;
import com.beitmama.util.SecurityUtil;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.cloud.firestore.SetOptions;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class UserDao {
    private static final String USERS = "users";
    private static final String SELLER_PROFILES = "seller_profiles";

    public User findByEmail(String email) {
        if (email == null || email.isBlank()) return null;
        String emailKey = SecurityUtil.hash256(email.trim().toLowerCase());
        try {
            DocumentSnapshot index = FirestoreDatabase.await(
                    FirestoreDatabase.get().collection("user_emails").document(emailKey).get());
            if (!index.exists()) return null;
            return findById(number(index.get("user_id")));
        } catch (SQLException e) {
            throw databaseError("تعذر البحث عن الحساب بالبريد الإلكتروني", e);
        }
    }

    public User findById(int id) {
        if (id < 1) return null;
        try {
            DocumentSnapshot snapshot = FirestoreDatabase.read(USERS, id);
            return snapshot.exists() ? mapUser(snapshot) : null;
        } catch (SQLException e) {
            throw databaseError("تعذر تحميل الحساب", e);
        }
    }

    public boolean motherNameExists(String name) {
        String normalized = normalizeName(name);
        try {
            for (QueryDocumentSnapshot snapshot : FirestoreDatabase.list(USERS)) {
                if ("MOTHER".equals(snapshot.getString("role"))
                        && normalized.equals(normalizeName(snapshot.getString("name")))) return true;
            }
            return false;
        } catch (SQLException e) {
            throw databaseError("تعذر التحقق من اسم الأم", e);
        }
    }

    public SellerProfile findApprovedMotherProfile(int sellerId) {
        User user = findById(sellerId);
        SellerProfile profile = getSellerProfile(sellerId);
        if (user == null || !"MOTHER".equals(user.getRole()) || !"ACTIVE".equals(user.getStatus())
                || profile == null || !"APPROVED".equals(profile.getApprovalStatus())) return null;
        return profile;
    }

    public List<MotherProfile> searchApprovedMothers(String query, int limit) {
        String needle = query == null ? "" : query.trim().toLowerCase();
        List<MotherProfile> result = new ArrayList<>();
        try {
            for (QueryDocumentSnapshot snapshot : FirestoreDatabase.list(USERS)) {
                if (!"MOTHER".equals(snapshot.getString("role"))
                        || !"ACTIVE".equals(snapshot.getString("status"))
                        || !Objects.toString(snapshot.getString("name"), "").toLowerCase().contains(needle)) continue;
                SellerProfile profile = getSellerProfile(number(snapshot.get("id")));
                if (profile == null || !"APPROVED".equals(profile.getApprovalStatus())) continue;
                MotherProfile mother = new MotherProfile();
                mother.setId(number(snapshot.get("id")));
                mother.setName(snapshot.getString("name"));
                mother.setRegion(profile.getRegion());
                mother.setProfileImageUrl(profile.getProfileImageUrl());
                result.add(mother);
            }
            result.sort((left, right) -> Objects.toString(left.getName(), "")
                    .compareToIgnoreCase(Objects.toString(right.getName(), "")));
            return result.subList(0, Math.min(Math.max(limit, 0), result.size()));
        } catch (SQLException e) {
            throw databaseError("تعذر البحث عن الأمهات", e);
        }
    }

    public User registerUser(String name, String email, String phone, String password, String role, String status)
            throws SQLException {
        String normalizedEmail = email.trim().toLowerCase();
        String emailKey = SecurityUtil.hash256(normalizedEmail);
        String salt = SecurityUtil.generateSalt();
        String passwordHash = SecurityUtil.hashPassword(password, salt);
        Firestore firestore = FirestoreDatabase.get();
        DocumentReference emailRef = firestore.collection("user_emails").document(emailKey);
        DocumentReference counterRef = firestore.collection("system").document("counters");

        int id = FirestoreDatabase.await(firestore.runTransaction(transaction -> {
            DocumentSnapshot emailSnapshot = transaction.get(emailRef).get();
            DocumentSnapshot counterSnapshot = transaction.get(counterRef).get();
            if (emailSnapshot.exists()) throw new IllegalArgumentException("البريد الإلكتروني مسجل مسبقاً");
            Long current = counterSnapshot.getLong("users_next_id");
            int next = current == null ? 2 : Math.toIntExact(current + 1);
            Map<String, Object> user = new HashMap<>();
            user.put("id", next);
            user.put("name", name.trim());
            user.put("email", normalizedEmail);
            user.put("phone", phone.trim());
            user.put("alternate_phone", null);
            user.put("address", null);
            user.put("preferred_contact", "phone");
            user.put("profile_image_url", null);
            user.put("password_hash", passwordHash);
            user.put("salt", salt);
            user.put("role", role);
            user.put("status", status);
            user.put("created_at", new Date());
            transaction.set(firestore.collection(USERS).document(Integer.toString(next)), user);
            transaction.set(emailRef, Map.of("user_id", next));
            transaction.set(counterRef, Map.of("users_next_id", next), SetOptions.merge());
            return next;
        }));
        return findById(id);
    }

    public void updateUserStatus(int userId, String status) throws SQLException {
        FirestoreDatabase.await(FirestoreDatabase.document(USERS, userId).update("status", status));
    }

    public User updateCustomerProfile(int userId, String name, String phone, String alternatePhone, String address,
                                      String preferredContact) throws SQLException {
        DocumentReference reference = FirestoreDatabase.document(USERS, userId);
        DocumentSnapshot snapshot = FirestoreDatabase.await(reference.get());
        if (!snapshot.exists() || !"CUSTOMER".equals(snapshot.getString("role"))) return null;
        Map<String, Object> updates = new HashMap<>();
        updates.put("name", name.trim());
        updates.put("phone", phone.trim());
        updates.put("alternate_phone", alternatePhone);
        updates.put("address", address);
        updates.put("preferred_contact", preferredContact);
        FirestoreDatabase.await(reference.update(updates));
        return findById(userId);
    }

    public User updateCustomerProfileImage(int customerId, String imageUrl) throws SQLException {
        DocumentReference reference = FirestoreDatabase.document(USERS, customerId);
        DocumentSnapshot snapshot = FirestoreDatabase.await(reference.get());
        if (!snapshot.exists() || !"CUSTOMER".equals(snapshot.getString("role"))) return null;
        FirestoreDatabase.await(reference.update("profile_image_url", imageUrl));
        return findById(customerId);
    }

    public boolean changePassword(int userId, String currentPassword, String newPassword) throws SQLException {
        User user = findById(userId);
        if (user == null || !SecurityUtil.hashPassword(currentPassword, user.getSalt()).equals(user.getPasswordHash())) {
            return false;
        }
        String salt = SecurityUtil.generateSalt();
        FirestoreDatabase.await(FirestoreDatabase.document(USERS, userId).update(
                Map.of("password_hash", SecurityUtil.hashPassword(newPassword, salt), "salt", salt)));
        return true;
    }

    public List<User> findAllMothers() {
        return findByRole("MOTHER");
    }

    public List<User> findAllCustomers() {
        return findByRole("CUSTOMER");
    }

    public List<User> searchByPhone(String phoneQuery) {
        String needle = phoneQuery == null ? "" : phoneQuery.replaceAll("[\\s-]", "");
        List<User> result = new ArrayList<>();
        for (User user : allUsers()) {
            if (Objects.toString(user.getPhone(), "").replaceAll("[\\s-]", "").contains(needle)) {
                result.add(user);
            }
        }
        result.sort((a, b) -> {
            int roleOrder = Objects.toString(a.getRole(), "").compareTo(b.getRole());
            return roleOrder != 0 ? roleOrder : Integer.compare(b.getId(), a.getId());
        });
        return result.subList(0, Math.min(50, result.size()));
    }

    public SellerProfile getSellerProfile(int userId) {
        try {
            DocumentSnapshot snapshot = FirestoreDatabase.read(SELLER_PROFILES, userId);
            return snapshot.exists() ? mapSellerProfile(snapshot) : null;
        } catch (SQLException e) {
            throw databaseError("تعذر تحميل ملف الأم", e);
        }
    }

    public void saveSellerProfile(SellerProfile profile) throws SQLException {
        FirestoreDatabase.await(FirestoreDatabase.document(SELLER_PROFILES, profile.getUserId())
                .set(sellerProfileData(profile)));
    }

    public void updateMotherProfileImage(int sellerId, String imageUrl) throws SQLException {
        DocumentReference reference = FirestoreDatabase.document(SELLER_PROFILES, sellerId);
        DocumentSnapshot snapshot = FirestoreDatabase.await(reference.get());
        if (!snapshot.exists()) throw new SQLException("ملف الأم غير موجود");
        FirestoreDatabase.await(reference.update("profile_image_url", imageUrl));
    }

    public List<MotherLocation> findApprovedMotherLocations() {
        List<MotherLocation> result = new ArrayList<>();
        try {
            for (User user : allUsers()) {
                if (!"MOTHER".equals(user.getRole()) || !"ACTIVE".equals(user.getStatus())) continue;
                SellerProfile profile = getSellerProfile(user.getId());
                if (profile == null || !"APPROVED".equals(profile.getApprovalStatus())
                        || profile.getLatitude() == null || profile.getLongitude() == null) continue;
                result.add(new MotherLocation(user.getId(), user.getName(), profile.getRegion(),
                        profile.getLocationUrl(), profile.getLatitude(), profile.getLongitude()));
            }
            result.sort((a, b) -> Integer.compare(a.getId(), b.getId()));
            return result;
        } catch (RuntimeException e) {
            throw databaseError("تعذر تحميل مواقع الأمهات", e);
        }
    }

    public boolean toggleFollow(int customerId, int sellerId) throws SQLException {
        Firestore firestore = FirestoreDatabase.get();
        DocumentReference reference = firestore.collection("follows").document(customerId + "_" + sellerId);
        DocumentReference customerRef = FirestoreDatabase.document(USERS, customerId);
        DocumentReference sellerRef = FirestoreDatabase.document(USERS, sellerId);
        DocumentReference profileRef = FirestoreDatabase.document(SELLER_PROFILES, sellerId);
        return FirestoreDatabase.await(firestore.runTransaction(transaction -> {
            DocumentSnapshot customer = transaction.get(customerRef).get();
            DocumentSnapshot seller = transaction.get(sellerRef).get();
            DocumentSnapshot profile = transaction.get(profileRef).get();
            DocumentSnapshot follow = transaction.get(reference).get();
            if (!customer.exists() || !"CUSTOMER".equals(customer.getString("role"))
                    || !"ACTIVE".equals(customer.getString("status"))
                    || !seller.exists() || !"MOTHER".equals(seller.getString("role"))
                    || !"ACTIVE".equals(seller.getString("status"))
                    || !profile.exists() || !"APPROVED".equals(profile.getString("approval_status"))) {
                return false;
            }
            if (follow.exists()) {
                transaction.delete(reference);
                return false;
            }
            transaction.set(reference, Map.of(
                    "customer_id", customerId, "seller_id", sellerId, "created_at", new Date()));
            return true;
        }));
    }

    public List<MotherProfile> getFollowedMothers(int customerId) {
        List<MotherProfile> result = new ArrayList<>();
        try {
            QuerySnapshot query = FirestoreDatabase.await(FirestoreDatabase.get().collection("follows")
                    .whereEqualTo("customer_id", customerId).get());
            for (QueryDocumentSnapshot follow : query.getDocuments()) {
                int sellerId = number(follow.get("seller_id"));
                User user = findById(sellerId);
                SellerProfile profile = getSellerProfile(sellerId);
                if (user == null || profile == null) continue;
                MotherProfile mother = new MotherProfile();
                mother.setId(sellerId);
                mother.setName(user.getName());
                mother.setRegion(profile.getRegion());
                mother.setProfileImageUrl(profile.getProfileImageUrl());
                result.add(mother);
            }
            result.sort((a, b) -> Objects.toString(a.getName(), "")
                    .compareToIgnoreCase(Objects.toString(b.getName(), "")));
            return result;
        } catch (SQLException e) {
            throw databaseError("تعذر تحميل قائمة المتابعة", e);
        }
    }

    public void updateSellerApproval(int userId, String approvalStatus) throws SQLException {
        DocumentReference reference = FirestoreDatabase.document(SELLER_PROFILES, userId);
        DocumentSnapshot snapshot = FirestoreDatabase.await(reference.get());
        if (!snapshot.exists()) throw new SQLException("ملف الأم غير موجود");
        FirestoreDatabase.await(reference.update("approval_status", approvalStatus));
    }

    private List<User> findByRole(String role) {
        return allUsers().stream().filter(user -> role.equals(user.getRole()))
                .sorted((a, b) -> Integer.compare(b.getId(), a.getId())).toList();
    }

    private List<User> allUsers() {
        try {
            List<User> users = new ArrayList<>();
            for (QueryDocumentSnapshot snapshot : FirestoreDatabase.list(USERS)) users.add(mapUser(snapshot));
            return users;
        } catch (SQLException e) {
            throw databaseError("تعذر تحميل الحسابات", e);
        }
    }

    private User mapUser(DocumentSnapshot snapshot) {
        User user = new User(
                number(snapshot.get("id")),
                snapshot.getString("name"),
                snapshot.getString("email"),
                snapshot.getString("phone"),
                snapshot.getString("alternate_phone"),
                snapshot.getString("address"),
                snapshot.getString("preferred_contact"),
                snapshot.getString("password_hash"),
                snapshot.getString("salt"),
                snapshot.getString("role"),
                snapshot.getString("status"),
                FirestoreDatabase.timestamp(snapshot.get("created_at")));
        user.setProfileImageUrl(snapshot.getString("profile_image_url"));
        return user;
    }

    private SellerProfile mapSellerProfile(DocumentSnapshot snapshot) {
        SellerProfile profile = new SellerProfile(
                number(snapshot.get("user_id")),
                snapshot.getString("bio"),
                snapshot.getString("pickup_address"),
                snapshot.getString("region"),
                snapshot.getString("approval_status"),
                snapshot.getString("pickup_instructions"),
                snapshot.getString("location_url"),
                doubleValue(snapshot.get("latitude")),
                doubleValue(snapshot.get("longitude")));
        profile.setProfileImageUrl(snapshot.getString("profile_image_url"));
        return profile;
    }

    private Map<String, Object> sellerProfileData(SellerProfile profile) {
        Map<String, Object> data = new HashMap<>();
        data.put("user_id", profile.getUserId());
        data.put("bio", profile.getBio());
        data.put("pickup_address", profile.getPickupAddress());
        data.put("region", profile.getRegion());
        data.put("approval_status", profile.getApprovalStatus());
        data.put("pickup_instructions", profile.getPickupInstructions());
        data.put("location_url", profile.getLocationUrl());
        data.put("latitude", profile.getLatitude());
        data.put("longitude", profile.getLongitude());
        data.put("profile_image_url", profile.getProfileImageUrl());
        return data;
    }

    private static int number(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private static Double doubleValue(Object value) {
        return value instanceof Number number ? number.doubleValue() : null;
    }

    private static String normalizeName(String name) {
        return Objects.toString(name, "").trim().replaceAll("\\s+", " ").toLowerCase();
    }

    private static IllegalStateException databaseError(String message, Exception cause) {
        return new IllegalStateException(message, cause);
    }
}
