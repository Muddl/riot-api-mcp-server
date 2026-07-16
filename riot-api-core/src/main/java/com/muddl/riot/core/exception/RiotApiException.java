package com.muddl.riot.core.exception;

import lombok.Getter;

/**
 * Thrown for any non-2xx Riot API response. Carries the HTTP status code and the raw response
 * body, but its {@code message} is an actionable, human-readable explanation derived from the
 * status — not the raw body. The intended consumer is a third party installing against their own
 * key, so the message is what they (and the model) act on; the body remains reachable via {@link
 * #getRawBody()} for diagnostics. See ADR-0007.
 */
@Getter
public class RiotApiException extends RuntimeException {

    private final int statusCode;

    /** The raw Riot response body, preserved for diagnostics. May be {@code null}. */
    private final String rawBody;

    public RiotApiException(String message, int statusCode) {
        this(message, statusCode, null);
    }

    public RiotApiException(String message, int statusCode, String rawBody) {
        super(message);
        this.statusCode = statusCode;
        this.rawBody = rawBody;
    }

    public RiotApiException(String message, Throwable cause, int statusCode) {
        super(message, cause);
        this.statusCode = statusCode;
        this.rawBody = null;
    }

    /**
     * Builds an exception whose message is the actionable explanation for {@code statusCode},
     * keeping {@code rawBody} for diagnostics. This is the single place the status→message table
     * lives, so the wording is consistent wherever an error surfaces.
     */
    public static RiotApiException forStatus(int statusCode, String rawBody) {
        return new RiotApiException(messageFor(statusCode), statusCode, rawBody);
    }

    private static String messageFor(int statusCode) {
        return switch (statusCode) {
            case 403 -> "Your Riot API key is invalid or expired — development keys expire every 24 hours";
            case 404 -> "The requested resource was not found";
            case 429 -> "Rate limited by the Riot API";
            case 503 -> "The Riot API is temporarily unavailable";
            default -> "Riot API returned HTTP " + statusCode;
        };
    }
}
