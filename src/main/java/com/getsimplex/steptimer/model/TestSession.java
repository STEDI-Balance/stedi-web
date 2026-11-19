package com.getsimplex.steptimer.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * A testing session derived from a participant's stored rapid step tests (trials).
 * Sessions are not stored; they are rebuilt from the raw trials so scoring can be revisited later.
 */
public class TestSession {

    public static final String UNVERSIONED_PROTOCOL = "unversioned";
    public static final String MIXED_PROTOCOL = "mixed";

    private String participantId;
    private String sessionId;
    private int sessionNumber;//1 for the participant's first session
    private int previousTrialCount;//valid trials the participant completed before this session
    private long startTimeMillis;
    private String startTimeUtc;
    private String protocolVersion;
    private String softwareVersion;
    private List<Double> trialTimesSeconds = new ArrayList<>();//valid trials in the order they were completed
    private List<Long> trialStartTimesMillis = new ArrayList<>();
    private int completedTrials;
    private Double scoreSeconds;//null unless the session has the two trials the scoring method needs
    private String scoringMethod = MdcPolicy.SCORING_METHOD;
    private ParticipantStability healthReport;//what was reported in this session about the time since the last test

    public boolean isScored(){
        return scoreSeconds != null;
    }

    public boolean hasComparableProtocol(TestSession other){
        return !MIXED_PROTOCOL.equals(protocolVersion) && protocolVersion.equals(other.protocolVersion);
    }

    public String getParticipantId() {
        return participantId;
    }

    public void setParticipantId(String participantId) {
        this.participantId = participantId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public int getSessionNumber() {
        return sessionNumber;
    }

    public void setSessionNumber(int sessionNumber) {
        this.sessionNumber = sessionNumber;
    }

    public int getPreviousTrialCount() {
        return previousTrialCount;
    }

    public void setPreviousTrialCount(int previousTrialCount) {
        this.previousTrialCount = previousTrialCount;
    }

    public long getStartTimeMillis() {
        return startTimeMillis;
    }

    public void setStartTimeMillis(long startTimeMillis) {
        this.startTimeMillis = startTimeMillis;
        this.startTimeUtc = Instant.ofEpochMilli(startTimeMillis).toString();
    }

    public String getStartTimeUtc() {
        return startTimeUtc;
    }

    public String getProtocolVersion() {
        return protocolVersion;
    }

    public void setProtocolVersion(String protocolVersion) {
        this.protocolVersion = protocolVersion;
    }

    public String getSoftwareVersion() {
        return softwareVersion;
    }

    public void setSoftwareVersion(String softwareVersion) {
        this.softwareVersion = softwareVersion;
    }

    public List<Double> getTrialTimesSeconds() {
        return trialTimesSeconds;
    }

    public List<Long> getTrialStartTimesMillis() {
        return trialStartTimesMillis;
    }

    public int getCompletedTrials() {
        return completedTrials;
    }

    public void setCompletedTrials(int completedTrials) {
        this.completedTrials = completedTrials;
    }

    public Double getScoreSeconds() {
        return scoreSeconds;
    }

    public void setScoreSeconds(Double scoreSeconds) {
        this.scoreSeconds = scoreSeconds;
    }

    public String getScoringMethod() {
        return scoringMethod;
    }

    public ParticipantStability getHealthReport() {
        return healthReport;
    }

    public void setHealthReport(ParticipantStability healthReport) {
        this.healthReport = healthReport;
    }
}
