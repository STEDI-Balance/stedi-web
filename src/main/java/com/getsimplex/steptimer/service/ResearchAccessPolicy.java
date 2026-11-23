package com.getsimplex.steptimer.service;

import com.getsimplex.steptimer.model.LearningEffect;
import com.getsimplex.steptimer.model.MdcEstimate;
import com.getsimplex.steptimer.model.MdcSnapshot;
import com.getsimplex.steptimer.model.User;
import com.getsimplex.steptimer.utils.GsonFactory;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Who may see what in the MDC95 API.
 * - Research admins (STEDI_RESEARCH_ADMINS, comma-separated emails; defaults to the existing STEDI admin)
 *   may recalculate, approve validation, and see unsuppressed aggregates.
 * - Everyone else sees aggregates with small cohorts suppressed, and only their own longitudinal change.
 */
public class ResearchAccessPolicy {

    private static final String DEFAULT_ADMIN = "scmurdock@gmail.com";//the admin DELETE /user already recognizes

    private final Set<String> researchAdmins;

    public ResearchAccessPolicy(Set<String> researchAdmins){
        this.researchAdmins = researchAdmins.stream().map(String::toLowerCase).collect(Collectors.toSet());
    }

    public static ResearchAccessPolicy fromEnvironment(){
        String admins = System.getenv("STEDI_RESEARCH_ADMINS");
        if (admins == null || admins.trim().isEmpty()){
            admins = DEFAULT_ADMIN;
        }
        return new ResearchAccessPolicy(Arrays.stream(admins.split(",")).map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toSet()));
    }

    public boolean isResearchAdmin(User user){
        return user != null && user.getEmail() != null && researchAdmins.contains(user.getEmail().toLowerCase());
    }

    // Participants may only interpret their own results
    public boolean canViewParticipant(User user, String customerEmail){
        return user != null && user.getEmail() != null && user.getEmail().equals(customerEmail);
    }

    public MdcSnapshot forCaller(MdcSnapshot snapshot, User user){
        if (snapshot == null || isResearchAdmin(user)){
            return snapshot;
        }
        return suppressSmallCohorts(snapshot);
    }

    // Copy of the snapshot with every cohort smaller than the policy minimum reduced to its status
    static MdcSnapshot suppressSmallCohorts(MdcSnapshot snapshot){
        MdcSnapshot copy = GsonFactory.getGson().fromJson(GsonFactory.getGson().toJson(snapshot), MdcSnapshot.class);
        int minimum = copy.getPolicy().getMinPublicCohortSize();
        for (MdcEstimate estimate : copy.getEstimates()){
            if (estimate.getUniqueParticipants() == null || estimate.getUniqueParticipants() < minimum){
                estimate.setSuppressed(true);
                estimate.setUniqueParticipants(null);
                estimate.setEligiblePairs(null);
                estimate.setPairsUsed(null);
                estimate.setMeanElapsedDays(null);
                estimate.setMinElapsedDays(null);
                estimate.setMaxElapsedDays(null);
                estimate.setStatistics(null);
                estimate.getStatusReasons().clear();
                estimate.getStatusReasons().add("Fewer than " + minimum + " participants: details are suppressed to protect privacy");
            }
        }
        for (LearningEffect effect : copy.getLearningEffects()){
            if (effect.getUniqueParticipants() == null || effect.getUniqueParticipants() < minimum){
                effect.setSuppressed(true);
                effect.setUniqueParticipants(null);
                effect.setMeanDifferenceSeconds(null);
                effect.setMeanDifferenceCi(null);
                effect.setSystematicDifferenceDetected(null);
                effect.setMeanElapsedDays(null);
            }
        }
        if (copy.getParticipantsIncluded() < minimum){
            copy.setParticipantsIncluded(null);
            copy.setParticipantsExcludedWithoutConsent(null);
            copy.setSessions(null);
            copy.setPairsWithReportedChangeExcluded(null);
            copy.setPairsUnderOneDay(null);
            copy.setPairsWithDifferentProtocols(null);
        }
        return copy;
    }
}
