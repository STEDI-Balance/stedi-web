package com.getsimplex.steptimer.model;

import com.google.gson.annotations.SerializedName;

/**
 * How much an MDC95 estimate can be relied on, from least to most supported.
 * VALIDATED is never assigned automatically: it needs an explicit research or admin approval.
 */
public enum EstimateStatus {
    @SerializedName("insufficient-data") INSUFFICIENT_DATA,
    @SerializedName("exploratory") EXPLORATORY,
    @SerializedName("research-supported") RESEARCH_SUPPORTED,
    @SerializedName("validated") VALIDATED;

    public boolean atLeast(EstimateStatus other){
        return compareTo(other) >= 0;
    }
}
