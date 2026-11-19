package com.getsimplex.steptimer.service;

import com.getsimplex.steptimer.model.HealthChange;
import com.getsimplex.steptimer.model.MdcPolicy;
import com.getsimplex.steptimer.model.ParticipantStability;
import com.getsimplex.steptimer.model.RapidStepTest;
import com.getsimplex.steptimer.model.TestSession;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Groups a participant's stored rapid step tests (one test = one 30-step trial) into sessions.
 *
 * A trial is complete under the same rule /riskscore uses (stop time, test time, and 30 step points).
 * Its time is stop minus start, also as /riskscore uses it. Complete trials that start within
 * MdcPolicy.sessionGapMinutes of the previous trial ending belong to the same session. A session is
 * scored as the mean of its first two valid trials, the existing STEDI scoring; a session with one
 * trial is kept but left unscored, so it is never compared with mean-of-two scores.
 */
public class LongitudinalSessionBuilder {

    public static List<TestSession> build(String participantId, List<RapidStepTest> tests, MdcPolicy policy){
        List<RapidStepTest> validTrials = tests.stream()
                .filter(LongitudinalSessionBuilder::isComplete)
                .filter(test -> isValidDuration(test, policy))
                .sorted(Comparator.comparing(RapidStepTest::getStartTime))
                .collect(Collectors.toList());

        long gapMillis = policy.getSessionGapMinutes() * 60_000L;
        List<List<RapidStepTest>> groups = new ArrayList<>();
        List<RapidStepTest> current = null;
        long previousStop = 0;
        for (RapidStepTest trial : validTrials){
            if (current == null || trial.getStartTime() - previousStop > gapMillis){
                current = new ArrayList<>();
                groups.add(current);
            }
            current.add(trial);
            previousStop = Math.max(previousStop, trial.getStopTime());
        }

        List<TestSession> sessions = new ArrayList<>();
        int previousTrialCount = 0;
        for (List<RapidStepTest> group : groups){
            TestSession session = toSession(participantId, group);
            session.setSessionNumber(sessions.size() + 1);
            session.setPreviousTrialCount(previousTrialCount);
            previousTrialCount += group.size();
            sessions.add(session);
        }
        return sessions;
    }

    // Same completeness filter as StepHistory.riskScore
    static boolean isComplete(RapidStepTest test){
        return test != null && test.getStartTime() != null && test.getStopTime() != null && test.getTestTime() != null
                && test.getStepPoints() != null && test.getStepPoints().size() == 30;
    }

    static boolean isValidDuration(RapidStepTest test, MdcPolicy policy){
        double seconds = trialSeconds(test);
        return seconds > 0 && seconds <= policy.getMaxTrialSeconds();
    }

    static double trialSeconds(RapidStepTest test){
        return (test.getStopTime() - test.getStartTime()) / 1000.0;
    }

    private static TestSession toSession(String participantId, List<RapidStepTest> trials){
        TestSession session = new TestSession();
        session.setParticipantId(participantId);
        session.setStartTimeMillis(trials.get(0).getStartTime());
        session.setSessionId("S" + trials.get(0).getStartTime());
        session.setCompletedTrials(trials.size());

        List<String> protocols = trials.stream().map(LongitudinalSessionBuilder::protocolOf).distinct().collect(Collectors.toList());
        session.setProtocolVersion(protocols.size() == 1 ? protocols.get(0) : TestSession.MIXED_PROTOCOL);
        session.setSoftwareVersion(trials.get(0).getSoftwareVersion());

        for (RapidStepTest trial : trials){
            session.getTrialTimesSeconds().add(trialSeconds(trial));
            session.getTrialStartTimesMillis().add(trial.getStartTime());
        }
        if (trials.size() >= 2 && !TestSession.MIXED_PROTOCOL.equals(session.getProtocolVersion())){
            session.setScoreSeconds((session.getTrialTimesSeconds().get(0) + session.getTrialTimesSeconds().get(1)) / 2);
        }
        session.setHealthReport(healthReport(trials));
        return session;
    }

    private static String protocolOf(RapidStepTest trial){
        String protocol = trial.getProtocolVersion();
        return protocol == null || protocol.trim().isEmpty() ? TestSession.UNVERSIONED_PROTOCOL : protocol.trim();
    }

    // Any reported change wins; otherwise one explicit NONE makes the session stable; otherwise unknown
    private static ParticipantStability healthReport(List<RapidStepTest> trials){
        boolean reportedNoChange = false;
        for (RapidStepTest trial : trials){
            HealthChange change = HealthChange.parse(trial.getHealthChangeSinceLastTest());
            if (change != null && change != HealthChange.NONE){
                return ParticipantStability.CHANGE_REPORTED;
            }
            reportedNoChange |= change == HealthChange.NONE;
        }
        return reportedNoChange ? ParticipantStability.STABLE : ParticipantStability.UNKNOWN;
    }
}
