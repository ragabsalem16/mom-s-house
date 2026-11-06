package com.beitmama.dao;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.cloud.FirestoreClient;
import com.google.auth.oauth2.GoogleCredentials;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

public final class FirestoreDatabase {
    private static volatile Firestore firestore;

    private FirestoreDatabase() {}

    public static synchronized Firestore initialize() {
        if (firestore != null) return firestore;
        try {
            if (FirebaseApp.getApps().isEmpty()) {
                FirebaseOptions.Builder options = FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.getApplicationDefault());
                String projectId = System.getenv("FIREBASE_PROJECT_ID");
                if (projectId == null || projectId.isBlank()) {
                    projectId = System.getenv("GOOGLE_CLOUD_PROJECT");
                }
                if (projectId != null && !projectId.isBlank()) {
                    options.setProjectId(projectId);
                }
                FirebaseApp.initializeApp(options.build());
            }
            firestore = FirestoreClient.getFirestore();
            return firestore;
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException(
                    "تعذر الاتصال بـFirestore. تحقق من بيانات اعتماد Google Cloud ومعرّف المشروع.", e);
        }
    }

    public static Firestore get() {
        Firestore current = firestore;
        if (current == null) {
            throw new IllegalStateException("يجب تهيئة Firestore قبل استخدام قاعدة البيانات.");
        }
        return current;
    }

    public static <T> T await(ApiFuture<T> future) throws SQLException {
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SQLException("توقف تنفيذ طلب Firestore قبل اكتماله.", e);
        } catch (ExecutionException e) {
            throw new SQLException("فشل تنفيذ طلب Firestore.", e.getCause());
        }
    }

    public static DocumentReference document(String collection, int id) {
        return get().collection(collection).document(Integer.toString(id));
    }

    public static DocumentSnapshot read(String collection, int id) throws SQLException {
        return await(document(collection, id).get());
    }

    public static List<QueryDocumentSnapshot> list(String collection) throws SQLException {
        QuerySnapshot snapshot = await(get().collection(collection).get());
        return new ArrayList<>(snapshot.getDocuments());
    }

    public static int nextId(String collection) throws SQLException {
        DocumentReference counter = get().collection("system").document("counters");
        return await(get().runTransaction(transaction -> {
            DocumentSnapshot snapshot = transaction.get(counter).get();
            String field = collection + "_next_id";
            Long current = snapshot.getLong(field);
            int next = current == null ? 1 : Math.toIntExact(current + 1);
            transaction.set(counter, Map.of(field, next), com.google.cloud.firestore.SetOptions.merge());
            return next;
        }));
    }

    public static java.sql.Timestamp timestamp(Object value) {
        if (value instanceof java.util.Date date) return new java.sql.Timestamp(date.getTime());
        if (value instanceof com.google.cloud.Timestamp timestamp) {
            return new java.sql.Timestamp(timestamp.toDate().getTime());
        }
        return null;
    }
}
