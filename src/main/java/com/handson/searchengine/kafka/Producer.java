package com.handson.searchengine.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;


@Component
public class Producer {

    public static final String APP_TOPIC = "searchengine";
    @Autowired
    ObjectMapper om;

    @Autowired
    private KafkaTemplate kafkaTemplate;

    public void send(Object message) throws JsonProcessingException {
        kafkaTemplate.send(APP_TOPIC, om.writeValueAsString(message));
    }

}
