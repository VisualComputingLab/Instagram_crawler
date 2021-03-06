package gr.iti.vcl.instagramcrawl.impl;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.MessageProperties;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.URL;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

/*
 *
 * @author  Samaras Dimitris 
 * June 13th, 2014
 * dimitris.samaras@iti.gr
 * 
 */
public class InstagramCrawl {

    // LOOK OUT FOR "&" AMONG PREFIXES
    private static final String API_SITE = "https://api.instagram.com/v1";
    private static final String PREFIX_TAG = "/tags";
    // https://api.instagram.com/v1/tags/snow/media/recent?access_token=1368132404.f59def8.21ffc415777940d7b5d0123beb4bbacd
    private static final String PREFIX_MEDIA = "/media/recent";
    private static final String PREFIX_NEXT = "max_tag_id=";
    private static final String PREFIX_NEXT_LOC = "max_id=";
    // https://api.instagram.com/v1/tags/search?q=snowy&access_token=1368132404.f59def8.21ffc415777940d7b5d0123beb4bbacd
    private static final String PREFIX_SEARCH = "/search";
    private static final String PREFIX_Q = "q=";
    // DO NOT FORGET THE " ? " AFTER SEARCH!!!
    private static final String API_ACCESS_TOKEN = "access_token=";
    private static final String API_CLIENT_ID = "client_id=";
    // https://api.instagram.com/v1/locations/search?lat=48.858844&lng=2.294351&access_token=1368132404.f59def8.21ffc415777940d7b5d0123beb4bbacd
    private static final String API_LOC_SITE = "https://api.instagram.com/v1/locations";
    private static final String PREFIX_LNG = "lng=";
    private static final String PREFIX_LAT = "lat=";
    //https://api.instagram.com/v1/locations/514276/media/recent?access_token=1368132404.f59def8.21ffc415777940d7b5d0123beb4bbacd
    public static String OP_COMMAND_TAG = "tags";
    public static String OP_COMMAND_LOCATION = "location";
    public Connection connection = null;
    public Channel channel = null;

    public InstagramCrawl() {
    }

    @SuppressWarnings("empty-statement")
    public JSONObject parseOut(JSONObject jsonObject) throws Exception, IOException {

        // Create the JSONObject to construct the response that will be saved to RabbitMQ
        JSONObject resultObject = new JSONObject();
        //JSONArray for acitivies
        String operation;
        String topic;
        double lat, lng;
        String apiKey_val;
        String host;
        String qName;

        try {

            operation = jsonObject.getString("search_by");

            apiKey_val = jsonObject.getJSONObject("instagram").getString("apiKey");
            host = jsonObject.getJSONObject("rabbit").getString("host");
            qName = jsonObject.getJSONObject("rabbit").getString("queue");

            int stopper = jsonObject.getJSONObject("instagram").optInt("max_results", 0);
            if (stopper <= 0) {
                err("Max result has to be between 1 and 1000000, do not use 'max_results' to get all results,");
                resultObject.put("Status", "Error");
                resultObject.put("Message", "Max results problem");
                return resultObject;
            } else if (stopper == 0) {
                stopper = Integer.MAX_VALUE;
            }
            //connect to RMQ
            openRMQ(host, qName);

            if (operation.equals(OP_COMMAND_TAG)) {

                topic = jsonObject.getJSONObject("instagram").getString("tag").replaceAll(" ", "_");
                if (topic == null || topic.isEmpty()) {
                    err("No topic given to explore, aborting");
                    resultObject.put("Status", "Error");
                    resultObject.put("Message", "No topic given");
                    return resultObject;
                }

                URL tagsUrl = new URL(API_SITE + PREFIX_TAG + PREFIX_SEARCH + "?" + PREFIX_Q + topic + "&" + API_CLIENT_ID + apiKey_val);
                String tags = callGET(tagsUrl);
                JSONObject tagsObj = new JSONObject(tags);
                JSONArray tagsObjData = tagsObj.getJSONArray("data");
                String tag0 = new JSONObject(tagsObjData.getString(0)).getString("name");
                if (tag0.equals(topic)) {
                    //THERE IS AN EXACT MATCH
                    int mediacount = 0;
                    parseTagMedia(tag0, apiKey_val, stopper, mediacount, qName);

                } else {
                    //THERE IS NOT AN EXACT MATCH   
                    int mediacount = 0;
                    for (int i = 0; i < tagsObjData.length(); i++) {
                        //We may get less that 5 tag results...
                        //Consider getting more results i+.... OR consider going only through this loop
                        if (i > 4) {
                            break;
                        }
                        String tag = new JSONObject(tagsObjData.getString(i)).getString("name");
                        int count = parseTagMedia(tag, apiKey_val, stopper, mediacount, qName);
                        mediacount = count;

                        if (mediacount >= stopper) {
                            break;
                        }
                    }
                }
            } else if (operation.equals(OP_COMMAND_LOCATION)) {
                lat = jsonObject.getJSONObject("instagram").getDouble("lat");
                lng = jsonObject.getJSONObject("instagram").getDouble("lng");

                URL tagsUrl = new URL(API_LOC_SITE + PREFIX_SEARCH + "?" + PREFIX_LAT + lat + "&" + PREFIX_LNG + lng + "&" + API_CLIENT_ID + apiKey_val);
                String tags = callGET(tagsUrl);
                log("Locations: " + tags);
                JSONObject tagsObj = new JSONObject(tags);
                JSONArray tagsObjData = tagsObj.getJSONArray("data");
                int mediacount = 0;
                for (int i = 0; i < tagsObjData.length(); i++) {
                    String locid = new JSONObject(tagsObjData.getString(i)).getString("id");
                    int count = parseLocationMedia(locid, apiKey_val, stopper, mediacount, qName);
                    mediacount = count;
                    if (mediacount >= stopper) {
                        break;
                    }
                }
            }
            closeRMQ();

        } catch (JSONException e) {
            err("JSONException parsing initial response: " + e);
        }
        resultObject.put("Status", "200");
        resultObject.put("Message", "OK");
        return resultObject;
    }

    public int parseTagMedia(String tag, String apiKey_val, int stopper, int mediacount, String qName) throws Exception {

        String pageToken = "";
        try {
            do {
                URL tagMediaUrl = new URL(API_SITE + PREFIX_TAG + "/" + tag + PREFIX_MEDIA + "?" + API_CLIENT_ID + apiKey_val + "&" + PREFIX_NEXT + pageToken);
                String tagMedia = callGET(tagMediaUrl);
                JSONObject tagMediaobj = new JSONObject(tagMedia);

                pageToken = tagMediaobj.getJSONObject("pagination").optString("next_max_tag_id", "noMore");

                JSONArray tagMediaObjData = tagMediaobj.getJSONArray("data");
                log("media response : " + tagMediaObjData.toString());

                for (int i = 0; i < tagMediaObjData.length(); i++) {
                    JSONObject mediaObject = new JSONObject();
                    JSONObject item = new JSONObject(tagMediaObjData.getString(i));
                    //log(item.toString());
                    if (item.isNull("location")) {
                        mediaObject.put("location", "No location coordinates");
                    } else {
                        mediaObject.put("location", item.getJSONObject("location"));
                    }
                    //From the comments only the comment "text" is needed 
                    JSONArray commentsData = item.getJSONObject("comments").getJSONArray("data");
                    JSONArray comments = new JSONArray();
                    for (int z = 0; z < commentsData.length(); z++) {
                        String com = new JSONObject(commentsData.getString(z)).getString("text");
                        comments.put(com);
                    }
                    mediaObject.put("comments", comments);
                    mediaObject.put("filter", item.getString("filter"));
                    mediaObject.put("created_time", item.getString("created_time"));
                    //convert it to real time?
                    mediaObject.put("likes", item.getJSONObject("likes").getInt("count"));
                    mediaObject.put("image", item.getJSONObject("images").getJSONObject("standard_resolution"));
                    mediaObject.put("users_in_photo", item.getJSONArray("users_in_photo"));
                    if (item.isNull("caption")) {
                        mediaObject.put("caption", "No caption");
                    } else {
                        mediaObject.put("caption", item.getJSONObject("caption").optString("text", "no caption"));
                    }
                    mediaObject.put("id", item.getString("id"));
                    mediaObject.put("user", item.getJSONObject("user"));

                    log(mediaObject.toString());
                    mediacount++;
                    //WRITE to RABBITMQ : 
                    writeToRMQ(mediaObject, qName);

                }
//                5000 reqs/hour minus the initial tags search get... 
//                leaves 1,38 reqs/sec (Thread.sleep(724)) 
//                processing time of the object leaves takes some time...        
                try {
                    Thread.sleep(700);
                } catch (InterruptedException e) {
                    err("Unexpected exception while sleeping: " + e);
                }

            } while (!pageToken.equals("noMore") && mediacount < stopper);
        } catch (JSONException e) {
            err("Problem in media/search response for tag: " + e);
        }
        return mediacount;
    }

    public int parseLocationMedia(String locid, String apiKey_val, int stopper, int mediacount, String qName) throws Exception {

        String pageToken = "";
        try {
            do {
                URL tagMediaUrl = new URL(API_LOC_SITE + "/" + locid + PREFIX_MEDIA + "?" + PREFIX_NEXT_LOC + pageToken + "&" + API_CLIENT_ID + apiKey_val);
                String tagMedia = callGET(tagMediaUrl);
                JSONObject tagMediaobj = new JSONObject(tagMedia);

                pageToken = tagMediaobj.getJSONObject("pagination").optString("next_max_id", "noMore");

                JSONArray tagMediaObjData = tagMediaobj.getJSONArray("data");
                log("media response : " + tagMediaObjData.toString());

                for (int i = 0; i < tagMediaObjData.length(); i++) {
                    JSONObject mediaObject = new JSONObject();
                    JSONObject item = new JSONObject(tagMediaObjData.getString(i));
                    //log(item.toString());
                    if (item.isNull("location")) {
                        mediaObject.put("location", "No location coordinates");
                    } else {
                        mediaObject.put("location", item.getJSONObject("location"));
                    }
                    //From the comments only the comment "text" is needed 
                    JSONArray commentsData = item.getJSONObject("comments").getJSONArray("data");
                    JSONArray comments = new JSONArray();
                    for (int z = 0; z < commentsData.length(); z++) {
                        String com = new JSONObject(commentsData.getString(z)).getString("text");
                        comments.put(com);
                    }
                    mediaObject.put("comments", comments);
                    mediaObject.put("filter", item.getString("filter"));
                    mediaObject.put("created_time", item.getString("created_time"));
                    //convert it to real time?
                    mediaObject.put("likes", item.getJSONObject("likes").getInt("count"));
                    mediaObject.put("image", item.getJSONObject("images").getJSONObject("standard_resolution"));
                    mediaObject.put("users_in_photo", item.getJSONArray("users_in_photo"));
                    if (item.isNull("caption")) {
                        mediaObject.put("caption", "No caption");
                    } else {
                        mediaObject.put("caption", item.getJSONObject("caption").optString("text", "no caption"));
                    }

                    mediaObject.put("id", item.getString("id"));
                    mediaObject.put("user", item.getJSONObject("user"));

                    log(mediaObject.toString());
                    mediacount++;
                    //WRITE to RABBITMQ : 
                    writeToRMQ(mediaObject, qName);
                }
                try {
                    Thread.sleep(700);
                } catch (InterruptedException e) {
                    err("Unexpected exception while sleeping: " + e);
                }
            } while (!pageToken.equals("noMore") && mediacount < stopper);
        } catch (JSONException e) {
            err("Problem in media/search response for tag: " + e);
        }
        return mediacount;
    }

    public String callGET(URL url) {

        String output;
        int code = 0;
        String msg = null;

        try {
            HttpURLConnection httpCon = (HttpURLConnection) url.openConnection();
            // you need the following if you pass server credentials
            // httpCon.setRequestProperty("Authorization", "Basic " + new BASE64Encoder().encode(servercredentials.getBytes()));
            httpCon.setDoOutput(true);
            httpCon.setRequestMethod("GET");
            output = convertStreamToString(httpCon.getInputStream());
            code = httpCon.getResponseCode();
            msg = httpCon.getResponseMessage();
            //output = "" + httpCon.getResponseCode() + "\n" + httpCon.getResponseMessage() + "\n" + output;

        } catch (IOException e) {
            output = "IOException during GET CallGET: " + e;
            err(output);
        }
        // Check for Response 
        if ((code != 200 || code != 201) && !("OK".equals(msg))) {
            //output = "NOT OK RESPONSE";
            err("Failed CallGET: HTTP error code : " + code);
        }
        return output;

    }

    private static String convertStreamToString(InputStream is) throws IOException {
        //
        // To convert the InputStream to String we use the
        // Reader.read(char[] buffer) method. We iterate until the
        // Reader return -1 which means there's no more data to
        // read. We use the StringWriter class to produce the string.
        //
        if (is != null) {
            Writer writer = new StringWriter();

            char[] buffer = new char[1024];
            try {
                Reader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
                int n;
                while ((n = reader.read(buffer)) != -1) {
                    writer.write(buffer, 0, n);
                }
            } finally {
                is.close();
            }

            return writer.toString();
        } else {
            return "";
        }
    }

    public void writeToRMQ(JSONObject json, String qName) throws IOException {

        channel.basicPublish("", qName,
                MessageProperties.PERSISTENT_TEXT_PLAIN,
                json.toString().getBytes("UTF-8"));
        log(" [x] Sent to queue '" + json + "'");
    }

    public void openRMQ(String host, String qName) throws IOException {
        //Pass the queue name here from the RESQUEST JSON

        //Create queue, connect and write to rabbitmq
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(host);

        log("connected to rabbitMQ on localhost ...");

        try {
            connection = factory.newConnection();
            channel = connection.createChannel();

            channel.queueDeclare(qName, true, false, false, null);
        } catch (IOException ex) {
            err("IOException during queue creation: " + ex);
        }
    }

    public void closeRMQ() throws IOException {

        if (connection != null) {
            log("Closing rabbitmq connection and channels");
            try {
                connection.close();
                connection = null;
            } catch (IOException ex) {
                err("IOException during closing rabbitmq connection and channels: " + ex);
            }
        } else {
            log("Closed OK");
        }
    }

    private void log(String message) {
        System.out.println("InstagramCrawler:INFO: " + message);
    }

    private void err(String message) {
        System.err.println("InstagramCrawler:ERROR: " + message);
    }
}
