CREATE TABLE system_configs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    config_key VARCHAR(100) NOT NULL UNIQUE,
    config_value VARCHAR(2000) NOT NULL,
    description VARCHAR(500),
    data_type VARCHAR(20) NOT NULL DEFAULT 'STRING',
    is_public BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)
);

INSERT INTO system_configs (config_key, config_value, description, data_type, is_public) VALUES
('PLATFORM_FEE_PERCENTAGE', '0.05', 'Phí hoa hồng thu của cửa hàng (0.05 = 5%)', 'NUMBER', false),
('MIN_PAYOUT_AMOUNT', '50000', 'Số dư khả dụng tối thiểu để tạo lệnh rút tiền (VNĐ)', 'NUMBER', true),
('MAX_PAYOUT_AMOUNT', '20000000', 'Số tiền rút tối đa cho một giao dịch (VNĐ)', 'NUMBER', true),
('HOTLINE_SUPPORT', '19001560', 'Số điện thoại tổng đài hỗ trợ', 'STRING', true),
('CONTACT_EMAIL', 'support@foodshare.vn', 'Email liên hệ chăm sóc khách hàng', 'STRING', true),
('MAINTENANCE_MODE', 'false', 'Bật chế độ bảo trì toàn bộ hệ thống (true/false)', 'BOOLEAN', true);
