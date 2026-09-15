package com.datn.foodshare.integration;

import com.datn.foodshare.config.DatabaseInitializer;
import com.datn.foodshare.domain.entity.BusinessProfile;
import com.datn.foodshare.domain.entity.Category;
import com.datn.foodshare.domain.entity.FoodPost;
import com.datn.foodshare.domain.entity.User;
import com.datn.foodshare.event.listener.NotificationEventListener;
import com.datn.foodshare.repository.BusinessProfileRepository;
import com.datn.foodshare.repository.CategoryRepository;
import com.datn.foodshare.repository.FoodPostRepository;
import com.datn.foodshare.repository.UserRepository;
import com.datn.foodshare.security.JwtTokenProvider;
import com.datn.foodshare.service.CloudinaryService;
import com.datn.foodshare.service.matching.DynamicMatchingGraphSynchronizer;
import com.datn.foodshare.util.constant.AuthProvider;
import com.datn.foodshare.util.constant.PostStatus;
import com.datn.foodshare.util.constant.PostType;
import com.datn.foodshare.util.constant.ProfileType;
import com.datn.foodshare.util.constant.Role;
import com.datn.foodshare.util.constant.SupplierType;
import com.datn.foodshare.util.constant.VerificationStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:foodshare_integration;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.open-in-view=false",
        "spring.flyway.enabled=false",
        "firebase.credentials.path=",
        "app.jwt.secret=dGhpc19pc19hX3Zlcnlfc2VjdXJlX2tleV9mb3JfZm9vZHNoYXJlX2ludGVncmF0aW9uX3Rlc3Rz"
})
@AutoConfigureMockMvc
public abstract class IntegrationTestSupport {

    protected final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected BusinessProfileRepository businessProfileRepository;

    @Autowired
    protected CategoryRepository categoryRepository;

    @Autowired
    protected FoodPostRepository foodPostRepository;

    @Autowired
    protected JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    protected CloudinaryService cloudinaryService;

    @MockitoBean
    protected DynamicMatchingGraphSynchronizer matchingGraphSynchronizer;

    @MockitoBean
    protected NotificationEventListener notificationEventListener;

    @MockitoBean
    protected DatabaseInitializer databaseInitializer;

    protected User createUser(Role role, boolean profileCompleted) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String digits = String.format("%08d", Integer.toUnsignedLong(suffix.hashCode()) % 100_000_000L);
        User user = User.builder()
                .phone("09" + digits)
                .email(suffix + "@quyenpa.test")
                .passwordHash("not-used")
                .fullName("QuyenPA " + role + " " + suffix)
                .role(role)
                .authProvider(AuthProvider.LOCAL)
                .active(true)
                .profileCompleted(profileCompleted)
                .build();
        return userRepository.saveAndFlush(user);
    }

    protected BusinessProfile createSupplierProfile(User supplier) {
        BusinessProfile profile = BusinessProfile.builder()
                .user(supplier)
                .name("QuyenPA Supplier " + supplier.getId())
                .profileType(ProfileType.SUPPLIER)
                .supplierType(SupplierType.OTHER)
                .verificationStatus(VerificationStatus.VERIFIED)
                .build();
        BusinessProfile saved = businessProfileRepository.saveAndFlush(profile);
        supplier.setBusinessProfile(saved);
        return saved;
    }

    protected Category createCategory() {
        return categoryRepository.saveAndFlush(Category.builder()
                .name("QuyenPA Category " + UUID.randomUUID())
                .build());
    }

    protected FoodPost createAvailablePost(BusinessProfile supplier, Category category, int quantity) {
        Instant now = Instant.now();
        return foodPostRepository.saveAndFlush(FoodPost.builder()
                .businessProfile(supplier)
                .category(category)
                .name("QuyenPA Food " + UUID.randomUUID())
                .description("QuyenPA integration test food post")
                .totalQuantity(quantity)
                .availableQuantity(quantity)
                .unitPrice(BigDecimal.ZERO)
                .postType(PostType.FREE)
                .postStatus(PostStatus.AVAILABLE)
                .expiresAt(now.plusSeconds(86_400))
                .pickupAddress("123 QuyenPA Street, Hanoi")
                .pickupStartAt(now.plusSeconds(3_600))
                .pickupEndAt(now.plusSeconds(7_200))
                .build());
    }

    protected String bearer(User user) {
        return "Bearer " + jwtTokenProvider.createAccessToken(user);
    }

    protected void authenticate(User user) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        String.valueOf(user.getId()),
                        "integration-test",
                        List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))));
    }

    protected void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }
}
