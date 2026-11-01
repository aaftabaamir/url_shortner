package com.aaftab.shortener.exception;

public class LinkNotFoundException extends RuntimeException {
    public LinkNotFoundException(String shortCode) {
        super("No link found for short code: " + shortCode);
    }
}
