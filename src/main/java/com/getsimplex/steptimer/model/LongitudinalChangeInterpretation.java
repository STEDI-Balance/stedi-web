package com.getsimplex.steptimer.model;

import java.util.ArrayList;
import java.util.List;

/**
 * A participant's change between two compatible sessions, with the measurement-variability
 * estimate that applies to the elapsed time. Never claims clinical improvement or deterioration.
 */
public class LongitudinalChangeInterpretation {

    private String participantId;
    private String previousSessionId;
    private String currentSessionId;
    private String previousSessionStartUtc;
    private String currentSessionStartUtc;
    private Double previousScoreSeconds;
    private Double currentScoreSeconds;
    private Double changeSeconds;//current minus previous: negative is faster
    private Double percentChange;
    private Double elapsedDays;
    private IntervalCategory intervalCategory;//null when under one day, which no category covers
    private String protocolVersion;
    private String scoringMethod = MdcPolicy.SCORING_METHOD;
    private Reliability reliability;//null when no estimate exists for this protocol and interval
    private Interpretation interpretation = new Interpretation();

    public static class Reliability {
        public String snapshotId;
        public String estimateId;
        public EstimateCohort cohort;
        public String estimateType;
        public EstimateStatus status;
        public Double mdc95Seconds;
        public ConfidenceInterval mdc95Ci;
        public Double semSeconds;
        public Double meanPairedDifferenceSeconds;
        public Boolean systematicDifferenceDetected;
        public Double limitsOfAgreementLowerSeconds;
        public Double limitsOfAgreementUpperSeconds;
        public Integer uniqueParticipants;
        public Integer eligiblePairs;
        public String modelVersion;
    }

    public static class Interpretation {
        public String direction;//"improvement" (faster), "decline" (slower), or "no-change"
        public Boolean exceedsMdc95;//null when no usable estimate exists
        public Boolean outsideLimitsOfAgreement;
        public String thresholdBasis;//"mdc95", "limits-of-agreement", or "none"
        public Boolean exceedsThreshold;//against thresholdBasis; null when no usable estimate exists
        public boolean thresholdSupportsIndividualInterpretation;//only for research-supported or validated estimates
        public boolean clinicalSignificanceEstablished = false;//never established by this calculation
        public String message;
        public List<String> limitations = new ArrayList<>();
    }

    public String getParticipantId() {
        return participantId;
    }

    public void setParticipantId(String participantId) {
        this.participantId = participantId;
    }

    public String getPreviousSessionId() {
        return previousSessionId;
    }

    public void setPreviousSessionId(String previousSessionId) {
        this.previousSessionId = previousSessionId;
    }

    public String getCurrentSessionId() {
        return currentSessionId;
    }

    public void setCurrentSessionId(String currentSessionId) {
        this.currentSessionId = currentSessionId;
    }

    public String getPreviousSessionStartUtc() {
        return previousSessionStartUtc;
    }

    public void setPreviousSessionStartUtc(String previousSessionStartUtc) {
        this.previousSessionStartUtc = previousSessionStartUtc;
    }

    public String getCurrentSessionStartUtc() {
        return currentSessionStartUtc;
    }

    public void setCurrentSessionStartUtc(String currentSessionStartUtc) {
        this.currentSessionStartUtc = currentSessionStartUtc;
    }

    public Double getPreviousScoreSeconds() {
        return previousScoreSeconds;
    }

    public void setPreviousScoreSeconds(Double previousScoreSeconds) {
        this.previousScoreSeconds = previousScoreSeconds;
    }

    public Double getCurrentScoreSeconds() {
        return currentScoreSeconds;
    }

    public void setCurrentScoreSeconds(Double currentScoreSeconds) {
        this.currentScoreSeconds = currentScoreSeconds;
    }

    public Double getChangeSeconds() {
        return changeSeconds;
    }

    public void setChangeSeconds(Double changeSeconds) {
        this.changeSeconds = changeSeconds;
    }

    public Double getPercentChange() {
        return percentChange;
    }

    public void setPercentChange(Double percentChange) {
        this.percentChange = percentChange;
    }

    public Double getElapsedDays() {
        return elapsedDays;
    }

    public void setElapsedDays(Double elapsedDays) {
        this.elapsedDays = elapsedDays;
    }

    public IntervalCategory getIntervalCategory() {
        return intervalCategory;
    }

    public void setIntervalCategory(IntervalCategory intervalCategory) {
        this.intervalCategory = intervalCategory;
    }

    public String getProtocolVersion() {
        return protocolVersion;
    }

    public void setProtocolVersion(String protocolVersion) {
        this.protocolVersion = protocolVersion;
    }

    public String getScoringMethod() {
        return scoringMethod;
    }

    public Reliability getReliability() {
        return reliability;
    }

    public void setReliability(Reliability reliability) {
        this.reliability = reliability;
    }

    public Interpretation getInterpretation() {
        return interpretation;
    }
}
