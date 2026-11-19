//Copyright 2021 Sean Murdock

package com.getsimplex.steptimer.service;

import com.getsimplex.steptimer.utils.InvalidLoginException;
import com.google.gson.Gson;
import com.getsimplex.steptimer.model.LoginRequest;
import com.getsimplex.steptimer.model.User;
import org.apache.commons.codec.digest.DigestUtils;
import spark.Request;
import com.getsimplex.steptimer.utils.JedisClient;

import java.util.logging.Logger;

import static com.getsimplex.steptimer.service.TokenService.*;
import static com.getsimplex.steptimer.utils.JedisData.deleteFromRedis;

/**
 * Created by mandy on 9/22/2016.
 */

public class LoginController {
    private static Gson gson = new Gson();
    private static JedisClient jedisClient = new JedisClient();

    public static String handleRequest(Request request) throws Exception{
        String loginRequestString = request.body();
        LoginRequest loginRequest = gson.fromJson(loginRequestString, LoginRequest.class);
        return tryLogin(loginRequest.getUserName().toLowerCase(), loginRequest.getPassword());

    }

    public static String tryLogin(String userName, String password) throws Exception{
        if(isValidPassword(userName, password)){

                String newToken = createUserToken(userName);
                return newToken;
        }else{
            throw new InvalidLoginException("Invalid Login");
        }
    }



    public static Boolean isValidPassword(String unauthenticatedName, String attemptedPwValue)throws Exception {
        boolean passwordIsValid = false;
        boolean userNameIsValid=false;
        User currentUser = FindUser.getUserByUserName(unauthenticatedName);

        //use if logging in with Redis
        try {
            if (unauthenticatedName != null && !unauthenticatedName.isEmpty()) {
//
                String userName = currentUser.getUserName();

                if (userName.equals(unauthenticatedName)) {
                    userNameIsValid = true;
                }
            }

            if (userNameIsValid) {

                String storedHashedPassword = currentUser.getPassword();

                // Phone-only accounts (created by /signup/verify) have no password.
                // They sign in with an SMS code, so password login is always refused for them.
                if (storedHashedPassword == null || storedHashedPassword.isEmpty()
                        || attemptedPwValue == null || attemptedPwValue.isEmpty()) {
                    return false;
                }

                String attemptedHashedPassword = DigestUtils.sha256Hex(attemptedPwValue);

                if (storedHashedPassword.equals(attemptedHashedPassword)) {
                    passwordIsValid = true;
                }
            }
        } catch (Exception e){
            Logger.getLogger(LoginController.class.getName()).severe(e.getMessage());
        }


        return (passwordIsValid && userNameIsValid);
    }



}
