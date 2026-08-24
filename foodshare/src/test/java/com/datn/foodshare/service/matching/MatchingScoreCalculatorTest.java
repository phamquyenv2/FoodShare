package com.datn.foodshare.service.matching;

import com.datn.foodshare.domain.entity.BusinessProfile;
import com.datn.foodshare.domain.entity.FoodPost;
import com.datn.foodshare.domain.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.datn.foodshare.service.matching.MatchingScoreCalculator.MatchingScoreResult;

@ExtendWith(MockitoExtension.class)
class MatchingScoreCalculatorTest {

    @Mock
    private ReceiverCapacityService receiverCapacityService;

    @InjectMocks
    private MatchingScoreCalculator calculator;

    private Instant evaluatedAt;
    private FoodPost foodPost;

    @BeforeEach
    void setUp() {
        evaluatedAt = Instant.parse("2026-08-18T08:00:00Z");

        User supplier = user(100L, "10.762622", "106.660172");
        BusinessProfile businessProfile = new BusinessProfile();
        businessProfile.setId(10L);
        businessProfile.setUser(supplier);

        foodPost = new FoodPost();
        foodPost.setId(20L);
        foodPost.setBusinessProfile(businessProfile);
        foodPost.setExpiresAt(evaluatedAt.plus(1, ChronoUnit.HOURS));
    }

    @Test
    void calculateScore_usesDistanceUrgencyAndCapacity() {
        User candidate = user(1L, "10.762622", "106.660172");

        MatchingScoreCalculator.MatchingScoreResult result =
                calculator.calculateScore(foodPost, candidate, 0, evaluatedAt);

        assertEquals(0.0, result.distanceKm(), 0.000001);
        assertEquals(1.0, result.distanceScore(), 0.000001);
        assertEquals(0.5, result.urgencyScore(), 0.000001);
        assertEquals(1.0, result.capacityScore(), 0.000001);
        assertEquals((1.0 + 0.5 + 1.0) / 3.0, result.score(), 0.000001);
    }

    @Test
    void calculateScores_loadsCapacityOnceAndAppliesCountsPerCandidate() {
        User available = user(1L, "10.772622", "106.670172");
        User busier = user(2L, "10.772622", "106.670172");
        when(receiverCapacityService.countActiveOrders(any())).thenReturn(Map.of(2L, 3L));

        List<MatchingScoreCalculator.MatchingScoreResult> results =
                calculator.calculateScores(foodPost, List.of(available, busier), evaluatedAt);

        assertEquals(2, results.size());
        assertEquals(1.0, results.get(0).capacityScore(), 0.000001);
        assertEquals(0.25, results.get(1).capacityScore(), 0.000001);
        assertTrue(results.get(0).score() > results.get(1).score());
        verify(receiverCapacityService).countActiveOrders(List.of(1L, 2L));
    }

    @Test
    void calculateScore_omitsDistanceWhenLocationDataIsUnavailable() {
        User candidate = user(1L, null, null);

        MatchingScoreCalculator.MatchingScoreResult result =
                calculator.calculateScore(foodPost, candidate, 0, evaluatedAt);

        assertNull(result.distanceKm());
        assertNull(result.distanceScore());
        assertEquals((0.5 + 1.0) / 2.0, result.score(), 0.000001);
    }

    @Test
    void allScoresAreNormalizedToZeroOneRange() {
        User candidate = user(1L, "21.028511", "105.804817");

        MatchingScoreCalculator.MatchingScoreResult result =
                calculator.calculateScore(foodPost, candidate, 4, evaluatedAt);

        assertNormalized(result.distanceScore());
        assertNormalized(result.urgencyScore());
        assertNormalized(result.capacityScore());
        assertNormalized(result.score());
    }

    @Test
    void compareTo_tiesUseShorterDistanceThenLowerCandidateId() {
        User candidateOne = user(1L, null, null);
        User candidateTwo = user(2L, null, null);
        User candidateThree = user(3L, null, null);

        MatchingScoreCalculator.MatchingScoreResult farther = result(candidateOne, 0.8, 2.0);
        MatchingScoreCalculator.MatchingScoreResult nearerHigherId = result(candidateThree, 0.8, 1.0);
        MatchingScoreCalculator.MatchingScoreResult nearerLowerId = result(candidateTwo, 0.8, 1.0);

        List<MatchingScoreCalculator.MatchingScoreResult> results =
                new ArrayList<>(List.of(farther, nearerHigherId, nearerLowerId));
        results.sort(null);

        assertEquals(List.of(2L, 3L, 1L), results.stream()
                .map(score -> score.candidate().getId())
                .toList());
    }

    @Test
    void calculateScores_emptyInputDoesNotLoadCapacity() {
        assertTrue(calculator.calculateScores(foodPost, List.of(), evaluatedAt).isEmpty());
        verify(receiverCapacityService, never()).countActiveOrders(any());
    }

    private static MatchingScoreCalculator.MatchingScoreResult result(
            User candidate,
            double score,
            Double distanceKm
    ) {
        return new MatchingScoreCalculator.MatchingScoreResult(
                candidate, score, distanceKm, 0.5, 0.5, 0.5, 1
        );
    }

    private static User user(Long id, String latitude, String longitude) {
        User user = new User();
        user.setId(id);
        if (latitude != null) {
            user.setLatitude(new BigDecimal(latitude));
        }
        if (longitude != null) {
            user.setLongitude(new BigDecimal(longitude));
        }
        return user;
    }

    private static void assertNormalized(double score) {
        assertTrue(score >= 0.0 && score <= 1.0);
    }
}
