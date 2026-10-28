package com.beitmama.model;

import java.sql.Timestamp;

public class Invitation {
    private int id;
    private String token; // Token plain-text (only available when freshly created)
    private String tokenHash;
    private int createdBy;
    private String emailOrPhone;
    private Timestamp expiresAt;
    private Timestamp usedAt;
    private Integer usedBy;
    private Timestamp revokedAt;

    public Invitation() {}

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    public String getTokenHash() { return tokenHash; }
    public void setTokenHash(String tokenHash) { this.tokenHash = tokenHash; }

    public int getCreatedBy() { return createdBy; }
    public void setCreatedBy(int createdBy) { this.createdBy = createdBy; }

    public String getEmailOrPhone() { return emailOrPhone; }
    public void setEmailOrPhone(String emailOrPhone) { this.emailOrPhone = emailOrPhone; }

    public Timestamp getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Timestamp expiresAt) { this.expiresAt = expiresAt; }

    public Timestamp getUsedAt() { return usedAt; }
    public void setUsedAt(Timestamp usedAt) { this.usedAt = usedAt; }

    public Integer getUsedBy() { return usedBy; }
    public void setUsedBy(Integer usedBy) { this.usedBy = usedBy; }

    public Timestamp getRevokedAt() { return revokedAt; }
    public void setRevokedAt(Timestamp revokedAt) { this.revokedAt = revokedAt; }

    public boolean isValid() {
        if (revokedAt != null) return false;
        if (usedAt != null) return false;
        if (expiresAt != null && expiresAt.before(new Timestamp(System.currentTimeMillis()))) return false;
        return true;
    }
}
