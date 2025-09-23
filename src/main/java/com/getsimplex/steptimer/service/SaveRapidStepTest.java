//Copyright 2021 Sean Murdock

package com.getsimplex.steptimer.service;

import com.getsimplex.steptimer.datarepository.CustomerRepository;
import com.getsimplex.steptimer.datarepository.RapidStepTestRepository;
import com.getsimplex.steptimer.model.Customer;
import com.getsimplex.steptimer.model.DeviceMessage;
import com.getsimplex.steptimer.model.User;
import com.getsimplex.steptimer.utils.JedisClient;
import com.google.gson.Gson;
import com.getsimplex.steptimer.model.RapidStepTest;
import com.getsimplex.steptimer.utils.GsonFactory;

import java.util.Optional;


/**
 * Created by sean on 9/7/2016.
 */
public class SaveRapidStepTest {
    private static Gson gson = GsonFactory.getGson();

    private static RapidStepTestRepository rapidStepTestRepository = new RapidStepTestRepository();

    public static void save(String rapidStepTestString) throws Exception{
        System.out.println("Rapid Step Test:"+rapidStepTestString);
        RapidStepTest rapidStepTest = gson.fromJson(rapidStepTestString, RapidStepTest.class);
        User user = FindUser.getUserByUserName(rapidStepTest.getCustomer());//we are assuming the user is testing their own risk

        if (rapidStepTest.getDeviceId()==null || rapidStepTest.getDeviceId().isEmpty()){
            // if the device ID is not set, we will set it to the user's default device nickname
            rapidStepTest.setDeviceId(user.getDeviceNickName());
        }   

        rapidStepTestRepository.addToArrayAtKey(user.getPhone(),rapidStepTest);
        // The above adds the data to a redis key called RapidStepTests which is a JSON object with the key being the customer phone number:
        // - the array for each phone number contains all the rapid step tests for the customer
        //- the array is in ascending order of the creation time of the test

        //TO-DO: we need to create a concept of a user session when a user has begun a rapid step test
        // then each step we receive will be added to that session in the database
        // when they have reached the end of 30 steps, we will consider the session half done
        // when they have reached the end of 60 steps, we will consider the session done
        // we will then calculate the score and save it to the database


        ///===================================================================================================
        // Process for enhancement (once we have the ability to process steps based on sensor data)
        ///===================================================================================================
        // Step 1: the mobile app etc. POSTs a  RapidStepTest with only a startTime, a username, and a device ID
        // Step 2: we create a semaphore for that device ID in the database so tying the username and the device ID
        // Step 2: the IoT device sends SensorUpdate POST requests with a device ID, stepTimes, and an end time
        // Step 3: When we receive the PATCH requests we read the semaphore and determine which user has an active session with the device ID
        // Step 4: We update the RapidStepTest with the stepTimes, and if applicable an end time
        // Step 5: If we received an end time we delete the semaphore and subsequent device PATCH requests won't update that user's records
        // Repeat Step 1 for the next user

        //TO-DO: we currently  have 2 concepts of how a user could complete a session -

        //(1) the user is logged into an app or a web application and the web app or device
        // is keeping the count of how many steps the user has taken
        // then the web app or device sends the data to the server when the user has completed the test
        //(2) The user is using a device that is connected to the server and the server is keeping track of the steps
        // the user has taken and when the user has completed the test
        // ** We need to create a different way of starting a session and saving the sensor data for the second scenario


        //TO-DO: if a user tries to start a session with a device that is already in use, we need to prevent that from happening
        // so we need to block their request and give some kind of message that the device is already in use, unless it is the same user
        // or if the session has been cancelled or has completed
        DeviceMessage deviceMessage = new DeviceMessage();
        if(rapidStepTest.getDeviceId()!=null){
            deviceMessage.setDeviceId(rapidStepTest.getDeviceId());
        }else {
            deviceMessage.setDeviceId(user.getEmail());
        }
        deviceMessage.setMessage(rapidStepTestString);

        MessageIntake.route(deviceMessage);

    }
}
