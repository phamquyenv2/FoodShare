package com.datn.foodshare.domain.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
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
@Table(name = "payout_accounts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class PayoutAccount extends BaseModel {

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "business_profile_id", nullable = false)
    private BusinessProfile businessProfile;

    @Column(nullable = false, length = 30)
    private String bankCode;

    @Column(nullable = false, length = 150)
    private String bankName;

    @Column(nullable = false, length = 50)
    private String accountNumber;

    @Column(nullable = false, length = 150)
    private String accountHolderName;

    @Builder.Default
    @Column(nullable = false)
    private boolean isDefault = false;

    @Builder.Default
    @Column(nullable = false)
    private boolean isActive = true;

    @JsonIgnore
    @Builder.Default
    @OneToMany(mappedBy = "payoutAccount", fetch = FetchType.LAZY)
    private List<Payout> payouts = new ArrayList<>();
}
