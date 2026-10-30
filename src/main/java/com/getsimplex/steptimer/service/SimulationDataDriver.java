//Copyright 2021 Sean Murdock

package com.getsimplex.steptimer.service;

import com.getsimplex.steptimer.datarepository.RapidStepTestRepository;
import com.getsimplex.steptimer.model.Customer;
import com.getsimplex.steptimer.model.DeviceMessage;
import com.getsimplex.steptimer.model.RapidStepTest;
import com.getsimplex.steptimer.model.StediEvent;
import com.getsimplex.steptimer.model.User;
import com.getsimplex.steptimer.utils.AlreadyExistsException;
import com.getsimplex.steptimer.utils.JedisClient;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.gson.Gson;
import org.eclipse.jetty.websocket.api.Session;

import java.util.*;

public class SimulationDataDriver {

    private static String[] lastNames = {"Jones", "Smith", "Ahmed", "Wu", "Doshi", "Anandh", "Clayton", "Harris", "Gonzalez", "Abram", "Khatib", "Clark", "Mitra", "Habschied", "Jackson", "Phillips", "Lincoln", "Spencer", "Anderson", "Hansen", "Davis", "Jones", "Fibonnaci", "Staples", "Jefferson", "Huey", "Olson", "Howard", "Sanchez", "Aristotle"};
    private static String[] firstNames = {"Sarah", "Bobby", "Frank", "Edward", "Danny", "Chris", "Spencer", "Ashley", "Santosh", "Senthil", "Christina", "Suresh", "Neeraj", "Angie", "Sean", "Lyn", "John", "Ben", "Travis", "David", "Larry", "Jerry", "Gail", "Craig", "Dan", "Jason", "Eric", "Trevor", "Jane", "Jacob", "Jaya", "Manoj", "Liz", "Christina"};
    private static List<Customer> testCustomers = new ArrayList<Customer>();
    private static Random random = new Random();
    private static Gson gson = new Gson();
    private static Session remoteSession;
    private static boolean simulationActive = false;
    private static Map<String, Long> mostRecentTestTime = new HashMap<String, Long>();
    private static boolean solutionActive = false;
    private static RapidStepTestRepository rapidStepTestRepository = new RapidStepTestRepository();
    private static final String[] regions = {"US", "MX", "BR", "GB", "FR", "DE", "ES", "IN", "JP", "KR", "PH", "NG", "KE", "ZA", "EG"};//each has a unique dial code in the country-codes dataset
    private static final String SIMULATED_USER_PASSWORD = "Stedi-Sim1!";
    private static final String SIMULATED_DEVICE_ID = "1234";//this is just a device id used for testing

    static {
        solutionActive = Boolean.valueOf(System.getProperty("solutionActive"));//this makes it like the Spark app is sending messages (only for teacher use)
    }

    public static synchronized void setSimulationActive(boolean active){
        simulationActive= active;
    }

    public static synchronized  boolean getSimulationActive(){
        return simulationActive;
    }

    public static synchronized void setRemoteSession(Session session){
        remoteSession=session;
    }

    public static synchronized void generateTestCustomers(int numberOfUsers) {
        testCustomers.clear();
        int nextCustomerAge = 55;
        for (int i = 0; i < numberOfUsers - 1; i++) {
            try {
                String region = regions[i % regions.length];
                String firstName = firstNames[i % firstNames.length];
                String lastName = lastNames[(i * 7) % lastNames.length];
                String email = firstName + "." + lastName + "." + i + "@test.com";
                String birthDay = (2020 - nextCustomerAge++) + "-01-01";//spread age out evenly
                String phone = simulatedPhone(region, i);//unique per customer: the phone number is the customer's key

                Customer customer = new Customer();
                customer.setCustomerName(firstName + " " + lastName);
                customer.setEmail(email);
                customer.setPhone(phone);
                customer.setWhatsAppPhone(phone);
                customer.setRegion(region);
                customer.setBirthDay(birthDay);
                try {
                    CustomerService.createOrUpdateCustomer(customer, false);//formats the phone, ex: +442079460000
                } catch (AlreadyExistsException e) {
                    //created by an earlier simulation run; reuse it
                }

                User user = new User();
                user.setUserName(email);
                user.setEmail(email);
                user.setPassword(SIMULATED_USER_PASSWORD);
                user.setVerifyPassword(SIMULATED_USER_PASSWORD);
                user.setPhone(phone);
                user.setWhatsAppPhone(phone);
                user.setRegion(region);
                user.setBirthDate(birthDay);
                user.setDeviceNickName(SIMULATED_DEVICE_ID);
                try {
                    CreateNewUser.createUser(user);//risk scores are looked up by user name
                } catch (AlreadyExistsException e) {
                    //created by an earlier simulation run; reuse it
                }

                testCustomers.add(customer);
                saveSignupEvent(customer);
                if (previousTestCount(customer) < 4) {
                    for (int test = 0; test < 4; test++) {//a risk score needs more than 4 tests, so seed history and score from the first round
                        saveRapidStepTest(customer, (120 - random.nextInt(60)) * 1000L, System.currentTimeMillis() - (4 - test) * 60000L);
                    }
                }
            } catch (Exception e) {
                System.out.println(e.getMessage());
            }
        }

    }

    // Builds a valid-looking phone number for the region from libphonenumber's example number, made unique by index
    private static String simulatedPhone(String region, int index) {
        PhoneNumberUtil phoneUtil = PhoneNumberUtil.getInstance();
        String nationalNumber = String.valueOf(phoneUtil.getExampleNumberForType(region, PhoneNumberUtil.PhoneNumberType.MOBILE).getNationalNumber());
        String suffix = String.format("%03d", index % 1000);
        return "+" + phoneUtil.getCountryCodeForRegion(region) + nationalNumber.substring(0, nationalNumber.length() - 3) + suffix;
    }

    private static int previousTestCount(Customer customer) {
        try {
            return rapidStepTestRepository.getArrayAtKey(customer.getPhone()).size();
        } catch (Exception e) {
            return 0;//no tests are stored for a new customer yet
        }
    }

    // Writes a signup event to Redis at signup:<email> for the CSAI 380 ETL exercise to extract
    private static void saveSignupEvent(Customer customer) throws Exception {
        Map<String, Object> signup = new LinkedHashMap<String, Object>();
        signup.put("user_id", customer.getEmail());
        signup.put("customer_name", customer.getCustomerName());
        signup.put("email", customer.getEmail());
        signup.put("phone", customer.getPhone());
        signup.put("region", customer.getRegion());
        signup.put("country_dial_code", String.valueOf(PhoneNumberUtil.getInstance().getCountryCodeForRegion(customer.getRegion())));
        signup.put("birth_year", Integer.valueOf(customer.getBirthDay().split("-")[0]));
        signup.put("signup_time", System.currentTimeMillis());
        JedisClient.set("signup:" + customer.getEmail(), gson.toJson(signup));
    }

    // Saves a complete 30-step test where the risk score reads it: the RapidStepTests array keyed by the customer's phone
    private static void saveRapidStepTest(Customer customer, long testTime, long stopTime) {
        RapidStepTest rapidStepTest = new RapidStepTest();
        rapidStepTest.setCustomer(customer.getEmail());
        rapidStepTest.setDeviceId(SIMULATED_DEVICE_ID);
        rapidStepTest.setStopTime(stopTime);
        rapidStepTest.setStartTime(stopTime - testTime);
        rapidStepTest.setTestTime(testTime);
        rapidStepTest.setTotalSteps(30);
        ArrayList<Long> stepPoints = new ArrayList<Long>();
        for (int step = 1; step <= 30; step++) {
            stepPoints.add(testTime * step / 30);
        }
        rapidStepTest.setStepPoints(stepPoints);
        rapidStepTestRepository.addToArrayAtKey(customer.getPhone(), rapidStepTest);
    }

    public static void createRapidStepTests() {
        for (Customer testCustomer:testCustomers){
             try {
                 long randomChange;
                 long testTime;
                 Integer birthYear = Integer.valueOf(testCustomer.getBirthDay().split("-")[0]);
                 Optional<Long> previousTestTime= Optional.empty();
                 if (mostRecentTestTime.containsKey(testCustomer.getCustomerName())){
                     previousTestTime=Optional.of(mostRecentTestTime.get(testCustomer.getCustomerName()));
                 }

                 if (!previousTestTime.isPresent()){//create their first score
                     randomChange = random.nextInt(60);//negative offset (in seconds) from 2 minute test time
                     testTime = (120 - randomChange) * 1000;//test time (in milliseconds)
                 } else{
                    if ( birthYear>=1950 && birthYear <=1960 ){
                        randomChange = random.nextInt(4);//simulate deteriorating change of 1-4 seconds
                        testTime = previousTestTime.get() + (randomChange*1000);
                    } else {
                        randomChange = random.nextInt(60);//random variation (in seconds) from arbitrary 2 minutes (test time)
                        testTime = (120 - randomChange) * 1000;//test time (in milliseconds)
                    }
                 }

                 mostRecentTestTime.put(testCustomer.getCustomerName(), testTime);

                 Thread.sleep(2000);//2 seconds sleep time between each message makes a new message every minute for every customer assuming 30 test customers
                 saveRapidStepTest(testCustomer, testTime, System.currentTimeMillis());
                 String riskScoreJson = StepHistory.riskScore(testCustomer.getEmail());//this is logged, and we don't actually need it right here, we just want it to be visible for logging purposes
                 if (riskScoreJson == null) {
                     continue;//not enough complete tests to score yet
                 }

                 StediEvent event = new StediEvent();
                 event.setMessage(riskScoreJson);

                 MessageIntake.route(event);//this is where we will pick up the CustomerRisk events from outside of STEDI (like in Spark for example)

                 if (solutionActive) {//this is directly simulating the messages that will be coming from Kafka (WITH the birth year) when solved
                     try {
                         DeviceMessage deviceMessage = new DeviceMessage();
                         deviceMessage.setDate(System.currentTimeMillis());
                         deviceMessage.setDeviceId("1234");//this is just a device id used for testing
                         deviceMessage.setMessage(riskScoreJson);
                         MessageIntake.route(deviceMessage);

                     } catch (Exception e) {
                         System.out.println("Error retrieving risk score for customer: " + e.getMessage());
                     }
                 }
             } catch (Exception e){
                 System.out.println(e.getMessage());
             }
        }
    }

}
