package com.datn.foodshare.service.payment.strategy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GatewaySigningTest {

    @Test
    void hmacSha256_computesCorrectSignature() {
        String key = "secret-key";
        String data = "sample-data-to-sign";

        String signature = GatewaySigning.hmacSha256(key, data);

        assertNotNull(signature);
        assertFalse(signature.isBlank());
        assertEquals(64, signature.length()); // SHA-256 hex is 64 characters
    }

    @Test
    void hmacSha256_deterministic() {
        String key = "testKey123";
        String data = "orderId=123&amount=50000";

        String sig1 = GatewaySigning.hmacSha256(key, data);
        String sig2 = GatewaySigning.hmacSha256(key, data);

        assertEquals(sig1, sig2);
    }
}
