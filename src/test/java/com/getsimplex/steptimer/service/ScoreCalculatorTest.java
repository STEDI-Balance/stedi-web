package com.getsimplex.steptimer.service;

import com.getsimplex.steptimer.model.BalanceTest;
import com.getsimplex.steptimer.model.TestRound;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Checks the numbers shown on the result card (Figma: "2 x 30 steps, Total time 48.2 s, Avg. interval 0.80 s").
 * Run with:  mvn test
 */
class ScoreCalculatorTest {

    /** Builds a test whose rounds each have `steps` gaps of `gapMs`, with a 2-minute rest between rounds. */
    static BalanceTest makeTest(String status, long start, int[] stepsPerRound, long gapMs) {
        BalanceTest test = new BalanceTest();
        test.setStatus(status);
        List<TestRound> rounds = new ArrayList<>();
        long roundStart = start;
        for (int i = 0; i < stepsPerRound.length; i++) {
            TestRound round = new TestRound();
            round.setRound(i + 1);
            round.setStartTime(roundStart);
            round.setStepPoints(new ArrayList<>(Collections.nCopies(stepsPerRound[i], gapMs)));
            rounds.add(round);
            roundStart += stepsPerRound[i] * gapMs + 120_000;
        }
        test.setRounds(rounds);
        return test;
    }

    @Test
    void measuresTwoRoundsOfThirtySteps() {
        BalanceTest test = makeTest(BalanceTest.COMPLETE, 1_000_000, new int[]{30, 30}, 800);

        ScoreCalculator.measure(test);

        assertEquals(60, test.getStepCount());
        assertEquals(48_000, test.getTotalTimeMs());          // 2 x 30 x 0.8 s (the rest isn't counted)
        assertEquals(800L, test.getAvgIntervalMs());
        assertEquals(24_000, test.getRounds().get(0).getDurationMs());
        assertEquals(1_000_000 + 24_000, test.getRounds().get(0).getStopTime());
        assertEquals(1_000_000, test.getStartedAt());
        assertEquals(test.getRounds().get(1).getStopTime(), test.getCompletedAt());
    }

    @Test
    void averageIsRoundedToTheNearestMillisecond() {
        BalanceTest test = makeTest(BalanceTest.STOPPED, 0, new int[]{3}, 1);
        test.getRounds().get(0).setStepPoints(new ArrayList<>(List.of(800L, 801L, 801L)));   // 2402 / 3 = 800.67

        ScoreCalculator.measure(test);

        assertEquals(801L, test.getAvgIntervalMs());
    }

    @Test
    void aStoppedRoundWithNoStepsHasNoAverage() {
        BalanceTest test = makeTest(BalanceTest.STOPPED, 0, new int[]{0}, 800);

        ScoreCalculator.measure(test);

        assertEquals(0, test.getStepCount());
        assertNull(test.getAvgIntervalMs());
    }

    @Test
    void stoppedTestsAreNeverScored() {
        BalanceTest test = makeTest(BalanceTest.STOPPED, 0, new int[]{18}, 800);
        ScoreCalculator.measure(test);

        ScoreCalculator.score(test);

        assertNull(test.getScore());                       // Figma 21B: "No score was saved"
        assertEquals(ScoreCalculator.VERSION, test.getScoreVersion());
    }

    @Test
    void completeTestsWaitForTheSponsorsFormula() {
        // When the sponsor adds the formula, change this test to check real scores.
        BalanceTest test = makeTest(BalanceTest.COMPLETE, 0, new int[]{30, 30}, 800);
        ScoreCalculator.measure(test);

        ScoreCalculator.score(test);

        assertEquals("pending", ScoreCalculator.VERSION);
        assertNull(test.getScore());
    }
}
