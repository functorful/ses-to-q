package com.functorful.sestoq.model;

import io.micronaut.serde.annotation.Serdeable;

import java.util.List;

@Serdeable
public record SesNotification(
        Mail mail,
        String content
) {

    @Serdeable
    public record Mail(
            String source,
            CommonHeaders commonHeaders
    ) {}

    @Serdeable
    public record CommonHeaders(
            List<String> from,
            String subject
    ) {}
}
