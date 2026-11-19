package com.getsimplex.steptimer.model;

import java.util.List;

/**
 * One round of a Rapid Step Test (a full test is 2 rounds of 30 steps, with a 2-minute rest between).
 *
 * stepPoints use the same meaning as the older RapidStepTest: the milliseconds between steps,
 * where the first number is the time from the start of the round to the first step.
 * Example: startTime 1000, stepPoints [820, 790, 805, ...] means steps at 1820, 2610, 3415, ...
 */
public class TestRound {

    private int round;                 // 1 or 2
    private long startTime;            // when the round started (milliseconds since 1970)
    private long stopTime;             // worked out by the server: startTime + all stepPoints
    private List<Long> stepPoints;     // gaps between steps in ms (30 for a finished round)
    private long durationMs;           // worked out by the server
    private Long avgIntervalMs;        // worked out by the server (null if no steps)

    public int getRound() { return round; }
    public void setRound(int round) { this.round = round; }

    public long getStartTime() { return startTime; }
    public void setStartTime(long startTime) { this.startTime = startTime; }

    public long getStopTime() { return stopTime; }
    public void setStopTime(long stopTime) { this.stopTime = stopTime; }

    public List<Long> getStepPoints() { return stepPoints; }
    public void setStepPoints(List<Long> stepPoints) { this.stepPoints = stepPoints; }

    public long getDurationMs() { return durationMs; }
    public void setDurationMs(long durationMs) { this.durationMs = durationMs; }

    public Long getAvgIntervalMs() { return avgIntervalMs; }
    public void setAvgIntervalMs(Long avgIntervalMs) { this.avgIntervalMs = avgIntervalMs; }
}
