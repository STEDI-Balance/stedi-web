//Copyright 2021 Sean Murdock

package com.getsimplex.steptimer.service;

import com.getsimplex.steptimer.model.ExchangeOTPForLoginToken;
import com.getsimplex.steptimer.model.OneTimePassword;
import com.getsimplex.steptimer.utils.ExpiredException;
import com.getsimplex.steptimer.utils.JedisData;
import com.getsimplex.steptimer.utils.NotFoundException;
import com.getsimplex.steptimer.utils.SendText;
import com.google.gson.Gson;
import scala.concurrent.java8.FuturesConvertersImpl;
import spark.Request;

import java.util.Date;
import java.util.Optional;

public class OneTimePasswordService {
    private static Gson gson = new Gson();

    public static String handleRequest(Request request) throws Exception{
        String requestBody = request.body();
        ExchangeOTPForLoginToken exchangeOTPForLoginToken = gson.fromJson(requestBody, ExchangeOTPForLoginToken.class);
        if(exchangeOTPForLoginToken.getRegion()==null || exchangeOTPForLoginToken.getRegion().isEmpty()){
            exchangeOTPForLoginToken.setRegion("US");
        }
        return exchangeOneTimePasswordForToken(exchangeOTPForLoginToken);
    }

    public static void saveOneTimePassword(OneTimePassword oneTimePassword) throws Exception{
        JedisData.loadToJedis(oneTimePassword, String.valueOf(oneTimePassword.getOneTimePassword()), oneTimePassword.getExpirationDate().getTime());
    }

    public static Optional<OneTimePassword> getOneTimePassword(Integer oneTimePassword) throws Exception{
        return JedisData.getEntityById(OneTimePassword.class, String.valueOf(oneTimePassword));
    }

    public static String exchangeOneTimePasswordForToken(ExchangeOTPForLoginToken otpForToken) throws Exception{
        Optional<OneTimePassword> otp = getOneTimePassword(otpForToken.getOneTimePassword());
        otpForToken.setPhoneNumber(SendText.getFormattedPhone(otpForToken.getPhoneNumber(), otpForToken.getRegion()));
        String loginToken="";
        if (!otp.isPresent() || otp.isPresent() && !otp.get().getPhoneNumber().equals(otpForToken.getPhoneNumber())){
            throw new NotFoundException("OTP :"+otpForToken.getOneTimePassword()+" not found for phone: "+otpForToken.getPhoneNumber());
        }

        if (otp.isPresent() && otp.get().getPhoneNumber().equals(otpForToken.getPhoneNumber()) && otp.get().getExpirationDate().before(new Date(System.currentTimeMillis()))){
            throw new ExpiredException("OTP: "+otpForToken.getOneTimePassword()+" for phone: "+otpForToken.getPhoneNumber()+" expired at: "+otp.get().getExpirationDate().getTime());
        }

        if (otp.isPresent() && otp.get().getPhoneNumber().equals(otpForToken.getPhoneNumber()) && otp.get().getExpirationDate().after(new Date())){
            loginToken= otp.get().getLoginToken();
        }

        return loginToken;
    }

    public static boolean verifyOneTimePasswordForPhone(String phoneNumber, Integer oneTimePassword, String region) throws Exception{
        if (oneTimePassword == null){
            return false;
        }

        Optional<OneTimePassword> otp = getOneTimePassword(oneTimePassword);
        String formattedPhoneNumber = SendText.getFormattedPhone(phoneNumber, region);

        return otp.isPresent()
                && otp.get().getPhoneNumber().equals(formattedPhoneNumber)
                && otp.get().getExpirationDate().after(new Date());
    }

}
