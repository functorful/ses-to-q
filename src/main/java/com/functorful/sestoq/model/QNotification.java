package com.functorful.sestoq.model;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record QNotification(
        String version,
        String source,
        Content content
) {

    @Serdeable
    public record Content(
            String textType,
            String title,
            String description
    ) {}

    public static QNotification contactForm(String sender, String subject, String body) {
        String description = "*From:* %s\n*Subject:* %s\n\n%s".formatted(sender, subject, body);
        return new QNotification(
                "1.0",
                "custom",
                new Content("client-markdown", "Contact form: " + subject, description)
        );
    }
}
