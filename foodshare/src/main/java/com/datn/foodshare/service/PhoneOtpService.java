package com.datn.foodshare.service;

import com.datn.foodshare.domain.request.SendPhoneOtpRequest;
import com.datn.foodshare.domain.request.VerifyPhoneOtpRequest;
import com.datn.foodshare.domain.response.PhoneOtpChallengeResponse;
import com.datn.foodshare.domain.response.PhoneVerificationResponse;
import com.datn.foodshare.repository.UserRepository;
import com.datn.foodshare.security.JwtTokenProvider;
import com.datn.foodshare.util.PhoneNumberUtil;
import com.datn.foodshare.util.error.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PhoneOtpService {

    private static final long OTP_EXPIRATION_SECONDS = 15 * 60;
    private static final long REGISTRATION_EXPIRATION_SECONDS = 10 * 60;

    private final UserRepository userRepository;
    private final InfobipOtpClient infobipOtpClient;
    private final JwtTokenProvider jwtTokenProvider;

    public PhoneOtpChallengeResponse sendOtp(SendPhoneOtpRequest request) {
        String phone = PhoneNumberUtil.normalizeVietnamese(request.phone());
        if (userRepository.existsByPhone(phone)) {
            throw new BusinessException("Số điện thoại đã được sử dụng");
        }
        String pinId = infobipOtpClient.sendPin(PhoneNumberUtil.toInternational(phone));
        String challengeToken = jwtTokenProvider.createPhoneOtpChallengeToken(phone, pinId);
        return new PhoneOtpChallengeResponse(challengeToken, mask(phone), OTP_EXPIRATION_SECONDS);
    }

    public PhoneVerificationResponse verifyOtp(VerifyPhoneOtpRequest request) {
        if (!jwtTokenProvider.validatePhoneOtpChallengeToken(request.challengeToken())) {
            throw new BusinessException("Phiên xác minh OTP không hợp lệ hoặc đã hết hạn");
        }
        String phone = jwtTokenProvider.getPhoneFromToken(request.challengeToken());
        String pinId = jwtTokenProvider.getPinIdFromChallengeToken(request.challengeToken());
        if (pinId == null || !infobipOtpClient.verifyPin(pinId, request.otp())) {
            throw new BusinessException("OTP không đúng hoặc đã hết hạn");
        }
        String registrationToken = jwtTokenProvider.createPhoneRegistrationToken(phone);
        return new PhoneVerificationResponse(registrationToken, phone, REGISTRATION_EXPIRATION_SECONDS);
    }

    private String mask(String phone) {
        return phone.length() <= 3 ? phone : "******" + phone.substring(phone.length() - 3);
    }
}
