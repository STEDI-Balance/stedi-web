package com.getsimplex.steptimer.service;

import com.getsimplex.steptimer.datarepository.RapidStepTestRepository;
import com.getsimplex.steptimer.model.*;
import com.getsimplex.steptimer.utils.JedisClient;
import com.getsimplex.steptimer.utils.JedisData;
import com.getsimplex.steptimer.utils.Pseudonymizer;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Stores and serves MDC95 snapshots in Redis.
 *
 * API requests only read the latest stored snapshot. Recalculation runs in MdcRecalculationActor:
 * on a timer (MDC_RECALC_INTERVAL_MINUTES, default 60) it recalculates only if test data, consent,
 * or users changed since the latest snapshot (the MdcDataChangedAt marker), and admins can force it.
 * Snapshots are kept by id (their calculation time), so older interpretations stay reproducible.
 */
public class MdcEstimateService {

    private static Logger logger = Logger.getLogger(MdcEstimateService.class.getName());

    static final String DATA_CHANGED_KEY = "MdcDataChangedAt";
    static final String RECALCULATION_LOCK_KEY = "MdcRecalculationLock";

    private static RapidStepTestRepository rapidStepTestRepository = new RapidStepTestRepository();
    private static Pseudonymizer pseudonymizer = Pseudonymizer.fromEnvironment();
    private static ScheduledExecutorService scheduler;

    // Called after any change that could change the aggregates: a new test, a deletion, or a consent change
    public static void markDataChanged(){
        try {
            JedisClient.set(DATA_CHANGED_KEY, String.valueOf(System.currentTimeMillis()));
        } catch (Exception e){
            logger.warning("Unable to mark MDC95 data as changed: " + e.getMessage());//never block the change itself
        }
    }

    public static synchronized void startScheduledRecalculation(){
        if (scheduler != null){
            return;
        }
        String interval = System.getenv("MDC_RECALC_INTERVAL_MINUTES");
        long minutes = interval != null && !interval.isEmpty() ? Long.parseLong(interval) : 60;
        if (minutes <= 0){
            logger.info("Scheduled MDC95 recalculation is off (MDC_RECALC_INTERVAL_MINUTES=" + minutes + ")");
            return;
        }
        scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "mdc-recalculation-timer");
            thread.setDaemon(true);
            return thread;
        });
        scheduler.scheduleAtFixedRate(() -> MessageIntake.route(new RecalculateMdcEstimates(false)), 1, minutes, TimeUnit.MINUTES);
    }

    // Returns the new snapshot, or empty if nothing changed or another pod is already recalculating
    public static Optional<MdcSnapshot> recalculate(boolean force) throws Exception{
        long dataChangedAt = dataChangedAt();
        Optional<MdcSnapshot> latest = latestSnapshot();
        if (!force && latest.isPresent() && latest.get().getDataChangedAtMillis() >= dataChangedAt){
            return Optional.empty();
        }
        if (!JedisClient.setIfAbsent(RECALCULATION_LOCK_KEY, String.valueOf(System.currentTimeMillis()), 15 * 60)){
            logger.info("MDC95 recalculation already running elsewhere; skipping");
            return Optional.empty();
        }
        try {
            long started = System.currentTimeMillis();
            MdcSnapshot snapshot = MdcSnapshotCalculator.calculate(loadParticipants(), MdcPolicy.fromEnvironment(), pseudonymizer,
                    approvals(), started, dataChangedAt);
            JedisData.loadToJedis(snapshot, snapshot.getSnapshotId(), snapshot.getComputedAtMillis());
            logger.info("Stored MDC95 snapshot " + snapshot.getSnapshotId() + " for " + snapshot.getParticipantsIncluded()
                    + " consenting participants in " + (System.currentTimeMillis() - started) + " ms");
            return Optional.of(snapshot);
        } finally {
            JedisClient.delete(RECALCULATION_LOCK_KEY);
        }
    }

    public static Optional<MdcSnapshot> latestSnapshot() throws Exception{
        List<String> ids = JedisClient.zrange(MdcSnapshot.class.getSimpleName(), -1, -1);
        if (ids.isEmpty()){
            return Optional.empty();
        }
        return snapshot(ids.get(0));
    }

    public static Optional<MdcSnapshot> snapshot(String snapshotId) throws Exception{
        return JedisData.getEntityById(MdcSnapshot.class, snapshotId);
    }

    // A participant's own sessions, identified by their email rather than a pseudonym since only they see them
    public static List<TestSession> participantSessions(User user){
        List<RapidStepTest> tests;
        try {
            tests = rapidStepTestRepository.getArrayAtKey(user.getPhone());
        } catch (Exception e){
            tests = new ArrayList<>();//no tests stored for this phone yet
        }
        return LongitudinalSessionBuilder.build(user.getEmail(), tests, MdcPolicy.fromEnvironment());
    }

    // Only research-supported estimates in the latest snapshot can be approved
    public static MdcValidationApproval approve(String estimateId, User approver, String reference) throws Exception{
        MdcSnapshot latest = latestSnapshot().orElseThrow(() -> new IllegalStateException("No MDC95 snapshot has been calculated yet"));
        MdcEstimate estimate = latest.getEstimates().stream().filter(e -> e.getEstimateId().equals(estimateId)).findFirst()
                .orElseThrow(() -> new IllegalStateException("Estimate " + estimateId + " is not in the latest snapshot"));
        if (estimate.getStatus() != EstimateStatus.RESEARCH_SUPPORTED){
            throw new IllegalStateException("Only research-supported estimates can be validated; this estimate is " + estimate.getStatus().name().toLowerCase().replace('_', '-'));
        }
        MdcValidationApproval approval = new MdcValidationApproval();
        approval.setEstimateId(estimateId);
        approval.setApprovedBy(approver.getEmail());
        approval.setApprovedAtUtc(Instant.now().toString());
        approval.setReference(reference);
        JedisData.loadToJedis(approval, estimateId, System.currentTimeMillis());
        markDataChanged();
        MessageIntake.route(new RecalculateMdcEstimates(true));
        return approval;
    }

    public static void revokeApproval(String estimateId) throws Exception{
        JedisData.deleteJedis(MdcValidationApproval.class, estimateId);
        markDataChanged();
        MessageIntake.route(new RecalculateMdcEstimates(true));
    }

    private static Map<String, MdcValidationApproval> approvals() throws Exception{
        Map<String, MdcValidationApproval> approvals = new HashMap<>();
        for (Object approval : JedisData.getEntities(MdcValidationApproval.class)){
            approvals.put(((MdcValidationApproval) approval).getEstimateId(), (MdcValidationApproval) approval);
        }
        return approvals;
    }

    private static long dataChangedAt() throws Exception{
        String value = JedisClient.get(DATA_CHANGED_KEY);
        return value == null ? 0 : Long.parseLong(value);
    }

    // Tests are only read for users who agreed to share anonymous data; deleted users have no User record, so they drop out
    private static List<ParticipantTestHistory> loadParticipants() throws Exception{
        List<ParticipantTestHistory> participants = new ArrayList<>();
        for (Object entity : JedisData.getEntities(User.class)){
            User user = (User) entity;
            if (user.getPhone() == null || user.getPhone().isEmpty()){
                continue;
            }
            boolean consented = user.getAgreedToShareAnonymousData() > 0;
            List<RapidStepTest> tests = new ArrayList<>();
            if (consented){
                try {
                    tests = rapidStepTestRepository.getArrayAtKey(user.getPhone());
                } catch (Exception e){
                    //no tests stored for this phone
                }
            }
            participants.add(new ParticipantTestHistory(user.getPhone(), consented, tests));
        }
        return participants;
    }
}
