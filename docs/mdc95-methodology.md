# Longitudinal MDC95 for the 30-step rapid step test

STEDI estimates how much a participant's 30-step rapid step test (30-RST) score varies between tests when nothing real has changed. That variability tells participants and clinicians whether an observed change is larger than ordinary measurement noise.

The estimates come from anonymized, aggregated test–retest data from participants who agreed to share anonymous data. Each estimate is labelled with a status that says how much it can be relied on. None of the estimates is a validated clinical threshold unless the research team explicitly approves it.

Reference: Goldberg A, Talley SA. (2015). Performance on a test of rapid stepping in community-dwelling older adults: validity, relative and absolute reliability and minimum detectable change. *Physiotherapy Theory and Practice*, 31(7), 483–488. DOI: 10.3109/09593985.2015.1024805.

Goldberg and Talley reported ICC 0.85, SEM 3.4 s, MDC95 9.4 s, and an average improvement of 4.3 s between two trials at least two minutes apart. Those values apply to repeated trials within one session. They were not established for tests days, weeks, or months apart, which is why STEDI estimates variability separately for each interval.

## From stored tests to scores

STEDI stores each rapid step test (`POST /rapidsteptest`) as one **trial** of 30 steps, unchanged. Sessions and scores are derived from the raw trials whenever estimates are recalculated, so other scoring methods can be evaluated later from the same data.

| Step | Rule |
| --- | --- |
| Complete trial | Has a start time, a stop time, a test time and 30 step points. This is the same filter `/riskscore` uses. |
| Trial time | Stop time minus start time, in seconds, as `/riskscore` uses it. A trial longer than `MDC_MAX_TRIAL_SECONDS` (600) or with a non-positive time is treated as an invalid recording. |
| Session | Complete trials that start within `MDC_SESSION_GAP_MINUTES` (30) of the previous trial ending. Sessions are numbered from 1 in time order. |
| Session score | The mean of the session's first two trials. This preserves the existing STEDI scoring (`/riskscore` averages two trials). A session with one trial is kept but not scored, so it is never compared with a mean-of-two score. |
| Protocol | The optional `protocolVersion` sent with each test. Tests without one are grouped as `unversioned`. A session that mixes protocols is not scored, and sessions with different protocols are never compared. |

Each test can also carry `softwareVersion` and `healthChangeSinceLastTest` (`NONE`, `ILLNESS`, `INJURY`, `REHABILITATION`, `OTHER`). The app does not send these yet.

## Test pairs and interval categories

For each participant, STEDI forms two kinds of pairs:

- **Within-session**: trial 1 and trial 2 of a scored session, the design Goldberg and Talley used.
- **Between-session**: each scored session paired with the participant's next scored session on the same protocol.

Only consecutive scored sessions are paired. Four sessions give three pairs, not six overlapping ones. The exact elapsed time between the two scores is kept with every pair. Between-session pairs are then put in a category by the number of **completed days** between them:

| Category | Interval |
| --- | --- |
| within-session | Repeated trials in one session |
| short-term | 1–3 completed days |
| weekly | 4–14 completed days |
| monthly | 15–45 completed days |
| extended | More than 45 completed days |

These are provisional analytical categories, not validated clinical boundaries. Sessions less than one full day apart fall into none of them: they are counted (`pairsUnderOneDay`) but not used yet.

## Participant stability

Participants may really improve or get worse between sessions, so a difference between two scores is not automatically measurement error. Each pair is classified by what the participant reported in the sessions after the first score, up to and including the second:

| Stability | Rule |
| --- | --- |
| stable | Every session in the interval reported `NONE`. Within-session pairs are treated as stable because the trials are minutes apart, as in the original study. |
| change-reported | Any session in the interval reported illness, injury, rehabilitation or another change. These pairs are never used in an estimate. |
| unknown | At least one session in the interval had no report. A missing report is never treated as stable. |

For each protocol and between-session category, STEDI calculates two estimates:

- **stability-confirmed**: only stable pairs. This is the MDC95 estimate, and it can become research-supported.
- **observed**: stable and unknown pairs. This is labelled *observed test–retest variability*, mixes true change with measurement error, and is never more than exploratory.

Until the app sends health reports, only within-session estimates and observed between-session estimates will have data.

## Statistical method (model version 1.0.0)

**Independence.** A participant can contribute many pairs, and pairs that share a session are not independent. Each estimate therefore uses **one pair per participant**: the most recent eligible pair by default (`MDC_PAIR_SELECTION=LATEST`, after the most familiarization), or the earliest (`EARLIEST`). The estimate reports both `eligiblePairs` (all pairs) and `pairsUsed` (one per participant).

**Statistics.** For the n chosen pairs, the difference is second score minus first score. A negative difference means the second score was faster.

| Quantity | Calculation |
| --- | --- |
| Mean paired difference (bias) | mean of the differences, with a t-based 95% CI on n − 1 degrees of freedom |
| SD of differences | sample SD (n − 1), with the exact chi-square 95% CI: SD × √((n − 1) / χ²) |
| SEM | SD of differences / √2. This equals √MSE from the two-way ANOVA for two occasions. |
| MDC95 | 1.96 × √2 × SEM, which equals 1.96 × SD of differences. Its CI scales the SD CI by 1.96. |
| 95% limits of agreement | mean ± 1.96 × SD (Bland and Altman), each with a 95% CI of ± t × √(3 SD² / n) |
| ICC(A,1) | two-way random effects, absolute agreement, single measurement (McGraw and Wong 1996). Reported as context; null when there is no between-participant variance. |

The t and chi-square quantiles are computed in `StatisticalDistributions`. The unit tests check every statistic against values calculated independently with SciPy.

These formulas assume:

- the paired differences are roughly normally distributed;
- measurement error has a similar variance on both occasions;
- error does not depend on how fast the participant is;
- the chosen pairs are independent across participants.

When these assumptions are doubtful, for example with skewed differences or error that grows with completion time, treat the confidence intervals as approximate.

## Learning effects versus random error

The SD of differences is taken around the mean difference. A consistent improvement shared by most participants, such as getting used to the test, therefore shows up as a **systematic difference** (the mean) and does not inflate SEM or MDC95. An estimate reports `systematicDifferenceDetected` when the 95% CI of the mean difference excludes zero.

Each snapshot also reports learning effects separately (`learningEffects`), as the mean difference with a 95% CI and one pair per participant:

- **trial order**: trial 2 compared with trial 1 in the same session;
- **session number** (previous exposure): sessions 2, 3, 4 and 5+, each compared with the participant's previous scored session.

An improvement in either is not evidence of better balance or mobility. The model version is stored with every estimate, so the learning-effect model can be replaced (for example by a mixed model with elapsed time as a continuous predictor) without losing older results.

## Statuses

| Status | When |
| --- | --- |
| insufficient-data | Fewer than 2 participants, or fewer than `MDC_MIN_PARTICIPANTS` (30) unique participants, or fewer than `MDC_MIN_PAIRS` (30) eligible pairs. |
| exploratory | Enough data, but the estimate is observed variability (includes unknown stability), shows zero variability, or has an MDC95 CI wider than `MDC_MAX_CI_WIDTH_SECONDS` (if set). |
| research-supported | A stability-confirmed estimate that meets every configured requirement. |
| validated | Never assigned automatically. A research admin approves one research-supported estimate with a reference (`POST /mdc/estimates/{estimateId}/validation`). |

The estimate id is a fingerprint of the estimate's data and method. When new data or a new method changes the estimate, the approval stops applying and the estimate goes back to its calculated status until it is approved again.

Thirty participants is a preliminary engineering default, not a statistical validation threshold. The research team should set `MDC_MIN_PARTICIPANTS`, `MDC_MIN_PAIRS` and `MDC_MAX_CI_WIDTH_SECONDS` from a sample-size and precision analysis. For example, the MDC95 95% CI runs from about 20% below to 34% above the estimate at n = 30, and from 13% below to 18% above at n = 85.

## Interpreting one participant's change

`GET /mdc/interpretation/{email}` compares the participant's two most recent scored sessions, or two chosen with `previousSessionId` and `currentSessionId`. They must use the same protocol and be in time order. The response gives the previous and current scores, the change, the percentage change, the elapsed days, the interval category and the matching estimate. The estimate is chosen as follows:

1. the stability-confirmed estimate for the same protocol and category, if it is at least exploratory;
2. otherwise the observed estimate, if it is exploratory;
3. otherwise no threshold is used. The response gives the observed change and says it cannot be distinguished from measurement variability.

Estimates are never borrowed from another category or interpolated between categories.

**Systematic differences.** If the estimate shows a systematic difference, the change is compared with the asymmetric limits of agreement (`thresholdBasis: limits-of-agreement`), not the symmetric MDC95. For example, if participants usually get 4 s faster over a week, a 6 s improvement is ordinary, but slowing by 2 s is not.

**Labels on every interpretation:**

- `thresholdSupportsIndividualInterpretation` is true only for research-supported or validated estimates.
- An exploratory comparison says so in its message.
- `clinicalSignificanceEstablished` is always false.
- A change larger than measurement variability shows that something other than measurement noise is likely. It does not show a clinical improvement or deterioration in balance: practice, health, effort and testing conditions can all change scores.

## Recalculation and versioning

API requests only read stored snapshots. They never recalculate.

- Saving a test, a user deletion, and any change to consent update the `MdcDataChangedAt` marker in Redis.
- `MdcRecalculationActor`, on the existing Akka `MessageIntake`, recalculates every `MDC_RECALC_INTERVAL_MINUTES` (60; 0 turns it off), but only if the marker is newer than the latest snapshot. A Redis lock stops several pods from recalculating at once.
- A research admin can force a recalculation with `POST /mdc/recalculate`.
- Each recalculation is a new snapshot, stored by its calculation time. Older snapshots are kept (`GET /mdc/estimates?snapshotId=`), and every interpretation names the snapshot and estimate it used.

A full recalculation, rather than incrementally updated totals, is deliberate. Corrections, deletions and consent withdrawals have to remove data, and the one-pair-per-participant choice can change when a participant tests again. The calculation reads only the stored tests of consenting participants.

## Privacy and access

- Only users with `agreedToShareAnonymousData` set are included. `DELETE /user/{username}/consent/anonymousdata` withdraws consent, and deleted users have no user record. Either way, the participant's tests are left out of the next snapshot.
- Phone numbers are replaced by HMAC-SHA256 pseudonyms (`MDC_PSEUDONYM_SECRET`) during calculation. Snapshots contain only aggregates: no identifiers, pseudonyms or individual scores.
- Every `/mdc` endpoint needs a session token. Participants can only see their own sessions and interpretation.
- Non-admin callers see cohorts smaller than `MDC_MIN_PUBLIC_COHORT` (10) only as their status. Snapshot-level counts are hidden when fewer participants than that are included.
- Research admins are listed in `STEDI_RESEARCH_ADMINS` (comma-separated emails). The default is the existing STEDI admin. They can recalculate, approve or revoke validation, and see unsuppressed estimates.

## Decisions for the research team

These defaults are placeholders until the research team decides:

1. `MDC_MIN_PARTICIPANTS`, `MDC_MIN_PAIRS` and `MDC_MAX_CI_WIDTH_SECONDS`, from a precision analysis.
2. Whether each participant should contribute their latest or earliest pair (`MDC_PAIR_SELECTION`), or whether to move to a repeated-measures model that uses every pair.
3. The interval boundaries, and whether sessions less than a day apart need their own category.
4. Whether the session gap (30 minutes) and the scoring (mean of the first two trials) match the intended protocol. Goldberg and Talley required at least two minutes between trials.
5. The wording and timing of the health-change question in the app, and whether self-reported "no change" is enough evidence of stability.
6. Which protocol versions the app should send, so protocol changes are never mixed.
