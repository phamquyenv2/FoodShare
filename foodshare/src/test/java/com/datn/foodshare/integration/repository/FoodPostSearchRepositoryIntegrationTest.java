package com.datn.foodshare.integration.repository;

import com.datn.foodshare.domain.entity.BusinessProfile;
import com.datn.foodshare.domain.entity.Category;
import com.datn.foodshare.domain.entity.FoodPost;
import com.datn.foodshare.domain.entity.FoodPostImage;
import com.datn.foodshare.domain.entity.User;
import com.datn.foodshare.domain.request.FoodPostFilterRequest;
import com.datn.foodshare.domain.response.FoodPostResponse;
import com.datn.foodshare.repository.BusinessProfileRepository;
import com.datn.foodshare.repository.CategoryRepository;
import com.datn.foodshare.repository.FoodPostRepository;
import com.datn.foodshare.repository.UserRepository;
import com.datn.foodshare.service.CloudinaryService;
import com.datn.foodshare.service.FoodPostService;
import com.datn.foodshare.service.PermissionService;
import com.datn.foodshare.service.matching.DynamicMatchingGraphSynchronizer;
import com.datn.foodshare.util.constant.AuthProvider;
import com.datn.foodshare.util.constant.PostStatus;
import com.datn.foodshare.util.constant.PostType;
import com.datn.foodshare.util.constant.ProfileType;
import com.datn.foodshare.util.constant.Role;
import com.datn.foodshare.util.constant.SupplierType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "spring.jpa.open-in-view=false"
})
@Import(FoodPostService.class)
class FoodPostSearchRepositoryIntegrationTest {

    private static final Instant REFERENCE_TIME = Instant.parse("2099-01-01T00:00:00Z");

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private FoodPostService foodPostService;

    @MockitoBean
    private CloudinaryService cloudinaryService;

    @MockitoBean
    private DynamicMatchingGraphSynchronizer matchingGraphSynchronizer;

    @MockitoBean
    private PermissionService permissionService;

    private BusinessProfile supplier;
    private Category bakery;
    private Category produce;

    @BeforeEach
    void setUp() {
        User user = User.builder()
                .phone("0900000001")
                .passwordHash("hashed-password")
                .fullName("QuyenPA Supplier")
                .role(Role.SUPPLIER)
                .authProvider(AuthProvider.LOCAL)
                .active(true)
                .profileCompleted(true)
                .build();
        entityManager.persist(user);

        supplier = BusinessProfile.builder()
                .user(user)
                .name("QuyenPA Food Store")
                .profileType(ProfileType.SUPPLIER)
                .supplierType(SupplierType.OTHER)
                .build();
        entityManager.persist(supplier);

        bakery = Category.builder().name("QuyenPA Bakery").build();
        produce = Category.builder().name("QuyenPA Produce").build();
        entityManager.persist(bakery);
        entityManager.persist(produce);
    }

    @Test
    void combinesKeywordCategoryTypePriceQuantityAndExpiryFilters() {
        persistPost("QuyenPA Bread Box", "QuyenPA Fresh Food", bakery, PostType.PAID, "15000", 5,
                REFERENCE_TIME.plusSeconds(2 * 86_400), PostStatus.AVAILABLE, 1);
        persistPost("QuyenPA Bread Box", "QuyenPA Fresh Food", produce, PostType.PAID, "15000", 5,
                REFERENCE_TIME.plusSeconds(2 * 86_400), PostStatus.AVAILABLE, 1);
        persistPost("QuyenPA Bread Box", "QuyenPA Fresh Food", bakery, PostType.FREE, "0", 5,
                REFERENCE_TIME.plusSeconds(2 * 86_400), PostStatus.AVAILABLE, 1);
        persistPost("QuyenPA Bread Box", "QuyenPA Fresh Food", bakery, PostType.PAID, "25000", 5,
                REFERENCE_TIME.plusSeconds(2 * 86_400), PostStatus.AVAILABLE, 1);
        persistPost("QuyenPA Bread Box", "QuyenPA Fresh Food", bakery, PostType.PAID, "15000", 1,
                REFERENCE_TIME.plusSeconds(2 * 86_400), PostStatus.AVAILABLE, 1);
        persistPost("QuyenPA Bread Box", "QuyenPA Fresh Food", bakery, PostType.PAID, "15000", 5,
                REFERENCE_TIME.minusSeconds(86_400), PostStatus.AVAILABLE, 1);
        persistPost("QuyenPA Bread Box", "QuyenPA Fresh Food", bakery, PostType.PAID, "15000", 5,
                REFERENCE_TIME.plusSeconds(2 * 86_400), PostStatus.HIDDEN, 1);
        flushAndClear();

        FoodPostFilterRequest filter = new FoodPostFilterRequest();
        filter.setKeyword("  qUyEnPa  ");
        filter.setCategoryId(bakery.getId());
        filter.setPostType(PostType.PAID);
        filter.setMinPrice(new BigDecimal("10000"));
        filter.setMaxPrice(new BigDecimal("20000"));
        filter.setMinAvailableQuantity(3);
        filter.setExpiresFrom(REFERENCE_TIME.plusSeconds(86_400));
        filter.setExpiresTo(REFERENCE_TIME.plusSeconds(3 * 86_400));

        Page<FoodPostResponse> result = foodPostService.getPublicList(filter, PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent())
                .singleElement()
                .satisfies(post -> {
                    assertThat(post.getName()).isEqualTo("QuyenPA Bread Box");
                    assertThat(post.getImages()).hasSize(1);
                    assertThat(post.getCategory().getName()).isEqualTo("QuyenPA Bakery");
                    assertThat(post.getSupplier().getName()).isEqualTo("QuyenPA Food Store");
                });
    }

    @Test
    void supportsPaginationAndSorting() {
        persistPost("QuyenPA Low Price", null, bakery, PostType.PAID, "10000", 2,
                REFERENCE_TIME.plusSeconds(86_400), PostStatus.AVAILABLE, 0);
        persistPost("QuyenPA Middle Price", null, bakery, PostType.PAID, "20000", 2,
                REFERENCE_TIME.plusSeconds(86_400), PostStatus.AVAILABLE, 0);
        persistPost("QuyenPA High Price", null, bakery, PostType.PAID, "30000", 2,
                REFERENCE_TIME.plusSeconds(86_400), PostStatus.AVAILABLE, 0);
        flushAndClear();

        Page<FoodPostResponse> result = foodPostService.getPublicList(
                new FoodPostFilterRequest(),
                PageRequest.of(1, 1, Sort.by(Sort.Direction.DESC, "unitPrice")));

        assertThat(result.getTotalElements()).isEqualTo(3);
        assertThat(result.getTotalPages()).isEqualTo(3);
        assertThat(result.getNumber()).isEqualTo(1);
        assertThat(result.getContent()).extracting(FoodPostResponse::getName).containsExactly("QuyenPA Middle Price");
    }

    @Test
    void excludesExpiredPostsWithoutClientExpiryBounds() {
        persistPost("QuyenPA Expired Food", null, bakery, PostType.FREE, "0", 2,
                Instant.now().minusSeconds(60), PostStatus.AVAILABLE, 0);
        persistPost("QuyenPA Current Food", null, bakery, PostType.FREE, "0", 2,
                REFERENCE_TIME, PostStatus.AVAILABLE, 0);
        flushAndClear();

        Page<FoodPostResponse> result = foodPostService.getPublicList(
                new FoodPostFilterRequest(),
                PageRequest.of(0, 10));

        assertThat(result.getContent()).extracting(FoodPostResponse::getName).containsExactly("QuyenPA Current Food");
    }

    @Test
    void loadsCategorySupplierAndImagesWithoutNPlusOneQueries() {
        persistPost("QuyenPA Post 1", null, bakery, PostType.FREE, "0", 2,
                REFERENCE_TIME.plusSeconds(86_400), PostStatus.AVAILABLE, 2);
        persistPost("QuyenPA Post 2", null, bakery, PostType.FREE, "0", 2,
                REFERENCE_TIME.plusSeconds(2 * 86_400), PostStatus.AVAILABLE, 2);
        persistPost("QuyenPA Post 3", null, bakery, PostType.FREE, "0", 2,
                REFERENCE_TIME.plusSeconds(3 * 86_400), PostStatus.AVAILABLE, 2);
        flushAndClear();

        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        Page<FoodPostResponse> result = foodPostService.getPublicList(
                new FoodPostFilterRequest(),
                PageRequest.of(0, 2, Sort.by("expiresAt")));

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent()).allSatisfy(post -> {
            assertThat(post.getImages()).hasSize(2);
            assertThat(post.getCategory().getName()).isEqualTo("QuyenPA Bakery");
            assertThat(post.getSupplier().getName()).isEqualTo("QuyenPA Food Store");
        });
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(3);
    }

    private FoodPost persistPost(
            String name,
            String description,
            Category category,
            PostType postType,
            String price,
            int availableQuantity,
            Instant expiresAt,
            PostStatus status,
            int imageCount) {
        FoodPost post = FoodPost.builder()
                .businessProfile(supplier)
                .category(category)
                .name(name)
                .description(description)
                .totalQuantity(Math.max(availableQuantity, 10))
                .availableQuantity(availableQuantity)
                .unitPrice(new BigDecimal(price))
                .postType(postType)
                .postStatus(status)
                .expiresAt(expiresAt)
                .pickupAddress("123 QuyenPA Street, Hanoi")
                .pickupStartAt(REFERENCE_TIME.plusSeconds(3_600))
                .pickupEndAt(REFERENCE_TIME.plusSeconds(7_200))
                .build();
        for (int i = 0; i < imageCount; i++) {
            post.getImages().add(FoodPostImage.builder()
                    .foodPost(post)
                    .imageUrl("https://quyenpa.dev/foodshare/" + name.replace(" ", "_") + "/" + i + ".jpg")
                    .build());
        }
        entityManager.persist(post);
        return post;
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
