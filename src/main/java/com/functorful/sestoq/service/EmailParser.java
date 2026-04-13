package com.functorful.sestoq.service;

import com.functorful.sestoq.model.SesNotification;
import io.micronaut.json.JsonMapper;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.List;

@Slf4j
@Singleton
public class EmailParser {

    private static final String HEADER_BODY_SEPARATOR = "\r\n\r\n";
    private static final String HEADER_BODY_SEPARATOR_LF = "\n\n";

    private final JsonMapper jsonMapper;

    public EmailParser(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public ParsedEmail parse(String sesNotificationJson) {
        SesNotification notification = deserialize(sesNotificationJson);

        String sender = extractSender(notification);
        String subject = extractSubject(notification);
        String body = extractBody(notification.content());

        return new ParsedEmail(sender, subject, body);
    }

    private SesNotification deserialize(String json) {
        try {
            return jsonMapper.readValue(json, SesNotification.class);
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to parse SES notification JSON", e);
        }
    }

    private String extractSender(SesNotification notification) {
        if (notification.mail() == null || notification.mail().commonHeaders() == null) {
            return "Unknown";
        }
        List<String> from = notification.mail().commonHeaders().from();
        if (from == null || from.isEmpty()) {
            return "Unknown";
        }
        return String.join(", ", from);
    }

    private String extractSubject(SesNotification notification) {
        if (notification.mail() == null || notification.mail().commonHeaders() == null) {
            return "No Subject";
        }
        String subject = notification.mail().commonHeaders().subject();
        return subject != null ? subject : "No Subject";
    }

    String extractBody(String rawContent) {
        if (rawContent == null || rawContent.isBlank()) {
            return "(empty message)";
        }

        String separator;
        int separatorIndex = rawContent.indexOf(HEADER_BODY_SEPARATOR);
        if (separatorIndex >= 0) {
            separator = HEADER_BODY_SEPARATOR;
        } else {
            separatorIndex = rawContent.indexOf(HEADER_BODY_SEPARATOR_LF);
            separator = HEADER_BODY_SEPARATOR_LF;
        }
        if (separatorIndex < 0) {
            return "(could not extract message body)";
        }

        int bodyStart = separatorIndex + separator.length();

        String body = rawContent.substring(bodyStart).trim();
        return body.isEmpty() ? "(empty message)" : body;
    }

    public record ParsedEmail(String sender, String subject, String body) {}
}
