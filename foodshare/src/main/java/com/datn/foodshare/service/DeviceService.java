package com.datn.foodshare.service;

import com.datn.foodshare.domain.entity.User;
import com.datn.foodshare.domain.entity.UserDevice;
import com.datn.foodshare.domain.request.RegisterDeviceRequest;
import com.datn.foodshare.repository.UserDeviceRepository;
import com.datn.foodshare.repository.UserRepository;
import com.datn.foodshare.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DeviceService {

    private final UserDeviceRepository userDeviceRepository;
    private final UserRepository userRepository;

    @Transactional
    public void register(RegisterDeviceRequest request) {
        User user = currentUser();
        String token = request.fcmToken().trim();
        UserDevice device = userDeviceRepository.findByFcmToken(token)
                .orElseGet(UserDevice::new);
        device.setUser(user);
        device.setFcmToken(token);
        device.setDeviceType(request.deviceType());
        device.setDeviceName(request.deviceName() == null ? null : request.deviceName().trim());
        device.setActive(true);
        userDeviceRepository.save(device);
    }

    @Transactional
    public void deactivateCurrentUserDevices() {
        User user = currentUser();
        userDeviceRepository.findByUserIdAndIsActiveTrue(user.getId()).forEach(device -> device.setActive(false));
    }

    private User currentUser() {
        Long userId = SecurityUtil.getCurrentUserId()
                .orElseThrow(() -> new BadCredentialsException("Không xác định được người dùng hiện tại"));
        return userRepository.findById(userId)
                .orElseThrow(() -> new BadCredentialsException("Tài khoản không tồn tại"));
    }
}
