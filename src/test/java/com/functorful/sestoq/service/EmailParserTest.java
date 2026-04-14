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

    private String loadFixture() throws IOException {
        try (var stream = getClass().getResourceAsStream(FIXTURE_PATH)) {
            assertThat(stream).as("Fixture file %s must exist", FIXTURE_PATH).isNotNull();
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
