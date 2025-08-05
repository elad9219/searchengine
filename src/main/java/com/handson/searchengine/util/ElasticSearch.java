package com.handson.searchengine.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.handson.searchengine.model.UrlSearchDoc;
import okhttp3.*;
import org.apache.tomcat.util.codec.binary.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;



@Component
public class ElasticSearch {
    OkHttpClient client =  new OkHttpClient();

    @Value("${elasticsearch.base.url}")
    private String ELASTIC_SEARCH_URL;
    @Value("${elasticsearch.key}")
    private  String API_KEY;
    @Value("${elasticsearch.index}")
    private  String index;
    @Autowired
    ObjectMapper om;


    public  void addData(UrlSearchDoc doc)  {
        try {
            String auth =  new String(Base64.encodeBase64(API_KEY.getBytes()));
            String content = om.writeValueAsString(doc);
            MediaType mediaType = MediaType.parse("application/json");
            RequestBody body = RequestBody.create(mediaType, content);
            Request request = new Request.Builder()
                    .url(ELASTIC_SEARCH_URL +  "/"+ index + "/doc")
                    .method("POST", body)
                    .addHeader("Content-Type", "application/json")
                    .addHeader(HttpHeaders.AUTHORIZATION, "Basic " + auth)
                    .build();
            Response response =  client.newCall(request).execute();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
