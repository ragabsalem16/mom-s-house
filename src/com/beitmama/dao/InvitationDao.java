package com.beitmama.dao;

import com.beitmama.model.Invitation;
import com.beitmama.util.SecurityUtil;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class InvitationDao {

    /**
     * إنشاء دعوة جديدة مع حفظ بصمة الرمز (Token Hash)
     */
    public Invitation createInvitation(int adminId, String emailOrPhone, int validityDays) throws SQLException {
        String rawToken = SecurityUtil.generateInvitationToken();
        String tokenHash = SecurityUtil.hash256(rawToken);
        Timestamp expiresAt = new Timestamp(System.currentTimeMillis() + (validityDays * 24L * 3600L * 1000L));

        String sql = "INSERT INTO invitations (token_hash, created_by, email_or_phone, expires_at) VALUES (?, ?, ?, ?)";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, tokenHash);
            ps.setInt(2, adminId);
            ps.setString(3, emailOrPhone);
            ps.setTimestamp(4, expiresAt);
            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    Invitation inv = new Invitation();
                    inv.setId(rs.getInt(1));
                    inv.setToken(rawToken); // متاح للمدير لنسخه وإرساله
                    inv.setTokenHash(tokenHash);
                    inv.setCreatedBy(adminId);
                    inv.setEmailOrPhone(emailOrPhone);
                    inv.setExpiresAt(expiresAt);
                    return inv;
                }
            }
        }
        return null;
    }

    public Invitation findByToken(String rawToken) {
        String tokenHash = SecurityUtil.hash256(rawToken);
        String sql = "SELECT * FROM invitations WHERE token_hash = ?";
        try (Connection conn = DatabaseManager.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tokenHash);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapInvitation(rs);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public synchronized boolean consumeInvitation(int invitationId, int motherUserId) {
        // التحقق واستهلاك الدعوة في معاملة آمنة (Atomic Transaction) لمنع استخدام الرمز مرتين
        String sql = "UPDATE invitations SET used_at = CURRENT_TIMESTAMP, used_by = ? WHERE id = ? AND used_at IS NULL AND revoked_at IS NULL";
        try (Connection conn = DatabaseManager.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, motherUserId);
            ps.setInt(2, invitationId);
            int affected = ps.executeUpdate();
            return affected > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean revokeInvitation(int invitationId) {
        String sql = "UPDATE invitations SET revoked_at = CURRENT_TIMESTAMP WHERE id = ? AND used_at IS NULL";
        try (Connection conn = DatabaseManager.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, invitationId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public List<Invitation> listAllInvitations() {
        List<Invitation> list = new ArrayList<>();
        String sql = "SELECT * FROM invitations ORDER BY id DESC";
        try (Connection conn = DatabaseManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(mapInvitation(rs));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    private Invitation mapInvitation(ResultSet rs) throws SQLException {
        Invitation inv = new Invitation();
        inv.setId(rs.getInt("id"));
        inv.setTokenHash(rs.getString("token_hash"));
        inv.setCreatedBy(rs.getInt("created_by"));
        inv.setEmailOrPhone(rs.getString("email_or_phone"));
        inv.setExpiresAt(rs.getTimestamp("expires_at"));
        inv.setUsedAt(rs.getTimestamp("used_at"));
        int usedBy = rs.getInt("used_by");
        if (!rs.wasNull()) {
            inv.setUsedBy(usedBy);
        }
        inv.setRevokedAt(rs.getTimestamp("revoked_at"));
        return inv;
    }
}
