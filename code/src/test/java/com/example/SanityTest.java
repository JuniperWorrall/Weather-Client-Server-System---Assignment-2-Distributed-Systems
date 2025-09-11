package com.example;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

public class SanityTest {
    @Test
    void alwaysPasses() {
        assertEquals(2, 1 + 1);
    }
}