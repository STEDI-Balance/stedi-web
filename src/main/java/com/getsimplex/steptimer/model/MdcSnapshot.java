package com.getsimplex.steptimer.model;

import java.util.ArrayList;
import java.util.List;

/**
 * One versioned recalculation of every MDC95 estimate. Snapshots hold only aggregates, are kept
 * after newer ones are made, and are referenced by interpretations so they stay reproducible.
 */
public class MdcSnapshot {

    private String snapshotId;
    private long computedAtMillis;
    private String computedAtUtc;
    private long dataChangedAtMillis;//the data-change marker this snapshot includes
    private String modelVersion;
    private MdcPolicy policy;
    private String inputFingerprint;
    private Integer participantsIncluded = 0;//null when suppressed
    private Integer participantsExcludedWithoutConsent = 0;//null when suppressed
    private Integer sessions = 0;//null when suppressed
    private Integer pairsWithReportedChangeExcluded = 0;//null when suppressed
    private Integer pairsUnderOneDay = 0;//null when suppressed
    private Integer pairsWithDifferentProtocols = 0;//null when suppressed
    private List<MdcEstimate> estimates = new ArrayList<>();
    private List<LearningEffect> learningEffects = new ArrayList<>();

    public String getSnapshotId() {
        return snapshotId;
    }

    public void setSnapshotId(String snapshotId) {
        this.snapshotId = snapshotId;
    }

    public long getComputedAtMillis() {
        return computedAtMillis;
    }

    public void setComputedAtMillis(long computedAtMillis) {
        this.computedAtMillis = computedAtMillis;
    }

    public String getComputedAtUtc() {
        return computedAtUtc;
    }

    public void setComputedAtUtc(String computedAtUtc) {
        this.computedAtUtc = computedAtUtc;
    }

    public long getDataChangedAtMillis() {
        return dataChangedAtMillis;
    }

    public void setDataChangedAtMillis(long dataChangedAtMillis) {
        this.dataChangedAtMillis = dataChangedAtMillis;
    }

    public String getModelVersion() {
        return modelVersion;
    }

    public void setModelVersion(String modelVersion) {
        this.modelVersion = modelVersion;
    }

    public MdcPolicy getPolicy() {
        return policy;
    }

    public void setPolicy(MdcPolicy policy) {
        this.policy = policy;
    }

    public String getInputFingerprint() {
        return inputFingerprint;
    }

    public void setInputFingerprint(String inputFingerprint) {
        this.inputFingerprint = inputFingerprint;
    }

    public Integer getParticipantsIncluded() {
        return participantsIncluded;
    }

    public void setParticipantsIncluded(Integer participantsIncluded) {
        this.participantsIncluded = participantsIncluded;
    }

    public Integer getParticipantsExcludedWithoutConsent() {
        return participantsExcludedWithoutConsent;
    }

    public void setParticipantsExcludedWithoutConsent(Integer participantsExcludedWithoutConsent) {
        this.participantsExcludedWithoutConsent = participantsExcludedWithoutConsent;
    }

    public Integer getSessions() {
        return sessions;
    }

    public void setSessions(Integer sessions) {
        this.sessions = sessions;
    }

    public Integer getPairsWithReportedChangeExcluded() {
        return pairsWithReportedChangeExcluded;
    }

    public void setPairsWithReportedChangeExcluded(Integer pairsWithReportedChangeExcluded) {
        this.pairsWithReportedChangeExcluded = pairsWithReportedChangeExcluded;
    }

    public Integer getPairsUnderOneDay() {
        return pairsUnderOneDay;
    }

    public void setPairsUnderOneDay(Integer pairsUnderOneDay) {
        this.pairsUnderOneDay = pairsUnderOneDay;
    }

    public Integer getPairsWithDifferentProtocols() {
        return pairsWithDifferentProtocols;
    }

    public void setPairsWithDifferentProtocols(Integer pairsWithDifferentProtocols) {
        this.pairsWithDifferentProtocols = pairsWithDifferentProtocols;
    }

    public List<MdcEstimate> getEstimates() {
        return estimates;
    }

    public void setEstimates(List<MdcEstimate> estimates) {
        this.estimates = estimates;
    }

    public List<LearningEffect> getLearningEffects() {
        return learningEffects;
    }

    public void setLearningEffects(List<LearningEffect> learningEffects) {
        this.learningEffects = learningEffects;
    }
}
