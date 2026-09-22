package com.datn.foodshare.controller;

import com.datn.foodshare.domain.request.CreatePayoutAccountRequest;
import com.datn.foodshare.domain.request.CreatePayoutRequest;
import com.datn.foodshare.domain.response.PayoutAccountResponse;
import com.datn.foodshare.domain.response.PayoutResponse;
import com.datn.foodshare.domain.response.WalletSummaryResponse;
import com.datn.foodshare.service.PayoutService;
import com.datn.foodshare.util.error.PermissionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PayoutControllerTest {

    @Mock
    private PayoutService payoutService;

    private PayoutController controller;

    @BeforeEach
    void setUp() {
        controller = new PayoutController(payoutService);
    }

    @Test
    void createPayoutAccount_returns201() throws PermissionException {
        CreatePayoutAccountRequest request = new CreatePayoutAccountRequest();
        PayoutAccountResponse response = PayoutAccountResponse.builder().id(1L).build();
        when(payoutService.createPayoutAccount(request)).thenReturn(response);

        ResponseEntity<PayoutAccountResponse> entity = controller.createPayoutAccount(request);

        assertEquals(HttpStatus.CREATED, entity.getStatusCode());
        assertSame(response, entity.getBody());
    }

    @Test
    void getMyPayoutAccounts_returns200() throws PermissionException {
        List<PayoutAccountResponse> list = List.of(PayoutAccountResponse.builder().id(1L).build());
        when(payoutService.getMyPayoutAccounts()).thenReturn(list);

        ResponseEntity<List<PayoutAccountResponse>> entity = controller.getMyPayoutAccounts();

        assertEquals(HttpStatus.OK, entity.getStatusCode());
        assertSame(list, entity.getBody());
    }

    @Test
    void createPayout_returns201() throws PermissionException {
        CreatePayoutRequest request = new CreatePayoutRequest();
        PayoutResponse response = PayoutResponse.builder().id(1L).build();
        when(payoutService.createPayout(10L, request)).thenReturn(response);

        ResponseEntity<PayoutResponse> entity = controller.createPayout(10L, request);

        assertEquals(HttpStatus.CREATED, entity.getStatusCode());
        assertSame(response, entity.getBody());
    }

    @Test
    void createPayoutRequest_returns201() throws PermissionException {
        CreatePayoutRequest request = new CreatePayoutRequest();
        PayoutResponse response = PayoutResponse.builder().id(1L).build();
        when(payoutService.createPayoutRequest(request)).thenReturn(response);

        ResponseEntity<PayoutResponse> entity = controller.createPayoutRequest(request);

        assertEquals(HttpStatus.CREATED, entity.getStatusCode());
        assertSame(response, entity.getBody());
    }

    @Test
    void getMyPayoutRequests_returns200() throws PermissionException {
        Pageable pageable = PageRequest.of(0, 10);
        Page<PayoutResponse> page = new PageImpl<>(List.of(PayoutResponse.builder().id(1L).build()));
        when(payoutService.getMyPayoutRequests(pageable)).thenReturn(page);

        ResponseEntity<Page<PayoutResponse>> entity = controller.getMyPayoutRequests(pageable);

        assertEquals(HttpStatus.OK, entity.getStatusCode());
        assertSame(page, entity.getBody());
    }

    @Test
    void getMyWallet_returns200() throws PermissionException {
        Pageable pageable = PageRequest.of(0, 10);
        WalletSummaryResponse response = WalletSummaryResponse.builder().availableBalance(BigDecimal.ZERO).build();
        when(payoutService.getWalletSummary(pageable)).thenReturn(response);

        ResponseEntity<WalletSummaryResponse> entity = controller.getMyWallet(pageable);

        assertEquals(HttpStatus.OK, entity.getStatusCode());
        assertSame(response, entity.getBody());
    }
}
