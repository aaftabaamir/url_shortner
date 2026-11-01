package com.aaftab.shortener.exception;

public class RateLimitExceededException extends RuntimeException {
    public RateLimitExceededException(String apiKey) {
        super("Rate limit exceeded for API key: " + apiKey);
    }
}
