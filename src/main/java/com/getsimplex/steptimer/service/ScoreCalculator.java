package com.getsimplex.steptimer.service;

import com.getsimplex.steptimer.model.BalanceTest;
import com.getsimplex.steptimer.model.TestRound;

/**
 * Works out a test's numbers. Two separate jobs:
 *
 *  1. measure()  the plain facts: durations, step counts, average step interval.
 *                These never change, so they're worked out once when the test is saved.
 *
 *  2. score()    the 0-100 balance score shown on the result card.
 *                The formula must come from the STEDI sponsor / clinical team, so for now it
 *                returns null and VERSION is "pending".
 *
 * When the formula arrives:
 *   - write it inside score()
 *   - change VERSION (for example to "1")
 * Every saved test remembers which VERSION scored it, and BalanceTestService re-scores
 * older ones the next time they're read. No migration script is needed.
 */
public class ScoreCalculator {

    public static final String VERSION = "pending";

    /** Fills in each round's stopTime, duration and average, plus the test's totals. */
    public static void measure(BalanceTest test) {
        int steps = 0;
        long totalTime = 0;
        for (TestRound round : test.getRounds()) {
            long roundTime = 0;
            for (Long gap : round.getStepPoints()) {
                roundTime += gap;
            }
            int roundSteps = round.getStepPoints().size();
            round.setStopTime(round.getStartTime() + roundTime);
            round.setDurationMs(roundTime);
            round.setAvgIntervalMs(roundSteps == 0 ? null : Math.round((double) roundTime / roundSteps));
            steps += roundSteps;
            totalTime += roundTime;
        }
        TestRound first = test.getRounds().get(0);
        TestRound last = test.getRounds().get(test.getRounds().size() - 1);
        test.setStartedAt(first.getStartTime());
        test.setCompletedAt(last.getStopTime());
        test.setStepCount(steps);
        test.setTotalTimeMs(totalTime);
        test.setAvgIntervalMs(steps == 0 ? null : Math.round((double) totalTime / steps));
    }

    /** Sets test.score and test.scoreVersion. Stopped tests are never scored (Figma: "No score was saved"). */
    public static void score(BalanceTest test) {
        test.setScoreVersion(VERSION);
        if (!BalanceTest.COMPLETE.equals(test.getStatus())) {
            test.setScore(null);
            return;
        }
        // TODO (sponsor): the clinical formula goes here. It can use test.getRounds() (every
        // step interval in both rounds), test.getTotalTimeMs(), test.getAvgIntervalMs(),
        // and the profile's age, gender, height and weight if needed.
        test.setScore(null);
    }
}
