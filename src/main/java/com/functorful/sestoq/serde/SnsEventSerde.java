package com.functorful.sestoq.serde;

import com.amazonaws.services.lambda.runtime.events.SNSEvent;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.SerdeImport;

import java.util.List;

@SerdeImport(value = SNSEvent.class, mixin = SnsEventSerde.SNSEventMixin.class)
@SerdeImport(value = SNSEvent.SNSRecord.class, mixin = SnsEventSerde.SNSRecordMixin.class)
@SerdeImport(value = SNSEvent.SNS.class)
@SerdeImport(value = SNSEvent.MessageAttribute.class)
public class SnsEventSerde {

    abstract static class SNSEventMixin {
        @JsonProperty("Records")
        abstract List<SNSEvent.SNSRecord> getRecords();
    }

    abstract static class SNSRecordMixin {
        @JsonProperty("EventVersion")
        abstract String getEventVersion();

        @JsonProperty("EventSubscriptionArn")
        abstract String getEventSubscriptionArn();

        @JsonProperty("EventSource")
        abstract String getEventSource();

        @JsonProperty("Sns")
        abstract SNSEvent.SNS getSNS();
    }
}
