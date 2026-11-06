package com.beitmama.dao;

import com.beitmama.model.Category;
import com.beitmama.model.Product;
import com.beitmama.model.SellerProfile;
import com.beitmama.model.User;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.QueryDocumentSnapshot;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ProductDao {
    private static final Set<String> SALES_STATUSES = Set.of("confirmed", "preparing", "ready", "completed");
    private final UserDao userDao = new UserDao();
    private final ReviewDao reviewDao = new ReviewDao();

    public List<Product> findAllAvailable() {
        List<Product> result = new ArrayList<>();
        try {
            for (QueryDocumentSnapshot snapshot : FirestoreDatabase.list("products")) {
                Product product = mapProduct(snapshot);
                if (!product.isAvailable() || product.getQuantityAvailable() < 1
                        || isExpired(product.getAvailableUntil())) continue;
                result.add(product);
            }
            populateDetails(result, true);
            result.removeIf(product -> product.getSellerName() == null || product.getSellerRegion() == null);
            result.sort(Comparator.comparingInt(Product::getId).reversed());
            return result;
        } catch (SQLException e) {
            throw databaseError("تعذر تحميل الوجبات المتاحة", e);
        }
    }

    public List<Product> findBySeller(int sellerId) {
        List<Product> result = new ArrayList<>();
        try {
            for (QueryDocumentSnapshot snapshot : FirestoreDatabase.await(FirestoreDatabase.get()
                    .collection("products").whereEqualTo("seller_id", sellerId).get()).getDocuments()) {
                Product product = mapProduct(snapshot);
                result.add(product);
            }
            populateDetails(result, false);
            result.sort(Comparator.comparingInt(Product::getId).reversed());
            return result;
        } catch (SQLException e) {
            throw databaseError("تعذر تحميل وجبات الأم", e);
        }
    }

    public List<Product> findAvailableBySeller(int sellerId) {
        return findBySeller(sellerId).stream()
                .filter(product -> product.isAvailable() && product.getQuantityAvailable() > 0
                        && !isExpired(product.getAvailableUntil()))
                .sorted(Comparator.comparingInt(Product::getSalesCount).reversed()
                        .thenComparing(Comparator.comparingInt(Product::getId).reversed()))
                .toList();
    }

    public List<Category> findCategories() {
        List<Category> result = new ArrayList<>();
        try {
            for (QueryDocumentSnapshot snapshot : FirestoreDatabase.list("categories")) {
                result.add(new Category(number(snapshot.get("id")), snapshot.getString("name")));
            }
            result.sort(Comparator.comparingInt(Category::getId));
            return result;
        } catch (SQLException e) {
            throw databaseError("تعذر تحميل تصنيفات الوجبات", e);
        }
    }

    public Product findById(int id) {
        try {
            DocumentSnapshot snapshot = FirestoreDatabase.read("products", id);
            if (!snapshot.exists()) return null;
            Product product = mapProduct(snapshot);
            populateDetails(List.of(product), false);
            return product;
        } catch (SQLException e) {
            throw databaseError("تعذر تحميل الوجبة", e);
        }
    }

    public boolean addProduct(Product product) {
        try {
            int id = FirestoreDatabase.nextId("products");
            product.setId(id);
            FirestoreDatabase.await(FirestoreDatabase.document("products", id).set(productData(product)));
            return true;
        } catch (SQLException e) {
            throw databaseError("تعذر إضافة الوجبة", e);
        }
    }

    public boolean updateProduct(Product product) {
        try {
            DocumentReference reference = FirestoreDatabase.document("products", product.getId());
            DocumentSnapshot snapshot = FirestoreDatabase.await(reference.get());
            if (!snapshot.exists() || number(snapshot.get("seller_id")) != product.getSellerId()) return false;
            FirestoreDatabase.await(reference.update(productData(product)));
            return true;
        } catch (SQLException e) {
            throw databaseError("تعذر تحديث الوجبة", e);
        }
    }

    public boolean deleteProduct(int id, int sellerId) {
        try {
            DocumentReference reference = FirestoreDatabase.document("products", id);
            DocumentSnapshot snapshot = FirestoreDatabase.await(reference.get());
            if (!snapshot.exists() || number(snapshot.get("seller_id")) != sellerId) return false;
            if (!FirestoreDatabase.await(FirestoreDatabase.get().collection("order_items")
                    .whereEqualTo("product_id", id).limit(1).get()).isEmpty()) return false;
            FirestoreDatabase.await(reference.delete());
            return true;
        } catch (SQLException e) {
            throw databaseError("تعذر حذف الوجبة", e);
        }
    }

    private void populateDetails(List<Product> products, boolean approvedMothersOnly) throws SQLException {
        Map<Integer, Category> categories = new HashMap<>();
        for (Category category : findCategories()) categories.put(category.getId(), category);
        Map<Integer, Integer> salesCounts = salesCounts();
        Map<Integer, User> sellers = new HashMap<>();
        Map<Integer, SellerProfile> profiles = new HashMap<>();
        Map<Integer, ReviewDao.MotherRatingSummary> ratings = new HashMap<>();
        for (Product product : products) {
            int sellerId = product.getSellerId();
            if (!sellers.containsKey(sellerId)) sellers.put(sellerId, userDao.findById(sellerId));
            if (!profiles.containsKey(sellerId)) {
                SellerProfile profile = approvedMothersOnly
                        ? userDao.findApprovedMotherProfile(sellerId) : userDao.getSellerProfile(sellerId);
                profiles.put(sellerId, profile);
            }
            if (!ratings.containsKey(sellerId)) ratings.put(sellerId, reviewDao.motherRatingSummary(sellerId));
            User seller = sellers.get(sellerId);
            SellerProfile profile = profiles.get(sellerId);
            ReviewDao.MotherRatingSummary rating = ratings.get(sellerId);
            Category category = categories.get(product.getCategoryId());
            product.setSellerName(seller == null ? null : seller.getName());
            product.setSellerRegion(profile == null ? null : profile.getRegion());
            product.setCategoryName(category == null ? null : category.getName());
            product.setSellerRating(rating.average());
            product.setSellerRatingCount(rating.ratingCount());
            product.setSellerLikeCount(rating.likeCount());
            product.setSalesCount(salesCounts.getOrDefault(product.getId(), 0));
        }
    }

    private Map<Integer, Integer> salesCounts() throws SQLException {
        Map<Integer, String> orderStatuses = new HashMap<>();
        for (QueryDocumentSnapshot order : FirestoreDatabase.list("orders")) {
            orderStatuses.put(number(order.get("id")), order.getString("status"));
        }
        Map<Integer, Integer> counts = new HashMap<>();
        for (QueryDocumentSnapshot item : FirestoreDatabase.list("order_items")) {
            if (!SALES_STATUSES.contains(orderStatuses.get(number(item.get("order_id"))))) continue;
            counts.merge(number(item.get("product_id")), number(item.get("quantity")), Integer::sum);
        }
        return counts;
    }

    private Map<String, Object> productData(Product product) {
        Map<String, Object> data = new HashMap<>();
        data.put("id", product.getId());
        data.put("seller_id", product.getSellerId());
        data.put("category_id", product.getCategoryId());
        data.put("name", product.getName());
        data.put("description", product.getDescription());
        data.put("price", product.getPrice() == null ? null : product.getPrice().doubleValue());
        BigDecimal customerPrice = product.getCustomerPrice() == null
                ? product.getPrice() : product.getCustomerPrice();
        data.put("customer_price", customerPrice == null ? null : customerPrice.doubleValue());
        data.put("quantity_available", product.getQuantityAvailable());
        data.put("is_available", product.isAvailable());
        data.put("image_url", product.getImageUrl());
        data.put("available_until", product.getAvailableUntil());
        data.put("ingredients", product.getIngredients());
        data.put("preparation_minutes", product.getPreparationMinutes());
        data.put("freshly_cooked", product.isFreshlyCooked());
        return data;
    }

    private Product mapProduct(DocumentSnapshot snapshot) {
        Product product = new Product();
        product.setId(number(snapshot.get("id")));
        product.setSellerId(number(snapshot.get("seller_id")));
        product.setCategoryId(number(snapshot.get("category_id")));
        product.setName(snapshot.getString("name"));
        product.setDescription(snapshot.getString("description"));
        product.setPrice(decimal(snapshot.get("price")));
        product.setCustomerPrice(decimal(snapshot.get("customer_price")));
        product.setQuantityAvailable(number(snapshot.get("quantity_available")));
        product.setAvailable(Boolean.TRUE.equals(snapshot.getBoolean("is_available")));
        product.setImageUrl(snapshot.getString("image_url"));
        product.setAvailableUntil(snapshot.getString("available_until"));
        product.setIngredients(snapshot.getString("ingredients"));
        product.setPreparationMinutes(number(snapshot.get("preparation_minutes")));
        product.setFreshlyCooked(Boolean.TRUE.equals(snapshot.getBoolean("freshly_cooked")));
        return product;
    }

    private boolean isExpired(String availableUntil) {
        if (availableUntil == null || availableUntil.isBlank()) return false;
        try {
            return LocalDate.parse(availableUntil).isBefore(LocalDate.now());
        } catch (RuntimeException e) {
            throw new IllegalStateException("تاريخ انتهاء توفر الوجبة غير صالح في Firestore.", e);
        }
    }

    private int number(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private BigDecimal decimal(Object value) {
        if (value instanceof BigDecimal decimal) return decimal;
        if (value instanceof Number number) return BigDecimal.valueOf(number.doubleValue());
        return BigDecimal.ZERO;
    }

    private IllegalStateException databaseError(String message, Exception cause) {
        return new IllegalStateException(message, cause);
    }
}
