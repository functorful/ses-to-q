package com.functorful.sestoq.service;

import com.functorful.sestoq.model.SesNotification;
import io.micronaut.json.JsonMapper;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.parser.Parser;
import org.jsoup.safety.Safelist;

import java.io.IOException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Singleton
public class EmailParser {

    private static final String HEADER_BODY_SEPARATOR = "\r\n\r\n";
    private static final String HEADER_BODY_SEPARATOR_LF = "\n\n";
    private static final Pattern CONTENT_TYPE_PATTERN = Pattern.compile(
            "(?i)^Content-Type:\\s*(.+?)$", Pattern.MULTILINE);
    private static final Pattern BOUNDARY_PATTERN = Pattern.compile(
            "(?i)boundary=[\"']?([^\"';\\s]+)[\"']?");
    private static final Pattern MIME_CONTENT_TYPE_PATTERN = Pattern.compile(
            "(?i)^Content-Type:\\s*(.+?)$", Pattern.MULTILINE);

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

        String headers = rawContent.substring(0, separatorIndex);
        String body = rawContent.substring(separatorIndex + separator.length()).trim();

        if (body.isEmpty()) {
            return "(empty message)";
        }

        String contentType = extractHeaderValue(headers, CONTENT_TYPE_PATTERN);

        if (contentType != null && contentType.toLowerCase().contains("multipart/")) {
            return extractFromMultipart(contentType, body);
        }

        if (looksLikeHtml(contentType, body)) {
            return stripHtml(body);
        }

        return body;
    }

    private String extractFromMultipart(String contentType, String body) {
        String boundary = extractBoundary(contentType);
        if (boundary == null) {
            log.warn("Multipart Content-Type without boundary, falling back to HTML stripping");
            return stripHtml(body);
        }

        String[] parts = body.split("--" + Pattern.quote(boundary));

        String textPlainBody = null;
        String textHtmlBody = null;

        for (String part : parts) {
            String trimmedPart = part.trim();
            if (trimmedPart.isEmpty() || trimmedPart.equals("--")) {
                continue;
            }

            String partSeparator;
            int partSepIndex = trimmedPart.indexOf(HEADER_BODY_SEPARATOR);
            if (partSepIndex >= 0) {
                partSeparator = HEADER_BODY_SEPARATOR;
            } else {
                partSepIndex = trimmedPart.indexOf(HEADER_BODY_SEPARATOR_LF);
                partSeparator = HEADER_BODY_SEPARATOR_LF;
            }
            if (partSepIndex < 0) {
                continue;
            }

            String partHeaders = trimmedPart.substring(0, partSepIndex);
            String partBody = trimmedPart.substring(partSepIndex + partSeparator.length()).trim();

            String partContentType = extractHeaderValue(partHeaders, MIME_CONTENT_TYPE_PATTERN);

            if (partContentType != null && partContentType.toLowerCase().contains("text/plain")) {
                textPlainBody = partBody;
            } else if (partContentType != null && partContentType.toLowerCase().contains("text/html")) {
                textHtmlBody = partBody;
            }
        }

        if (textPlainBody != null && !textPlainBody.isBlank()) {
            return textPlainBody;
        }
        if (textHtmlBody != null && !textHtmlBody.isBlank()) {
            return stripHtml(textHtmlBody);
        }

        log.warn("No text/plain or text/html part found in multipart email");
        return stripHtml(body);
    }

    private String extractBoundary(String contentType) {
        Matcher matcher = BOUNDARY_PATTERN.matcher(contentType);
        return matcher.find() ? matcher.group(1) : null;
    }

    private boolean looksLikeHtml(String contentType, String body) {
        if (contentType != null && contentType.toLowerCase().contains("text/html")) {
            return true;
        }
        return body.contains("<html") || body.contains("<HTML")
                || body.contains("<body") || body.contains("<BODY")
                || body.contains("<div") || body.contains("<DIV")
                || body.contains("<!DOCTYPE") || body.contains("<!doctype");
    }

    String stripHtml(String html) {
        Document doc = Jsoup.parse(html);
        Document.OutputSettings outputSettings = new Document.OutputSettings().prettyPrint(false);
        doc.outputSettings(outputSettings);

        doc.select("br").after("\\n");
        doc.select("p, div, li, tr, h1, h2, h3, h4, h5, h6, blockquote").after("\\n");

        String cleaned = Jsoup.clean(doc.body().html(), "", Safelist.none(), outputSettings);

        return Parser.unescapeEntities(cleaned, false)
                .replace("\\n", "\n")
                .replaceAll("[ \\t]*\\n", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    private String extractHeaderValue(String headers, Pattern pattern) {
        Matcher matcher = pattern.matcher(headers);
        return matcher.find() ? matcher.group(1).trim() : null;
    }

    public record ParsedEmail(String sender, String subject, String body) {}
}
