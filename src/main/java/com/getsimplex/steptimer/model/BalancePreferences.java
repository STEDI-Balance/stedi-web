package com.getsimplex.steptimer.model;

/**
 * One profile's answers to the balance questions (Figma: "Your balance, your goals" during
 * sign-up, and "Edit balance preferences" in Profile).
 *
 * Saved in Redis as BalancePreferencesMap[profileId] -> this object as JSON.
 * The same class is also the JSON body of PUT /profiles/:id/preferences, e.g.
 * {"feeling":"ok","improveGoal":8,"referralSource":"physical_therapist"}
 */
public class BalancePreferences {

    private String profileId;
    private String feeling;          // "How do you feel about your balance today?" poor | fair | ok | good | great
    private Integer improveGoal;     // "How much do you want to improve?" 1 (not a priority) to 10 (very important)
    private String referralSource;   // "Who recommended STEDI?" clinician | physical_therapist | friend_family | social_media | other (optional)
    private long updatedAt;

    public String getProfileId() { return profileId; }
    public void setProfileId(String profileId) { this.profileId = profileId; }

    public String getFeeling() { return feeling; }
    public void setFeeling(String feeling) { this.feeling = feeling; }

    public Integer getImproveGoal() { return improveGoal; }
    public void setImproveGoal(Integer improveGoal) { this.improveGoal = improveGoal; }

    public String getReferralSource() { return referralSource; }
    public void setReferralSource(String referralSource) { this.referralSource = referralSource; }

    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
}
