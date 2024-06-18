package com.getsimplex.steptimer.service;

/**
 Copyright 2021 Sean Murdock
 * Created by sean on 8/10/2016 based on https://github.com/tipsy/spark-websocket/tree/master/src/main/java
 */


import com.getsimplex.steptimer.model.*;
import com.getsimplex.steptimer.tcp.NettyServerBootstrap;
import com.getsimplex.steptimer.utils.*;
import com.google.gson.Gson;
import org.apache.commons.codec.digest.DigestUtils;
import spark.Filter;
import spark.Request;
import spark.Response;
import spark.Spark;

import java.util.Date;
import java.util.logging.Logger;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

import static spark.Spark.*;

public class WebAppRunner {

    private static Gson gson = new Gson();

    private static Logger logger = Logger.getLogger(WebAppRunner.class.getName());

    private static String CONTENT_SID= "";

    static{
        CONTENT_SID = System.getenv("TWILIO_OTP_MESSAGE_SID");
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
        patch("/user/:username",(req,res)->{//update password
            userFilter(req,res);
            User existingUser = (User) JedisData.getFromRedisMap(req.params("username"), User.class);
            String response = "";
            if (existingUser!=null){
                User userUpdate = gson.fromJson(req.body(),User.class);
                if (CreateNewUser.validatePassword(userUpdate.getPassword())){
                    existingUser.setPassword(DigestUtils.sha256Hex(userUpdate.getPassword()));
                    JedisData.updateRedisMap(existingUser,existingUser.getUserName());
                    res.status(200);
                    response = "Updated password";
                    res.body(response);
                } else{
                    res.status(400);
                    response="Password doesn't meet requirements";
                    res.body(response);
                }
            } else{
                res.status(404);//user not found
                response = "User "+req.params("username")+" not found.";
                res.body(response);
            }

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
            res.type("application/json");
            //It only allows them to log in with their own user as long as it exits in the dev.stedi.me application
            Gson gson = new Gson();
            TextMessage textMessage = gson.fromJson(req.body(), TextMessage.class);//
            Optional<User> userOptional = userFilter(req,res);
            if (!userOptional.isEmpty() && userOptional.get().getPhone().equals(SendText.getFormattedPhone(textMessage.getPhoneNumber()))) {
                SendText.send(textMessage.getPhoneNumber(), textMessage.getMessage());
                res.status(200);
                return "Text Sent";
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
           Optional<User> loggedInUser = userFilter(req,res);
           if (loggedInUser.get().getEmail().equals("scmurdock@gmail.com")) {//only allow admins to delete users
               CreateNewUser.deleteUser(req.params("username"));
           }
           return "deleted user";
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
            Customer customer = CustomerService.getCustomerByPhone(req.params(":customerPhone"));//get existing customer from database
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
            Optional<User> optionalUser = Optional.empty();
            try {
              optionalUser = userFilter(req, res);
            } catch (Exception e){
                res.status(401);
                logger.warning("*** Error Finding Customer: "+e.getMessage());
                System.out.println("*** Error Finding Customer: "+e.getMessage());
                return null;
            }
            if(optionalUser.isPresent() && optionalUser.get().getPhone().equals( SendText.getFormattedPhone(phone))){
                String customerJson =gson.toJson(CustomerService.getCustomerByPhone(phone));
                res.type("application/json");
                res.body(customerJson);
                return customerJson;
            }
            return  null;
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
        int randomNum = ThreadLocalRandom.current().nextInt(1111, 10000);
        User user=null;
        try {
            phoneNumber = SendText.getFormattedPhone(phoneNumber);
            user = FindUser.getUserByPhone(phoneNumber);
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
                    SendWhatsApp.send(phoneNumber, CONTENT_SID, String.valueOf(randomNum));
                }
                else{
                    SendText.send(phoneNumber, "Your STEDI one-time password is : "+String.valueOf(randomNum));
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

            Optional<User> user = TokenService.getUserFromToken(tokenString);//

            Boolean tokenExpired = SessionValidator.validateToken(tokenString);

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
