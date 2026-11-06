package com.beitmama.dao;

import com.beitmama.model.Order;
import com.beitmama.model.OrderItem;
import com.beitmama.model.SellerProfile;
import com.beitmama.model.User;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class OrderDao {
    private static final Set<String> REVENUE_EXCLUDED_STATUSES = Set.of("rejected", "cancelled");
    private final UserDao userDao = new UserDao();

    public Order createOrder(int customerId, int sellerId, String pickupTime, String proposedDeliveryTime,
                             String deliveryMethod, String deliveryAddress, String customerName,
                             String customerPhone, String customerAltPhone, String customerAddress,
                             String contactMethod, String paymentMethod, List<OrderItem> items) throws Exception {
        validateOrder(deliveryMethod, deliveryAddress, customerPhone, contactMethod, paymentMethod,
                pickupTime, items);
        Map<Integer, Integer> quantities = new LinkedHashMap<>();
        for (OrderItem item : items) quantities.merge(item.getProductId(), item.getQuantity(), Integer::sum);
        items.clear();
        quantities.forEach((productId, quantity) -> {
            OrderItem item = new OrderItem();
            item.setProductId(productId);
            item.setQuantity(quantity);
            items.add(item);
        });

        int orderId = FirestoreDatabase.nextId("orders");
        Firestore firestore = FirestoreDatabase.get();
        DocumentReference orderRef = FirestoreDatabase.document("orders", orderId);
        List<DocumentReference> productRefs = items.stream()
                .map(item -> FirestoreDatabase.document("products", item.getProductId())).toList();
        try {
            FirestoreDatabase.await(firestore.runTransaction(transaction -> {
                List<DocumentSnapshot> products = new ArrayList<>();
                for (DocumentReference reference : productRefs) {
                    products.add(transaction.get(reference).get());
                }

                BigDecimal totalAmount = BigDecimal.ZERO;
                List<Map<String, Object>> itemData = new ArrayList<>();
                for (int i = 0; i < items.size(); i++) {
                    OrderItem item = items.get(i);
                    DocumentSnapshot product = products.get(i);
                    if (!product.exists() || number(product.get("seller_id")) != sellerId
                            || isExpired(product.getString("available_until"))) {
                        throw new IllegalArgumentException(
                                "الوجبة غير موجودة أو لا تتبع لهذه الأم: " + item.getProductId());
                    }
                    if (!Boolean.TRUE.equals(product.getBoolean("is_available"))) {
                        throw new IllegalArgumentException("الوجبة غير متاحة للطلب حالياً");
                    }
                    int stock = number(product.get("quantity_available"));
                    if (stock < item.getQuantity()) {
                        throw new IllegalArgumentException("الكمية المطلوبة (" + item.getQuantity()
                                + ") أكبر من المتاح بالمخزن (" + stock + ")");
                    }
                    BigDecimal sellerPrice = decimal(product.get("price"));
                    BigDecimal customerPrice = decimal(product.get("customer_price"));
                    if (customerPrice.compareTo(BigDecimal.ZERO) <= 0) customerPrice = sellerPrice;
                    item.setUnitPrice(customerPrice);
                    item.setSubtotal(customerPrice.multiply(BigDecimal.valueOf(item.getQuantity())));
                    totalAmount = totalAmount.add(item.getSubtotal());

                    Map<String, Object> savedItem = new HashMap<>();
                    savedItem.put("id", i + 1);
                    savedItem.put("order_id", orderId);
                    savedItem.put("product_id", item.getProductId());
                    savedItem.put("product_name", product.getString("name"));
                    savedItem.put("category_id", number(product.get("category_id")));
                    savedItem.put("quantity", item.getQuantity());
                    savedItem.put("unit_price", item.getUnitPrice().doubleValue());
                    savedItem.put("seller_unit_price", sellerPrice.doubleValue());
                    savedItem.put("subtotal", item.getSubtotal().doubleValue());
                    itemData.add(savedItem);
                }

                Map<String, Object> order = new HashMap<>();
                order.put("id", orderId);
                order.put("customer_id", customerId);
                order.put("seller_id", sellerId);
                order.put("status", "pending");
                order.put("total_amount", totalAmount.doubleValue());
                order.put("payment_method", paymentMethod);
                order.put("pickup_time", pickupTime);
                order.put("proposed_delivery_time", proposedDeliveryTime);
                order.put("delivery_method", deliveryMethod);
                order.put("delivery_address", deliveryAddress);
                order.put("customer_name_snapshot", customerName);
                order.put("customer_phone_snapshot", customerPhone);
                order.put("customer_alternate_phone_snapshot", customerAltPhone);
                order.put("customer_address_snapshot", customerAddress);
                order.put("contact_method", contactMethod);
                order.put("created_at", new Date());

                transaction.set(orderRef, order);
                for (int i = 0; i < items.size(); i++) {
                    DocumentReference productRef = productRefs.get(i);
                    transaction.update(productRef, "quantity_available",
                            number(products.get(i).get("quantity_available")) - items.get(i).getQuantity());
                    transaction.set(firestore.collection("order_items")
                            .document(orderId + "_" + items.get(i).getProductId()), itemData.get(i));
                }
                return null;
            }));
            return findById(orderId);
        } catch (SQLException e) {
            throw databaseError("تعذر إنشاء الطلب", e);
        }
    }

    public Order findById(int orderId) {
        try {
            DocumentSnapshot snapshot = FirestoreDatabase.read("orders", orderId);
            return snapshot.exists() ? mapOrder(snapshot, true) : null;
        } catch (SQLException e) {
            throw databaseError("تعذر تحميل الطلب", e);
        }
    }

    public List<Order> findByCustomer(int customerId) {
        return findOrders("customer_id", customerId);
    }

    public List<Order> findBySeller(int sellerId) {
        return findOrders("seller_id", sellerId);
    }

    public List<Order> findAllOrders() {
        try {
            List<Order> orders = new ArrayList<>();
            for (QueryDocumentSnapshot snapshot : FirestoreDatabase.list("orders")) {
                orders.add(mapOrder(snapshot, true));
            }
            orders.sort(Comparator.comparingInt(Order::getId).reversed());
            return orders;
        } catch (SQLException e) {
            throw databaseError("تعذر تحميل الطلبات", e);
        }
    }

    public boolean updateOrderStatus(int orderId, String newStatus, int actorId, String actorRole) {
        Firestore firestore = FirestoreDatabase.get();
        DocumentReference orderRef = FirestoreDatabase.document("orders", orderId);
        try {
            return FirestoreDatabase.await(firestore.runTransaction(transaction -> {
                DocumentSnapshot order = transaction.get(orderRef).get();
                if (!order.exists() || (!"ADMIN".equals(actorRole)
                        && number(order.get("seller_id")) != actorId)) return false;
                String currentStatus = order.getString("status");
                if (!isValidTransition(currentStatus, newStatus)) return false;

                List<QueryDocumentSnapshot> items = List.of();
                List<DocumentSnapshot> products = List.of();
                if ("rejected".equals(newStatus) || "cancelled".equals(newStatus)) {
                    items = transaction.get(firestore.collection("order_items")
                            .whereEqualTo("order_id", orderId)).get().getDocuments();
                    List<DocumentSnapshot> loadedProducts = new ArrayList<>();
                    for (QueryDocumentSnapshot item : items) {
                        loadedProducts.add(transaction.get(FirestoreDatabase.document(
                                "products", number(item.get("product_id")))).get());
                    }
                    products = loadedProducts;
                }

                transaction.update(orderRef, "status", newStatus);
                for (int i = 0; i < items.size(); i++) {
                    DocumentSnapshot product = products.get(i);
                    if (!product.exists()) continue;
                    int restoredStock = number(product.get("quantity_available"))
                            + number(items.get(i).get("quantity"));
                    transaction.update(product.getReference(), "quantity_available", restoredStock);
                }
                return true;
            }));
        } catch (SQLException e) {
            throw databaseError("تعذر تحديث حالة الطلب", e);
        }
    }

    public String getAdminStatsJson() {
        try {
            int customers = 0;
            int mothers = 0;
            for (QueryDocumentSnapshot user : FirestoreDatabase.list("users")) {
                if ("CUSTOMER".equals(user.getString("role"))) customers++;
                if ("MOTHER".equals(user.getString("role"))) mothers++;
            }

            List<QueryDocumentSnapshot> orders = FirestoreDatabase.list("orders");
            List<QueryDocumentSnapshot> orderItems = FirestoreDatabase.list("order_items");
            Map<Integer, String> statuses = new HashMap<>();
            Map<Integer, BigDecimal> completedRevenue = new HashMap<>();
            int pending = 0;
            BigDecimal revenue = BigDecimal.ZERO;
            BigDecimal ordersRevenue = BigDecimal.ZERO;
            for (QueryDocumentSnapshot order : orders) {
                int id = number(order.get("id"));
                String status = order.getString("status");
                statuses.put(id, status);
                BigDecimal amount = decimal(order.get("total_amount"));
                if ("completed".equals(status)) revenue = revenue.add(amount);
                if (!REVENUE_EXCLUDED_STATUSES.contains(status)) {
                    ordersRevenue = ordersRevenue.add(amount);
                }
                if ("pending".equals(status)) pending++;
            }

            Map<Integer, BigDecimal> productPrices = new HashMap<>();
            for (QueryDocumentSnapshot product : FirestoreDatabase.list("products")) {
                productPrices.put(number(product.get("id")), decimal(product.get("price")));
            }
            BigDecimal platformProfit = BigDecimal.ZERO;
            BigDecimal mothersNetProfit = BigDecimal.ZERO;
            for (QueryDocumentSnapshot item : orderItems) {
                if (REVENUE_EXCLUDED_STATUSES.contains(statuses.get(number(item.get("order_id"))))) continue;
                BigDecimal sellerPrice = item.contains("seller_unit_price")
                        ? decimal(item.get("seller_unit_price"))
                        : productPrices.getOrDefault(number(item.get("product_id")), BigDecimal.ZERO);
                BigDecimal sellerSubtotal = sellerPrice.multiply(
                        BigDecimal.valueOf(number(item.get("quantity"))));
                mothersNetProfit = mothersNetProfit.add(sellerSubtotal);
                platformProfit = platformProfit.add(decimal(item.get("subtotal")).subtract(sellerSubtotal));
            }

            return "{\"customers\":" + customers + ",\"mothers\":" + mothers
                    + ",\"orders\":" + orders.size() + ",\"revenue\":" + jsonNumber(revenue)
                    + ",\"orders_revenue\":" + jsonNumber(ordersRevenue)
                    + ",\"platform_profit\":" + jsonNumber(platformProfit)
                    + ",\"mothers_net_profit\":" + jsonNumber(mothersNetProfit)
                    + ",\"platform_due_total\":" + jsonNumber(platformProfit)
                    + ",\"pending\":" + pending + "}";
        } catch (SQLException e) {
            throw databaseError("تعذر تحميل إحصائيات الإدارة", e);
        }
    }

    private List<Order> findOrders(String field, int value) {
        try {
            List<Order> orders = new ArrayList<>();
            QuerySnapshot query = FirestoreDatabase.await(
                    FirestoreDatabase.get().collection("orders").whereEqualTo(field, value).get());
            for (QueryDocumentSnapshot snapshot : query.getDocuments()) {
                orders.add(mapOrder(snapshot, true));
            }
            orders.sort(Comparator.comparingInt(Order::getId).reversed());
            return orders;
        } catch (SQLException e) {
            throw databaseError("تعذر تحميل الطلبات", e);
        }
    }

    private Order mapOrder(DocumentSnapshot snapshot, boolean includeItems) throws SQLException {
        Order order = new Order();
        order.setId(number(snapshot.get("id")));
        order.setCustomerId(number(snapshot.get("customer_id")));
        order.setSellerId(number(snapshot.get("seller_id")));
        order.setStatus(snapshot.getString("status"));
        order.setTotalAmount(decimal(snapshot.get("total_amount")));
        order.setPaymentMethod(snapshot.getString("payment_method"));
        order.setPickupTime(snapshot.getString("pickup_time"));
        order.setProposedDeliveryTime(snapshot.getString("proposed_delivery_time"));
        order.setDeliveryMethod(snapshot.getString("delivery_method"));
        order.setDeliveryAddress(snapshot.getString("delivery_address"));
        order.setContactMethod(snapshot.getString("contact_method"));
        order.setCreatedAt(FirestoreDatabase.timestamp(snapshot.get("created_at")));

        User customer = userDao.findById(order.getCustomerId());
        User seller = userDao.findById(order.getSellerId());
        order.setCustomerName(firstNonBlank(snapshot.getString("customer_name_snapshot"),
                customer == null ? null : customer.getName()));
        order.setCustomerPhone(firstNonBlank(snapshot.getString("customer_phone_snapshot"),
                customer == null ? null : customer.getPhone()));
        order.setSellerName(seller == null ? null : seller.getName());
        SellerProfile profile = userDao.getSellerProfile(order.getSellerId());
        order.setSellerAddress(profile == null ? null : profile.getPickupAddress());
        if (includeItems) order.setItems(findOrderItems(order.getId()));
        return order;
    }

    private List<OrderItem> findOrderItems(int orderId) throws SQLException {
        List<OrderItem> items = new ArrayList<>();
        QuerySnapshot query = FirestoreDatabase.await(FirestoreDatabase.get().collection("order_items")
                .whereEqualTo("order_id", orderId).get());
        for (QueryDocumentSnapshot snapshot : query.getDocuments()) {
            OrderItem item = new OrderItem();
            item.setId(number(snapshot.get("id")));
            item.setOrderId(orderId);
            item.setProductId(number(snapshot.get("product_id")));
            item.setProductName(snapshot.getString("product_name"));
            item.setCategoryId(number(snapshot.get("category_id")));
            item.setQuantity(number(snapshot.get("quantity")));
            item.setUnitPrice(decimal(snapshot.get("unit_price")));
            item.setSubtotal(decimal(snapshot.get("subtotal")));
            DocumentSnapshot category = FirestoreDatabase.read("categories", item.getCategoryId());
            item.setCategoryName(category.exists() ? category.getString("name") : null);
            items.add(item);
        }
        items.sort(Comparator.comparingInt(OrderItem::getId));
        return items;
    }

    private void validateOrder(String deliveryMethod, String deliveryAddress, String customerPhone,
                               String contactMethod, String paymentMethod, String pickupTime,
                               List<OrderItem> items) {
        if (!"pickup".equals(deliveryMethod) && !"delivery".equals(deliveryMethod)) {
            throw new IllegalArgumentException("طريقة الاستلام غير صحيحة");
        }
        if ("delivery".equals(deliveryMethod) && (deliveryAddress == null || deliveryAddress.isBlank())) {
            throw new IllegalArgumentException("أدخل عنوان التوصيل");
        }
        if (customerPhone == null || customerPhone.isBlank() || customerPhone.length() > 30) {
            throw new IllegalArgumentException("أدخل رقم هاتف صحيحاً");
        }
        if (!"phone".equals(contactMethod) && !"whatsapp".equals(contactMethod)) {
            throw new IllegalArgumentException("طريقة التواصل غير صحيحة");
        }
        String expectedPayment = "delivery".equals(deliveryMethod) ? "cash_on_delivery" : "cash_on_pickup";
        if (!expectedPayment.equals(paymentMethod)) {
            throw new IllegalArgumentException("طريقة الدفع لا تتوافق مع طريقة الاستلام");
        }
        if (items == null || items.isEmpty()) throw new IllegalArgumentException("السلة فارغة");
        for (OrderItem item : items) {
            if (item.getProductId() <= 0 || item.getQuantity() <= 0) {
                throw new IllegalArgumentException("الوجبة والكمية يجب أن تكونا صحيحتين");
            }
        }
        if (pickupTime == null || pickupTime.isBlank() || pickupTime.length() > 100) {
            throw new IllegalArgumentException("أدخل موعداً صحيحاً");
        }
    }

    private boolean isValidTransition(String currentStatus, String newStatus) {
        if (currentStatus == null || newStatus == null) return false;
        return switch (currentStatus) {
            case "pending" -> "confirmed".equals(newStatus) || "rejected".equals(newStatus);
            case "confirmed" -> "preparing".equals(newStatus) || "cancelled".equals(newStatus);
            case "preparing" -> "ready".equals(newStatus);
            case "ready" -> "completed".equals(newStatus);
            default -> false;
        };
    }

    private boolean isExpired(String availableUntil) {
        if (availableUntil == null || availableUntil.isBlank()) return false;
        try {
            return LocalDate.parse(availableUntil).isBefore(LocalDate.now());
        } catch (RuntimeException e) {
            throw new IllegalStateException("تاريخ انتهاء توفر الوجبة غير صالح في Firestore.", e);
        }
    }

    private String firstNonBlank(String preferred, String fallback) {
        return preferred == null || preferred.isBlank() ? fallback : preferred;
    }

    private int number(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private BigDecimal decimal(Object value) {
        if (value instanceof BigDecimal decimal) return decimal;
        if (value instanceof Number number) return BigDecimal.valueOf(number.doubleValue());
        return BigDecimal.ZERO;
    }

    private String jsonNumber(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private IllegalStateException databaseError(String message, Exception cause) {
        return new IllegalStateException(message, cause);
    }
}
