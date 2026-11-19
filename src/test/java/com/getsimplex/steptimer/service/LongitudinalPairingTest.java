package com.getsimplex.steptimer.service;

import com.getsimplex.steptimer.model.IntervalCategory;
import com.getsimplex.steptimer.model.MdcPolicy;
import com.getsimplex.steptimer.model.ParticipantStability;
import com.getsimplex.steptimer.model.RapidStepTest;
import com.getsimplex.steptimer.model.SessionPair;
import com.getsimplex.steptimer.model.TestSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.getsimplex.steptimer.service.LongitudinalFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class LongitudinalPairingTest {

    private final MdcPolicy policy = new MdcPolicy();

    @ParameterizedTest
    @CsvSource({
            "0.0, ",
            "0.99, ",
            "1.0, SHORT_TERM",
            "3.99, SHORT_TERM",
            "4.0, WEEKLY",
            "14.5, WEEKLY",
            "15.0, MONTHLY",
            "28, MONTHLY",
            "45.99, MONTHLY",
            "46.0, EXTENDED",
            "400, EXTENDED"
    })
    void intervalClassificationUsesCompletedDays(double elapsedDays, String expected){
        Optional<IntervalCategory> category = IntervalCategory.forElapsedDays(elapsedDays);
        if (expected == null){
            assertTrue(category.isEmpty());
        } else {
            assertEquals(IntervalCategory.valueOf(expected), category.get());
        }
    }

    @Test
    void negativeElapsedTimeIsRejected(){
        assertThrows(IllegalArgumentException.class, () -> IntervalCategory.forElapsedDays(-0.5));
    }

    @Test
    void intervalLabelsRoundTrip(){
        assertEquals(IntervalCategory.MONTHLY, IntervalCategory.fromLabel("monthly").get());
        assertEquals(IntervalCategory.WITHIN_SESSION, IntervalCategory.fromLabel("within-session").get());
        assertTrue(IntervalCategory.fromLabel("yearly").isEmpty());
    }

    @Test
    void trialsCloseTogetherFormOneSessionScoredAsMeanOfFirstTwo(){
        List<RapidStepTest> trials = new ArrayList<>(session(BASE, 40.0, 36.0, "NONE"));
        trials.add(trial(BASE + 10 * MINUTE, 20.0));//third trial is kept but not scored
        List<TestSession> sessions = LongitudinalSessionBuilder.build("p1", trials, policy);

        assertEquals(1, sessions.size());
        TestSession session = sessions.get(0);
        assertEquals(3, session.getCompletedTrials());
        assertEquals(List.of(40.0, 36.0, 20.0), session.getTrialTimesSeconds());
        assertEquals(38.0, session.getScoreSeconds(), 1e-9);
        assertEquals("2026-01-01T00:00:00Z", session.getStartTimeUtc());
        assertEquals(TestSession.UNVERSIONED_PROTOCOL, session.getProtocolVersion());
    }

    @Test
    void sessionsSplitOnGapAndAreNumberedChronologically(){
        List<RapidStepTest> trials = new ArrayList<>();
        trials.addAll(session(BASE + 7 * DAY, 35, 34, null));//stored out of order
        trials.addAll(session(BASE, 40, 38, null));
        trials.add(trial(BASE + 7 * DAY + 3 * MINUTE + 31 * MINUTE + 34_000, 33));//31 min after the last trial ended
        List<TestSession> sessions = LongitudinalSessionBuilder.build("p1", trials, policy);

        assertEquals(3, sessions.size());
        assertEquals(List.of(1, 2, 3), sessions.stream().map(TestSession::getSessionNumber).collect(Collectors.toList()));
        assertEquals(List.of(0, 2, 4), sessions.stream().map(TestSession::getPreviousTrialCount).collect(Collectors.toList()));
        assertEquals(39.0, sessions.get(0).getScoreSeconds(), 1e-9);
        assertFalse(sessions.get(2).isScored(), "a single trial is not comparable with mean-of-two scores");
    }

    @Test
    void incompleteAndInvalidTrialsAreExcluded(){
        RapidStepTest partial = trial(BASE, 30);
        partial.getStepPoints().remove(0);//29 steps
        RapidStepTest noStop = trial(BASE + MINUTE, 30);
        noStop.setStopTime(null);
        RapidStepTest negative = trial(BASE + 2 * MINUTE, 30);
        negative.setStopTime(negative.getStartTime() - 1000);
        RapidStepTest tooLong = trial(BASE + 3 * MINUTE, 601);
        List<RapidStepTest> trials = new ArrayList<>(List.of(partial, noStop, negative, tooLong));
        trials.add(null);

        assertTrue(LongitudinalSessionBuilder.build("p1", trials, policy).isEmpty());
    }

    @Test
    void mixedProtocolSessionIsNotScored(){
        List<RapidStepTest> trials = session(BASE, 40, 38, null);
        trials.get(1).setProtocolVersion("30-RST-v2");
        TestSession session = LongitudinalSessionBuilder.build("p1", trials, policy).get(0);
        assertEquals(TestSession.MIXED_PROTOCOL, session.getProtocolVersion());
        assertFalse(session.isScored());
    }

    @Test
    void withinAndBetweenSessionPairsWithExactElapsedTime(){
        List<RapidStepTest> trials = new ArrayList<>();
        trials.addAll(session(BASE, 40, 38, null));
        trials.addAll(session(BASE + 28 * DAY + 6 * 3_600_000L, 35, 33, "NONE"));
        LongitudinalPairGenerator.Result result = LongitudinalPairGenerator.generate(LongitudinalSessionBuilder.build("p1", trials, policy));

        List<SessionPair> within = result.getPairs().stream().filter(p -> p.getCategory() == IntervalCategory.WITHIN_SESSION).collect(Collectors.toList());
        assertEquals(2, within.size());
        assertEquals(-2.0, within.get(0).getDifferenceSeconds(), 1e-9);
        assertEquals(3.0 / (24 * 60), within.get(0).getElapsedDays(), 1e-12);

        SessionPair between = result.getPairs().stream().filter(p -> p.getCategory() == IntervalCategory.MONTHLY).findFirst().get();
        assertEquals(28.25, between.getElapsedDays(), 1e-12);
        assertEquals(39.0, between.getFirstScoreSeconds(), 1e-9);
        assertEquals(34.0, between.getSecondScoreSeconds(), 1e-9);
        assertEquals(-5.0, between.getDifferenceSeconds(), 1e-9);
        assertEquals(2, between.getLaterSessionNumber());
        assertEquals(ParticipantStability.STABLE, between.getStability());
    }

    @Test
    void onlyConsecutiveScoredSessionsArePaired(){
        List<RapidStepTest> trials = new ArrayList<>();
        for (int i = 0; i < 4; i++){
            trials.addAll(session(BASE + i * 7 * DAY, 40 - i, 39 - i, "NONE"));
        }
        List<SessionPair> between = LongitudinalPairGenerator.generate(LongitudinalSessionBuilder.build("p1", trials, policy)).getPairs()
                .stream().filter(p -> p.getCategory() != IntervalCategory.WITHIN_SESSION).collect(Collectors.toList());
        assertEquals(3, between.size(), "4 sessions give 3 adjacent pairs, not 6 overlapping ones");
        assertTrue(between.stream().allMatch(p -> p.getCategory() == IntervalCategory.WEEKLY));
    }

    @Test
    void healthReportsDetermineStabilityAndMissingReportsAreUnknown(){
        List<RapidStepTest> trials = new ArrayList<>();
        trials.addAll(session(BASE, 40, 38, null));
        trials.addAll(session(BASE + 7 * DAY, 39, 37, null));//no report
        trials.addAll(session(BASE + 14 * DAY, 39, 37, "INJURY"));
        trials.addAll(session(BASE + 21 * DAY, 39, 37, "none"));
        trials.addAll(session(BASE + 28 * DAY, 39, 37, "not-a-real-value"));
        List<ParticipantStability> stability = LongitudinalPairGenerator.generate(LongitudinalSessionBuilder.build("p1", trials, policy)).getPairs()
                .stream().filter(p -> p.getCategory() != IntervalCategory.WITHIN_SESSION).map(SessionPair::getStability).collect(Collectors.toList());
        assertEquals(List.of(ParticipantStability.UNKNOWN, ParticipantStability.CHANGE_REPORTED,
                ParticipantStability.STABLE, ParticipantStability.UNKNOWN), stability);
    }

    @Test
    void reportFromAnUnscoredSessionInTheIntervalCounts(){
        List<RapidStepTest> trials = new ArrayList<>();
        trials.addAll(session(BASE, 40, 38, null));
        trials.add(trial(BASE + 3 * DAY, 45, "ILLNESS"));//single-trial session between the two scored sessions
        trials.addAll(session(BASE + 7 * DAY, 39, 37, "NONE"));
        SessionPair between = LongitudinalPairGenerator.generate(LongitudinalSessionBuilder.build("p1", trials, policy)).getPairs()
                .stream().filter(p -> p.getCategory() == IntervalCategory.WEEKLY).findFirst().get();
        assertEquals(ParticipantStability.CHANGE_REPORTED, between.getStability());
        assertEquals(3, between.getLaterSessionNumber());
    }

    @Test
    void sameDaySessionsAndProtocolChangesAreNotPaired(){
        List<RapidStepTest> trials = new ArrayList<>();
        trials.addAll(session(BASE, 40, 38, null));
        trials.addAll(session(BASE + 5 * 3_600_000L, 39, 37, null));//5 hours later
        List<RapidStepTest> newProtocol = session(BASE + 10 * DAY, 39, 37, null);
        newProtocol.forEach(t -> t.setProtocolVersion("30-RST-v2"));
        trials.addAll(newProtocol);
        LongitudinalPairGenerator.Result result = LongitudinalPairGenerator.generate(LongitudinalSessionBuilder.build("p1", trials, policy));

        assertEquals(1, result.getPairsUnderOneDay());
        assertEquals(1, result.getPairsWithDifferentProtocols());
        assertTrue(result.getPairs().stream().allMatch(p -> p.getCategory() == IntervalCategory.WITHIN_SESSION));
    }
}
