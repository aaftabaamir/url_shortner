package com.aaftab.shortener.service;

import org.springframework.stereotype.Component;

/**
 * Encodes a numeric auto-increment ID into a short, URL-safe Base62 string.
 * Chosen over a random-string + collision-retry approach so link creation
 * is O(1) with no wasted DB round trips checking for collisions.
 */
@Component
public class Base62Encoder {

    private static final String ALPHABET =
            "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final int BASE = ALPHABET.length();

    public String encode(long id) {
        if (id == 0) return String.valueOf(ALPHABET.charAt(0));
        StringBuilder sb = new StringBuilder();
        long value = id;
        while (value > 0) {
            sb.append(ALPHABET.charAt((int) (value % BASE)));
            value /= BASE;
        }
        return sb.reverse().toString();
    }
}
