package com.beitmama.dao;

import com.beitmama.model.User;
import com.beitmama.model.SellerProfile;
import com.beitmama.util.SecurityUtil;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class UserDao {

    public User findByEmail(String email) {
        String sql = "SELECT * FROM users WHERE email = ?";
        try (Connection conn = DatabaseManager.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapUser(rs);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public User findById(int id) {
        String sql = "SELECT * FROM users WHERE id = ?";
        try (Connection conn = DatabaseManager.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapUser(rs);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public User registerUser(String name, String email, String phone, String password, String role, String status) throws SQLException {
        String salt = SecurityUtil.generateSalt();
        String hash = SecurityUtil.hashPassword(password, salt);
        String sql = "INSERT INTO users (name, email, phone, password_hash, salt, role, status) VALUES (?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setString(2, email);
            ps.setString(3, phone);
            ps.setString(4, hash);
            ps.setString(5, salt);
            ps.setString(6, role);
            ps.setString(7, status);
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    int id = keys.getInt(1);
                    return findById(id);
                }
            }
        }
        return null;
    }

    public void updateUserStatus(int userId, String status) throws SQLException {
        String sql = "UPDATE users SET status = ? WHERE id = ?";
        try (Connection conn = DatabaseManager.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setInt(2, userId);
            ps.executeUpdate();
        }
    }

    public User updateCustomerProfile(int userId, String name, String phone, String address, String preferredContact) throws SQLException {
        String sql = "UPDATE users SET name = ?, phone = ?, address = ?, preferred_contact = ? WHERE id = ? AND role = 'CUSTOMER'";
        try (Connection conn = DatabaseManager.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setString(2, phone);
            ps.setString(3, address);
            ps.setString(4, preferredContact);
            ps.setInt(5, userId);
            if (ps.executeUpdate() == 0) return null;
        }
        return findById(userId);
    }

    public boolean changePassword(int userId, String currentPassword, String newPassword) throws SQLException {
        User user = findById(userId);
        if (user == null || !SecurityUtil.hashPassword(currentPassword, user.getSalt()).equals(user.getPasswordHash())) {
            return false;
        }
        String salt = SecurityUtil.generateSalt();
        String sql = "UPDATE users SET password_hash = ?, salt = ? WHERE id = ?";
        try (Connection conn = DatabaseManager.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, SecurityUtil.hashPassword(newPassword, salt));
            ps.setString(2, salt);
            ps.setInt(3, userId);
            return ps.executeUpdate() > 0;
        }
    }

    public List<User> findAllMothers() {
        List<User> list = new ArrayList<>();
        String sql = "SELECT * FROM users WHERE role = 'MOTHER' ORDER BY id DESC";
        try (Connection conn = DatabaseManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(mapUser(rs));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    public List<User> findAllCustomers() {
        List<User> list = new ArrayList<>();
        String sql = "SELECT * FROM users WHERE role = 'CUSTOMER' ORDER BY id DESC";
        try (Connection conn = DatabaseManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(mapUser(rs));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    // Seller Profiles
    /** بحث عن مستخدم (عميل أو أم أو مدير) برقم الهاتف أو جزء منه */
    public List<User> searchByPhone(String phoneQuery) {
        List<User> list = new ArrayList<>();
        String sql = "SELECT * FROM users WHERE phone LIKE ? ORDER BY role, id DESC LIMIT 50";
        try (Connection conn = DatabaseManager.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, "%" + phoneQuery.replaceAll("[\\s-]", "") + "%");
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapUser(rs));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    public SellerProfile getSellerProfile(int userId) {
        String sql = "SELECT * FROM seller_profiles WHERE user_id = ?";
        try (Connection conn = DatabaseManager.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new SellerProfile(
                            rs.getInt("user_id"),
                            rs.getString("bio"),
                            rs.getString("pickup_address"),
                            rs.getString("region"),
                            rs.getString("approval_status"),
                            rs.getString("pickup_instructions")
                    );
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public void saveSellerProfile(SellerProfile profile) throws SQLException {
        String sql = "INSERT OR REPLACE INTO seller_profiles (user_id, bio, pickup_address, region, approval_status, pickup_instructions) " +
                     "VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseManager.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, profile.getUserId());
            ps.setString(2, profile.getBio());
            ps.setString(3, profile.getPickupAddress());
            ps.setString(4, profile.getRegion());
            ps.setString(5, profile.getApprovalStatus());
            ps.setString(6, profile.getPickupInstructions());
            ps.executeUpdate();
        }
    }

    public void updateSellerApproval(int userId, String approvalStatus) throws SQLException {
        String sql = "UPDATE seller_profiles SET approval_status = ? WHERE user_id = ?";
        try (Connection conn = DatabaseManager.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, approvalStatus);
            ps.setInt(2, userId);
            ps.executeUpdate();
        }
    }

    private User mapUser(ResultSet rs) throws SQLException {
        return new User(
                rs.getInt("id"),
                rs.getString("name"),
                rs.getString("email"),
                rs.getString("phone"),
                rs.getString("address"),
                rs.getString("preferred_contact"),
                rs.getString("password_hash"),
                rs.getString("salt"),
                rs.getString("role"),
                rs.getString("status"),
                rs.getTimestamp("created_at")
        );
    }
}
