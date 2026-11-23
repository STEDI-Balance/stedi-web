package com.getsimplex.steptimer.model;

import com.google.gson.annotations.SerializedName;

/**
 * Which pairs an estimate uses. Pairs with a reported health change are never used.
 */
public enum EstimateCohort {
    @SerializedName("stability-confirmed") STABILITY_CONFIRMED,//only pairs where the participant reported no change
    @SerializedName("observed") OBSERVED//also pairs with unknown stability: observed test-retest variability, at most exploratory
}
