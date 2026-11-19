package com.getsimplex.steptimer.service;

import com.getsimplex.steptimer.model.*;
import com.getsimplex.steptimer.utils.GsonFactory;
import com.getsimplex.steptimer.utils.Pseudonymizer;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.*;

import static com.getsimplex.steptimer.service.LongitudinalFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class ResearchAccessPolicyTest {

    private final ResearchAccessPolicy access = new ResearchAccessPolicy(Set.of("Research.Admin@stedi.me"));

    private static User user(String email){
        User user = new User();
        user.setEmail(email);
        user.setUserName(email);
        return user;
    }

    // 12 participants with within-session pairs, 3 with monthly pairs
    private static MdcSnapshot snapshot(){
        List<ParticipantTestHistory> participants = new ArrayList<>();
        for (int i = 0; i < 12; i++){
            List<RapidStepTest> trials = new ArrayList<>(session(BASE, 30 + i, 29 + i * 0.9, "NONE"));
            if (i < 3){
                trials.addAll(session(BASE + 20 * DAY, 31 + i, 30 + i, "NONE"));
            }
            participants.add(new ParticipantTestHistory("+1801555" + String.format("%04d", i), true, trials));
        }
        MdcPolicy policy = new MdcPolicy();
        policy.setMinParticipants(10);
        policy.setMinPairs(10);
        return MdcSnapshotCalculator.calculate(participants, policy, new Pseudonymizer("s".getBytes(StandardCharsets.UTF_8)),
                Collections.emptyMap(), BASE + 30 * DAY, BASE + 30 * DAY);
    }

    @Test
    void researchAdminsComeFromConfiguration(){
        assertTrue(access.isResearchAdmin(user("research.admin@stedi.me")));
        assertFalse(access.isResearchAdmin(user("participant@test.com")));
        assertFalse(access.isResearchAdmin(null));
    }

    @Test
    void participantsCanOnlyInterpretTheirOwnResults(){
        assertTrue(access.canViewParticipant(user("a@test.com"), "a@test.com"));
        assertFalse(access.canViewParticipant(user("a@test.com"), "b@test.com"));
        assertFalse(access.canViewParticipant(user("research.admin@stedi.me"), "b@test.com"));
        assertFalse(access.canViewParticipant(null, "a@test.com"));
    }

    @Test
    void smallCohortsAreSuppressedForNonAdmins(){
        MdcSnapshot full = snapshot();
        MdcSnapshot shown = access.forCaller(full, user("participant@test.com"));

        MdcEstimate within = shown.getEstimates().stream().filter(e -> e.getIntervalCategory() == IntervalCategory.WITHIN_SESSION).findFirst().get();
        assertFalse(within.isSuppressed());
        assertEquals(12, within.getUniqueParticipants());
        assertNotNull(within.getStatistics());

        MdcEstimate monthly = shown.getEstimates().stream()
                .filter(e -> e.getIntervalCategory() == IntervalCategory.MONTHLY && e.getCohort() == EstimateCohort.STABILITY_CONFIRMED).findFirst().get();
        assertTrue(monthly.isSuppressed());
        assertNull(monthly.getUniqueParticipants());
        assertNull(monthly.getEligiblePairs());
        assertNull(monthly.getStatistics());
        assertEquals(EstimateStatus.INSUFFICIENT_DATA, monthly.getStatus());

        assertTrue(shown.getLearningEffects().stream().filter(e -> e.getFactor().equals("session-number")).allMatch(LearningEffect::isSuppressed));

        // the stored snapshot is not modified, and admins see everything
        assertEquals(3, full.getEstimates().stream().filter(e -> e.getIntervalCategory() == IntervalCategory.MONTHLY).findFirst().get().getUniqueParticipants());
        assertSame(full, access.forCaller(full, user("research.admin@stedi.me")));
    }

    @Test
    void smallOverallCohortHidesSnapshotCounts(){
        MdcSnapshot tiny = MdcSnapshotCalculator.calculate(
                List.of(new ParticipantTestHistory("+1", true, session(BASE, 30, 29, null)), new ParticipantTestHistory("+2", false, session(BASE, 30, 29, null))),
                new MdcPolicy(), new Pseudonymizer("s".getBytes(StandardCharsets.UTF_8)), Collections.emptyMap(), BASE, BASE);
        MdcSnapshot shown = access.forCaller(tiny, user("participant@test.com"));
        assertNull(shown.getParticipantsIncluded());
        assertNull(shown.getParticipantsExcludedWithoutConsent());
    }

    @Test
    void responsesNeverContainParticipantKeysOrPseudonyms(){
        MdcSnapshot full = snapshot();
        String json = GsonFactory.getGson().toJson(full);
        assertFalse(json.contains("+1801555"));
        assertFalse(json.contains("8015550"));
        Pseudonymizer pseudonymizer = new Pseudonymizer("s".getBytes(StandardCharsets.UTF_8));
        assertFalse(json.contains(pseudonymizer.pseudonym("+18015550000")));
        assertTrue(json.contains("\"intervalCategory\":\"within-session\""));
        assertTrue(json.contains("\"status\":\"research-supported\""));
    }
}
