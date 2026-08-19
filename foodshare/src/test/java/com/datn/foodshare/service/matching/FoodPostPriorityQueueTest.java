package com.datn.foodshare.service.matching;

import com.datn.foodshare.domain.entity.BusinessProfile;
import com.datn.foodshare.domain.entity.Category;
import com.datn.foodshare.domain.entity.FoodPost;
import com.datn.foodshare.repository.FoodPostRepository;
import com.datn.foodshare.service.matching.FoodPostPriorityQueue.FoodPostPriorityEntry;
import com.datn.foodshare.util.constant.PostStatus;
import com.datn.foodshare.util.constant.PostType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FoodPostPriorityQueueTest {

    @Mock
    private FoodPostRepository foodPostRepository;

    private FoodPostPriorityQueue priorityQueue;

    @BeforeEach
    void setUp() {
        when(foodPostRepository.findAllByPostStatusAndExpiresAtAfterAndAvailableQuantityGreaterThan(
                eq(PostStatus.AVAILABLE), any(Instant.class), eq(0)))
                .thenReturn(List.of());

        priorityQueue = new FoodPostPriorityQueue(foodPostRepository);
        priorityQueue.init();
    }

    @Nested
    class InsertTests {

        @Test
        void insert_validFoodPost_addedToQueue() {
            FoodPost post = createPost(1L, PostStatus.AVAILABLE, 10,
                    Instant.now().plus(2, ChronoUnit.HOURS));

            priorityQueue.addOrUpdate(post);

            assertEquals(1, priorityQueue.size());
            assertTrue(priorityQueue.contains(1L));
        }

        @Test
        void insert_multiplePosts_allAdded() {
            FoodPost post1 = createPost(1L, PostStatus.AVAILABLE, 10,
                    Instant.now().plus(2, ChronoUnit.HOURS));
            FoodPost post2 = createPost(2L, PostStatus.AVAILABLE, 5,
                    Instant.now().plus(4, ChronoUnit.HOURS));
            FoodPost post3 = createPost(3L, PostStatus.AVAILABLE, 3,
                    Instant.now().plus(1, ChronoUnit.HOURS));

            priorityQueue.addOrUpdate(post1);
            priorityQueue.addOrUpdate(post2);
            priorityQueue.addOrUpdate(post3);

            assertEquals(3, priorityQueue.size());
            assertTrue(priorityQueue.contains(1L));
            assertTrue(priorityQueue.contains(2L));
            assertTrue(priorityQueue.contains(3L));
        }

        @Test
        void insert_rejectsDraftPost() {
            FoodPost post = createPost(1L, PostStatus.DRAFT, 10,
                    Instant.now().plus(2, ChronoUnit.HOURS));

            priorityQueue.addOrUpdate(post);

            assertEquals(0, priorityQueue.size());
            assertFalse(priorityQueue.contains(1L));
        }

        @Test
        void insert_rejectsHiddenPost() {
            FoodPost post = createPost(1L, PostStatus.HIDDEN, 10,
                    Instant.now().plus(2, ChronoUnit.HOURS));

            priorityQueue.addOrUpdate(post);

            assertEquals(0, priorityQueue.size());
        }

        @Test
        void insert_rejectsExpiredPost() {
            FoodPost post = createPost(1L, PostStatus.AVAILABLE, 10,
                    Instant.now().minus(1, ChronoUnit.HOURS));

            priorityQueue.addOrUpdate(post);

            assertEquals(0, priorityQueue.size());
        }

        @Test
        void insert_rejectsOutOfStockPost() {
            FoodPost post = createPost(1L, PostStatus.OUT_OF_STOCK, 0,
                    Instant.now().plus(2, ChronoUnit.HOURS));

            priorityQueue.addOrUpdate(post);

            assertEquals(0, priorityQueue.size());
        }

        @Test
        void insert_rejectsZeroQuantityPost() {
            FoodPost post = createPost(1L, PostStatus.AVAILABLE, 0,
                    Instant.now().plus(2, ChronoUnit.HOURS));

            priorityQueue.addOrUpdate(post);

            assertEquals(0, priorityQueue.size());
        }

        @Test
        void insert_rejectsDeletedPost() {
            FoodPost post = createPost(1L, PostStatus.DELETED, 5,
                    Instant.now().plus(2, ChronoUnit.HOURS));

            priorityQueue.addOrUpdate(post);

            assertEquals(0, priorityQueue.size());
        }
    }

    @Nested
    class RemoveTests {

        @Test
        void remove_existingPost_removedSuccessfully() {
            FoodPost post = createPost(1L, PostStatus.AVAILABLE, 10,
                    Instant.now().plus(2, ChronoUnit.HOURS));
            priorityQueue.addOrUpdate(post);

            priorityQueue.remove(1L);

            assertEquals(0, priorityQueue.size());
            assertFalse(priorityQueue.contains(1L));
        }

        @Test
        void remove_nonExistingPost_noEffect() {
            FoodPost post = createPost(1L, PostStatus.AVAILABLE, 10,
                    Instant.now().plus(2, ChronoUnit.HOURS));
            priorityQueue.addOrUpdate(post);

            priorityQueue.remove(999L);

            assertEquals(1, priorityQueue.size());
            assertTrue(priorityQueue.contains(1L));
        }

        @Test
        void remove_emptyQueue_noError() {
            assertDoesNotThrow(() -> priorityQueue.remove(1L));
            assertEquals(0, priorityQueue.size());
        }

        @Test
        void remove_whenHidden_removedFromQueue() {
            FoodPost post = createPost(1L, PostStatus.AVAILABLE, 10,
                    Instant.now().plus(2, ChronoUnit.HOURS));
            priorityQueue.addOrUpdate(post);
            assertEquals(1, priorityQueue.size());

            post.setPostStatus(PostStatus.HIDDEN);
            priorityQueue.addOrUpdate(post);

            assertEquals(0, priorityQueue.size());
        }

        @Test
        void remove_whenCancelled_removedFromQueue() {
            FoodPost post = createPost(1L, PostStatus.AVAILABLE, 10,
                    Instant.now().plus(2, ChronoUnit.HOURS));
            priorityQueue.addOrUpdate(post);

            post.setPostStatus(PostStatus.DELETED);
            priorityQueue.addOrUpdate(post);

            assertEquals(0, priorityQueue.size());
        }

        @Test
        void remove_whenOutOfStock_removedFromQueue() {
            FoodPost post = createPost(1L, PostStatus.AVAILABLE, 10,
                    Instant.now().plus(2, ChronoUnit.HOURS));
            priorityQueue.addOrUpdate(post);

            post.setAvailableQuantity(0);
            post.setPostStatus(PostStatus.OUT_OF_STOCK);
            priorityQueue.addOrUpdate(post);

            assertEquals(0, priorityQueue.size());
        }

        @Test
        void poll_removesAndReturnsHighestPriority() {
            FoodPost post1 = createPost(1L, PostStatus.AVAILABLE, 10,
                    Instant.now().plus(5, ChronoUnit.HOURS));
            FoodPost post2 = createPost(2L, PostStatus.AVAILABLE, 10,
                    Instant.now().plus(1, ChronoUnit.HOURS));
            priorityQueue.addOrUpdate(post1);
            priorityQueue.addOrUpdate(post2);

            FoodPostPriorityEntry polled = priorityQueue.poll();

            assertNotNull(polled);
            assertEquals(2L, polled.foodPostId());
            assertEquals(1, priorityQueue.size());
            assertFalse(priorityQueue.contains(2L));
        }
    }

    @Nested
    class UpdatePriorityTests {

        @Test
        void update_whenTimeChanges_priorityRecalculated() {
            FoodPost post = createPost(1L, PostStatus.AVAILABLE, 10,
                    Instant.now().plus(5, ChronoUnit.HOURS));
            priorityQueue.addOrUpdate(post);

            FoodPostPriorityEntry before = priorityQueue.peek();
            assertNotNull(before);

            post.setExpiresAt(Instant.now().plus(30, ChronoUnit.MINUTES));
            priorityQueue.addOrUpdate(post);

            FoodPostPriorityEntry after = priorityQueue.peek();
            assertNotNull(after);
            assertTrue(after.remainingSeconds() < before.remainingSeconds());
            assertTrue(after.urgency() > before.urgency());
        }

        @Test
        void update_whenQuantityChanges_priorityRecalculated() {
            FoodPost post = createPost(1L, PostStatus.AVAILABLE, 10,
                    Instant.now().plus(2, ChronoUnit.HOURS));
            priorityQueue.addOrUpdate(post);

            FoodPostPriorityEntry before = priorityQueue.peek();
            assertNotNull(before);
            assertEquals(10, before.availableQuantity());

            post.setAvailableQuantity(3);
            priorityQueue.addOrUpdate(post);

            FoodPostPriorityEntry after = priorityQueue.peek();
            assertNotNull(after);
            assertEquals(3, after.availableQuantity());
        }

        @Test
        void update_replacesExistingEntry_noDuplicates() {
            FoodPost post = createPost(1L, PostStatus.AVAILABLE, 10,
                    Instant.now().plus(2, ChronoUnit.HOURS));
            priorityQueue.addOrUpdate(post);
            priorityQueue.addOrUpdate(post);
            priorityQueue.addOrUpdate(post);

            assertEquals(1, priorityQueue.size());
        }

        @Test
        void update_becomesInvalid_removedFromQueue() {
            FoodPost post = createPost(1L, PostStatus.AVAILABLE, 10,
                    Instant.now().plus(2, ChronoUnit.HOURS));
            priorityQueue.addOrUpdate(post);
            assertEquals(1, priorityQueue.size());

            post.setPostStatus(PostStatus.EXPIRED);
            priorityQueue.addOrUpdate(post);
            assertEquals(0, priorityQueue.size());
        }
    }

    @Nested
    class OrderingTests {

        @Test
        void ordering_byTimeRemaining_shortestFirst() {
            Instant now = Instant.now();
            FoodPost far = createPost(1L, PostStatus.AVAILABLE, 10,
                    now.plus(24, ChronoUnit.HOURS));
            FoodPost near = createPost(2L, PostStatus.AVAILABLE, 10,
                    now.plus(1, ChronoUnit.HOURS));
            FoodPost mid = createPost(3L, PostStatus.AVAILABLE, 10,
                    now.plus(6, ChronoUnit.HOURS));

            priorityQueue.addOrUpdate(far);
            priorityQueue.addOrUpdate(near);
            priorityQueue.addOrUpdate(mid);

            List<FoodPostPriorityEntry> ordered = priorityQueue.getOrderedEntries();
            assertEquals(3, ordered.size());
            assertEquals(2L, ordered.get(0).foodPostId());
            assertEquals(3L, ordered.get(1).foodPostId());
            assertEquals(1L, ordered.get(2).foodPostId());
        }

        @Test
        void ordering_tieBreak_lowerQuantityFirst() {
            Instant sameExpiry = Instant.now().plus(3, ChronoUnit.HOURS);
            Instant sameCreated = Instant.now().minus(1, ChronoUnit.HOURS);

            FoodPost lotsOfStock = createPost(1L, PostStatus.AVAILABLE, 50, sameExpiry);
            lotsOfStock.setCreatedAt(sameCreated);
            FoodPost lowStock = createPost(2L, PostStatus.AVAILABLE, 3, sameExpiry);
            lowStock.setCreatedAt(sameCreated);

            priorityQueue.addOrUpdate(lotsOfStock);
            priorityQueue.addOrUpdate(lowStock);

            List<FoodPostPriorityEntry> ordered = priorityQueue.getOrderedEntries();
            assertEquals(2, ordered.size());
            assertEquals(2L, ordered.get(0).foodPostId());
            assertEquals(1L, ordered.get(1).foodPostId());
        }

        @Test
        void ordering_tieBreak_earlierCreatedFirst() {
            Instant sameExpiry = Instant.now().plus(3, ChronoUnit.HOURS);
            Instant earlier = Instant.now().minus(5, ChronoUnit.HOURS);
            Instant later = Instant.now().minus(1, ChronoUnit.HOURS);

            FoodPost olderPost = createPost(1L, PostStatus.AVAILABLE, 10, sameExpiry);
            olderPost.setCreatedAt(earlier);
            FoodPost newerPost = createPost(2L, PostStatus.AVAILABLE, 10, sameExpiry);
            newerPost.setCreatedAt(later);

            priorityQueue.addOrUpdate(olderPost);
            priorityQueue.addOrUpdate(newerPost);

            List<FoodPostPriorityEntry> ordered = priorityQueue.getOrderedEntries();
            assertEquals(2, ordered.size());
            assertEquals(1L, ordered.get(0).foodPostId());
            assertEquals(2L, ordered.get(1).foodPostId());
        }

        @Test
        void ordering_tieBreak_smallerIdFirst() {
            Instant sameExpiry = Instant.now().plus(3, ChronoUnit.HOURS);
            Instant sameCreated = Instant.now().minus(2, ChronoUnit.HOURS);

            FoodPost post100 = createPost(100L, PostStatus.AVAILABLE, 10, sameExpiry);
            post100.setCreatedAt(sameCreated);
            FoodPost post1 = createPost(1L, PostStatus.AVAILABLE, 10, sameExpiry);
            post1.setCreatedAt(sameCreated);

            priorityQueue.addOrUpdate(post100);
            priorityQueue.addOrUpdate(post1);

            List<FoodPostPriorityEntry> ordered = priorityQueue.getOrderedEntries();
            assertEquals(2, ordered.size());
            assertEquals(1L, ordered.get(0).foodPostId());
            assertEquals(100L, ordered.get(1).foodPostId());
        }

        @Test
        void ordering_complexScenario_correctOrder() {
            Instant now = Instant.now();

            FoodPost postA = createPost(1L, PostStatus.AVAILABLE, 5,
                    now.plus(1, ChronoUnit.HOURS));
            postA.setCreatedAt(now.minus(3, ChronoUnit.HOURS));

            FoodPost postB = createPost(2L, PostStatus.AVAILABLE, 10,
                    now.plus(1, ChronoUnit.HOURS));
            postB.setCreatedAt(now.minus(3, ChronoUnit.HOURS));

            FoodPost postC = createPost(3L, PostStatus.AVAILABLE, 2,
                    now.plus(6, ChronoUnit.HOURS));
            postC.setCreatedAt(now.minus(1, ChronoUnit.HOURS));

            FoodPost postD = createPost(4L, PostStatus.AVAILABLE, 50,
                    now.plus(24, ChronoUnit.HOURS));
            postD.setCreatedAt(now.minus(5, ChronoUnit.HOURS));

            priorityQueue.addOrUpdate(postD);
            priorityQueue.addOrUpdate(postB);
            priorityQueue.addOrUpdate(postC);
            priorityQueue.addOrUpdate(postA);

            List<FoodPostPriorityEntry> ordered = priorityQueue.getOrderedEntries();
            assertEquals(4, ordered.size());
            assertEquals(1L, ordered.get(0).foodPostId());
            assertEquals(2L, ordered.get(1).foodPostId());
            assertEquals(3L, ordered.get(2).foodPostId());
            assertEquals(4L, ordered.get(3).foodPostId());
        }

        @Test
        void peek_returnsHighestPriority_withoutRemoving() {
            Instant now = Instant.now();
            FoodPost far = createPost(1L, PostStatus.AVAILABLE, 10,
                    now.plus(24, ChronoUnit.HOURS));
            FoodPost near = createPost(2L, PostStatus.AVAILABLE, 10,
                    now.plus(1, ChronoUnit.HOURS));

            priorityQueue.addOrUpdate(far);
            priorityQueue.addOrUpdate(near);

            FoodPostPriorityEntry peeked = priorityQueue.peek();
            assertNotNull(peeked);
            assertEquals(2L, peeked.foodPostId());
            assertEquals(2, priorityQueue.size());
        }
    }

    @Nested
    class UrgencyTests {

        @Test
        void urgency_atZeroRemaining_isMaximal() {
            FoodPostPriorityEntry entry = new FoodPostPriorityEntry(
                    1L, 0, 10, Instant.now(), Instant.now().minus(1, ChronoUnit.HOURS));
            assertEquals(1.0, entry.urgency(), 0.001);
        }

        @Test
        void urgency_atOneHourRemaining_isAboutHalf() {
            FoodPostPriorityEntry entry = new FoodPostPriorityEntry(
                    1L, 3600, 10, Instant.now().plus(1, ChronoUnit.HOURS),
                    Instant.now().minus(1, ChronoUnit.HOURS));
            assertEquals(0.5, entry.urgency(), 0.001);
        }

        @Test
        void urgency_decreasesWithMoreTime() {
            FoodPostPriorityEntry near = new FoodPostPriorityEntry(
                    1L, 1800, 10, Instant.now().plus(30, ChronoUnit.MINUTES),
                    Instant.now());
            FoodPostPriorityEntry far = new FoodPostPriorityEntry(
                    2L, 86400, 10, Instant.now().plus(24, ChronoUnit.HOURS),
                    Instant.now());

            assertTrue(near.urgency() > far.urgency());
        }

        @Test
        void urgency_fromFoodPost_calculatedCorrectly() {
            FoodPost post = createPost(1L, PostStatus.AVAILABLE, 10,
                    Instant.now().plus(2, ChronoUnit.HOURS));
            Instant now = Instant.now();
            FoodPostPriorityEntry entry = FoodPostPriorityEntry.fromFoodPost(post, now);

            assertTrue(entry.remainingSeconds() > 0);
            assertTrue(entry.urgency() > 0.0 && entry.urgency() < 1.0);
        }
    }

    @Nested
    class EvictionTests {

        @Test
        void evictExpired_removesExpiredEntries() {
            FoodPost almostExpired = createPost(1L, PostStatus.AVAILABLE, 10,
                    Instant.now().plus(1, ChronoUnit.SECONDS));
            FoodPost valid = createPost(2L, PostStatus.AVAILABLE, 10,
                    Instant.now().plus(5, ChronoUnit.HOURS));

            priorityQueue.addOrUpdate(almostExpired);
            priorityQueue.addOrUpdate(valid);
            assertEquals(2, priorityQueue.size());

            priorityQueue.remove(1L);
            assertEquals(1, priorityQueue.size());
            assertTrue(priorityQueue.contains(2L));
        }

        @Test
        void getOrderedEntries_filtersExpiredEntries() {
            FoodPost valid = createPost(1L, PostStatus.AVAILABLE, 10,
                    Instant.now().plus(5, ChronoUnit.HOURS));
            priorityQueue.addOrUpdate(valid);

            List<FoodPostPriorityEntry> entries = priorityQueue.getOrderedEntries();
            assertFalse(entries.isEmpty());
            assertEquals(1L, entries.get(0).foodPostId());
        }
    }

    @Nested
    class RebuildTests {

        @Test
        void rebuild_loadsFromDatabase() {
            Instant now = Instant.now();
            FoodPost post1 = createPost(1L, PostStatus.AVAILABLE, 10,
                    now.plus(2, ChronoUnit.HOURS));
            FoodPost post2 = createPost(2L, PostStatus.AVAILABLE, 5,
                    now.plus(1, ChronoUnit.HOURS));

            when(foodPostRepository.findAllByPostStatusAndExpiresAtAfterAndAvailableQuantityGreaterThan(
                    eq(PostStatus.AVAILABLE), any(Instant.class), eq(0)))
                    .thenReturn(List.of(post1, post2));

            priorityQueue.rebuild();

            assertEquals(2, priorityQueue.size());
            assertTrue(priorityQueue.contains(1L));
            assertTrue(priorityQueue.contains(2L));
        }

        @Test
        void rebuild_clearsExistingEntries() {
            FoodPost manuallyAdded = createPost(99L, PostStatus.AVAILABLE, 10,
                    Instant.now().plus(5, ChronoUnit.HOURS));
            priorityQueue.addOrUpdate(manuallyAdded);
            assertEquals(1, priorityQueue.size());

            when(foodPostRepository.findAllByPostStatusAndExpiresAtAfterAndAvailableQuantityGreaterThan(
                    eq(PostStatus.AVAILABLE), any(Instant.class), eq(0)))
                    .thenReturn(List.of());

            priorityQueue.rebuild();

            assertEquals(0, priorityQueue.size());
            assertFalse(priorityQueue.contains(99L));
        }

        @Test
        void rebuild_maintainsCorrectOrdering() {
            Instant now = Instant.now();
            FoodPost postFar = createPost(1L, PostStatus.AVAILABLE, 10,
                    now.plus(24, ChronoUnit.HOURS));
            FoodPost postNear = createPost(2L, PostStatus.AVAILABLE, 10,
                    now.plus(1, ChronoUnit.HOURS));

            when(foodPostRepository.findAllByPostStatusAndExpiresAtAfterAndAvailableQuantityGreaterThan(
                    eq(PostStatus.AVAILABLE), any(Instant.class), eq(0)))
                    .thenReturn(List.of(postFar, postNear));

            priorityQueue.rebuild();

            List<FoodPostPriorityEntry> ordered = priorityQueue.getOrderedEntries();
            assertEquals(2, ordered.size());
            assertEquals(2L, ordered.get(0).foodPostId());
            assertEquals(1L, ordered.get(1).foodPostId());
        }
    }

    @Nested
    class EdgeCaseTests {

        @Test
        void peek_emptyQueue_returnsNull() {
            assertNull(priorityQueue.peek());
        }

        @Test
        void poll_emptyQueue_returnsNull() {
            assertNull(priorityQueue.poll());
        }

        @Test
        void size_emptyQueue_returnsZero() {
            assertEquals(0, priorityQueue.size());
        }

        @Test
        void contains_emptyQueue_returnsFalse() {
            assertFalse(priorityQueue.contains(1L));
        }

        @Test
        void getOrderedEntries_emptyQueue_returnsEmptyList() {
            List<FoodPostPriorityEntry> entries = priorityQueue.getOrderedEntries();
            assertNotNull(entries);
            assertTrue(entries.isEmpty());
        }

        @Test
        void addOrUpdate_thenPoll_allEntries() {
            Instant now = Instant.now();
            for (int i = 1; i <= 5; i++) {
                FoodPost post = createPost((long) i, PostStatus.AVAILABLE, i * 2,
                        now.plus(i, ChronoUnit.HOURS));
                priorityQueue.addOrUpdate(post);
            }
            assertEquals(5, priorityQueue.size());

            long previousRemaining = -1;
            int polled = 0;
            FoodPostPriorityEntry entry;
            while ((entry = priorityQueue.poll()) != null) {
                assertTrue(entry.remainingSeconds() >= previousRemaining);
                previousRemaining = entry.remainingSeconds();
                polled++;
            }
            assertEquals(5, polled);
            assertEquals(0, priorityQueue.size());
        }
    }

    private FoodPost createPost(Long id, PostStatus status, int availableQty, Instant expiresAt) {
        Category category = new Category();
        category.setId(1L);
        category.setName("Test");

        BusinessProfile bp = new BusinessProfile();
        bp.setId(1L);

        FoodPost post = FoodPost.builder()
                .name("Test Post " + id)
                .description("Description")
                .totalQuantity(Math.max(availableQty, 10))
                .availableQuantity(availableQty)
                .unitPrice(BigDecimal.ZERO)
                .postType(PostType.FREE)
                .postStatus(status)
                .expiresAt(expiresAt)
                .pickupAddress("123 ABC")
                .pickupStartAt(Instant.now().plus(1, ChronoUnit.HOURS))
                .pickupEndAt(Instant.now().plus(3, ChronoUnit.HOURS))
                .build();
        post.setId(id);
        post.setCreatedAt(Instant.now().minus(1, ChronoUnit.HOURS));
        post.setCategory(category);
        post.setBusinessProfile(bp);
        return post;
    }
}
