package com.getsimplex.steptimer.service;

import com.getsimplex.steptimer.model.Customer;
import com.getsimplex.steptimer.model.Profile;
import com.getsimplex.steptimer.model.ProfileRequest;
import com.getsimplex.steptimer.model.User;
import com.getsimplex.steptimer.utils.JedisClient;
import com.getsimplex.steptimer.utils.JedisData;
import com.getsimplex.steptimer.utils.NotFoundException;
import com.google.gson.Gson;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Family profiles. Every method takes the logged-in User first and only ever touches
 * profiles that User owns. That ownership rule is what keeps one family's data
 * away from everyone else.
 */
public class ProfileService {

    private static final Gson gson = new Gson();

    static final int MAX_PROFILES = 10;   // the account holder + up to 9 family members
    private static final Set<String> GENDERS = Set.of("woman", "man", "non_binary", "prefer_not_to_say");
    private static final Set<String> RELATIONSHIPS = Set.of("parent", "child", "partner", "sibling", "grandparent", "friend", "other");
    private static final String[] AVATAR_COLORS = {"green", "coral", "violet", "sky"};

    // ---------------------------------------------------------------- list

    /** All of this account's profiles, the account holder first. Creates the self profile the first time. */
    public static List<Profile> list(User user) throws Exception {
        List<Profile> profiles = ownedProfiles(user.getUserName());
        if (profiles.stream().noneMatch(Profile::isSelf)) {
            profiles.add(0, createSelfProfile(user));
        }
        profiles.sort(Comparator.comparing((Profile p) -> !p.isSelf()).thenComparing(Profile::getCreatedAt));
        return profiles;
    }

    // ---------------------------------------------------------------- read one

    /**
     * The ownership check. A profile that doesn't exist and a profile owned by someone
     * else get the same "not found", so nobody can probe for other families' profile IDs.
     */
    public static Profile getOwned(User user, String profileId) throws Exception {
        Optional<Profile> profile = JedisData.getEntityById(Profile.class, profileId);
        if (profile.isEmpty() || !user.getUserName().equals(profile.get().getOwnerUserName())) {
            throw new NotFoundException("Profile not found");
        }
        return profile.get();
    }

    // ---------------------------------------------------------------- add a family member

    public static Profile addFamilyMember(User user, String requestBody) throws Exception {
        ProfileRequest request = read(requestBody);
        List<Profile> existing = list(user);   // also makes sure the self profile exists
        if (existing.size() >= MAX_PROFILES) {
            throw new IllegalArgumentException("An account can have at most " + MAX_PROFILES + " profiles");
        }

        Profile profile = new Profile();
        profile.setProfileId(UUID.randomUUID().toString());
        profile.setOwnerUserName(user.getUserName());
        profile.setSelf(false);
        profile.setCreatedAt(System.currentTimeMillis());
        profile.setAvatarColor(AVATAR_COLORS[existing.size() % AVATAR_COLORS.length]);

        // Name and relationship are required for a new family member.
        if (blank(request.getFullName())) {
            throw new IllegalArgumentException("Full name is required");
        }
        if (blank(request.getRelationship())) {
            throw new IllegalArgumentException("Relationship is required");
        }
        applyChanges(profile, request);
        save(profile);
        return profile;
    }

    // ---------------------------------------------------------------- edit

    public static Profile update(User user, String profileId, String requestBody) throws Exception {
        Profile profile = getOwned(user, profileId);
        ProfileRequest request = read(requestBody);
        if (profile.isSelf() && request.getRelationship() != null) {
            throw new IllegalArgumentException("Your own profile's relationship can't be changed");
        }
        applyChanges(profile, request);
        save(profile);
        if (profile.isSelf()) {
            copyToCustomer(user, profile);   // keep the older Customer record (used by risk scores) in step
        }
        return profile;
    }

    // ---------------------------------------------------------------- delete

    public static void delete(User user, String profileId) throws Exception {
        Profile profile = getOwned(user, profileId);
        if (profile.isSelf()) {
            throw new IllegalArgumentException("Your own profile can't be deleted");
        }
        JedisData.deleteJedis(Profile.class, profileId);                         // ProfileMap + Profile sorted set
        JedisClient.zrem(ownerIndex(profile.getOwnerUserName()), profileId);     // the owner's list
        PreferencesService.deleteFor(profileId);                                 // their balance answers
        BalanceTestService.deleteAllFor(profileId);                              // their test history
    }

    // ---------------------------------------------------------------- helpers

    /** Checks each field that was sent, then copies it onto the profile. Fields left out are not touched. */
    static void applyChanges(Profile profile, ProfileRequest request) {
        if (request.getFullName() != null) {
            if (blank(request.getFullName()) || request.getFullName().trim().length() > 80) {
                throw new IllegalArgumentException("Full name must be 1-80 characters");
            }
            profile.setFullName(request.getFullName().trim());
        }
        if (request.getBirthDay() != null) {
            profile.setBirthDay(checkBirthDay(request.getBirthDay().trim()));
        }
        if (request.getGender() != null) {
            String gender = request.getGender().trim().toLowerCase();
            if (!GENDERS.contains(gender)) {
                throw new IllegalArgumentException("Gender must be one of " + GENDERS);
            }
            profile.setGender(gender);
        }
        if (request.getRelationship() != null) {
            String relationship = request.getRelationship().trim().toLowerCase();
            if (!RELATIONSHIPS.contains(relationship)) {
                throw new IllegalArgumentException("Relationship must be one of " + RELATIONSHIPS);
            }
            profile.setRelationship(relationship);
        }
        if (request.wantsHealthMetricsCleared()) {
            profile.setHeightCm(null);
            profile.setWeightKg(null);
        }
        if (request.getHeightCm() != null) {
            profile.setHeightCm(checkRange(request.getHeightCm(), 40, 250, "Height (cm)"));
        }
        if (request.getWeightKg() != null) {
            profile.setWeightKg(checkRange(request.getWeightKg(), 2, 350, "Weight (kg)"));
        }
    }

    private static String checkBirthDay(String birthDay) {
        try {
            LocalDate date = LocalDate.parse(birthDay);   // must be YYYY-MM-DD
            if (date.isAfter(LocalDate.now()) || date.isBefore(LocalDate.of(1900, 1, 1))) {
                throw new IllegalArgumentException("Birthday must be between 1900 and today");
            }
            return date.toString();
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Birthday must look like 1960-09-08");
        }
    }

    private static Double checkRange(Double value, double min, double max, String label) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(label + " must be between " + (int) min + " and " + (int) max);
        }
        return Math.round(value * 10) / 10.0;   // keep one decimal place
    }

    /** The account holder's own profile, filled in from the Customer record made at sign-up. */
    private static Profile createSelfProfile(User user) throws Exception {
        Profile profile = new Profile();
        profile.setProfileId(UUID.randomUUID().toString());
        profile.setOwnerUserName(user.getUserName());
        profile.setSelf(true);
        profile.setRelationship("self");
        profile.setAvatarColor(AVATAR_COLORS[0]);
        profile.setCreatedAt(System.currentTimeMillis());
        profile.setFullName(user.getUserName());   // replaced below if the Customer has a name

        Optional<Customer> customer = user.getPhone() == null ? Optional.empty() : CustomerService.findCustomerByPhone(user.getPhone());
        if (customer.isPresent()) {
            Customer c = customer.get();
            if (!blank(c.getCustomerName())) profile.setFullName(c.getCustomerName());
            if (!blank(c.getBirthDay())) profile.setBirthDay(c.getBirthDay());
            if (!blank(c.getGender())) profile.setGender(c.getGender().toLowerCase());
            if (c.getHeight() > 0) profile.setHeightCm((double) c.getHeight());          // Customer height is in cm
            if (c.getWeight() > 0) profile.setWeightKg(c.getWeight() / 1000.0);          // Customer weight is in grams
        }
        save(profile);
        return profile;
    }

    /** Edits to the self profile are copied to the Customer record too, which older code still reads. */
    private static void copyToCustomer(User user, Profile profile) throws Exception {
        if (user.getPhone() == null) return;
        Optional<Customer> customer = CustomerService.findCustomerByPhone(user.getPhone());
        if (customer.isEmpty()) return;
        Customer c = customer.get();
        c.setCustomerName(profile.getFullName());
        if (profile.getBirthDay() != null) c.setBirthDay(profile.getBirthDay());
        if (profile.getGender() != null) c.setGender(profile.getGender());
        c.setHeight(profile.getHeightCm() == null ? 0 : Math.round(profile.getHeightCm()));
        c.setWeight(profile.getWeightKg() == null ? 0 : Math.round(profile.getWeightKg() * 1000));
        CustomerService.createOrUpdateCustomer(c, true);
    }

    private static List<Profile> ownedProfiles(String ownerUserName) throws Exception {
        return new ArrayList<>(JedisData.getEntitiesByIndex(Profile.class, "Owner", ownerUserName));
    }

    private static void save(Profile profile) throws Exception {
        // ProfileMap[id], the Profile sorted set, and ProfileByOwner-<owner>, all scored by createdAt
        JedisData.loadToJedisWithIndexUsingScore(profile, profile.getProfileId(), profile.getCreatedAt(),
                "Owner", profile.getOwnerUserName());
    }

    private static String ownerIndex(String ownerUserName) {
        return "ProfileByOwner-" + ownerUserName;
    }

    private static ProfileRequest read(String requestBody) {
        ProfileRequest request = gson.fromJson(requestBody, ProfileRequest.class);
        if (request == null) {
            throw new IllegalArgumentException("Profile details are required");
        }
        return request;
    }

    private static boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
