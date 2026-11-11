package com.getsimplex.steptimer.service;

import com.getsimplex.steptimer.model.BalanceTest;
import com.getsimplex.steptimer.model.Profile;
import com.getsimplex.steptimer.model.TestRound;
import com.getsimplex.steptimer.model.User;
import com.getsimplex.steptimer.utils.JedisClient;
import com.getsimplex.steptimer.utils.JedisData;
import com.getsimplex.steptimer.utils.NotFoundException;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Two-round Rapid Step Tests, saved per profile. Every method goes through
 * ProfileService.getOwned, so only the account that owns a profile can save or read its tests.
 */
public class BalanceTestService {

    private static final Gson gson = new Gson();

    static final int STEPS_PER_ROUND = 30;
    static final int ROUNDS_PER_TEST = 2;
    static final long MAX_GAP_MS = 2 * 60 * 1000;            // a single gap over 2 minutes isn't a real step
    static final long MAX_AGE_MS = 30L * 24 * 60 * 60 * 1000; // tests saved offline can be uploaded up to 30 days later
    static final long CLOCK_SLACK_MS = 5 * 60 * 1000;         // allow a phone clock to be up to 5 minutes fast

    // Letters, numbers and dashes, 8-64 long (a UUID like 3f9a1c2e-... fits).
    private static final Pattern CLIENT_ID_RULE = Pattern.compile("^[A-Za-z0-9-]{8,64}$");

    /** What save() hands back: the test, and whether it was just created or had already been saved. */
    public static class SaveResult {
        public final BalanceTest test;
        public final boolean created;
        SaveResult(BalanceTest test, boolean created) { this.test = test; this.created = created; }
    }

    // ---------------------------------------------------------------- save

    /**
     * Saves a test. Safe to repeat: if the app sends the same clientTestId again (for example
     * because the connection dropped before it got the reply), the first copy is returned
     * and nothing new is saved. This is called making the request "idempotent".
     */
    public static SaveResult save(User user, String profileId, String requestBody) throws Exception {
        Profile profile = ProfileService.getOwned(user, profileId);
        BalanceTest request = gson.fromJson(requestBody, BalanceTest.class);
        if (request == null) {
            throw new IllegalArgumentException("Test results are required");
        }

        String clientTestId = request.getClientTestId() == null ? "" : request.getClientTestId().trim();
        if (!CLIENT_ID_RULE.matcher(clientTestId).matches()) {
            throw new IllegalArgumentException("clientTestId is required (a UUID made by the app)");
        }
        List<BalanceTest> alreadySaved = JedisData.getEntitiesByIndex(
                BalanceTest.class, "ClientId", clientIdKey(profile.getProfileId(), clientTestId));
        if (!alreadySaved.isEmpty()) {
            return new SaveResult(alreadySaved.get(0), false);   // a resend: hand back the first copy
        }

        checkRounds(request);

        // Build the record from only the parts the app is allowed to send.
        BalanceTest test = new BalanceTest();
        test.setTestId(UUID.randomUUID().toString());
        test.setClientTestId(clientTestId);
        test.setProfileId(profile.getProfileId());             // from the URL, never the body
        test.setUserName(user.getUserName());
        test.setStatus(request.getStatus());
        test.setDeviceId(cleanDeviceId(request.getDeviceId(), user));
        List<TestRound> rounds = new ArrayList<>();
        for (TestRound sent : request.getRounds()) {
            TestRound round = new TestRound();
            round.setRound(sent.getRound());
            round.setStartTime(sent.getStartTime());
            round.setStepPoints(new ArrayList<>(sent.getStepPoints()));
            rounds.add(round);
        }
        test.setRounds(rounds);

        ScoreCalculator.measure(test);   // durations, totals, averages
        checkTiming(test);
        ScoreCalculator.score(test);     // null + "pending" until the sponsor's formula exists
        store(test);
        // Remember clientTestId -> testId, so a resend finds this test.
        JedisClient.zadd("BalanceTestByClientId-" + clientIdKey(test.getProfileId(), clientTestId), 0, test.getTestId());
        return new SaveResult(test, true);
    }

    /** The clientTestId is only unique within one profile, so the key includes both. */
    private static String clientIdKey(String profileId, String clientTestId) {
        return profileId + "-" + clientTestId;
    }

    // ---------------------------------------------------------------- read

    /** A profile's tests between two times (milliseconds since 1970), newest first. */
    public static List<BalanceTest> list(User user, String profileId, String from, String to) throws Exception {
        Profile profile = ProfileService.getOwned(user, profileId);
        long fromTime = parseTime(from, 0, "from");
        long toTime = parseTime(to, Long.MAX_VALUE, "to");
        List<BalanceTest> tests = JedisData.getEntitiesByIndexAndScore(
                BalanceTest.class, "Profile", profile.getProfileId(), fromTime, toTime);   // ZRANGEBYSCORE on completedAt
        for (BalanceTest test : tests) {
            rescoreIfOutdated(test);
        }
        tests.sort(Comparator.comparingLong(BalanceTest::getCompletedAt).reversed());
        return tests;
    }

    /** One test. A test that doesn't exist and one from another account both answer "Test not found". */
    public static BalanceTest getOne(User user, String testId) throws Exception {
        Optional<BalanceTest> test = JedisData.getEntityById(BalanceTest.class, testId);
        if (test.isEmpty()) {
            throw new NotFoundException("Test not found");
        }
        try {
            ProfileService.getOwned(user, test.get().getProfileId());
        } catch (NotFoundException notYours) {
            throw new NotFoundException("Test not found");
        }
        rescoreIfOutdated(test.get());
        return test.get();
    }

    /** Called when a family member's profile is deleted. */
    static void deleteAllFor(String profileId) throws Exception {
        List<BalanceTest> tests = JedisData.getEntitiesByIndexAndScore(BalanceTest.class, "Profile", profileId, 0, Long.MAX_VALUE);
        for (BalanceTest test : tests) {
            JedisData.deleteJedis(BalanceTest.class, test.getTestId());
            if (test.getClientTestId() != null) {
                JedisClient.zrem("BalanceTestByClientId-" + clientIdKey(profileId, test.getClientTestId()), test.getTestId());
            }
        }
        JedisClient.zremrangeByScore("BalanceTestByProfile-" + profileId, 0, Double.MAX_VALUE);
    }

    // ---------------------------------------------------------------- checks

    /**
     * complete: exactly rounds 1 and 2, 30 steps each.
     * stopped:  rounds in order starting at 1; every round but the last has 30 steps,
     *           the last has 0 to 29 (Figma "Test stopped": "Before you stopped 18/30").
     */
    private static void checkRounds(BalanceTest request) {
        String status = request.getStatus() == null ? "" : request.getStatus().trim().toLowerCase();
        if (!status.equals(BalanceTest.COMPLETE) && !status.equals(BalanceTest.STOPPED)) {
            throw new IllegalArgumentException("status must be \"complete\" or \"stopped\"");
        }
        request.setStatus(status);

        List<TestRound> rounds = request.getRounds();
        if (rounds == null || rounds.isEmpty() || rounds.size() > ROUNDS_PER_TEST) {
            throw new IllegalArgumentException("A test has 1 or 2 rounds");
        }
        if (status.equals(BalanceTest.COMPLETE) && rounds.size() != ROUNDS_PER_TEST) {
            throw new IllegalArgumentException("A complete test has 2 rounds");
        }

        for (int i = 0; i < rounds.size(); i++) {
            TestRound round = rounds.get(i);
            boolean isLast = (i == rounds.size() - 1);
            if (round.getRound() != i + 1) {
                throw new IllegalArgumentException("Rounds must be numbered 1, 2 in order");
            }
            if (round.getStepPoints() == null) {
                throw new IllegalArgumentException("Round " + (i + 1) + " needs stepPoints");
            }
            int steps = round.getStepPoints().size();
            boolean mustBeFull = status.equals(BalanceTest.COMPLETE) || !isLast;
            if (mustBeFull && steps != STEPS_PER_ROUND) {
                throw new IllegalArgumentException("Round " + (i + 1) + " must have " + STEPS_PER_ROUND + " steps");
            }
            if (!mustBeFull && steps >= STEPS_PER_ROUND) {
                throw new IllegalArgumentException("A stopped test's last round has fewer than " + STEPS_PER_ROUND + " steps");
            }
            for (Long gap : round.getStepPoints()) {
                if (gap == null || gap <= 0 || gap > MAX_GAP_MS) {
                    throw new IllegalArgumentException("Each step gap must be between 1 ms and 2 minutes");
                }
            }
        }
    }

    /** Times must make sense: not in the future, not ancient, and round 2 starts after round 1 ends. */
    private static void checkTiming(BalanceTest test) {
        long now = System.currentTimeMillis();
        if (test.getStartedAt() < now - MAX_AGE_MS || test.getCompletedAt() > now + CLOCK_SLACK_MS) {
            throw new IllegalArgumentException("Test times must be within the last 30 days");
        }
        for (int i = 1; i < test.getRounds().size(); i++) {
            if (test.getRounds().get(i).getStartTime() < test.getRounds().get(i - 1).getStopTime()) {
                throw new IllegalArgumentException("Round " + (i + 1) + " must start after round " + i + " ends");
            }
        }
    }

    // ---------------------------------------------------------------- helpers

    /** If the score formula has changed since this test was saved, score it again and save it. */
    private static void rescoreIfOutdated(BalanceTest test) throws Exception {
        if (!ScoreCalculator.VERSION.equals(test.getScoreVersion())) {
            ScoreCalculator.score(test);
            store(test);
        }
    }

    private static void store(BalanceTest test) throws Exception {
        JedisData.loadToJedisWithIndexUsingScore(test, test.getTestId(), test.getCompletedAt(), "Profile", test.getProfileId());
    }

    /** Same default as the older SaveRapidStepTest: no device sent means the account's saved device. */
    private static String cleanDeviceId(String deviceId, User user) {
        String value = (deviceId == null || deviceId.isBlank()) ? user.getDeviceNickName() : deviceId.trim();
        if (value != null && value.length() > 64) {
            throw new IllegalArgumentException("deviceId is too long");
        }
        return value;
    }

    private static long parseTime(String value, long fallback, String name) {
        if (value == null || value.isBlank()) return fallback;
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(name + " must be a time in milliseconds, e.g. 1794300000000");
        }
    }
}
