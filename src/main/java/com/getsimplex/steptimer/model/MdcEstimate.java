package com.getsimplex.steptimer.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Measurement variability for one protocol, interval category, and cohort.
 */
public class MdcEstimate {

    private String estimateId;
    private String protocolVersion;
    private IntervalCategory intervalCategory;
    private EstimateCohort cohort;
    private String estimateType;//"MDC95" or "observed test-retest variability"
    private EstimateStatus status;
    private List<String> statusReasons = new ArrayList<>();
    private Integer uniqueParticipants;
    private Integer eligiblePairs;
    private Integer pairsUsed;//one per participant
    private Double meanElapsedDays;
    private Double minElapsedDays;
    private Double maxElapsedDays;
    private PairedDifferenceStatistics statistics;//null when fewer than 2 participants
    private String analysisMethod;
    private String modelVersion;
    private MdcValidationApproval validation;
    private boolean suppressed;//true when the cohort is too small to show to non-admin callers

    public String getEstimateId() {
        return estimateId;
    }

    public void setEstimateId(String estimateId) {
        this.estimateId = estimateId;
    }

    public String getProtocolVersion() {
        return protocolVersion;
    }

    public void setProtocolVersion(String protocolVersion) {
        this.protocolVersion = protocolVersion;
    }

    public IntervalCategory getIntervalCategory() {
        return intervalCategory;
    }

    public void setIntervalCategory(IntervalCategory intervalCategory) {
        this.intervalCategory = intervalCategory;
    }

    public EstimateCohort getCohort() {
        return cohort;
    }

    public void setCohort(EstimateCohort cohort) {
        this.cohort = cohort;
    }

    public String getEstimateType() {
        return estimateType;
    }

    public void setEstimateType(String estimateType) {
        this.estimateType = estimateType;
    }

    public EstimateStatus getStatus() {
        return status;
    }

    public void setStatus(EstimateStatus status) {
        this.status = status;
    }

    public List<String> getStatusReasons() {
        return statusReasons;
    }

    public void setStatusReasons(List<String> statusReasons) {
        this.statusReasons = statusReasons;
    }

    public Integer getUniqueParticipants() {
        return uniqueParticipants;
    }

    public void setUniqueParticipants(Integer uniqueParticipants) {
        this.uniqueParticipants = uniqueParticipants;
    }

    public Integer getEligiblePairs() {
        return eligiblePairs;
    }

    public void setEligiblePairs(Integer eligiblePairs) {
        this.eligiblePairs = eligiblePairs;
    }

    public Integer getPairsUsed() {
        return pairsUsed;
    }

    public void setPairsUsed(Integer pairsUsed) {
        this.pairsUsed = pairsUsed;
    }

    public Double getMeanElapsedDays() {
        return meanElapsedDays;
    }

    public void setMeanElapsedDays(Double meanElapsedDays) {
        this.meanElapsedDays = meanElapsedDays;
    }

    public Double getMinElapsedDays() {
        return minElapsedDays;
    }

    public void setMinElapsedDays(Double minElapsedDays) {
        this.minElapsedDays = minElapsedDays;
    }

    public Double getMaxElapsedDays() {
        return maxElapsedDays;
    }

    public void setMaxElapsedDays(Double maxElapsedDays) {
        this.maxElapsedDays = maxElapsedDays;
    }

    public PairedDifferenceStatistics getStatistics() {
        return statistics;
    }

    public void setStatistics(PairedDifferenceStatistics statistics) {
        this.statistics = statistics;
    }

    public String getAnalysisMethod() {
        return analysisMethod;
    }

    public void setAnalysisMethod(String analysisMethod) {
        this.analysisMethod = analysisMethod;
    }

    public String getModelVersion() {
        return modelVersion;
    }

    public void setModelVersion(String modelVersion) {
        this.modelVersion = modelVersion;
    }

    public MdcValidationApproval getValidation() {
        return validation;
    }

    public void setValidation(MdcValidationApproval validation) {
        this.validation = validation;
    }

    public boolean isSuppressed() {
        return suppressed;
    }

    public void setSuppressed(boolean suppressed) {
        this.suppressed = suppressed;
    }
}
