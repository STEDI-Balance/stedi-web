package com.getsimplex.steptimer.service;

import com.getsimplex.steptimer.model.DeviceMessage;
import com.getsimplex.steptimer.model.Token;
import com.getsimplex.steptimer.utils.JedisData;
import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;

import java.io.*;
import java.util.Date;
import java.util.UUID;

/**
 * //Copyright 2021 Sean Murdock, Created by sean on 9/15/2016.
 */
public class WebServiceHandler {
    private static final Logger logger = LoggerFactory.getLogger(WebServiceHandler.class);
    private static Gson gson = new Gson();

    public static String routePdfRequest(Request request, Response response) throws Exception{
        File pdfFile = new File("C:\\temp\\temp.pdf");
        FileInputStream inputStream = new FileInputStream(pdfFile);
        Reader inputStreamReader = new InputStreamReader(inputStream);
        response.header("Content-Type","application/pdf");
        int data = inputStreamReader.read();
        while (data!=-1) {
            byte thisbyte = (byte) data;
            response.raw().getOutputStream().write(thisbyte);
            data = inputStreamReader.read();
        }

        return "Done";
    }

    public static String routeDeviceRequest(Request request) throws Exception{
        logger.info("Processing device request from IP: {}", request.ip());
        
        DeviceMessage deviceMessage = gson.fromJson(request.body(), DeviceMessage.class);
        logger.info("Parsed device message for device: {}", deviceMessage.getDeviceId());
        
        // Use a unique key per message so sorted-set by timestamp retains all entries
    long ts = deviceMessage.getTimestamp() > 0 ? deviceMessage.getTimestamp() : System.currentTimeMillis();
    deviceMessage.setTimestamp(ts);
    String uniqueId = String.format("%s:%d:%s", deviceMessage.getDeviceId(), ts, UUID.randomUUID());
    
    logger.info("Generated unique ID: {} for device: {} with timestamp: {}", uniqueId, deviceMessage.getDeviceId(), ts);
    
    // Persist message with global and per-device timestamp-sorted indexes
    JedisData.loadToJedisWithIndexUsingScore(deviceMessage, uniqueId, ts, "DeviceId", deviceMessage.getDeviceId());
    logger.info("Persisted device message to Redis with key: {}", uniqueId);
    
        try {
            MessageIntake.route(deviceMessage);
            logger.info("Successfully routed device message for device: {}", deviceMessage.getDeviceId());
        } catch (Exception e) {
            logger.error("Failed to route device message for device: {}", deviceMessage.getDeviceId(), e);
            throw e;
        }

        return "Accepted";
    }

}
