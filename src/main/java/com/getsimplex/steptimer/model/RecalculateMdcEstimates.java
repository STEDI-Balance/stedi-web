package com.getsimplex.steptimer.model;

// Asks MdcRecalculationActor to recalculate MDC95 estimates; without force it only runs when data changed
public class RecalculateMdcEstimates {

    private boolean force;

    public RecalculateMdcEstimates(boolean force){
        this.force = force;
    }

    public boolean isForce() {
        return force;
    }

    public void setForce(boolean force) {
        this.force = force;
    }
}
