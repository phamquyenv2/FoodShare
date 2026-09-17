package com.datn.foodshare.service.matching;

import com.datn.foodshare.domain.entity.User;
import com.datn.foodshare.repository.FoodPostRepository;
import com.datn.foodshare.repository.UserRepository;
import com.datn.foodshare.util.constant.PostStatus;
import com.datn.foodshare.util.constant.Role;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;
import java.util.List;

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
        try { rebuildFromDatabase(); }
        catch (RuntimeException e) { graph.markUnavailable(); log.error("Cannot initialize matching graph", e); }
    }
    public void foodPostChangedAfterCommit(long id) { eventPublisher.publishEvent(new FoodPostChanged(id)); }
    public void userChangedAfterCommit(long id) { eventPublisher.publishEvent(new UserChanged(id)); }
    public void rebuildAfterCommit() { eventPublisher.publishEvent(new RebuildRequested()); }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public synchronized void onFoodPostChanged(FoodPostChanged event) {
        try {
            var post = foodPostRepository.findByIdForMatching(event.foodPostId()).orElse(null);
            if (post == null) graph.removeFoodPost(event.foodPostId());
            else graph.synchronizeFoodPost(post);
        } catch (RuntimeException e) { graph.markUnavailable(); log.error("Cannot synchronize post {}", event.foodPostId(), e); }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public synchronized void onUserChanged(UserChanged event) {
        try {
            User user = userRepository.findById(event.userId()).orElse(null);
            if (candidateFilter.isGloballyEligibleCandidate(user)) {
                long count = candidateFilter.loadActiveOrderCounts(List.of(user.getId())).getOrDefault(user.getId(), 0L);
                graph.synchronizeCandidate(user, count,
                        candidateFilter.loadPreviouslyRequestedPosts(List.of(user.getId()))
                                .getOrDefault(user.getId(), java.util.Set.of()));
                graph.updateFreeQuantityToday(user.getId(), candidateFilter.countFreeQuantityToday(user.getId()));
            } else graph.removeCandidate(event.userId());
            if (user != null && user.getRole() == Role.SUPPLIER) graph.synchronizeSupplier(user);
        } catch (RuntimeException e) { graph.markUnavailable(); log.error("Cannot synchronize user {}", event.userId(), e); }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public synchronized void onRebuildRequested(RebuildRequested event) { initialize(); }

    /** Recovery and reconciliation for missed events/external SQL; requests never silently use a broken graph. */
    @Scheduled(fixedDelay = 300_000, initialDelay = 300_000)
    @Transactional(readOnly = true)
    public synchronized void reconcile() { initialize(); }

    public synchronized void rebuildFromDatabase() {
        var posts = foodPostRepository.findAllForMatchingGraph(PostStatus.AVAILABLE, Instant.now(), 0);
        var candidates = candidateFilter.findGloballyEligibleCandidates();
        var counts = candidateFilter.loadActiveOrderCounts(candidates.stream().map(User::getId).toList());
        graph.rebuild(posts, candidates, counts,
                candidateFilter.loadPreviouslyRequestedPosts(candidates.stream().map(User::getId).toList()));
        candidates.forEach(user -> graph.updateFreeQuantityToday(user.getId(),
                candidateFilter.countFreeQuantityToday(user.getId())));
        log.info("Matching graph ready: {} posts, {} receivers, {} edges",
                graph.foodPostCount(), graph.candidateCount(), graph.edgeCount());
    }
    public record FoodPostChanged(long foodPostId) {}
    public record UserChanged(long userId) {}
    public record RebuildRequested() {}
}
