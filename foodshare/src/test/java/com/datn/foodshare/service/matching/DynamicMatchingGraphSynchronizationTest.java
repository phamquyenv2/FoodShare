package com.datn.foodshare.service.matching;

import com.datn.foodshare.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.jdbc.datasource.*;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringJUnitConfig(DynamicMatchingGraphSynchronizationTest.Config.class)
class DynamicMatchingGraphSynchronizationTest {
    @Autowired DynamicMatchingGraph graph;
    @Autowired DynamicMatchingGraphSynchronizer synchronizer;
    @Autowired FoodPostRepository posts;
    @Autowired UserRepository users;
    @Autowired MatchingCandidateFilter filter;
    @Autowired PlatformTransactionManager transactions;

    @BeforeEach void resetState() {
        reset(posts, users, filter);
        graph.replaceAll(List.of(), List.of(), List.of());
    }
    @Test void committedPostReadsOnlyChangedPostAndUsesCandidatesInRam() {
        var receiver = DynamicWeightedMatchingTest.candidate(20, "10.7815");
        var post = DynamicWeightedMatchingTest.post(10, 5);
        graph.addOrUpdateCandidate(receiver);
        when(posts.findByIdForMatching(10L)).thenReturn(Optional.of(post));
        new TransactionTemplate(transactions).executeWithoutResult(s -> synchronizer.foodPostChangedAfterCommit(10));
        assertTrue(graph.hasEdge(10, 20));
        verify(posts).findByIdForMatching(10L);
        verifyNoMoreInteractions(posts); verifyNoInteractions(filter);
    }
    @Test void rollbackDoesNotReadOrMutateGraph() {
        new TransactionTemplate(transactions).executeWithoutResult(s -> {
            synchronizer.foodPostChangedAfterCommit(10); s.setRollbackOnly();
        });
        assertEquals(0, graph.foodPostCount()); verifyNoInteractions(posts);
    }
    @Test void eventWithoutTransactionIsIgnored() {
        synchronizer.foodPostChangedAfterCommit(10);
        verifyNoInteractions(posts); assertEquals(0, graph.foodPostCount());
    }
    @Test void receiverUpdateDoesNotQueryPostsAndRefreshesCapacity() {
        var receiver = DynamicWeightedMatchingTest.candidate(20, "10.7815");
        graph.rebuild(List.of(DynamicWeightedMatchingTest.post(10, 5)), List.of(receiver), Map.of());
        when(users.findById(20L)).thenReturn(Optional.of(receiver));
        when(filter.isGloballyEligibleCandidate(receiver)).thenReturn(true);
        when(filter.loadActiveOrderCounts(List.of(20L))).thenReturn(Map.of(20L, 2L));
        new TransactionTemplate(transactions).executeWithoutResult(s -> synchronizer.userChangedAfterCommit(20));
        assertTrue(graph.snapshot(Instant.now()).posts().getFirst().scores().isEmpty());
        verifyNoInteractions(posts);
    }
    @Test void supplierUpdateUsesOwnedPostsInRam() {
        var post = DynamicWeightedMatchingTest.post(10, 5);
        graph.rebuild(List.of(post), List.of(DynamicWeightedMatchingTest.candidate(20, "10.7815")), Map.of());
        var supplier = post.getBusinessProfile().getUser();
        supplier.setLatitude(new java.math.BigDecimal("12.0"));
        when(users.findById(1L)).thenReturn(Optional.of(supplier));
        new TransactionTemplate(transactions).executeWithoutResult(s -> synchronizer.userChangedAfterCommit(1));
        assertFalse(graph.hasEdge(10, 20)); verifyNoInteractions(posts);
    }
    @Test void failedSynchronizationBlocksReadsUntilSuccessfulRebuild() {
        when(posts.findByIdForMatching(10L)).thenThrow(new IllegalStateException("DB down"));
        new TransactionTemplate(transactions).executeWithoutResult(s -> synchronizer.foodPostChangedAfterCommit(10));
        assertThrows(IllegalStateException.class, () -> graph.snapshot(Instant.now()));
        when(posts.findAllForMatchingGraph(any(), any(), eq(0))).thenReturn(List.of());
        when(filter.findGloballyEligibleCandidates()).thenReturn(List.of());
        when(filter.loadActiveOrderCounts(List.of())).thenReturn(Map.of());
        synchronizer.rebuildFromDatabase();
        assertTrue(graph.snapshot(Instant.now()).posts().isEmpty());
    }

    @TestConfiguration @EnableTransactionManagement
    static class Config {
        @Bean DataSource dataSource() { return new DriverManagerDataSource("jdbc:h2:mem:dynamic-graph;DB_CLOSE_DELAY=-1", "sa", ""); }
        @Bean PlatformTransactionManager transactionManager(DataSource ds) { return new DataSourceTransactionManager(ds); }
        @Bean DynamicMatchingGraph graph() { return new DynamicMatchingGraph(); }
        @Bean FoodPostRepository posts() { return mock(FoodPostRepository.class); }
        @Bean UserRepository users() { return mock(UserRepository.class); }
        @Bean MatchingCandidateFilter filter() { return mock(MatchingCandidateFilter.class); }
        @Bean DynamicMatchingGraphSynchronizer synchronizer(DynamicMatchingGraph g, FoodPostRepository p,
                UserRepository u, MatchingCandidateFilter f, ApplicationEventPublisher publisher) {
            return new DynamicMatchingGraphSynchronizer(g, p, u, f, publisher);
        }
    }
}
