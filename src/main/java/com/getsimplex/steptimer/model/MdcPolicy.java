package com.getsimplex.steptimer.model;

import java.util.Map;

/**
 * Configurable eligibility and status rules for MDC95 estimation, read from environment variables.
 * The defaults are conservative engineering choices, not validated statistical thresholds;
 * the research team should set them from a sample-size and precision analysis.
 */
public class MdcPolicy {

    public static final String MODEL_VERSION = "1.0.0";//bump when the statistical method changes
    public static final String SCORING_METHOD = "mean-of-first-two-trials";//matches the existing /riskscore scoring

    private int minParticipants = 30;//MDC_MIN_PARTICIPANTS: unique participants before an estimate leaves insufficient-data
    private int minPairs = 30;//MDC_MIN_PAIRS: eligible session pairs before an estimate leaves insufficient-data
    private int minPublicCohortSize = 10;//MDC_MIN_PUBLIC_COHORT: smaller cohorts are suppressed for non-admin callers
    private Double maxMdc95CiWidthSeconds;//MDC_MAX_CI_WIDTH_SECONDS: if set, research-supported also needs a narrower MDC95 CI
    private int sessionGapMinutes = 30;//MDC_SESSION_GAP_MINUTES: trials further apart than this start a new session
    private double maxTrialSeconds = 600;//MDC_MAX_TRIAL_SECONDS: longer trials are treated as invalid recordings
    private PairSelection pairSelection = PairSelection.LATEST;//MDC_PAIR_SELECTION: which pair each participant contributes

    public enum PairSelection {
        LATEST,//the participant's most recent eligible pair in the category, after the most familiarization
        EARLIEST
    }

    public static MdcPolicy fromEnvironment(){
        return fromEnvironment(System.getenv());
    }

    public static MdcPolicy fromEnvironment(Map<String, String> environment){
        MdcPolicy policy = new MdcPolicy();
        policy.minParticipants = intValue(environment, "MDC_MIN_PARTICIPANTS", policy.minParticipants);
        policy.minPairs = intValue(environment, "MDC_MIN_PAIRS", policy.minPairs);
        policy.minPublicCohortSize = intValue(environment, "MDC_MIN_PUBLIC_COHORT", policy.minPublicCohortSize);
        policy.sessionGapMinutes = intValue(environment, "MDC_SESSION_GAP_MINUTES", policy.sessionGapMinutes);
        String maxWidth = environment.get("MDC_MAX_CI_WIDTH_SECONDS");
        if (maxWidth != null && !maxWidth.isEmpty()){
            policy.maxMdc95CiWidthSeconds = Double.valueOf(maxWidth);
        }
        String maxTrial = environment.get("MDC_MAX_TRIAL_SECONDS");
        if (maxTrial != null && !maxTrial.isEmpty()){
            policy.maxTrialSeconds = Double.parseDouble(maxTrial);
        }
        String selection = environment.get("MDC_PAIR_SELECTION");
        if (selection != null && !selection.isEmpty()){
            policy.pairSelection = PairSelection.valueOf(selection.toUpperCase());
        }
        return policy;
    }

    private static int intValue(Map<String, String> environment, String name, int defaultValue){
        String value = environment.get(name);
        return value == null || value.isEmpty() ? defaultValue : Integer.parseInt(value);
    }

    public int getMinParticipants() {
        return minParticipants;
    }

    public void setMinParticipants(int minParticipants) {
        this.minParticipants = minParticipants;
    }

    public int getMinPairs() {
        return minPairs;
    }

    public void setMinPairs(int minPairs) {
        this.minPairs = minPairs;
    }

    public int getMinPublicCohortSize() {
        return minPublicCohortSize;
    }

    public void setMinPublicCohortSize(int minPublicCohortSize) {
        this.minPublicCohortSize = minPublicCohortSize;
    }

    public Double getMaxMdc95CiWidthSeconds() {
        return maxMdc95CiWidthSeconds;
    }

    public void setMaxMdc95CiWidthSeconds(Double maxMdc95CiWidthSeconds) {
        this.maxMdc95CiWidthSeconds = maxMdc95CiWidthSeconds;
    }

    public int getSessionGapMinutes() {
        return sessionGapMinutes;
    }

    public void setSessionGapMinutes(int sessionGapMinutes) {
        this.sessionGapMinutes = sessionGapMinutes;
    }

    public double getMaxTrialSeconds() {
        return maxTrialSeconds;
    }

    public void setMaxTrialSeconds(double maxTrialSeconds) {
        this.maxTrialSeconds = maxTrialSeconds;
    }

    public PairSelection getPairSelection() {
        return pairSelection;
    }

    public void setPairSelection(PairSelection pairSelection) {
        this.pairSelection = pairSelection;
    }
}
