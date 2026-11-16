package com.getsimplex.steptimer.service;

import com.getsimplex.steptimer.model.BalanceTest;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.getsimplex.steptimer.service.ScoreCalculatorTest.makeTest;
import static org.junit.jupiter.api.Assertions.*;

/** The shape and timing rules POST /profiles/:id/tests enforces (BalanceTestService.checkRounds / checkTiming). */
class BalanceTestRulesTest {

    private static final long FIVE_MINUTES_AGO = System.currentTimeMillis() - 5 * 60_000;

    @Test
    void aCompleteTestOfTwoRoundsOfThirtyIsAccepted() {
        assertDoesNotThrow(() -> BalanceTestService.checkRounds(makeTest("complete", 0, new int[]{30, 30}, 800)));
    }

    @Test
    void statusIsCaseInsensitiveAndStoredLowerCase() {
        BalanceTest test = makeTest("COMPLETE", 0, new int[]{30, 30}, 800);
        BalanceTestService.checkRounds(test);
        assertEquals("complete", test.getStatus());
    }

    @Test
    void anUnknownStatusIsRefused() {
        assertThrows(IllegalArgumentException.class,
                () -> BalanceTestService.checkRounds(makeTest("finished", 0, new int[]{30, 30}, 800)));
    }

    @Test
    void aCompleteTestNeedsBothRounds() {
        assertThrows(IllegalArgumentException.class,
                () -> BalanceTestService.checkRounds(makeTest("complete", 0, new int[]{30}, 800)));
    }

    @Test
    void aCompleteTestNeedsThirtyStepsInEachRound() {
        assertThrows(IllegalArgumentException.class,
                () -> BalanceTestService.checkRounds(makeTest("complete", 0, new int[]{30, 25}, 800)));
    }

    @Test
    void aStoppedTestCanEndPartWayThroughItsLastRound() {
        assertDoesNotThrow(() -> BalanceTestService.checkRounds(makeTest("stopped", 0, new int[]{18}, 800)));
        assertDoesNotThrow(() -> BalanceTestService.checkRounds(makeTest("stopped", 0, new int[]{30, 7}, 800)));
    }

    @Test
    void aStoppedTestCantHaveAFullLastRound() {
        assertThrows(IllegalArgumentException.class,
                () -> BalanceTestService.checkRounds(makeTest("stopped", 0, new int[]{30}, 800)));
    }

    @Test
    void earlierRoundsOfAStoppedTestMustBeFull() {
        assertThrows(IllegalArgumentException.class,
                () -> BalanceTestService.checkRounds(makeTest("stopped", 0, new int[]{12, 5}, 800)));
    }

    @Test
    void roundsMustBeNumberedInOrder() {
        BalanceTest test = makeTest("complete", 0, new int[]{30, 30}, 800);
        test.getRounds().get(0).setRound(2);
        test.getRounds().get(1).setRound(1);
        assertThrows(IllegalArgumentException.class, () -> BalanceTestService.checkRounds(test));
    }

    @Test
    void stepGapsMustBePositiveAndUnderTwoMinutes() {
        BalanceTest zeroGap = makeTest("complete", 0, new int[]{30, 30}, 800);
        zeroGap.getRounds().get(0).getStepPoints().set(4, 0L);
        assertThrows(IllegalArgumentException.class, () -> BalanceTestService.checkRounds(zeroGap));

        BalanceTest hugeGap = makeTest("complete", 0, new int[]{30, 30}, 800);
        hugeGap.getRounds().get(1).getStepPoints().set(0, 3 * 60_000L);
        assertThrows(IllegalArgumentException.class, () -> BalanceTestService.checkRounds(hugeGap));
    }

    @Test
    void aRecentTestHasValidTiming() {
        BalanceTest test = makeTest("complete", FIVE_MINUTES_AGO, new int[]{30, 30}, 800);
        ScoreCalculator.measure(test);
        assertDoesNotThrow(() -> BalanceTestService.checkTiming(test));
    }

    @Test
    void testsOlderThanThirtyDaysAreRefused() {
        long fortyDaysAgo = System.currentTimeMillis() - 40L * 24 * 60 * 60 * 1000;
        BalanceTest test = makeTest("complete", fortyDaysAgo, new int[]{30, 30}, 800);
        ScoreCalculator.measure(test);
        assertThrows(IllegalArgumentException.class, () -> BalanceTestService.checkTiming(test));
    }

    @Test
    void testsFromTheFutureAreRefused() {
        long tomorrow = System.currentTimeMillis() + 24L * 60 * 60 * 1000;
        BalanceTest test = makeTest("complete", tomorrow, new int[]{30, 30}, 800);
        ScoreCalculator.measure(test);
        assertThrows(IllegalArgumentException.class, () -> BalanceTestService.checkTiming(test));
    }

    @Test
    void roundTwoMustStartAfterRoundOneEnds() {
        BalanceTest test = makeTest("complete", FIVE_MINUTES_AGO, new int[]{30, 30}, 800);
        test.getRounds().get(1).setStartTime(FIVE_MINUTES_AGO + 1_000);   // overlaps round 1
        ScoreCalculator.measure(test);
        assertThrows(IllegalArgumentException.class, () -> BalanceTestService.checkTiming(test));
    }
}
