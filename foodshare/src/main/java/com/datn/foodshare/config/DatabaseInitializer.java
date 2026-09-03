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
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Optional;

@Slf4j
@Component
public class DatabaseInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;

    @Value("${app.default-admin.phone:0987654321}")
    private String defaultPhone;

    @Value("${app.default-admin.email:admin@foodshare.com}")
    private String defaultEmail;

    @Value("${app.default-admin.password:Admin@123456}")
    private String defaultPassword;

    @Value("${app.default-admin.full-name:FoodShare Administrator}")
    private String defaultFullName;

    public DatabaseInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder, JdbcTemplate jdbcTemplate) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        try {
            log.info("Fixing legacy enum data in database...");
            jdbcTemplate.update("UPDATE orders SET order_status = 'ACCEPTED' WHERE order_status = 'CONFIRMED' OR order_status = 'PREPARING'");
            jdbcTemplate.update("UPDATE payments SET payment_status = 'SUCCESS' WHERE payment_status = 'COMPLETED'");
            jdbcTemplate.update("UPDATE payouts SET payout_status = 'SUCCESS' WHERE payout_status = 'COMPLETED'");
            jdbcTemplate.update("UPDATE notifications SET notification_type = 'ORDER' WHERE notification_type LIKE 'ORDER_%'");
            jdbcTemplate.update("UPDATE notifications SET notification_type = 'PAYMENT' WHERE notification_type LIKE 'PAYMENT_%'");
            jdbcTemplate.update("UPDATE notifications SET notification_type = 'REPORT' WHERE notification_type LIKE 'REPORT_%'");
            jdbcTemplate.update("UPDATE notifications SET notification_type = 'REVIEW' WHERE notification_type LIKE 'REVIEW_%'");
            jdbcTemplate.update("UPDATE notifications SET notification_type = 'REQUEST' WHERE notification_type LIKE 'REQUEST_%'");
            jdbcTemplate.update("UPDATE notifications SET notification_type = 'SYSTEM' WHERE notification_type NOT IN ('REQUEST', 'ORDER', 'PAYMENT', 'REPORT', 'REVIEW', 'SYSTEM')");
            jdbcTemplate.update("UPDATE notifications SET notification_type = 'REVIEW' WHERE notification_type = 'SYSTEM' AND title LIKE '%Đánh giá%'");
            jdbcTemplate.update("UPDATE notifications SET reference_type = NULL WHERE reference_type IS NOT NULL AND reference_type NOT IN ('ORDER', 'FOOD_POST', 'PAYMENT', 'REPORT')");
            
            // Fix empty string enums in reports table
            jdbcTemplate.update("UPDATE reports SET report_status = 'PENDING' WHERE report_status = '' OR report_status IS NULL");
            jdbcTemplate.update("UPDATE reports SET report_type = 'COMPLAINT' WHERE report_type = '' OR report_type IS NULL OR report_type NOT IN ('COMPLAINT', 'ISSUE', 'FEEDBACK')");
            jdbcTemplate.update("UPDATE reports SET reference_type = 'USER' WHERE reference_type = '' OR reference_type IS NULL");
            jdbcTemplate.update("UPDATE reports SET reference_type = 'USER' WHERE reference_type = 'SYSTEM' OR reference_type = 'BUSINESS_PROFILE'");
            log.info("Legacy enum data fixed.");
        } catch (Exception e) {
            log.error("Failed to fix legacy enum data", e);
        }

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
                    .profileCompleted(true)
                    .build();

            userRepository.save(admin);
            log.info(">>> Khởi tạo tài khoản ADMIN thành công: Email = {}, Phone = {}", defaultEmail, defaultPhone);
        } else {
            log.info("Tài khoản ADMIN đã tồn tại trong hệ thống. Bỏ qua bước khởi tạo.");
        }

        // Clean up the dummy demo notification
        jdbcTemplate.update("DELETE FROM notifications WHERE title = 'Đăng ký hồ sơ mới (Mẫu demo)'");
    }
}
