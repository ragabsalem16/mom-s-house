package com.beitmama.model;

public class SellerProfile {
    private int userId;
    private String bio;
    private String pickupAddress;
    private String region;
    private String approvalStatus; 
    private String pickupInstructions;
    private String locationUrl;
    private Double latitude;
    private Double longitude;
    private String profileImageUrl;

    public SellerProfile() {}

    public SellerProfile(int userId, String bio, String pickupAddress, String region, String approvalStatus,
                         String pickupInstructions) {
        this(userId, bio, pickupAddress, region, approvalStatus, pickupInstructions, null, null, null);
    }

    public SellerProfile(int userId, String bio, String pickupAddress, String region, String approvalStatus,
                         String pickupInstructions, String locationUrl, Double latitude, Double longitude) {
        this.userId = userId;
        this.bio = bio;
        this.pickupAddress = pickupAddress;
        this.region = region;
        this.approvalStatus = approvalStatus;
        this.pickupInstructions = pickupInstructions;
        this.locationUrl = locationUrl;
        this.latitude = latitude;
        this.longitude = longitude;
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

    public String getLocationUrl() { return locationUrl; }
    public void setLocationUrl(String locationUrl) { this.locationUrl = locationUrl; }

    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }

    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }

    public String getProfileImageUrl() { return profileImageUrl; }
    public void setProfileImageUrl(String profileImageUrl) { this.profileImageUrl = profileImageUrl; }
}
