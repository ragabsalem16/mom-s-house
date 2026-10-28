package com.beitmama.model;

public class SellerProfile {
    private int userId;
    private String bio;
    private String pickupAddress;
    private String region;
    private String approvalStatus; // PENDING, APPROVED, REJECTED, SUSPENDED
    private String pickupInstructions;

    public SellerProfile() {}

    public SellerProfile(int userId, String bio, String pickupAddress, String region, String approvalStatus,
                         String pickupInstructions) {
        this.userId = userId;
        this.bio = bio;
        this.pickupAddress = pickupAddress;
        this.region = region;
        this.approvalStatus = approvalStatus;
        this.pickupInstructions = pickupInstructions;
    }

    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }

    public String getBio() { return bio; }
    public void setBio(String bio) { this.bio = bio; }

    public String getPickupAddress() { return pickupAddress; }
    public void setPickupAddress(String pickupAddress) { this.pickupAddress = pickupAddress; }

    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }

    public String getApprovalStatus() { return approvalStatus; }
    public void setApprovalStatus(String approvalStatus) { this.approvalStatus = approvalStatus; }

    public String getPickupInstructions() { return pickupInstructions; }
    public void setPickupInstructions(String pickupInstructions) { this.pickupInstructions = pickupInstructions; }
}
