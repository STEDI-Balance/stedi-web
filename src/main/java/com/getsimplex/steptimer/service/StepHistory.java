//Copyright 2021 Sean Murdock

package com.getsimplex.steptimer.service;

import com.getsimplex.steptimer.datarepository.RapidStepTestRepository;
import com.getsimplex.steptimer.model.*;
import com.getsimplex.steptimer.utils.NotFoundException;
import com.google.gson.Gson;
import com.getsimplex.steptimer.utils.GsonFactory;
import com.getsimplex.steptimer.utils.JedisData;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;


/**
 * Created by .
 */
public class StepHistory {
    private static Logger logger = Logger.getLogger(StepHistory.class.getName());
    private static Gson gson = GsonFactory.getGson();

    private static RapidStepTestRepository rapidStepTestRepository = new RapidStepTestRepository();




    public static String getAllTests(String phoneNumber) throws Exception{
        //List<RapidStepTest> rapidStepTests = JedisData.getEntitiesByIndex(RapidStepTest.class, "CustomerId",email);
        List<RapidStepTest> rapidStepTests = rapidStepTestRepository.getArrayAtKey(phoneNumber);
        return (gson.toJson(rapidStepTests));
    }

    public static String riskScore(String email) throws Exception{
        logger.info("Received score request for: "+email);
        Optional<Customer> customer = Optional.empty();
        User user = FindUser.getUserByUserName(email);

        if (user!=null) {
            customer = CustomerService.findCustomerByEmail(user.getPhone());
            if (!customer.isPresent()){
                throw new Exception ("Unable to score risk for non-existent customer: "+email);
            }
        } else{
            throw new Exception("Unable to locate user: "+email);
        }

        List<RapidStepTest> rapidStepTestsSortedByDate = rapidStepTestRepository.getArrayAtKey(user.getPhone());
        //Filter here to make sure we have a complete test
        rapidStepTestsSortedByDate = rapidStepTestsSortedByDate.stream().filter(rapidStepTest -> rapidStepTest.getStopTime()!=null && rapidStepTest.getStepPoints().size()==30 && rapidStepTest.getTestTime()!=null).collect(Collectors.toList());
        //List<RapidStepTest> rapidStepTestsSortedByDate = JedisData.getEntitiesByIndex(RapidStepTest.class,"CustomerId", email);
        Collections.sort(rapidStepTestsSortedByDate);
        if (rapidStepTestsSortedByDate.size()>4) {

            RapidStepTest mostRecentTest = rapidStepTestsSortedByDate.get(rapidStepTestsSortedByDate.size() - 1);
            RapidStepTest secondMostRecentTest = rapidStepTestsSortedByDate.get(rapidStepTestsSortedByDate.size() - 2);

            BigDecimal currentTestAverageScore = BigDecimal.valueOf((mostRecentTest.getStopTime() - mostRecentTest.getStartTime()) + (secondMostRecentTest.getStopTime() - secondMostRecentTest.getStartTime())).divide(BigDecimal.valueOf(2l));

            RapidStepTest thirdMostRecentTest = rapidStepTestsSortedByDate.get(rapidStepTestsSortedByDate.size() - 3);
            RapidStepTest fourthMostRecentTest = rapidStepTestsSortedByDate.get(rapidStepTestsSortedByDate.size() - 4);

            BigDecimal previousTestAverageScore = BigDecimal.valueOf((thirdMostRecentTest.getStopTime() - thirdMostRecentTest.getStartTime()) + (fourthMostRecentTest.getStopTime() - fourthMostRecentTest.getStartTime())).divide(BigDecimal.valueOf(2l));

            BigDecimal riskScore = (previousTestAverageScore.subtract(currentTestAverageScore)).divide(new BigDecimal(1000l));
            //positive means they have improved
            //negative means they have declined

            Integer birthYear = Integer.valueOf(customer.get().getBirthDay().split("-")[0]);

            CustomerRisk customerRisk = new CustomerRisk();
            customerRisk.setScore(riskScore.setScale(2, RoundingMode.HALF_UP).toBigInteger().floatValue());
            customerRisk.setCustomer(email);
            customerRisk.setRiskDate(new Date(mostRecentTest.getStopTime()));
            customerRisk.setBirthYear(birthYear);

            JedisData.loadToJedisWithIndex(customerRisk, email, customerRisk.getRiskDate().getTime(), "BirthYear", String.valueOf(birthYear));
            return gson.toJson(customerRisk);
        } else {
            return null;//we're just saving data for single steps
        }

    }



}
