package com.getsimplex.steptimer.model;

/**
 * A research or administrative approval that promotes one exact estimate to VALIDATED.
 * The estimate id is a fingerprint of the estimate's data and method, so the approval stops
 * applying as soon as new data or a new method changes the estimate.
 */
public class MdcValidationApproval {

    private String estimateId;
    private String approvedBy;
    private String approvedAtUtc;
    private String reference;//ex: the study, protocol, or decision record that supports the approval

    public String getEstimateId() {
        return estimateId;
    }

    public void setEstimateId(String estimateId) {
        this.estimateId = estimateId;
    }

    public String getApprovedBy() {
        return approvedBy;
    }

    public void setApprovedBy(String approvedBy) {
        this.approvedBy = approvedBy;
    }

    public String getApprovedAtUtc() {
        return approvedAtUtc;
    }

    public void setApprovedAtUtc(String approvedAtUtc) {
        this.approvedAtUtc = approvedAtUtc;
    }

    public String getReference() {
        return reference;
    }

    public void setReference(String reference) {
        this.reference = reference;
    }
}
