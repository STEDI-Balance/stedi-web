//Copyright 2021 Sean Murdock

package com.getsimplex.steptimer.utils;

import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;
import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.rest.api.v2010.account.MessageCreator;
import com.twilio.type.PhoneNumber;

public class SendText {
    private static String ACCOUNT_SID ="";
    private static String AUTH_TOKEN = "";
    private static String TWILIO_PHONE= "";


    static {

        ACCOUNT_SID = System.getenv("TWILIO_ACCOUNT_SID");
        AUTH_TOKEN = System.getenv("TWILIO_AUTH_TOKEN");
        TWILIO_PHONE = System.getenv("TWILIO_PHONE");
        if (ACCOUNT_SID != null && !ACCOUNT_SID.isEmpty() && AUTH_TOKEN != null && !AUTH_TOKEN.isEmpty()) {//without credentials (local or student runs) skip init so phone formatting still works
            Twilio.init(ACCOUNT_SID, AUTH_TOKEN);
        }
    }

    public static void send(String destinationPhone, String text, String region) throws Exception{

        String formattedPhone = getFormattedPhone(destinationPhone, region);
        PhoneNumber destination = new PhoneNumber(formattedPhone);
        PhoneNumber origin = new PhoneNumber(TWILIO_PHONE);

        Message message =Message.creator(destination, origin, text).create();

        System.out.println("**** Sent Text: ID "+message.getSid());

    }

    public static String getFormattedPhone(String inputPhone, String region) throws Exception{
        PhoneNumberUtil phoneUtil = PhoneNumberUtil.getInstance();
        Phonenumber.PhoneNumber phoneNumber = phoneUtil.parse(inputPhone, region);
        String formattedPhone = phoneUtil.format(phoneNumber, PhoneNumberUtil.PhoneNumberFormat.INTERNATIONAL);
        formattedPhone = formattedPhone.replace(" ","");
        return formattedPhone;
    }

}
