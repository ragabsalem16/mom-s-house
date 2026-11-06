package com.beitmama.dao;

import com.beitmama.util.SecurityUtil;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;

import java.sql.SQLException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class DatabaseManager {

    public static void initializeDatabase() {
        if (Boolean.parseBoolean(System.getenv("BEITMAMA_PRODUCTION"))) {
            String adminPassword = System.getenv("BEITMAMA_ADMIN_PASSWORD");
            if (adminPassword == null || adminPassword.length() < 16) {
                throw new IllegalStateException(
                        "BEITMAMA_ADMIN_PASSWORD must contain at least 16 characters in production");
            }
        }
        Firestore firestore = FirestoreDatabase.initialize();
        try {
            seedAdmin(firestore);
            seedCategories(firestore);
        } catch (SQLException e) {
            throw new IllegalStateException("تعذر تهيئة بيانات بيت ماما في Firestore.", e);
        }
    }

    private static void seedAdmin(Firestore firestore) throws SQLException {
        String email = "admin@beitmama.com";
        DocumentReference reference = firestore.collection("users").document("1");
        DocumentSnapshot existing = FirestoreDatabase.await(reference.get());
        if (!existing.exists()) {
            String password = System.getenv("BEITMAMA_ADMIN_PASSWORD");
            if (password == null || password.isBlank()) password = "admin123";
            String salt = SecurityUtil.generateSalt();
            Map<String, Object> admin = new HashMap<>();
            admin.put("id", 1);
            admin.put("name", "مدير النظام");
            admin.put("email", email);
            admin.put("phone", "01000000000");
            admin.put("alternate_phone", null);
            admin.put("address", null);
            admin.put("preferred_contact", "phone");
            admin.put("profile_image_url", null);
            admin.put("password_hash", SecurityUtil.hashPassword(password, salt));
            admin.put("salt", salt);
            admin.put("role", "ADMIN");
            admin.put("status", "ACTIVE");
            admin.put("created_at", new Date());
            FirestoreDatabase.await(reference.set(admin));
        }
        String emailKey = SecurityUtil.hash256(email.trim().toLowerCase());
        FirestoreDatabase.await(firestore.collection("user_emails").document(emailKey)
                .set(Map.of("user_id", 1)));
        int maxUserId = 1;
        for (com.google.cloud.firestore.QueryDocumentSnapshot user : FirestoreDatabase.list("users")) {
            Object id = user.get("id");
            if (id instanceof Number number) maxUserId = Math.max(maxUserId, number.intValue());
        }
        DocumentSnapshot counters = FirestoreDatabase.await(
                firestore.collection("system").document("counters").get());
        Long currentUserCounter = counters.getLong("users_next_id");
        if (currentUserCounter != null) maxUserId = Math.max(maxUserId, Math.toIntExact(currentUserCounter));
        FirestoreDatabase.await(firestore.collection("system").document("counters").set(
                Map.of("users_next_id", maxUserId), com.google.cloud.firestore.SetOptions.merge()));
    }

    private static void seedCategories(Firestore firestore) throws SQLException {
        String[] categories = {
                "محاشي ومحمر", "طواجن ومكرونات", "مخبوزات وفطائر", "حلويات شرقية", "المشروبات",
                "الأرز والمكرونة", "اللحوم", "الدواجن", "السلطات", "الحلويات", "الشوربات"
        };
        for (int i = 0; i < categories.length; i++) {
            DocumentReference reference = firestore.collection("categories").document(Integer.toString(i + 1));
            if (!FirestoreDatabase.await(reference.get()).exists()) {
                FirestoreDatabase.await(reference.set(Map.of("id", i + 1, "name", categories[i])));
            }
        }
        DocumentSnapshot counters = FirestoreDatabase.await(
                firestore.collection("system").document("counters").get());
        Long currentCategoryCounter = counters.getLong("categories_next_id");
        int nextId = currentCategoryCounter == null
                ? categories.length : Math.max(categories.length, Math.toIntExact(currentCategoryCounter));
        FirestoreDatabase.await(firestore.collection("system").document("counters").set(
                Map.of("categories_next_id", nextId), com.google.cloud.firestore.SetOptions.merge()));
    }
}
