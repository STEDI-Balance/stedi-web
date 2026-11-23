package com.getsimplex.steptimer.model;

import java.util.List;

/**
 * One participant's stored tests and whether they agreed to share anonymous data,
 * as loaded for an aggregate recalculation.
 */
public class ParticipantTestHistory {

    private final String participantKey;//the stored key (phone); replaced by a pseudonym before analysis
    private final boolean consentedToShareAnonymousData;
    private final List<RapidStepTest> tests;

    public ParticipantTestHistory(String participantKey, boolean consentedToShareAnonymousData, List<RapidStepTest> tests){
        this.participantKey = participantKey;
        this.consentedToShareAnonymousData = consentedToShareAnonymousData;
        this.tests = tests;
    }

    public String getParticipantKey() {
        return participantKey;
    }

    public boolean isConsentedToShareAnonymousData() {
        return consentedToShareAnonymousData;
    }

    public List<RapidStepTest> getTests() {
        return tests;
    }
}
