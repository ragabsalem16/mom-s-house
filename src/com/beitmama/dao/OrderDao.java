package com.beitmama.dao;

import com.beitmama.model.Order;
import com.beitmama.model.OrderItem;

import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class OrderDao {

    /**
     * إنشاء طلب جديد والتحقق من الكميات المتوفرة وتحديث المخزون بشكل ترانزاكشن متسق
     */
    public synchronized Order createOrder(int customerId, int sellerId, String pickupTime, String deliveryMethod,
                                          String deliveryAddress, String customerPhone, String contactMethod,
                                          String paymentMethod, List<OrderItem> items) throws Exception {
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
        if (items.isEmpty()) throw new IllegalArgumentException("السلة فارغة");
        Map<Integer, Integer> quantities = new LinkedHashMap<>();
        for (OrderItem item : items) {
            if (item.getProductId() <= 0 || item.getQuantity() <= 0) {
                throw new IllegalArgumentException("الوجبة والكمية يجب أن تكونا صحيحتين");
            }
            quantities.merge(item.getProductId(), item.getQuantity(), Integer::sum);
        }
        items.clear();
        quantities.forEach((productId, quantity) -> {
            OrderItem item = new OrderItem();
            item.setProductId(productId);
            item.setQuantity(quantity);
            items.add(item);
        });
        if (pickupTime == null || pickupTime.isBlank() || pickupTime.length() > 100) {
            throw new IllegalArgumentException("أدخل موعداً صحيحاً");
        }
        Connection conn = DatabaseManager.getConnection();
        boolean autoCommitOrig = conn.getAutoCommit();
        try {
            conn.setAutoCommit(false);

            // 1. حساب الإجمالي والتحقق الصارم من توفر الكميات وسعر المنتج المعتمد في السيرفر
            BigDecimal totalAmount = BigDecimal.ZERO;
            String checkSql = "SELECT price, quantity_available, is_available FROM products WHERE id = ? AND seller_id = ? AND (available_until IS NULL OR available_until = '' OR date(available_until) >= date('now'))";
            String updateStockSql = "UPDATE products SET quantity_available = quantity_available - ? WHERE id = ? AND quantity_available >= ?";

            PreparedStatement psCheck = conn.prepareStatement(checkSql);
            PreparedStatement psStock = conn.prepareStatement(updateStockSql);

            for (OrderItem item : items) {
                psCheck.setInt(1, item.getProductId());
                psCheck.setInt(2, sellerId);
                try (ResultSet rs = psCheck.executeQuery()) {
                    if (!rs.next()) {
                        conn.rollback();
                        throw new IllegalArgumentException("الوجبة غير موجودة أو لا تتبع لهذه الأم: " + item.getProductId());
                    }
                    if (rs.getInt("is_available") != 1) {
                        conn.rollback();
                        throw new IllegalArgumentException("الوجبة غير متاحة للطلب حالياً");
                    }
                    int stock = rs.getInt("quantity_available");
                    if (stock < item.getQuantity()) {
                        conn.rollback();
                        throw new IllegalArgumentException("الكمية المطلوبة (" + item.getQuantity() + ") أكبر من المتاح بالمخزن (" + stock + ")");
                    }
                    // تثبيت سعر الخادم لضمان عدم تلاعب العميل بالسعر
                    BigDecimal unitPrice = rs.getBigDecimal("price");
                    item.setUnitPrice(unitPrice);
                    BigDecimal subtotal = unitPrice.multiply(BigDecimal.valueOf(item.getQuantity()));
                    item.setSubtotal(subtotal);
                    totalAmount = totalAmount.add(subtotal);
                }

                // خصم المخزون
                psStock.setInt(1, item.getQuantity());
                psStock.setInt(2, item.getProductId());
                psStock.setInt(3, item.getQuantity());
                int rows = psStock.executeUpdate();
                if (rows == 0) {
                    conn.rollback();
                    throw new IllegalStateException("حدث تعارض أثناء حجز الكمية بالمخزن.");
                }
            }

            // 2. إدخال جدول orders
            String insertOrderSql = "INSERT INTO orders (customer_id, seller_id, status, total_amount, payment_method, pickup_time, delivery_method, delivery_address, customer_phone_snapshot, contact_method) " +
                                   "VALUES (?, ?, 'pending', ?, ?, ?, ?, ?, ?, ?)";
            PreparedStatement psOrder = conn.prepareStatement(insertOrderSql, Statement.RETURN_GENERATED_KEYS);
            psOrder.setInt(1, customerId);
            psOrder.setInt(2, sellerId);
            psOrder.setBigDecimal(3, totalAmount);
            psOrder.setString(4, paymentMethod);
            psOrder.setString(5, pickupTime);
            psOrder.setString(6, deliveryMethod);
            psOrder.setString(7, deliveryAddress);
            psOrder.setString(8, customerPhone);
            psOrder.setString(9, contactMethod);
            psOrder.executeUpdate();

            int orderId = 0;
            try (ResultSet rsKeys = psOrder.getGeneratedKeys()) {
                if (rsKeys.next()) {
                    orderId = rsKeys.getInt(1);
                }
            }

            // 3. إدخال تفاصيل الطلب order_items
            String insertItemSql = "INSERT INTO order_items (order_id, product_id, quantity, unit_price, subtotal) VALUES (?, ?, ?, ?, ?)";
            PreparedStatement psItem = conn.prepareStatement(insertItemSql);
            for (OrderItem item : items) {
                psItem.setInt(1, orderId);
                psItem.setInt(2, item.getProductId());
                psItem.setInt(3, item.getQuantity());
                psItem.setBigDecimal(4, item.getUnitPrice());
                psItem.setBigDecimal(5, item.getSubtotal());
                psItem.addBatch();
            }
            psItem.executeBatch();

            conn.commit();

            return findById(orderId);
        } catch (Exception ex) {
            conn.rollback();
            throw ex;
        } finally {
            conn.setAutoCommit(autoCommitOrig);
            conn.close();
        }
    }

    public Order findById(int orderId) {
        String sql = "SELECT o.*, c.name as customer_name, COALESCE(o.customer_phone_snapshot, c.phone) as customer_phone, " +
                     "s.name as seller_name, sp.pickup_address as seller_address " +
                     "FROM orders o " +
                     "JOIN users c ON o.customer_id = c.id " +
                     "JOIN users s ON o.seller_id = s.id " +
                     "LEFT JOIN seller_profiles sp ON s.id = sp.user_id " +
                     "WHERE o.id = ?";
        try (Connection conn = DatabaseManager.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Order order = mapOrder(rs);
                    order.setItems(findOrderItems(order.getId()));
                    return order;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public List<Order> findByCustomer(int customerId) {
        List<Order> list = new ArrayList<>();
        String sql = "SELECT o.*, c.name as customer_name, COALESCE(o.customer_phone_snapshot, c.phone) as customer_phone, " +
                     "s.name as seller_name, sp.pickup_address as seller_address " +
                     "FROM orders o " +
                     "JOIN users c ON o.customer_id = c.id " +
                     "JOIN users s ON o.seller_id = s.id " +
                     "LEFT JOIN seller_profiles sp ON s.id = sp.user_id " +
                     "WHERE o.customer_id = ? ORDER BY o.id DESC";
        try (Connection conn = DatabaseManager.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, customerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Order order = mapOrder(rs);
                    order.setItems(findOrderItems(order.getId()));
                    list.add(order);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    public List<Order> findBySeller(int sellerId) {
        List<Order> list = new ArrayList<>();
        String sql = "SELECT o.*, c.name as customer_name, COALESCE(o.customer_phone_snapshot, c.phone) as customer_phone, " +
                     "s.name as seller_name, sp.pickup_address as seller_address " +
                     "FROM orders o " +
                     "JOIN users c ON o.customer_id = c.id " +
                     "JOIN users s ON o.seller_id = s.id " +
                     "LEFT JOIN seller_profiles sp ON s.id = sp.user_id " +
                     "WHERE o.seller_id = ? ORDER BY o.id DESC";
        try (Connection conn = DatabaseManager.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, sellerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Order order = mapOrder(rs);
                    order.setItems(findOrderItems(order.getId()));
                    list.add(order);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    public List<Order> findAllOrders() {
        List<Order> list = new ArrayList<>();
        String sql = "SELECT o.*, c.name as customer_name, COALESCE(o.customer_phone_snapshot, c.phone) as customer_phone, " +
                     "s.name as seller_name, sp.pickup_address as seller_address " +
                     "FROM orders o " +
                     "JOIN users c ON o.customer_id = c.id " +
                     "JOIN users s ON o.seller_id = s.id " +
                     "LEFT JOIN seller_profiles sp ON s.id = sp.user_id " +
                     "ORDER BY o.id DESC";
        try (Connection conn = DatabaseManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                Order order = mapOrder(rs);
                order.setItems(findOrderItems(order.getId()));
                list.add(order);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    public boolean updateOrderStatus(int orderId, String newStatus, int actorId, String actorRole) {
        try (Connection conn = DatabaseManager.getConnection()) {
            String sql = "SELECT seller_id, status FROM orders WHERE id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, orderId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next() || (!"ADMIN".equals(actorRole) && rs.getInt("seller_id") != actorId)) return false;
                    String current = rs.getString("status");
                    boolean valid = switch (current) {
                        case "pending" -> "confirmed".equals(newStatus) || "rejected".equals(newStatus);
                        case "confirmed" -> "preparing".equals(newStatus) || "cancelled".equals(newStatus);
                        case "preparing" -> "ready".equals(newStatus);
                        case "ready" -> "completed".equals(newStatus);
                        default -> false;
                    };
                    if (!valid) return false;
                }
            }
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement("UPDATE orders SET status = ? WHERE id = ?")) {
                ps.setString(1, newStatus);
                ps.setInt(2, orderId);
                if (ps.executeUpdate() == 0) {
                    conn.rollback();
                    return false;
                }
            }
            if ("rejected".equals(newStatus) || "cancelled".equals(newStatus)) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE products SET quantity_available = quantity_available + " +
                                "(SELECT quantity FROM order_items WHERE order_id = ? AND product_id = products.id) " +
                                "WHERE id IN (SELECT product_id FROM order_items WHERE order_id = ?)")) {
                    ps.setInt(1, orderId);
                    ps.setInt(2, orderId);
                    ps.executeUpdate();
                }
            }
            conn.commit();
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public String getAdminStatsJson() {
        String sql = "SELECT " +
                "(SELECT COUNT(*) FROM users WHERE role='CUSTOMER') customers, " +
                "(SELECT COUNT(*) FROM users WHERE role='MOTHER') mothers, " +
                "(SELECT COUNT(*) FROM orders) orders_count, " +
                "(SELECT COALESCE(SUM(total_amount), 0) FROM orders WHERE status='completed') revenue, " +
                "(SELECT COUNT(*) FROM orders WHERE status='pending') pending";
        try (Connection conn = DatabaseManager.getConnection();
             Statement statement = conn.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            if (rs.next()) {
                return String.format("{\"customers\":%d,\"mothers\":%d,\"orders\":%d,\"revenue\":%s,\"pending\":%d}",
                        rs.getInt("customers"), rs.getInt("mothers"), rs.getInt("orders_count"),
                        rs.getBigDecimal("revenue"), rs.getInt("pending"));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("تعذر تحميل إحصائيات الإدارة", e);
        }
        throw new IllegalStateException("تعذر تحميل إحصائيات الإدارة");
    }

    private List<OrderItem> findOrderItems(int orderId) {
        List<OrderItem> items = new ArrayList<>();
        String sql = "SELECT oi.*, p.name as product_name FROM order_items oi JOIN products p ON oi.product_id = p.id WHERE oi.order_id = ?";
        try (Connection conn = DatabaseManager.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    OrderItem item = new OrderItem();
                    item.setId(rs.getInt("id"));
                    item.setOrderId(rs.getInt("order_id"));
                    item.setProductId(rs.getInt("product_id"));
                    item.setProductName(rs.getString("product_name"));
                    item.setQuantity(rs.getInt("quantity"));
                    item.setUnitPrice(rs.getBigDecimal("unit_price"));
                    item.setSubtotal(rs.getBigDecimal("subtotal"));
                    items.add(item);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return items;
    }

    private Order mapOrder(ResultSet rs) throws SQLException {
        Order o = new Order();
        o.setId(rs.getInt("id"));
        o.setCustomerId(rs.getInt("customer_id"));
        o.setCustomerName(rs.getString("customer_name"));
        o.setCustomerPhone(rs.getString("customer_phone"));
        o.setContactMethod(rs.getString("contact_method"));
        o.setSellerId(rs.getInt("seller_id"));
        o.setSellerName(rs.getString("seller_name"));
        o.setSellerAddress(rs.getString("seller_address"));
        o.setStatus(rs.getString("status"));
        o.setTotalAmount(rs.getBigDecimal("total_amount"));
        o.setPaymentMethod(rs.getString("payment_method"));
        o.setPickupTime(rs.getString("pickup_time"));
        o.setDeliveryMethod(rs.getString("delivery_method"));
        o.setDeliveryAddress(rs.getString("delivery_address"));
        o.setCreatedAt(rs.getTimestamp("created_at"));
        return o;
    }
}
