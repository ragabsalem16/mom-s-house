package com.beitmama.dao;

import com.beitmama.model.Product;

import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ProductDao {

    public List<Product> findAllAvailable() {
        List<Product> list = new ArrayList<>();
        String sql = "SELECT p.*, u.name as seller_name, sp.region as seller_region, c.name as category_name " +
                     "FROM products p " +
                     "JOIN users u ON p.seller_id = u.id " +
                     "JOIN seller_profiles sp ON u.id = sp.user_id " +
                     "JOIN categories c ON p.category_id = c.id " +
                     "WHERE p.is_available = 1 AND p.quantity_available > 0 AND (p.available_until IS NULL OR p.available_until = '' OR date(p.available_until) >= date('now')) AND sp.approval_status = 'APPROVED' AND u.status = 'ACTIVE' " +
                     "ORDER BY p.id DESC";
        try (Connection conn = DatabaseManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(mapProduct(rs));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    public List<Product> findBySeller(int sellerId) {
        List<Product> list = new ArrayList<>();
        String sql = "SELECT p.*, u.name as seller_name, sp.region as seller_region, c.name as category_name " +
                     "FROM products p " +
                     "JOIN users u ON p.seller_id = u.id " +
                     "LEFT JOIN seller_profiles sp ON u.id = sp.user_id " +
                     "JOIN categories c ON p.category_id = c.id " +
                     "WHERE p.seller_id = ? ORDER BY p.id DESC";
        try (Connection conn = DatabaseManager.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, sellerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapProduct(rs));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    public Product findById(int id) {
        String sql = "SELECT p.*, u.name as seller_name, sp.region as seller_region, c.name as category_name " +
                     "FROM products p " +
                     "JOIN users u ON p.seller_id = u.id " +
                     "LEFT JOIN seller_profiles sp ON u.id = sp.user_id " +
                     "JOIN categories c ON p.category_id = c.id " +
                     "WHERE p.id = ?";
        try (Connection conn = DatabaseManager.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapProduct(rs);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public boolean addProduct(Product p) {
        String sql = "INSERT INTO products (seller_id, category_id, name, description, price, quantity_available, is_available, image_url, available_until) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseManager.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, p.getSellerId());
            ps.setInt(2, p.getCategoryId());
            ps.setString(3, p.getName());
            ps.setString(4, p.getDescription());
            ps.setBigDecimal(5, p.getPrice());
            ps.setInt(6, p.getQuantityAvailable());
            ps.setInt(7, p.isAvailable() ? 1 : 0);
            ps.setString(8, p.getImageUrl());
            ps.setString(9, p.getAvailableUntil());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean updateProduct(Product p) {
        String sql = "UPDATE products SET name = ?, description = ?, price = ?, quantity_available = ?, is_available = ?, category_id = ?, image_url = ?, available_until = ? " +
                     "WHERE id = ? AND seller_id = ?";
        try (Connection conn = DatabaseManager.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, p.getName());
            ps.setString(2, p.getDescription());
            ps.setBigDecimal(3, p.getPrice());
            ps.setInt(4, p.getQuantityAvailable());
            ps.setInt(5, p.isAvailable() ? 1 : 0);
            ps.setInt(6, p.getCategoryId());
            ps.setString(7, p.getImageUrl());
            ps.setString(8, p.getAvailableUntil());
            ps.setInt(9, p.getId());
            ps.setInt(10, p.getSellerId());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean deleteProduct(int id, int sellerId) {
        String sql = "UPDATE products SET is_available = 0, quantity_available = 0 WHERE id = ? AND seller_id = ?";
        try (Connection conn = DatabaseManager.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.setInt(2, sellerId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    private Product mapProduct(ResultSet rs) throws SQLException {
        Product p = new Product();
        p.setId(rs.getInt("id"));
        p.setSellerId(rs.getInt("seller_id"));
        p.setSellerName(rs.getString("seller_name"));
        p.setSellerRegion(rs.getString("seller_region"));
        p.setCategoryId(rs.getInt("category_id"));
        p.setCategoryName(rs.getString("category_name"));
        p.setName(rs.getString("name"));
        p.setDescription(rs.getString("description"));
        p.setPrice(rs.getBigDecimal("price"));
        p.setQuantityAvailable(rs.getInt("quantity_available"));
        p.setAvailable(rs.getInt("is_available") == 1);
        p.setImageUrl(rs.getString("image_url"));
        p.setAvailableUntil(rs.getString("available_until"));
        return p;
    }
}
