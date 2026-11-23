package com.getsimplex.steptimer.service;

import com.getsimplex.steptimer.model.PairedDifferenceStatistics;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MdcStatisticsTest {

    private static final double TOLERANCE = 1e-9;

    // Expected values computed independently with numpy 2 / scipy 1.18.1 (ddof=1, t and chi2 ppf, ICC(A,1) from two-way ANOVA)
    private static final double[] FIRST = {30.2, 25.4, 41.0, 35.5, 28.9, 33.3, 45.1, 38.7, 27.6, 31.8};
    private static final double[] SECOND = {28.1, 24.9, 37.2, 33.0, 29.5, 30.1, 41.8, 36.9, 26.0, 30.7};

    @Test
    void knownDatasetMatchesIndependentCalculation(){
        PairedDifferenceStatistics stats = MdcStatistics.compute(FIRST, SECOND).get();

        assertEquals(10, stats.getPairs());
        assertEquals(-1.93, stats.getMeanPairedDifferenceSeconds(), TOLERANCE);
        assertEquals(-2.901557803162508, stats.getMeanPairedDifferenceCi().getLower(), TOLERANCE);
        assertEquals(-0.9584421968374923, stats.getMeanPairedDifferenceCi().getUpper(), TOLERANCE);
        assertEquals(1.3581441586059833, stats.getSdPairedDifferenceSeconds(), TOLERANCE);
        assertEquals(0.9341793681296927, stats.getSdPairedDifferenceCi().getLower(), TOLERANCE);
        assertEquals(2.4794418052805622, stats.getSdPairedDifferenceCi().getUpper(), TOLERANCE);
        assertEquals(0.9603529443791886, stats.getSemSeconds(), TOLERANCE);
        assertEquals(2.661962550867727, stats.getMdc95Seconds(), TOLERANCE);
        assertEquals(1.8309915615341976, stats.getMdc95Ci().getLower(), TOLERANCE);
        assertEquals(4.859705938349902, stats.getMdc95Ci().getUpper(), TOLERANCE);
        assertEquals(-4.591962550867727, stats.getLimitsOfAgreementLowerSeconds(), TOLERANCE);
        assertEquals(0.7319625508677268, stats.getLimitsOfAgreementUpperSeconds(), TOLERANCE);
        assertEquals(-6.274750028435193, stats.getLimitsOfAgreementLowerCi().getLower(), TOLERANCE);
        assertEquals(-2.9091750733002613, stats.getLimitsOfAgreementLowerCi().getUpper(), TOLERANCE);
        assertEquals(-0.9508249266997393, stats.getLimitsOfAgreementUpperCi().getLower(), TOLERANCE);
        assertEquals(2.4147500284351926, stats.getLimitsOfAgreementUpperCi().getUpper(), TOLERANCE);
        assertEquals(0.9253724448550644, stats.getIccAbsoluteAgreement(), TOLERANCE);
        assertTrue(stats.isSystematicDifferenceDetected(), "every participant but one was faster the second time");
    }

    @Test
    void mdcEqualsBothFormulas(){
        PairedDifferenceStatistics stats = MdcStatistics.compute(FIRST, SECOND).get();
        assertEquals(1.96 * Math.sqrt(2) * stats.getSemSeconds(), stats.getMdc95Seconds(), TOLERANCE);
        assertEquals(1.96 * stats.getSdPairedDifferenceSeconds(), stats.getMdc95Seconds(), TOLERANCE);
    }

    @Test
    void goldbergPublishedSemReproducesPublishedMdc(){
        // Goldberg and Talley (2015): SEM 3.4 s gives MDC95 9.4 s
        double sdOfDifferences = 3.4 * Math.sqrt(2);
        double[] first = {0, 0};
        double[] second = {-sdOfDifferences / Math.sqrt(2), sdOfDifferences / Math.sqrt(2)};//two differences with this SD
        PairedDifferenceStatistics stats = MdcStatistics.compute(first, second).get();
        assertEquals(3.4, stats.getSemSeconds(), TOLERANCE);
        assertEquals(9.4, stats.getMdc95Seconds(), 0.05);
    }

    @Test
    void learningEffectShiftsMeanButNotMdc(){
        double[] learned = new double[SECOND.length];
        for (int i = 0; i < SECOND.length; i++){
            learned[i] = SECOND[i] - 4.3;//everyone 4.3 s faster, the average improvement Goldberg reported
        }
        PairedDifferenceStatistics base = MdcStatistics.compute(FIRST, SECOND).get();
        PairedDifferenceStatistics shifted = MdcStatistics.compute(FIRST, learned).get();

        assertEquals(base.getMeanPairedDifferenceSeconds() - 4.3, shifted.getMeanPairedDifferenceSeconds(), TOLERANCE);
        assertEquals(base.getMdc95Seconds(), shifted.getMdc95Seconds(), TOLERANCE);
        assertEquals(base.getSemSeconds(), shifted.getSemSeconds(), TOLERANCE);
        assertTrue(shifted.isSystematicDifferenceDetected());
    }

    @Test
    void noSystematicDifferenceWhenChangesBalance(){
        double[] first = {30, 32, 34, 36, 38, 40};
        double[] second = {31, 31, 35, 35, 39, 39};
        PairedDifferenceStatistics stats = MdcStatistics.compute(first, second).get();
        assertEquals(0, stats.getMeanPairedDifferenceSeconds(), TOLERANCE);
        assertFalse(stats.isSystematicDifferenceDetected());
    }

    @Test
    void zeroVariability(){
        double[] first = {30, 35, 40};
        double[] second = {28, 33, 38};
        PairedDifferenceStatistics stats = MdcStatistics.compute(first, second).get();
        assertTrue(stats.isZeroVariability());
        assertEquals(0, stats.getMdc95Seconds(), TOLERANCE);
        assertEquals(0, stats.getMdc95Ci().getUpper(), TOLERANCE);
        assertEquals(-2, stats.getLimitsOfAgreementLowerSeconds(), TOLERANCE);
        assertTrue(stats.isSystematicDifferenceDetected());
    }

    @Test
    void identicalScoresHaveUndefinedIcc(){
        double[] same = {30, 30, 30};
        PairedDifferenceStatistics stats = MdcStatistics.compute(same, same).get();
        assertTrue(stats.isZeroVariability());
        assertNull(stats.getIccAbsoluteAgreement());
        assertFalse(stats.isSystematicDifferenceDetected());
    }

    @Test
    void nearZeroVariabilityStaysFinite(){
        double[] first = {30, 35, 40, 45};
        double[] second = {30 + 1e-9, 35, 40 - 1e-9, 45};
        PairedDifferenceStatistics stats = MdcStatistics.compute(first, second).get();
        assertFalse(stats.isZeroVariability());
        assertTrue(stats.getMdc95Seconds() > 0 && stats.getMdc95Seconds() < 1e-8);
        assertTrue(Double.isFinite(stats.getMdc95Ci().getUpper()));
    }

    @Test
    void fewerThanTwoPairsIsNotEstimable(){
        assertTrue(MdcStatistics.compute(new double[]{}, new double[]{}).isEmpty());
        assertTrue(MdcStatistics.compute(new double[]{30}, new double[]{28}).isEmpty());
    }

    @Test
    void invalidMeasurementsAreRejected(){
        assertThrows(IllegalArgumentException.class, () -> MdcStatistics.compute(new double[]{30, Double.NaN}, new double[]{28, 29}));
        assertThrows(IllegalArgumentException.class, () -> MdcStatistics.compute(new double[]{30}, new double[]{28, 29}));
    }
}
