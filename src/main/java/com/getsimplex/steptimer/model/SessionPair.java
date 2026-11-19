package com.getsimplex.steptimer.model;

/**
 * Two comparable scores from one participant: two trials in one session, or two scored sessions.
 * The difference is second minus first, so negative means the second score was faster.
 */
public class SessionPair {

    private final String participantId;
    private final String protocolVersion;
    private final IntervalCategory category;
    private final double elapsedDays;//exact elapsed time between the two scores
    private final double firstScoreSeconds;
    private final double secondScoreSeconds;
    private final int laterSessionNumber;
    private final long laterStartTimeMillis;
    private final ParticipantStability stability;

    public SessionPair(String participantId, String protocolVersion, IntervalCategory category, double elapsedDays,
                       double firstScoreSeconds, double secondScoreSeconds, int laterSessionNumber,
                       long laterStartTimeMillis, ParticipantStability stability){
        this.participantId = participantId;
        this.protocolVersion = protocolVersion;
        this.category = category;
        this.elapsedDays = elapsedDays;
        this.firstScoreSeconds = firstScoreSeconds;
        this.secondScoreSeconds = secondScoreSeconds;
        this.laterSessionNumber = laterSessionNumber;
        this.laterStartTimeMillis = laterStartTimeMillis;
        this.stability = stability;
    }

    public double getDifferenceSeconds(){
        return secondScoreSeconds - firstScoreSeconds;
    }

    public String getParticipantId() {
        return participantId;
    }

    public String getProtocolVersion() {
        return protocolVersion;
    }

    public IntervalCategory getCategory() {
        return category;
    }

    public double getElapsedDays() {
        return elapsedDays;
    }

    public double getFirstScoreSeconds() {
        return firstScoreSeconds;
    }

    public double getSecondScoreSeconds() {
        return secondScoreSeconds;
    }

    public int getLaterSessionNumber() {
        return laterSessionNumber;
    }

    public long getLaterStartTimeMillis() {
        return laterStartTimeMillis;
    }

    public ParticipantStability getStability() {
        return stability;
    }
}
