package com.getsimplex.steptimer.service;

import com.getsimplex.steptimer.model.*;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.getsimplex.steptimer.service.LongitudinalFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class LongitudinalChangeInterpreterTest {

    private final MdcPolicy policy = new MdcPolicy();

    private List<TestSession> sessions(double previousScore, double currentScore, long elapsedMillis){
        List<RapidStepTest> trials = new ArrayList<>(session(BASE, previousScore, previousScore, null));
        trials.addAll(session(BASE + elapsedMillis, currentScore, currentScore, null));
        return LongitudinalSessionBuilder.build("p1", trials, policy);
    }

    private static MdcSnapshot snapshotWith(IntervalCategory category, EstimateCohort cohort, EstimateStatus status,
                                            double meanDifference, double mdc95, boolean systematic){
        PairedDifferenceStatistics stats = new PairedDifferenceStatistics();
        stats.setMeanPairedDifferenceSeconds(meanDifference);
        stats.setSdPairedDifferenceSeconds(mdc95 / 1.96);
        stats.setSemSeconds(mdc95 / 1.96 / Math.sqrt(2));
        stats.setMdc95Seconds(mdc95);
        stats.setMdc95Ci(new ConfidenceInterval(mdc95 * 0.8, mdc95 * 1.3));
        stats.setLimitsOfAgreementLowerSeconds(meanDifference - mdc95);
        stats.setLimitsOfAgreementUpperSeconds(meanDifference + mdc95);
        stats.setSystematicDifferenceDetected(systematic);

        MdcEstimate estimate = new MdcEstimate();
        estimate.setEstimateId("E1");
        estimate.setProtocolVersion(TestSession.UNVERSIONED_PROTOCOL);
        estimate.setIntervalCategory(category);
        estimate.setCohort(cohort);
        estimate.setStatus(status);
        estimate.getStatusReasons().add("Includes pairs with unknown participant stability");
        estimate.setStatistics(stats);
        estimate.setUniqueParticipants(85);
        estimate.setEligiblePairs(92);
        estimate.setModelVersion(MdcPolicy.MODEL_VERSION);

        MdcSnapshot snapshot = new MdcSnapshot();
        snapshot.setSnapshotId("1");
        snapshot.getEstimates().add(estimate);
        return snapshot;
    }

    @Test
    void reportsObservedChangeAndExploratoryComparison() throws Exception{
        MdcSnapshot snapshot = snapshotWith(IntervalCategory.MONTHLY, EstimateCohort.OBSERVED, EstimateStatus.EXPLORATORY, -0.5, 12.5, false);
        LongitudinalChangeInterpretation result = LongitudinalChangeInterpreter.interpret("participant@test.com", sessions(42, 35, 28 * DAY), null, null, snapshot);

        assertEquals(42.0, result.getPreviousScoreSeconds(), 1e-9);
        assertEquals(35.0, result.getCurrentScoreSeconds(), 1e-9);
        assertEquals(-7.0, result.getChangeSeconds(), 1e-9);
        assertEquals(-16.6666666667, result.getPercentChange(), 1e-6);
        assertEquals(28.0, result.getElapsedDays(), 1e-9);
        assertEquals(IntervalCategory.MONTHLY, result.getIntervalCategory());
        assertEquals(EstimateStatus.EXPLORATORY, result.getReliability().status);
        assertEquals(12.5, result.getReliability().mdc95Seconds, 1e-9);

        LongitudinalChangeInterpretation.Interpretation interpretation = result.getInterpretation();
        assertEquals("improvement", interpretation.direction);
        assertFalse(interpretation.exceedsMdc95);
        assertEquals("mdc95", interpretation.thresholdBasis);
        assertFalse(interpretation.thresholdSupportsIndividualInterpretation);
        assertFalse(interpretation.clinicalSignificanceEstablished);
        assertTrue(interpretation.message.startsWith("Completion time improved by 7.0 seconds. This change does not exceed the exploratory estimate"));
        assertTrue(interpretation.message.endsWith("That estimate is exploratory, not a validated threshold."));
    }

    @Test
    void researchSupportedEstimateSupportsIndividualInterpretationButNotClinicalClaims() throws Exception{
        MdcSnapshot snapshot = snapshotWith(IntervalCategory.WEEKLY, EstimateCohort.STABILITY_CONFIRMED, EstimateStatus.RESEARCH_SUPPORTED, 0.2, 5.0, false);
        LongitudinalChangeInterpretation result = LongitudinalChangeInterpreter.interpret("p", sessions(30, 38, 7 * DAY), null, null, snapshot);

        assertEquals("decline", result.getInterpretation().direction);
        assertTrue(result.getInterpretation().exceedsMdc95);
        assertTrue(result.getInterpretation().thresholdSupportsIndividualInterpretation);
        assertFalse(result.getInterpretation().clinicalSignificanceEstablished);
        assertTrue(result.getInterpretation().message.contains("exceeds the research-supported estimate"));
        assertTrue(result.getInterpretation().limitations.get(0).contains("does not establish a clinical improvement or deterioration"));
    }

    @Test
    void systematicDifferenceUsesLimitsOfAgreementNotSymmetricMdc() throws Exception{
        // participants typically get 4 s faster: limits of agreement -9 to +1 s, MDC95 5 s
        MdcSnapshot snapshot = snapshotWith(IntervalCategory.WEEKLY, EstimateCohort.STABILITY_CONFIRMED, EstimateStatus.RESEARCH_SUPPORTED, -4.0, 5.0, true);
        LongitudinalChangeInterpretation.Interpretation improvement = LongitudinalChangeInterpreter
                .interpret("p", sessions(40, 34, 7 * DAY), null, null, snapshot).getInterpretation();
        assertTrue(improvement.exceedsMdc95, "a 6 s change exceeds the symmetric 5 s MDC95");
        assertFalse(improvement.exceedsThreshold, "but is within the usual learning-related change");
        assertEquals("limits-of-agreement", improvement.thresholdBasis);

        LongitudinalChangeInterpretation.Interpretation decline = LongitudinalChangeInterpreter
                .interpret("p", sessions(40, 42, 7 * DAY), null, null, snapshot).getInterpretation();
        assertFalse(decline.exceedsMdc95);
        assertTrue(decline.exceedsThreshold, "slowing by 2 s is unusual when people usually speed up by 4 s");
    }

    @Test
    void insufficientEstimateGivesObservedChangeWithoutThresholdClaims() throws Exception{
        MdcSnapshot snapshot = snapshotWith(IntervalCategory.MONTHLY, EstimateCohort.STABILITY_CONFIRMED, EstimateStatus.INSUFFICIENT_DATA, 0, 5, false);
        LongitudinalChangeInterpretation result = LongitudinalChangeInterpreter.interpret("p", sessions(40, 30, 20 * DAY), null, null, snapshot);

        assertEquals(EstimateStatus.INSUFFICIENT_DATA, result.getReliability().status);
        assertNull(result.getReliability().mdc95Seconds);
        assertNull(result.getInterpretation().exceedsMdc95);
        assertNull(result.getInterpretation().exceedsThreshold);
        assertEquals("none", result.getInterpretation().thresholdBasis);
        assertTrue(result.getInterpretation().message.contains("cannot be distinguished from measurement variability"));
    }

    @Test
    void noSnapshotOrOtherIntervalHasNoReliability() throws Exception{
        assertNull(LongitudinalChangeInterpreter.interpret("p", sessions(40, 30, 20 * DAY), null, null, null).getReliability());
        MdcSnapshot weeklyOnly = snapshotWith(IntervalCategory.WEEKLY, EstimateCohort.STABILITY_CONFIRMED, EstimateStatus.RESEARCH_SUPPORTED, 0, 5, false);
        LongitudinalChangeInterpretation result = LongitudinalChangeInterpreter.interpret("p", sessions(40, 30, 20 * DAY), null, null, weeklyOnly);
        assertNull(result.getReliability(), "estimates are never borrowed from another interval");
        assertNull(result.getInterpretation().exceedsMdc95);
    }

    @Test
    void sameDaySessionsHaveNoCategory() throws Exception{
        LongitudinalChangeInterpretation result = LongitudinalChangeInterpreter.interpret("p", sessions(40, 30, 6 * 3_600_000L), null, null, null);
        assertNull(result.getIntervalCategory());
        assertTrue(result.getInterpretation().message.contains("less than one day apart"));
    }

    @Test
    void suppressedEstimateIsNotUsed() throws Exception{
        MdcSnapshot snapshot = snapshotWith(IntervalCategory.MONTHLY, EstimateCohort.OBSERVED, EstimateStatus.EXPLORATORY, 0, 5, false);
        snapshot.getEstimates().get(0).setSuppressed(true);
        assertNull(LongitudinalChangeInterpreter.interpret("p", sessions(40, 30, 20 * DAY), null, null, snapshot).getInterpretation().exceedsMdc95);
    }

    @Test
    void explicitSessionsAndIncompatibleRequests() throws Exception{
        List<RapidStepTest> trials = new ArrayList<>();
        trials.addAll(session(BASE, 40, 40, null));
        trials.addAll(session(BASE + 7 * DAY, 38, 38, null));
        trials.add(trial(BASE + 14 * DAY, 30));//unscored single trial
        List<RapidStepTest> v2 = session(BASE + 21 * DAY, 36, 36, null);
        v2.forEach(t -> t.setProtocolVersion("30-RST-v2"));
        trials.addAll(v2);
        List<TestSession> sessions = LongitudinalSessionBuilder.build("p1", trials, policy);

        LongitudinalChangeInterpretation explicit = LongitudinalChangeInterpreter.interpret("p", sessions,
                "S" + BASE, "S" + (BASE + 7 * DAY), null);
        assertEquals(-2.0, explicit.getChangeSeconds(), 1e-9);

        assertThrows(LongitudinalChangeInterpreter.NotComparableException.class,
                () -> LongitudinalChangeInterpreter.interpret("p", sessions, null, null, null), "latest two sessions use different protocols");
        assertThrows(LongitudinalChangeInterpreter.NotComparableException.class,
                () -> LongitudinalChangeInterpreter.interpret("p", sessions, "S" + BASE, "S" + (BASE + 14 * DAY), null), "single-trial session is unscored");
        assertThrows(LongitudinalChangeInterpreter.NotComparableException.class,
                () -> LongitudinalChangeInterpreter.interpret("p", sessions, "S" + (BASE + 7 * DAY), "S" + BASE, null), "order reversed");
        assertThrows(LongitudinalChangeInterpreter.NotComparableException.class,
                () -> LongitudinalChangeInterpreter.interpret("p", sessions, "S" + BASE, null, null));
        assertThrows(LongitudinalChangeInterpreter.NotComparableException.class,
                () -> LongitudinalChangeInterpreter.interpret("p", sessions.subList(0, 1), null, null, null), "needs two sessions");
    }
}
