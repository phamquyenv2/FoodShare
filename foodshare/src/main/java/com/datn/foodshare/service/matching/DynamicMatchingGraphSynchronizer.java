package com.datn.foodshare.service.matching;

import com.datn.foodshare.domain.entity.FoodPost;
import com.datn.foodshare.domain.entity.User;
import com.datn.foodshare.repository.FoodPostRepository;
import com.datn.foodshare.repository.UserRepository;
import com.datn.foodshare.service.matching.DynamicMatchingGraph.CandidateEdge;
import com.datn.foodshare.util.constant.PostStatus;
import com.datn.foodshare.util.constant.Role;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class DynamicMatchingGraphSynchronizer {

    private final DynamicMatchingGraph graph;
    private final FoodPostRepository foodPostRepository;
    private final UserRepository userRepository;
    private final MatchingCandidateFilter candidateFilter;
    private final ApplicationEventPublisher eventPublisher;

    @PostConstruct
    public void initialize() {
        try {
            rebuildFromDatabase();
        } catch (RuntimeException exception) {
            log.error("Could not initialize dynamic matching graph; it remains rebuildable from the database", exception);
        }
    }

    public void foodPostChangedAfterCommit(long foodPostId) {
        eventPublisher.publishEvent(new FoodPostChanged(foodPostId));
    }

    public void userChangedAfterCommit(long userId) {
        eventPublisher.publishEvent(new UserChanged(userId));
    }

    public void rebuildAfterCommit() {
        eventPublisher.publishEvent(new RebuildRequested());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public synchronized void onFoodPostChanged(FoodPostChanged event) {
        try {
            synchronizeFoodPost(event.foodPostId());
        } catch (RuntimeException exception) {
            log.error("Could not synchronize FoodPost {} to dynamic matching graph", event.foodPostId(), exception);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public synchronized void onUserChanged(UserChanged event) {
        try {
            synchronizeUser(event.userId());
        } catch (RuntimeException exception) {
            log.error("Could not synchronize user {} to dynamic matching graph", event.userId(), exception);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public synchronized void onRebuildRequested(RebuildRequested event) {
        try {
            rebuildFromDatabase();
        } catch (RuntimeException exception) {
            log.error("Could not rebuild dynamic matching graph", exception);
        }
    }

    public void rebuildFromDatabase() {
        Instant now = Instant.now();
        List<FoodPost> foodPosts = foodPostRepository
                .findAllForMatchingGraph(PostStatus.AVAILABLE, now, 0);
        List<User> globallyEligibleCandidates = userRepository
                .findByRoleIn(MatchingCandidateFilter.RECEIVER_ROLES)
                .stream()
                .filter(candidateFilter::isGloballyEligibleCandidate)
                .toList();

        Map<Long, User> candidateNodes = new LinkedHashMap<>();
        globallyEligibleCandidates.forEach(candidate -> candidateNodes.put(candidate.getId(), candidate));
        List<CandidateEdge> edges = new ArrayList<>();

        for (FoodPost foodPost : foodPosts) {
            for (User candidate : candidateFilter.filterCandidates(foodPost)) {
                candidateNodes.put(candidate.getId(), candidate);
                edges.add(new CandidateEdge(foodPost.getId(), candidate.getId()));
            }
        }

        graph.replaceAll(foodPosts, candidateNodes.values(), edges);
        log.info("Dynamic matching graph rebuilt: {} FoodPost node(s), {} candidate node(s), {} edge(s)",
                graph.foodPostCount(), graph.candidateCount(), graph.edgeCount());
    }

    private void synchronizeFoodPost(long foodPostId) {
        FoodPost foodPost = foodPostRepository.findByIdForMatching(foodPostId).orElse(null);
        synchronizeFoodPost(foodPostId, foodPost);
    }

    private void synchronizeFoodPost(long foodPostId, FoodPost foodPost) {
        if (!DynamicMatchingGraph.isEligible(foodPost, Instant.now())) {
            graph.removeFoodPost(foodPostId);
            return;
        }
        graph.addOrUpdateFoodPost(foodPost, candidateFilter.filterCandidates(foodPost));
    }

    private void synchronizeUser(long userId) {
        User user = userRepository.findById(userId).orElse(null);

        if (candidateFilter.isGloballyEligibleCandidate(user)) {
            synchronizeCandidate(user);
        } else {
            graph.removeCandidate(userId);
        }

        if (user != null && user.getRole() == Role.SUPPLIER) {
            foodPostRepository.findAllBySupplierUserIdForMatching(userId)
                    .forEach(foodPost -> synchronizeFoodPost(foodPost.getId(), foodPost));
        }
    }

    private void synchronizeCandidate(User candidate) {
        graph.addOrUpdateCandidate(candidate);
        List<FoodPost> foodPosts = foodPostRepository
                .findAllForMatchingGraph(PostStatus.AVAILABLE, Instant.now(), 0);
        Set<Long> eligibleFoodPostIds = candidateFilter.findEligibleFoodPostIds(foodPosts, candidate);
        for (FoodPost foodPost : foodPosts) {
            graph.updateCandidateRelation(
                    foodPost.getId(),
                    candidate,
                    eligibleFoodPostIds.contains(foodPost.getId())
            );
        }
    }

    record FoodPostChanged(long foodPostId) {
    }

    record UserChanged(long userId) {
    }

    record RebuildRequested() {
    }
}
