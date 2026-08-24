package com.datn.foodshare.domain.response;

import com.datn.foodshare.domain.entity.PayoutAccount;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PayoutAccountResponse {
    private Long id;
    private Long businessProfileId;
    private String bankCode;
    private String bankName;
    private String accountNumber;
    private String accountHolderName;
    private boolean isDefault;
    private boolean isActive;

    public static PayoutAccountResponse from(PayoutAccount account) {
        return PayoutAccountResponse.builder()
                .id(account.getId())
                .businessProfileId(account.getBusinessProfile().getId())
                .bankCode(account.getBankCode())
                .bankName(account.getBankName())
                .accountNumber(account.getAccountNumber())
                .accountHolderName(account.getAccountHolderName())
                .isDefault(account.isDefault())
                .isActive(account.isActive())
                .build();
    }
}
