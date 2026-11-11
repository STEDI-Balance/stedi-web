package com.getsimplex.steptimer.model;

/**
 * The JSON body of POST /profiles (add a family member) and PATCH /profiles/:id (edit).
 * Every field may be left out. On PATCH, a missing field means "don't change it".
 * Example: {"fullName":"Jordan Morgan","birthDay":"1960-09-08","gender":"man","relationship":"parent"}
 */
public class ProfileRequest {

    private String fullName;
    private String birthDay;
    private String gender;
    private String relationship;
    private Double heightCm;
    private Double weightKg;
    private Boolean clearHealthMetrics;   // true = remove height and weight ("Not shared")

    public String getFullName() { return fullName; }
    public String getBirthDay() { return birthDay; }
    public String getGender() { return gender; }
    public String getRelationship() { return relationship; }
    public Double getHeightCm() { return heightCm; }
    public Double getWeightKg() { return weightKg; }
    public boolean wantsHealthMetricsCleared() { return Boolean.TRUE.equals(clearHealthMetrics); }
}
