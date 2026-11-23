package com.getsimplex.steptimer.model;

import java.util.List;

/**
 * One Rapid Step Test taken by one profile (Figma: "Perform a test" and "History & trends").
 * Stored under the profile's ID, not a phone number, so family members each have their
 * own history and tests stay put if the account's phone number changes.
 *
 * Saved in Redis as:
 *   BalanceTestMap[testId]               -> this object as JSON
 *   BalanceTest (sorted set)             -> testId, scored by completedAt
 *   BalanceTestByProfile-<profileId>     -> testId, scored by completedAt (one person's history, in time order)
 *
 *   BalanceTestByClientId-<profileId>-<clientTestId> -> testId (finds a resent test, see clientTestId)
 *
 * The same class is the JSON body of POST /profiles/:id/tests. The app only sends
 * clientTestId, status, deviceId and rounds (startTime + stepPoints); the server fills in everything else.
 */
public class BalanceTest {

    public static final String COMPLETE = "complete";   // both rounds of 30 steps
    public static final String STOPPED = "stopped";     // ended early: kept, but never scored

    private String testId;
    private String clientTestId;    // made up by the app (a UUID); sending the same one twice saves only one test
    private String profileId;
    private String userName;        // the account that recorded it
    private String deviceId;        // the STEDI sensor used (optional)
    private String status;          // complete | stopped
    private List<TestRound> rounds;
    private long startedAt;         // round 1 start
    private long completedAt;       // last round's stop time
    private int stepCount;          // e.g. 60 for 2 x 30
    private long totalTimeMs;       // time spent stepping (both rounds, not the rest)  Figma: "Total time 48.2 s"
    private Long avgIntervalMs;     // average time per step                             Figma: "Avg. interval 0.80 s"
    private Double score;           // 0-100 balance score; null until the sponsor's formula exists
    private String scoreVersion;    // which version of ScoreCalculator made the score

    public String getTestId() { return testId; }
    public void setTestId(String testId) { this.testId = testId; }

    public String getClientTestId() { return clientTestId; }
    public void setClientTestId(String clientTestId) { this.clientTestId = clientTestId; }

    public String getProfileId() { return profileId; }
    public void setProfileId(String profileId) { this.profileId = profileId; }

    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public List<TestRound> getRounds() { return rounds; }
    public void setRounds(List<TestRound> rounds) { this.rounds = rounds; }

    public long getStartedAt() { return startedAt; }
    public void setStartedAt(long startedAt) { this.startedAt = startedAt; }

    public long getCompletedAt() { return completedAt; }
    public void setCompletedAt(long completedAt) { this.completedAt = completedAt; }

    public int getStepCount() { return stepCount; }
    public void setStepCount(int stepCount) { this.stepCount = stepCount; }

    public long getTotalTimeMs() { return totalTimeMs; }
    public void setTotalTimeMs(long totalTimeMs) { this.totalTimeMs = totalTimeMs; }

    public Long getAvgIntervalMs() { return avgIntervalMs; }
    public void setAvgIntervalMs(Long avgIntervalMs) { this.avgIntervalMs = avgIntervalMs; }

    public Double getScore() { return score; }
    public void setScore(Double score) { this.score = score; }

    public String getScoreVersion() { return scoreVersion; }
    public void setScoreVersion(String scoreVersion) { this.scoreVersion = scoreVersion; }
}
