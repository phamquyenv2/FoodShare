package com.datn.foodshare.service.matching;

import com.datn.foodshare.domain.entity.FoodPost;
import com.datn.foodshare.domain.entity.User;
import com.datn.foodshare.util.constant.PostStatus;
import com.datn.foodshare.util.constant.Role;
import com.datn.foodshare.util.constant.PostType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Component
public class DynamicMatchingGraph {
    private final Map<Long, FoodPostNode> posts = new HashMap<>();
    private final Map<Long, CandidateNode> candidates = new HashMap<>();
    private final Map<Long, Map<Long, WeightedEdge>> byPost = new HashMap<>();
    private final Map<Long, Set<Long>> byCandidate = new HashMap<>();
    private final Map<Long, Long> activeOrders = new HashMap<>();
    private final Map<Long, Long> freeQuantitiesToday = new HashMap<>();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final Map<Long, Set<Long>> requestedPosts = new HashMap<>();
    private boolean ready;
    private long version;

    public void replaceAll(Collection<FoodPost> foodPosts, Collection<User> users, Collection<CandidateEdge> edges) {
        replaceAll(foodPosts, users, edges, Instant.now());
    }

    void replaceAll(Collection<FoodPost> foodPosts, Collection<User> users,
                    Collection<CandidateEdge> edges, Instant now) {
        Map<Long, FoodPostNode> nextPosts = new HashMap<>();
        Map<Long, CandidateNode> nextCandidates = new HashMap<>();
        foodPosts.stream().filter(p -> isEligible(p, now)).map(FoodPostNode::from)
                .forEach(p -> nextPosts.put(p.id(), p));
        users.stream().map(CandidateNode::from).forEach(c -> nextCandidates.put(c.id(), c));
        lock.writeLock().lock();
        try {
            posts.clear(); posts.putAll(nextPosts);
            candidates.clear(); candidates.putAll(nextCandidates);
            byPost.clear(); byCandidate.clear(); activeOrders.clear(); freeQuantitiesToday.clear(); requestedPosts.clear();
            edges.forEach(e -> addEdge(e.foodPostId(), e.candidateId()));
            ready = true;
            version++;
        } finally { lock.writeLock().unlock(); }
    }

    public void rebuild(Collection<FoodPost> foodPosts, Collection<User> users, Map<Long, Long> counts) {
        rebuild(foodPosts, users, counts, Map.of());
    }

    public void rebuild(Collection<FoodPost> foodPosts, Collection<User> users,
                        Map<Long, Long> counts, Map<Long, Set<Long>> previousRequests) {
        Instant now = Instant.now();
        List<CandidateEdge> edges = new ArrayList<>();
        for (FoodPost p : foodPosts) {
            if (!isEligible(p, now)) continue;
            FoodPostNode node = FoodPostNode.from(p);
            for (User u : users) {
                if (related(node, CandidateNode.from(u))) edges.add(new CandidateEdge(p.getId(), u.getId()));
            }
        }
        lock.writeLock().lock();
        try {
            replaceAll(foodPosts, users, edges, now);
            activeOrders.putAll(counts);
            previousRequests.forEach((id, ids) -> requestedPosts.put(id, Set.copyOf(ids)));
        } finally { lock.writeLock().unlock(); }
    }

    public void markUnavailable() {
        lock.writeLock().lock();
        try { ready = false; } finally { lock.writeLock().unlock(); }
    }

    public void synchronizeFoodPost(FoodPost post) {
        lock.writeLock().lock();
        try {
            if (!isEligible(post, Instant.now())) { removePost(post.getId()); version++; return; }
            FoodPostNode node = FoodPostNode.from(post);
            removePost(node.id());
            posts.put(node.id(), node);
            candidates.values().stream().filter(c -> related(node, c)).forEach(c -> addEdge(node.id(), c.id()));
            version++;
        } finally { lock.writeLock().unlock(); }
    }

    public void synchronizeCandidate(User user, long count) {
        synchronizeCandidate(user, count, Set.of());
    }

    public void synchronizeCandidate(User user, long count, Set<Long> previousRequests) {
        if (count < 0) throw new IllegalArgumentException("Active order count cannot be negative");
        CandidateNode node = CandidateNode.from(user);
        lock.writeLock().lock();
        try {
            removeCandidateInternal(node.id());
            if (globallyEligible(node)) {
                candidates.put(node.id(), node);
                activeOrders.put(node.id(), count);
                requestedPosts.put(node.id(), Set.copyOf(previousRequests));
                posts.values().stream().filter(p -> related(p, node)).forEach(p -> addEdge(p.id(), node.id()));
            }
            version++;
        } finally { lock.writeLock().unlock(); }
    }

    public void updateFreeQuantityToday(long candidateId, long quantity) {
        lock.writeLock().lock();
        try { freeQuantitiesToday.put(candidateId, Math.max(0, quantity)); version++; }
        finally { lock.writeLock().unlock(); }
    }

    public void synchronizeSupplier(User supplier) {
        lock.writeLock().lock();
        try {
            List<FoodPostNode> owned = posts.values().stream().filter(p -> p.supplierId() == supplier.getId()).toList();
            for (FoodPostNode p : owned) {
                FoodPostNode updated = new FoodPostNode(p.id(), p.availableQuantity(), p.postStatus(), p.postType(),
                        p.expiresAt(), p.pickupAddress(), supplier.getLatitude(), supplier.getLongitude(),
                        p.supplierId(), p.pickupEndAt(), p.createdAt());
                removePost(p.id());
                posts.put(p.id(), updated);
                candidates.values().stream().filter(c -> related(updated, c)).forEach(c -> addEdge(p.id(), c.id()));
            }
            version++;
        } finally { lock.writeLock().unlock(); }
    }

    public void addOrUpdateFoodPost(FoodPost post, Collection<User> users) {
        addOrUpdateFoodPost(post, users, Instant.now());
    }
    void addOrUpdateFoodPost(FoodPost post, Collection<User> users, Instant now) {
        FoodPostNode node = isEligible(post, now) ? FoodPostNode.from(post) : null;
        lock.writeLock().lock();
        try {
            removePost(post.getId());
            if (node != null) {
                posts.put(node.id(), node);
                for (User u : users) { candidates.put(u.getId(), CandidateNode.from(u)); addEdge(node.id(), u.getId()); }
            }
            version++;
        } finally { lock.writeLock().unlock(); }
    }
    public void addOrUpdateCandidate(User user) {
        lock.writeLock().lock();
        try { candidates.put(user.getId(), CandidateNode.from(user)); version++; }
        finally { lock.writeLock().unlock(); }
    }
    public void replaceCandidateEdges(long postId, Collection<User> users) {
        lock.writeLock().lock();
        try {
            removePostEdges(postId);
            for (User u : users) { candidates.put(u.getId(), CandidateNode.from(u)); addEdge(postId, u.getId()); }
            version++;
        } finally { lock.writeLock().unlock(); }
    }
    public void updateCandidateRelation(long postId, User user, boolean eligible) {
        lock.writeLock().lock();
        try {
            removeEdge(postId, user.getId());
            if (eligible) { candidates.put(user.getId(), CandidateNode.from(user)); addEdge(postId, user.getId()); }
            version++;
        } finally { lock.writeLock().unlock(); }
    }
    public void removeFoodPost(long id) {
        lock.writeLock().lock();
        try { removePost(id); version++; } finally { lock.writeLock().unlock(); }
    }
    public void removeCandidate(long id) {
        lock.writeLock().lock();
        try { removeCandidateInternal(id); version++; } finally { lock.writeLock().unlock(); }
    }
    private void removeCandidateInternal(long id) {
        for (long postId : Set.copyOf(byCandidate.getOrDefault(id, Set.of()))) removeEdge(postId, id);
        candidates.remove(id); activeOrders.remove(id); requestedPosts.remove(id);
    }
    private void removePost(long id) { removePostEdges(id); posts.remove(id); }
    private void removePostEdges(long id) {
        for (long candidateId : Set.copyOf(byPost.getOrDefault(id, Map.of()).keySet())) removeEdge(id, candidateId);
    }
    private void removeEdge(long postId, long candidateId) {
        Map<Long, WeightedEdge> edges = byPost.get(postId);
        if (edges != null) { edges.remove(candidateId); if (edges.isEmpty()) byPost.remove(postId); }
        Set<Long> ids = byCandidate.get(candidateId);
        if (ids != null) { ids.remove(postId); if (ids.isEmpty()) byCandidate.remove(candidateId); }
    }
    private void addEdge(long postId, long candidateId) {
        FoodPostNode p = posts.get(postId); CandidateNode c = candidates.get(candidateId);
        if (p == null || c == null) return;
        Double distance = distance(p, c);
        byPost.computeIfAbsent(postId, ignored -> new HashMap<>()).put(candidateId,
                new WeightedEdge(postId, candidateId, distance,
                        distance == null ? null : MatchingMetrics.inverseNormalize(distance)));
        byCandidate.computeIfAbsent(candidateId, ignored -> new HashSet<>()).add(postId);
    }

    public Snapshot snapshot(Instant now) { return snapshot(now, null); }
    public Snapshot snapshotForCandidate(long candidateId, Instant now) { return snapshot(now, candidateId); }
    private Snapshot snapshot(Instant now, Long onlyCandidate) {
        Objects.requireNonNull(now);
        lock.readLock().lock();
        try {
            if (!ready) throw new IllegalStateException("Matching graph is unavailable; rebuild required");
            Collection<Long> ids = onlyCandidate == null ? posts.keySet()
                    : byCandidate.getOrDefault(onlyCandidate, Set.of());
            List<WeightedPost> result = new ArrayList<>();
            for (long id : ids) {
                FoodPostNode p = posts.get(id);
                if (p == null || !p.availableAt(now)) continue;
                List<MatchingScoreCalculator.MatchingScoreResult> scores = new ArrayList<>();
                Collection<WeightedEdge> edges = onlyCandidate == null
                        ? byPost.getOrDefault(id, Map.of()).values()
                        : Optional.ofNullable(byPost.getOrDefault(id, Map.of()).get(onlyCandidate)).stream().toList();
                for (WeightedEdge edge : edges) {
                    CandidateNode c = candidates.get(edge.candidateId());
                    long count = activeOrders.getOrDefault(c.id(), 0L);
                    if (globallyEligible(c)
                            && (c.role() != Role.RECIPIENT || count < 2)
                            && (p.postType() != PostType.FREE || freeQuantitiesToday.getOrDefault(c.id(), 0L) < 3)
                            && (c.role() != Role.RECIPIENT || !requestedPosts.getOrDefault(c.id(), Set.of()).contains(id))) scores.add(edge.evaluate(c, p.expiresAt(), count, now));
                }
                result.add(new WeightedPost(p, List.copyOf(scores)));
            }
            result.sort(Comparator.comparing((WeightedPost p) -> p.post().expiresAt())
                    .thenComparing(Comparator.comparingInt((WeightedPost p) -> p.post().availableQuantity()).reversed())
                    .thenComparing(p -> p.post().createdAt(), Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparingLong(p -> p.post().id()));
            return new Snapshot(version, now, List.copyOf(result));
        } finally { lock.readLock().unlock(); }
    }

    public Set<Long> getFoodPostIdsForCandidate(long id) {
        lock.readLock().lock();
        try { return Set.copyOf(byCandidate.getOrDefault(id, Set.of())); }
        finally { lock.readLock().unlock(); }
    }
    public Set<Long> getCandidateIds(long id) {
        lock.readLock().lock();
        try { return Set.copyOf(byPost.getOrDefault(id, Map.of()).keySet()); }
        finally { lock.readLock().unlock(); }
    }
    public Optional<FoodPostNode> getFoodPost(long id) {
        lock.readLock().lock();
        try { return Optional.ofNullable(posts.get(id)); } finally { lock.readLock().unlock(); }
    }
    public Optional<CandidateNode> getCandidate(long id) {
        lock.readLock().lock();
        try { return Optional.ofNullable(candidates.get(id)); } finally { lock.readLock().unlock(); }
    }
    public boolean hasEdge(long p, long c) { return getCandidateIds(p).contains(c); }
    public int foodPostCount() {
        lock.readLock().lock(); try { return posts.size(); } finally { lock.readLock().unlock(); }
    }
    public int candidateCount() {
        lock.readLock().lock(); try { return candidates.size(); } finally { lock.readLock().unlock(); }
    }
    public int edgeCount() {
        lock.readLock().lock(); try { return byPost.values().stream().mapToInt(Map::size).sum(); }
        finally { lock.readLock().unlock(); }
    }
    @Scheduled(fixedRate = 60_000)
    public void invalidateUnavailableFoodPosts() { invalidateUnavailableFoodPosts(Instant.now()); }
    void invalidateUnavailableFoodPosts(Instant now) {
        lock.writeLock().lock();
        try {
            List<Long> invalid = posts.values().stream().filter(p -> !p.availableAt(now)).map(FoodPostNode::id).toList();
            invalid.forEach(this::removePost);
            if (!invalid.isEmpty()) version++;
        } finally { lock.writeLock().unlock(); }
    }
    static boolean isEligible(FoodPost p, Instant now) {
        return p != null && p.getId() != null && p.getPostStatus() == PostStatus.AVAILABLE
                && !p.isHiddenByAdmin() && p.getAvailableQuantity() > 0
                && p.getExpiresAt() != null && p.getExpiresAt().isAfter(now)
                && (p.getPickupEndAt() == null || p.getPickupEndAt().isAfter(now));
    }
    private static boolean globallyEligible(CandidateNode c) {
        return c != null && (c.role() == Role.RECIPIENT || c.role() == Role.ORGANIZATION)
                && c.active() && c.profileCompleted() && c.latitude() != null && c.longitude() != null;
    }
    private static boolean related(FoodPostNode p, CandidateNode c) {
        if (!globallyEligible(c) || p.supplierId() == c.id()) return false;
        Double distance = distance(p, c);
        return distance == null || distance <= 10.0;
    }
    private static Double distance(FoodPostNode p, CandidateNode c) {
        return p.pickupLatitude() == null || p.pickupLongitude() == null || c.latitude() == null || c.longitude() == null
                ? null : MatchingMetrics.distanceKm(p.pickupLatitude().doubleValue(), p.pickupLongitude().doubleValue(),
                        c.latitude().doubleValue(), c.longitude().doubleValue());
    }

    public record FoodPostNode(long id, int availableQuantity, PostStatus postStatus, PostType postType, Instant expiresAt,
            String pickupAddress, BigDecimal pickupLatitude, BigDecimal pickupLongitude,
            long supplierId, Instant pickupEndAt, Instant createdAt) {
        static FoodPostNode from(FoodPost p) {
            User u = p.getBusinessProfile().getUser();
            BigDecimal lat = p.getPickupLatitude() != null ? p.getPickupLatitude() : u.getLatitude();
            BigDecimal lng = p.getPickupLongitude() != null ? p.getPickupLongitude() : u.getLongitude();
            return new FoodPostNode(p.getId(), p.getAvailableQuantity(), p.getPostStatus(), p.getPostType(), p.getExpiresAt(),
                    p.getPickupAddress(), lat, lng, u.getId(), p.getPickupEndAt(), p.getCreatedAt());
        }
        boolean availableAt(Instant now) {
            return postStatus == PostStatus.AVAILABLE && availableQuantity > 0 && expiresAt.isAfter(now)
                    && (pickupEndAt == null || pickupEndAt.isAfter(now));
        }
        FoodPost toFoodPost() {
            return FoodPost.builder().id(id).availableQuantity(availableQuantity).postStatus(postStatus)
                    .postType(postType).expiresAt(expiresAt).pickupEndAt(pickupEndAt).createdAt(createdAt).build();
        }
    }
    public record CandidateNode(long id, Role role, boolean active, boolean profileCompleted,
            BigDecimal latitude, BigDecimal longitude, String fullName) {
        static CandidateNode from(User u) {
            return new CandidateNode(u.getId(), u.getRole(), u.isActive(), u.isProfileCompleted(),
                    u.getLatitude(), u.getLongitude(), u.getFullName());
        }
        User toUser() {
            return User.builder().id(id).role(role).active(active).profileCompleted(profileCompleted)
                    .latitude(latitude).longitude(longitude).fullName(fullName).build();
        }
    }
    public record CandidateEdge(long foodPostId, long candidateId) {}
    public record WeightedEdge(long foodPostId, long candidateId, Double distanceKm, Double distanceScore) {
        MatchingScoreCalculator.MatchingScoreResult evaluate(CandidateNode c, Instant expiry, long count, Instant now) {
            double urgency = MatchingMetrics.urgency(expiry, now);
            double capacity = MatchingMetrics.inverseNormalize(count);
            double score = distanceScore == null ? (urgency + capacity) / 2 : (distanceScore + urgency + capacity) / 3;
            return new MatchingScoreCalculator.MatchingScoreResult(c.toUser(), score, distanceKm, distanceScore,
                    urgency, capacity, count);
        }
    }
    public record WeightedPost(FoodPostNode post, List<MatchingScoreCalculator.MatchingScoreResult> scores) {}
    public record Snapshot(long version, Instant evaluatedAt, List<WeightedPost> posts) {}
}
