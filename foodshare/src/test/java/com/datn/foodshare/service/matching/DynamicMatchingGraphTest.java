package com.datn.foodshare.service.matching;

import com.datn.foodshare.domain.entity.BusinessProfile;
import com.datn.foodshare.domain.entity.FoodPost;
import com.datn.foodshare.domain.entity.User;
import com.datn.foodshare.util.constant.PostStatus;
import com.datn.foodshare.util.constant.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DynamicMatchingGraphTest {

    private DynamicMatchingGraph graph;
    private Instant now;
    private FoodPost foodPost;
    private User candidate;

    @BeforeEach
    void setUp() {
        graph = new DynamicMatchingGraph();
        now = Instant.parse("2026-08-19T06:00:00Z");
        candidate = candidate(20L);
        foodPost = foodPost(10L, now.plusSeconds(3600));
    }

    @Test
    void addsAndUpdatesNodesAndCandidateEdge() {
        graph.addOrUpdateFoodPost(foodPost, List.of(candidate), now);

        assertEquals(1, graph.foodPostCount());
        assertEquals(1, graph.candidateCount());
        assertEquals(1, graph.edgeCount());
        assertTrue(graph.hasEdge(10L, 20L));

        foodPost.setAvailableQuantity(3);
        candidate.setLatitude(new BigDecimal("10.7800"));
        graph.addOrUpdateCandidate(candidate);
        graph.addOrUpdateFoodPost(foodPost, List.of(candidate), now);

        assertEquals(3, graph.getFoodPost(10L).orElseThrow().availableQuantity());
        assertEquals(new BigDecimal("10.7800"), graph.getCandidate(20L).orElseThrow().latitude());
        assertEquals(1, graph.edgeCount());
    }

    @Test
    void replacesCandidateRelationsWithoutLeavingStaleEdges() {
        User secondCandidate = candidate(21L);
        graph.addOrUpdateFoodPost(foodPost, List.of(candidate), now);

        graph.replaceCandidateEdges(10L, List.of(secondCandidate));

        assertFalse(graph.hasEdge(10L, 20L));
        assertTrue(graph.hasEdge(10L, 21L));
        assertEquals(1, graph.edgeCount());
    }

    @Test
    void removingCandidateInvalidatesAllOfItsEdges() {
        FoodPost secondPost = foodPost(11L, now.plusSeconds(7200));
        graph.addOrUpdateFoodPost(foodPost, List.of(candidate), now);
        graph.addOrUpdateFoodPost(secondPost, List.of(candidate), now);

        graph.removeCandidate(20L);

        assertTrue(graph.getCandidate(20L).isEmpty());
        assertFalse(graph.hasEdge(10L, 20L));
        assertFalse(graph.hasEdge(11L, 20L));
        assertEquals(0, graph.edgeCount());
    }

    @Test
    void outOfStockFoodPostIsRemovedWithItsEdges() {
        graph.addOrUpdateFoodPost(foodPost, List.of(candidate), now);
        foodPost.setAvailableQuantity(0);
        foodPost.setPostStatus(PostStatus.OUT_OF_STOCK);

        graph.addOrUpdateFoodPost(foodPost, List.of(candidate), now);

        assertTrue(graph.getFoodPost(10L).isEmpty());
        assertFalse(graph.hasEdge(10L, 20L));
        assertEquals(0, graph.edgeCount());
    }

    @Test
    void hiddenFoodPostIsRemovedWithItsEdges() {
        graph.addOrUpdateFoodPost(foodPost, List.of(candidate), now);
        foodPost.setPostStatus(PostStatus.HIDDEN);

        graph.addOrUpdateFoodPost(foodPost, List.of(candidate), now);

        assertTrue(graph.getFoodPost(10L).isEmpty());
        assertFalse(graph.hasEdge(10L, 20L));
    }

    @Test
    void updatingOneCandidateRelationPreservesOtherEdges() {
        User secondCandidate = candidate(21L);
        graph.addOrUpdateFoodPost(foodPost, List.of(candidate, secondCandidate), now);

        graph.updateCandidateRelation(10L, candidate, false);

        assertFalse(graph.hasEdge(10L, 20L));
        assertTrue(graph.hasEdge(10L, 21L));
        assertEquals(1, graph.edgeCount());
    }

    @Test
    void expirationInvalidatesFoodPostAndCandidateEdges() {
        graph.addOrUpdateFoodPost(foodPost, List.of(candidate), now);

        graph.invalidateUnavailableFoodPosts(now.plusSeconds(3601));

        assertTrue(graph.getFoodPost(10L).isEmpty());
        assertEquals(0, graph.edgeCount());
    }

    @Test
    void replaceAllDiscardsStaleRuntimeStateDuringDatabaseRebuild() {
        graph.addOrUpdateFoodPost(foodPost, List.of(candidate), now);
        FoodPost currentPost = foodPost(11L, now.plusSeconds(7200));
        User currentCandidate = candidate(21L);

        graph.replaceAll(
                List.of(currentPost),
                List.of(currentCandidate),
                List.of(new DynamicMatchingGraph.CandidateEdge(11L, 21L)),
                now
        );

        assertTrue(graph.getFoodPost(10L).isEmpty());
        assertTrue(graph.getCandidate(20L).isEmpty());
        assertTrue(graph.hasEdge(11L, 21L));
    }

    @Test
    void failedRebuildKeepsPreviousGraphState() {
        graph.addOrUpdateFoodPost(foodPost, List.of(candidate), now);
        FoodPost invalidSnapshot = foodPost(11L, now.plusSeconds(7200));
        invalidSnapshot.setBusinessProfile(null);

        org.junit.jupiter.api.Assertions.assertThrows(
                NullPointerException.class,
                () -> graph.replaceAll(List.of(invalidSnapshot), List.of(), List.of(), now)
        );

        assertTrue(graph.hasEdge(10L, 20L));
        assertEquals(1, graph.foodPostCount());
    }

    private FoodPost foodPost(long id, Instant expiresAt) {
        User supplier = User.builder()
                .id(1L)
                .role(Role.SUPPLIER)
                .latitude(new BigDecimal("10.7626"))
                .longitude(new BigDecimal("106.6601"))
                .build();
        BusinessProfile profile = BusinessProfile.builder().id(2L).user(supplier).build();
        return FoodPost.builder()
                .id(id)
                .businessProfile(profile)
                .availableQuantity(5)
                .postStatus(PostStatus.AVAILABLE)
                .expiresAt(expiresAt)
                .pickupAddress("Quận 1")
                .build();
    }

    private User candidate(long id) {
        return User.builder()
                .id(id)
                .role(Role.RECIPIENT)
                .active(true)
                .profileCompleted(true)
                .latitude(new BigDecimal("10.7700"))
                .longitude(new BigDecimal("106.6700"))
                .build();
    }
}
