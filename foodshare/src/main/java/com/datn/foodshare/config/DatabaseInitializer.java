package com.datn.foodshare.config;

import com.datn.foodshare.domain.entity.User;
import com.datn.foodshare.util.constant.AuthProvider;
import com.datn.foodshare.util.constant.Role;
import com.datn.foodshare.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Slf4j
@Component
public class DatabaseInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.default-admin.phone:0987654321}")
    private String defaultPhone;

    @Value("${app.default-admin.email:admin@foodshare.com}")
    private String defaultEmail;

    @Value("${app.default-admin.password:Admin@123456}")
    private String defaultPassword;

    @Value("${app.default-admin.full-name:FoodShare Administrator}")
    private String defaultFullName;

    public DatabaseInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        Optional<User> adminOpt = userRepository.findFirstByRole(Role.ADMIN);
        if (adminOpt.isEmpty()) {
            log.info("Chưa có tài khoản ADMIN trong hệ thống. Đang tiến hành tạo tài khoản Admin mặc định...");

            User admin = User.builder()
                    .phone(defaultPhone)
                    .email(defaultEmail)
                    .passwordHash(passwordEncoder.encode(defaultPassword))
                    .fullName(defaultFullName)
                    .role(Role.ADMIN)
                    .authProvider(AuthProvider.LOCAL)
                    .active(true)
                    .build();

            userRepository.save(admin);
            log.info(">>> Khởi tạo tài khoản ADMIN thành công: Email = {}, Phone = {}", defaultEmail, defaultPhone);
        } else {
            log.info("Tài khoản ADMIN đã tồn tại trong hệ thống. Bỏ qua bước khởi tạo.");
        }
    }
}
