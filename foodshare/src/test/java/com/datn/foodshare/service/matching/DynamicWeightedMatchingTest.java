package com.datn.foodshare.service.matching;

import com.datn.foodshare.domain.entity.*;
import com.datn.foodshare.util.constant.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DynamicWeightedMatchingTest {
    private static final Instant NOW = Instant.parse("2030-01-01T00:00:00Z");
    static User candidate(long id, String lat) {
        return User.builder().id(id).fullName("Receiver " + id).role(Role.RECIPIENT)
                .active(true).profileCompleted(true).latitude(new BigDecimal(lat))
                .longitude(new BigDecimal("106.7009")).build();
    }
    static FoodPost post(long id, int quantity) {
        User supplier = candidate(1, "10.7769"); supplier.setRole(Role.SUPPLIER);
        return FoodPost.builder().id(id).businessProfile(BusinessProfile.builder().id(2L).user(supplier).build())
                .availableQuantity(quantity).postStatus(PostStatus.AVAILABLE).expiresAt(NOW.plusSeconds(7200))
                .pickupEndAt(NOW.plusSeconds(3600)).createdAt(NOW.minusSeconds(10)).build();
    }
    @Test void readsWeightsFromGraphAndUpdatesUrgencyWithoutDatabase() {
        DynamicMatchingGraph graph = new DynamicMatchingGraph();
        User receiver = candidate(20, "10.7815");
        graph.rebuild(List.of(post(10, 5)), List.of(receiver), Map.of(20L, 0L));
        var first = graph.snapshotForCandidate(20, NOW).posts().getFirst().scores().getFirst();
        var later = graph.snapshotForCandidate(20, NOW.plusSeconds(1800)).posts().getFirst().scores().getFirst();
        assertTrue(later.score() > first.score());
        assertEquals(first.distanceKm(), later.distanceKm());
        assertEquals(0, first.activeOrderCount());
        assertEquals(List.of(10L), graph.getFoodPostIdsForCandidate(20).stream().toList());
    }
    @Test void movingReceiverRemovesOnlyItsRelationsAndCapacityCanRecover() {
        DynamicMatchingGraph graph = new DynamicMatchingGraph();
        User changed = candidate(20, "10.7815"), other = candidate(21, "10.7815");
        graph.rebuild(List.of(post(10, 5)), List.of(changed, other), Map.of(20L, 2L));
        assertTrue(graph.hasEdge(10, 20)); // Structural edge retained while at capacity.
        assertTrue(graph.snapshotForCandidate(20, NOW).posts().getFirst().scores().isEmpty());
        graph.synchronizeCandidate(changed, 1);
        assertEquals(1, graph.snapshotForCandidate(20, NOW).posts().getFirst().scores().getFirst().activeOrderCount());
        changed.setLatitude(new BigDecimal("12.0"));
        graph.synchronizeCandidate(changed, 0);
        assertFalse(graph.hasEdge(10, 20)); assertTrue(graph.hasEdge(10, 21));
        changed.setLatitude(new BigDecimal("10.7815"));
        graph.synchronizeCandidate(changed, 0);
        assertTrue(graph.hasEdge(10, 20));
    }
    @Test void queryRejectsExpiredPickupImmediatelyWithoutWaitingForScheduler() {
        DynamicMatchingGraph graph = new DynamicMatchingGraph();
        graph.rebuild(List.of(post(10, 5)), List.of(candidate(20, "10.7815")), Map.of());
        assertTrue(graph.snapshot(NOW.plusSeconds(3600)).posts().isEmpty());
        graph.invalidateUnavailableFoodPosts(NOW.plusSeconds(3600));
        assertEquals(0, graph.edgeCount());
    }
    @Test void snapshotsAreIndependentOfMutatedEntitiesAndLaterEvents() {
        DynamicMatchingGraph graph = new DynamicMatchingGraph();
        User receiver = candidate(20, "10.7815");
        FoodPost post = post(10, 5);
        graph.rebuild(List.of(post), List.of(receiver), Map.of());
        var before = graph.snapshot(NOW);
        receiver.setActive(false); post.setAvailableQuantity(0);
        assertEquals(5, graph.snapshot(NOW).posts().getFirst().post().availableQuantity());
        graph.synchronizeCandidate(receiver, 0); graph.synchronizeFoodPost(post);
        assertEquals(5, before.posts().getFirst().post().availableQuantity());
        assertEquals(1, before.posts().getFirst().scores().size());
        assertTrue(graph.snapshot(NOW).posts().isEmpty());
    }
    @Test void supplierMoveRecalculatesEdgesInMemory() {
        DynamicMatchingGraph graph = new DynamicMatchingGraph();
        FoodPost p = post(10, 5);
        graph.rebuild(List.of(p), List.of(candidate(20, "10.7815")), Map.of());
        User supplier = p.getBusinessProfile().getUser();
        supplier.setLatitude(new BigDecimal("12.0"));
        graph.synchronizeSupplier(supplier);
        assertFalse(graph.hasEdge(10, 20));
    }
    @Test void hiddenPostsExcludedAndUninitializedOrFailedGraphCannotServe() {
        DynamicMatchingGraph graph = new DynamicMatchingGraph();
        assertThrows(IllegalStateException.class, () -> graph.snapshot(NOW));
        FoodPost hidden = post(10, 5); hidden.setHiddenByAdmin(true);
        graph.rebuild(List.of(hidden), List.of(candidate(20, "10.7815")), Map.of());
        assertTrue(graph.snapshot(NOW).posts().isEmpty());
        graph.markUnavailable();
        assertThrows(IllegalStateException.class, () -> graph.snapshot(NOW));
    }
}
