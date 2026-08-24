package com.datn.foodshare.service.matching;

import com.datn.foodshare.domain.entity.BusinessProfile;
import com.datn.foodshare.domain.entity.FoodPost;
import com.datn.foodshare.domain.entity.User;
import com.datn.foodshare.repository.UserRepository;
import com.datn.foodshare.util.constant.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MatchingCandidateFilterTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private ReceiverCapacityService receiverCapacityService;

    @InjectMocks
    private MatchingCandidateFilter filter;

    private User supplier;
    private FoodPost foodPost;
    private Instant now;

    @BeforeEach
    void setUp() {
        now = Instant.now();

        supplier = new User();
        supplier.setId(100L);
        supplier.setRole(Role.SUPPLIER);
        supplier.setActive(true);
        supplier.setProfileCompleted(true);
        // Supplier location
        supplier.setLatitude(new BigDecimal("10.762622")); // HCMC
        supplier.setLongitude(new BigDecimal("106.660172"));

        BusinessProfile bp = new BusinessProfile();
        bp.setId(1L);
        bp.setUser(supplier);
        supplier.setBusinessProfile(bp);

        foodPost = new FoodPost();
        foodPost.setId(1L);
        foodPost.setBusinessProfile(bp);
        foodPost.setPickupEndAt(now.plus(2, ChronoUnit.HOURS)); // Valid time window
    }

    private User createCandidate(Long id, Role role, boolean active, boolean profileCompleted, String lat, String lng) {
        User u = new User();
        u.setId(id);
        u.setRole(role);
        u.setActive(active);
        u.setProfileCompleted(profileCompleted);
        if (lat != null && lng != null) {
            u.setLatitude(new BigDecimal(lat));
            u.setLongitude(new BigDecimal(lng));
        }
        return u;
    }

    @Test
    void filter_success_returnsValidCandidates() {
        // HCMC coordinates
        User valid1 = createCandidate(1L, Role.RECIPIENT, true, true, "10.772622", "106.670172"); // ~1.5km
        User valid2 = createCandidate(2L, Role.ORGANIZATION, true, true, "10.782622", "106.680172"); // ~3km

        when(userRepository.findByRoleIn(any())).thenReturn(List.of(valid1, valid2));
        when(receiverCapacityService.countActiveOrders(any())).thenReturn(Map.of());

        List<User> result = filter.filterCandidates(foodPost);

        assertEquals(2, result.size());
        assertTrue(result.contains(valid1));
        assertTrue(result.contains(valid2));
    }

    @Test
    void filter_excludesInactiveOrIncompleteProfiles() {
        User valid = createCandidate(1L, Role.RECIPIENT, true, true, "10.772622", "106.670172");
        User inactive = createCandidate(2L, Role.RECIPIENT, false, true, "10.772622", "106.670172");
        User incomplete = createCandidate(3L, Role.ORGANIZATION, true, false, "10.772622", "106.670172");

        when(userRepository.findByRoleIn(any())).thenReturn(List.of(valid, inactive, incomplete));
        when(receiverCapacityService.countActiveOrders(any())).thenReturn(Map.of());

        List<User> result = filter.filterCandidates(foodPost);

        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).getId());
    }

    @Test
    void filter_excludesSelfMatching() {
        // Same ID as supplier (100L)
        User self = createCandidate(100L, Role.ORGANIZATION, true, true, "10.762622", "106.660172");
        User valid = createCandidate(1L, Role.RECIPIENT, true, true, "10.772622", "106.670172");

        when(userRepository.findByRoleIn(any())).thenReturn(List.of(self, valid));
        when(receiverCapacityService.countActiveOrders(any())).thenReturn(Map.of());

        List<User> result = filter.filterCandidates(foodPost);

        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).getId());
    }

    @Test
    void filter_excludesMissingLocation() {
        User valid = createCandidate(1L, Role.RECIPIENT, true, true, "10.772622", "106.670172");
        User missingLat = createCandidate(2L, Role.RECIPIENT, true, true, null, "106.670172");
        User missingLng = createCandidate(3L, Role.RECIPIENT, true, true, "10.772622", null);

        when(userRepository.findByRoleIn(any())).thenReturn(List.of(valid, missingLat, missingLng));
        when(receiverCapacityService.countActiveOrders(any())).thenReturn(Map.of());

        List<User> result = filter.filterCandidates(foodPost);

        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).getId());
    }

    @Test
    void filter_excludesFarDistance() {
        User close = createCandidate(1L, Role.RECIPIENT, true, true, "10.772622", "106.670172"); // ~1.5km
        User far = createCandidate(2L, Role.RECIPIENT, true, true, "21.028511", "105.804817"); // Hanoi (~1100km)

        when(userRepository.findByRoleIn(any())).thenReturn(List.of(close, far));
        when(receiverCapacityService.countActiveOrders(any())).thenReturn(Map.of());

        List<User> result = filter.filterCandidates(foodPost, 10.0, 5); // max 10km

        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).getId());
    }

    @Test
    void filter_excludesOverCapacity() {
        User user1 = createCandidate(1L, Role.RECIPIENT, true, true, "10.772622", "106.670172");
        User user2 = createCandidate(2L, Role.RECIPIENT, true, true, "10.772622", "106.670172");

        when(userRepository.findByRoleIn(any())).thenReturn(List.of(user1, user2));
        // Simulate user1 having 5 active orders, user2 having 1
        when(receiverCapacityService.countActiveOrders(any())).thenReturn(Map.of(1L, 5L, 2L, 1L));

        List<User> result = filter.filterCandidates(foodPost, 10.0, 5); // max 5 active orders

        assertEquals(1, result.size());
        assertEquals(2L, result.get(0).getId()); // only user2 passes
    }

    @Test
    void filter_defaultCapacity_allowsOneActiveOrderAndRejectsTwo() {
        User belowLimit = createCandidate(1L, Role.RECIPIENT, true, true, "10.772622", "106.670172");
        User atLimit = createCandidate(2L, Role.RECIPIENT, true, true, "10.772622", "106.670172");

        when(userRepository.findByRoleIn(any())).thenReturn(List.of(belowLimit, atLimit));
        when(receiverCapacityService.countActiveOrders(any())).thenReturn(Map.of(1L, 1L, 2L, 2L));

        List<User> result = filter.filterCandidates(foodPost);

        assertEquals(List.of(belowLimit), result);
    }

    @Test
    void filter_returnsEmptyIfPickupWindowExpired() {
        User valid = createCandidate(1L, Role.RECIPIENT, true, true, "10.772622", "106.670172");

        when(userRepository.findByRoleIn(any())).thenReturn(List.of(valid));

        // Expired 1 hour ago
        foodPost.setPickupEndAt(now.minus(1, ChronoUnit.HOURS));

        List<User> result = filter.filterCandidates(foodPost);

        assertTrue(result.isEmpty());
    }

    @Test
    void filter_haversineFormulaTest() {
        // Test known distance (approx)
        // HCMC: 10.762622, 106.660172
        // Hanoi: 21.028511, 105.804817
        // Distance is ~1139 km
        double distance = MatchingCandidateFilter.haversineKm(
                10.762622, 106.660172,
                21.028511, 105.804817
        );
        assertTrue(distance > 1100 && distance < 1200);
    }

    @Test
    void findEligibleFoodPostIdsChecksOneCandidateAgainstPosts() {
        User candidate = createCandidate(1L, Role.RECIPIENT, true, true, "10.772622", "106.670172");
        FoodPost farPost = new FoodPost();
        farPost.setId(2L);
        User farSupplier = createCandidate(200L, Role.SUPPLIER, true, true, "21.028511", "105.804817");
        BusinessProfile farProfile = new BusinessProfile();
        farProfile.setUser(farSupplier);
        farPost.setBusinessProfile(farProfile);
        farPost.setPickupEndAt(now.plus(2, ChronoUnit.HOURS));
        when(receiverCapacityService.countActiveOrders(List.of(1L))).thenReturn(Map.of(1L, 0L));

        Set<Long> result = filter.findEligibleFoodPostIds(List.of(foodPost, farPost), candidate);

        assertEquals(Set.of(1L), result);
    }

    @Test
    void findEligibleFoodPostIdsRejectsCandidateAtCapacity() {
        User candidate = createCandidate(1L, Role.RECIPIENT, true, true, "10.772622", "106.670172");
        when(receiverCapacityService.countActiveOrders(List.of(1L))).thenReturn(Map.of(1L, 2L));

        Set<Long> result = filter.findEligibleFoodPostIds(List.of(foodPost), candidate);

        assertTrue(result.isEmpty());
    }
}
