package com.beitmama.dao;

import com.beitmama.model.Invitation;
import com.beitmama.util.SecurityUtil;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class InvitationDao {
    public Invitation createInvitation(int adminId, String emailOrPhone, int validityDays) throws SQLException {
        if (validityDays < 1 || validityDays > 365) {
            throw new IllegalArgumentException("مدة صلاحية الدعوة غير صحيحة");
        }
        String rawToken = SecurityUtil.generateInvitationToken();
        String tokenHash = SecurityUtil.hash256(rawToken);
        Timestamp expiresAt = new Timestamp(System.currentTimeMillis() + validityDays * 24L * 3600L * 1000L);
        int id = FirestoreDatabase.nextId("invitations");

        Map<String, Object> data = new HashMap<>();
        data.put("id", id);
        data.put("token_hash", tokenHash);
        data.put("created_by", adminId);
        data.put("email_or_phone", emailOrPhone);
        data.put("expires_at", new Date(expiresAt.getTime()));
        data.put("used_at", null);
        data.put("used_by", null);
        data.put("revoked_at", null);
        Firestore firestore = FirestoreDatabase.get();
        FirestoreDatabase.await(firestore.batch()
                .set(FirestoreDatabase.document("invitations", id), data)
                .set(firestore.collection("invitation_tokens").document(tokenHash), Map.of("invitation_id", id))
                .commit());

        Invitation invitation = mapInvitation(data);
        invitation.setToken(rawToken);
        return invitation;
    }

    public Invitation findByToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) return null;
        String tokenHash = SecurityUtil.hash256(rawToken);
        try {
            DocumentSnapshot index = FirestoreDatabase.await(
                    FirestoreDatabase.get().collection("invitation_tokens").document(tokenHash).get());
            if (!index.exists()) return null;
            DocumentSnapshot snapshot = FirestoreDatabase.read("invitations", number(index.get("invitation_id")));
            return snapshot.exists() ? mapInvitation(snapshot) : null;
        } catch (SQLException e) {
            throw databaseError("تعذر التحقق من رمز الدعوة", e);
        }
    }

    public boolean consumeInvitation(int invitationId, int motherUserId) {
        Firestore firestore = FirestoreDatabase.get();
        DocumentReference reference = FirestoreDatabase.document("invitations", invitationId);
        try {
            return FirestoreDatabase.await(firestore.runTransaction(transaction -> {
                DocumentSnapshot snapshot = transaction.get(reference).get();
                if (!snapshot.exists()
                        || snapshot.get("used_at") != null
                        || snapshot.get("revoked_at") != null
                        || !InvitationExpiry.isFuture(snapshot.get("expires_at"))) return false;
                transaction.update(reference, Map.of(
                        "used_at", new Date(),
                        "used_by", motherUserId));
                return true;
            }));
        } catch (SQLException e) {
            throw databaseError("تعذر استهلاك الدعوة", e);
        }
    }

    public boolean revokeInvitation(int invitationId) {
        Firestore firestore = FirestoreDatabase.get();
        DocumentReference reference = FirestoreDatabase.document("invitations", invitationId);
        try {
            return FirestoreDatabase.await(firestore.runTransaction(transaction -> {
                DocumentSnapshot snapshot = transaction.get(reference).get();
                if (!snapshot.exists() || snapshot.get("used_at") != null
                        || snapshot.get("revoked_at") != null) return false;
                transaction.update(reference, "revoked_at", new Date());
                return true;
            }));
        } catch (SQLException e) {
            throw databaseError("تعذر إلغاء الدعوة", e);
        }
    }

    public List<Invitation> listAllInvitations() {
        try {
            List<Invitation> result = new ArrayList<>();
            for (QueryDocumentSnapshot snapshot : FirestoreDatabase.list("invitations")) {
                result.add(mapInvitation(snapshot));
            }
            result.sort(Comparator.comparingInt(Invitation::getId).reversed());
            return result;
        } catch (SQLException e) {
            throw databaseError("تعذر تحميل الدعوات", e);
        }
    }

    private Invitation mapInvitation(Map<String, Object> data) {
        Invitation invitation = new Invitation();
        invitation.setId(number(data.get("id")));
        invitation.setTokenHash((String) data.get("token_hash"));
        invitation.setCreatedBy(number(data.get("created_by")));
        invitation.setEmailOrPhone((String) data.get("email_or_phone"));
        invitation.setExpiresAt(FirestoreDatabase.timestamp(data.get("expires_at")));
        invitation.setUsedAt(FirestoreDatabase.timestamp(data.get("used_at")));
        Object usedBy = data.get("used_by");
        if (usedBy instanceof Number number) invitation.setUsedBy(number.intValue());
        invitation.setRevokedAt(FirestoreDatabase.timestamp(data.get("revoked_at")));
        return invitation;
    }

    private Invitation mapInvitation(DocumentSnapshot snapshot) {
        Invitation invitation = new Invitation();
        invitation.setId(number(snapshot.get("id")));
        invitation.setTokenHash(snapshot.getString("token_hash"));
        invitation.setCreatedBy(number(snapshot.get("created_by")));
        invitation.setEmailOrPhone(snapshot.getString("email_or_phone"));
        invitation.setExpiresAt(FirestoreDatabase.timestamp(snapshot.get("expires_at")));
        invitation.setUsedAt(FirestoreDatabase.timestamp(snapshot.get("used_at")));
        Object usedBy = snapshot.get("used_by");
        if (usedBy instanceof Number number) invitation.setUsedBy(number.intValue());
        invitation.setRevokedAt(FirestoreDatabase.timestamp(snapshot.get("revoked_at")));
        return invitation;
    }

    private int number(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private IllegalStateException databaseError(String message, Exception cause) {
        return new IllegalStateException(message, cause);
    }

    private static final class InvitationExpiry {
        private static boolean isFuture(Object value) {
            Date expiration = FirestoreDatabase.timestamp(value);
            return expiration != null && expiration.after(new Date());
        }
    }
}
