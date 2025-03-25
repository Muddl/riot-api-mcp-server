package com.wkaiser.riotapimcpserver.shared.exception;

import lombok.Getter;

/**
 * Exception thrown when there's an error with the Riot API.
 * Includes the HTTP status code for more detailed error handling.
 */
@Getter
public class RiotApiException extends RuntimeException {
    private final int statusCode;
    
    public RiotApiException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }
    
    public RiotApiException(String message, Throwable cause, int statusCode) {
        super(message, cause);
        this.statusCode = statusCode;
    }
}
