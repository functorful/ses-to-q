package com.functorful.sestoq.service;

import com.functorful.sestoq.model.QNotification;
import io.micronaut.context.annotation.Value;
import io.micronaut.json.JsonMapper;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;

import java.io.IOException;

@Slf4j
@Singleton
public class NotificationPublisher {

    private final SnsClient snsClient;
    private final JsonMapper jsonMapper;
    private final String targetTopicArn;

    public NotificationPublisher(
            SnsClient snsClient,
            JsonMapper jsonMapper,
            @Value("${notification.target-topic-arn}") String targetTopicArn
    ) {
        this.snsClient = snsClient;
        this.jsonMapper = jsonMapper;
        this.targetTopicArn = targetTopicArn;
    }

    public void publish(String sender, String subject, String body) {
        QNotification notification = QNotification.contactForm(sender, subject, body);

        String message = serialize(notification);

        snsClient.publish(PublishRequest.builder()
                .topicArn(targetTopicArn)
                .message(message)
                .build());

        log.info("Published Q Developer notification to {}", targetTopicArn);
    }

    private String serialize(QNotification notification) {
        try {
            return jsonMapper.writeValueAsString(notification);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to serialize Q Developer notification", e);
        }
    }
}
