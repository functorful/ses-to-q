package com.functorful.sestoq;

import com.functorful.sestoq.service.EmailParser;
import com.functorful.sestoq.service.NotificationPublisher;
import io.micronaut.context.ApplicationContext;
import io.micronaut.function.aws.MicronautRequestHandler;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

@Slf4j
public class SesToQHandler extends MicronautRequestHandler<Map<String, Object>, Void> {

    @Inject
    EmailParser emailParser;

    @Inject
    NotificationPublisher notificationPublisher;

    public SesToQHandler() {}

    SesToQHandler(ApplicationContext applicationContext) {
        super(applicationContext);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Void execute(Map<String, Object> input) {
        List<Map<String, Object>> records = (List<Map<String, Object>>) input.get("Records");
        if (records == null) {
            log.warn("No Records found in SNS event");
            return null;
        }

        for (Map<String, Object> record : records) {
            Map<String, Object> sns = (Map<String, Object>) record.get("Sns");
            String snsMessage = (String) sns.get("Message");
            String messageId = (String) sns.get("MessageId");
            log.debug("Processing SES notification: {}", messageId);

            var parsed = emailParser.parse(snsMessage);
            notificationPublisher.publish(parsed.sender(), parsed.subject(), parsed.body());

            log.info("Published Q notification for email from: {}", parsed.sender());
        }
        return null;
    }
}
