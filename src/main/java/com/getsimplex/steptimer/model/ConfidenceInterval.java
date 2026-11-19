package com.getsimplex.steptimer.model;

public class ConfidenceInterval {

    private double lower;
    private double upper;
    private double level = 0.95;

    public ConfidenceInterval(double lower, double upper){
        this.lower = lower;
        this.upper = upper;
    }

    public ConfidenceInterval scale(double factor){
        return new ConfidenceInterval(lower * factor, upper * factor);
    }

    public boolean contains(double value){
        return value >= lower && value <= upper;
    }

    public double width(){
        return upper - lower;
    }

    public double getLower() {
        return lower;
    }

    public double getUpper() {
        return upper;
    }

    public double getLevel() {
        return level;
    }
}
