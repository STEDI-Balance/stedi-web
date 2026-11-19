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
    private int participantsIncluded;
    private int participantsExcludedWithoutConsent;
    private int sessions;
    private int pairsWithReportedChangeExcluded;
    private int pairsUnderOneDay;
    private int pairsWithDifferentProtocols;
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

    public int getParticipantsIncluded() {
        return participantsIncluded;
    }

    public void setParticipantsIncluded(int participantsIncluded) {
        this.participantsIncluded = participantsIncluded;
    }

    public int getParticipantsExcludedWithoutConsent() {
        return participantsExcludedWithoutConsent;
    }

    public void setParticipantsExcludedWithoutConsent(int participantsExcludedWithoutConsent) {
        this.participantsExcludedWithoutConsent = participantsExcludedWithoutConsent;
    }

    public int getSessions() {
        return sessions;
    }

    public void setSessions(int sessions) {
        this.sessions = sessions;
    }

    public int getPairsWithReportedChangeExcluded() {
        return pairsWithReportedChangeExcluded;
    }

    public void setPairsWithReportedChangeExcluded(int pairsWithReportedChangeExcluded) {
        this.pairsWithReportedChangeExcluded = pairsWithReportedChangeExcluded;
    }

    public int getPairsUnderOneDay() {
        return pairsUnderOneDay;
    }

    public void setPairsUnderOneDay(int pairsUnderOneDay) {
        this.pairsUnderOneDay = pairsUnderOneDay;
    }

    public int getPairsWithDifferentProtocols() {
        return pairsWithDifferentProtocols;
    }

    public void setPairsWithDifferentProtocols(int pairsWithDifferentProtocols) {
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
