package com.datn.foodshare.integration;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PayoutMigrationTest {

    @Test
    void migrationContainsLedgerBackfillAndPayoutOwnership() throws Exception {
        var resource = getClass().getResourceAsStream(
                "/db/migration/V11__create_supplier_earnings_and_aggregate_payouts.sql");
        assertNotNull(resource);
        String sql = new String(resource.readAllBytes(), StandardCharsets.UTF_8);

        assertTrue(sql.contains("CREATE TABLE supplier_earnings"));
        assertTrue(sql.contains("o.order_status = 'COMPLETED'"));
        assertTrue(sql.contains("p.payment_status = 'SUCCESS'"));
        assertTrue(sql.contains("p.method = 'EWALLET'"));
        assertTrue(sql.contains("ADD COLUMN business_profile_id"));
        assertTrue(sql.contains("ADD COLUMN requested_amount"));
    }
}
