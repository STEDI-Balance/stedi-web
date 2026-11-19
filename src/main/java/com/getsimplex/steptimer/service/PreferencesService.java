package com.getsimplex.steptimer.service;

import com.getsimplex.steptimer.model.BalancePreferences;
import com.getsimplex.steptimer.model.Profile;
import com.getsimplex.steptimer.model.User;
import com.getsimplex.steptimer.utils.JedisData;
import com.google.gson.Gson;

import java.util.Optional;
import java.util.Set;

/**
 * Balance preferences, one set per profile. Both methods start with ProfileService.getOwned,
 * so they inherit its ownership check: you can only read or change your own family's answers.
 */
public class PreferencesService {

    private static final Gson gson = new Gson();

    private static final Set<String> FEELINGS = Set.of("poor", "fair", "ok", "good", "great");
    private static final Set<String> REFERRAL_SOURCES = Set.of("clinician", "physical_therapist", "friend_family", "social_media", "other");

    /** The saved answers, or an object with only the profileId if nothing has been answered yet. */
    public static BalancePreferences get(User user, String profileId) throws Exception {
        Profile profile = ProfileService.getOwned(user, profileId);
        Optional<BalancePreferences> saved = JedisData.getEntityById(BalancePreferences.class, profile.getProfileId());
        if (saved.isPresent()) {
            return saved.get();
        }
        BalancePreferences empty = new BalancePreferences();
        empty.setProfileId(profile.getProfileId());
        return empty;   // the app shows "Not set yet"
    }

    /** Saves all the answers at once (PUT replaces the whole set, like pressing "Save preferences"). */
    public static BalancePreferences put(User user, String profileId, String requestBody) throws Exception {
        Profile profile = ProfileService.getOwned(user, profileId);
        BalancePreferences request = gson.fromJson(requestBody, BalancePreferences.class);
        if (request == null) {
            throw new IllegalArgumentException("Balance preferences are required");
        }

        BalancePreferences preferences = new BalancePreferences();
        preferences.setProfileId(profile.getProfileId());   // from the URL, never from the body
        preferences.setFeeling(pick(request.getFeeling(), FEELINGS, "feeling", true));
        preferences.setReferralSource(pick(request.getReferralSource(), REFERRAL_SOURCES, "referralSource", false));

        Integer goal = request.getImproveGoal();
        if (goal == null || goal < 1 || goal > 10) {
            throw new IllegalArgumentException("improveGoal must be a whole number from 1 to 10");
        }
        preferences.setImproveGoal(goal);
        preferences.setUpdatedAt(System.currentTimeMillis());

        JedisData.loadToJedis(preferences, profile.getProfileId(), preferences.getUpdatedAt());
        return preferences;
    }

    /** Called when a family member's profile is deleted. */
    static void deleteFor(String profileId) throws Exception {
        JedisData.deleteJedis(BalancePreferences.class, profileId);
    }

    /** Lower-cases the answer and checks it's one of the allowed choices. */
    static String pick(String value, Set<String> allowed, String fieldName, boolean required) {
        if (value == null || value.trim().isEmpty()) {
            if (required) throw new IllegalArgumentException(fieldName + " is required");
            return null;
        }
        String choice = value.trim().toLowerCase();
        if (!allowed.contains(choice)) {
            throw new IllegalArgumentException(fieldName + " must be one of " + allowed);
        }
        return choice;
    }
}
