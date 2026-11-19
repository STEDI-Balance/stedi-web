package com.getsimplex.steptimer.service;

import com.getsimplex.steptimer.model.*;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Interprets a participant's change between two scored sessions against the estimate that
 * applies to the same protocol and interval category. Estimates are never interpolated
 * between categories. When the estimate shows a systematic difference, the asymmetric limits
 * of agreement are used instead of the symmetric MDC95.
 */
public class LongitudinalChangeInterpreter {

    public static class NotComparableException extends Exception {
        public NotComparableException(String message){
            super(message);
        }
    }

    // previousSessionId and currentSessionId are optional; without them the two most recent scored sessions are compared
    public static LongitudinalChangeInterpretation interpret(String participantId, List<TestSession> sessions, String previousSessionId,
                                                             String currentSessionId, MdcSnapshot snapshot) throws NotComparableException{
        List<TestSession> scored = sessions.stream().filter(TestSession::isScored)
                .sorted(Comparator.comparingLong(TestSession::getStartTimeMillis)).collect(Collectors.toList());
        TestSession previous;
        TestSession current;
        if (previousSessionId != null || currentSessionId != null){
            if (previousSessionId == null || currentSessionId == null){
                throw new NotComparableException("Provide both previousSessionId and currentSessionId, or neither");
            }
            previous = find(scored, previousSessionId);
            current = find(scored, currentSessionId);
        } else {
            if (scored.size() < 2){
                throw new NotComparableException("At least two scored sessions (each with two completed trials) are needed");
            }
            previous = scored.get(scored.size() - 2);
            current = scored.get(scored.size() - 1);
        }
        if (previous.getStartTimeMillis() >= current.getStartTimeMillis()){
            throw new NotComparableException("The previous session must start before the current session");
        }
        if (!previous.hasComparableProtocol(current)){
            throw new NotComparableException("Sessions used different test protocols (" + previous.getProtocolVersion()
                    + ", " + current.getProtocolVersion() + ") and cannot be compared");
        }

        LongitudinalChangeInterpretation result = new LongitudinalChangeInterpretation();
        result.setParticipantId(participantId);
        result.setPreviousSessionId(previous.getSessionId());
        result.setCurrentSessionId(current.getSessionId());
        result.setPreviousSessionStartUtc(previous.getStartTimeUtc());
        result.setCurrentSessionStartUtc(current.getStartTimeUtc());
        result.setProtocolVersion(current.getProtocolVersion());
        result.setPreviousScoreSeconds(previous.getScoreSeconds());
        result.setCurrentScoreSeconds(current.getScoreSeconds());
        double change = current.getScoreSeconds() - previous.getScoreSeconds();
        result.setChangeSeconds(change);
        result.setPercentChange(100 * change / previous.getScoreSeconds());
        double elapsedDays = (current.getStartTimeMillis() - previous.getStartTimeMillis()) / LongitudinalPairGenerator.MILLIS_PER_DAY;
        result.setElapsedDays(elapsedDays);
        Optional<IntervalCategory> category = IntervalCategory.forElapsedDays(elapsedDays);
        result.setIntervalCategory(category.orElse(null));

        Optional<MdcEstimate> estimate = category.flatMap(c -> applicableEstimate(snapshot, current.getProtocolVersion(), c));
        estimate.ifPresent(e -> result.setReliability(reliability(snapshot, e)));
        Optional<MdcEstimate> usable = estimate.filter(LongitudinalChangeInterpreter::isUsable);
        describe(result.getInterpretation(), change, category.isPresent(), usable);
        return result;
    }

    private static TestSession find(List<TestSession> scored, String sessionId) throws NotComparableException{
        return scored.stream().filter(s -> s.getSessionId().equals(sessionId)).findFirst()
                .orElseThrow(() -> new NotComparableException("No scored session " + sessionId + " (a session needs two completed trials)"));
    }

    // Prefer a stability-confirmed estimate, then observed variability; report an insufficient one if nothing better exists
    static Optional<MdcEstimate> applicableEstimate(MdcSnapshot snapshot, String protocol, IntervalCategory category){
        if (snapshot == null){
            return Optional.empty();
        }
        List<MdcEstimate> matching = snapshot.getEstimates().stream()
                .filter(e -> e.getProtocolVersion().equals(protocol) && e.getIntervalCategory() == category)
                .collect(Collectors.toList());
        for (EstimateCohort cohort : EstimateCohort.values()){
            Optional<MdcEstimate> found = matching.stream().filter(e -> e.getCohort() == cohort && isUsable(e)).findFirst();
            if (found.isPresent()){
                return found;
            }
        }
        return matching.stream().filter(e -> e.getCohort() == EstimateCohort.STABILITY_CONFIRMED).findFirst();
    }

    static boolean isUsable(MdcEstimate estimate){
        return !estimate.isSuppressed() && estimate.getStatistics() != null && estimate.getStatus().atLeast(EstimateStatus.EXPLORATORY);
    }

    private static LongitudinalChangeInterpretation.Reliability reliability(MdcSnapshot snapshot, MdcEstimate estimate){
        LongitudinalChangeInterpretation.Reliability reliability = new LongitudinalChangeInterpretation.Reliability();
        reliability.snapshotId = snapshot.getSnapshotId();
        reliability.estimateId = estimate.getEstimateId();
        reliability.cohort = estimate.getCohort();
        reliability.estimateType = estimate.getEstimateType();
        reliability.status = estimate.getStatus();
        reliability.modelVersion = estimate.getModelVersion();
        reliability.uniqueParticipants = estimate.getUniqueParticipants();
        reliability.eligiblePairs = estimate.getEligiblePairs();
        if (isUsable(estimate)){
            PairedDifferenceStatistics stats = estimate.getStatistics();
            reliability.mdc95Seconds = stats.getMdc95Seconds();
            reliability.mdc95Ci = stats.getMdc95Ci();
            reliability.semSeconds = stats.getSemSeconds();
            reliability.meanPairedDifferenceSeconds = stats.getMeanPairedDifferenceSeconds();
            reliability.systematicDifferenceDetected = stats.isSystematicDifferenceDetected();
            reliability.limitsOfAgreementLowerSeconds = stats.getLimitsOfAgreementLowerSeconds();
            reliability.limitsOfAgreementUpperSeconds = stats.getLimitsOfAgreementUpperSeconds();
        }
        return reliability;
    }

    private static void describe(LongitudinalChangeInterpretation.Interpretation interpretation, double change, boolean categorized,
                                 Optional<MdcEstimate> usable){
        interpretation.direction = change < 0 ? "improvement" : change > 0 ? "decline" : "no-change";
        String observed = change < 0 ? String.format(Locale.ROOT, "Completion time improved by %.1f seconds.", -change)
                : change > 0 ? String.format(Locale.ROOT, "Completion time increased by %.1f seconds.", change)
                : "Completion time did not change.";

        interpretation.limitations.add("Exceeding a measurement-variability threshold does not establish a clinical improvement or deterioration in balance or mobility.");
        interpretation.limitations.add("Changes can reflect familiarity with the test, health, effort, or testing conditions as well as balance.");

        if (usable.isEmpty()){
            interpretation.thresholdBasis = "none";
            interpretation.message = observed + (categorized
                    ? " No sufficiently supported estimate of measurement variability exists yet for this interval, so this change cannot be distinguished from measurement variability."
                    : " The sessions were less than one day apart; no between-session estimate of measurement variability covers intervals that short.");
            return;
        }

        MdcEstimate estimate = usable.get();
        PairedDifferenceStatistics stats = estimate.getStatistics();
        interpretation.exceedsMdc95 = Math.abs(change) > stats.getMdc95Seconds();
        interpretation.outsideLimitsOfAgreement = change < stats.getLimitsOfAgreementLowerSeconds() || change > stats.getLimitsOfAgreementUpperSeconds();
        interpretation.thresholdSupportsIndividualInterpretation = estimate.getStatus().atLeast(EstimateStatus.RESEARCH_SUPPORTED);
        String label = interpretation.thresholdSupportsIndividualInterpretation
                ? (estimate.getStatus() == EstimateStatus.VALIDATED ? "the validated" : "the research-supported")
                : "the exploratory";

        String comparison;
        if (stats.isSystematicDifferenceDetected()){
            interpretation.thresholdBasis = "limits-of-agreement";
            interpretation.exceedsThreshold = interpretation.outsideLimitsOfAgreement;
            comparison = String.format(Locale.ROOT,
                    " Participants change by %.1f seconds on average over this interval, so the change is compared with %s 95%% limits of agreement (%.1f to %.1f seconds); it %s them.",
                    stats.getMeanPairedDifferenceSeconds(), label, stats.getLimitsOfAgreementLowerSeconds(), stats.getLimitsOfAgreementUpperSeconds(),
                    interpretation.exceedsThreshold ? "falls outside" : "falls within");
        } else {
            interpretation.thresholdBasis = "mdc95";
            interpretation.exceedsThreshold = interpretation.exceedsMdc95;
            comparison = String.format(Locale.ROOT, " This change %s %s estimate of measurement variability for this interval (MDC95 %.1f seconds).",
                    interpretation.exceedsThreshold ? "exceeds" : "does not exceed", label, stats.getMdc95Seconds());
        }
        interpretation.message = observed + comparison;
        if (!interpretation.thresholdSupportsIndividualInterpretation){
            interpretation.message += " That estimate is exploratory, not a validated threshold.";
            interpretation.limitations.add("The applicable estimate is exploratory: " + String.join("; ", estimate.getStatusReasons()) + ".");
        }
    }
}
