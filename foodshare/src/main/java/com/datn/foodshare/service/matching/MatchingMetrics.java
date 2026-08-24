package com.datn.foodshare.service.matching;

import java.time.Duration;
import java.time.Instant;

final class MatchingMetrics {

    private static final double EARTH_RADIUS_KM = 6371.0;

    private MatchingMetrics() {
    }

    static double distanceKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_KM * c;
    }

    static double urgency(Instant expiresAt, Instant evaluatedAt) {
        return urgency(Duration.between(evaluatedAt, expiresAt).getSeconds());
    }

    static double urgency(long remainingSeconds) {
        remainingSeconds = Math.max(remainingSeconds, 0);
        return inverseNormalize(remainingSeconds / 3600.0);
    }

    static double inverseNormalize(double nonNegativeValue) {
        if (nonNegativeValue < 0) {
            throw new IllegalArgumentException("Giá trị chuẩn hóa không được âm");
        }
        return 1.0 / (1.0 + nonNegativeValue);
    }
}
