package com.getsimplex.steptimer.service;

/**
 Copyright 2021 Sean Murdock
 * Created by sean on 8/10/2016 based on https://github.com/tipsy/spark-websocket/tree/master/src/main/java
 */


import com.getsimplex.steptimer.model.*;
import com.getsimplex.steptimer.tcp.NettyServerBootstrap;
import com.getsimplex.steptimer.utils.*;
import com.google.common.util.concurrent.RateLimiter;
import com.google.gson.Gson;
import org.apache.commons.codec.digest.DigestUtils;
import spark.Filter;
import spark.Request;
import spark.Response;
import spark.Spark;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.concurrent.ThreadLocalRandom;

import static spark.Spark.*;

public class WebAppRunner {

    private static Gson gson = new Gson();

    private static Logger logger = Logger.getLogger(WebAppRunner.class.getName());

    private static String TWILIO_OTP_MESSAGE_SID = "";
    private static String TWILIO_SECTOR_MESSAGE_SID;
    private static ConcurrentHashMap<String,RateLimiter> rateLimiters = new ConcurrentHashMap<>();

    static{
        TWILIO_OTP_MESSAGE_SID = System.getenv("TWILIO_OTP_MESSAGE_SID");
        TWILIO_SECTOR_MESSAGE_SID = System.getenv("TWILIO_SECTOR_MESSAGE_SID");
    }

    public static void startTCPSocket(){
        try{
            NettyServerBootstrap nettyServerBootStrap = new NettyServerBootstrap();
            nettyServerBootStrap.start(54321);
        } catch(InterruptedException exception){

        }
    }

    public static void main(String[] args){

        Spark.port(getHerokuAssignedPort());

        staticFileLocation("/public");
        webSocket("/socket", DeviceWebSocketHandler.class);
        webSocket("/timeruiwebsocket", TimerUIWebSocket.class);

        after((Filter) (request, response) -> {
            response.header("Access-Control-Allow-Origin", "*");
            response.header("Access-Control-Allow-Methods", "GET, PUT, POST, DELETE, PATCH, OPTIONS");
            response.header("Access-Control-Allow-Headers", "Content-Type,Authorization,X-Requested-With,Content-Length,Accept,Origin,");
            response.header("Access-Control-Allow-Credentials", "true");
        });
        post("/contact", (req, res)->{
            userFilter(req,res);
            try {
                EmailMessage emailMessage = gson.fromJson(req.body(), EmailMessage.class);
                SendGmail.send(emailMessage.getToAddress(), emailMessage.getMessageText(), emailMessage.getSubject(), emailMessage.getName());

                System.out.println(req.body());
                res.status(200);

            }catch(Exception e){
                return null;
            }
            return "sent";
        });
		//secure("/Applications/steptimerwebsocket/keystore.jks","password","/Applications/steptimerwebsocket/keystore.jks","password");

        //post("/sensorUpdates", (req, res)-> WebServiceHandler.routeDeviceRequest(req));
        //post("/generateHistoricalGraph", (req, res)->routePdfRequest(req, res));
        //get("/readPdf", (req, res)->routePdfRequest(req, res));
        post("/user", (req, res)-> {
            String response="Error creating user";
            try {
                response = callUserDatabase(req);
            } catch (AlreadyExistsException ae){
                res.status(409);
                System.out.println("User already exists");
            } catch (Exception e){
                res.status(500);
                System.out.println("Error creating user");
            }
            res.type("application/json");
            res.body(response);
            return response;
        }
        );

        patch("/user/:username", (req, res) -> {
            Optional<User> loggedInUserOptional = userFilter(req, res);
            String existingUserName = req.params("username");
            if(loggedInUserOptional.isEmpty() || !existingUserName.equals(loggedInUserOptional.get().getUserName())){//users can only update their own information
                res.status(403); // Forbidden
                res.body("You are not authorized to update this user.");
                return "You are not authorized to update this user.";
            }

            User existingUser = (User) JedisData.getFromRedisMap(existingUserName, User.class);
            String response = "";

            if (existingUser != null) {
                User userUpdate = gson.fromJson(req.body(), User.class);

                boolean updated = false;

                // Update password if provided
                if (userUpdate.getPassword() != null) {
                    if (CreateNewUser.validatePassword(userUpdate.getPassword())) {
                        existingUser.setPassword(DigestUtils.sha256Hex(userUpdate.getPassword()));
                        updated = true;
                    } else {
                        res.status(400);
                        response = "Password doesn't meet requirements";
                        res.body(response);
                        return response;
                    }
                }

                // Update expoPushToken if provided
                if (userUpdate.getExpoPushToken() != null) {
                    existingUser.setExpoPushToken(userUpdate.getExpoPushToken());
                    updated = true;
                }

                if (updated) {
                    JedisData.updateRedisMap(existingUser, existingUser.getUserName());
                    res.status(200);
                    response = "User updated successfully";
                } else {
                    res.status(400);
                    response = "No valid fields to update";
                }
            } else {
                res.status(404); // User not found
                response = "User " + req.params("username") + " not found.";
            }

            res.body(response);
            return response;
        });

        get("/validate/:token", (req,res)->{
            String emailAddress = SessionValidator.emailFromToken(req.params(":token"));
            if(emailAddress.isEmpty() || emailAddress==null){
                res.status(401);
            }
            res.body(emailAddress);
            res.type("application/json");
            return emailAddress;
        });
        post("/sendtext",(req,res)->{//this url is for the DevOps class at BYUI so they can deploy STEDI and not need Twilio Credentials
            req.ip();
            String region = req.queryParams("region");
            if(region==null || region.isEmpty()){
                region="US";//default to US
            }
            res.type("application/json");
            //It only allows them to log in with their own user as long as it exits in the dev.stedi.me application
            Gson gson = new Gson();
            TextMessage textMessage = gson.fromJson(req.body(), TextMessage.class);//
            Optional<User> userOptional = userFilter(req,res);
            if (!userOptional.isEmpty() && userOptional.get().getPhone().equals(SendText.getFormattedPhone(textMessage.getPhoneNumber(), region))) {
                String clientIp = req.ip();
                // Get rate limiter for the current IP address
                RateLimiter rateLimiter = rateLimiters.computeIfAbsent(clientIp, k -> RateLimiter.create(1)); // 1 requests per second per IP address
                // If the rate limiter allows, proceed with the request
                if (rateLimiter.tryAcquire()) {

                    SendText.send(textMessage.getPhoneNumber(), textMessage.getMessage(),region);
                    res.status(200);
                    System.out.println("Text sent for source IP "+ clientIp);
                    return "Text Sent";

                } else {
                    res.status(429); // Too Many Requests
                    System.out.println("Rate limit exceeded for source IP " + clientIp);
                    return "Rate limit exceeded. Try again later.";
                }
            } else{
                res.status(400);
                return "Recipient doesn't match user or user not found: Text not sent";
            }
        });
        get("/simulation", (req, res) -> SimulationDataDriver.getSimulationActive());
        post("/simulation", (req, res)-> MessageIntake.route(new StartSimulation(30)));
        delete("/simulation", (req, res)-> MessageIntake.route(new StopSimulation()));
        post("/complexity", (req,res)->{
            res.type("application/json");
            Gson gson = new Gson();
            Boolean validPassword = CreateNewUser.validatePassword(gson.fromJson(req.body(), User.class).getPassword());

            if (validPassword){
                res.status(200);
            } else{
                res.status(400);
            }
            return validPassword;
        });
        delete("/user/:username", (req,res)->{
            try {
                Optional<User> loggedInUser = userFilter(req,res);
                User userToDelete =  JedisData.getFromRedisMap(req.params("username"), User.class);
                Boolean isAdmin = loggedInUser.get().getEmail().equals("scmurdock@gmail.com"); //check if user is admin
                Boolean isTheSameUser = loggedInUser.get().getEmail().equals(userToDelete.getEmail()); //check if the requesting user and the user to be deleted are the same
                if (isAdmin || isTheSameUser) { //allow admins to delete any user, and users to delete themselves
                    CreateNewUser.deleteUser(userToDelete.getUserName());
                    CustomerService.deleteCustomer(userToDelete.getPhone());
                    return "Deleted user " + userToDelete.getUserName();
                } else { // if the user is not an admin nor the same user, return unauthorized
                    res.status(401);
                    return "Unauthorized";
                }
            } catch (Exception e) { // if an error occurs, return a 500 error
                res.status(500);
                return "Error deleting user: " + e.getMessage();
            }
        });
        get ("/stephistory/:customer", (req, res)-> {
            res.type("application/json");
            try{
                userFilter(req, res);
            } catch (Exception e){
                res.redirect("/");
            }
            String allTests = StepHistory.getAllTests(req.params(":customer"));
            res.body(allTests);
            return allTests;
        });
        post("/customer", (req, res)-> {
            userFilter(req,res);//new users receive a login token before their customer profile is created, so we filter this request
            String response;
            try {
                createNewCustomer(req, res);
                response="Successfully created customer";
            }

            catch (AlreadyExistsException ae){
                logger.info("User already exists");
                System.out.println("User already exists");
                res.status(409);
                logger.info("Error creating customer");
                response="Error creating customer";
            }

            catch (Exception e){
                logger.warning("*** Error Creating Customer: "+e.getMessage());
                System.out.println("*** Error Creating Customer: "+e.getMessage());
                res.status(500);
                response="Error creating customer";
            }
            res.type("application/json");
            res.body(response);
            return response;
        });

        patch("/customer/lastwalkerdate/:customerPhone/:days", (req,res) ->{
            userFilter(req,res);//new users receive a login token before their customer profile is created, so we filter this request
            Customer customer = CustomerService.getCustomerByPhone(req.params(":customerPhone"), req.queryParams("region"));//get existing customer from database

            if (customer != null) {
                int days = Integer.parseInt(req.params(":days"));

                // Calculate the new lastWalkerDate based on the user's input
                long currentTimeMillis = System.currentTimeMillis();
                long daysInMillis = days * 24 * 60 * 60 * 1000L; // Convert days to milliseconds
                long newLastWalkerDateMillis = currentTimeMillis - daysInMillis;

                // Update the lastWalkerDate of the customer
                customer.setLastWalkerDate(new Date(newLastWalkerDateMillis));

                // Assuming you have a method to update the customer in your service
                // You need to implement this method if not already implemented
                CustomerService.createOrUpdateCustomer(customer, true);
                res.type("application/json");
                res.body(gson.toJson(customer));
                res.status(200);
                return gson.toJson(customer);
            } else {
                res.status(404); // Customer not found
                return "Customer not found";
            }

        });

        put("/customer", (req, res)-> {
            userFilter(req,res);//new users receive a login token before their customer profile is created, so we filter this request

            String response;
            try {
                updateCustomer(req, res);
                response="Successfully created customer";
            }

            catch (AlreadyExistsException ae){
                logger.info("User already exists");
                System.out.println("User already exists");
                res.status(409);
                logger.info("Error creating customer");
                response="Error creating customer";
            }

            catch (Exception e){
                logger.warning("*** Error Creating Customer: "+e.getMessage());
                System.out.println("*** Error Creating Customer: "+e.getMessage());
                res.status(500);
                response="Error creating customer";
            }
            res.type("application/json");
            res.body(response);
            return response;
        });

        get("/customer/:phone", (req, res)-> {
            userFilter(req,res);//new users receive a login token before their customer profile is created, so we filter this request
            res.type("application/json");
            String phone =  req.params(":phone");
            String region =  req.queryParams("region");
            Optional<User> optionalUser = Optional.empty();
            try {
              optionalUser = userFilter(req, res);
            } catch (Exception e){
                res.status(401);
                logger.warning("*** Error Finding Customer: "+e.getMessage());
                System.out.println("*** Error Finding Customer: "+e.getMessage());
                return null;
            }
            if(optionalUser.isPresent() && optionalUser.get().getPhone().equals( SendText.getFormattedPhone(phone,  optionalUser.get().getRegion()))){
                String customerJson =gson.toJson(CustomerService.getCustomerByPhone(phone, region));
                res.type("application/json");
                res.body(customerJson);
                return customerJson;
            }
            return  null;
        });

        get("/pushtokentestonly/:userName", (req, res) -> {
            String requestedUserName = req.params(":userName");
            List<String> authorizedSenders = new ArrayList<>();
            Optional<User> loggedInUserOptional = userFilter(req, res);

            if (loggedInUserOptional.isPresent()) {
                authorizedSenders.add("scmurdock@gmail.com");
                authorizedSenders.add("physician@stedi.com");
                authorizedSenders.add(loggedInUserOptional.get().getUserName());
            } else{
                res.status(401);
                return "Unauthorized access.";
            }

            //physicans or admins can contact anyone
            //anyone can contact the physican
            if (authorizedSenders.contains(loggedInUserOptional.get().getUserName()) || requestedUserName.equals("physician@stedi.com")) {
                User requestedUser = FindUser.getUserByUserName(requestedUserName);

                if (requestedUser != null) {
                    // Remove sensitive information
                    requestedUser.setPassword(null);
                    res.type("application/json");
                    return gson.toJson(requestedUser.getExpoPushToken());
                } else {
                    res.status(404);
                    return "User not found.";
                }
            } else {
                res.status(403);
                return "Unauthorized access.";
            }
        });

        post("/simulatePushNotificationToPhysician", (req, res) -> {
            try {
                // Parse incoming JSON safely
                String body = req.body();
                java.lang.reflect.Type mapType = new com.google.gson.reflect.TypeToken<Map<String, Object>>(){}.getType();
                Map<String, Object> requestBody = gson.fromJson(body, mapType);
                // Check for 'data' field and ensure it's a Map
                Object dataObj = requestBody.get("data");
                if (!(dataObj instanceof Map)) {
                    res.status(400);
                    return "Missing or invalid 'data' field.";
                }
                Map<?,?> data = (Map<?,?>) dataObj;
                // Check for 'username' field in data
                Object usernameObj = data.get("username");
                String username = usernameObj != null ? usernameObj.toString() : null;
                if (username == null) {
                    res.status(400);
                    return "Missing 'username' in data field (it is case-sensitive).";
                }
                // Find user by username
                User sender = FindUser.getUserByUserName(username);
                if (sender == null) {
                    res.status(404);
                    return "Unable to find user with username: " + username;
                }
                // Check if user has an Expo push token
                String expoPushToken = sender.getExpoPushToken();
                if (expoPushToken == null || expoPushToken.isEmpty()) {
                    res.status(404);
                    return "Unable to find an Expo push token for " + username;
                }
                // Build message for Expo API to send a push notification back to sender
                Map<String, Object> message = new HashMap<>();
                message.put("to", expoPushToken);
                message.put("sound", "default");
                message.put("title", "Thank you for sending your data!");
                message.put("body", "A physician has received your data and will review it shortly.");
                String expoApiUrl = "https://exp.host/--/api/v2/push/send";
                java.net.URI uri = java.net.URI.create(expoApiUrl);
                java.net.URL url = uri.toURL();
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Accept", "application/json");
                conn.setRequestProperty("Accept-encoding", "gzip, deflate");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                String jsonMessage = gson.toJson(message);
                try (java.io.OutputStream os = conn.getOutputStream()) {
                    byte[] input = jsonMessage.getBytes("utf-8");
                    os.write(input, 0, input.length);
                }
                int status = conn.getResponseCode();
                java.io.InputStream is = (status < 400) ? conn.getInputStream() : conn.getErrorStream();
                String responseBody;
                try (java.util.Scanner s = new java.util.Scanner(is).useDelimiter("\\A")) {
                    responseBody = s.hasNext() ? s.next() : "";
                }
                res.status(status);
                return responseBody;
            } catch (Exception e) {
                res.status(500);
                return "Error: " + e.getMessage();
            }
        });

        post("/login", (req, res)->loginUser(req, res));
        post("/twofactorlogin/:phoneNumber",(req, res) -> twoFactorLogin(req, res));

        post("/twofactorlogin", (req, res) ->{
            String response = "";
           try{
               response=OneTimePasswordService.handleRequest(req);
           } catch (NotFoundException nfe){
               res.status(404);
               response= nfe.getMessage();
           } catch (ExpiredException ee){
               res.status(401);
               response= ee.getMessage();
           } catch (Exception e){
                res.status(500);
                response = "Unexpected error";
           }
            return response;
        });
        post("/rapidsteptest", (req, res)->{
            String returnBody="";
            try{
                Optional<User> user = userFilter(req, res);
                RapidStepTest rapidStepTest = gson.fromJson(req.body(), RapidStepTest.class);

                if(user.isPresent() && user.get().getEmail().equals(rapidStepTest.getCustomer())) {

                    saveStepSession(req);
                    returnBody= "Saved";
                } else{
                    res.status(401);
                    returnBody= "Error authenticating user";
                }

            } catch (Exception e){
                res.status(500);
            }
            return returnBody;
        });



        //TO-DO: the secure implementation
        post("/notify/:recipient",((req,res)->{
            Optional<User> user = userFilter(req, res);//this is the user sending the notification
            String recipientEmail = req.params(":recipient");//this should be the email address of the recipient  of the message
            //TO-DO: look up if the recipient gave their consent to share their balance record with the user sending the notification
            boolean userSharedWithRequestor = true;
            if(userSharedWithRequestor){
                User recipient = FindUser.getUserByUserName(recipientEmail);
                /// String expoPushNotificationToken = recipient.getExpoPushNotificationToken();

                //TO-DO: call the EAS endpoint and send them the message
            }
            res.status(200);
            return null;
        }));
        get("/riskscore/:customer",((req,res) -> {
            String customer = req.params(":customer");
            String returnBody = "";
            try{
                Optional<User> user = userFilter(req, res);

                if (user.isPresent() && user.get().getEmail().equals(customer)) {
                    returnBody= riskScore(req.params(":customer"));
                } else{
                    res.status(404);
                    returnBody= "Unable to locate customer for risk score: "+customer;
                }
            } catch (Exception e){
                logger.info("*** Issue Finding Risk Score for: "+customer+ " "+e.getMessage());
                System.out.println("*** Issue Finding Risk Score: "+e.getMessage());
                if(e instanceof NotFoundException){
                    ErrorPayload errorPayload= new ErrorPayload(e.getMessage());
                    returnBody=gson.toJson(errorPayload);
                    res.status(412);//precondition failed
                } else{
                    res.status(400);
                }
            }
            res.type("application/json");
            res.body(returnBody);
            return returnBody;
        }));

        options("/*",
                (request, response) -> {

                    String accessControlRequestHeaders = request
                            .headers("Access-Control-Request-Headers");
                    if (accessControlRequestHeaders != null) {
                        response.header("Access-Control-Allow-Headers",
                                accessControlRequestHeaders);
                    }

                    String accessControlRequestMethod = request
                            .headers("Access-Control-Request-Method");
                    if (accessControlRequestMethod != null) {
                        response.header("Access-Control-Allow-Methods",
                                accessControlRequestMethod);
                    }

                    return "OK";
        });
        init();
        startTCPSocket();
    }
    private static String twoFactorLogin(Request request, Response response){
        String phoneNumber =  request.params(":phoneNumber");
        Boolean whatsApp = Boolean.valueOf(request.queryParams("whatsApp"));
        String region = request.queryParams("region");
        int randomNum = ThreadLocalRandom.current().nextInt(1111, 10000);
        User user=null;
        try {
            phoneNumber = SendText.getFormattedPhone(phoneNumber, region);
            user = FindUser.getUserByPhone(phoneNumber, region);
            if (user!=null){
                Long expiration = new Date().getTime()+100l * 365l * 24l *60l * 60l *1000l;//100 years
                String loginToken=TokenService.createUserTokenSpecificTimeout(user.getUserName(), expiration);
                OneTimePassword oneTimePassword = new OneTimePassword();
                oneTimePassword.setOneTimePassword(randomNum);
                oneTimePassword.setExpirationDate(new Date(System.currentTimeMillis()+60l*30l*1000l));//30 minute OTP expiration
                oneTimePassword.setLoginToken(loginToken);
                oneTimePassword.setPhoneNumber(phoneNumber);
                OneTimePasswordService.saveOneTimePassword(oneTimePassword);

                if(whatsApp && !user.getWhatsAppPhone().isEmpty()){
                    SendWhatsApp.send(phoneNumber, TWILIO_OTP_MESSAGE_SID, String.valueOf(randomNum), region);
                }
                else{
                    SendText.send(phoneNumber, "Your STEDI one-time password is : "+String.valueOf(randomNum), region);
                }
                response.status(200);

            } else{
                response.status(400);
                logger.info("Unable to find user with phone number: "+phoneNumber);
                System.out.println("Unable to find user with phone number: "+phoneNumber);

            }
        } catch (Exception e){
            response.status(500);
            logger.info("Error while looking up user "+phoneNumber+" "+e.getMessage());
            System.out.println("Error while looking up user "+phoneNumber+" "+e.getMessage());
        }

        if (user==null){
            return "Unable to find user with phone number: "+phoneNumber;
        } else{
            return "Ok";
        }
    }

    private static Optional<User> userFilter(Request request, Response response) throws Exception{
            String tokenString = request.headers("suresteps.session.token");
            Set<String> headers = request.headers();
            Optional<User> user = TokenService.getUserFromToken(tokenString);//

            Boolean tokenExpired = SessionValidator.tokenIsExpired(tokenString);

            if (user.isPresent() && tokenExpired && !user.get().isLocked()){//if a user is locked, we won't renew tokens until they are unlocked
                TokenService.renewToken(tokenString);
                return user;
            }

            if (!user.isPresent()) { //Check to see if session expired
                logger.info("Invalid user token: user not found using token: "+tokenString);
                throw new Exception("Invalid user token: user not found using token: "+tokenString);
            }

            if (tokenExpired.equals(true)){
                logger.info("Invalid user token: "+tokenString+" expired");
                throw new Exception("Invalid user token: "+tokenString+" expired");
            }
        return user;
    }




    public static void createNewCustomer(Request request, Response response) throws Exception{
            CustomerService.handleRequest(request, false);
    }

    public static void updateCustomer(Request request, Response response) throws Exception{
        CustomerService.handleRequest(request, true);
    }

    private static String callUserDatabase(Request request)throws Exception{
        return CreateNewUser.handleRequest(request);
    }

    private static String loginUser(Request request, Response response) throws Exception{
        String responseText="";

        try{

            String token = responseText=LoginController.handleRequest(request);
            response.cookie("stedi-token",token);
        } catch(InvalidLoginException ile){
            response.status(401);
        }

        return responseText;

    }

    private static String riskScore(String email) throws Exception{
        return StepHistory.riskScore(email);
    }

    private static void saveStepSession(Request request) throws Exception{
        SaveRapidStepTest.save(request.body());
    }


    private static int getHerokuAssignedPort() {
        ProcessBuilder processBuilder = new ProcessBuilder();
        if (processBuilder.environment().get("PORT") != null) {
            return Integer.parseInt(processBuilder.environment().get("PORT"));
        }
        return Configuration.getConfiguration().getInt("suresteps.port"); //return default port if heroku-port isn't set (i.e. on localhost)
    }

}
