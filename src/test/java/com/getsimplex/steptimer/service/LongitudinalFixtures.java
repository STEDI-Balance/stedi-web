package com.getsimplex.steptimer.service;

import com.getsimplex.steptimer.model.RapidStepTest;

import java.util.ArrayList;
import java.util.List;

// Builds stored rapid step tests the way the app sends them: 30 step points, start, stop and test time
class LongitudinalFixtures {

    static final long DAY = 86_400_000L;
    static final long MINUTE = 60_000L;
    static final long BASE = 1_767_225_600_000L;//2026-01-01T00:00:00Z

    static RapidStepTest trial(long startMillis, double seconds){
        long millis = Math.round(seconds * 1000);
        RapidStepTest test = new RapidStepTest();
        test.setCustomer("participant@test.com");
        test.setStartTime(startMillis);
        test.setStopTime(startMillis + millis);
        test.setTestTime(millis);
        test.setTotalSteps(30);
        ArrayList<Long> stepPoints = new ArrayList<>();
        for (int step = 1; step <= 30; step++){
            stepPoints.add(millis * step / 30);
        }
        test.setStepPoints(stepPoints);
        return test;
    }

    static RapidStepTest trial(long startMillis, double seconds, String healthChange){
        RapidStepTest test = trial(startMillis, seconds);
        test.setHealthChangeSinceLastTest(healthChange);
        return test;
    }

    // Two trials three minutes apart, scored as their mean
    static List<RapidStepTest> session(long startMillis, double firstSeconds, double secondSeconds, String healthChange){
        List<RapidStepTest> trials = new ArrayList<>();
        trials.add(trial(startMillis, firstSeconds, healthChange));
        trials.add(trial(startMillis + 3 * MINUTE, secondSeconds, healthChange));
        return trials;
    }
}
