package com.datn.foodshare.service.matching;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.PriorityQueue;

import org.springframework.stereotype.Service;

import com.datn.foodshare.domain.entity.FoodPost;
import com.datn.foodshare.domain.entity.User;
import com.datn.foodshare.service.matching.MatchingScoreCalculator.MatchingScoreResult;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TopKMatchingService {

    private final MatchingScoreCalculator matchingScoreCalculator;

    public List<MatchingScoreResult> findTopMatches(
            FoodPost foodPost,
            List<User> filteredCandidates,
            int k
    ) {
        Objects.requireNonNull(foodPost, "Bài đăng thực phẩm không được rỗng");
        Objects.requireNonNull(filteredCandidates, "Danh sách ứng viên đã lọc không được rỗng");
        validateK(k);

        List<MatchingScoreResult> scores = matchingScoreCalculator.calculateScores(
                foodPost,
                filteredCandidates
        );
        return selectTopK(scores, k);
    }

    List<MatchingScoreResult> selectTopK(List<MatchingScoreResult> scores, int k) {
        Objects.requireNonNull(scores, "Danh sách điểm matching không được rỗng");
        validateK(k);

        if (scores.isEmpty()) {
            return List.of();
        }

        PriorityQueue<MatchingScoreResult> bestMatches = new PriorityQueue<>(
                k,
                Comparator.reverseOrder()
        );

        for (MatchingScoreResult score : scores) {
            Objects.requireNonNull(score, "Điểm matching không được rỗng");

            if (bestMatches.size() < k) {
                bestMatches.offer(score);
                continue;
            }

            MatchingScoreResult worstRetained = bestMatches.peek();
            if (score.compareTo(worstRetained) < 0) {
                bestMatches.poll();
                bestMatches.offer(score);
            }
        }

        return bestMatches.stream()
                .sorted()
                .toList();
    }

    private void validateK(int k) {
        if (k <= 0) {
            throw new IllegalArgumentException("K phải lớn hơn 0");
        }
    }
}
