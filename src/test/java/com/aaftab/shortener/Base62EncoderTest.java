package com.aaftab.shortener;

import com.aaftab.shortener.service.Base62Encoder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class Base62EncoderTest {

    private final Base62Encoder encoder = new Base62Encoder();

    @Test
    void encodesZeroToFirstCharacter() {
        assertEquals("0", encoder.encode(0));
    }

    @Test
    void encodingIsDeterministic() {
        assertEquals(encoder.encode(12345), encoder.encode(12345));
    }

    @Test
    void differentIdsProduceDifferentCodes() {
        assertNotEquals(encoder.encode(1), encoder.encode(2));
    }

    @Test
    void largeIdsProduceShortCodes() {
        // A billion-scale ID should still encode to a handful of characters,
        // which is the whole point of Base62 over decimal for short links.
        String encoded = encoder.encode(1_000_000_000L);
        assertEquals(true, encoded.length() <= 6);
    }
}
