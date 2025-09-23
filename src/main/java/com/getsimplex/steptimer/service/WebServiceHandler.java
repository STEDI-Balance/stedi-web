package com.getsimplex.steptimer.service;

import com.getsimplex.steptimer.model.DeviceMessage;
import com.getsimplex.steptimer.model.Token;
import com.getsimplex.steptimer.utils.JedisData;
import com.google.gson.Gson;
import spark.Request;
import spark.Response;

import java.io.*;
import java.util.Date;
import java.util.UUID;

/**
 * //Copyright 2021 Sean Murdock, Created by sean on 9/15/2016.
 */
public class WebServiceHandler {
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
        DeviceMessage deviceMessage = gson.fromJson(request.body(), DeviceMessage.class);
        // Use a unique key per message so sorted-set by timestamp retains all entries
    long ts = deviceMessage.getTimestamp() > 0 ? deviceMessage.getTimestamp() : System.currentTimeMillis();
    deviceMessage.setTimestamp(ts);
    String uniqueId = String.format("%s:%d:%s", deviceMessage.getDeviceId(), ts, UUID.randomUUID());
    // Persist message with global and per-device timestamp-sorted indexes
    JedisData.loadToJedisWithIndexUsingScore(deviceMessage, uniqueId, ts, "DeviceId", deviceMessage.getDeviceId());
        try {
            MessageIntake.route(deviceMessage);
        } catch (Exception e) {
            throw e;
        }

        return "Accepted";
    }

}
