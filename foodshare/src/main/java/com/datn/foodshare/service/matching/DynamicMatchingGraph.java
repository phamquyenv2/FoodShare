package com.datn.foodshare.service.matching;

import com.datn.foodshare.domain.entity.FoodPost;
import com.datn.foodshare.domain.entity.User;
import com.datn.foodshare.util.constant.PostStatus;
import com.datn.foodshare.util.constant.Role;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Component
@Slf4j
public class DynamicMatchingGraph {

    private final Map<Long, FoodPostNode> foodPostNodes = new HashMap<>();
    private final Map<Long, CandidateNode> candidateNodes = new HashMap<>();
    private final Map<Long, Set<Long>> candidatesByFoodPost = new HashMap<>();
    private final Map<Long, Set<Long>> foodPostsByCandidate = new HashMap<>();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public void replaceAll(
            Collection<FoodPost> foodPosts,
            Collection<User> candidates,
            Collection<CandidateEdge> edges
    ) {
        replaceAll(foodPosts, candidates, edges, Instant.now());
    }

    void replaceAll(
            Collection<FoodPost> foodPosts,
            Collection<User> candidates,
            Collection<CandidateEdge> edges,
            Instant evaluatedAt
    ) {
        Map<Long, FoodPostNode> newFoodPostNodes = new HashMap<>();
        Map<Long, CandidateNode> newCandidateNodes = new HashMap<>();
        Map<Long, Set<Long>> newCandidatesByFoodPost = new HashMap<>();
        Map<Long, Set<Long>> newFoodPostsByCandidate = new HashMap<>();

        foodPosts.stream()
                .filter(post -> isEligible(post, evaluatedAt))
                .map(FoodPostNode::from)
                .forEach(node -> newFoodPostNodes.put(node.id(), node));
        candidates.stream()
                .map(CandidateNode::from)
                .forEach(node -> newCandidateNodes.put(node.id(), node));
        for (CandidateEdge edge : edges) {
            if (newFoodPostNodes.containsKey(edge.foodPostId())
                    && newCandidateNodes.containsKey(edge.candidateId())) {
                newCandidatesByFoodPost.computeIfAbsent(edge.foodPostId(), ignored -> new HashSet<>())
                        .add(edge.candidateId());
                newFoodPostsByCandidate.computeIfAbsent(edge.candidateId(), ignored -> new HashSet<>())
                        .add(edge.foodPostId());
            }
        }

        lock.writeLock().lock();
        try {
            foodPostNodes.clear();
            foodPostNodes.putAll(newFoodPostNodes);
            candidateNodes.clear();
            candidateNodes.putAll(newCandidateNodes);
            candidatesByFoodPost.clear();
            candidatesByFoodPost.putAll(newCandidatesByFoodPost);
            foodPostsByCandidate.clear();
            foodPostsByCandidate.putAll(newFoodPostsByCandidate);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void addOrUpdateFoodPost(FoodPost foodPost, Collection<User> candidates) {
        addOrUpdateFoodPost(foodPost, candidates, Instant.now());
    }

    void addOrUpdateFoodPost(FoodPost foodPost, Collection<User> candidates, Instant evaluatedAt) {
        if (!isEligible(foodPost, evaluatedAt)) {
            removeFoodPost(foodPost.getId());
            return;
        }

        FoodPostNode foodPostNode = FoodPostNode.from(foodPost);
        Map<Long, CandidateNode> newCandidateNodes = new HashMap<>();
        candidates.stream()
                .map(CandidateNode::from)
                .forEach(node -> newCandidateNodes.put(node.id(), node));

        lock.writeLock().lock();
        try {
            removeFoodPostInternal(foodPost.getId());
            foodPostNodes.put(foodPost.getId(), foodPostNode);
            for (CandidateNode candidateNode : newCandidateNodes.values()) {
                candidateNodes.put(candidateNode.id(), candidateNode);
                addEdgeInternal(new CandidateEdge(foodPost.getId(), candidateNode.id()));
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void addOrUpdateCandidate(User candidate) {
        CandidateNode candidateNode = CandidateNode.from(candidate);
        lock.writeLock().lock();
        try {
            candidateNodes.put(candidate.getId(), candidateNode);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void replaceCandidateEdges(long foodPostId, Collection<User> candidates) {
        Map<Long, CandidateNode> newCandidateNodes = new HashMap<>();
        candidates.stream()
                .map(CandidateNode::from)
                .forEach(node -> newCandidateNodes.put(node.id(), node));

        lock.writeLock().lock();
        try {
            if (!foodPostNodes.containsKey(foodPostId)) {
                return;
            }
            removeEdgesForFoodPostInternal(foodPostId);
            for (CandidateNode candidateNode : newCandidateNodes.values()) {
                candidateNodes.put(candidateNode.id(), candidateNode);
                addEdgeInternal(new CandidateEdge(foodPostId, candidateNode.id()));
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void updateCandidateRelation(long foodPostId, User candidate, boolean eligible) {
        CandidateNode candidateNode = eligible ? CandidateNode.from(candidate) : null;
        lock.writeLock().lock();
        try {
            removeEdgeInternal(foodPostId, candidate.getId());
            if (eligible && foodPostNodes.containsKey(foodPostId)) {
                candidateNodes.put(candidate.getId(), candidateNode);
                addEdgeInternal(new CandidateEdge(foodPostId, candidate.getId()));
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void removeFoodPost(long foodPostId) {
        lock.writeLock().lock();
        try {
            removeFoodPostInternal(foodPostId);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void removeCandidate(long candidateId) {
        lock.writeLock().lock();
        try {
            candidateNodes.remove(candidateId);
            Set<Long> foodPostIds = foodPostsByCandidate.remove(candidateId);
            if (foodPostIds != null) {
                for (Long foodPostId : foodPostIds) {
                    Set<Long> candidateIds = candidatesByFoodPost.get(foodPostId);
                    if (candidateIds != null) {
                        candidateIds.remove(candidateId);
                        if (candidateIds.isEmpty()) {
                            candidatesByFoodPost.remove(foodPostId);
                        }
                    }
                }
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Scheduled(fixedRate = 60_000)
    public void invalidateUnavailableFoodPosts() {
        invalidateUnavailableFoodPosts(Instant.now());
    }

    void invalidateUnavailableFoodPosts(Instant evaluatedAt) {
        lock.writeLock().lock();
        try {
            Set<Long> invalidIds = new HashSet<>();
            foodPostNodes.forEach((id, node) -> {
                if (node.postStatus() != PostStatus.AVAILABLE
                        || node.availableQuantity() <= 0
                        || !node.expiresAt().isAfter(evaluatedAt)) {
                    invalidIds.add(id);
                }
            });
            invalidIds.forEach(this::removeFoodPostInternal);
            if (!invalidIds.isEmpty()) {
                log.info("Dynamic matching graph invalidated {} unavailable FoodPost node(s)", invalidIds.size());
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    public Optional<FoodPostNode> getFoodPost(long foodPostId) {
        lock.readLock().lock();
        try {
            return Optional.ofNullable(foodPostNodes.get(foodPostId));
        } finally {
            lock.readLock().unlock();
        }
    }

    public Optional<CandidateNode> getCandidate(long candidateId) {
        lock.readLock().lock();
        try {
            return Optional.ofNullable(candidateNodes.get(candidateId));
        } finally {
            lock.readLock().unlock();
        }
    }

    public Set<Long> getCandidateIds(long foodPostId) {
        lock.readLock().lock();
        try {
            return Set.copyOf(candidatesByFoodPost.getOrDefault(foodPostId, Set.of()));
        } finally {
            lock.readLock().unlock();
        }
    }

    public boolean hasEdge(long foodPostId, long candidateId) {
        lock.readLock().lock();
        try {
            return candidatesByFoodPost.getOrDefault(foodPostId, Set.of()).contains(candidateId);
        } finally {
            lock.readLock().unlock();
        }
    }

    public int foodPostCount() {
        lock.readLock().lock();
        try {
            return foodPostNodes.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    public int candidateCount() {
        lock.readLock().lock();
        try {
            return candidateNodes.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    public int edgeCount() {
        lock.readLock().lock();
        try {
            return candidatesByFoodPost.values().stream().mapToInt(Set::size).sum();
        } finally {
            lock.readLock().unlock();
        }
    }

    static boolean isEligible(FoodPost post, Instant evaluatedAt) {
        return post != null
                && post.getId() != null
                && post.getPostStatus() == PostStatus.AVAILABLE
                && post.getAvailableQuantity() > 0
                && post.getExpiresAt() != null
                && post.getExpiresAt().isAfter(evaluatedAt);
    }

    private void addEdgeInternal(CandidateEdge edge) {
        if (!foodPostNodes.containsKey(edge.foodPostId())
                || !candidateNodes.containsKey(edge.candidateId())) {
            return;
        }
        candidatesByFoodPost.computeIfAbsent(edge.foodPostId(), ignored -> new HashSet<>())
                .add(edge.candidateId());
        foodPostsByCandidate.computeIfAbsent(edge.candidateId(), ignored -> new HashSet<>())
                .add(edge.foodPostId());
    }

    private void removeFoodPostInternal(long foodPostId) {
        foodPostNodes.remove(foodPostId);
        removeEdgesForFoodPostInternal(foodPostId);
    }

    private void removeEdgeInternal(long foodPostId, long candidateId) {
        Set<Long> candidateIds = candidatesByFoodPost.get(foodPostId);
        if (candidateIds != null) {
            candidateIds.remove(candidateId);
            if (candidateIds.isEmpty()) {
                candidatesByFoodPost.remove(foodPostId);
            }
        }

        Set<Long> foodPostIds = foodPostsByCandidate.get(candidateId);
        if (foodPostIds != null) {
            foodPostIds.remove(foodPostId);
            if (foodPostIds.isEmpty()) {
                foodPostsByCandidate.remove(candidateId);
            }
        }
    }

    private void removeEdgesForFoodPostInternal(long foodPostId) {
        Set<Long> candidateIds = candidatesByFoodPost.remove(foodPostId);
        if (candidateIds == null) {
            return;
        }
        for (Long candidateId : candidateIds) {
            Set<Long> foodPostIds = foodPostsByCandidate.get(candidateId);
            if (foodPostIds != null) {
                foodPostIds.remove(foodPostId);
                if (foodPostIds.isEmpty()) {
                    foodPostsByCandidate.remove(candidateId);
                }
            }
        }
    }

    public record FoodPostNode(
            long id,
            int availableQuantity,
            PostStatus postStatus,
            Instant expiresAt,
            String pickupAddress,
            BigDecimal supplierLatitude,
            BigDecimal supplierLongitude
    ) {
        static FoodPostNode from(FoodPost post) {
            User supplier = post.getBusinessProfile().getUser();
            return new FoodPostNode(
                    post.getId(),
                    post.getAvailableQuantity(),
                    post.getPostStatus(),
                    post.getExpiresAt(),
                    post.getPickupAddress(),
                    supplier.getLatitude(),
                    supplier.getLongitude()
            );
        }
    }

    public record CandidateNode(
            long id,
            Role role,
            boolean active,
            boolean profileCompleted,
            BigDecimal latitude,
            BigDecimal longitude
    ) {
        static CandidateNode from(User user) {
            return new CandidateNode(
                    user.getId(),
                    user.getRole(),
                    user.isActive(),
                    user.isProfileCompleted(),
                    user.getLatitude(),
                    user.getLongitude()
            );
        }
    }

    public record CandidateEdge(long foodPostId, long candidateId) {
    }
}
