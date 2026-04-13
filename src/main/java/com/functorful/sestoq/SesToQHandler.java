package com.functorful.sestoq;

import com.amazonaws.services.lambda.runtime.events.SNSEvent;
import com.functorful.sestoq.service.EmailParser;
import com.functorful.sestoq.service.NotificationPublisher;
import io.micronaut.context.ApplicationContext;
import io.micronaut.function.aws.MicronautRequestHandler;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SesToQHandler extends MicronautRequestHandler<SNSEvent, Void> {

    @Inject
    EmailParser emailParser;

    @Inject
    NotificationPublisher notificationPublisher;

    public SesToQHandler() {}

    SesToQHandler(ApplicationContext applicationContext) {
        super(applicationContext);
    }

    @Override
    public Void execute(SNSEvent input) {
        for (SNSEvent.SNSRecord record : input.getRecords()) {
            String snsMessage = record.getSNS().getMessage();
            log.debug("Processing SES notification: {}", record.getSNS().getMessageId());

            var parsed = emailParser.parse(snsMessage);
            notificationPublisher.publish(parsed.sender(), parsed.subject(), parsed.body());

            log.info("Published Q notification for email from: {}", parsed.sender());
        }
        return null;
    }
}
