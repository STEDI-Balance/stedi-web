package com.getsimplex.steptimer.model;

/**
 * A systematic change in score associated with trial order or repeated sessions.
 * An improvement here is not evidence of better balance; it may be familiarity with the test.
 */
public class LearningEffect {

    private String protocolVersion;
    private String factor;//"trial-order" or "session-number"
    private String comparison;//ex: "trial 2 vs trial 1", "session 3 vs previous scored session"
    private Integer uniqueParticipants;
    private Double meanDifferenceSeconds;//negative means faster on the later trial or session
    private ConfidenceInterval meanDifferenceCi;
    private Boolean systematicDifferenceDetected;
    private Double meanElapsedDays;
    private boolean suppressed;

    public String getProtocolVersion() {
        return protocolVersion;
    }

    public void setProtocolVersion(String protocolVersion) {
        this.protocolVersion = protocolVersion;
    }

    public String getFactor() {
        return factor;
    }

    public void setFactor(String factor) {
        this.factor = factor;
    }

    public String getComparison() {
        return comparison;
    }

    public void setComparison(String comparison) {
        this.comparison = comparison;
    }

    public Integer getUniqueParticipants() {
        return uniqueParticipants;
    }

    public void setUniqueParticipants(Integer uniqueParticipants) {
        this.uniqueParticipants = uniqueParticipants;
    }

    public Double getMeanDifferenceSeconds() {
        return meanDifferenceSeconds;
    }

    public void setMeanDifferenceSeconds(Double meanDifferenceSeconds) {
        this.meanDifferenceSeconds = meanDifferenceSeconds;
    }

    public ConfidenceInterval getMeanDifferenceCi() {
        return meanDifferenceCi;
    }

    public void setMeanDifferenceCi(ConfidenceInterval meanDifferenceCi) {
        this.meanDifferenceCi = meanDifferenceCi;
    }

    public Boolean getSystematicDifferenceDetected() {
        return systematicDifferenceDetected;
    }

    public void setSystematicDifferenceDetected(Boolean systematicDifferenceDetected) {
        this.systematicDifferenceDetected = systematicDifferenceDetected;
    }

    public Double getMeanElapsedDays() {
        return meanElapsedDays;
    }

    public void setMeanElapsedDays(Double meanElapsedDays) {
        this.meanElapsedDays = meanElapsedDays;
    }

    public boolean isSuppressed() {
        return suppressed;
    }

    public void setSuppressed(boolean suppressed) {
        this.suppressed = suppressed;
    }
}
