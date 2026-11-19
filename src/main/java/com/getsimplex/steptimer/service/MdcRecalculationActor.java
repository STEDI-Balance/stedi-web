package com.getsimplex.steptimer.service;

import akka.actor.UntypedActor;
import com.getsimplex.steptimer.model.RecalculateMdcEstimates;

import java.util.logging.Logger;

/**
 * Recalculates MDC95 estimates in the background so API requests never trigger a full recalculation.
 */
public class MdcRecalculationActor extends UntypedActor {
    private static Logger logger = Logger.getLogger(MdcRecalculationActor.class.getName());

    public void onReceive(Object object){
        if (object instanceof RecalculateMdcEstimates){
            try {
                MdcEstimateService.recalculate(((RecalculateMdcEstimates) object).isForce());
            } catch (Exception e){
                logger.severe("MDC95 recalculation failed: " + e.getMessage());
            }
        }
    }
}
