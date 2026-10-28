package com.beitmama.dao;

import com.beitmama.model.Review;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ReviewDao {

    public boolean addReview(int orderId, int customerId, int sellerId, int rating, String comment) {
        if (rating < 1 || rating > 5) throw new IllegalArgumentException("التقييم يجب أن يكون بين نجمة و5 نجوم");
        String sql = "INSERT INTO reviews (order_id, customer_id, seller_id, rating, comment) " +
                "SELECT id, customer_id, seller_id, ?, ? FROM orders " +
                "WHERE id = ? AND customer_id = ? AND seller_id = ? AND status = 'completed'";
        try (Connection conn = DatabaseManager.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, rating);
            ps.setString(2, comment);
            ps.setInt(3, orderId);
            ps.setInt(4, customerId);
            ps.setInt(5, sellerId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public List<Review> findBySeller(int sellerId) {
        List<Review> list = new ArrayList<>();
        String sql = "SELECT r.*, u.name as customer_name FROM reviews r JOIN users u ON r.customer_id = u.id WHERE r.seller_id = ? ORDER BY r.id DESC";
        try (Connection conn = DatabaseManager.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, sellerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Review r = new Review();
                    r.setId(rs.getInt("id"));
                    r.setOrderId(rs.getInt("order_id"));
                    r.setCustomerId(rs.getInt("customer_id"));
                    r.setCustomerName(rs.getString("customer_name"));
                    r.setSellerId(rs.getInt("seller_id"));
                    r.setRating(rs.getInt("rating"));
                    r.setComment(rs.getString("comment"));
                    r.setCreatedAt(rs.getTimestamp("created_at"));
                    list.add(r);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }
}
