package com.beitmama.dao;

import com.beitmama.model.Review;
import com.beitmama.model.User;
import com.beitmama.util.SecurityUtil;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.cloud.firestore.QueryDocumentSnapshot;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ReviewDao {
    public record MotherRatingSummary(double average, int ratingCount, int likeCount) {}

    public boolean addReview(int orderId, int customerId, int sellerId, int rating, String comment) {
        validateRating(rating);
        Firestore firestore = FirestoreDatabase.get();
        DocumentReference orderRef = FirestoreDatabase.document("orders", orderId);
        DocumentReference reviewRef = firestore.collection("reviews").document(Integer.toString(orderId));
        try {
            return FirestoreDatabase.await(firestore.runTransaction(transaction -> {
                DocumentSnapshot order = transaction.get(orderRef).get();
                DocumentSnapshot existing = transaction.get(reviewRef).get();
                if (!order.exists() || existing.exists()
                        || number(order.get("customer_id")) != customerId
                        || number(order.get("seller_id")) != sellerId
                        || !"completed".equals(order.getString("status"))) return false;
                int id = number(order.get("id"));
                Map<String, Object> review = reviewData(id, orderId, customerId, sellerId, rating, comment);
                transaction.set(reviewRef, review);
                return true;
            }));
        } catch (SQLException e) {
            throw databaseError("تعذر حفظ تقييم الطلب", e);
        }
    }

    public List<Review> findBySeller(int sellerId) {
        List<Review> result = new ArrayList<>();
        try {
            QuerySnapshot query = FirestoreDatabase.await(FirestoreDatabase.get().collection("reviews")
                    .whereEqualTo("seller_id", sellerId).get());
            for (QueryDocumentSnapshot snapshot : query.getDocuments()) {
                Review review = mapReview(snapshot);
                User customer = new UserDao().findById(review.getCustomerId());
                review.setCustomerName(customer == null ? null : customer.getName());
                result.add(review);
            }
            result.sort(Comparator.comparingInt(Review::getId).reversed());
            return result;
        } catch (SQLException e) {
            throw databaseError("تعذر تحميل التقييمات", e);
        }
    }

    public boolean addMotherReview(int customerId, int sellerId, int rating, String comment) {
        validateRating(rating);
        if (comment == null || comment.isBlank() || comment.trim().length() > 1000) {
            throw new IllegalArgumentException("اكتب تعليقاً لا يزيد عن 1000 حرف");
        }
        User mother = new UserDao().findById(sellerId);
        if (mother == null || !"MOTHER".equals(mother.getRole()) || !"ACTIVE".equals(mother.getStatus())) {
            return false;
        }
        try {
            int id = FirestoreDatabase.nextId("mother_reviews");
            Map<String, Object> data = reviewData(id, 0, customerId, sellerId, rating, comment.trim());
            FirestoreDatabase.await(FirestoreDatabase.document("mother_reviews", id).set(data));
            return true;
        } catch (SQLException e) {
            throw databaseError("تعذر حفظ تقييم الأم", e);
        }
    }

    public List<Review> findLatestMotherReviews(int sellerId) {
        List<Review> result = new ArrayList<>();
        try {
            appendSellerReviews(result, "mother_reviews", sellerId);
            appendSellerReviews(result, "reviews", sellerId);
            result.sort(Comparator.comparing(Review::getCreatedAt,
                    Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(Comparator.comparingInt(Review::getId).reversed()));
            return result.subList(0, Math.min(5, result.size()));
        } catch (SQLException e) {
            throw databaseError("تعذر تحميل أحدث التقييمات", e);
        }
    }

    public double averageMotherRating(int sellerId) {
        return motherRatingSummary(sellerId).average();
    }

    public int motherRatingCount(int sellerId) {
        return motherRatingSummary(sellerId).ratingCount();
    }

    public MotherRatingSummary motherRatingSummary(int sellerId) {
        int total = 0;
        int count = 0;
        int likes = 0;
        try {
            for (String collection : List.of("mother_reviews", "reviews")) {
                QuerySnapshot query = FirestoreDatabase.await(FirestoreDatabase.get().collection(collection)
                        .whereEqualTo("seller_id", sellerId).get());
                for (QueryDocumentSnapshot snapshot : query.getDocuments()) {
                    total += number(snapshot.get("rating"));
                    count++;
                }
            }
            QuerySnapshot likesQuery = FirestoreDatabase.await(FirestoreDatabase.get().collection("mother_likes")
                    .whereEqualTo("seller_id", sellerId).get());
            for (QueryDocumentSnapshot like : likesQuery.getDocuments()) {
                total += 5;
                count++;
                likes++;
            }
        } catch (SQLException e) {
            throw databaseError("تعذر تحميل تقييم الأم", e);
        }
        return new MotherRatingSummary(count == 0 ? 0 : (double) total / count, count, likes);
    }

    public int motherLikeCount(int sellerId) {
        return motherRatingSummary(sellerId).likeCount();
    }

    public boolean customerLikedMother(int customerId, int sellerId) {
        try {
            return FirestoreDatabase.await(likeReference(customerId, sellerId).get()).exists();
        } catch (SQLException e) {
            throw databaseError("تعذر تحميل حالة الإعجاب", e);
        }
    }

    public boolean toggleMotherLike(int customerId, int sellerId) {
        Firestore firestore = FirestoreDatabase.get();
        DocumentReference reference = likeReference(customerId, sellerId);
        try {
            return FirestoreDatabase.await(firestore.runTransaction(transaction -> {
                DocumentSnapshot existing = transaction.get(reference).get();
                if (existing.exists()) {
                    transaction.delete(reference);
                    return false;
                }
                Map<String, Object> like = new HashMap<>();
                like.put("customer_id", customerId);
                like.put("seller_id", sellerId);
                like.put("created_at", new Date());
                transaction.set(reference, like);
                return true;
            }));
        } catch (SQLException e) {
            throw databaseError("تعذر تحديث إعجاب الأم", e);
        }
    }

    private void appendSellerReviews(List<Review> target, String collection, int sellerId) throws SQLException {
        QuerySnapshot query = FirestoreDatabase.await(FirestoreDatabase.get().collection(collection)
                .whereEqualTo("seller_id", sellerId).get());
        for (QueryDocumentSnapshot snapshot : query.getDocuments()) {
            Review review = mapReview(snapshot);
            User customer = new UserDao().findById(review.getCustomerId());
            review.setCustomerName(customer == null ? null : customer.getName());
            target.add(review);
        }
    }

    private Map<String, Object> reviewData(int id, int orderId, int customerId, int sellerId, int rating,
                                           String comment) {
        Map<String, Object> data = new HashMap<>();
        data.put("id", id);
        data.put("order_id", orderId);
        data.put("customer_id", customerId);
        data.put("seller_id", sellerId);
        data.put("rating", rating);
        data.put("comment", comment);
        data.put("created_at", new Date());
        return data;
    }

    private Review mapReview(DocumentSnapshot snapshot) {
        Review review = new Review();
        review.setId(number(snapshot.get("id")));
        review.setOrderId(number(snapshot.get("order_id")));
        review.setCustomerId(number(snapshot.get("customer_id")));
        review.setSellerId(number(snapshot.get("seller_id")));
        review.setRating(number(snapshot.get("rating")));
        review.setComment(snapshot.getString("comment"));
        review.setCreatedAt(FirestoreDatabase.timestamp(snapshot.get("created_at")));
        return review;
    }

    private DocumentReference likeReference(int customerId, int sellerId) {
        return FirestoreDatabase.get().collection("mother_likes")
                .document(customerId + "_" + sellerId);
    }

    private void validateRating(int rating) {
        if (rating < 1 || rating > 5) throw new IllegalArgumentException("التقييم يجب أن يكون بين نجمة و5 نجوم");
    }

    private int number(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private IllegalStateException databaseError(String message, Exception cause) {
        return new IllegalStateException(message, cause);
    }
}
