package com.getsimplex.steptimer.model;

/**
 * One person whose balance is tracked: the account holder ("self") or a family member.
 * Family members have no phone or login of their own; they belong to the owner's account.
 *
 * Saved in Redis as:
 *   ProfileMap[profileId]            -> this object as JSON
 *   Profile (sorted set)             -> profileId, scored by createdAt
 *   ProfileByOwner-<ownerUserName>   -> profileId, scored by createdAt (all of one account's profiles)
 */
public class Profile {

    private String profileId;       // random ID (UUID), never a phone number
    private String ownerUserName;   // the account this profile belongs to
    private boolean self;           // true for the account holder's own profile (exactly one per account)
    private String fullName;
    private String birthDay;        // YYYY-MM-DD, optional
    private String gender;          // woman | man | non_binary | prefer_not_to_say, optional
    private String relationship;    // self | parent | child | partner | sibling | grandparent | friend | other
    private Double heightCm;        // optional ("Only add height and weight if they're comfortable sharing")
    private Double weightKg;        // optional
    private String avatarColor;     // green | violet | coral | sky (the tinted avatar circles in Figma)
    private long createdAt;

    public String getProfileId() { return profileId; }
    public void setProfileId(String profileId) { this.profileId = profileId; }

    public String getOwnerUserName() { return ownerUserName; }
    public void setOwnerUserName(String ownerUserName) { this.ownerUserName = ownerUserName; }

    public boolean isSelf() { return self; }
    public void setSelf(boolean self) { this.self = self; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getBirthDay() { return birthDay; }
    public void setBirthDay(String birthDay) { this.birthDay = birthDay; }

    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }

    public String getRelationship() { return relationship; }
    public void setRelationship(String relationship) { this.relationship = relationship; }

    public Double getHeightCm() { return heightCm; }
    public void setHeightCm(Double heightCm) { this.heightCm = heightCm; }

    public Double getWeightKg() { return weightKg; }
    public void setWeightKg(Double weightKg) { this.weightKg = weightKg; }

    public String getAvatarColor() { return avatarColor; }
    public void setAvatarColor(String avatarColor) { this.avatarColor = avatarColor; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
}
