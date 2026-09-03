package com.datn.foodshare.service;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.datn.foodshare.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String identifier) throws UsernameNotFoundException {
        com.datn.foodshare.domain.entity.User userEntity = userRepository.findByPhoneOrEmail(identifier, identifier)
                .orElseThrow(() -> new UsernameNotFoundException("Tài khoản hoặc mật khẩu không chính xác"));

        UserDetails userDetails = User.withUsername(identifier)
                .password(userEntity.getPasswordHash())
                .authorities("ROLE_" + userEntity.getRole().name())
                .disabled(!userEntity.isActive())
                .build();

        return userDetails;
    }
}
