package com.getsimplex.steptimer.model;

/**
 * Test-retest statistics for n independent (first, second) score pairs, in seconds.
 * Differences are second minus first, so a negative mean difference means faster (better) second scores.
 */
public class PairedDifferenceStatistics {

    private int pairs;
    private double meanPairedDifferenceSeconds;
    private ConfidenceInterval meanPairedDifferenceCi;
    private boolean systematicDifferenceDetected;//the 95% CI of the mean difference excludes zero
    private double sdPairedDifferenceSeconds;
    private ConfidenceInterval sdPairedDifferenceCi;
    private double semSeconds;//SD of differences / sqrt(2), equal to sqrt(MSE) of the two-way ANOVA
    private ConfidenceInterval semCi;
    private double mdc95Seconds;//1.96 x sqrt(2) x SEM, which equals 1.96 x SD of differences
    private ConfidenceInterval mdc95Ci;
    private double limitsOfAgreementLowerSeconds;
    private double limitsOfAgreementUpperSeconds;
    private ConfidenceInterval limitsOfAgreementLowerCi;
    private ConfidenceInterval limitsOfAgreementUpperCi;
    private Double iccAbsoluteAgreement;//ICC(A,1); null when undefined (no between-participant variance)
    private boolean zeroVariability;

    public int getPairs() {
        return pairs;
    }

    public void setPairs(int pairs) {
        this.pairs = pairs;
    }

    public double getMeanPairedDifferenceSeconds() {
        return meanPairedDifferenceSeconds;
    }

    public void setMeanPairedDifferenceSeconds(double meanPairedDifferenceSeconds) {
        this.meanPairedDifferenceSeconds = meanPairedDifferenceSeconds;
    }

    public ConfidenceInterval getMeanPairedDifferenceCi() {
        return meanPairedDifferenceCi;
    }

    public void setMeanPairedDifferenceCi(ConfidenceInterval meanPairedDifferenceCi) {
        this.meanPairedDifferenceCi = meanPairedDifferenceCi;
    }

    public boolean isSystematicDifferenceDetected() {
        return systematicDifferenceDetected;
    }

    public void setSystematicDifferenceDetected(boolean systematicDifferenceDetected) {
        this.systematicDifferenceDetected = systematicDifferenceDetected;
    }

    public double getSdPairedDifferenceSeconds() {
        return sdPairedDifferenceSeconds;
    }

    public void setSdPairedDifferenceSeconds(double sdPairedDifferenceSeconds) {
        this.sdPairedDifferenceSeconds = sdPairedDifferenceSeconds;
    }

    public ConfidenceInterval getSdPairedDifferenceCi() {
        return sdPairedDifferenceCi;
    }

    public void setSdPairedDifferenceCi(ConfidenceInterval sdPairedDifferenceCi) {
        this.sdPairedDifferenceCi = sdPairedDifferenceCi;
    }

    public double getSemSeconds() {
        return semSeconds;
    }

    public void setSemSeconds(double semSeconds) {
        this.semSeconds = semSeconds;
    }

    public ConfidenceInterval getSemCi() {
        return semCi;
    }

    public void setSemCi(ConfidenceInterval semCi) {
        this.semCi = semCi;
    }

    public double getMdc95Seconds() {
        return mdc95Seconds;
    }

    public void setMdc95Seconds(double mdc95Seconds) {
        this.mdc95Seconds = mdc95Seconds;
    }

    public ConfidenceInterval getMdc95Ci() {
        return mdc95Ci;
    }

    public void setMdc95Ci(ConfidenceInterval mdc95Ci) {
        this.mdc95Ci = mdc95Ci;
    }

    public double getLimitsOfAgreementLowerSeconds() {
        return limitsOfAgreementLowerSeconds;
    }

    public void setLimitsOfAgreementLowerSeconds(double limitsOfAgreementLowerSeconds) {
        this.limitsOfAgreementLowerSeconds = limitsOfAgreementLowerSeconds;
    }

    public double getLimitsOfAgreementUpperSeconds() {
        return limitsOfAgreementUpperSeconds;
    }

    public void setLimitsOfAgreementUpperSeconds(double limitsOfAgreementUpperSeconds) {
        this.limitsOfAgreementUpperSeconds = limitsOfAgreementUpperSeconds;
    }

    public ConfidenceInterval getLimitsOfAgreementLowerCi() {
        return limitsOfAgreementLowerCi;
    }

    public void setLimitsOfAgreementLowerCi(ConfidenceInterval limitsOfAgreementLowerCi) {
        this.limitsOfAgreementLowerCi = limitsOfAgreementLowerCi;
    }

    public ConfidenceInterval getLimitsOfAgreementUpperCi() {
        return limitsOfAgreementUpperCi;
    }

    public void setLimitsOfAgreementUpperCi(ConfidenceInterval limitsOfAgreementUpperCi) {
        this.limitsOfAgreementUpperCi = limitsOfAgreementUpperCi;
    }

    public Double getIccAbsoluteAgreement() {
        return iccAbsoluteAgreement;
    }

    public void setIccAbsoluteAgreement(Double iccAbsoluteAgreement) {
        this.iccAbsoluteAgreement = iccAbsoluteAgreement;
    }

    public boolean isZeroVariability() {
        return zeroVariability;
    }

    public void setZeroVariability(boolean zeroVariability) {
        this.zeroVariability = zeroVariability;
    }
}
