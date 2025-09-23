package com.getsimplex.steptimer.utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Copyright 2021 Sean Murdock, Created by Sean on 9/1/2015.

 *  The basic idea is we want to be able to retrieve ALL of something (ex: all steps for all customers)
 *  AND we want to be able to retrieve something specific (ex: customer with email: sam@gmail.com
 *  AND we want to be able to retrieve one ore more things by a specific index (ex: customers who are female)

 * The solution to that is whenever you store something you do 2 things:
 * (1) You put the thing in a map by its primary key (ex: email address)
 * (2) You put the primary key in a sorted set with all the other things like it, including a score that would be convenient to sort by (ex: step test time)
 *
 * AND optionally
 * (3) You put the primary key in another sorted set named by a certain index, with the score as the index you wish to search later

 */
public class JedisData {

    private static Gson gson = new GsonBuilder().setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").create();

    public static synchronized <T> ArrayList<T> getEntityList(Class clazz) throws Exception{
        return getEntities(clazz);
    }

    public static synchronized <T> Optional<T> getEntityById(Class clazz, String key) throws Exception{
        Optional<String> mapValueOptional = JedisClient.hmget(clazz.getSimpleName()+"Map", key);
        Optional<T> optionalValue = Optional.empty();

        if (mapValueOptional.isPresent()){
            optionalValue = Optional.of((T) gson.fromJson(mapValueOptional.get(), clazz));
        }

        return optionalValue;
    }



    public static synchronized <T> ArrayList<T> getEntitiesByScore(Class clazz, long beginScore, long endScore) throws Exception{
        List<String> set = JedisClient.zrangeByScore(clazz.getSimpleName(), beginScore, endScore);
        ArrayList<T> arrayList = new ArrayList<T>();

        // loop through all the keys from the sorted set and for each key get the value from the redis map
        for (String key:set){
            Optional<String> mapValueOptional = JedisClient.hmget(clazz.getSimpleName()+"Map", key);
            if (mapValueOptional.isEmpty()){
                throw new Exception("Map "+clazz.getSimpleName()+" and Key: "+key+" is empty: should contain a JSON object.");
            } else{
                arrayList.add((T) gson.fromJson(mapValueOptional.get(), clazz));
            }
        }

        return arrayList;
    }

    public static synchronized <T> ArrayList<T> getEntities(Class clazz) throws Exception{
        List<String> set = JedisClient.zrange(clazz.getSimpleName(), 0, -1);
        ArrayList<T> arrayList = new ArrayList<T>();

        // loop through all the keys from the sorted set and for each key get the value from the redis map
        for (String key:set){
            Optional<String> mapValueOptional = JedisClient.hmget(clazz.getSimpleName()+"Map", key);
            if (mapValueOptional.isEmpty()){
                throw new Exception("Map "+clazz.getSimpleName()+" and Key: "+key+" is empty: should contain a JSON object.");
            } else{
                arrayList.add((T) gson.fromJson(mapValueOptional.get(), clazz));
            }
        }

        return arrayList;
    }

    public static <T> ArrayList<T> getEntitiesByIndex(Class clazz, String indexName, String index) throws Exception{
        List<String> set = JedisClient.zrangeByScore(clazz.getSimpleName()+"By"+indexName+"-"+index, 0, Long.MAX_VALUE);
        ArrayList<T> arrayList = new ArrayList<T>();

        // loop through all the keys from the sorted set and for each key get the value from the redis map
        for (String key:set){
            Optional<String> mapValueOptional = JedisClient.hmget(clazz.getSimpleName()+"Map", key);
            if (mapValueOptional.isEmpty()){
                throw new Exception("Map "+clazz.getSimpleName()+" and Key: "+key+" is empty: should contain a JSON object.");
            } else{
                arrayList.add((T) gson.fromJson(mapValueOptional.get(), clazz));
            }
        }

        return arrayList;
    }

    public static <T> ArrayList<T> getEntitiesByIndexAndScore(Class clazz, String indexName, String index, long beginScore, long endScore) throws Exception{
        List<String> set = JedisClient.zrangeByScore(clazz.getSimpleName()+"By"+indexName+"-"+index, beginScore, endScore);
        ArrayList<T> arrayList = new ArrayList<T>();

        for (String key : set) {
            Optional<String> mapValueOptional = JedisClient.hmget(clazz.getSimpleName()+"Map", key);
            if (mapValueOptional.isEmpty()){
                throw new Exception("Map "+clazz.getSimpleName()+" and Key: "+key+" is empty: should contain a JSON object.");
            } else{
                arrayList.add((T) gson.fromJson(mapValueOptional.get(), clazz));
            }
        }

        return arrayList;
    }


    public static synchronized <T> void update(T object, String key) throws Exception{
        JedisClient.hmset(object.getClass().getSimpleName()+"Map", key, gson.toJson(object));
    }

    public static <T> void updateRedisMap(T record, String id) throws Exception{
        String jsonFormatted = gson.toJson(record, record.getClass());
        JedisClient.hmset(record.getClass().getSimpleName()+"Map", id, jsonFormatted);
    }

    public static <T> T getFromRedisMap(String id, Class clazz) throws Exception{
        String jsonFormatted = JedisClient.hmget(clazz.getSimpleName()+"Map",id).get();
        T object = (T) gson.fromJson(jsonFormatted, clazz);
        return object;
    }


    public static <T> void loadToJedis(T record, String id) throws Exception{

        try {
            loadToJedis(record, id, 0);
        } catch (Exception e) {

            throw (e);
        }

    }

    public static <T> void loadToJedis(T record, String id, long score) throws Exception{

        try {
            String jsonFormatted = gson.toJson(record,record.getClass());
            JedisClient.hmset(record.getClass().getSimpleName()+"Map", id, jsonFormatted);
            JedisClient.zadd(record.getClass().getSimpleName(), score, id);
        } catch (Exception e) {

            throw (e);
        }

    }

    public static void deleteJedis(Class clazz, String id) throws Exception{
        try {
            JedisClient.hdel(clazz.getSimpleName()+"Map", id);
            JedisClient.zrem(clazz.getSimpleName(), id);
        } catch (Exception e) {

            throw (e);
        }
    }

    public static void deleteJedisByScore(Class clazz, String id, long score) throws Exception{
        try {
            JedisClient.hdel(clazz.getSimpleName()+"Map", id);
            JedisClient.zremrangeByScore(clazz.getSimpleName(), score, score);
        } catch (Exception e) {

            throw (e);
        }
    }

    public static <T> void loadToJedisWithIndex(T record, String id, long score, String indexName, String index) throws Exception{

        try {
            String jsonFormatted = gson.toJson(record,record.getClass());
            JedisClient.hmset(record.getClass().getSimpleName()+"Map", id, jsonFormatted);
            JedisClient.zadd(record.getClass().getSimpleName(), score, id);
            JedisClient.zadd(record.getClass().getSimpleName()+"By"+indexName+"-"+index, 0,id );//ex: CustomerByEmailsam@test.com
        } catch (Exception e) {

            throw (e);
        }

    }

    public static <T> void loadToJedisWithIndexUsingScore(T record, String id, long score, String indexName, String index) throws Exception{
        try {
            String jsonFormatted = gson.toJson(record,record.getClass());
            String className = record.getClass().getSimpleName();
            JedisClient.hmset(className+"Map", id, jsonFormatted);
            JedisClient.zadd(className, score, id);
            JedisClient.zadd(className+"By"+indexName+"-"+index, score, id);
        } catch (Exception e) {
            throw (e);
        }
    }

    public static <T> Long deleteFromRedis(List<T> list) throws Exception{
        Long deleteCount = 0l;
        int i = 0;
//        public List<CorporateDivision> getCorporateDivisionByID(Integer divisionID){
//            ArrayList<CorporateDivision> filteredDivisions = new ArrayList<>();
        for (T lists: list){
            if(deleteCount<list.size()){
//            if(list.size()>0){
                deleteFromRedis(list.get(i));
                deleteCount++;
                i++;
            }
        }

        return deleteCount;
    }

    public static <T> Long deleteFromRedis (T record) throws Exception{
        String jsonFormatted = gson.toJson(record, record.getClass());
        Long removeCount = JedisClient.zrem(record.getClass().getSimpleName(),jsonFormatted);
        if (removeCount!=1){
            throw new Exception("Attempt to remove the following json from redis failed: "+jsonFormatted);
        }
        return removeCount;

    }



}
