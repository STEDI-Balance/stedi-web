package com.getsimplex.steptimer.service;

import com.getsimplex.steptimer.model.IntervalCategory;
import com.getsimplex.steptimer.model.ParticipantStability;
import com.getsimplex.steptimer.model.SessionPair;
import com.getsimplex.steptimer.model.TestSession;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Builds test-retest pairs from one participant's sessions:
 * - within-session: trial 1 and trial 2 of a session (the Goldberg and Talley design)
 * - between-session: each scored session and the next scored session with the same protocol.
 *
 * Only consecutive scored sessions are paired, so the same session is never compared with
 * every later one. Each pair's stability combines the health reports of every session after
 * the first score up to the second.
 */
public class LongitudinalPairGenerator {

    public static final double MILLIS_PER_DAY = 86_400_000.0;

    public static class Result {
        private final List<SessionPair> pairs = new ArrayList<>();
        private int pairsUnderOneDay;//between-session pairs no provisional category covers
        private int pairsWithDifferentProtocols;

        public List<SessionPair> getPairs() {
            return pairs;
        }

        public int getPairsUnderOneDay() {
            return pairsUnderOneDay;
        }

        public int getPairsWithDifferentProtocols() {
            return pairsWithDifferentProtocols;
        }
    }

    public static Result generate(List<TestSession> sessions){
        Result result = new Result();

        for (TestSession session : sessions){
            if (session.isScored()){
                result.pairs.add(new SessionPair(session.getParticipantId(), session.getProtocolVersion(), IntervalCategory.WITHIN_SESSION,
                        (session.getTrialStartTimesMillis().get(1) - session.getTrialStartTimesMillis().get(0)) / MILLIS_PER_DAY,
                        session.getTrialTimesSeconds().get(0), session.getTrialTimesSeconds().get(1),
                        session.getSessionNumber(), session.getStartTimeMillis(), ParticipantStability.STABLE));
            }
        }

        TestSession previousScored = null;
        ParticipantStability stabilitySincePrevious = ParticipantStability.STABLE;
        for (TestSession session : sessions){
            if (previousScored != null){
                stabilitySincePrevious = combine(stabilitySincePrevious, session.getHealthReport());
            }
            if (!session.isScored()){
                continue;
            }
            if (previousScored != null){
                addBetweenSessionPair(result, previousScored, session, stabilitySincePrevious);
            }
            previousScored = session;
            stabilitySincePrevious = ParticipantStability.STABLE;
        }
        return result;
    }

    private static void addBetweenSessionPair(Result result, TestSession first, TestSession second, ParticipantStability stability){
        if (!first.hasComparableProtocol(second)){
            result.pairsWithDifferentProtocols++;
            return;
        }
        double elapsedDays = (second.getStartTimeMillis() - first.getStartTimeMillis()) / MILLIS_PER_DAY;
        Optional<IntervalCategory> category = IntervalCategory.forElapsedDays(elapsedDays);
        if (category.isEmpty()){
            result.pairsUnderOneDay++;
            return;
        }
        result.pairs.add(new SessionPair(first.getParticipantId(), first.getProtocolVersion(), category.get(), elapsedDays,
                first.getScoreSeconds(), second.getScoreSeconds(), second.getSessionNumber(), second.getStartTimeMillis(), stability));
    }

    private static ParticipantStability combine(ParticipantStability soFar, ParticipantStability next){
        if (soFar == ParticipantStability.CHANGE_REPORTED || next == ParticipantStability.CHANGE_REPORTED){
            return ParticipantStability.CHANGE_REPORTED;
        }
        if (soFar == ParticipantStability.UNKNOWN || next == ParticipantStability.UNKNOWN){
            return ParticipantStability.UNKNOWN;
        }
        return ParticipantStability.STABLE;
    }
}
