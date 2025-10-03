//Copyright 2021 Sean Murdock

package com.getsimplex.steptimer.service;

import com.getsimplex.steptimer.datarepository.RapidStepTestRepository;
import com.getsimplex.steptimer.model.*;
import com.getsimplex.steptimer.utils.SendText;
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


    public static String getAllTestsByEmail(String email) throws Exception{
        logger.info("Received getAllTests request for email: " + email);
        logger.info("Step 1: Starting step history retrieval for email: " + email);
        
        logger.info("Step 2: Attempting to find user by email: " + email);
        User user = FindUser.getUserByUserName(email);

        if (user != null) {
            logger.info("Step 3: User found successfully. Phone: " + user.getPhone());
            String standardizedPhone = SendText.getFormattedPhone(user.getPhone(), user.getRegion());
            logger.info("Step 4: Retrieving rapid step tests for phone: " + standardizedPhone);
            List<RapidStepTest> rapidStepTests = rapidStepTestRepository.getArrayAtKey(standardizedPhone);
            logger.info("Step 5: Retrieved " + (rapidStepTests != null ? rapidStepTests.size() : 0) + " total rapid step tests");
            return (gson.toJson(rapidStepTests));
        } else {
            logger.severe("Step 3: FAILED - Unable to locate user with email: " + email);
            throw new Exception("Unable to locate user: " + email);
        }
    }

    public static String riskScore(String email) throws Exception{
        logger.info("Received score request for: "+email);
        logger.info("Step 1: Starting risk score calculation for email: " + email);
        
        Optional<Customer> customer = Optional.empty();
        logger.info("Step 2: Attempting to find user by email: " + email);
        User user = FindUser.getUserByUserName(email);

        if (user!=null) {
            logger.info("Step 3: User found successfully. Phone: " + user.getPhone());
            logger.info("Step 4: Attempting to find customer by phone: " + user.getPhone());
            customer = CustomerService.findCustomerByPhone(user.getPhone());
            if (!customer.isPresent()){
                logger.severe("Step 5: FAILED - Unable to find customer for phone: " + user.getPhone());
                throw new Exception ("Unable to score risk for non-existent customer: "+email);
            }
            logger.info("Step 5: Customer found successfully for phone: " + user.getPhone());
        } else{
            logger.severe("Step 3: FAILED - Unable to locate user with email: " + email);
            throw new Exception("Unable to locate user: "+email);
        }

        logger.info("Step 6: Retrieving rapid step tests for phone: " + user.getPhone());
        List<RapidStepTest> rapidStepTestsSortedByDate = rapidStepTestRepository.getArrayAtKey(user.getPhone());
        logger.info("Step 7: Retrieved " + (rapidStepTestsSortedByDate != null ? rapidStepTestsSortedByDate.size() : 0) + " total rapid step tests");
        
        if (rapidStepTestsSortedByDate == null) {
            logger.warning("Step 7.1: Rapid step tests list is null, initializing empty list");
            rapidStepTestsSortedByDate = new ArrayList<>();
        }
        
        //Filter here to make sure we have a complete test
        logger.info("Step 8: Filtering tests to ensure completeness (stopTime!=null, stepPoints.size()==30, testTime!=null)");
        rapidStepTestsSortedByDate = rapidStepTestsSortedByDate.stream().filter(rapidStepTest -> rapidStepTest.getStopTime()!=null && rapidStepTest.getStepPoints().size()==30 && rapidStepTest.getTestTime()!=null).collect(Collectors.toList());
        logger.info("Step 9: After filtering, " + rapidStepTestsSortedByDate.size() + " complete tests remain");
        
        //List<RapidStepTest> rapidStepTestsSortedByDate = JedisData.getEntitiesByIndex(RapidStepTest.class,"CustomerId", email);
        logger.info("Step 10: Sorting tests by date");
        Collections.sort(rapidStepTestsSortedByDate);
        logger.info("Step 11: Tests sorted successfully");
        
        if (rapidStepTestsSortedByDate.size()>4) {
            logger.info("Step 12: Sufficient test data available (" + rapidStepTestsSortedByDate.size() + " tests > 4 required). Proceeding with risk calculation.");

            logger.info("Step 13: Extracting most recent test data");
            RapidStepTest mostRecentTest = rapidStepTestsSortedByDate.get(rapidStepTestsSortedByDate.size() - 1);
            logger.info("Step 13.1: Most recent test - Start: " + mostRecentTest.getStartTime() + ", Stop: " + mostRecentTest.getStopTime());
            
            RapidStepTest secondMostRecentTest = rapidStepTestsSortedByDate.get(rapidStepTestsSortedByDate.size() - 2);
            logger.info("Step 13.2: Second most recent test - Start: " + secondMostRecentTest.getStartTime() + ", Stop: " + secondMostRecentTest.getStopTime());

            logger.info("Step 14: Calculating current test average score");
            BigDecimal currentTestAverageScore = BigDecimal.valueOf((mostRecentTest.getStopTime() - mostRecentTest.getStartTime()) + (secondMostRecentTest.getStopTime() - secondMostRecentTest.getStartTime())).divide(BigDecimal.valueOf(2l));
            logger.info("Step 14.1: Current test average score calculated: " + currentTestAverageScore);

            logger.info("Step 15: Extracting third and fourth most recent test data");
            RapidStepTest thirdMostRecentTest = rapidStepTestsSortedByDate.get(rapidStepTestsSortedByDate.size() - 3);
            logger.info("Step 15.1: Third most recent test - Start: " + thirdMostRecentTest.getStartTime() + ", Stop: " + thirdMostRecentTest.getStopTime());
            
            RapidStepTest fourthMostRecentTest = rapidStepTestsSortedByDate.get(rapidStepTestsSortedByDate.size() - 4);
            logger.info("Step 15.2: Fourth most recent test - Start: " + fourthMostRecentTest.getStartTime() + ", Stop: " + fourthMostRecentTest.getStopTime());

            logger.info("Step 16: Calculating previous test average score");
            BigDecimal previousTestAverageScore = BigDecimal.valueOf((thirdMostRecentTest.getStopTime() - thirdMostRecentTest.getStartTime()) + (fourthMostRecentTest.getStopTime() - fourthMostRecentTest.getStartTime())).divide(BigDecimal.valueOf(2l));
            logger.info("Step 16.1: Previous test average score calculated: " + previousTestAverageScore);

            logger.info("Step 17: Calculating risk score");
            BigDecimal riskScore = (previousTestAverageScore.subtract(currentTestAverageScore)).divide(new BigDecimal(1000l));
            logger.info("Step 17.1: Risk score calculated: " + riskScore + " (positive=improved, negative=declined)");
            //positive means they have improved
            //negative means they have declined

            logger.info("Step 18: Extracting birth year from customer data");
            Integer birthYear = Integer.valueOf(customer.get().getBirthDay().split("-")[0]);
            logger.info("Step 18.1: Birth year extracted: " + birthYear);

            logger.info("Step 19: Creating CustomerRisk object");
            CustomerRisk customerRisk = new CustomerRisk();
            customerRisk.setScore(riskScore.setScale(2, RoundingMode.HALF_UP).toBigInteger().floatValue());
            customerRisk.setCustomer(email);
            customerRisk.setRiskDate(new Date(mostRecentTest.getStopTime()));
            customerRisk.setBirthYear(birthYear);
            logger.info("Step 19.1: CustomerRisk object created - Score: " + customerRisk.getScore() + ", Customer: " + customerRisk.getCustomer() + ", RiskDate: " + customerRisk.getRiskDate() + ", BirthYear: " + customerRisk.getBirthYear());

            logger.info("Step 20: Saving CustomerRisk to Jedis with index");
            JedisData.loadToJedisWithIndex(customerRisk, email, customerRisk.getRiskDate().getTime(), "BirthYear", String.valueOf(birthYear));
            logger.info("Step 20.1: CustomerRisk saved successfully to Jedis");
            
            logger.info("Step 21: Converting CustomerRisk to JSON and returning result");
            String result = gson.toJson(customerRisk);
            logger.info("Step 21.1: JSON result created, length: " + (result != null ? result.length() : 0) + " characters");
            return result;
        } else {
            logger.warning("Step 12: INSUFFICIENT DATA - Only " + rapidStepTestsSortedByDate.size() + " complete tests available (need >4). Returning null and saving data for single steps only.");
            return null;//we're just saving data for single steps
        }

    }



}
