package com.datn.foodshare.service.matching;

import com.datn.foodshare.domain.entity.BusinessProfile;
import com.datn.foodshare.domain.entity.FoodPost;
import com.datn.foodshare.domain.entity.User;
import com.datn.foodshare.repository.FoodPostRepository;
import com.datn.foodshare.repository.UserRepository;
import com.datn.foodshare.util.constant.PostStatus;
import com.datn.foodshare.util.constant.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringJUnitConfig(DynamicMatchingGraphSynchronizationTest.Config.class)
class DynamicMatchingGraphSynchronizationTest {

    @Autowired
    private DynamicMatchingGraph graph;
    @Autowired
    private DynamicMatchingGraphSynchronizer synchronizer;
    @Autowired
    private FoodPostRepository foodPostRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private MatchingCandidateFilter candidateFilter;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void setUp() {
        graph.replaceAll(List.of(), List.of(), List.of());
        reset(foodPostRepository, userRepository, candidateFilter);
    }

    @Test
    void committedTransactionSynchronizesFromDatabase() {
        FoodPost post = foodPost();
        User candidate = candidate();
        when(foodPostRepository.findByIdForMatching(10L)).thenReturn(Optional.of(post));
        when(candidateFilter.filterCandidates(post)).thenReturn(List.of(candidate));

        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                synchronizer.foodPostChangedAfterCommit(10L));

        assertTrue(graph.getFoodPost(10L).isPresent());
        assertTrue(graph.hasEdge(10L, 20L));
        verify(foodPostRepository).findByIdForMatching(10L);
    }

    @Test
    void rolledBackTransactionDoesNotMutateGraph() {
        FoodPost post = foodPost();
        when(foodPostRepository.findByIdForMatching(10L)).thenReturn(Optional.of(post));

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            synchronizer.foodPostChangedAfterCommit(10L);
            status.setRollbackOnly();
        });

        assertFalse(graph.getFoodPost(10L).isPresent());
    }

    @Test
    void eventPublishedWithoutTransactionDoesNotMutateGraph() {
        FoodPost post = foodPost();
        when(foodPostRepository.findByIdForMatching(10L)).thenReturn(Optional.of(post));

        synchronizer.foodPostChangedAfterCommit(10L);

        assertFalse(graph.getFoodPost(10L).isPresent());
    }

    @Test
    void supplierLocationChangeRefreshesOwnedFoodPostsAfterCommit() {
        FoodPost post = foodPost();
        User candidate = candidate();
        graph.addOrUpdateFoodPost(post, List.of(candidate));
        User supplier = post.getBusinessProfile().getUser();
        supplier.setLatitude(new BigDecimal("11.0000"));
        supplier.setLongitude(new BigDecimal("107.0000"));

        when(userRepository.findById(1L)).thenReturn(Optional.of(supplier));
        when(foodPostRepository.findAllBySupplierUserIdForMatching(1L)).thenReturn(List.of(post));
        when(candidateFilter.filterCandidates(post)).thenReturn(List.of(candidate));

        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                synchronizer.userChangedAfterCommit(1L));

        assertTrue(graph.hasEdge(10L, 20L));
        org.junit.jupiter.api.Assertions.assertEquals(
                new BigDecimal("11.0000"),
                graph.getFoodPost(10L).orElseThrow().supplierLatitude()
        );
    }

    @Test
    void receiverChangeUpdatesOnlyThatCandidatesRelations() {
        FoodPost post = foodPost();
        User changedCandidate = candidate();
        User unaffectedCandidate = candidate();
        unaffectedCandidate.setId(21L);
        graph.addOrUpdateFoodPost(post, List.of(changedCandidate, unaffectedCandidate));

        when(userRepository.findById(20L)).thenReturn(Optional.of(changedCandidate));
        when(candidateFilter.isGloballyEligibleCandidate(changedCandidate)).thenReturn(true);
        when(foodPostRepository.findAllForMatchingGraph(
                org.mockito.ArgumentMatchers.eq(PostStatus.AVAILABLE),
                org.mockito.ArgumentMatchers.any(Instant.class),
                org.mockito.ArgumentMatchers.eq(0)
        )).thenReturn(List.of(post));
        when(candidateFilter.findEligibleFoodPostIds(List.of(post), changedCandidate)).thenReturn(Set.of());

        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                synchronizer.userChangedAfterCommit(20L));

        assertFalse(graph.hasEdge(10L, 20L));
        assertTrue(graph.hasEdge(10L, 21L));
    }

    private FoodPost foodPost() {
        User supplier = User.builder()
                .id(1L)
                .role(Role.SUPPLIER)
                .latitude(new BigDecimal("10.7626"))
                .longitude(new BigDecimal("106.6601"))
                .build();
        BusinessProfile profile = BusinessProfile.builder().id(2L).user(supplier).build();
        return FoodPost.builder()
                .id(10L)
                .businessProfile(profile)
                .availableQuantity(5)
                .postStatus(PostStatus.AVAILABLE)
                .expiresAt(Instant.now().plus(1, ChronoUnit.DAYS))
                .pickupAddress("Quận 1")
                .pickupEndAt(Instant.now().plus(2, ChronoUnit.HOURS))
                .build();
    }

    private User candidate() {
        return User.builder()
                .id(20L)
                .role(Role.RECIPIENT)
                .active(true)
                .profileCompleted(true)
                .latitude(new BigDecimal("10.7700"))
                .longitude(new BigDecimal("106.6700"))
                .build();
    }

    @TestConfiguration
    @EnableTransactionManagement
    static class Config {

        @Bean
        DataSource dataSource() {
            return new DriverManagerDataSource("jdbc:h2:mem:graph-sync;DB_CLOSE_DELAY=-1", "sa", "");
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        DynamicMatchingGraph dynamicMatchingGraph() {
            return new DynamicMatchingGraph();
        }

        @Bean
        FoodPostRepository foodPostRepository() {
            return mock(FoodPostRepository.class);
        }

        @Bean
        UserRepository userRepository() {
            return mock(UserRepository.class);
        }

        @Bean
        MatchingCandidateFilter matchingCandidateFilter() {
            return mock(MatchingCandidateFilter.class);
        }

        @Bean
        DynamicMatchingGraphSynchronizer dynamicMatchingGraphSynchronizer(
                DynamicMatchingGraph graph,
                FoodPostRepository foodPostRepository,
                UserRepository userRepository,
                MatchingCandidateFilter candidateFilter,
                org.springframework.context.ApplicationEventPublisher eventPublisher
        ) {
            return new DynamicMatchingGraphSynchronizer(
                    graph,
                    foodPostRepository,
                    userRepository,
                    candidateFilter,
                    eventPublisher
            );
        }
    }
}
