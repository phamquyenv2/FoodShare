package com.datn.foodshare.integration;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class QueryPerformanceMigrationIntegrationTest {

    @Test
    void createsIndexesWithColumnsInQueryPredicateOrder() throws Exception {
        try (Connection connection = DriverManager.getConnection(
                "jdbc:h2:mem:query-performance;MODE=MySQL;DB_CLOSE_DELAY=-1")) {
            createMinimalSchema(connection);
            runMigration(connection);

            assertThat(indexColumns(connection, "USERS", "IDX_USERS_MATCHING_ELIGIBILITY"))
                    .containsExactly("ROLE", "ACTIVE", "PROFILE_COMPLETED");
            assertThat(indexColumns(connection, "ORDERS", "IDX_ORDERS_STATUS_PICKUP_DEADLINE"))
                    .containsExactly("ORDER_STATUS", "PICKUP_DEADLINE");
            assertThat(indexColumns(connection, "PAYOUTS", "IDX_PAYOUTS_STATUS_CREATED"))
                    .containsExactly("PAYOUT_STATUS", "CREATED_AT");
            assertThat(indexColumns(connection, "PAYOUTS", "IDX_PAYOUTS_PROFILE_CREATED"))
                    .containsExactly("BUSINESS_PROFILE_ID", "CREATED_AT");
            assertThat(indexColumns(connection, "NOTIFICATIONS", "IDX_NOTIFICATIONS_USER_CREATED"))
                    .containsExactly("USER_ID", "CREATED_AT");
            assertThat(indexColumns(connection, "REPORTS", "IDX_REPORTS_STATUS_CREATED"))
                    .containsExactly("REPORT_STATUS", "CREATED_AT");
        }
    }

    private void createMinimalSchema(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE users (role VARCHAR(20), active BOOLEAN, profile_completed BOOLEAN, created_at TIMESTAMP)");
            statement.execute("CREATE TABLE orders (order_status VARCHAR(30), pickup_deadline TIMESTAMP, created_at TIMESTAMP)");
            statement.execute("CREATE TABLE payouts (payout_status VARCHAR(20), business_profile_id BIGINT, created_at TIMESTAMP)");
            statement.execute("CREATE TABLE notifications (user_id BIGINT, created_at TIMESTAMP)");
            statement.execute("CREATE TABLE reports (report_status VARCHAR(20), created_at TIMESTAMP)");
        }
    }

    private void runMigration(Connection connection) throws Exception {
        byte[] bytes;
        try (var resource = getClass().getResourceAsStream(
                "/db/migration/V12__add_query_performance_indexes.sql")) {
            assertThat(resource).isNotNull();
            bytes = resource.readAllBytes();
        }
        String sql = new String(bytes, StandardCharsets.UTF_8);
        try (Statement statement = connection.createStatement()) {
            for (String command : sql.split(";")) {
                if (!command.isBlank()) {
                    statement.execute(command);
                }
            }
        }
    }

    private List<String> indexColumns(Connection connection, String table, String index) throws Exception {
        DatabaseMetaData metadata = connection.getMetaData();
        Map<Short, String> columnsByPosition = new HashMap<>();
        try (ResultSet result = metadata.getIndexInfo(null, null, table, false, false)) {
            while (result.next()) {
                if (index.equalsIgnoreCase(result.getString("INDEX_NAME"))) {
                    columnsByPosition.put(
                            result.getShort("ORDINAL_POSITION"),
                            result.getString("COLUMN_NAME"));
                }
            }
        }
        List<Map.Entry<Short, String>> ordered = new ArrayList<>(columnsByPosition.entrySet());
        ordered.sort(Comparator.comparing(Map.Entry::getKey));
        return ordered.stream().map(Map.Entry::getValue).toList();
    }
}
