package com.datn.foodshare.service;

import com.datn.foodshare.domain.request.SendPhoneOtpRequest;
import com.datn.foodshare.domain.request.VerifyPhoneOtpRequest;
import com.datn.foodshare.repository.UserRepository;
import com.datn.foodshare.security.JwtTokenProvider;
import com.datn.foodshare.util.error.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PhoneOtpServiceTest {

    @Mock UserRepository userRepository;
    @Mock InfobipOtpClient infobipOtpClient;
    @Mock JwtTokenProvider jwtTokenProvider;
    private PhoneOtpService service;

    @BeforeEach
    void setUp() {
        service = new PhoneOtpService(userRepository, infobipOtpClient, jwtTokenProvider);
    }

    @Test
    void sendOtpNormalizesPhoneAndReturnsSignedChallenge() {
        when(infobipOtpClient.sendPin("84912345678")).thenReturn("pin-id");
        when(jwtTokenProvider.createPhoneOtpChallengeToken("0912345678", "pin-id"))
                .thenReturn("challenge-token");

        var response = service.sendOtp(new SendPhoneOtpRequest("+84912345678"));

        assertEquals("challenge-token", response.challengeToken());
        assertEquals("******678", response.maskedPhone());
        verify(userRepository).existsByPhone("0912345678");
    }

    @Test
    void sendOtpRejectsExistingPhoneBeforeCallingInfobip() {
        when(userRepository.existsByPhone("0912345678")).thenReturn(true);

        assertThrows(BusinessException.class,
                () -> service.sendOtp(new SendPhoneOtpRequest("0912345678")));

        verifyNoInteractions(infobipOtpClient);
    }

    @Test
    void verifyOtpReturnsRegistrationTokenForBoundPhone() {
        when(jwtTokenProvider.validatePhoneOtpChallengeToken("challenge-token")).thenReturn(true);
        when(jwtTokenProvider.getPhoneFromToken("challenge-token")).thenReturn("0912345678");
        when(jwtTokenProvider.getPinIdFromChallengeToken("challenge-token")).thenReturn("pin-id");
        when(infobipOtpClient.verifyPin("pin-id", "1234")).thenReturn(true);
        when(jwtTokenProvider.createPhoneRegistrationToken("0912345678"))
                .thenReturn("registration-token");

        var response = service.verifyOtp(new VerifyPhoneOtpRequest("challenge-token", "1234"));

        assertEquals("registration-token", response.registrationToken());
        assertEquals("0912345678", response.phone());
    }

    @Test
    void verifyOtpRejectsInvalidChallengeWithoutCallingInfobip() {
        assertThrows(BusinessException.class,
                () -> service.verifyOtp(new VerifyPhoneOtpRequest("invalid", "1234")));
        verifyNoInteractions(infobipOtpClient);
    }

    @Test
    void verifyOtpRejectsWrongPin() {
        when(jwtTokenProvider.validatePhoneOtpChallengeToken("challenge-token")).thenReturn(true);
        when(jwtTokenProvider.getPhoneFromToken("challenge-token")).thenReturn("0912345678");
        when(jwtTokenProvider.getPinIdFromChallengeToken("challenge-token")).thenReturn("pin-id");

        assertThrows(BusinessException.class,
                () -> service.verifyOtp(new VerifyPhoneOtpRequest("challenge-token", "0000")));
    }
}
