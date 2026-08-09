CREATE TABLE users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    phone VARCHAR(20) NULL,
    email VARCHAR(100) NULL,
    password_hash VARCHAR(255) NULL,
    full_name VARCHAR(100) NOT NULL,
    avatar_url VARCHAR(500) NULL,
    specific_address VARCHAR(500) NULL,
    role VARCHAR(20) NOT NULL,
    auth_provider VARCHAR(20) NOT NULL DEFAULT 'LOCAL',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    latitude DECIMAL(10, 7) NULL,
    longitude DECIMAL(11, 7) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_users_phone UNIQUE (phone),
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT chk_users_identity CHECK (phone IS NOT NULL OR email IS NOT NULL),
    CONSTRAINT chk_users_latitude CHECK (latitude IS NULL OR latitude BETWEEN -90 AND 90),
    CONSTRAINT chk_users_longitude CHECK (longitude IS NULL OR longitude BETWEEN -180 AND 180)
);

CREATE TABLE user_tokens (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    refresh_token VARCHAR(512) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    device_name VARCHAR(150) NULL,
    ip_address VARCHAR(45) NULL,
    last_activated_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_user_tokens_refresh_token UNIQUE (refresh_token),
    CONSTRAINT fk_user_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    INDEX idx_user_tokens_user_active (user_id, revoked, expires_at)
);

CREATE TABLE user_devices (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    fcm_token VARCHAR(500) NOT NULL,
    device_type VARCHAR(20) NOT NULL,
    device_name VARCHAR(150) NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_user_devices_fcm_token UNIQUE (fcm_token),
    CONSTRAINT fk_user_devices_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    INDEX idx_user_devices_user_active (user_id, is_active)
);

CREATE TABLE business_profiles (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    name VARCHAR(150) NOT NULL,
    description TEXT NULL,
    tax_code VARCHAR(50) NULL,
    verification_status VARCHAR(20) NOT NULL DEFAULT 'UNVERIFIED',
    profile_type VARCHAR(20) NOT NULL,
    organization_type VARCHAR(30) NULL,
    supplier_type VARCHAR(30) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_business_profiles_user UNIQUE (user_id),
    CONSTRAINT uk_business_profiles_tax_code UNIQUE (tax_code),
    CONSTRAINT fk_business_profiles_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT chk_business_profiles_type CHECK (
        (profile_type = 'SUPPLIER' AND supplier_type IS NOT NULL AND organization_type IS NULL)
        OR (profile_type = 'ORGANIZATION' AND organization_type IS NOT NULL AND supplier_type IS NULL)
    ),
    INDEX idx_business_profiles_verification (verification_status)
);

CREATE TABLE licenses (
    id BIGINT NOT NULL AUTO_INCREMENT,
    business_profile_id BIGINT NOT NULL,
    file_url VARCHAR(1000) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_licenses_business_profile FOREIGN KEY (business_profile_id)
        REFERENCES business_profiles (id) ON DELETE CASCADE,
    INDEX idx_licenses_business_profile (business_profile_id)
);

CREATE TABLE payout_accounts (
    id BIGINT NOT NULL AUTO_INCREMENT,
    business_profile_id BIGINT NOT NULL,
    bank_code VARCHAR(30) NOT NULL,
    bank_name VARCHAR(150) NOT NULL,
    account_number VARCHAR(50) NOT NULL,
    account_holder_name VARCHAR(150) NOT NULL,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_payout_accounts_owner_number UNIQUE (business_profile_id, bank_code, account_number),
    CONSTRAINT fk_payout_accounts_business_profile FOREIGN KEY (business_profile_id)
        REFERENCES business_profiles (id),
    INDEX idx_payout_accounts_owner_active (business_profile_id, is_active)
);

CREATE TABLE categories (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_categories_name UNIQUE (name)
);

CREATE TABLE food_posts (
    id BIGINT NOT NULL AUTO_INCREMENT,
    business_profile_id BIGINT NOT NULL,
    category_id BIGINT NOT NULL,
    name VARCHAR(200) NOT NULL,
    description TEXT NULL,
    total_quantity INT NOT NULL,
    available_quantity INT NOT NULL,
    unit_price DECIMAL(19, 2) NOT NULL,
    post_type VARCHAR(20) NOT NULL,
    post_status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    expires_at DATETIME(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    pickup_address VARCHAR(500) NOT NULL,
    pickup_start_at DATETIME(6) NOT NULL,
    pickup_end_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_food_posts_business_profile FOREIGN KEY (business_profile_id) REFERENCES business_profiles (id),
    CONSTRAINT fk_food_posts_category FOREIGN KEY (category_id) REFERENCES categories (id),
    CONSTRAINT chk_food_posts_quantity CHECK (
        total_quantity >= 0 AND available_quantity >= 0 AND available_quantity <= total_quantity
    ),
    CONSTRAINT chk_food_posts_unit_price CHECK (unit_price >= 0),
    CONSTRAINT chk_food_posts_type_price CHECK (
        (post_type = 'FREE' AND unit_price = 0) OR post_type = 'PAID'
    ),
    CONSTRAINT chk_food_posts_pickup_time CHECK (pickup_start_at < pickup_end_at),
    INDEX idx_food_posts_status_expiry (post_status, expires_at),
    INDEX idx_food_posts_owner_status (business_profile_id, post_status),
    INDEX idx_food_posts_category_status (category_id, post_status)
);

CREATE TABLE food_post_images (
    id BIGINT NOT NULL AUTO_INCREMENT,
    food_post_id BIGINT NOT NULL,
    image_url VARCHAR(1000) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_food_post_images_post FOREIGN KEY (food_post_id) REFERENCES food_posts (id) ON DELETE CASCADE,
    INDEX idx_food_post_images_post (food_post_id)
);

CREATE TABLE orders (
    id BIGINT NOT NULL AUTO_INCREMENT,
    order_code VARCHAR(30) NOT NULL,
    order_status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    total_amount DECIMAL(19, 2) NOT NULL,
    receiver_id BIGINT NOT NULL,
    business_profile_id BIGINT NOT NULL,
    receiver_note VARCHAR(1000) NULL,
    ready_at DATETIME(6) NULL,
    pickup_deadline DATETIME(6) NULL,
    delivered_at DATETIME(6) NULL,
    completed_at DATETIME(6) NULL,
    cancelled_at DATETIME(6) NULL,
    cancellation_reason VARCHAR(1000) NULL,
    rejected_at DATETIME(6) NULL,
    rejection_reason VARCHAR(1000) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_orders_order_code UNIQUE (order_code),
    CONSTRAINT fk_orders_receiver FOREIGN KEY (receiver_id) REFERENCES users (id),
    CONSTRAINT fk_orders_business_profile FOREIGN KEY (business_profile_id) REFERENCES business_profiles (id),
    CONSTRAINT chk_orders_total_amount CHECK (total_amount >= 0),
    INDEX idx_orders_receiver_status_created (receiver_id, order_status, created_at),
    INDEX idx_orders_owner_status_created (business_profile_id, order_status, created_at)
);

CREATE TABLE order_details (
    id BIGINT NOT NULL AUTO_INCREMENT,
    order_id BIGINT NOT NULL,
    food_post_id BIGINT NOT NULL,
    unit_price DECIMAL(19, 2) NOT NULL,
    quantity INT NOT NULL,
    subtotal DECIMAL(19, 2) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_order_detail_order_post UNIQUE (order_id, food_post_id),
    CONSTRAINT fk_order_details_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE,
    CONSTRAINT fk_order_details_food_post FOREIGN KEY (food_post_id) REFERENCES food_posts (id),
    CONSTRAINT chk_order_details_quantity CHECK (quantity > 0),
    CONSTRAINT chk_order_details_amount CHECK (unit_price >= 0 AND subtotal >= 0),
    INDEX idx_order_details_food_post (food_post_id)
);

CREATE TABLE payments (
    id BIGINT NOT NULL AUTO_INCREMENT,
    order_id BIGINT NOT NULL,
    amount DECIMAL(19, 2) NOT NULL,
    method VARCHAR(20) NOT NULL,
    payment_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    external_transaction_id VARCHAR(255) NULL,
    paid_at DATETIME(6) NULL,
    provider VARCHAR(50) NULL,
    transfer_content VARCHAR(255) NULL,
    expires_at DATETIME(6) NULL,
    refund_transaction_id VARCHAR(255) NULL,
    refunded_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_payments_external_transaction UNIQUE (external_transaction_id),
    CONSTRAINT uk_payments_refund_transaction UNIQUE (refund_transaction_id),
    CONSTRAINT fk_payments_order FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT chk_payments_amount CHECK (amount >= 0),
    INDEX idx_payments_order_status (order_id, payment_status)
);

CREATE TABLE payouts (
    id BIGINT NOT NULL AUTO_INCREMENT,
    order_id BIGINT NOT NULL,
    payout_account_id BIGINT NOT NULL,
    payout_code VARCHAR(30) NOT NULL,
    gross_amount DECIMAL(19, 2) NOT NULL,
    platform_fee DECIMAL(19, 2) NOT NULL,
    net_amount DECIMAL(19, 2) NOT NULL,
    payout_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    external_transaction_id VARCHAR(255) NULL,
    completed_at DATETIME(6) NULL,
    failed_at DATETIME(6) NULL,
    retry_count INT NOT NULL DEFAULT 0,
    failure_reason VARCHAR(1000) NULL,
    note VARCHAR(1000) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_payouts_order UNIQUE (order_id),
    CONSTRAINT uk_payouts_code UNIQUE (payout_code),
    CONSTRAINT uk_payouts_external_transaction UNIQUE (external_transaction_id),
    CONSTRAINT fk_payouts_order FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT fk_payouts_account FOREIGN KEY (payout_account_id) REFERENCES payout_accounts (id),
    CONSTRAINT chk_payouts_amount CHECK (
        gross_amount >= 0 AND platform_fee >= 0 AND net_amount >= 0
        AND net_amount = gross_amount - platform_fee
    ),
    CONSTRAINT chk_payouts_retry_count CHECK (retry_count >= 0),
    INDEX idx_payouts_account_status (payout_account_id, payout_status)
);

CREATE TABLE reviews (
    id BIGINT NOT NULL AUTO_INCREMENT,
    order_id BIGINT NOT NULL,
    reviewer_id BIGINT NOT NULL,
    business_profile_id BIGINT NOT NULL,
    rating INT NOT NULL,
    comment VARCHAR(1000) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_reviews_order UNIQUE (order_id),
    CONSTRAINT fk_reviews_order FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT fk_reviews_reviewer FOREIGN KEY (reviewer_id) REFERENCES users (id),
    CONSTRAINT fk_reviews_business_profile FOREIGN KEY (business_profile_id) REFERENCES business_profiles (id),
    CONSTRAINT chk_reviews_rating CHECK (rating BETWEEN 1 AND 5),
    INDEX idx_reviews_business_profile (business_profile_id, created_at)
);

CREATE TABLE notifications (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    notification_type VARCHAR(20) NOT NULL,
    reference_type VARCHAR(20) NULL,
    reference_id BIGINT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    INDEX idx_notifications_user_read_created (user_id, is_read, created_at),
    INDEX idx_notifications_reference (reference_type, reference_id)
);

CREATE TABLE reports (
    id BIGINT NOT NULL AUTO_INCREMENT,
    reporter_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    report_type VARCHAR(20) NOT NULL,
    response TEXT NULL,
    evidence_url VARCHAR(1000) NULL,
    reference_type VARCHAR(20) NOT NULL,
    reference_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_reports_reporter FOREIGN KEY (reporter_id) REFERENCES users (id),
    INDEX idx_reports_reporter_created (reporter_id, created_at),
    INDEX idx_reports_reference (reference_type, reference_id)
);
