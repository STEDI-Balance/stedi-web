package com.getsimplex.steptimer.service;

import com.getsimplex.steptimer.model.Profile;
import com.getsimplex.steptimer.model.ProfileRequest;
import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** The rules for editing a profile (ProfileService.applyChanges) and balance answers (PreferencesService.pick). */
class ProfileRulesTest {

    private static final Gson gson = new Gson();

    /** Makes a ProfileRequest from JSON, the same way the server reads a request body. */
    private static ProfileRequest request(String json) {
        return gson.fromJson(json, ProfileRequest.class);
    }

    private static Profile jordan() {
        Profile profile = new Profile();
        profile.setFullName("Jordan Lee");
        profile.setBirthDay("1960-09-08");
        profile.setRelationship("parent");
        profile.setHeightCm(178.0);
        profile.setWeightKg(80.0);
        return profile;
    }

    @Test
    void fieldsLeftOutAreNotChanged() {
        Profile profile = jordan();
        ProfileService.applyChanges(profile, request("{\"heightCm\": 180}"));
        assertEquals("Jordan Lee", profile.getFullName());
        assertEquals("1960-09-08", profile.getBirthDay());
        assertEquals(180.0, profile.getHeightCm());
    }

    @Test
    void choicesAreLowerCased() {
        Profile profile = jordan();
        ProfileService.applyChanges(profile, request("{\"gender\": \"Man\", \"relationship\": \"PARENT\"}"));
        assertEquals("man", profile.getGender());
        assertEquals("parent", profile.getRelationship());
    }

    @Test
    void unknownChoicesAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> ProfileService.applyChanges(jordan(), request("{\"gender\": \"robot\"}")));
        assertThrows(IllegalArgumentException.class, () -> ProfileService.applyChanges(jordan(), request("{\"relationship\": \"self\"}")));
    }

    @Test
    void birthdaysMustBeRealPastDates() {
        assertThrows(IllegalArgumentException.class, () -> ProfileService.applyChanges(jordan(), request("{\"birthDay\": \"2099-01-01\"}")));
        assertThrows(IllegalArgumentException.class, () -> ProfileService.applyChanges(jordan(), request("{\"birthDay\": \"1899-12-31\"}")));
        assertThrows(IllegalArgumentException.class, () -> ProfileService.applyChanges(jordan(), request("{\"birthDay\": \"09/08/1960\"}")));
        assertThrows(IllegalArgumentException.class, () -> ProfileService.applyChanges(jordan(), request("{\"birthDay\": \"1960-02-30\"}")));
    }

    @Test
    void heightAndWeightMustBeSensibleAndKeepOneDecimal() {
        assertThrows(IllegalArgumentException.class, () -> ProfileService.applyChanges(jordan(), request("{\"heightCm\": 300}")));
        assertThrows(IllegalArgumentException.class, () -> ProfileService.applyChanges(jordan(), request("{\"weightKg\": 1}")));

        Profile profile = jordan();
        ProfileService.applyChanges(profile, request("{\"heightCm\": 178.26}"));
        assertEquals(178.3, profile.getHeightCm());
    }

    @Test
    void healthMetricsCanBeClearedForNotShared() {
        Profile profile = jordan();
        ProfileService.applyChanges(profile, request("{\"clearHealthMetrics\": true}"));
        assertNull(profile.getHeightCm());
        assertNull(profile.getWeightKg());
    }

    @Test
    void aNameCantBeBlank() {
        assertThrows(IllegalArgumentException.class, () -> ProfileService.applyChanges(jordan(), request("{\"fullName\": \"   \"}")));
    }

    @Test
    void balanceAnswersMustBeOneOfTheChoices() {
        Set<String> feelings = Set.of("poor", "fair", "ok", "good", "great");
        assertEquals("ok", PreferencesService.pick("OK", feelings, "feeling", true));
        assertNull(PreferencesService.pick(null, feelings, "referralSource", false));      // optional and missing
        assertThrows(IllegalArgumentException.class, () -> PreferencesService.pick(null, feelings, "feeling", true));
        assertThrows(IllegalArgumentException.class, () -> PreferencesService.pick("amazing", feelings, "feeling", true));
    }
}
