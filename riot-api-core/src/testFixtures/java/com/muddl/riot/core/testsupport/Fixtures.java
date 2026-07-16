package com.muddl.riot.core.testsupport;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * Loads canned JSON fixtures from {@code src/test/resources/fixtures/} on the test
 * classpath. Used by the WireMock adapter tests to stub Riot API responses.
 */
public final class Fixtures {

    private Fixtures() {}

    /** Returns the UTF-8 contents of {@code /fixtures/<name>}; fails fast if absent. */
    public static String read(String name) {
        String path = "/fixtures/" + name;
        try (InputStream in = Fixtures.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalArgumentException("Fixture not found on classpath: " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read fixture: " + path, e);
        }
    }
}
