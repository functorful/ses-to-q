package com.functorful.sestoq.service;

import com.functorful.sestoq.model.QNotification;
import io.micronaut.json.JsonMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.PublishResponse;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationPublisherTest {

    private static final String TARGET_TOPIC_ARN = "arn:aws:sns:eu-west-1:000000000000:test-topic";
    private static final String SENDER = "Alice <alice@example.com>";
    private static final String SUBJECT = "Test subject";
    private static final String BODY = "Hello, this is a test message.";

    @Mock
    SnsClient snsClient;

    @Mock
    JsonMapper jsonMapper;

    NotificationPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new NotificationPublisher(snsClient, jsonMapper, TARGET_TOPIC_ARN);
    }

    @Test
    void publishesToTargetTopic() throws IOException {
        when(snsClient.publish(any(PublishRequest.class)))
                .thenReturn(PublishResponse.builder().messageId("msg-1").build());
        when(jsonMapper.writeValueAsString(any(QNotification.class)))
                .thenReturn("{\"version\":\"1.0\"}");

        publisher.publish(SENDER, SUBJECT, BODY);

        ArgumentCaptor<PublishRequest> captor = ArgumentCaptor.forClass(PublishRequest.class);
        verify(snsClient).publish(captor.capture());

        assertThat(captor.getValue().topicArn()).isEqualTo(TARGET_TOPIC_ARN);
    }

    @Test
    void serializesQNotification() throws IOException {
        when(snsClient.publish(any(PublishRequest.class)))
                .thenReturn(PublishResponse.builder().messageId("msg-1").build());

        ArgumentCaptor<QNotification> notificationCaptor = ArgumentCaptor.forClass(QNotification.class);
        when(jsonMapper.writeValueAsString(notificationCaptor.capture()))
                .thenReturn("{}");

        publisher.publish(SENDER, SUBJECT, BODY);

        QNotification captured = notificationCaptor.getValue();
        assertThat(captured.version()).isEqualTo("1.0");
        assertThat(captured.source()).isEqualTo("custom");
        assertThat(captured.content().textType()).isEqualTo("client-markdown");
        assertThat(captured.content().title()).isEqualTo("Contact form: " + SUBJECT);
        assertThat(captured.content().description()).contains(SENDER);
        assertThat(captured.content().description()).contains(BODY);
    }
}
