package com.datn.foodshare.service;

import com.datn.foodshare.domain.entity.FoodPost;
import com.datn.foodshare.repository.FoodPostRepository;
import com.datn.foodshare.util.constant.PostStatus;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Service
@RequiredArgsConstructor
@Slf4j
public class FoodPostPriorityQueue {

    private final FoodPostRepository foodPostRepository;

    static final Comparator<FoodPostPriorityEntry> PRIORITY_COMPARATOR = Comparator
            .comparingLong(FoodPostPriorityEntry::remainingSeconds)
            .thenComparingInt(FoodPostPriorityEntry::availableQuantity)
            .thenComparing(FoodPostPriorityEntry::createdAt)
            .thenComparingLong(FoodPostPriorityEntry::foodPostId);

    private final PriorityQueue<FoodPostPriorityEntry> queue = new PriorityQueue<>(PRIORITY_COMPARATOR);
    private final Map<Long, FoodPostPriorityEntry> entryIndex = new ConcurrentHashMap<>();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public record FoodPostPriorityEntry(
            long foodPostId,
            long remainingSeconds,
            int availableQuantity,
            Instant expiresAt,
            Instant createdAt
    ) {
        static FoodPostPriorityEntry fromFoodPost(FoodPost post, Instant now) {
            long remaining = Duration.between(now, post.getExpiresAt()).getSeconds();
            return new FoodPostPriorityEntry(
                    post.getId(),
                    Math.max(remaining, 0),
                    post.getAvailableQuantity(),
                    post.getExpiresAt(),
                    post.getCreatedAt()
            );
        }

        double urgency() {
            return 1.0 / (1.0 + remainingSeconds / 3600.0);
        }
    }

    @PostConstruct
    void init() {
        rebuild();
    }

    public void rebuild() {
        lock.writeLock().lock();
        try {
            queue.clear();
            entryIndex.clear();

            Instant now = Instant.now();
            List<FoodPost> validPosts = foodPostRepository.findAllByPostStatusAndExpiresAtAfterAndAvailableQuantityGreaterThan(
                    PostStatus.AVAILABLE, now, 0);

            for (FoodPost post : validPosts) {
                FoodPostPriorityEntry entry = FoodPostPriorityEntry.fromFoodPost(post, now);
                queue.offer(entry);
                entryIndex.put(post.getId(), entry);
            }

            log.info("Priority Queue rebuilt: {} FoodPost(s) loaded", queue.size());
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void addOrUpdate(FoodPost post) {
        lock.writeLock().lock();
        try {
            removeInternal(post.getId());

            if (!isEligible(post)) {
                return;
            }

            Instant now = Instant.now();
            FoodPostPriorityEntry entry = FoodPostPriorityEntry.fromFoodPost(post, now);
            queue.offer(entry);
            entryIndex.put(post.getId(), entry);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void remove(long foodPostId) {
        lock.writeLock().lock();
        try {
            removeInternal(foodPostId);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public FoodPostPriorityEntry peek() {
        lock.writeLock().lock();
        try {
            evictExpired();
            return queue.peek();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public FoodPostPriorityEntry poll() {
        lock.writeLock().lock();
        try {
            evictExpired();
            FoodPostPriorityEntry entry = queue.poll();
            if (entry != null) {
                entryIndex.remove(entry.foodPostId());
            }
            return entry;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public List<FoodPostPriorityEntry> getOrderedEntries() {
        lock.readLock().lock();
        try {
            return queue.stream()
                    .filter(e -> e.expiresAt().isAfter(Instant.now()))
                    .sorted(PRIORITY_COMPARATOR)
                    .toList();
        } finally {
            lock.readLock().unlock();
        }
    }

    public int size() {
        lock.readLock().lock();
        try {
            return queue.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    public boolean contains(long foodPostId) {
        lock.readLock().lock();
        try {
            return entryIndex.containsKey(foodPostId);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Scheduled(fixedRate = 60_000)
    void evictExpiredScheduled() {
        lock.writeLock().lock();
        try {
            int before = queue.size();
            evictExpired();
            int evicted = before - queue.size();
            if (evicted > 0) {
                log.info("Priority Queue: evicted {} expired entry(ies), remaining: {}", evicted, queue.size());
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    private boolean isEligible(FoodPost post) {
        return post.getPostStatus() == PostStatus.AVAILABLE
                && post.getExpiresAt().isAfter(Instant.now())
                && post.getAvailableQuantity() > 0;
    }

    private void removeInternal(long foodPostId) {
        FoodPostPriorityEntry existing = entryIndex.remove(foodPostId);
        if (existing != null) {
            queue.remove(existing);
        }
    }

    private void evictExpired() {
        Instant now = Instant.now();
        List<FoodPostPriorityEntry> expired = new ArrayList<>();
        for (FoodPostPriorityEntry entry : queue) {
            if (!entry.expiresAt().isAfter(now)) {
                expired.add(entry);
            }
        }
        for (FoodPostPriorityEntry entry : expired) {
            queue.remove(entry);
            entryIndex.remove(entry.foodPostId());
        }
    }
}
