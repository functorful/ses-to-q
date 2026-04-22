package com.functorful.sestoq.service;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@MicronautTest
class EmailParserTest {

    private static final String FIXTURE_PATH = "/fixtures/ses-notification.json";

    @Inject
    EmailParser emailParser;

    @Test
    void parsesFixtureNotification() throws IOException {
        String json = loadFixture();

        EmailParser.ParsedEmail parsed = emailParser.parse(json);

        assertThat(parsed.sender()).isEqualTo("Alice Smith <sender@example.com>");
        assertThat(parsed.subject()).isEqualTo("Inquiry about consulting");
        assertThat(parsed.body()).contains("I would like to discuss a potential project.");
        assertThat(parsed.body()).contains("Best regards,");
    }

    @Test
    void handlesMultipleSenders() throws IOException {
        String json = loadFixture()
                .replace(
                        "\"from\": [\"Alice Smith <sender@example.com>\"]",
                        "\"from\": [\"Alice <a@test.com>\", \"Bob <b@test.com>\"]"
                );

        EmailParser.ParsedEmail parsed = emailParser.parse(json);

        assertThat(parsed.sender()).isEqualTo("Alice <a@test.com>, Bob <b@test.com>");
    }

    @Test
    void handlesMissingCommonHeaders() {
        String json = """
                {"mail": {"source": "test@test.com"}, "content": "From: test\\r\\n\\r\\nBody text"}
                """;

        EmailParser.ParsedEmail parsed = emailParser.parse(json);

        assertThat(parsed.sender()).isEqualTo("Unknown");
        assertThat(parsed.subject()).isEqualTo("No Subject");
    }

    @Test
    void handlesNullContent() {
        String json = """
                {"mail": {"source": "t@t.com", "commonHeaders": {"from": ["Test"], "subject": "Hi"}}}
                """;

        EmailParser.ParsedEmail parsed = emailParser.parse(json);

        assertThat(parsed.body()).isEqualTo("(empty message)");
    }

    @Test
    void handlesContentWithLfLineEndings() {
        String json = """
                {"mail": {"source": "t@t.com", "commonHeaders": {"from": ["Test"], "subject": "Hi"}},
                 "content": "From: test\\nTo: dest\\n\\nBody with LF endings"}
                """;

        EmailParser.ParsedEmail parsed = emailParser.parse(json);

        assertThat(parsed.body()).isEqualTo("Body with LF endings");
    }

    @Test
    void rejectsInvalidJson() {
        assertThatThrownBy(() -> emailParser.parse("not json"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Failed to parse SES notification JSON");
    }

    @Test
    void extractBodyReturnsMessageForContentWithoutHeaders() {
        String result = emailParser.extractBody("no header separator here");

        assertThat(result).isEqualTo("(could not extract message body)");
    }

    @Test
    void stripsHtmlFromHtmlEmail() {
        String rawContent = "From: test@test.com\r\n"
                + "Content-Type: text/html; charset=UTF-8\r\n"
                + "\r\n"
                + "<html><body><h1>Hello</h1><p>This is a <strong>test</strong> message.</p></body></html>";

        String result = emailParser.extractBody(rawContent);

        assertThat(result).contains("Hello");
        assertThat(result).contains("This is a test message.");
        assertThat(result).doesNotContain("<html>");
        assertThat(result).doesNotContain("<p>");
        assertThat(result).doesNotContain("<strong>");
    }

    @Test
    void stripsHtmlDetectedByTags() {
        String rawContent = "From: test@test.com\r\n"
                + "\r\n"
                + "<html><body><p>Detected by tags</p></body></html>";

        String result = emailParser.extractBody(rawContent);

        assertThat(result).isEqualTo("Detected by tags");
    }

    @Test
    void extractsTextPlainFromMultipart() {
        String rawContent = "From: test@test.com\r\n"
                + "Content-Type: multipart/alternative; boundary=\"boundary123\"\r\n"
                + "\r\n"
                + "--boundary123\r\n"
                + "Content-Type: text/plain; charset=UTF-8\r\n"
                + "\r\n"
                + "Plain text content\r\n"
                + "--boundary123\r\n"
                + "Content-Type: text/html; charset=UTF-8\r\n"
                + "\r\n"
                + "<html><body><p>HTML content</p></body></html>\r\n"
                + "--boundary123--";

        String result = emailParser.extractBody(rawContent);

        assertThat(result).isEqualTo("Plain text content");
    }

    @Test
    void fallsBackToStrippedHtmlWhenNoTextPlain() {
        String rawContent = "From: test@test.com\r\n"
                + "Content-Type: multipart/alternative; boundary=\"boundary456\"\r\n"
                + "\r\n"
                + "--boundary456\r\n"
                + "Content-Type: text/html; charset=UTF-8\r\n"
                + "\r\n"
                + "<html><body><p>Only HTML here</p></body></html>\r\n"
                + "--boundary456--";

        String result = emailParser.extractBody(rawContent);

        assertThat(result).contains("Only HTML here");
        assertThat(result).doesNotContain("<p>");
        assertThat(result).doesNotContain("<html>");
    }

    @Test
    void stripHtmlPreservesLineBreaks() {
        String html = "<p>First paragraph</p><p>Second paragraph</p><br>Third line";

        String result = emailParser.stripHtml(html);

        assertThat(result).contains("First paragraph");
        assertThat(result).contains("Second paragraph");
        assertThat(result).contains("Third line");
        assertThat(result).doesNotContain("<p>");
        assertThat(result).doesNotContain("<br>");
    }

    @Test
    void stripHtmlHandlesEntities() {
        String html = "<p>Price: &lt;100&gt; &amp; free</p>";

        String result = emailParser.stripHtml(html);

        assertThat(result).contains("Price: <100> & free");
    }

    @Test
    void stripHtmlRemovesScriptAndStyleTags() {
        String html = "<html><head><style>body{color:red}</style></head>"
                + "<body><script>alert('xss')</script><p>Safe content</p></body></html>";

        String result = emailParser.stripHtml(html);

        assertThat(result).contains("Safe content");
        assertThat(result).doesNotContain("alert");
        assertThat(result).doesNotContain("color:red");
        assertThat(result).doesNotContain("<script>");
    }

    private String loadFixture() throws IOException {
        try (var stream = getClass().getResourceAsStream(FIXTURE_PATH)) {
            assertThat(stream).as("Fixture file %s must exist", FIXTURE_PATH).isNotNull();
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
