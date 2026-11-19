package com.getsimplex.steptimer.model;

/**
 * What a participant reported, with a test, about their health since their previous test.
 * A missing report is UNKNOWN stability, never evidence of stability.
 */
public enum HealthChange {
    NONE,//no meaningful health or activity change
    ILLNESS,
    INJURY,
    REHABILITATION,//physical rehabilitation, therapy, or another intervention
    OTHER;

    // Returns null for a missing or unrecognized value so it is treated as unknown
    public static HealthChange parse(String value){
        if (value == null || value.trim().isEmpty()){
            return null;
        }
        try {
            return HealthChange.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e){
            return null;
        }
    }
}
