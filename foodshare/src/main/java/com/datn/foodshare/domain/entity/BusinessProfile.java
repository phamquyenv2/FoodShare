package com.datn.foodshare.domain.entity;

import com.datn.foodshare.util.constant.OrganizationType;
import com.datn.foodshare.util.constant.ProfileType;
import com.datn.foodshare.util.constant.SupplierType;
import com.datn.foodshare.util.constant.VerificationStatus;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "business_profiles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class BusinessProfile extends BaseModel {

    @JsonIgnore
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(unique = true, length = 50)
    private String taxCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private VerificationStatus verificationStatus = VerificationStatus.UNVERIFIED;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProfileType profileType;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private OrganizationType organizationType;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private SupplierType supplierType;

    @Builder.Default
    @OneToMany(mappedBy = "businessProfile", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<License> licenses = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "businessProfile", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PayoutAccount> payoutAccounts = new ArrayList<>();

    @JsonIgnore
    @Builder.Default
    @OneToMany(mappedBy = "businessProfile", fetch = FetchType.LAZY)
    private List<FoodPost> foodPosts = new ArrayList<>();

    @JsonIgnore
    @Builder.Default
    @OneToMany(mappedBy = "businessProfile", fetch = FetchType.LAZY)
    private List<Order> orders = new ArrayList<>();

    @JsonIgnore
    @Builder.Default
    @OneToMany(mappedBy = "businessProfile", fetch = FetchType.LAZY)
    private List<Review> reviews = new ArrayList<>();
}
