package com.getsimplex.steptimer.service;

import com.getsimplex.steptimer.model.*;
import com.getsimplex.steptimer.utils.Pseudonymizer;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Calculates a full MDC95 snapshot from participants' stored tests. Pure (no Redis) so it can be tested.
 *
 * Participants who have not agreed to share anonymous data are left out entirely. Each participant
 * contributes one pair per estimate (MdcPolicy.pairSelection), so repeated, overlapping observations
 * from the same person are never treated as independent.
 */
public class MdcSnapshotCalculator {

    static final String ESTIMATE_TYPE_MDC = "MDC95";
    static final String ESTIMATE_TYPE_OBSERVED = "observed test-retest variability";

    public static MdcSnapshot calculate(List<ParticipantTestHistory> participants, MdcPolicy policy, Pseudonymizer pseudonymizer,
                                        Map<String, MdcValidationApproval> approvals, long nowMillis, long dataChangedAtMillis){
        MdcSnapshot snapshot = new MdcSnapshot();
        snapshot.setComputedAtMillis(nowMillis);
        snapshot.setComputedAtUtc(Instant.ofEpochMilli(nowMillis).toString());
        snapshot.setSnapshotId(String.valueOf(nowMillis));
        snapshot.setDataChangedAtMillis(dataChangedAtMillis);
        snapshot.setModelVersion(MdcPolicy.MODEL_VERSION);
        snapshot.setPolicy(policy);

        List<SessionPair> pairs = new ArrayList<>();
        Set<String> included = new HashSet<>();
        for (ParticipantTestHistory participant : participants){
            if (!participant.isConsentedToShareAnonymousData()){
                snapshot.setParticipantsExcludedWithoutConsent(snapshot.getParticipantsExcludedWithoutConsent() + 1);
                continue;
            }
            String pseudonym = pseudonymizer.pseudonym(participant.getParticipantKey());
            if (!included.add(pseudonym)){
                continue;//the same participant key loaded twice
            }
            List<TestSession> sessions = LongitudinalSessionBuilder.build(pseudonym, participant.getTests(), policy);
            snapshot.setSessions(snapshot.getSessions() + sessions.size());
            LongitudinalPairGenerator.Result result = LongitudinalPairGenerator.generate(sessions);
            snapshot.setPairsUnderOneDay(snapshot.getPairsUnderOneDay() + result.getPairsUnderOneDay());
            snapshot.setPairsWithDifferentProtocols(snapshot.getPairsWithDifferentProtocols() + result.getPairsWithDifferentProtocols());
            pairs.addAll(result.getPairs());
        }
        snapshot.setParticipantsIncluded(included.size());
        snapshot.setPairsWithReportedChangeExcluded((int) pairs.stream().filter(p -> p.getStability() == ParticipantStability.CHANGE_REPORTED).count());

        Map<String, List<SessionPair>> byProtocol = pairs.stream()
                .filter(p -> p.getStability() != ParticipantStability.CHANGE_REPORTED)
                .collect(Collectors.groupingBy(SessionPair::getProtocolVersion, TreeMap::new, Collectors.toList()));

        for (Map.Entry<String, List<SessionPair>> protocol : byProtocol.entrySet()){
            for (IntervalCategory category : IntervalCategory.values()){
                List<SessionPair> categoryPairs = protocol.getValue().stream().filter(p -> p.getCategory() == category).collect(Collectors.toList());
                if (category == IntervalCategory.WITHIN_SESSION){
                    // trials minutes apart: the original Goldberg design, where true change is not expected
                    snapshot.getEstimates().add(estimate(protocol.getKey(), category, EstimateCohort.STABILITY_CONFIRMED, categoryPairs, policy, approvals));
                } else {
                    List<SessionPair> confirmed = categoryPairs.stream().filter(p -> p.getStability() == ParticipantStability.STABLE).collect(Collectors.toList());
                    snapshot.getEstimates().add(estimate(protocol.getKey(), category, EstimateCohort.STABILITY_CONFIRMED, confirmed, policy, approvals));
                    snapshot.getEstimates().add(estimate(protocol.getKey(), category, EstimateCohort.OBSERVED, categoryPairs, policy, approvals));
                }
            }
            snapshot.getLearningEffects().addAll(learningEffects(protocol.getKey(), protocol.getValue(), policy));
        }

        snapshot.setInputFingerprint(Pseudonymizer.sha256Hex(snapshot.getEstimates().stream()
                .map(MdcEstimate::getEstimateId).sorted().collect(Collectors.joining(","))).substring(0, 16));
        return snapshot;
    }

    static MdcEstimate estimate(String protocol, IntervalCategory category, EstimateCohort cohort, List<SessionPair> pairs,
                                MdcPolicy policy, Map<String, MdcValidationApproval> approvals){
        List<SessionPair> used = onePairPerParticipant(pairs, policy);

        MdcEstimate estimate = new MdcEstimate();
        estimate.setProtocolVersion(protocol);
        estimate.setIntervalCategory(category);
        estimate.setCohort(cohort);
        estimate.setEstimateType(cohort == EstimateCohort.STABILITY_CONFIRMED ? ESTIMATE_TYPE_MDC : ESTIMATE_TYPE_OBSERVED);
        estimate.setModelVersion(MdcPolicy.MODEL_VERSION);
        estimate.setEligiblePairs(pairs.size());
        estimate.setUniqueParticipants(used.size());
        estimate.setPairsUsed(used.size());
        estimate.setAnalysisMethod("One pair per participant (" + policy.getPairSelection().name().toLowerCase() + "); "
                + "MDC95 = 1.96 x sqrt(2) x SEM, SEM = SD(paired differences) / sqrt(2); "
                + "t-based 95% CI for the mean difference; chi-square 95% CI for SD, SEM and MDC95; "
                + "Bland-Altman 95% limits of agreement");
        if (!used.isEmpty()){
            DoubleSummaryStatistics elapsed = used.stream().mapToDouble(SessionPair::getElapsedDays).summaryStatistics();
            estimate.setMeanElapsedDays(elapsed.getAverage());
            estimate.setMinElapsedDays(elapsed.getMin());
            estimate.setMaxElapsedDays(elapsed.getMax());
        }
        double[] first = used.stream().mapToDouble(SessionPair::getFirstScoreSeconds).toArray();
        double[] second = used.stream().mapToDouble(SessionPair::getSecondScoreSeconds).toArray();
        estimate.setStatistics(MdcStatistics.compute(first, second).orElse(null));
        estimate.setEstimateId(estimateId(estimate, used, policy));
        assignStatus(estimate, policy, approvals.get(estimate.getEstimateId()));
        return estimate;
    }

    static List<SessionPair> onePairPerParticipant(List<SessionPair> pairs, MdcPolicy policy){
        Comparator<SessionPair> byTime = Comparator.comparingLong(SessionPair::getLaterStartTimeMillis);
        Map<String, SessionPair> chosen = pairs.stream().collect(Collectors.toMap(SessionPair::getParticipantId, Function.identity(),
                (a, b) -> policy.getPairSelection() == MdcPolicy.PairSelection.LATEST
                        ? (byTime.compare(a, b) >= 0 ? a : b)
                        : (byTime.compare(a, b) <= 0 ? a : b),
                TreeMap::new));
        return new ArrayList<>(chosen.values());
    }

    // Fingerprint of the data and method behind an estimate, without participant identifiers
    private static String estimateId(MdcEstimate estimate, List<SessionPair> used, MdcPolicy policy){
        String data = used.stream()
                .map(p -> String.format(Locale.ROOT, "%.3f,%.3f,%.4f", p.getFirstScoreSeconds(), p.getSecondScoreSeconds(), p.getElapsedDays()))
                .sorted().collect(Collectors.joining(";"));
        String method = String.join("|", MdcPolicy.MODEL_VERSION, MdcPolicy.SCORING_METHOD, policy.getPairSelection().name(),
                String.valueOf(policy.getSessionGapMinutes()), String.valueOf(policy.getMaxTrialSeconds()));
        String key = String.join("|", estimate.getProtocolVersion(), estimate.getIntervalCategory().getLabel(),
                estimate.getCohort().name(), String.valueOf(estimate.getEligiblePairs()), method, data);
        return "E" + Pseudonymizer.sha256Hex(key).substring(0, 20);
    }

    static void assignStatus(MdcEstimate estimate, MdcPolicy policy, MdcValidationApproval approval){
        List<String> reasons = estimate.getStatusReasons();
        PairedDifferenceStatistics stats = estimate.getStatistics();
        if (stats == null){
            estimate.setStatus(EstimateStatus.INSUFFICIENT_DATA);
            reasons.add("Variability cannot be estimated from fewer than 2 participants");
            return;
        }
        boolean enoughParticipants = estimate.getUniqueParticipants() >= policy.getMinParticipants();
        boolean enoughPairs = estimate.getEligiblePairs() >= policy.getMinPairs();
        if (!enoughParticipants){
            reasons.add(estimate.getUniqueParticipants() + " unique participants; at least " + policy.getMinParticipants() + " are required");
        }
        if (!enoughPairs){
            reasons.add(estimate.getEligiblePairs() + " eligible pairs; at least " + policy.getMinPairs() + " are required");
        }
        if (!enoughParticipants || !enoughPairs){
            estimate.setStatus(EstimateStatus.INSUFFICIENT_DATA);
            return;
        }

        estimate.setStatus(EstimateStatus.EXPLORATORY);
        if (estimate.getCohort() == EstimateCohort.OBSERVED){
            reasons.add("Includes pairs with unknown participant stability, so true change cannot be separated from measurement error");
        } else if (stats.isZeroVariability()){
            reasons.add("Zero variability between paired scores; check the data before relying on this estimate");
        } else if (policy.getMaxMdc95CiWidthSeconds() != null && stats.getMdc95Ci().width() > policy.getMaxMdc95CiWidthSeconds()){
            reasons.add(String.format(Locale.ROOT, "MDC95 95%% CI is %.2f s wide; at most %.2f s is required",
                    stats.getMdc95Ci().width(), policy.getMaxMdc95CiWidthSeconds()));
        } else {
            estimate.setStatus(EstimateStatus.RESEARCH_SUPPORTED);
        }

        if (stats.isSystematicDifferenceDetected()){
            reasons.add("Systematic difference between the paired scores: use the mean difference and limits of agreement, not a symmetric MDC95 alone");
        }
        if (approval != null){
            if (estimate.getStatus() == EstimateStatus.RESEARCH_SUPPORTED){
                estimate.setStatus(EstimateStatus.VALIDATED);
                estimate.setValidation(approval);
            } else {
                reasons.add("A validation approval exists but only applies to a research-supported estimate");
            }
        }
    }

    static List<LearningEffect> learningEffects(String protocol, List<SessionPair> eligiblePairs, MdcPolicy policy){
        List<LearningEffect> effects = new ArrayList<>();
        // trial order: trial 2 vs trial 1 within a session
        effects.add(learningEffect(protocol, "trial-order", "trial 2 vs trial 1",
                eligiblePairs.stream().filter(p -> p.getCategory() == IntervalCategory.WITHIN_SESSION).collect(Collectors.toList()), policy));

        // session number / previous exposure: each session vs the participant's previous scored session, over any interval
        List<SessionPair> between = eligiblePairs.stream().filter(p -> p.getCategory() != IntervalCategory.WITHIN_SESSION).collect(Collectors.toList());
        for (int sessionNumber = 2; sessionNumber <= 5; sessionNumber++){
            final int current = sessionNumber;
            boolean last = sessionNumber == 5;
            effects.add(learningEffect(protocol, "session-number",
                    "session " + current + (last ? "+" : "") + " vs previous scored session",
                    between.stream().filter(p -> last ? p.getLaterSessionNumber() >= current : p.getLaterSessionNumber() == current).collect(Collectors.toList()),
                    policy));
        }
        return effects;
    }

    private static LearningEffect learningEffect(String protocol, String factor, String comparison, List<SessionPair> pairs, MdcPolicy policy){
        List<SessionPair> used = onePairPerParticipant(pairs, policy);
        LearningEffect effect = new LearningEffect();
        effect.setProtocolVersion(protocol);
        effect.setFactor(factor);
        effect.setComparison(comparison);
        effect.setUniqueParticipants(used.size());
        MdcStatistics.compute(used.stream().mapToDouble(SessionPair::getFirstScoreSeconds).toArray(),
                used.stream().mapToDouble(SessionPair::getSecondScoreSeconds).toArray()).ifPresent(stats -> {
            effect.setMeanDifferenceSeconds(stats.getMeanPairedDifferenceSeconds());
            effect.setMeanDifferenceCi(stats.getMeanPairedDifferenceCi());
            effect.setSystematicDifferenceDetected(stats.isSystematicDifferenceDetected());
            effect.setMeanElapsedDays(used.stream().mapToDouble(SessionPair::getElapsedDays).average().orElse(0));
        });
        return effect;
    }
}
