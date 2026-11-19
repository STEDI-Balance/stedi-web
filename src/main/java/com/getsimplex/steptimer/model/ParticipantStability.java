package com.getsimplex.steptimer.model;

import com.google.gson.annotations.SerializedName;

/**
 * Evidence about whether a participant's true performance could have changed between two scores.
 */
public enum ParticipantStability {
    @SerializedName("stable") STABLE,//every report in the interval was HealthChange.NONE, or trials in one session
    @SerializedName("change-reported") CHANGE_REPORTED,//illness, injury, rehabilitation, or another change was reported
    @SerializedName("unknown") UNKNOWN//at least one session in the interval had no report

}
