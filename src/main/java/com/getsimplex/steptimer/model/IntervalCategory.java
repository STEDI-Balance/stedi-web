package com.getsimplex.steptimer.model;

import com.google.gson.annotations.SerializedName;

import java.util.Optional;

/**
 * Provisional analytical categories for the time between two scores. They are not validated
 * clinical boundaries. Between-session pairs are classified by completed days elapsed
 * (floor of the exact elapsed time), and the exact elapsed time is kept with every pair.
 */
public enum IntervalCategory {
    @SerializedName("within-session") WITHIN_SESSION("within-session", null, null),//repeated trials in one session
    @SerializedName("short-term") SHORT_TERM("short-term", 1, 3),
    @SerializedName("weekly") WEEKLY("weekly", 4, 14),
    @SerializedName("monthly") MONTHLY("monthly", 15, 45),
    @SerializedName("extended") EXTENDED("extended", 46, null);//more than 45 days

    private final String label;
    private final Integer minCompletedDays;
    private final Integer maxCompletedDays;

    IntervalCategory(String label, Integer minCompletedDays, Integer maxCompletedDays){
        this.label = label;
        this.minCompletedDays = minCompletedDays;
        this.maxCompletedDays = maxCompletedDays;
    }

    // Between-session category for an elapsed time; empty for less than one full day,
    // which no provisional category covers yet
    public static Optional<IntervalCategory> forElapsedDays(double elapsedDays){
        if (!Double.isFinite(elapsedDays) || elapsedDays < 0){
            throw new IllegalArgumentException("Elapsed days must be zero or more: " + elapsedDays);
        }
        long completedDays = (long) Math.floor(elapsedDays);
        for (IntervalCategory category : values()){
            if (category.minCompletedDays != null && completedDays >= category.minCompletedDays
                    && (category.maxCompletedDays == null || completedDays <= category.maxCompletedDays)){
                return Optional.of(category);
            }
        }
        return Optional.empty();
    }

    public static Optional<IntervalCategory> fromLabel(String label){
        for (IntervalCategory category : values()){
            if (category.label.equalsIgnoreCase(label) || category.name().equalsIgnoreCase(label)){
                return Optional.of(category);
            }
        }
        return Optional.empty();
    }

    public String getLabel() {
        return label;
    }
}
