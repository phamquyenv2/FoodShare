package com.datn.foodshare.service;

import com.datn.foodshare.domain.entity.User;
import com.datn.foodshare.domain.entity.UserDevice;
import com.datn.foodshare.domain.request.RegisterDeviceRequest;
import com.datn.foodshare.repository.UserDeviceRepository;
import com.datn.foodshare.repository.UserRepository;
import com.datn.foodshare.util.SecurityUtil;
import com.datn.foodshare.util.constant.DeviceType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeviceServiceTest {

    @Mock
    private UserDeviceRepository userDeviceRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private DeviceService deviceService;

    @Test
    void registerExistingToken_updatesDeviceAndTrimsOptionalName() {
        User user = new User();
        user.setId(7L);
        UserDevice device = new UserDevice();
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(userDeviceRepository.findByFcmToken("token")).thenReturn(Optional.of(device));

        try (MockedStatic<SecurityUtil> security = org.mockito.Mockito.mockStatic(SecurityUtil.class)) {
            security.when(SecurityUtil::getCurrentUserId).thenReturn(Optional.of(7L));
            deviceService.register(new RegisterDeviceRequest("  token  ", DeviceType.WEB, "  Browser  "));
        }

        assertSame(user, device.getUser());
        assertTrue(device.isActive());
        org.junit.jupiter.api.Assertions.assertEquals("token", device.getFcmToken());
        org.junit.jupiter.api.Assertions.assertEquals("Browser", device.getDeviceName());
        org.junit.jupiter.api.Assertions.assertEquals(DeviceType.WEB, device.getDeviceType());
        verify(userDeviceRepository).save(device);
    }

    @Test
    void registerNewToken_allowsNullNameAndCreatesDevice() {
        User user = new User();
        user.setId(8L);
        when(userRepository.findById(8L)).thenReturn(Optional.of(user));
        when(userDeviceRepository.findByFcmToken("new-token")).thenReturn(Optional.empty());

        try (MockedStatic<SecurityUtil> security = org.mockito.Mockito.mockStatic(SecurityUtil.class)) {
            security.when(SecurityUtil::getCurrentUserId).thenReturn(Optional.of(8L));
            deviceService.register(new RegisterDeviceRequest("new-token", DeviceType.MOBILE, null));
        }

        var saved = org.mockito.ArgumentCaptor.forClass(UserDevice.class);
        verify(userDeviceRepository).save(saved.capture());
        assertSame(user, saved.getValue().getUser());
        assertNull(saved.getValue().getDeviceName());
        assertTrue(saved.getValue().isActive());
    }

    @Test
    void deactivateCurrentUserDevices_deactivatesOnlyActiveDevices() {
        User user = new User();
        user.setId(9L);
        UserDevice first = new UserDevice();
        UserDevice second = new UserDevice();
        when(userRepository.findById(9L)).thenReturn(Optional.of(user));
        when(userDeviceRepository.findByUserIdAndIsActiveTrue(9L)).thenReturn(List.of(first, second));

        try (MockedStatic<SecurityUtil> security = org.mockito.Mockito.mockStatic(SecurityUtil.class)) {
            security.when(SecurityUtil::getCurrentUserId).thenReturn(Optional.of(9L));
            deviceService.deactivateCurrentUserDevices();
        }

        assertFalse(first.isActive());
        assertFalse(second.isActive());
    }

    @Test
    void registerWithoutAuthenticatedUser_throwsBadCredentials() {
        try (MockedStatic<SecurityUtil> security = org.mockito.Mockito.mockStatic(SecurityUtil.class)) {
            security.when(SecurityUtil::getCurrentUserId).thenReturn(Optional.empty());
            assertThrows(BadCredentialsException.class,
                    () -> deviceService.register(new RegisterDeviceRequest("token", DeviceType.WEB, null)));
        }
    }

    @Test
    void registerWhenUserDoesNotExist_throwsBadCredentials() {
        when(userRepository.findById(10L)).thenReturn(Optional.empty());
        try (MockedStatic<SecurityUtil> security = org.mockito.Mockito.mockStatic(SecurityUtil.class)) {
            security.when(SecurityUtil::getCurrentUserId).thenReturn(Optional.of(10L));
            assertThrows(BadCredentialsException.class,
                    () -> deviceService.register(new RegisterDeviceRequest("token", DeviceType.WEB, null)));
        }
    }
}
