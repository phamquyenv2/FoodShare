package com.datn.foodshare.service;

import com.datn.foodshare.domain.entity.FoodPost;
import com.datn.foodshare.domain.entity.User;
import com.datn.foodshare.repository.UserRepository;
import com.datn.foodshare.util.SecurityUtil;
import com.datn.foodshare.util.constant.Role;
import com.datn.foodshare.util.error.PermissionException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
@RequiredArgsConstructor
public class PermissionService {
    private final UserRepository userRepository;

    public User currentUser() {
        Long userId = SecurityUtil.getCurrentUserId()
                .orElseThrow(() -> new BadCredentialsException("Không xác định được người dùng hiện tại"));
        return userRepository.findById(userId)
                .orElseThrow(() -> new BadCredentialsException("Tài khoản không tồn tại"));
    }

    public User requireRole(Role expected) throws PermissionException {
        return requireRole(currentUser(), expected);
    }

    public User requireRole(User user, Role expected) throws PermissionException {
        if (user.getRole() != expected) {
            throw new PermissionException("Chỉ " + expected.name() + " mới có quyền thực hiện hành động này");
        }
        return user;
    }

    public User requireReceiver() throws PermissionException {
        return requireReceiver(currentUser());
    }

    public User requireReceiver(User user) throws PermissionException {
        if (user.getRole() != Role.RECIPIENT && user.getRole() != Role.ORGANIZATION) {
            throw new PermissionException("Chỉ RECIPIENT hoặc ORGANIZATION mới có quyền thực hiện hành động này");
        }
        return user;
    }

    public void requireFoodPostOwnership(User user, FoodPost post) throws PermissionException {
        if (post == null || post.getBusinessProfile() == null
                || post.getBusinessProfile().getUser() == null
                || !Objects.equals(post.getBusinessProfile().getUser().getId(), user.getId())) {
            throw new PermissionException("Bạn không có quyền thao tác với bài đăng này");
        }
    }
}
