package com.getsimplex.steptimer.service;

import com.getsimplex.steptimer.model.*;
import com.getsimplex.steptimer.utils.Pseudonymizer;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.*;

import static com.getsimplex.steptimer.service.LongitudinalFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class MdcSnapshotCalculatorTest {

    private static final double TOLERANCE = 1e-9;
    private static final double[] FIRST = {30.2, 25.4, 41.0, 35.5, 28.9, 33.3, 45.1, 38.7, 27.6, 31.8};
    private static final double[] SECOND = {28.1, 24.9, 37.2, 33.0, 29.5, 30.1, 41.8, 36.9, 26.0, 30.7};
    private static final Pseudonymizer PSEUDONYMIZER = new Pseudonymizer("test-secret".getBytes(StandardCharsets.UTF_8));
    private static final long NOW = BASE + 400 * DAY;

    private static MdcPolicy policy(int minParticipants){
        MdcPolicy policy = new MdcPolicy();
        policy.setMinParticipants(minParticipants);
        policy.setMinPairs(minParticipants);
        return policy;
    }

    private static MdcSnapshot calculate(List<ParticipantTestHistory> participants, MdcPolicy policy){
        return calculate(participants, policy, Collections.emptyMap());
    }

    private static MdcSnapshot calculate(List<ParticipantTestHistory> participants, MdcPolicy policy, Map<String, MdcValidationApproval> approvals){
        return MdcSnapshotCalculator.calculate(participants, policy, PSEUDONYMIZER, approvals, NOW, NOW);
    }

    private static MdcEstimate estimate(MdcSnapshot snapshot, IntervalCategory category, EstimateCohort cohort){
        return snapshot.getEstimates().stream()
                .filter(e -> e.getIntervalCategory() == category && e.getCohort() == cohort)
                .findFirst().orElseThrow();
    }

    // Participant i does one session: trial 1 = FIRST[i], trial 2 = SECOND[i]
    private static List<ParticipantTestHistory> withinSessionCohort(){
        List<ParticipantTestHistory> participants = new ArrayList<>();
        for (int i = 0; i < FIRST.length; i++){
            participants.add(new ParticipantTestHistory("+1801555000" + i, true, session(BASE + i * DAY, FIRST[i], SECOND[i], null)));
        }
        return participants;
    }

    // Each participant does two sessions 28 days apart, scored as the mean of two trials
    private static List<ParticipantTestHistory> monthlyCohort(int size, String healthChange){
        List<ParticipantTestHistory> participants = new ArrayList<>();
        for (int i = 0; i < size; i++){
            double first = 40 + (i % 7);
            double second = first - 1 + ((i * 3) % 5 - 2) * 0.5;
            List<RapidStepTest> trials = new ArrayList<>(session(BASE, first, first, null));
            trials.addAll(session(BASE + 28 * DAY, second, second, healthChange));
            participants.add(new ParticipantTestHistory("+1208555" + String.format("%04d", i), true, trials));
        }
        return participants;
    }

    @Test
    void knownDatasetFlowsThroughThePipelineUnchanged(){
        MdcSnapshot snapshot = calculate(withinSessionCohort(), policy(10));
        MdcEstimate within = estimate(snapshot, IntervalCategory.WITHIN_SESSION, EstimateCohort.STABILITY_CONFIRMED);

        assertEquals(10, within.getUniqueParticipants());
        assertEquals(10, within.getEligiblePairs());
        assertEquals(-1.93, within.getStatistics().getMeanPairedDifferenceSeconds(), TOLERANCE);
        assertEquals(0.9603529443791886, within.getStatistics().getSemSeconds(), TOLERANCE);
        assertEquals(2.661962550867727, within.getStatistics().getMdc95Seconds(), TOLERANCE);
        assertEquals(EstimateStatus.RESEARCH_SUPPORTED, within.getStatus());
        assertEquals("MDC95", within.getEstimateType());
        assertTrue(within.getStatusReasons().stream().anyMatch(r -> r.contains("limits of agreement")),
                "the systematic trial-order improvement must be flagged");
        assertEquals(10, snapshot.getParticipantsIncluded());
        assertEquals(MdcPolicy.MODEL_VERSION, snapshot.getModelVersion());
    }

    @Test
    void trialOrderLearningEffectIsReportedSeparatelyFromVariability(){
        MdcSnapshot snapshot = calculate(withinSessionCohort(), policy(10));
        LearningEffect trialOrder = snapshot.getLearningEffects().stream()
                .filter(e -> e.getFactor().equals("trial-order")).findFirst().orElseThrow();
        assertEquals(-1.93, trialOrder.getMeanDifferenceSeconds(), TOLERANCE);
        assertEquals(-2.901557803162508, trialOrder.getMeanDifferenceCi().getLower(), TOLERANCE);
        assertTrue(trialOrder.getSystematicDifferenceDetected());
    }

    @Test
    void sessionNumberLearningEffectIsEstimatedPerTransition(){
        // everyone improves 4 s from session 1 to 2, then holds steady (with symmetric noise) from 2 to 3
        List<ParticipantTestHistory> participants = new ArrayList<>();
        for (int i = 0; i < 12; i++){
            double noise = (i % 2 == 0 ? 0.5 : -0.5) * (1 + i % 3);
            List<RapidStepTest> trials = new ArrayList<>(session(BASE, 44, 44, null));
            trials.addAll(session(BASE + 7 * DAY, 40 + noise / 4, 40 + noise / 4, "NONE"));
            trials.addAll(session(BASE + 14 * DAY, 40 + noise, 40 + noise, "NONE"));
            participants.add(new ParticipantTestHistory("+1" + i, true, trials));
        }
        MdcSnapshot snapshot = calculate(participants, policy(10));
        LearningEffect second = snapshot.getLearningEffects().stream().filter(e -> e.getComparison().startsWith("session 2 ")).findFirst().orElseThrow();
        LearningEffect third = snapshot.getLearningEffects().stream().filter(e -> e.getComparison().startsWith("session 3 ")).findFirst().orElseThrow();

        assertEquals(12, second.getUniqueParticipants());
        assertTrue(second.getMeanDifferenceSeconds() < -3.5);
        assertTrue(second.getSystematicDifferenceDetected());
        assertEquals(0, third.getMeanDifferenceSeconds(), 0.2);
        assertFalse(third.getSystematicDifferenceDetected());
    }

    @Test
    void repeatedObservationsFromOneParticipantCountOnce(){
        List<RapidStepTest> trials = new ArrayList<>();
        for (int week = 0; week < 5; week++){
            trials.addAll(session(BASE + week * 7 * DAY, 40 - week, 40 - week, "NONE"));
        }
        List<ParticipantTestHistory> participants = new ArrayList<>(monthlyCohort(3, "NONE"));
        participants.add(new ParticipantTestHistory("+19995550000", true, trials));
        MdcEstimate weekly = estimate(calculate(participants, policy(2)), IntervalCategory.WEEKLY, EstimateCohort.STABILITY_CONFIRMED);

        assertEquals(4, weekly.getEligiblePairs());
        assertEquals(1, weekly.getUniqueParticipants());
        assertEquals(1, weekly.getPairsUsed());
        assertNull(weekly.getStatistics(), "one participant cannot estimate variability, however many pairs they have");
        assertEquals(EstimateStatus.INSUFFICIENT_DATA, weekly.getStatus());
    }

    @Test
    void pairSelectionPicksLatestOrEarliestPair(){
        List<SessionPair> pairs = List.of(
                new SessionPair("p1", "v", IntervalCategory.WEEKLY, 7, 40, 39, 2, BASE + 7 * DAY, ParticipantStability.STABLE),
                new SessionPair("p1", "v", IntervalCategory.WEEKLY, 7, 39, 38, 3, BASE + 14 * DAY, ParticipantStability.STABLE));
        MdcPolicy policy = new MdcPolicy();
        assertEquals(3, MdcSnapshotCalculator.onePairPerParticipant(pairs, policy).get(0).getLaterSessionNumber());
        policy.setPairSelection(MdcPolicy.PairSelection.EARLIEST);
        assertEquals(2, MdcSnapshotCalculator.onePairPerParticipant(pairs, policy).get(0).getLaterSessionNumber());
    }

    @Test
    void participantsWithoutConsentAreExcluded(){
        List<ParticipantTestHistory> participants = withinSessionCohort();
        participants.add(new ParticipantTestHistory("+1999", false, session(BASE, 90, 10, null)));
        MdcSnapshot snapshot = calculate(participants, policy(10));

        assertEquals(10, snapshot.getParticipantsIncluded());
        assertEquals(1, snapshot.getParticipantsExcludedWithoutConsent());
        assertEquals(2.661962550867727, estimate(snapshot, IntervalCategory.WITHIN_SESSION, EstimateCohort.STABILITY_CONFIRMED)
                .getStatistics().getMdc95Seconds(), TOLERANCE);
    }

    @Test
    void consentWithdrawalAndDeletionRemoveDataFromTheNextRecalculation(){
        List<ParticipantTestHistory> participants = withinSessionCohort();
        participants.add(new ParticipantTestHistory("+1999", true, session(BASE, 90, 10, null)));
        MdcSnapshot before = calculate(participants, policy(10));
        assertEquals(11, estimate(before, IntervalCategory.WITHIN_SESSION, EstimateCohort.STABILITY_CONFIRMED).getUniqueParticipants());

        participants.set(10, new ParticipantTestHistory("+1999", false, participants.get(10).getTests()));//withdrawn
        MdcSnapshot withdrawn = calculate(participants, policy(10));
        participants.remove(10);//deleted
        MdcSnapshot deleted = calculate(participants, policy(10));

        for (MdcSnapshot after : List.of(withdrawn, deleted)){
            MdcEstimate within = estimate(after, IntervalCategory.WITHIN_SESSION, EstimateCohort.STABILITY_CONFIRMED);
            assertEquals(10, within.getUniqueParticipants());
            assertEquals(2.661962550867727, within.getStatistics().getMdc95Seconds(), TOLERANCE);
            assertNotEquals(before.getInputFingerprint(), after.getInputFingerprint());
        }
        assertEquals(withdrawn.getInputFingerprint(), deleted.getInputFingerprint());
    }

    @Test
    void insufficientSampleIsExplicit(){
        MdcEstimate within = estimate(calculate(withinSessionCohort(), new MdcPolicy()), IntervalCategory.WITHIN_SESSION, EstimateCohort.STABILITY_CONFIRMED);
        assertEquals(EstimateStatus.INSUFFICIENT_DATA, within.getStatus());
        assertTrue(within.getStatusReasons().get(0).contains("at least 30"));

        MdcSnapshot empty = calculate(Collections.emptyList(), new MdcPolicy());
        assertTrue(empty.getEstimates().isEmpty());
        assertEquals(0, empty.getParticipantsIncluded());
    }

    @Test
    void unknownStabilityIsOnlyEverExploratory(){
        MdcSnapshot snapshot = calculate(monthlyCohort(200, null), policy(30));
        MdcEstimate confirmed = estimate(snapshot, IntervalCategory.MONTHLY, EstimateCohort.STABILITY_CONFIRMED);
        MdcEstimate observed = estimate(snapshot, IntervalCategory.MONTHLY, EstimateCohort.OBSERVED);

        assertEquals(0, confirmed.getEligiblePairs());
        assertEquals(EstimateStatus.INSUFFICIENT_DATA, confirmed.getStatus());
        assertEquals(200, observed.getUniqueParticipants());
        assertEquals(EstimateStatus.EXPLORATORY, observed.getStatus());
        assertEquals("observed test-retest variability", observed.getEstimateType());
    }

    @Test
    void reportedHealthChangesAreExcludedFromEveryEstimate(){
        List<ParticipantTestHistory> participants = new ArrayList<>(monthlyCohort(40, "NONE"));
        List<ParticipantTestHistory> injured = monthlyCohort(5, "INJURY");
        for (int i = 0; i < injured.size(); i++){
            participants.add(new ParticipantTestHistory("+44" + i, true, injured.get(i).getTests()));
        }
        MdcSnapshot snapshot = calculate(participants, policy(30));

        assertEquals(5, snapshot.getPairsWithReportedChangeExcluded());
        assertEquals(40, estimate(snapshot, IntervalCategory.MONTHLY, EstimateCohort.STABILITY_CONFIRMED).getEligiblePairs());
        assertEquals(40, estimate(snapshot, IntervalCategory.MONTHLY, EstimateCohort.OBSERVED).getEligiblePairs());
        assertEquals(EstimateStatus.RESEARCH_SUPPORTED, estimate(snapshot, IntervalCategory.MONTHLY, EstimateCohort.STABILITY_CONFIRMED).getStatus());
    }

    @Test
    void validatedRequiresAnApprovalForThatExactEstimate(){
        MdcSnapshot large = calculate(monthlyCohort(1000, "NONE"), policy(30));
        MdcEstimate monthly = estimate(large, IntervalCategory.MONTHLY, EstimateCohort.STABILITY_CONFIRMED);
        assertEquals(EstimateStatus.RESEARCH_SUPPORTED, monthly.getStatus(), "sample size alone never validates");

        MdcValidationApproval approval = new MdcValidationApproval();
        approval.setEstimateId(monthly.getEstimateId());
        approval.setApprovedBy("research-admin@test.com");
        MdcSnapshot approved = calculate(monthlyCohort(1000, "NONE"), policy(30), Map.of(monthly.getEstimateId(), approval));
        MdcEstimate validated = estimate(approved, IntervalCategory.MONTHLY, EstimateCohort.STABILITY_CONFIRMED);
        assertEquals(monthly.getEstimateId(), validated.getEstimateId(), "identical data and method give the same estimate id");
        assertEquals(EstimateStatus.VALIDATED, validated.getStatus());
        assertEquals("research-admin@test.com", validated.getValidation().getApprovedBy());

        // new data changes the estimate id, so the old approval no longer applies
        MdcSnapshot moreData = calculate(monthlyCohort(1001, "NONE"), policy(30), Map.of(monthly.getEstimateId(), approval));
        assertEquals(EstimateStatus.RESEARCH_SUPPORTED, estimate(moreData, IntervalCategory.MONTHLY, EstimateCohort.STABILITY_CONFIRMED).getStatus());
    }

    @Test
    void approvalCannotPromoteAnExploratoryEstimate(){
        MdcEstimate observed = estimate(calculate(monthlyCohort(40, null), policy(30)), IntervalCategory.MONTHLY, EstimateCohort.OBSERVED);
        MdcValidationApproval approval = new MdcValidationApproval();
        approval.setEstimateId(observed.getEstimateId());
        MdcEstimate after = estimate(calculate(monthlyCohort(40, null), policy(30), Map.of(observed.getEstimateId(), approval)),
                IntervalCategory.MONTHLY, EstimateCohort.OBSERVED);
        assertEquals(EstimateStatus.EXPLORATORY, after.getStatus());
        assertNull(after.getValidation());
    }

    @Test
    void wideConfidenceIntervalKeepsEstimateExploratory(){
        MdcPolicy policy = policy(10);
        policy.setMaxMdc95CiWidthSeconds(0.5);
        MdcEstimate within = estimate(calculate(withinSessionCohort(), policy), IntervalCategory.WITHIN_SESSION, EstimateCohort.STABILITY_CONFIRMED);
        assertEquals(EstimateStatus.EXPLORATORY, within.getStatus());
        assertTrue(within.getStatusReasons().stream().anyMatch(r -> r.contains("CI is 3.03 s wide")));
    }

    @Test
    void zeroVariabilityIsNotResearchSupported(){
        List<ParticipantTestHistory> participants = new ArrayList<>();
        for (int i = 0; i < 12; i++){
            participants.add(new ParticipantTestHistory("+1" + i, true, session(BASE, 30 + i, 28 + i, null)));
        }
        MdcEstimate within = estimate(calculate(participants, policy(10)), IntervalCategory.WITHIN_SESSION, EstimateCohort.STABILITY_CONFIRMED);
        assertTrue(within.getStatistics().isZeroVariability());
        assertEquals(EstimateStatus.EXPLORATORY, within.getStatus());
    }

    @Test
    void resultsAreReproducibleAndDoNotDependOnThePseudonymSecret(){
        MdcSnapshot first = calculate(monthlyCohort(40, "NONE"), policy(30));
        MdcSnapshot second = MdcSnapshotCalculator.calculate(monthlyCohort(40, "NONE"), policy(30),
                new Pseudonymizer("another-secret".getBytes(StandardCharsets.UTF_8)), Collections.emptyMap(), NOW, NOW);
        assertEquals(first.getInputFingerprint(), second.getInputFingerprint());
    }

    @Test
    void pseudonymsHideTheParticipantKey(){
        String pseudonym = PSEUDONYMIZER.pseudonym("+18015551234");
        assertFalse(pseudonym.contains("8015551234"));
        assertEquals(pseudonym, PSEUDONYMIZER.pseudonym("+18015551234"));
        assertNotEquals(pseudonym, new Pseudonymizer("other".getBytes(StandardCharsets.UTF_8)).pseudonym("+18015551234"));
    }
}
