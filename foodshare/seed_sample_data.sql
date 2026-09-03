-- ========================================================================
-- SCRIPT LÀM SẠCH VÀ TẠO DỮ LIỆU MẪU CHO FOODSHARE
-- ========================================================================

-- TẮT KIỂM TRA FOREIGN KEY ĐỂ XOÁ SẠCH DỮ LIỆU BẢNG NGHIỆP VỤ
SET FOREIGN_KEY_CHECKS = 0;

TRUNCATE TABLE reports;
TRUNCATE TABLE notifications;
TRUNCATE TABLE payouts;
TRUNCATE TABLE reviews;
TRUNCATE TABLE payments;
TRUNCATE TABLE order_details;
TRUNCATE TABLE orders;
TRUNCATE TABLE payout_accounts;
TRUNCATE TABLE licenses;
TRUNCATE TABLE food_post_images;
TRUNCATE TABLE food_posts;

-- BẬT LẠI KIỂM TRA FOREIGN KEY
SET FOREIGN_KEY_CHECKS = 1;

-- Cấu hình User IDs
SET @admin_id = 1;
SET @recipient_id = 2;
SET @supplier_user_id = 3;

-- Lấy ID của Business Profile tương ứng với User 3 (Quyền Phạm Anh - Supplier)
SET @bp_id = (SELECT id FROM business_profiles WHERE user_id = @supplier_user_id LIMIT 1);

-- ==========================================
-- 1. BÀI ĐĂNG (food_posts)
-- ==========================================
INSERT IGNORE INTO food_posts (business_profile_id, category_id, name, description, total_quantity, available_quantity, unit_price, original_price, post_type, post_status, expires_at, pickup_start_at, pickup_end_at, pickup_address, version, created_at, updated_at)
SELECT @bp_id, 1, 'Cơm rang dưa bò', 'Cơm rang còn dư cuối ngày rất ngon', 5, 5, 20000, 35000, 'PAID', 'AVAILABLE', DATE_ADD(NOW(), INTERVAL 1 DAY), NOW(), DATE_ADD(NOW(), INTERVAL 4 HOUR), '928 Lê Văn Lương', 0, NOW(), NOW() FROM dual WHERE @bp_id IS NOT NULL UNION ALL
SELECT @bp_id, 2, 'Bún đậu mắm tôm', 'Bún đậu mẹt đầy đủ', 10, 8, 15000, 30000, 'PAID', 'AVAILABLE', DATE_ADD(NOW(), INTERVAL 2 DAY), NOW(), DATE_ADD(NOW(), INTERVAL 5 HOUR), '928 Lê Văn Lương', 0, NOW(), NOW() FROM dual WHERE @bp_id IS NOT NULL UNION ALL
SELECT @bp_id, 3, 'Bánh mì thịt nướng', 'Bánh mì pate thịt nướng', 20, 20, 10000, 20000, 'PAID', 'AVAILABLE', DATE_ADD(NOW(), INTERVAL 3 DAY), NOW(), DATE_ADD(NOW(), INTERVAL 2 HOUR), '928 Lê Văn Lương', 0, NOW(), NOW() FROM dual WHERE @bp_id IS NOT NULL UNION ALL
SELECT @bp_id, 4, 'Trà sữa trân châu', 'Trà sữa làm lố số lượng', 15, 5, 12000, 25000, 'PAID', 'AVAILABLE', DATE_ADD(NOW(), INTERVAL 1 DAY), NOW(), DATE_ADD(NOW(), INTERVAL 6 HOUR), '928 Lê Văn Lương', 0, NOW(), NOW() FROM dual WHERE @bp_id IS NOT NULL UNION ALL
SELECT @bp_id, 5, 'Rau củ hỗn hợp', 'Rau xanh bán chậm trong ngày', 30, 30, 0, NULL, 'FREE', 'AVAILABLE', DATE_ADD(NOW(), INTERVAL 1 DAY), NOW(), DATE_ADD(NOW(), INTERVAL 3 HOUR), '928 Lê Văn Lương', 0, NOW(), NOW() FROM dual WHERE @bp_id IS NOT NULL UNION ALL
SELECT @bp_id, 1, 'Phở bò tái nạm', 'Nước dùng phở và bánh phở riêng', 8, 0, 25000, 45000, 'PAID', 'OUT_OF_STOCK', DATE_ADD(NOW(), INTERVAL 1 DAY), NOW(), DATE_ADD(NOW(), INTERVAL 2 HOUR), '928 Lê Văn Lương', 0, NOW(), NOW() FROM dual WHERE @bp_id IS NOT NULL UNION ALL
SELECT @bp_id, 2, 'Gà rán mật ong', 'Gà rán tẩm mật ong cực ngon', 12, 12, 22000, 40000, 'PAID', 'HIDDEN', DATE_ADD(NOW(), INTERVAL 2 DAY), NOW(), DATE_ADD(NOW(), INTERVAL 8 HOUR), '928 Lê Văn Lương', 0, NOW(), NOW() FROM dual WHERE @bp_id IS NOT NULL UNION ALL
SELECT @bp_id, 3, 'Bánh bao xá xíu', 'Bánh bao hấp nóng', 50, 50, 5000, 12000, 'PAID', 'DRAFT', DATE_ADD(NOW(), INTERVAL 5 DAY), NOW(), DATE_ADD(NOW(), INTERVAL 10 HOUR), '928 Lê Văn Lương', 0, NOW(), NOW() FROM dual WHERE @bp_id IS NOT NULL UNION ALL
SELECT @bp_id, 4, 'Nước ép dưa hấu', 'Nước ép thanh mát', 10, 2, 8000, 15000, 'PAID', 'AVAILABLE', DATE_ADD(NOW(), INTERVAL 1 DAY), NOW(), DATE_ADD(NOW(), INTERVAL 4 HOUR), '928 Lê Văn Lương', 0, NOW(), NOW() FROM dual WHERE @bp_id IS NOT NULL UNION ALL
SELECT @bp_id, 5, 'Trái cây gọt sẵn', 'Xoài, ổi, mận gọt sẵn', 15, 15, 0, NULL, 'FREE', 'AVAILABLE', DATE_ADD(NOW(), INTERVAL 1 DAY), NOW(), DATE_ADD(NOW(), INTERVAL 4 HOUR), '928 Lê Văn Lương', 0, NOW(), NOW() FROM dual WHERE @bp_id IS NOT NULL;

-- ==========================================
-- 2. ẢNH BÀI ĐĂNG (food_post_images)
-- ==========================================
INSERT IGNORE INTO food_post_images (food_post_id, image_url, created_at, updated_at)
SELECT id, 'https://images.unsplash.com/photo-1546069901-ba9599a7e63c?w=500&h=500&fit=crop', NOW(), NOW() FROM food_posts WHERE business_profile_id = @bp_id;

-- ==========================================
-- 3. CHỨNG CHỈ / HỒ SƠ (licenses)
-- ==========================================
INSERT IGNORE INTO licenses (business_profile_id, file_url, created_at, updated_at) VALUES 
(@bp_id, 'https://example.com/license1.pdf', NOW(), NOW()),
(@bp_id, 'https://example.com/license2.pdf', NOW(), NOW()),
(@bp_id, 'https://example.com/license3.pdf', NOW(), NOW()),
(@bp_id, 'https://example.com/license4.pdf', NOW(), NOW()),
(@bp_id, 'https://example.com/license5.pdf', NOW(), NOW()),
(@bp_id, 'https://example.com/license6.pdf', NOW(), NOW()),
(@bp_id, 'https://example.com/license7.pdf', NOW(), NOW()),
(@bp_id, 'https://example.com/license8.pdf', NOW(), NOW()),
(@bp_id, 'https://example.com/license9.pdf', NOW(), NOW()),
(@bp_id, 'https://example.com/license10.pdf', NOW(), NOW());

-- ==========================================
-- 4. TÀI KHOẢN NHẬN TIỀN (payout_accounts)
-- ==========================================
INSERT IGNORE INTO payout_accounts (business_profile_id, bank_code, bank_name, account_number, account_holder_name, is_default, is_active, created_at, updated_at) VALUES 
(@bp_id, 'VCB', 'Vietcombank', '0011001234567', 'Quyền Phạm Anh', 1, 1, NOW(), NOW()),
(@bp_id, 'TCB', 'Techcombank', '1903456789012', 'Quyền Phạm Anh', 0, 1, NOW(), NOW()),
(@bp_id, 'MB', 'MBBank', '0987654321', 'Quyền Phạm Anh', 0, 1, NOW(), NOW()),
(@bp_id, 'ACB', 'ACB', '12345678', 'Quyền Phạm Anh', 0, 1, NOW(), NOW()),
(@bp_id, 'BIDV', 'BIDV', '87654321', 'Quyền Phạm Anh', 0, 1, NOW(), NOW()),
(@bp_id, 'VPB', 'VPBank', '1122334455', 'Quyền Phạm Anh', 0, 1, NOW(), NOW()),
(@bp_id, 'STB', 'Sacombank', '9988776655', 'Quyền Phạm Anh', 0, 1, NOW(), NOW()),
(@bp_id, 'VIB', 'VIB', '33445566', 'Quyền Phạm Anh', 0, 1, NOW(), NOW()),
(@bp_id, 'TPB', 'TPBank', '77889900', 'Quyền Phạm Anh', 0, 1, NOW(), NOW()),
(@bp_id, 'OCB', 'OCB', '55667788', 'Quyền Phạm Anh', 0, 1, NOW(), NOW());

-- ==========================================
-- 5. ĐƠN HÀNG (orders)
-- ==========================================
INSERT IGNORE INTO orders (order_code, order_status, total_amount, receiver_id, business_profile_id, receiver_note, created_at, updated_at)
VALUES ('ORD-TEST-001', 'PENDING', 40000, @recipient_id, @bp_id, 'Đến lấy sau 5h chiều', DATE_SUB(NOW(), INTERVAL 1 HOUR), NOW());
INSERT IGNORE INTO orders (order_code, order_status, total_amount, receiver_id, business_profile_id, receiver_note, created_at, updated_at)
VALUES ('ORD-TEST-002', 'CONFIRMED', 30000, @recipient_id, @bp_id, 'Vui lòng gói kỹ', DATE_SUB(NOW(), INTERVAL 2 HOUR), NOW());
INSERT IGNORE INTO orders (order_code, order_status, total_amount, receiver_id, business_profile_id, receiver_note, created_at, updated_at)
VALUES ('ORD-TEST-003', 'PREPARING', 20000, @recipient_id, @bp_id, '', DATE_SUB(NOW(), INTERVAL 3 HOUR), NOW());
INSERT IGNORE INTO orders (order_code, order_status, total_amount, receiver_id, business_profile_id, receiver_note, ready_at, created_at, updated_at)
VALUES ('ORD-TEST-004', 'READY_FOR_PICKUP', 24000, @recipient_id, @bp_id, 'Đã đến trước cửa', NOW(), DATE_SUB(NOW(), INTERVAL 4 HOUR), NOW());

-- Đơn 5 đến 10: COMPLETED
INSERT IGNORE INTO orders (order_code, order_status, total_amount, receiver_id, business_profile_id, receiver_note, completed_at, created_at, updated_at)
VALUES ('ORD-TEST-005', 'COMPLETED', 15000, @recipient_id, @bp_id, '', DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY));
INSERT IGNORE INTO orders (order_code, order_status, total_amount, receiver_id, business_profile_id, receiver_note, completed_at, created_at, updated_at)
VALUES ('ORD-TEST-006', 'COMPLETED', 45000, @recipient_id, @bp_id, '', DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY));
INSERT IGNORE INTO orders (order_code, order_status, total_amount, receiver_id, business_profile_id, receiver_note, completed_at, created_at, updated_at)
VALUES ('ORD-TEST-007', 'COMPLETED', 100000, @recipient_id, @bp_id, '', DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY));
INSERT IGNORE INTO orders (order_code, order_status, total_amount, receiver_id, business_profile_id, receiver_note, completed_at, created_at, updated_at)
VALUES ('ORD-TEST-008', 'COMPLETED', 50000, @recipient_id, @bp_id, '', DATE_SUB(NOW(), INTERVAL 4 DAY), DATE_SUB(NOW(), INTERVAL 4 DAY), DATE_SUB(NOW(), INTERVAL 4 DAY));
INSERT IGNORE INTO orders (order_code, order_status, total_amount, receiver_id, business_profile_id, receiver_note, completed_at, created_at, updated_at)
VALUES ('ORD-TEST-009', 'COMPLETED', 75000, @recipient_id, @bp_id, '', DATE_SUB(NOW(), INTERVAL 5 DAY), DATE_SUB(NOW(), INTERVAL 5 DAY), DATE_SUB(NOW(), INTERVAL 5 DAY));
INSERT IGNORE INTO orders (order_code, order_status, total_amount, receiver_id, business_profile_id, receiver_note, completed_at, created_at, updated_at)
VALUES ('ORD-TEST-010', 'COMPLETED', 20000, @recipient_id, @bp_id, '', DATE_SUB(NOW(), INTERVAL 6 DAY), DATE_SUB(NOW(), INTERVAL 6 DAY), DATE_SUB(NOW(), INTERVAL 6 DAY));

-- Đơn 11-12: Hủy/Từ chối
INSERT IGNORE INTO orders (order_code, order_status, total_amount, receiver_id, business_profile_id, cancellation_reason, cancelled_at, created_at, updated_at)
VALUES ('ORD-TEST-011', 'CANCELLED', 25000, @recipient_id, @bp_id, 'Đổi ý không mua nữa', DATE_SUB(NOW(), INTERVAL 5 HOUR), DATE_SUB(NOW(), INTERVAL 6 HOUR), NOW());
INSERT IGNORE INTO orders (order_code, order_status, total_amount, receiver_id, business_profile_id, rejection_reason, rejected_at, created_at, updated_at)
VALUES ('ORD-TEST-012', 'REJECTED', 50000, @recipient_id, @bp_id, 'Hết món', DATE_SUB(NOW(), INTERVAL 10 HOUR), DATE_SUB(NOW(), INTERVAL 11 HOUR), NOW());

-- ==========================================
-- 6. CHI TIẾT ĐƠN HÀNG (order_details) (Không có subtotal nữa)
-- ==========================================
INSERT IGNORE INTO order_details (order_id, food_post_id, unit_price, quantity, created_at, updated_at)
SELECT o.id, p.id, 20000, 2, NOW(), NOW() FROM orders o JOIN food_posts p ON p.name = 'Cơm rang dưa bò' WHERE o.order_code = 'ORD-TEST-001' LIMIT 1;
INSERT IGNORE INTO order_details (order_id, food_post_id, unit_price, quantity, created_at, updated_at)
SELECT o.id, p.id, 15000, 2, NOW(), NOW() FROM orders o JOIN food_posts p ON p.name = 'Bún đậu mắm tôm' WHERE o.order_code = 'ORD-TEST-002' LIMIT 1;
INSERT IGNORE INTO order_details (order_id, food_post_id, unit_price, quantity, created_at, updated_at)
SELECT o.id, p.id, 20000, 1, NOW(), NOW() FROM orders o JOIN food_posts p ON p.name = 'Cơm rang dưa bò' WHERE o.order_code = 'ORD-TEST-003' LIMIT 1;
INSERT IGNORE INTO order_details (order_id, food_post_id, unit_price, quantity, created_at, updated_at)
SELECT o.id, p.id, 12000, 2, NOW(), NOW() FROM orders o JOIN food_posts p ON p.name = 'Bún đậu mắm tôm' WHERE o.order_code = 'ORD-TEST-004' LIMIT 1;
INSERT IGNORE INTO order_details (order_id, food_post_id, unit_price, quantity, created_at, updated_at)
SELECT o.id, p.id, 15000, 1, NOW(), NOW() FROM orders o JOIN food_posts p ON p.name = 'Cơm rang dưa bò' WHERE o.order_code = 'ORD-TEST-005' LIMIT 1;
INSERT IGNORE INTO order_details (order_id, food_post_id, unit_price, quantity, created_at, updated_at)
SELECT o.id, p.id, 15000, 3, NOW(), NOW() FROM orders o JOIN food_posts p ON p.name = 'Bún đậu mắm tôm' WHERE o.order_code = 'ORD-TEST-006' LIMIT 1;
INSERT IGNORE INTO order_details (order_id, food_post_id, unit_price, quantity, created_at, updated_at)
SELECT o.id, p.id, 20000, 5, NOW(), NOW() FROM orders o JOIN food_posts p ON p.name = 'Cơm rang dưa bò' WHERE o.order_code = 'ORD-TEST-007' LIMIT 1;
INSERT IGNORE INTO order_details (order_id, food_post_id, unit_price, quantity, created_at, updated_at)
SELECT o.id, p.id, 25000, 2, NOW(), NOW() FROM orders o JOIN food_posts p ON p.name = 'Bún đậu mắm tôm' WHERE o.order_code = 'ORD-TEST-008' LIMIT 1;
INSERT IGNORE INTO order_details (order_id, food_post_id, unit_price, quantity, created_at, updated_at)
SELECT o.id, p.id, 25000, 3, NOW(), NOW() FROM orders o JOIN food_posts p ON p.name = 'Cơm rang dưa bò' WHERE o.order_code = 'ORD-TEST-009' LIMIT 1;
INSERT IGNORE INTO order_details (order_id, food_post_id, unit_price, quantity, created_at, updated_at)
SELECT o.id, p.id, 10000, 2, NOW(), NOW() FROM orders o JOIN food_posts p ON p.name = 'Bún đậu mắm tôm' WHERE o.order_code = 'ORD-TEST-010' LIMIT 1;
INSERT IGNORE INTO order_details (order_id, food_post_id, unit_price, quantity, created_at, updated_at)
SELECT o.id, p.id, 25000, 1, NOW(), NOW() FROM orders o JOIN food_posts p ON p.name = 'Cơm rang dưa bò' WHERE o.order_code = 'ORD-TEST-011' LIMIT 1;
INSERT IGNORE INTO order_details (order_id, food_post_id, unit_price, quantity, created_at, updated_at)
SELECT o.id, p.id, 25000, 2, NOW(), NOW() FROM orders o JOIN food_posts p ON p.name = 'Bún đậu mắm tôm' WHERE o.order_code = 'ORD-TEST-012' LIMIT 1;

-- ==========================================
-- 7. THANH TOÁN (payments)
-- ==========================================
INSERT IGNORE INTO payments (order_id, amount, method, payment_status, external_transaction_id, paid_at, provider, created_at, updated_at)
SELECT id, 15000, 'VNPAY', 'COMPLETED', 'VNP-005', NOW(), 'VNPAY', NOW(), NOW() FROM orders WHERE order_code = 'ORD-TEST-005';
INSERT IGNORE INTO payments (order_id, amount, method, payment_status, external_transaction_id, paid_at, provider, created_at, updated_at)
SELECT id, 45000, 'MOMO', 'COMPLETED', 'MM-006', NOW(), 'MOMO', NOW(), NOW() FROM orders WHERE order_code = 'ORD-TEST-006';
INSERT IGNORE INTO payments (order_id, amount, method, payment_status, external_transaction_id, paid_at, provider, created_at, updated_at)
SELECT id, 100000, 'VNPAY', 'COMPLETED', 'VNP-007', NOW(), 'VNPAY', NOW(), NOW() FROM orders WHERE order_code = 'ORD-TEST-007';
INSERT IGNORE INTO payments (order_id, amount, method, payment_status, external_transaction_id, paid_at, provider, created_at, updated_at)
SELECT id, 50000, 'CASH', 'COMPLETED', 'CASH-008', NOW(), 'CASH', NOW(), NOW() FROM orders WHERE order_code = 'ORD-TEST-008';
INSERT IGNORE INTO payments (order_id, amount, method, payment_status, external_transaction_id, paid_at, provider, created_at, updated_at)
SELECT id, 75000, 'MOMO', 'COMPLETED', 'MM-009', NOW(), 'MOMO', NOW(), NOW() FROM orders WHERE order_code = 'ORD-TEST-009';
INSERT IGNORE INTO payments (order_id, amount, method, payment_status, external_transaction_id, paid_at, provider, created_at, updated_at)
SELECT id, 20000, 'VNPAY', 'COMPLETED', 'VNP-010', NOW(), 'VNPAY', NOW(), NOW() FROM orders WHERE order_code = 'ORD-TEST-010';
INSERT IGNORE INTO payments (order_id, amount, method, payment_status, external_transaction_id, paid_at, provider, created_at, updated_at)
SELECT id, 40000, 'VNPAY', 'PENDING', 'VNP-001', NULL, 'VNPAY', NOW(), NOW() FROM orders WHERE order_code = 'ORD-TEST-001';
INSERT IGNORE INTO payments (order_id, amount, method, payment_status, external_transaction_id, paid_at, provider, created_at, updated_at)
SELECT id, 30000, 'MOMO', 'COMPLETED', 'MM-002', NOW(), 'MOMO', NOW(), NOW() FROM orders WHERE order_code = 'ORD-TEST-002';
INSERT IGNORE INTO payments (order_id, amount, method, payment_status, external_transaction_id, paid_at, provider, created_at, updated_at)
SELECT id, 20000, 'CASH', 'PENDING', 'CASH-003', NULL, 'CASH', NOW(), NOW() FROM orders WHERE order_code = 'ORD-TEST-003';
INSERT IGNORE INTO payments (order_id, amount, method, payment_status, external_transaction_id, paid_at, provider, created_at, updated_at)
SELECT id, 24000, 'VNPAY', 'COMPLETED', 'VNP-004', NOW(), 'VNPAY', NOW(), NOW() FROM orders WHERE order_code = 'ORD-TEST-004';

-- ==========================================
-- 8. ĐÁNH GIÁ (reviews)
-- ==========================================
INSERT IGNORE INTO reviews (order_id, reviewer_id, business_profile_id, rating, comment, created_at, updated_at)
SELECT id, @recipient_id, @bp_id, 5, 'Đồ ăn rất ngon, chủ quán thân thiện!', DATE_SUB(NOW(), INTERVAL 1 DAY), NOW() FROM orders WHERE order_code = 'ORD-TEST-005';
INSERT IGNORE INTO reviews (order_id, reviewer_id, business_profile_id, rating, comment, created_at, updated_at)
SELECT id, @recipient_id, @bp_id, 4, 'Bún ngon nhưng mắm tôm hơi mặn.', DATE_SUB(NOW(), INTERVAL 2 DAY), NOW() FROM orders WHERE order_code = 'ORD-TEST-006';
INSERT IGNORE INTO reviews (order_id, reviewer_id, business_profile_id, rating, comment, created_at, updated_at)
SELECT id, @recipient_id, @bp_id, 5, 'Tuyệt vời, sẽ ủng hộ tiếp', DATE_SUB(NOW(), INTERVAL 3 DAY), NOW() FROM orders WHERE order_code = 'ORD-TEST-007';
INSERT IGNORE INTO reviews (order_id, reviewer_id, business_profile_id, rating, comment, created_at, updated_at)
SELECT id, @recipient_id, @bp_id, 3, 'Bình thường, không có gì đặc sắc.', DATE_SUB(NOW(), INTERVAL 4 DAY), NOW() FROM orders WHERE order_code = 'ORD-TEST-008';
INSERT IGNORE INTO reviews (order_id, reviewer_id, business_profile_id, rating, comment, created_at, updated_at)
SELECT id, @recipient_id, @bp_id, 5, 'Ăn no nê, giá siêu rẻ!', DATE_SUB(NOW(), INTERVAL 5 DAY), NOW() FROM orders WHERE order_code = 'ORD-TEST-009';
INSERT IGNORE INTO reviews (order_id, reviewer_id, business_profile_id, rating, comment, created_at, updated_at)
SELECT id, @recipient_id, @bp_id, 5, 'Gói hàng rất cẩn thận, cảm ơn quán.', DATE_SUB(NOW(), INTERVAL 6 DAY), NOW() FROM orders WHERE order_code = 'ORD-TEST-010';
INSERT IGNORE INTO reviews (order_id, reviewer_id, business_profile_id, rating, comment, created_at, updated_at)
SELECT id, @recipient_id, @bp_id, 4, 'Ngon nhưng đợi hơi lâu.', DATE_SUB(NOW(), INTERVAL 7 DAY), NOW() FROM orders WHERE order_code = 'ORD-TEST-002';
INSERT IGNORE INTO reviews (order_id, reviewer_id, business_profile_id, rating, comment, created_at, updated_at)
SELECT id, @recipient_id, @bp_id, 5, 'Rất hài lòng!', DATE_SUB(NOW(), INTERVAL 8 DAY), NOW() FROM orders WHERE order_code = 'ORD-TEST-004';
INSERT IGNORE INTO reviews (order_id, reviewer_id, business_profile_id, rating, comment, created_at, updated_at)
SELECT id, @recipient_id, @bp_id, 1, 'Hủy đơn vô cớ', DATE_SUB(NOW(), INTERVAL 9 DAY), NOW() FROM orders WHERE order_code = 'ORD-TEST-011';
INSERT IGNORE INTO reviews (order_id, reviewer_id, business_profile_id, rating, comment, created_at, updated_at)
SELECT id, @recipient_id, @bp_id, 1, 'Hết món mà không báo sớm', DATE_SUB(NOW(), INTERVAL 10 DAY), NOW() FROM orders WHERE order_code = 'ORD-TEST-012';

-- ==========================================
-- 9. RÚT TIỀN PAYOUTS (payouts)
-- ==========================================
INSERT IGNORE INTO payouts (order_id, payout_account_id, payout_code, gross_amount, platform_fee, net_amount, payout_status, completed_at, created_at, updated_at)
SELECT o.id, p.id, 'PO-005', 15000, 1500, 13500, 'COMPLETED', NOW(), NOW(), NOW() FROM orders o JOIN payout_accounts p ON p.bank_code = 'VCB' AND p.business_profile_id = @bp_id WHERE o.order_code = 'ORD-TEST-005' LIMIT 1;
INSERT IGNORE INTO payouts (order_id, payout_account_id, payout_code, gross_amount, platform_fee, net_amount, payout_status, completed_at, created_at, updated_at)
SELECT o.id, p.id, 'PO-006', 45000, 4500, 40500, 'COMPLETED', NOW(), NOW(), NOW() FROM orders o JOIN payout_accounts p ON p.bank_code = 'VCB' AND p.business_profile_id = @bp_id WHERE o.order_code = 'ORD-TEST-006' LIMIT 1;
INSERT IGNORE INTO payouts (order_id, payout_account_id, payout_code, gross_amount, platform_fee, net_amount, payout_status, completed_at, created_at, updated_at)
SELECT o.id, p.id, 'PO-007', 100000, 10000, 90000, 'PENDING', NULL, NOW(), NOW() FROM orders o JOIN payout_accounts p ON p.bank_code = 'VCB' AND p.business_profile_id = @bp_id WHERE o.order_code = 'ORD-TEST-007' LIMIT 1;
INSERT IGNORE INTO payouts (order_id, payout_account_id, payout_code, gross_amount, platform_fee, net_amount, payout_status, completed_at, created_at, updated_at)
SELECT o.id, p.id, 'PO-008', 50000, 5000, 45000, 'COMPLETED', NOW(), NOW(), NOW() FROM orders o JOIN payout_accounts p ON p.bank_code = 'VCB' AND p.business_profile_id = @bp_id WHERE o.order_code = 'ORD-TEST-008' LIMIT 1;
INSERT IGNORE INTO payouts (order_id, payout_account_id, payout_code, gross_amount, platform_fee, net_amount, payout_status, completed_at, created_at, updated_at)
SELECT o.id, p.id, 'PO-009', 75000, 7500, 67500, 'PENDING', NULL, NOW(), NOW() FROM orders o JOIN payout_accounts p ON p.bank_code = 'VCB' AND p.business_profile_id = @bp_id WHERE o.order_code = 'ORD-TEST-009' LIMIT 1;
INSERT IGNORE INTO payouts (order_id, payout_account_id, payout_code, gross_amount, platform_fee, net_amount, payout_status, completed_at, created_at, updated_at)
SELECT o.id, p.id, 'PO-010', 20000, 2000, 18000, 'FAILED', NULL, NOW(), NOW() FROM orders o JOIN payout_accounts p ON p.bank_code = 'VCB' AND p.business_profile_id = @bp_id WHERE o.order_code = 'ORD-TEST-010' LIMIT 1;
INSERT IGNORE INTO payouts (order_id, payout_account_id, payout_code, gross_amount, platform_fee, net_amount, payout_status, completed_at, created_at, updated_at)
SELECT o.id, p.id, 'PO-011', 25000, 2500, 22500, 'PENDING', NULL, NOW(), NOW() FROM orders o JOIN payout_accounts p ON p.bank_code = 'VCB' AND p.business_profile_id = @bp_id WHERE o.order_code = 'ORD-TEST-011' LIMIT 1;
INSERT IGNORE INTO payouts (order_id, payout_account_id, payout_code, gross_amount, platform_fee, net_amount, payout_status, completed_at, created_at, updated_at)
SELECT o.id, p.id, 'PO-012', 50000, 5000, 45000, 'COMPLETED', NOW(), NOW(), NOW() FROM orders o JOIN payout_accounts p ON p.bank_code = 'VCB' AND p.business_profile_id = @bp_id WHERE o.order_code = 'ORD-TEST-012' LIMIT 1;
INSERT IGNORE INTO payouts (order_id, payout_account_id, payout_code, gross_amount, platform_fee, net_amount, payout_status, completed_at, created_at, updated_at)
SELECT o.id, p.id, 'PO-001', 40000, 4000, 36000, 'PENDING', NULL, NOW(), NOW() FROM orders o JOIN payout_accounts p ON p.bank_code = 'VCB' AND p.business_profile_id = @bp_id WHERE o.order_code = 'ORD-TEST-001' LIMIT 1;
INSERT IGNORE INTO payouts (order_id, payout_account_id, payout_code, gross_amount, platform_fee, net_amount, payout_status, completed_at, created_at, updated_at)
SELECT o.id, p.id, 'PO-002', 30000, 3000, 27000, 'PENDING', NULL, NOW(), NOW() FROM orders o JOIN payout_accounts p ON p.bank_code = 'VCB' AND p.business_profile_id = @bp_id WHERE o.order_code = 'ORD-TEST-002' LIMIT 1;

-- ==========================================
-- 10. THÔNG BÁO (notifications)
-- ==========================================
INSERT IGNORE INTO notifications (user_id, title, content, is_read, notification_type, reference_type, reference_id, created_at, updated_at) 
SELECT @supplier_user_id, 'Đơn hàng mới', 'Bạn có đơn hàng mới từ ORD-TEST-001', 0, 'ORDER_CREATED', 'ORDER', id, NOW(), NOW() FROM orders WHERE order_code = 'ORD-TEST-001' LIMIT 1;
INSERT IGNORE INTO notifications (user_id, title, content, is_read, notification_type, reference_type, reference_id, created_at, updated_at) 
SELECT @supplier_user_id, 'Đơn hàng mới', 'Bạn có đơn hàng mới từ ORD-TEST-002', 1, 'ORDER_CREATED', 'ORDER', id, NOW(), NOW() FROM orders WHERE order_code = 'ORD-TEST-002' LIMIT 1;
INSERT IGNORE INTO notifications (user_id, title, content, is_read, notification_type, reference_type, reference_id, created_at, updated_at) 
SELECT @supplier_user_id, 'Thanh toán thành công', 'Thanh toán cho ORD-TEST-005 đã hoàn tất', 0, 'PAYMENT_RECEIVED', 'ORDER', id, NOW(), NOW() FROM orders WHERE order_code = 'ORD-TEST-005' LIMIT 1;
INSERT IGNORE INTO notifications (user_id, title, content, is_read, notification_type, reference_type, reference_id, created_at, updated_at) 
SELECT @supplier_user_id, 'Đơn bị hủy', 'Đơn hàng ORD-TEST-011 đã bị hủy', 1, 'ORDER_CANCELLED', 'ORDER', id, NOW(), NOW() FROM orders WHERE order_code = 'ORD-TEST-011' LIMIT 1;
INSERT IGNORE INTO notifications (user_id, title, content, is_read, notification_type, reference_type, reference_id, created_at, updated_at) 
SELECT @supplier_user_id, 'Rút tiền thất bại', 'Rút tiền PO-010 bị lỗi', 0, 'PAYOUT_FAILED', 'PAYOUT', id, NOW(), NOW() FROM orders WHERE order_code = 'ORD-TEST-010' LIMIT 1;
INSERT IGNORE INTO notifications (user_id, title, content, is_read, notification_type, reference_type, reference_id, created_at, updated_at) VALUES 
(@supplier_user_id, 'Thông báo hệ thống', 'Chào mừng bạn đến với hệ thống FoodShare', 1, 'SYSTEM', NULL, NULL, NOW(), NOW()),
(@supplier_user_id, 'Cập nhật tài khoản', 'Tài khoản của bạn đã được xác thực', 1, 'SYSTEM', NULL, NULL, NOW(), NOW());
INSERT IGNORE INTO notifications (user_id, title, content, is_read, notification_type, reference_type, reference_id, created_at, updated_at) 
SELECT @supplier_user_id, 'Đánh giá mới', 'Khách hàng vừa đánh giá 5 sao cho bạn', 0, 'REVIEW_RECEIVED', 'ORDER', id, NOW(), NOW() FROM orders WHERE order_code = 'ORD-TEST-005' LIMIT 1;
INSERT IGNORE INTO notifications (user_id, title, content, is_read, notification_type, reference_type, reference_id, created_at, updated_at) 
SELECT @supplier_user_id, 'Hết hạn bài đăng', 'Bài đăng Cơm rang dưa bò sắp hết hạn', 0, 'SYSTEM', 'FOOD_POST', id, NOW(), NOW() FROM food_posts WHERE name = 'Cơm rang dưa bò' LIMIT 1;

INSERT IGNORE INTO notifications (user_id, title, content, is_read, notification_type, reference_type, reference_id, created_at, updated_at) 
SELECT @recipient_id, 'Xác nhận đơn hàng', 'Đơn hàng ORD-TEST-002 đã được xác nhận', 0, 'ORDER_CONFIRMED', 'ORDER', id, NOW(), NOW() FROM orders WHERE order_code = 'ORD-TEST-002' LIMIT 1;
INSERT IGNORE INTO notifications (user_id, title, content, is_read, notification_type, reference_type, reference_id, created_at, updated_at) 
SELECT @recipient_id, 'Đơn hàng đã sẵn sàng', 'Đơn hàng ORD-TEST-004 đã sẵn sàng để lấy', 0, 'ORDER_READY', 'ORDER', id, NOW(), NOW() FROM orders WHERE order_code = 'ORD-TEST-004' LIMIT 1;
INSERT IGNORE INTO notifications (user_id, title, content, is_read, notification_type, reference_type, reference_id, created_at, updated_at) 
SELECT @recipient_id, 'Hoàn thành đơn hàng', 'Cảm ơn bạn đã nhận hàng ORD-TEST-005', 1, 'ORDER_COMPLETED', 'ORDER', id, NOW(), NOW() FROM orders WHERE order_code = 'ORD-TEST-005' LIMIT 1;
INSERT IGNORE INTO notifications (user_id, title, content, is_read, notification_type, reference_type, reference_id, created_at, updated_at) 
SELECT @recipient_id, 'Đơn hàng bị từ chối', 'Đơn hàng ORD-TEST-012 bị từ chối do hết món', 1, 'ORDER_REJECTED', 'ORDER', id, NOW(), NOW() FROM orders WHERE order_code = 'ORD-TEST-012' LIMIT 1;
INSERT IGNORE INTO notifications (user_id, title, content, is_read, notification_type, reference_type, reference_id, created_at, updated_at) VALUES 
(@recipient_id, 'Gợi ý món mới', 'Có món mới gần bạn', 0, 'SYSTEM', NULL, NULL, NOW(), NOW());
INSERT IGNORE INTO notifications (user_id, title, content, is_read, notification_type, reference_type, reference_id, created_at, updated_at) 
SELECT @recipient_id, 'Hoàn tiền thành công', 'Hoàn tiền cho ORD-TEST-011 đã xong', 0, 'SYSTEM', 'ORDER', id, NOW(), NOW() FROM orders WHERE order_code = 'ORD-TEST-011' LIMIT 1;
INSERT IGNORE INTO notifications (user_id, title, content, is_read, notification_type, reference_type, reference_id, created_at, updated_at) VALUES 
(@recipient_id, 'Thông báo hệ thống', 'Cập nhật điều khoản sử dụng', 1, 'SYSTEM', NULL, NULL, NOW(), NOW()),
(@recipient_id, 'Thông báo hệ thống', 'Cảm ơn bạn đã tham gia FoodShare', 1, 'SYSTEM', NULL, NULL, NOW(), NOW());

-- ==========================================
-- 11. BÁO CÁO / KHIẾU NẠI (reports)
-- ==========================================
INSERT IGNORE INTO reports (reporter_id, title, content, report_type, report_status, response, evidence_url, reference_type, reference_id, created_at, updated_at) 
SELECT @recipient_id, 'Thức ăn hỏng', 'Món ăn có mùi ôi thiu', 'FOOD_QUALITY', 'PENDING', NULL, NULL, 'FOOD_POST', id, NOW(), NOW() FROM food_posts WHERE name = 'Cơm rang dưa bò' LIMIT 1;
INSERT IGNORE INTO reports (reporter_id, title, content, report_type, report_status, response, evidence_url, reference_type, reference_id, created_at, updated_at) VALUES 
(@recipient_id, 'Thái độ tệ', 'Chủ quán mắng khách', 'SUPPLIER_ATTITUDE', 'RESOLVED', 'Đã xử lý nhắc nhở', NULL, 'BUSINESS_PROFILE', @bp_id, NOW(), NOW()),
(@recipient_id, 'Chỉ đường sai', 'Địa chỉ ảo', 'OTHER', 'PENDING', NULL, NULL, 'BUSINESS_PROFILE', @bp_id, NOW(), NOW());
INSERT IGNORE INTO reports (reporter_id, title, content, report_type, report_status, response, evidence_url, reference_type, reference_id, created_at, updated_at) 
SELECT @recipient_id, 'Giá cao hơn thực tế', 'Giá trên app 20k thu thêm 5k', 'FRAUD', 'IN_PROGRESS', 'Đang xác minh', NULL, 'ORDER', id, NOW(), NOW() FROM orders WHERE order_code = 'ORD-TEST-001' LIMIT 1;
INSERT IGNORE INTO reports (reporter_id, title, content, report_type, report_status, response, evidence_url, reference_type, reference_id, created_at, updated_at) 
SELECT @recipient_id, 'Chờ quá lâu', 'Chờ 2 tiếng không thấy món', 'OTHER', 'RESOLVED', 'Đã bồi thường', NULL, 'ORDER', id, NOW(), NOW() FROM orders WHERE order_code = 'ORD-TEST-002' LIMIT 1;
INSERT IGNORE INTO reports (reporter_id, title, content, report_type, report_status, response, evidence_url, reference_type, reference_id, created_at, updated_at) 
SELECT @supplier_user_id, 'Bom hàng', 'Khách không đến lấy', 'USER_BEHAVIOR', 'PENDING', NULL, NULL, 'ORDER', id, NOW(), NOW() FROM orders WHERE order_code = 'ORD-TEST-004' LIMIT 1;
INSERT IGNORE INTO reports (reporter_id, title, content, report_type, report_status, response, evidence_url, reference_type, reference_id, created_at, updated_at) 
SELECT @supplier_user_id, 'Bom hàng', 'Khách bom hàng lần 2', 'USER_BEHAVIOR', 'RESOLVED', 'Đã khóa tài khoản khách', NULL, 'ORDER', id, NOW(), NOW() FROM orders WHERE order_code = 'ORD-TEST-011' LIMIT 1;
INSERT IGNORE INTO reports (reporter_id, title, content, report_type, report_status, response, evidence_url, reference_type, reference_id, created_at, updated_at) 
SELECT @supplier_user_id, 'Lỗi ứng dụng', 'Không nhấn được nút xác nhận', 'SYSTEM_ERROR', 'PENDING', NULL, NULL, 'ORDER', id, NOW(), NOW() FROM orders WHERE order_code = 'ORD-TEST-003' LIMIT 1;
INSERT IGNORE INTO reports (reporter_id, title, content, report_type, report_status, response, evidence_url, reference_type, reference_id, created_at, updated_at) VALUES 
(@admin_id, 'Kiểm tra định kỳ', 'Report từ hệ thống', 'OTHER', 'RESOLVED', 'Đã hoàn tất', NULL, 'BUSINESS_PROFILE', @bp_id, NOW(), NOW());
