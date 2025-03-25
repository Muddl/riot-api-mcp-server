package com.wkaiser.riotapimcpserver.shared.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

/**
 * Global exception handler for the application.
 * Handles various types of exceptions and converts them to appropriate HTTP responses.
 * This is particularly important for the Riot API integration to handle rate limiting
 * and other API-specific error conditions.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    @ExceptionHandler(RiotApiException.class)
    public ProblemDetail handleRiotApiException(RiotApiException e) {
        log.error("Riot API error: {}", e.getMessage(), e);
        
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.valueOf(e.getStatusCode()),
                e.getMessage()
        );
    }
    
    @ExceptionHandler(HttpClientErrorException.class)
    public ProblemDetail handleHttpClientErrorException(HttpClientErrorException e) {
        log.error("HTTP client error: {}", e.getMessage(), e);
        
        return ProblemDetail.forStatusAndDetail(
                e.getStatusCode(),
                e.getMessage()
        );
    }
    
    @ExceptionHandler(HttpServerErrorException.class)
    public ProblemDetail handleHttpServerErrorException(HttpServerErrorException e) {
        log.error("HTTP server error: {}", e.getMessage(), e);
        
        return ProblemDetail.forStatusAndDetail(
                e.getStatusCode(),
                e.getMessage()
        );
    }
    
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgumentException(IllegalArgumentException e) {
        log.error("Invalid argument: {}", e.getMessage(), e);
        
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                e.getMessage()
        );
    }
    
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGenericException(Exception e) {
        log.error("Unexpected error: {}", e.getMessage(), e);
        
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred: " + e.getMessage()
        );
    }
}
