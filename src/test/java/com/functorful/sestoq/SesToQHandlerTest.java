package com.functorful.sestoq;

import com.functorful.sestoq.service.EmailParser;
import com.functorful.sestoq.service.NotificationPublisher;
import io.micronaut.context.ApplicationContext;
import io.micronaut.test.annotation.MockBean;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.sns.SnsClient;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@MicronautTest
class SesToQHandlerTest {

    private static final String SENDER = "Alice <alice@example.com>";
    private static final String SUBJECT = "Test";
    private static final String BODY = "Hello";
    private static final String SES_NOTIFICATION_JSON = "{\"mail\":{}}";

    @Inject
    ApplicationContext applicationContext;

    @Inject
    EmailParser emailParser;

    @Inject
    NotificationPublisher notificationPublisher;

    SesToQHandler handler;

    @MockBean(EmailParser.class)
    EmailParser emailParser() {
        return mock(EmailParser.class);
    }

    @MockBean(NotificationPublisher.class)
    NotificationPublisher notificationPublisher() {
        return mock(NotificationPublisher.class);
    }

    @MockBean(SnsClient.class)
    SnsClient snsClient() {
        return mock(SnsClient.class);
    }

    @BeforeEach
    void setUp() {
        handler = new SesToQHandler(applicationContext);
    }

    @Test
    void parsesAndPublishesEachRecord() {
        when(emailParser.parse(anyString()))
                .thenReturn(new EmailParser.ParsedEmail(SENDER, SUBJECT, BODY));

        Map<String, Object> event = createSnsEvent(SES_NOTIFICATION_JSON);

        handler.execute(event);

        verify(emailParser).parse(SES_NOTIFICATION_JSON);
        verify(notificationPublisher).publish(SENDER, SUBJECT, BODY);
    }

    @Test
    void handlesMultipleRecords() {
        when(emailParser.parse(anyString()))
                .thenReturn(new EmailParser.ParsedEmail(SENDER, SUBJECT, BODY));

        String secondMessage = "{\"mail\":{\"source\":\"other\"}}";
        Map<String, Object> event = createSnsEvent(SES_NOTIFICATION_JSON, secondMessage);

        handler.execute(event);

        verify(emailParser).parse(SES_NOTIFICATION_JSON);
        verify(emailParser).parse(secondMessage);
        verify(notificationPublisher, times(2)).publish(SENDER, SUBJECT, BODY);
    }

    private Map<String, Object> createSnsEvent(String... messages) {
        List<Map<String, Object>> records = java.util.Arrays.stream(messages)
                .map(msg -> Map.<String, Object>of(
                        "Sns", Map.of("Message", msg, "MessageId", "test-id")
                ))
                .toList();

        return Map.of("Records", records);
    }
}
