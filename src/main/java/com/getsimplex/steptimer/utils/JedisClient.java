//Copyright 2021 Sean Murdock

package com.getsimplex.steptimer.utils;


import com.google.gson.Gson;
import com.typesafe.config.Config;
import org.json.JSONArray;
import redis.clients.jedis.*;
import redis.clients.jedis.json.Path2;
import redis.clients.jedis.providers.PooledConnectionProvider;

import java.util.*;

/**
 * Created by Admin on 8/18/2016.
 */
public class JedisClient {

    private static Config config;
    private static String password;
    private static String host;
    private static String port;
    private static String dbName;
    private static String url;
    private static JedisPool jedisPool;

    private static Gson gson = new Gson();

    public static UnifiedJedis unifiedJedis;

    static {
        config = Configuration.getConfiguration();

        if (System.getenv("REDIS_HOST")!=null && !System.getenv("REDIS_HOST").isEmpty()){

            password = System.getenv("REDIS_PASSWORD");
            host = System.getenv("REDIS_HOST");
            port = System.getenv("REDIS_PORT");
            dbName = System.getenv("REDIS_DB");

            if (password !=null && !password.isEmpty()){
                url = "redis://:"+password+"@"+host+":"+port+"/"+dbName;
                jedisPool  = new JedisPool(url);
            }
            else{



                jedisPool = new JedisPool(host, Integer.valueOf(port));
            }



        } else{
            config = Configuration.getConfiguration();
            try {
                password = config.getString("redis.password");
            } catch (Exception e){
                //config object throws exception for non-existent keys, workaround is to catch the exception
            }
            host = config.getString("redis.host");
            port = config.getString("redis.port");
            dbName = config.getString("redis.db");
            if (password !=null && !password.isEmpty()){
                url = "redis://:"+password+"@"+host+":"+port+"/"+dbName;
                jedisPool  = new JedisPool(url);
            } else{
                jedisPool = new JedisPool(host, Integer.valueOf(port));
            }

        }
        PooledConnectionProvider provider;
        HostAndPort hostAndPort = new HostAndPort(host, Integer.valueOf(port));

        if(password!=null && !password.isEmpty()){
            JedisClientConfig defaultJedisClientConfig = DefaultJedisClientConfig.builder()
                    .password(password)
                    .database(Integer.valueOf(dbName))
                    .build();
            provider = new PooledConnectionProvider(hostAndPort, defaultJedisClientConfig);
            unifiedJedis = new UnifiedJedis(provider);
        } else if (host!=null && !host.isEmpty() && port!=null && !port.isEmpty()){
            provider = new PooledConnectionProvider(hostAndPort);
            unifiedJedis = new UnifiedJedis(provider);
        }


    }

//    private static synchronized Jedis getJedis(){
//        Jedis jedis = jedisPool.getResource();
//        try{
//            jedis.ping();
//        }
//
//        catch (Exception e){
//            jedis = new Jedis(url);
//        }
//        return jedis;
//    }

    public static synchronized void set(String key, String value) throws Exception{
        Jedis jedis = jedisPool.getResource();
        try{
            jedis.set(key,value);
            jedisPool.returnResource(jedis);
        } catch(Exception e){
            jedisPool.returnBrokenResource(jedis);
            throw new Exception ("Tried setting key:"+key+ " and value:"+value+" without success");
        }
    }

    // Sets the key only if it does not exist, expiring after the given seconds; true if it was set (ex: a lock across pods)
    public static synchronized boolean setIfAbsent(String key, String value, long expireSeconds) throws Exception{
        Jedis jedis = jedisPool.getResource();
        try{
            String result = jedis.set(key, value, redis.clients.jedis.params.SetParams.setParams().nx().ex(expireSeconds));
            jedisPool.returnResource(jedis);
            return "OK".equals(result);
        } catch(Exception e){
            jedisPool.returnBrokenResource(jedis);
            throw new Exception ("Tried setting key:"+key+" if absent without success");
        }
    }

    public static synchronized void delete(String key) throws Exception{
        Jedis jedis = jedisPool.getResource();
        try{
            jedis.del(key);
            jedisPool.returnResource(jedis);
        } catch(Exception e){
            jedisPool.returnBrokenResource(jedis);
            throw new Exception ("Tried deleting key:"+key+" without success");
        }
    }
//
//    public static synchronized Boolean exists(String key) throws Exception{
//        int tries =0;
//        try{
//            tries++;
//            return jedis.exists(key);
//        }
//
//        catch (Exception e ){
//            if (tries<1000)
//            {
//                getJedis();
//                return exists(key);
//            }
//
//            else {
//                throw new Exception ("Tried 1000 times exists on key:"+key+" without success");
//            }
//        }
//    }

    public static synchronized List<String> zrange(String key, long start, long end) throws Exception{
        Jedis jedis = jedisPool.getResource();
        try {

            List<String> results = jedis.zrange(key,start,end);
            jedisPool.returnResource(jedis);
            return results;
        }

        catch (Exception e){
            jedisPool.returnBrokenResource(jedis);

            throw new Exception("Tried to get range:"+key+" start:"+start+" end:"+end+" without success");
        }
    }

    public static synchronized List<String> zrangeByScore (String key, long start, long end) throws Exception{
        Jedis jedis = jedisPool.getResource();
        try {
            List<String> results = jedis.zrangeByScore(key,start,end);
            jedisPool.returnResource(jedis);
            return results;
        }

        catch (Exception e){
            System.out.println("error selecting data " + e.getMessage());
            jedisPool.returnBrokenResource(jedis);

            throw new Exception("Tried  to get range:"+key+" start:"+start+" end:"+end+" without success");

        }
    }

    public static synchronized void jsonArrayAdd(String key, String path, Object value) {
        Object rootObject = unifiedJedis.jsonGet(key,Path2.of("$"));
        if(rootObject==null){//the root object doesn't exist yet
            if(path.equals("$")){//for this redis key, we are making a top level array- -- so this is how we initialize an empty JSON array before inserting into it
                unifiedJedis.jsonSet(key,Path2.of("$"),(Object)(new ArrayList<>()));
            } else{//for this redis key, we are making a nested array -- so we need to initialize an empty object
                unifiedJedis.jsonSet(key,Path2.of("$"),"{}");//create a top-level object we can put fields into
                unifiedJedis.jsonSet(key,Path2.of(path),new ArrayList<>());
            }
        }

        Object nestedObject = unifiedJedis.jsonGet(key,Path2.of(path));
        if (nestedObject!=null && nestedObject.getClass().getName().equals("org.json.JSONArray")){
            JSONArray nestedArray = (JSONArray) nestedObject;
            if(nestedArray.length()==0){//if Redis  returns an empty array to show there are no items available at this location, otherwise the array has an array in it (length = 1)
                unifiedJedis.jsonSet(key,Path2.of(path),new ArrayList<>());
            }
        }
        else if (nestedObject==null ){//the nested array doesn't exist, so we need to create it before we can append to it
            unifiedJedis.jsonSet(key,Path2.of(path),new ArrayList<>());
        }


         /*
         key is the name of the Redis key we are updating
         Path2 is the JSON path where we need to put the value within the Redis key

            Below we would want to use path $.+18017190908 or $.+12085551212 to append to the nested array

             {
              "+18017190908":[
                 {
                 "startTime":46565654,
                 "endTime": 47565654,
                 "score":15
                 }
               ],
               "+12085551212": [
                 {
                 "startTime":56565654,
                 "endTime": 57565654,
                 "score":10
                 }
               ]

            BUT if the JSON object looks like this we would use path $ (or Path2.ROOT_PATH) to append an object:

            [
                 {
                 "startTime":46565654,
                 "endTime": 47565654,
                 "score":15
                 },
                 {
                 "startTime":56565654,
                 "endTime": 57565654,
                 "score":10
                 }
            ]

          */

        List<Long> result = unifiedJedis.jsonArrAppend(key, Path2.of(path),gson.toJson(value));
        System.out.println("Result "+result);

    }

    public static synchronized Object jsonGet(String key) throws Exception{
        Object object = unifiedJedis.jsonGet(key);
        return object;
    }

    public static synchronized JSONArray jsonGetArray(String key, String path){
        JSONArray arrayOfArrays =  (JSONArray) unifiedJedis.jsonGet(key,Path2.of(path));
        JSONArray jsonArray = new JSONArray();
        if(arrayOfArrays.length()>0){
            jsonArray =(JSONArray) arrayOfArrays.get(0);// the first jsonArray
        }
        return jsonArray;
    }

    public static synchronized void zadd(String key, long score, String value) throws Exception{
        Jedis jedis = jedisPool.getResource();
        try {
            jedis.zadd(key,score,value);
            jedisPool.returnResource(jedis);
        }

        catch (Exception e){
            jedisPool.returnBrokenResource(jedis);
            throw new Exception("Tried to persist :"+value+" without success");

        }
    }

    public static synchronized long zrem(String key, String value) throws Exception{

        Jedis jedis = jedisPool.getResource();
        try {
            long result = jedis.zrem(key,value);
            jedisPool.returnResource(jedis);
            return result;
        }

        catch (Exception e){
            jedisPool.returnBrokenResource(jedis);

            throw new Exception("Tried to remove key: "+key+" value: "+value+" without success");

        }
    }

    public static synchronized void zremrangeByScore(String key, double start, double end) throws Exception{

        Jedis jedis = jedisPool.getResource();
        try {
            jedis.zremrangeByScore(key,start,end);
            jedisPool.returnResource(jedis);
        }

        catch (Exception e){
            jedisPool.returnBrokenResource(jedis);

            throw new Exception("Tried to remove :"+key+" without success");

        }
    }

    public static synchronized Long zcount(String keyName, double min, double max) throws Exception{

        Jedis jedis = jedisPool.getResource();
        try {

            Long result = jedis.zcount(keyName, min, max);
            jedisPool.returnResource(jedis);
            return result;
        }

        catch (Exception e){
            jedisPool.returnBrokenResource(jedis);

            throw new Exception("Tried to zcount :"+keyName+" without success");

        }

    }

    public static synchronized String get(String key) throws Exception{
        Jedis jedis = jedisPool.getResource();
        try{
            String result = jedis.get(key);
            jedisPool.returnResource(jedis);
            return result;
        }
        catch (Exception e){
            jedisPool.returnBrokenResource(jedis);

            throw new Exception("Tried to get key:"+key+" without success");


        }
    }

    public static synchronized void hmset (String mapName, String key, String json) throws Exception{
        Jedis jedis = jedisPool.getResource();
        try{
            jedis.hmset(mapName, Map.of(key, json));
            jedisPool.returnResource(jedis);

        } catch (Exception e){
            jedisPool.returnBrokenResource(jedis);
            throw new Exception("Tried setting: "+mapName+" key : "+key+" without success");
        }
    }

    public static synchronized void hdel (String mapName, String key) throws Exception{
        Jedis jedis = jedisPool.getResource();
        try{
            jedis.hdel(mapName, key);
            jedisPool.returnResource(jedis);

        } catch (Exception e){
            jedisPool.returnBrokenResource(jedis);
            throw new Exception("Tried setting: "+mapName+" key : "+key+" without success");
        }
    }

    public static synchronized Optional<String> hmget (String mapName, String key) throws Exception {
        Jedis jedis = jedisPool.getResource();
        try {
            List<String> valueList = jedis.hmget(mapName, key);
            jedisPool.returnResource(jedis);
            Optional<String> valueOptional = Optional.empty();
            if (valueList.size() == 1 && valueList.get(0) != null) {
                valueOptional = Optional.of(valueList.get(0));
            } else if (valueList.size() > 1) {
                throw new Exception("Map: " + mapName + " and Key: " + key + " returned " + valueList.size() + " values: should only return one or zero.");
            }
            return valueOptional;
        } catch (Exception e) {
            jedisPool.returnBrokenResource(jedis);
            throw new Exception("Tried getting: " + mapName + " key: " + key + " without success");
        }
    }

}
