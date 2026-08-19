package com.datn.foodshare.service.matching;

import com.datn.foodshare.domain.entity.FoodPost;
import com.datn.foodshare.domain.entity.User;
import com.datn.foodshare.service.matching.MatchingScoreCalculator.MatchingScoreResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TopKMatchingServiceTest {

    @Mock
    private MatchingScoreCalculator matchingScoreCalculator;

    @InjectMocks
    private TopKMatchingService topKMatchingService;

    @Test
    void findTopMatches_scoresFilteredCandidatesAndReturnsBestK() {
        FoodPost foodPost = new FoodPost();
        List<User> candidates = List.of(user(1L), user(2L), user(3L), user(4L));
        List<MatchingScoreResult> scores = List.of(
                score(candidates.get(0), 0.40, 4.0),
                score(candidates.get(1), 0.90, 1.0),
                score(candidates.get(2), 0.70, 2.0),
                score(candidates.get(3), 0.80, 3.0)
        );
        when(matchingScoreCalculator.calculateScores(foodPost, candidates)).thenReturn(scores);

        List<MatchingScoreResult> result = topKMatchingService.findTopMatches(foodPost, candidates, 3);

        assertEquals(List.of(2L, 4L, 3L), candidateIds(result));
        verify(matchingScoreCalculator).calculateScores(foodPost, candidates);
    }

    @Test
    void selectTopK_candidateCountBelowKReturnsAllInBestFirstOrder() {
        List<MatchingScoreResult> scores = List.of(
                score(user(1L), 0.50, 2.0),
                score(user(2L), 0.80, 1.0)
        );

        List<MatchingScoreResult> result = topKMatchingService.selectTopK(scores, 5);

        assertEquals(List.of(2L, 1L), candidateIds(result));
    }

    @Test
    void selectTopK_tiesUseExistingDistanceThenCandidateIdRules() {
        List<MatchingScoreResult> scores = List.of(
                score(user(4L), 0.80, 2.0),
                score(user(3L), 0.80, 1.0),
                score(user(2L), 0.80, 1.0),
                score(user(1L), 0.70, 0.5)
        );

        List<MatchingScoreResult> result = topKMatchingService.selectTopK(scores, 3);

        assertEquals(List.of(2L, 3L, 4L), candidateIds(result));
    }

    @Test
    void selectTopK_emptyScoresReturnsEmptyResult() {
        assertEquals(List.of(), topKMatchingService.selectTopK(List.of(), 3));
    }

    @Test
    void selectTopK_rejectsNonPositiveK() {
        assertThrows(
                IllegalArgumentException.class,
                () -> topKMatchingService.selectTopK(List.of(), 0)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> topKMatchingService.selectTopK(List.of(), -1)
        );
    }

    @Test
    void selectTopK_largeDatasetRetainsOnlyKWithinPerformanceBudget() {
        int candidateCount = 100_000;
        int k = 50;
        List<MatchingScoreResult> scores = new ArrayList<>(candidateCount);
        for (int i = 1; i <= candidateCount; i++) {
            double normalizedScore = i / (double) candidateCount;
            scores.add(score(user((long) i), normalizedScore, (double) (candidateCount - i)));
        }

        List<MatchingScoreResult> result = assertTimeout(
                Duration.ofSeconds(2),
                () -> topKMatchingService.selectTopK(scores, k)
        );

        assertEquals(k, result.size());
        assertEquals(100_000L, result.get(0).candidate().getId());
        assertEquals(99_951L, result.get(k - 1).candidate().getId());
    }

    private static MatchingScoreResult score(User candidate, double totalScore, Double distanceKm) {
        return new MatchingScoreResult(
                candidate,
                totalScore,
                distanceKm,
                distanceKm == null ? null : 1.0 / (1.0 + distanceKm),
                0.5,
                0.5,
                1
        );
    }

    private static User user(Long id) {
        User user = new User();
        user.setId(id);
        return user;
    }

    private static List<Long> candidateIds(List<MatchingScoreResult> results) {
        return results.stream()
                .map(result -> result.candidate().getId())
                .toList();
    }
}
