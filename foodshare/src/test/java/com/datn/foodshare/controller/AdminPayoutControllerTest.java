package com.datn.foodshare.controller;

import com.datn.foodshare.controller.admin.AdminPayoutController;
import com.datn.foodshare.domain.request.RejectPayoutRequest;
import com.datn.foodshare.domain.response.PayoutResponse;
import com.datn.foodshare.service.PayoutService;
import com.datn.foodshare.util.constant.PayoutStatus;
import com.datn.foodshare.util.error.PermissionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminPayoutControllerTest {

    @Mock
    private PayoutService payoutService;

    @Test
    void getPayouts_delegatesStatusFilter() throws PermissionException {
        AdminPayoutController controller = new AdminPayoutController(payoutService);
        PageRequest pageable = PageRequest.of(0, 20);
        PayoutResponse payout = PayoutResponse.builder().id(1L).status(PayoutStatus.PENDING).build();
        when(payoutService.getPayoutRequestsForAdmin(PayoutStatus.PENDING, pageable))
                .thenReturn(new PageImpl<>(List.of(payout), pageable, 1));

        var response = controller.getPayouts(PayoutStatus.PENDING, pageable);

        assertEquals(1, response.getBody().getTotalElements());
    }

    @Test
    void approve_delegatesToService() throws PermissionException {
        AdminPayoutController controller = new AdminPayoutController(payoutService);
        PayoutResponse payout = PayoutResponse.builder().id(2L).status(PayoutStatus.SUCCESS).build();
        when(payoutService.approvePayout(2L)).thenReturn(payout);

        assertEquals(PayoutStatus.SUCCESS, controller.approve(2L).getBody().getStatus());
        verify(payoutService).approvePayout(2L);
    }

    @Test
    void reject_passesValidatedReasonToService() throws PermissionException {
        AdminPayoutController controller = new AdminPayoutController(payoutService);
        RejectPayoutRequest request = new RejectPayoutRequest("Sai tài khoản");
        PayoutResponse payout = PayoutResponse.builder().id(3L).status(PayoutStatus.FAILED).build();
        when(payoutService.rejectPayout(3L, "Sai tài khoản")).thenReturn(payout);

        assertEquals(PayoutStatus.FAILED, controller.reject(3L, request).getBody().getStatus());
        verify(payoutService).rejectPayout(3L, "Sai tài khoản");
    }
}
