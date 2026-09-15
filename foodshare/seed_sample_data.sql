-- ========================================================================
-- SCRIPT TẠO DỮ LIỆU MẪU TOÀN DIỆN CHO FOODSHARE (4 USERS - MỖI BẢNG 20+ DÒNG)
-- 4 Vai trò: ADMIN, RECIPIENT, SUPPLIER, ORGANIZATION
-- Bao phủ tất cả các luồng: Matching (PriorityQueue, TopK, MCMF), Orders, 
-- Payments, Payouts, Earnings, Reviews, Reports, Notifications, Devices, Configs
-- ========================================================================

SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- 1. Làm sạch dữ liệu các bảng nghiệp vụ
TRUNCATE TABLE supplier_earnings;
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
TRUNCATE TABLE user_devices;

SET FOREIGN_KEY_CHECKS = 1;

-- ========================================================================
-- 2. ĐẢM BẢO 4 USERS TỒN TẠI VÀ CHUẨN HÓA DỮ LIỆU
-- ========================================================================
SET @admin_id = (SELECT id FROM users WHERE role = 'ADMIN' ORDER BY id LIMIT 1);
SET @recipient_id = (SELECT id FROM users WHERE role = 'RECIPIENT' ORDER BY id LIMIT 1);
SET @supplier_id = (SELECT id FROM users WHERE role = 'SUPPLIER' ORDER BY id LIMIT 1);
SET @organization_id = (SELECT id FROM users WHERE role = 'ORGANIZATION' ORDER BY id LIMIT 1);

-- Nếu chưa có ORGANIZATION, tự động tạo mới
INSERT INTO users (phone, email, password_hash, full_name, specific_address, role, auth_provider, active, profile_completed, latitude, longitude, created_at, updated_at)
SELECT '0383870816', 'saigonchildren@foodshare.com', '$2a$10$7R0Zqj0Fj3qj8W/mockhash', 'Quỹ Mái Ấm Sài Gòn (Organization)', '45 Đinh Tiên Hoàng, Phường Đa Kao, Quận 1, TP.HCM', 'ORGANIZATION', 'LOCAL', 1, 1, 10.7815000, 106.7045000, NOW(), NOW()
FROM DUAL WHERE @organization_id IS NULL;

SET @organization_id = (SELECT id FROM users WHERE role = 'ORGANIZATION' ORDER BY id LIMIT 1);

-- Cập nhật thông tin chuẩn cho 4 users: Tọa độ GPS gần nhau (Quận 1 TP.HCM) để test thuật toán Matching
UPDATE users SET 
    active = TRUE, 
    profile_completed = TRUE,
    full_name = CASE 
        WHEN id = @admin_id THEN 'Quản Trị Viên Hệ Thống FoodShare'
        WHEN id = @recipient_id THEN 'Phạm Anh Quyền (Người nhận cá nhân)'
        WHEN id = @supplier_id THEN 'Quyền Quán Cơm & Bánh Mì (Nhà cung cấp)'
        WHEN id = @organization_id THEN 'Mái Ấm Hy Vọng (Tổ chức từ thiện)'
        ELSE full_name END,
    specific_address = CASE 
        WHEN id = @admin_id THEN '123 Hàm Nghi, Phường Bến Thành, Quận 1, TP.HCM'
        WHEN id = @recipient_id THEN '88 Lê Thánh Tôn, Phường Bến Nghé, Quận 1, TP.HCM'
        WHEN id = @supplier_id THEN '928 Lê Văn Lương, Phường Tân Phong, Quận 7, TP.HCM'
        WHEN id = @organization_id THEN '45 Đinh Tiên Hoàng, Phường Đa Kao, Quận 1, TP.HCM'
        ELSE specific_address END,
    latitude = CASE 
        WHEN id = @admin_id THEN 10.7750000
        WHEN id = @recipient_id THEN 10.7790000
        WHEN id = @supplier_id THEN 10.7769000
        WHEN id = @organization_id THEN 10.7815000
        ELSE latitude END,
    longitude = CASE 
        WHEN id = @admin_id THEN 106.7000000
        WHEN id = @recipient_id THEN 106.7030000
        WHEN id = @supplier_id THEN 106.7009000
        WHEN id = @organization_id THEN 106.7045000
        ELSE longitude END
WHERE id IN (@admin_id, @recipient_id, @supplier_id, @organization_id);

-- ========================================================================
-- 3. HỒ SƠ DOANH NGHIỆP / TỔ CHỨC (business_profiles)
-- ========================================================================
INSERT INTO business_profiles (user_id, name, description, tax_code, verification_status, profile_type, supplier_type, organization_type, created_at, updated_at)
SELECT @supplier_id, 'Bếp Cơm Thiện Nguyện & Bánh Mì Quyền Quán', 'Chuyên cung cấp các phần ăn dinh dưỡng, cơm trưa và bánh mì trợ giá hoặc miễn phí cho cộng đồng.', 'MST-0318999888', 'VERIFIED', 'SUPPLIER', 'RESTAURANT', NULL, NOW(), NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM business_profiles WHERE user_id = @supplier_id);

UPDATE business_profiles SET 
    name = 'Bếp Cơm Thiện Nguyện & Bánh Mì Quyền Quán',
    description = 'Chuyên cung cấp các phần ăn dinh dưỡng, cơm trưa và bánh mì trợ giá hoặc miễn phí cho cộng đồng.',
    tax_code = 'MST-0318999888',
    verification_status = 'VERIFIED',
    profile_type = 'SUPPLIER',
    supplier_type = 'RESTAURANT',
    organization_type = NULL
WHERE user_id = @supplier_id;

INSERT INTO business_profiles (user_id, name, description, tax_code, verification_status, profile_type, supplier_type, organization_type, created_at, updated_at)
SELECT @organization_id, 'Mái Ấm Hy Vọng & Quỹ Từ Thiện Sài Gòn', 'Tổ chức thiện nguyện bảo trợ trẻ em cơ nhỡ, người già neo đơn và hỗ trợ phân phối thực phẩm cứu trợ.', 'MST-0319999777', 'VERIFIED', 'ORGANIZATION', NULL, 'CHARITY', NOW(), NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM business_profiles WHERE user_id = @organization_id);

UPDATE business_profiles SET 
    name = 'Mái Ấm Hy Vọng & Quỹ Từ Thiện Sài Gòn',
    description = 'Tổ chức thiện nguyện bảo trợ trẻ em cơ nhỡ, người già neo đơn và hỗ trợ phân phối thực phẩm cứu trợ.',
    tax_code = 'MST-0319999777',
    verification_status = 'VERIFIED',
    profile_type = 'ORGANIZATION',
    supplier_type = NULL,
    organization_type = 'CHARITY'
WHERE user_id = @organization_id;

SET @supplier_bp = (SELECT id FROM business_profiles WHERE user_id = @supplier_id LIMIT 1);
SET @organization_bp = (SELECT id FROM business_profiles WHERE user_id = @organization_id LIMIT 1);

-- ========================================================================
-- 4. DANH MỤC THỰC PHẨM (categories - 20 dòng)
-- ========================================================================
INSERT IGNORE INTO categories (id, name, created_at, updated_at) VALUES
(1, 'Cơm và món chính', NOW(), NOW()),
(2, 'Rau củ và trái cây', NOW(), NOW()),
(3, 'Bánh và đồ ăn nhẹ', NOW(), NOW()),
(4, 'Đồ uống & giải khát', NOW(), NOW()),
(5, 'Thực phẩm khô & đồ hộp', NOW(), NOW()),
(6, 'Sữa và chế phẩm từ sữa', NOW(), NOW()),
(7, 'Món chay thanh tịnh', NOW(), NOW()),
(8, 'Thịt cá tươi sống', NOW(), NOW()),
(9, 'Thức ăn nhanh & ăn vặt', NOW(), NOW()),
(10, 'Bún, phở & mì nước', NOW(), NOW()),
(11, 'Canh & súp dinh dưỡng', NOW(), NOW()),
(12, 'Bánh mì & xôi sáng', NOW(), NOW()),
(13, 'Chè & món tráng miệng', NOW(), NOW()),
(14, 'Hải sản các loại', NOW(), NOW()),
(15, 'Thực phẩm đông lạnh', NOW(), NOW()),
(16, 'Gia vị & phụ liệu bếp', NOW(), NOW()),
(17, 'Thực phẩm hữu cơ Organic', NOW(), NOW()),
(18, 'Đồ nướng & BBQ', NOW(), NOW()),
(19, 'Salad & ăn kiêng Healthy', NOW(), NOW()),
(20, 'Suất ăn từ thiện đặc biệt', NOW(), NOW());

-- ========================================================================
-- 5. GIẤY PHÉP & HỒ SƠ PHÁP LÝ (licenses - 20 dòng)
-- ========================================================================
INSERT INTO licenses (business_profile_id, file_url, created_at, updated_at) VALUES
(@supplier_bp, 'https://res.cloudinary.com/dqbheiddg/image/upload/v1/foodshare/giay_phep_dkkd_nha_hang_01.pdf', NOW(), NOW()),
(@supplier_bp, 'https://res.cloudinary.com/dqbheiddg/image/upload/v1/foodshare/chung_nhan_vsattp_so_y_te_02.pdf', NOW(), NOW()),
(@supplier_bp, 'https://res.cloudinary.com/dqbheiddg/image/upload/v1/foodshare/giay_xet_nghiem_nguon_nuoc_03.pdf', NOW(), NOW()),
(@supplier_bp, 'https://res.cloudinary.com/dqbheiddg/image/upload/v1/foodshare/chung_chi_tap_huan_attp_dau_bep_04.pdf', NOW(), NOW()),
(@supplier_bp, 'https://res.cloudinary.com/dqbheiddg/image/upload/v1/foodshare/hop_dong_cung_ung_nong_san_vietgap_05.pdf', NOW(), NOW()),
(@supplier_bp, 'https://res.cloudinary.com/dqbheiddg/image/upload/v1/foodshare/bien_ban_kiem_dinh_pccc_06.pdf', NOW(), NOW()),
(@supplier_bp, 'https://res.cloudinary.com/dqbheiddg/image/upload/v1/foodshare/giay_kham_suc_khoe_nhan_vien_07.pdf', NOW(), NOW()),
(@supplier_bp, 'https://res.cloudinary.com/dqbheiddg/image/upload/v1/foodshare/giay_cam_ket_nguon_goc_thuc_pham_08.pdf', NOW(), NOW()),
(@supplier_bp, 'https://res.cloudinary.com/dqbheiddg/image/upload/v1/foodshare/chung_chi_iso_22000_ve_sinh_09.pdf', NOW(), NOW()),
(@supplier_bp, 'https://res.cloudinary.com/dqbheiddg/image/upload/v1/foodshare/giay_xac_nhan_ho_kinh_doanh_10.pdf', NOW(), NOW()),
(@organization_bp, 'https://res.cloudinary.com/dqbheiddg/image/upload/v1/foodshare/quyet_dinh_thanh_lap_mai_am_11.pdf', NOW(), NOW()),
(@organization_bp, 'https://res.cloudinary.com/dqbheiddg/image/upload/v1/foodshare/giay_phep_hoat_dong_tu_thien_so_ldtbxh_12.pdf', NOW(), NOW()),
(@organization_bp, 'https://res.cloudinary.com/dqbheiddg/image/upload/v1/foodshare/dieu_le_to_chuc_va_hoat_dong_quy_13.pdf', NOW(), NOW()),
(@organization_bp, 'https://res.cloudinary.com/dqbheiddg/image/upload/v1/foodshare/giay_xac_nhan_dia_diem_hoat_dong_14.pdf', NOW(), NOW()),
(@organization_bp, 'https://res.cloudinary.com/dqbheiddg/image/upload/v1/foodshare/danh_sach_ban_dieu_hanh_to_chuc_15.pdf', NOW(), NOW()),
(@organization_bp, 'https://res.cloudinary.com/dqbheiddg/image/upload/v1/foodshare/bien_ban_kiem_toan_tai_chinh_quy_16.pdf', NOW(), NOW()),
(@organization_bp, 'https://res.cloudinary.com/dqbheiddg/image/upload/v1/foodshare/chung_nhan_co_so_bao_tro_xa_hoi_17.pdf', NOW(), NOW()),
(@organization_bp, 'https://res.cloudinary.com/dqbheiddg/image/upload/v1/foodshare/cam_ket_tiep_nhan_thuc_pham_cuu_tro_18.pdf', NOW(), NOW()),
(@organization_bp, 'https://res.cloudinary.com/dqbheiddg/image/upload/v1/foodshare/bien_ban_hop_tac_hoi_chu_thap_do_19.pdf', NOW(), NOW()),
(@organization_bp, 'https://res.cloudinary.com/dqbheiddg/image/upload/v1/foodshare/giay_khen_dong_gop_vi_cong_dong_20.pdf', NOW(), NOW());

-- ========================================================================
-- 6. TÀI KHOẢN NGÂN HÀNG THỤ HƯỞNG (payout_accounts - 20 dòng)
-- ========================================================================
INSERT INTO payout_accounts (business_profile_id, bank_code, bank_name, account_number, account_holder_name, is_default, is_active, created_at, updated_at) VALUES
(@supplier_bp, 'VCB', 'Ngân hàng Ngoại Thương Việt Nam (Vietcombank)', '9704360001001', 'QUYEN QUAN COM VA BANH MI', TRUE, TRUE, NOW(), NOW()),
(@supplier_bp, 'TCB', 'Ngân hàng Kỹ Thương Việt Nam (Techcombank)', '1903600002002', 'PHAM ANH QUYEN', FALSE, TRUE, NOW(), NOW()),
(@supplier_bp, 'MB', 'Ngân hàng Quân Đội (MBBank)', '0800100003003', 'QUYEN QUAN COM VA BANH MI', FALSE, TRUE, NOW(), NOW()),
(@supplier_bp, 'BIDV', 'Ngân hàng Đầu tư và Phát triển Việt Nam (BIDV)', '6501000004004', 'PHAM ANH QUYEN', FALSE, TRUE, NOW(), NOW()),
(@supplier_bp, 'ACB', 'Ngân hàng Á Châu (ACB)', '2401000005005', 'PHAM ANH QUYEN', FALSE, TRUE, NOW(), NOW()),
(@supplier_bp, 'VPB', 'Ngân hàng Việt Nam Thịnh Vượng (VPBank)', '1501000006006', 'QUYEN QUAN COM VA BANH MI', FALSE, TRUE, NOW(), NOW()),
(@supplier_bp, 'TPB', 'Ngân hàng Tiên Phong (TPBank)', '0301000007007', 'PHAM ANH QUYEN', FALSE, TRUE, NOW(), NOW()),
(@supplier_bp, 'CTG', 'Ngân hàng Công Thương Việt Nam (VietinBank)', '711A000008008', 'QUYEN QUAN COM VA BANH MI', FALSE, TRUE, NOW(), NOW()),
(@supplier_bp, 'STB', 'Ngân hàng Sài Gòn Thương Tín (Sacombank)', '0601000009009', 'PHAM ANH QUYEN', FALSE, TRUE, NOW(), NOW()),
(@supplier_bp, 'HDB', 'Ngân hàng Phát triển TP.HCM (HDBank)', '0887000010010', 'QUYEN QUAN COM VA BANH MI', FALSE, TRUE, NOW(), NOW()),
(@organization_bp, 'VCB', 'Ngân hàng Ngoại Thương Việt Nam (Vietcombank)', '9704360002001', 'QUY MAI AM HY VONG SAI GON', TRUE, TRUE, NOW(), NOW()),
(@organization_bp, 'TCB', 'Ngân hàng Kỹ Thương Việt Nam (Techcombank)', '1903600002003', 'QUY MAI AM HY VONG SAI GON', FALSE, TRUE, NOW(), NOW()),
(@organization_bp, 'MB', 'Ngân hàng Quân Đội (MBBank)', '0800100003004', 'QUY MAI AM HY VONG SAI GON', FALSE, TRUE, NOW(), NOW()),
(@organization_bp, 'BIDV', 'Ngân hàng Đầu tư và Phát triển Việt Nam (BIDV)', '6501000004005', 'QUY MAI AM HY VONG SAI GON', FALSE, TRUE, NOW(), NOW()),
(@organization_bp, 'ACB', 'Ngân hàng Á Châu (ACB)', '2401000005006', 'QUY MAI AM HY VONG SAI GON', FALSE, TRUE, NOW(), NOW()),
(@organization_bp, 'VPB', 'Ngân hàng Việt Nam Thịnh Vượng (VPBank)', '1501000006007', 'QUY MAI AM HY VONG SAI GON', FALSE, TRUE, NOW(), NOW()),
(@organization_bp, 'TPB', 'Ngân hàng Tiên Phong (TPBank)', '0301000007008', 'QUY MAI AM HY VONG SAI GON', FALSE, TRUE, NOW(), NOW()),
(@organization_bp, 'CTG', 'Ngân hàng Công Thương Việt Nam (VietinBank)', '711A000008009', 'QUY MAI AM HY VONG SAI GON', FALSE, TRUE, NOW(), NOW()),
(@organization_bp, 'STB', 'Ngân hàng Sài Gòn Thương Tín (Sacombank)', '0601000009010', 'QUY MAI AM HY VONG SAI GON', FALSE, TRUE, NOW(), NOW()),
(@organization_bp, 'HDB', 'Ngân hàng Phát triển TP.HCM (HDBank)', '0887000010011', 'QUY MAI AM HY VONG SAI GON', FALSE, TRUE, NOW(), NOW());

-- ========================================================================
-- 7. BÀI ĐĂNG THỰC PHẨM (food_posts - 20 dòng bao phủ mọi luồng Matching & Trạng thái)
-- ========================================================================
INSERT INTO food_posts (business_profile_id, category_id, name, description, total_quantity, available_quantity, unit_price, original_price, post_type, post_status, hidden_by_admin, moderation_hidden, expires_at, pickup_start_at, pickup_end_at, pickup_address, version, created_at, updated_at) VALUES
-- 1-6: AVAILABLE - Khẩn cấp cao (hết hạn trong 1-4 giờ) để kiểm thử PriorityQueue
(@supplier_bp, 1, 'Cơm sườn bì chả đặc biệt sốt mật ong', 'Cơm tấm sườn nướng than hoa thơm lừng, kèm chả trứng hấp và bì heo tươi trong ngày.', 15, 12, 25000, 45000, 'PAID', 'AVAILABLE', FALSE, FALSE, DATE_ADD(NOW(), INTERVAL 2 HOUR), NOW(), DATE_ADD(NOW(), INTERVAL 3 HOUR), '928 Lê Văn Lương, Phường Tân Phong, Quận 7, TP.HCM', 0, NOW(), NOW()),
(@supplier_bp, 10, 'Bún chả Hà Nội nướng than hoa gia truyền', 'Bún chả tươi mới làm buổi sáng, chả nướng thơm kèm nước mắm đu đủ tỏi ớt chua ngọt.', 10, 8, 20000, 40000, 'PAID', 'AVAILABLE', FALSE, FALSE, DATE_ADD(NOW(), INTERVAL 3 HOUR), NOW(), DATE_ADD(NOW(), INTERVAL 4 HOUR), '928 Lê Văn Lương, Phường Tân Phong, Quận 7, TP.HCM', 0, NOW(), NOW()),
(@supplier_bp, 12, 'Bánh mì pate chả lụa giòn rụm', 'Bánh mì nóng hổi, pate gan nhà làm béo ngậy kèm chả lụa và dưa leo rau thơm.', 25, 20, 10000, 20000, 'PAID', 'AVAILABLE', FALSE, FALSE, DATE_ADD(NOW(), INTERVAL 2 HOUR), NOW(), DATE_ADD(NOW(), INTERVAL 3 HOUR), '928 Lê Văn Lương, Phường Tân Phong, Quận 7, TP.HCM', 0, NOW(), NOW()),
(@supplier_bp, 2, 'Rau củ quả sạch tổng hợp nông trại Đà Lạt', 'Cải ngọt, bắp cải, cà rốt và dưa leo tươi sạch hái trong ngày, ưu tiên bà con có hoàn cảnh khó khăn.', 30, 30, 0, NULL, 'FREE', 'AVAILABLE', FALSE, FALSE, DATE_ADD(NOW(), INTERVAL 4 HOUR), NOW(), DATE_ADD(NOW(), INTERVAL 5 HOUR), '928 Lê Văn Lương, Phường Tân Phong, Quận 7, TP.HCM', 0, NOW(), NOW()),
(@supplier_bp, 10, 'Phở bò tái nạm Nam Định nước dùng thanh ngọt', 'Phở bò nước hầm xương ống 12 tiếng thơm nức, bánh phở mềm tươi kèm rau giá chanh ớt.', 8, 6, 25000, 50000, 'PAID', 'AVAILABLE', FALSE, FALSE, DATE_ADD(NOW(), INTERVAL 3 HOUR), NOW(), DATE_ADD(NOW(), INTERVAL 4 HOUR), '928 Lê Văn Lương, Phường Tân Phong, Quận 7, TP.HCM', 0, NOW(), NOW()),
(@supplier_bp, 7, 'Cơm chay thập cẩm nấm đông cô hạt sen', 'Cơm gạo lứt ăn kèm nấm kho tiêu, đậu hũ sốt cà chua và canh rong biển thanh đạm.', 20, 18, 0, NULL, 'FREE', 'AVAILABLE', FALSE, FALSE, DATE_ADD(NOW(), INTERVAL 4 HOUR), NOW(), DATE_ADD(NOW(), INTERVAL 5 HOUR), '928 Lê Văn Lương, Phường Tân Phong, Quận 7, TP.HCM', 0, NOW(), NOW()),

-- 7-12: AVAILABLE - Thời gian bình thường (hết hạn trong 12 - 48 giờ)
(@supplier_bp, 3, 'Bánh bao xá xíu trứng cút nóng hổi', 'Bánh bao nhân thịt xá xíu đậm đà kết hợp 2 quả trứng cút luộc, vỏ bánh mềm xốp.', 20, 15, 12000, 22000, 'PAID', 'AVAILABLE', FALSE, FALSE, DATE_ADD(NOW(), INTERVAL 12 HOUR), NOW(), DATE_ADD(NOW(), INTERVAL 8 HOUR), '928 Lê Văn Lương, Phường Tân Phong, Quận 7, TP.HCM', 0, NOW(), NOW()),
(@supplier_bp, 4, 'Trà sen vàng macchiato bùi béo', 'Trà thanh mát kết hợp hạt sen Huế bùi bùi và lớp kem cheese béo mịn hấp dẫn.', 15, 10, 15000, 35000, 'PAID', 'AVAILABLE', FALSE, FALSE, DATE_ADD(NOW(), INTERVAL 8 HOUR), NOW(), DATE_ADD(NOW(), INTERVAL 6 HOUR), '928 Lê Văn Lương, Phường Tân Phong, Quận 7, TP.HCM', 0, NOW(), NOW()),
(@supplier_bp, 12, 'Xôi gà xé mỡ hành hạt sen thơm dẻo', 'Xôi nếp cái hoa vàng nấu nước dừa béo thơm, gà xé đậm vị rưới mỡ hành phi giòn.', 15, 14, 15000, 30000, 'PAID', 'AVAILABLE', FALSE, FALSE, DATE_ADD(NOW(), INTERVAL 10 HOUR), NOW(), DATE_ADD(NOW(), INTERVAL 8 HOUR), '928 Lê Văn Lương, Phường Tân Phong, Quận 7, TP.HCM', 0, NOW(), NOW()),
(@supplier_bp, 2, 'Trái cây nhiệt đới gọt sẵn (dưa hấu, ổi, xoài)', 'Hộp trái cây tươi mát gồm dưa hấu ruột đỏ, ổi giòn ngọt và xoài chua ngọt chấm muối tôm.', 20, 20, 0, NULL, 'FREE', 'AVAILABLE', FALSE, FALSE, DATE_ADD(NOW(), INTERVAL 6 HOUR), NOW(), DATE_ADD(NOW(), INTERVAL 5 HOUR), '928 Lê Văn Lương, Phường Tân Phong, Quận 7, TP.HCM', 0, NOW(), NOW()),
(@supplier_bp, 6, 'Sữa tươi tiệt trùng nguyên chất tiệt trùng', 'Thùng sữa tươi còn hạn dùng hơn 2 tuần, đóng lốc 4 hộp hỗ trợ trẻ em mái ấm.', 40, 35, 5000, 12000, 'PAID', 'AVAILABLE', FALSE, FALSE, DATE_ADD(NOW(), INTERVAL 24 HOUR), NOW(), DATE_ADD(NOW(), INTERVAL 12 HOUR), '928 Lê Văn Lương, Phường Tân Phong, Quận 7, TP.HCM', 0, NOW(), NOW()),
(@supplier_bp, 11, 'Canh súp gà ngô non nấm tuyết bồi bổ', 'Súp gà nấu nấm tuyết và bắp non ngọt thanh, ấm bụng và giàu dưỡng chất.', 12, 10, 10000, 25000, 'PAID', 'AVAILABLE', FALSE, FALSE, DATE_ADD(NOW(), INTERVAL 6 HOUR), NOW(), DATE_ADD(NOW(), INTERVAL 5 HOUR), '928 Lê Văn Lương, Phường Tân Phong, Quận 7, TP.HCM', 0, NOW(), NOW()),

-- 13-14: DRAFT - Bài đăng bản nháp của nhà cung cấp
(@supplier_bp, 14, 'Mì xào giòn hải sản tôm mực chua ngọt', 'Mì xào giòn rụm kèm tôm tươi, mực lá sốt cà chua thơm ngon (Đang soạn bài).', 10, 10, 30000, 55000, 'PAID', 'DRAFT', FALSE, FALSE, DATE_ADD(NOW(), INTERVAL 48 HOUR), NOW(), DATE_ADD(NOW(), INTERVAL 24 HOUR), '928 Lê Văn Lương, Phường Tân Phong, Quận 7, TP.HCM', 0, NOW(), NOW()),
(@supplier_bp, 3, 'Bánh ngọt croissant bơ tỏi kiểu Pháp', 'Bánh sừng bò nướng bơ tỏi thơm ngậy giòn xốp (Đang soạn bài nháp).', 15, 15, 15000, 30000, 'PAID', 'DRAFT', FALSE, FALSE, DATE_ADD(NOW(), INTERVAL 48 HOUR), NOW(), DATE_ADD(NOW(), INTERVAL 24 HOUR), '928 Lê Văn Lương, Phường Tân Phong, Quận 7, TP.HCM', 0, NOW(), NOW()),

-- 15-16: HIDDEN - Chủ quán tạm ẩn bài đăng
(@supplier_bp, 9, 'Gà rán sốt cay ngọt Hàn Quốc rắc mè', 'Gà rán giòn rụm phủ sốt cay ngọt đậm đà, quán tạm ẩn để chuẩn bị thêm sốt.', 12, 12, 25000, 45000, 'PAID', 'HIDDEN', FALSE, FALSE, DATE_ADD(NOW(), INTERVAL 24 HOUR), NOW(), DATE_ADD(NOW(), INTERVAL 12 HOUR), '928 Lê Văn Lương, Phường Tân Phong, Quận 7, TP.HCM', 0, NOW(), NOW()),
(@supplier_bp, 13, 'Chè bưởi An Giang cốt dừa béo ngậy', 'Chè cùi bưởi giòn sần sật đậu xanh bùi bùi (Tạm ẩn do đợi nước cốt dừa).', 20, 20, 10000, 20000, 'PAID', 'HIDDEN', FALSE, FALSE, DATE_ADD(NOW(), INTERVAL 24 HOUR), NOW(), DATE_ADD(NOW(), INTERVAL 12 HOUR), '928 Lê Văn Lương, Phường Tân Phong, Quận 7, TP.HCM', 0, NOW(), NOW()),

-- 17-18: OUT_OF_STOCK - Đã hết phần ăn (availableQuantity = 0)
(@supplier_bp, 10, 'Bún đậu mắm tôm thập cẩm chả cốm', 'Mẹt bún đậu đầy đặn đậu mơ rán giòn, chả cốm và nem rán (Đã phát hết sạch suất).', 20, 0, 25000, 45000, 'PAID', 'OUT_OF_STOCK', FALSE, FALSE, DATE_ADD(NOW(), INTERVAL 5 HOUR), NOW(), DATE_ADD(NOW(), INTERVAL 4 HOUR), '928 Lê Văn Lương, Phường Tân Phong, Quận 7, TP.HCM', 0, NOW(), NOW()),
(@supplier_bp, 10, 'Hủ tiếu Nam Vang nước lèo tôm thịt', 'Hủ tiếu dai ngon với tôm tươi, thịt nạc và trứng cút (Đã hết hàng trong buổi sáng).', 15, 0, 20000, 40000, 'PAID', 'OUT_OF_STOCK', FALSE, FALSE, DATE_ADD(NOW(), INTERVAL 4 HOUR), NOW(), DATE_ADD(NOW(), INTERVAL 3 HOUR), '928 Lê Văn Lương, Phường Tân Phong, Quận 7, TP.HCM', 0, NOW(), NOW()),

-- 19: EXPIRED - Hết hạn sử dụng (expiresAt trong quá khứ)
(@supplier_bp, 1, 'Cháo sườn non hầm nấm hạt sen', 'Cháo sườn nóng hổi ninh nhừ cùng nấm rơm (Đã quá giờ hẹn nhận của ngày hôm qua).', 10, 5, 10000, 25000, 'PAID', 'EXPIRED', FALSE, FALSE, DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), '928 Lê Văn Lương, Phường Tân Phong, Quận 7, TP.HCM', 0, DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY)),

-- 20: DELETED / HIDDEN_BY_ADMIN - Admin kiểm duyệt ẩn bài vi phạm
(@supplier_bp, 19, 'Salad ức gà xé sốt mè rang Healthy', 'Salad rau xà lách ức gà luộc (Bị admin ẩn do báo cáo không đảm bảo vệ sinh).', 10, 0, 15000, 35000, 'PAID', 'DELETED', TRUE, TRUE, DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), '928 Lê Văn Lương, Phường Tân Phong, Quận 7, TP.HCM', 0, DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY));

-- ========================================================================
-- 8. HÌNH ẢNH MÓN ĂN (food_post_images - 20 dòng tương ứng 20 bài đăng)
-- ========================================================================
INSERT INTO food_post_images (food_post_id, image_url, created_at, updated_at)
SELECT id, CASE (id % 10)
    WHEN 1 THEN 'https://images.unsplash.com/photo-1546069901-ba9599a7e63c?w=600'
    WHEN 2 THEN 'https://images.unsplash.com/photo-1555939594-58d7cb561ad1?w=600'
    WHEN 3 THEN 'https://images.unsplash.com/photo-1565299624946-b28f40a0ae38?w=600'
    WHEN 4 THEN 'https://images.unsplash.com/photo-1540420773420-3366772f4999?w=600'
    WHEN 5 THEN 'https://images.unsplash.com/photo-1582878826629-29b7ad1cdc43?w=600'
    WHEN 6 THEN 'https://images.unsplash.com/photo-1512621776951-a57141f2eefd?w=600'
    WHEN 7 THEN 'https://images.unsplash.com/photo-1563379091339-03b21ab4a4f8?w=600'
    WHEN 8 THEN 'https://images.unsplash.com/photo-1576097449798-7c7f90e1248a?w=600'
    WHEN 9 THEN 'https://images.unsplash.com/photo-1517248135467-4c7edcad34c4?w=600'
    ELSE 'https://images.unsplash.com/photo-1504674900247-0877df9cc836?w=600' END,
    NOW(), NOW()
FROM food_posts
WHERE business_profile_id = @supplier_bp
ORDER BY id LIMIT 20;

-- ========================================================================
-- 9. ĐƠN HÀNG (orders - 25 dòng bao phủ toàn bộ OrderStatus & 2 người nhận)
-- Gồm: 1 PENDING, 1 ACCEPTED, 1 READY_FOR_PICKUP, 20 COMPLETED, 1 CANCELLED, 1 REJECTED
-- ========================================================================
INSERT INTO orders (order_code, order_status, total_amount, receiver_id, business_profile_id, receiver_note, ready_at, pickup_deadline, delivered_at, completed_at, cancelled_at, cancellation_reason, rejected_at, rejection_reason, created_at, updated_at) VALUES
-- 1. PENDING (Đơn vừa đặt, chờ quán xác nhận)
('FS-ORD-2026-001', 'PENDING', 50000, @recipient_id, @supplier_bp, 'Em sẽ ghé lấy trước 11h30 trưa nay ạ.', NULL, DATE_ADD(NOW(), INTERVAL 3 HOUR), NULL, NULL, NULL, NULL, NULL, NULL, DATE_SUB(NOW(), INTERVAL 30 MINUTE), NOW()),

-- 2. ACCEPTED (Quán đã nhận đơn, đang chuẩn bị phần ăn)
('FS-ORD-2026-002', 'ACCEPTED', 40000, @organization_id, @supplier_bp, 'Mái ấm cử tình nguyện viên qua nhận 2 suất.', NULL, DATE_ADD(NOW(), INTERVAL 4 HOUR), NULL, NULL, NULL, NULL, NULL, NULL, DATE_SUB(NOW(), INTERVAL 1 HOUR), NOW()),

-- 3. READY_FOR_PICKUP (Phần ăn đã chuẩn bị xong, chờ khách đến lấy)
('FS-ORD-2026-003', 'READY_FOR_PICKUP', 20000, @recipient_id, @supplier_bp, 'Bọc kín giúp em vì em đi xe máy.', DATE_SUB(NOW(), INTERVAL 15 MINUTE), DATE_ADD(NOW(), INTERVAL 2 HOUR), NULL, NULL, NULL, NULL, NULL, NULL, DATE_SUB(NOW(), INTERVAL 2 HOUR), NOW()),

-- 4 - 23: COMPLETED (20 đơn hoàn thành xuất sắc - dùng để tạo Payments, Earnings, Reviews, Payouts)
('FS-ORD-2026-004', 'COMPLETED', 50000, @recipient_id, @supplier_bp, 'Cơm nhiều rau giúp em ạ.', DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 23 HOUR), DATE_SUB(NOW(), INTERVAL 23 HOUR), DATE_SUB(NOW(), INTERVAL 23 HOUR), NULL, NULL, NULL, NULL, DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 23 HOUR)),
('FS-ORD-2026-005', 'COMPLETED', 40000, @organization_id, @supplier_bp, 'Nhận hỗ trợ cho các bé mồ côi.', DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 22 HOUR), DATE_SUB(NOW(), INTERVAL 22 HOUR), DATE_SUB(NOW(), INTERVAL 22 HOUR), NULL, NULL, NULL, NULL, DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 22 HOUR)),
('FS-ORD-2026-006', 'COMPLETED', 30000, @recipient_id, @supplier_bp, 'Đồ ăn mang về ăn trưa văn phòng.', DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 47 HOUR), DATE_SUB(NOW(), INTERVAL 46 HOUR), DATE_SUB(NOW(), INTERVAL 46 HOUR), NULL, NULL, NULL, NULL, DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 46 HOUR)),
('FS-ORD-2026-007', 'COMPLETED', 20000, @organization_id, @supplier_bp, 'Cảm ơn tấm lòng của chủ quán nhiều!', DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 45 HOUR), DATE_SUB(NOW(), INTERVAL 45 HOUR), DATE_SUB(NOW(), INTERVAL 45 HOUR), NULL, NULL, NULL, NULL, DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 45 HOUR)),
('FS-ORD-2026-008', 'COMPLETED', 75000, @recipient_id, @supplier_bp, 'Lấy 3 suất cơm trưa cho gia đình.', DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 71 HOUR), DATE_SUB(NOW(), INTERVAL 70 HOUR), DATE_SUB(NOW(), INTERVAL 70 HOUR), NULL, NULL, NULL, NULL, DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 70 HOUR)),
('FS-ORD-2026-009', 'COMPLETED', 60000, @organization_id, @supplier_bp, 'Suất ăn chiều cho các cụ già neo đơn.', DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 70 HOUR), DATE_SUB(NOW(), INTERVAL 69 HOUR), DATE_SUB(NOW(), INTERVAL 69 HOUR), NULL, NULL, NULL, NULL, DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 69 HOUR)),
('FS-ORD-2026-010', 'COMPLETED', 25000, @recipient_id, @supplier_bp, 'Cho em xin thêm ớt và chanh.', DATE_SUB(NOW(), INTERVAL 4 DAY), DATE_SUB(NOW(), INTERVAL 95 HOUR), DATE_SUB(NOW(), INTERVAL 94 HOUR), DATE_SUB(NOW(), INTERVAL 94 HOUR), NULL, NULL, NULL, NULL, DATE_SUB(NOW(), INTERVAL 4 DAY), DATE_SUB(NOW(), INTERVAL 94 HOUR)),
('FS-ORD-2026-011', 'COMPLETED', 36000, @organization_id, @supplier_bp, 'Nhận đồ ăn điểm tâm sáng cho các cháu.', DATE_SUB(NOW(), INTERVAL 4 DAY), DATE_SUB(NOW(), INTERVAL 94 HOUR), DATE_SUB(NOW(), INTERVAL 93 HOUR), DATE_SUB(NOW(), INTERVAL 93 HOUR), NULL, NULL, NULL, NULL, DATE_SUB(NOW(), INTERVAL 4 DAY), DATE_SUB(NOW(), INTERVAL 93 HOUR)),
('FS-ORD-2026-012', 'COMPLETED', 30000, @recipient_id, @supplier_bp, 'Đã ghé nhận đúng hẹn.', DATE_SUB(NOW(), INTERVAL 5 DAY), DATE_SUB(NOW(), INTERVAL 119 HOUR), DATE_SUB(NOW(), INTERVAL 118 HOUR), DATE_SUB(NOW(), INTERVAL 118 HOUR), NULL, NULL, NULL, NULL, DATE_SUB(NOW(), INTERVAL 5 DAY), DATE_SUB(NOW(), INTERVAL 118 HOUR)),
('FS-ORD-2026-013', 'COMPLETED', 45000, @organization_id, @supplier_bp, 'Bữa trưa ấm lòng cho mái ấm.', DATE_SUB(NOW(), INTERVAL 5 DAY), DATE_SUB(NOW(), INTERVAL 118 HOUR), DATE_SUB(NOW(), INTERVAL 117 HOUR), DATE_SUB(NOW(), INTERVAL 117 HOUR), NULL, NULL, NULL, NULL, DATE_SUB(NOW(), INTERVAL 5 DAY), DATE_SUB(NOW(), INTERVAL 117 HOUR)),
('FS-ORD-2026-014', 'COMPLETED', 50000, @recipient_id, @supplier_bp, 'Cơm sườn ngon xuất sắc.', DATE_SUB(NOW(), INTERVAL 6 DAY), DATE_SUB(NOW(), INTERVAL 143 HOUR), DATE_SUB(NOW(), INTERVAL 142 HOUR), DATE_SUB(NOW(), INTERVAL 142 HOUR), NULL, NULL, NULL, NULL, DATE_SUB(NOW(), INTERVAL 6 DAY), DATE_SUB(NOW(), INTERVAL 142 HOUR)),
('FS-ORD-2026-015', 'COMPLETED', 40000, @organization_id, @supplier_bp, 'Thực phẩm sạch sẽ, đóng gói cẩn thận.', DATE_SUB(NOW(), INTERVAL 6 DAY), DATE_SUB(NOW(), INTERVAL 142 HOUR), DATE_SUB(NOW(), INTERVAL 141 HOUR), DATE_SUB(NOW(), INTERVAL 141 HOUR), NULL, NULL, NULL, NULL, DATE_SUB(NOW(), INTERVAL 6 DAY), DATE_SUB(NOW(), INTERVAL 141 HOUR)),
('FS-ORD-2026-016', 'COMPLETED', 20000, @recipient_id, @supplier_bp, 'Bún tươi ngon nước dùng ấm.', DATE_SUB(NOW(), INTERVAL 7 DAY), DATE_SUB(NOW(), INTERVAL 167 HOUR), DATE_SUB(NOW(), INTERVAL 166 HOUR), DATE_SUB(NOW(), INTERVAL 166 HOUR), NULL, NULL, NULL, NULL, DATE_SUB(NOW(), INTERVAL 7 DAY), DATE_SUB(NOW(), INTERVAL 166 HOUR)),
('FS-ORD-2026-017', 'COMPLETED', 60000, @organization_id, @supplier_bp, 'Cảm ơn quán luôn đồng hành cùng các bé.', DATE_SUB(NOW(), INTERVAL 7 DAY), DATE_SUB(NOW(), INTERVAL 166 HOUR), DATE_SUB(NOW(), INTERVAL 165 HOUR), DATE_SUB(NOW(), INTERVAL 165 HOUR), NULL, NULL, NULL, NULL, DATE_SUB(NOW(), INTERVAL 7 DAY), DATE_SUB(NOW(), INTERVAL 165 HOUR)),
('FS-ORD-2026-018', 'COMPLETED', 25000, @recipient_id, @supplier_bp, 'Giá quá rẻ so với chất lượng quán.', DATE_SUB(NOW(), INTERVAL 8 DAY), DATE_SUB(NOW(), INTERVAL 191 HOUR), DATE_SUB(NOW(), INTERVAL 190 HOUR), DATE_SUB(NOW(), INTERVAL 190 HOUR), NULL, NULL, NULL, NULL, DATE_SUB(NOW(), INTERVAL 8 DAY), DATE_SUB(NOW(), INTERVAL 190 HOUR)),
('FS-ORD-2026-019', 'COMPLETED', 50000, @organization_id, @supplier_bp, 'Các cụ già rất thích món canh ấm này.', DATE_SUB(NOW(), INTERVAL 8 DAY), DATE_SUB(NOW(), INTERVAL 190 HOUR), DATE_SUB(NOW(), INTERVAL 189 HOUR), DATE_SUB(NOW(), INTERVAL 189 HOUR), NULL, NULL, NULL, NULL, DATE_SUB(NOW(), INTERVAL 8 DAY), DATE_SUB(NOW(), INTERVAL 189 HOUR)),
('FS-ORD-2026-020', 'COMPLETED', 30000, @recipient_id, @supplier_bp, 'Bánh mì pate thơm giòn.', DATE_SUB(NOW(), INTERVAL 9 DAY), DATE_SUB(NOW(), INTERVAL 215 HOUR), DATE_SUB(NOW(), INTERVAL 214 HOUR), DATE_SUB(NOW(), INTERVAL 214 HOUR), NULL, NULL, NULL, NULL, DATE_SUB(NOW(), INTERVAL 9 DAY), DATE_SUB(NOW(), INTERVAL 214 HOUR)),
('FS-ORD-2026-021', 'COMPLETED', 40000, @organization_id, @supplier_bp, 'Phần ăn đầy đặn chất lượng cao.', DATE_SUB(NOW(), INTERVAL 9 DAY), DATE_SUB(NOW(), INTERVAL 214 HOUR), DATE_SUB(NOW(), INTERVAL 213 HOUR), DATE_SUB(NOW(), INTERVAL 213 HOUR), NULL, NULL, NULL, NULL, DATE_SUB(NOW(), INTERVAL 9 DAY), DATE_SUB(NOW(), INTERVAL 213 HOUR)),
('FS-ORD-2026-022', 'COMPLETED', 25000, @recipient_id, @supplier_bp, 'Ăn no nê bữa tối tuyệt vời.', DATE_SUB(NOW(), INTERVAL 10 DAY), DATE_SUB(NOW(), INTERVAL 239 HOUR), DATE_SUB(NOW(), INTERVAL 238 HOUR), DATE_SUB(NOW(), INTERVAL 238 HOUR), NULL, NULL, NULL, NULL, DATE_SUB(NOW(), INTERVAL 10 DAY), DATE_SUB(NOW(), INTERVAL 238 HOUR)),
('FS-ORD-2026-023', 'COMPLETED', 50000, @organization_id, @supplier_bp, 'Cộng đồng rất biết ơn nghĩa cử của quán.', DATE_SUB(NOW(), INTERVAL 10 DAY), DATE_SUB(NOW(), INTERVAL 238 HOUR), DATE_SUB(NOW(), INTERVAL 237 HOUR), DATE_SUB(NOW(), INTERVAL 237 HOUR), NULL, NULL, NULL, NULL, DATE_SUB(NOW(), INTERVAL 10 DAY), DATE_SUB(NOW(), INTERVAL 237 HOUR)),

-- 24. CANCELLED (Khách bận đột xuất tự hủy đơn)
('FS-ORD-2026-024', 'CANCELLED', 25000, @recipient_id, @supplier_bp, 'Em có việc đột xuất không ghé lấy kịp.', NULL, NULL, NULL, NULL, DATE_SUB(NOW(), INTERVAL 5 HOUR), 'Khách hàng có việc bận đột xuất tại công ty không ghé lấy đúng hẹn', NULL, NULL, DATE_SUB(NOW(), INTERVAL 6 HOUR), DATE_SUB(NOW(), INTERVAL 5 HOUR)),

-- 25. REJECTED (Chủ quán từ chối do quá tải đơn)
('FS-ORD-2026-025', 'REJECTED', 30000, @recipient_id, @supplier_bp, 'Lấy gấp trước 10h sáng.', NULL, NULL, NULL, NULL, NULL, NULL, DATE_SUB(NOW(), INTERVAL 12 HOUR), 'Quán đột xuất hết nguyên liệu do khách tại chỗ quá đông', DATE_SUB(NOW(), INTERVAL 13 HOUR), DATE_SUB(NOW(), INTERVAL 12 HOUR));

-- ========================================================================
-- 10. CHI TIẾT ĐƠN HÀNG (order_details - 25 dòng khớp từng đơn)
-- ========================================================================
INSERT INTO order_details (order_id, food_post_id, unit_price, quantity, created_at, updated_at)
SELECT o.id, p.id, 
       CASE WHEN o.total_amount = 50000 THEN 25000
            WHEN o.total_amount = 40000 THEN 20000
            WHEN o.total_amount = 30000 THEN 15000
            WHEN o.total_amount = 20000 THEN 10000
            WHEN o.total_amount = 75000 THEN 25000
            WHEN o.total_amount = 60000 THEN 20000
            WHEN o.total_amount = 25000 THEN 25000
            WHEN o.total_amount = 36000 THEN 12000
            ELSE 25000 END,
       CASE WHEN o.total_amount = 50000 THEN 2
            WHEN o.total_amount = 40000 THEN 2
            WHEN o.total_amount = 30000 THEN 2
            WHEN o.total_amount = 20000 THEN 2
            WHEN o.total_amount = 75000 THEN 3
            WHEN o.total_amount = 60000 THEN 3
            WHEN o.total_amount = 25000 THEN 1
            WHEN o.total_amount = 36000 THEN 3
            ELSE 1 END,
       o.created_at, o.updated_at
FROM orders o
JOIN food_posts p ON p.business_profile_id = @supplier_bp AND p.post_status = 'AVAILABLE'
WHERE p.id = (SELECT MIN(id) FROM food_posts WHERE business_profile_id = @supplier_bp);

-- ========================================================================
-- 11. THANH TOÁN (payments - 25 dòng bao phủ CASH, MOMO, ZALOPAY, SUCCESS, PENDING, REFUNDED)
-- ========================================================================
INSERT INTO payments (order_id, amount, method, payment_status, external_transaction_id, paid_at, provider, transfer_content, expires_at, refund_transaction_id, refunded_at, created_at, updated_at)
SELECT o.id, o.total_amount,
       CASE (o.id % 3) WHEN 0 THEN 'CASH' WHEN 1 THEN 'MOMO' ELSE 'ZALOPAY' END,
       CASE WHEN o.order_status = 'COMPLETED' THEN 'SUCCESS'
            WHEN o.order_status = 'CANCELLED' THEN 'REFUNDED'
            WHEN o.order_status = 'REJECTED' THEN 'FAILED'
            ELSE 'PENDING' END,
       CONCAT('TXN-PAY-', LPAD(o.id, 5, '0')),
       CASE WHEN o.order_status = 'COMPLETED' THEN o.completed_at ELSE NULL END,
       CASE (o.id % 3) WHEN 0 THEN 'CASH' WHEN 1 THEN 'MOMO' ELSE 'ZALOPAY' END,
       CONCAT('Thanh toan don hang FoodShare ', o.order_code),
       DATE_ADD(o.created_at, INTERVAL 30 MINUTE),
       CASE WHEN o.order_status = 'CANCELLED' THEN CONCAT('REFUND-', LPAD(o.id, 5, '0')) ELSE NULL END,
       CASE WHEN o.order_status = 'CANCELLED' THEN o.cancelled_at ELSE NULL END,
       o.created_at, o.updated_at
FROM orders o;

-- ========================================================================
-- 12. THU NHẬP NHÀ CUNG CẤP (supplier_earnings - 20 dòng tương ứng 20 đơn COMPLETED)
-- Phí sàn: 5% (fee_rate = 0.050000), net_amount = 95%
-- Bao gồm 18 đơn bình thường và 2 đơn có hoàn phí/tranh chấp (reversed_at)
-- ========================================================================
INSERT INTO supplier_earnings (business_profile_id, order_id, payment_id, gross_amount, fee_rate, platform_fee, net_amount, earned_at, reversed_at, reversal_reason, created_at, updated_at)
SELECT o.business_profile_id, o.id, p.id,
       p.amount,
       0.050000,
       ROUND(p.amount * 0.050000, 2),
       p.amount - ROUND(p.amount * 0.050000, 2),
       o.completed_at,
       CASE WHEN o.order_code IN ('FS-ORD-2026-022', 'FS-ORD-2026-023') THEN DATE_ADD(o.completed_at, INTERVAL 2 HOUR) ELSE NULL END,
       CASE WHEN o.order_code IN ('FS-ORD-2026-022', 'FS-ORD-2026-023') THEN 'Khách khiếu nại thức ăn bị nguội, hoàn trả một phần thu nhập' ELSE NULL END,
       o.completed_at, o.updated_at
FROM orders o
JOIN payments p ON p.order_id = o.id
WHERE o.order_status = 'COMPLETED'
ORDER BY o.id
LIMIT 20;

-- ========================================================================
-- 13. YÊU CẦU RÚT TIỀN (payouts - 20 dòng bao phủ SUCCESS, PENDING, FAILED, CANCELLED)
-- ========================================================================
INSERT INTO payouts (order_id, business_profile_id, payout_account_id, payout_code, gross_amount, platform_fee, net_amount, requested_amount, payout_status, bank_code, bank_name, account_number, account_holder_name, external_transaction_id, completed_at, failed_at, retry_count, failure_reason, note, reviewed_by, reviewed_at, rejection_reason, created_at, updated_at) VALUES
-- 1-8: SUCCESS (Rút tiền thành công, Admin đã duyệt và giải ngân)
(NULL, @supplier_bp, (SELECT MIN(id) FROM payout_accounts WHERE business_profile_id = @supplier_bp), 'PO-2026-001', 500000, 25000, 475000, 475000, 'SUCCESS', 'VCB', 'Vietcombank', '9704360001001', 'QUYEN QUAN COM VA BANH MI', 'VNP-PAYOUT-001', DATE_SUB(NOW(), INTERVAL 9 DAY), NULL, 0, NULL, 'Rút doanh thu tuần 1 tháng 9', @admin_id, DATE_SUB(NOW(), INTERVAL 9 DAY), NULL, DATE_SUB(NOW(), INTERVAL 10 DAY), DATE_SUB(NOW(), INTERVAL 9 DAY)),
(NULL, @supplier_bp, (SELECT MIN(id) FROM payout_accounts WHERE business_profile_id = @supplier_bp), 'PO-2026-002', 350000, 17500, 332500, 332500, 'SUCCESS', 'VCB', 'Vietcombank', '9704360001001', 'QUYEN QUAN COM VA BANH MI', 'VNP-PAYOUT-002', DATE_SUB(NOW(), INTERVAL 8 DAY), NULL, 0, NULL, 'Rút doanh thu các món cơm trưa', @admin_id, DATE_SUB(NOW(), INTERVAL 8 DAY), NULL, DATE_SUB(NOW(), INTERVAL 9 DAY), DATE_SUB(NOW(), INTERVAL 8 DAY)),
(NULL, @supplier_bp, (SELECT MIN(id) FROM payout_accounts WHERE business_profile_id = @supplier_bp), 'PO-2026-003', 420000, 21000, 399000, 399000, 'SUCCESS', 'VCB', 'Vietcombank', '9704360001001', 'QUYEN QUAN COM VA BANH MI', 'VNP-PAYOUT-003', DATE_SUB(NOW(), INTERVAL 7 DAY), NULL, 0, NULL, 'Rút tiền bán bánh mì trợ giá', @admin_id, DATE_SUB(NOW(), INTERVAL 7 DAY), NULL, DATE_SUB(NOW(), INTERVAL 8 DAY), DATE_SUB(NOW(), INTERVAL 7 DAY)),
(NULL, @supplier_bp, (SELECT MIN(id) FROM payout_accounts WHERE business_profile_id = @supplier_bp), 'PO-2026-004', 600000, 30000, 570000, 570000, 'SUCCESS', 'VCB', 'Vietcombank', '9704360001001', 'QUYEN QUAN COM VA BANH MI', 'VNP-PAYOUT-004', DATE_SUB(NOW(), INTERVAL 6 DAY), NULL, 0, NULL, 'Rút tiền nhập nguyên liệu tuần mới', @admin_id, DATE_SUB(NOW(), INTERVAL 6 DAY), NULL, DATE_SUB(NOW(), INTERVAL 7 DAY), DATE_SUB(NOW(), INTERVAL 6 DAY)),
(NULL, @supplier_bp, (SELECT MIN(id) FROM payout_accounts WHERE business_profile_id = @supplier_bp), 'PO-2026-005', 250000, 12500, 237500, 237500, 'SUCCESS', 'VCB', 'Vietcombank', '9704360001001', 'QUYEN QUAN COM VA BANH MI', 'VNP-PAYOUT-005', DATE_SUB(NOW(), INTERVAL 5 DAY), NULL, 0, NULL, 'Rút doanh thu đồ uống', @admin_id, DATE_SUB(NOW(), INTERVAL 5 DAY), NULL, DATE_SUB(NOW(), INTERVAL 6 DAY), DATE_SUB(NOW(), INTERVAL 5 DAY)),
(NULL, @supplier_bp, (SELECT MIN(id) FROM payout_accounts WHERE business_profile_id = @supplier_bp), 'PO-2026-006', 480000, 24000, 456000, 456000, 'SUCCESS', 'VCB', 'Vietcombank', '9704360001001', 'QUYEN QUAN COM VA BANH MI', 'VNP-PAYOUT-006', DATE_SUB(NOW(), INTERVAL 4 DAY), NULL, 0, NULL, 'Rút tiền bù chi phí nhân công', @admin_id, DATE_SUB(NOW(), INTERVAL 4 DAY), NULL, DATE_SUB(NOW(), INTERVAL 5 DAY), DATE_SUB(NOW(), INTERVAL 4 DAY)),
(NULL, @supplier_bp, (SELECT MIN(id) FROM payout_accounts WHERE business_profile_id = @supplier_bp), 'PO-2026-007', 300000, 15000, 285000, 285000, 'SUCCESS', 'VCB', 'Vietcombank', '9704360001001', 'QUYEN QUAN COM VA BANH MI', 'VNP-PAYOUT-007', DATE_SUB(NOW(), INTERVAL 3 DAY), NULL, 0, NULL, 'Rút doanh thu đơn hàng cuối tuần', @admin_id, DATE_SUB(NOW(), INTERVAL 3 DAY), NULL, DATE_SUB(NOW(), INTERVAL 4 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY)),
(NULL, @supplier_bp, (SELECT MIN(id) FROM payout_accounts WHERE business_profile_id = @supplier_bp), 'PO-2026-008', 550000, 27500, 522500, 522500, 'SUCCESS', 'VCB', 'Vietcombank', '9704360001001', 'QUYEN QUAN COM VA BANH MI', 'VNP-PAYOUT-008', DATE_SUB(NOW(), INTERVAL 2 DAY), NULL, 0, NULL, 'Rút tiền ủng hộ thêm gạo từ thiện', @admin_id, DATE_SUB(NOW(), INTERVAL 2 DAY), NULL, DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY)),

-- 9-14: PENDING (Đang chờ Admin duyệt chi trả)
(NULL, @supplier_bp, (SELECT MIN(id) FROM payout_accounts WHERE business_profile_id = @supplier_bp), 'PO-2026-009', 200000, 10000, 190000, 190000, 'PENDING', 'VCB', 'Vietcombank', '9704360001001', 'QUYEN QUAN COM VA BANH MI', NULL, NULL, NULL, 0, NULL, 'Đang chờ admin duyệt đợt 1 sáng nay', NULL, NULL, NULL, DATE_SUB(NOW(), INTERVAL 12 HOUR), NOW()),
(NULL, @supplier_bp, (SELECT MIN(id) FROM payout_accounts WHERE business_profile_id = @supplier_bp), 'PO-2026-010', 380000, 19000, 361000, 361000, 'PENDING', 'VCB', 'Vietcombank', '9704360001001', 'QUYEN QUAN COM VA BANH MI', NULL, NULL, NULL, 0, NULL, 'Yêu cầu rút tiền bán phở bò', NULL, NULL, NULL, DATE_SUB(NOW(), INTERVAL 8 HOUR), NOW()),
(NULL, @supplier_bp, (SELECT MIN(id) FROM payout_accounts WHERE business_profile_id = @supplier_bp), 'PO-2026-011', 150000, 7500, 142500, 142500, 'PENDING', 'VCB', 'Vietcombank', '9704360001001', 'QUYEN QUAN COM VA BANH MI', NULL, NULL, NULL, 0, NULL, 'Rút tiền chè bưởi & trà sữa', NULL, NULL, NULL, DATE_SUB(NOW(), INTERVAL 6 HOUR), NOW()),
(NULL, @supplier_bp, (SELECT MIN(id) FROM payout_accounts WHERE business_profile_id = @supplier_bp), 'PO-2026-012', 450000, 22500, 427500, 427500, 'PENDING', 'VCB', 'Vietcombank', '9704360001001', 'QUYEN QUAN COM VA BANH MI', NULL, NULL, NULL, 0, NULL, 'Yêu cầu rút thanh toán MoMo', NULL, NULL, NULL, DATE_SUB(NOW(), INTERVAL 4 HOUR), NOW()),
(NULL, @supplier_bp, (SELECT MIN(id) FROM payout_accounts WHERE business_profile_id = @supplier_bp), 'PO-2026-013', 280000, 14000, 266000, 266000, 'PENDING', 'VCB', 'Vietcombank', '9704360001001', 'QUYEN QUAN COM VA BANH MI', NULL, NULL, NULL, 0, NULL, 'Yêu cầu rút thanh toán ZaloPay', NULL, NULL, NULL, DATE_SUB(NOW(), INTERVAL 2 HOUR), NOW()),
(NULL, @supplier_bp, (SELECT MIN(id) FROM payout_accounts WHERE business_profile_id = @supplier_bp), 'PO-2026-014', 500000, 25000, 475000, 475000, 'PENDING', 'VCB', 'Vietcombank', '9704360001001', 'QUYEN QUAN COM VA BANH MI', NULL, NULL, NULL, 0, NULL, 'Đề nghị chuyển khoản tài khoản Techcombank', NULL, NULL, NULL, DATE_SUB(NOW(), INTERVAL 1 HOUR), NOW()),

-- 15-17: FAILED (Chuyển khoản thất bại từ cổng ngân hàng)
(NULL, @supplier_bp, (SELECT MIN(id) FROM payout_accounts WHERE business_profile_id = @supplier_bp), 'PO-2026-015', 300000, 15000, 285000, 285000, 'FAILED', 'VCB', 'Vietcombank', '9704360001001', 'QUYEN QUAN COM VA BANH MI', NULL, NULL, DATE_SUB(NOW(), INTERVAL 2 DAY), 2, 'Hệ thống ngân hàng thụ hưởng đang bảo trì định kỳ NAPAS', 'Chuyển tiền lỗi NAPAS', @admin_id, DATE_SUB(NOW(), INTERVAL 2 DAY), NULL, DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY)),
(NULL, @supplier_bp, (SELECT MIN(id) FROM payout_accounts WHERE business_profile_id = @supplier_bp), 'PO-2026-016', 220000, 11000, 209000, 209000, 'FAILED', 'VCB', 'Vietcombank', '9704360001001', 'QUYEN QUAN COM VA BANH MI', NULL, NULL, DATE_SUB(NOW(), INTERVAL 4 DAY), 1, 'Số tài khoản thụ hưởng tạm thời bị phong tỏa nhận tiền', 'Lỗi tài khoản nhận', @admin_id, DATE_SUB(NOW(), INTERVAL 4 DAY), NULL, DATE_SUB(NOW(), INTERVAL 5 DAY), DATE_SUB(NOW(), INTERVAL 4 DAY)),
(NULL, @supplier_bp, (SELECT MIN(id) FROM payout_accounts WHERE business_profile_id = @supplier_bp), 'PO-2026-017', 180000, 9000, 171000, 171000, 'FAILED', 'VCB', 'Vietcombank', '9704360001001', 'QUYEN QUAN COM VA BANH MI', NULL, NULL, DATE_SUB(NOW(), INTERVAL 6 DAY), 3, 'Timeout kết nối cổng thanh toán ngân hàng VNPAY Payout', 'Lỗi kết nối gateway', @admin_id, DATE_SUB(NOW(), INTERVAL 6 DAY), NULL, DATE_SUB(NOW(), INTERVAL 7 DAY), DATE_SUB(NOW(), INTERVAL 6 DAY)),

-- 18-20: CANCELLED (Admin từ chối yêu cầu rút tiền do sai thông tin)
(NULL, @supplier_bp, (SELECT MIN(id) FROM payout_accounts WHERE business_profile_id = @supplier_bp), 'PO-2026-018', 1000000, 50000, 950000, 950000, 'CANCELLED', 'VCB', 'Vietcombank', '9704360001001', 'QUYEN QUAN COM VA BANH MI', NULL, NULL, NULL, 0, NULL, 'Yêu cầu vượt hạn mức rút tiền trong ngày', @admin_id, DATE_SUB(NOW(), INTERVAL 5 DAY), 'Số dư tài khoản ví chưa đủ điều kiện rút số tiền lớn này', DATE_SUB(NOW(), INTERVAL 6 DAY), DATE_SUB(NOW(), INTERVAL 5 DAY)),
(NULL, @supplier_bp, (SELECT MIN(id) FROM payout_accounts WHERE business_profile_id = @supplier_bp), 'PO-2026-019', 200000, 10000, 190000, 190000, 'CANCELLED', 'VCB', 'Vietcombank', '9704360001001', 'QUYEN QUAN COM VA BANH MI', NULL, NULL, NULL, 0, NULL, 'Thông tin tên chủ thẻ không trùng khớp', @admin_id, DATE_SUB(NOW(), INTERVAL 7 DAY), 'Tên chủ tài khoản ngân hàng và tên trên CCCD không trùng khớp', DATE_SUB(NOW(), INTERVAL 8 DAY), DATE_SUB(NOW(), INTERVAL 7 DAY)),
(NULL, @supplier_bp, (SELECT MIN(id) FROM payout_accounts WHERE business_profile_id = @supplier_bp), 'PO-2026-020', 150000, 7500, 142500, 142500, 'CANCELLED', 'VCB', 'Vietcombank', '9704360001001', 'QUYEN QUAN COM VA BANH MI', NULL, NULL, NULL, 0, NULL, 'Yêu cầu rút trùng lặp với mã PO-008', @admin_id, DATE_SUB(NOW(), INTERVAL 3 DAY), 'Đơn yêu cầu rút bị bấm gửi trùng lặp 2 lần liên tiếp', DATE_SUB(NOW(), INTERVAL 4 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY));

-- ========================================================================
-- 14. ĐÁNH GIÁ (reviews - 20 dòng từ Recipient và Organization cho 20 đơn COMPLETED)
-- Điểm từ 1 đến 5 sao kèm bình luận thực tế
-- ========================================================================
INSERT INTO reviews (order_id, reviewer_id, business_profile_id, rating, comment, created_at, updated_at) VALUES
((SELECT id FROM orders WHERE order_code = 'FS-ORD-2026-004'), @recipient_id, @supplier_bp, 5, 'Cơm sườn nướng cực kỳ ngon, sườn to mềm thơm phức, đóng gói hộp xốp rất sạch sẽ!', DATE_SUB(NOW(), INTERVAL 23 HOUR), NOW()),
((SELECT id FROM orders WHERE order_code = 'FS-ORD-2026-005'), @organization_id, @supplier_bp, 5, 'Các cháu ở mái ấm rất vui và ăn hết sạch suất cơm trưa. Tri ân tấm lòng quán nhiều lắm!', DATE_SUB(NOW(), INTERVAL 22 HOUR), NOW()),
((SELECT id FROM orders WHERE order_code = 'FS-ORD-2026-006'), @recipient_id, @supplier_bp, 4, 'Bánh mì pate giòn ngon, nước sốt vừa miệng. Trừ 1 sao nhỏ vì quán đông phải chờ 5 phút.', DATE_SUB(NOW(), INTERVAL 46 HOUR), NOW()),
((SELECT id FROM orders WHERE order_code = 'FS-ORD-2026-007'), @organization_id, @supplier_bp, 5, 'Rau củ quả rất tươi, các bé nấu canh ăn ngọt lịm. Quán rất nhiệt tình hỗ trợ khuân vác.', DATE_SUB(NOW(), INTERVAL 45 HOUR), NOW()),
((SELECT id FROM orders WHERE order_code = 'FS-ORD-2026-008'), @recipient_id, @supplier_bp, 5, 'Phở bò tái nạm nước dùng trong và ngọt xương đậm đà, thịt bò mềm tươi!', DATE_SUB(NOW(), INTERVAL 70 HOUR), NOW()),
((SELECT id FROM orders WHERE order_code = 'FS-ORD-2026-009'), @organization_id, @supplier_bp, 5, 'Cơm chay thanh đạm nhưng rất bắt cơm, các cụ già tại viện dưỡng lão khen ngon hết lời.', DATE_SUB(NOW(), INTERVAL 69 HOUR), NOW()),
((SELECT id FROM orders WHERE order_code = 'FS-ORD-2026-010'), @recipient_id, @supplier_bp, 4, 'Bánh bao xá xíu nóng hổi, nhân đầy đặn 2 trứng cút. Giá 12k quá hời!', DATE_SUB(NOW(), INTERVAL 94 HOUR), NOW()),
((SELECT id FROM orders WHERE order_code = 'FS-ORD-2026-011'), @organization_id, @supplier_bp, 5, 'Súp gà nấm ấm nóng, phù hợp cho người bệnh và trẻ nhỏ tẩm bổ.', DATE_SUB(NOW(), INTERVAL 93 HOUR), NOW()),
((SELECT id FROM orders WHERE order_code = 'FS-ORD-2026-012'), @recipient_id, @supplier_bp, 5, 'Trà sen macchiato thơm mát, hạt sen ninh nhừ dẻo ngọt rất ngon.', DATE_SUB(NOW(), INTERVAL 118 HOUR), NOW()),
((SELECT id FROM orders WHERE order_code = 'FS-ORD-2026-013'), @organization_id, @supplier_bp, 5, 'Mái ấm xin chân thành cảm ơn nhà hảo tâm, đồ ăn hỗ trợ rất kịp thời và chu đáo.', DATE_SUB(NOW(), INTERVAL 117 HOUR), NOW()),
((SELECT id FROM orders WHERE order_code = 'FS-ORD-2026-014'), @recipient_id, @supplier_bp, 4, 'Xôi gà xé hạt sen dẻo thơm, gà ướp vừa vặn không bị khô.', DATE_SUB(NOW(), INTERVAL 142 HOUR), NOW()),
((SELECT id FROM orders WHERE order_code = 'FS-ORD-2026-015'), @organization_id, @supplier_bp, 5, 'Hộp trái cây tươi rói, ngọt mát, các bé thích mê.', DATE_SUB(NOW(), INTERVAL 141 HOUR), NOW()),
((SELECT id FROM orders WHERE order_code = 'FS-ORD-2026-016'), @recipient_id, @supplier_bp, 3, 'Chất lượng món ăn ổn, nhưng canh hơi nguội một xíu khi em đến lấy.', DATE_SUB(NOW(), INTERVAL 166 HOUR), NOW()),
((SELECT id FROM orders WHERE order_code = 'FS-ORD-2026-017'), @organization_id, @supplier_bp, 5, 'Chương trình FoodShare thật sự ý nghĩa, cảm ơn quán Quyền đã chia sẻ bữa ăn chất lượng.', DATE_SUB(NOW(), INTERVAL 165 HOUR), NOW()),
((SELECT id FROM orders WHERE order_code = 'FS-ORD-2026-018'), @recipient_id, @supplier_bp, 4, 'Bún chả Hà Nội nướng vừa lửa, nước chấm hơi chua chút nhưng nhìn chung rất ổn.', DATE_SUB(NOW(), INTERVAL 190 HOUR), NOW()),
((SELECT id FROM orders WHERE order_code = 'FS-ORD-2026-019'), @organization_id, @supplier_bp, 5, 'Thực phẩm sữa tươi date xa, đảm bảo tiêu chuẩn an toàn cho trẻ em.', DATE_SUB(NOW(), INTERVAL 189 HOUR), NOW()),
((SELECT id FROM orders WHERE order_code = 'FS-ORD-2026-020'), @recipient_id, @supplier_bp, 5, 'Bánh mì ngon bổ rẻ, chủ quán lúc nào cũng tươi cười niềm nở!', DATE_SUB(NOW(), INTERVAL 214 HOUR), NOW()),
((SELECT id FROM orders WHERE order_code = 'FS-ORD-2026-021'), @organization_id, @supplier_bp, 4, 'Suất ăn đóng gói cẩn thận, ghi chú rõ ràng các phần ăn chay và mặn.', DATE_SUB(NOW(), INTERVAL 213 HOUR), NOW()),
((SELECT id FROM orders WHERE order_code = 'FS-ORD-2026-022'), @recipient_id, @supplier_bp, 2, 'Hôm nay thịt nướng hơi khét và có mùi khói, mong quán chú ý khâu nướng hơn.', DATE_SUB(NOW(), INTERVAL 238 HOUR), NOW()),
((SELECT id FROM orders WHERE order_code = 'FS-ORD-2026-023'), @organization_id, @supplier_bp, 1, 'Món canh bị chua do thời tiết nắng nóng để lâu bên ngoài, bên mình không dám cho trẻ ăn.', DATE_SUB(NOW(), INTERVAL 237 HOUR), NOW());

-- ========================================================================
-- 15. BÁO CÁO KHIẾU NẠI (reports - 20 dòng đầy đủ ReportType & ReportStatus)
-- ========================================================================
INSERT INTO reports (reporter_id, title, content, report_type, report_status, response, evidence_url, reference_type, reference_id, resolved_at, created_at, updated_at) VALUES
(@recipient_id, 'Phần ăn cơm sườn có dấu hiệu thịt bị khét', 'Hôm nay em nhận suất cơm tấm sườn nướng bị cháy đen nhiều góc, ăn đắng.', 'FOOD_QUALITY', 'RESOLVED', 'Admin đã nhắc nhở nhà cung cấp kiểm soát nhiệt độ nướng và tặng bạn mã giảm giá 20k.', 'https://res.cloudinary.com/dqbheiddg/image/upload/v1/foodshare/evidence_com_chay.jpg', 'ORDER', (SELECT id FROM orders WHERE order_code = 'FS-ORD-2026-022'), DATE_SUB(NOW(), INTERVAL 9 DAY), DATE_SUB(NOW(), INTERVAL 10 DAY), DATE_SUB(NOW(), INTERVAL 9 DAY)),
(@organization_id, 'Canh chua bị chua hỏng do thời tiết nắng gắt', 'Canh nhận về vào buổi trưa có bọt khí và vị chua khác thường, không an toàn cho các bé.', 'HYGIENE', 'RESOLVED', 'Đã xác nhận sự cố thời tiết nóng khiến món nhanh thiu. Quán đã xin lỗi và cung cấp lại đợt sữa tươi thay thế.', 'https://res.cloudinary.com/dqbheiddg/image/upload/v1/foodshare/evidence_canh_chua.jpg', 'ORDER', (SELECT id FROM orders WHERE order_code = 'FS-ORD-2026-023'), DATE_SUB(NOW(), INTERVAL 9 DAY), DATE_SUB(NOW(), INTERVAL 10 DAY), DATE_SUB(NOW(), INTERVAL 9 DAY)),
(@recipient_id, 'Chủ quán báo hết món nhưng trên app vẫn hiện AVAILABLE', 'Em đến nơi thì quán báo đã hết bún đậu từ 30 phút trước, làm em tốn công đi xe máy qua.', 'ISSUE', 'REVIEWING', 'Admin đang tiến hành đối soát lịch sử cập nhật số lượng tồn kho của quán.', NULL, 'FOOD_POST', (SELECT MIN(id) FROM food_posts), NULL, DATE_SUB(NOW(), INTERVAL 1 DAY), NOW()),
(@organization_id, 'Đề xuất hỗ trợ thêm tính năng hẹn giờ lấy cho xe tải từ thiện', 'Mong đội ngũ phát triển app bổ sung tùy chọn hẹn giờ trước 2 tiếng để bên em sắp xếp tài xế.', 'FEEDBACK', 'RESOLVED', 'Cảm ơn đóng góp quý báu của tổ chức, tính năng này đã được đưa vào roadmap phiên bản 2.1.', NULL, 'SYSTEM', 1, DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 5 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY)),
(@recipient_id, 'Thanh toán MoMo bị trừ tiền 2 lần cho đơn hàng #004', 'Tài khoản ví MoMo của tôi bị trừ 2 lần 50.000đ khi mạng chập chờn.', 'COMPLAINT', 'RESOLVED', 'Hệ thống đã đối soát cùng MoMo và hoàn lại 50.000đ vào ví của bạn thành công.', 'https://res.cloudinary.com/dqbheiddg/image/upload/v1/foodshare/evidence_momo_double.jpg', 'PAYMENT', 4, DATE_SUB(NOW(), INTERVAL 12 HOUR), DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 12 HOUR)),
(@supplier_id, 'Người nhận đặt đơn nhưng không đến lấy và không nghe máy', 'Có đơn hàng khách bấm nhận lúc 11h trưa nhưng đến 15h chiều không thấy tới, gọi 3 cuộc không bắt máy.', 'COMPLAINT', 'REVIEWING', 'Admin đang liên hệ với người nhận để ghi nhận lý do và cảnh cáo tài khoản nếu cố ý.', NULL, 'ORDER', (SELECT id FROM orders WHERE order_code = 'FS-ORD-2026-024'), NULL, DATE_SUB(NOW(), INTERVAL 4 HOUR), NOW()),
(@recipient_id, 'Hình ảnh đại diện món ăn khác xa thực tế nhận được', 'Ảnh chụp đĩa cơm sườn trứng to đẹp mắt nhưng nhận về chỉ có nửa miếng sườn nhỏ.', 'INAPPROPRIATE', 'REJECTED', 'Sau khi kiểm tra camera đóng gói của quán, định lượng phần ăn đúng cam kết giá 25k trợ giá.', 'https://res.cloudinary.com/dqbheiddg/image/upload/v1/foodshare/evidence_dish_compare.jpg', 'FOOD_POST', (SELECT MIN(id) FROM food_posts), DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 4 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY)),
(@organization_id, 'Nghi vấn có người nhận gom đồ ăn từ thiện mang đi bán lại', 'Phát hiện đối tượng dùng nhiều tài khoản gom hàng chục phần rau miễn phí để mang ra chợ bán.', 'FRAUD', 'REVIEWING', 'Đội ngũ kiểm soát gian lận đang kiểm tra địa chỉ IP và lịch sử nhận hàng của nhóm tài khoản trên.', 'https://res.cloudinary.com/dqbheiddg/image/upload/v1/foodshare/evidence_resell.jpg', 'USER', @recipient_id, NULL, DATE_SUB(NOW(), INTERVAL 2 DAY), NOW()),
(@recipient_id, 'Ứng dụng bị văng khi bấm vào bản đồ tìm đường', 'Mỗi lần ấn nút Xem đường đi qua Google Maps trên điện thoại Android là app bị đóng đột ngột.', 'ISSUE', 'PENDING', NULL, NULL, 'SYSTEM', 2, NULL, DATE_SUB(NOW(), INTERVAL 8 HOUR), NOW()),
(@supplier_id, 'Đề xuất giảm mức phí nền tảng cho các quán ăn thuần từ thiện', 'Các món phát miễn phí 0đ mong ban quản trị không tính bất kỳ chi phí phát sinh nào.', 'FEEDBACK', 'RESOLVED', 'Chính sách hiện tại của FoodShare là 0% phí đối với tất cả bài đăng loại FREE (Miễn phí).', NULL, 'SYSTEM', 3, DATE_SUB(NOW(), INTERVAL 6 DAY), DATE_SUB(NOW(), INTERVAL 7 DAY), DATE_SUB(NOW(), INTERVAL 6 DAY)),
(@recipient_id, 'Quán đóng gói bằng hộp xốp không phân hủy gây ô nhiễm', 'Góp ý quán nên chuyển sang dùng hộp bã mía hoặc lá chuối thân thiện với môi trường.', 'OTHER', 'PENDING', NULL, NULL, 'FOOD_POST', (SELECT MIN(id) FROM food_posts), NULL, DATE_SUB(NOW(), INTERVAL 1 DAY), NOW()),
(@organization_id, 'Muốn cấp quyền cho 3 thành viên khác cùng quản lý tài khoản', 'Mái ấm muốn thêm tài khoản phụ để các bạn nhân viên thay nhau xác nhận đơn.', 'OTHER', 'PENDING', NULL, NULL, 'USER', @organization_id, NULL, DATE_SUB(NOW(), INTERVAL 14 HOUR), NOW()),
(@recipient_id, 'Thái độ nhân viên quán lúc phát cơm rất cọc cằn', 'Nhân viên đưa cơm ném đồ ăn lên bàn và có lời lẽ khó nghe với người đến nhận.', 'COMPLAINT', 'RESOLVED', 'Chủ quán đã trực tiếp chấn chỉnh thái độ nhân viên và gọi điện xin lỗi người nhận.', NULL, 'USER', @supplier_id, DATE_SUB(NOW(), INTERVAL 4 DAY), DATE_SUB(NOW(), INTERVAL 5 DAY), DATE_SUB(NOW(), INTERVAL 4 DAY)),
(@supplier_id, 'Lỗi không cập nhật được ảnh đại diện món ăn mới', 'Upload ảnh đuôi .webp báo lỗi định dạng không hỗ trợ.', 'ISSUE', 'RESOLVED', 'Hệ thống đã cập nhật hỗ trợ định dạng ảnh WEBP trên Cloudinary.', NULL, 'SYSTEM', 4, DATE_SUB(NOW(), INTERVAL 8 DAY), DATE_SUB(NOW(), INTERVAL 9 DAY), DATE_SUB(NOW(), INTERVAL 8 DAY)),
(@recipient_id, 'Tài xế nhận hộ không xuất trình mã đơn hàng đúng', 'Có người xưng là nhận hộ nhưng đọc sai mã đơn hàng #003.', 'FRAUD', 'RESOLVED', 'Quán đã xử lý chuẩn xác bằng việc giữ lại đồ ăn cho đúng chủ tài khoản.', NULL, 'ORDER', (SELECT id FROM orders WHERE order_code = 'FS-ORD-2026-003'), DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY)),
(@organization_id, 'Cảm ơn ban quản trị đã hỗ trợ kết nối nguồn thực phẩm quý báu', 'Thư cảm ơn từ đại diện Ban Giám Đốc Mái Ấm Hy Vọng gửi đến toàn bộ đội ngũ sáng lập FoodShare.', 'FEEDBACK', 'RESOLVED', 'FoodShare vô cùng vinh hạnh được đồng hành và sẻ chia cùng mái ấm!', NULL, 'SYSTEM', 5, DATE_SUB(NOW(), INTERVAL 10 DAY), DATE_SUB(NOW(), INTERVAL 11 DAY), DATE_SUB(NOW(), INTERVAL 10 DAY)),
(@recipient_id, 'Cơm rang dưa bò hơi nhiều dầu mỡ', 'Món ăn hơi ngấy, mong đầu bếp bớt dầu trong các lần tới.', 'FOOD_QUALITY', 'PENDING', NULL, NULL, 'FOOD_POST', (SELECT MIN(id) FROM food_posts), NULL, DATE_SUB(NOW(), INTERVAL 5 HOUR), NOW()),
(@supplier_id, 'Rút tiền báo thành công nhưng tài khoản ngân hàng chưa nhận', 'Mã lệnh rút PO-008 đã duyệt hôm qua nhưng tiền chưa vào tài khoản Vietcombank.', 'ISSUE', 'REVIEWING', 'Admin đang tra soát lệnh chuyển tiền liên ngân hàng NAPAS.', NULL, 'PAYMENT', 8, NULL, DATE_SUB(NOW(), INTERVAL 1 DAY), NOW()),
(@recipient_id, 'Không nhận được mã OTP xác thực qua tin nhắn SMS', 'Bấm gửi lại mã 3 lần vẫn chưa nhận được tin nhắn OTP đăng nhập.', 'ISSUE', 'RESOLVED', 'Đã khắc phục nghẽn cổng kết nối SMS Brandname Infobip.', NULL, 'SYSTEM', 6, DATE_SUB(NOW(), INTERVAL 7 DAY), DATE_SUB(NOW(), INTERVAL 8 DAY), DATE_SUB(NOW(), INTERVAL 7 DAY)),
(@organization_id, 'Báo cáo địa chỉ quán trên bản đồ bị lệch khoảng 200m', 'Vị trí ghim trên bản đồ chỉ ra đầu hẻm thay vì số nhà cụ thể bên trong.', 'OTHER', 'RESOLVED', 'Admin đã hiệu chỉnh lại tọa độ GPS chính xác đến số nhà của quán.', NULL, 'USER', @supplier_id, DATE_SUB(NOW(), INTERVAL 11 DAY), DATE_SUB(NOW(), INTERVAL 12 DAY), DATE_SUB(NOW(), INTERVAL 11 DAY));

-- ========================================================================
-- 16. THÔNG BÁO (notifications - 20 dòng chia đều cho 4 Users)
-- ========================================================================
INSERT INTO notifications (user_id, title, content, is_read, notification_type, reference_type, reference_id, created_at, updated_at) VALUES
-- Admin notifications
(@admin_id, 'Yêu cầu rút tiền mới đang chờ duyệt', 'Nhà cung cấp Quyền Quán vừa gửi yêu cầu rút tiền mã #PO-2026-009 trị giá 190.000đ.', FALSE, 'PAYMENT', 'PAYMENT', 9, DATE_SUB(NOW(), INTERVAL 12 HOUR), NOW()),
(@admin_id, 'Báo cáo khiếu nại chất lượng thực phẩm mới', 'Người nhận gửi khiếu nại về món Cơm sườn cần ban quản trị đối soát giải quyết.', FALSE, 'REPORT', 'REPORT', 1, DATE_SUB(NOW(), INTERVAL 1 DAY), NOW()),
(@admin_id, 'Hồ sơ pháp lý tổ chức thiện nguyện đã nộp', 'Mái Ấm Hy Vọng vừa bổ sung giấy phép hoạt động từ thiện của Sở LĐ-TB&XH.', TRUE, 'REQUEST', 'USER', @organization_id, DATE_SUB(NOW(), INTERVAL 2 DAY), NOW()),
(@admin_id, 'Cảnh báo an toàn hệ thống FoodShare', 'Phát hiện 5 lượt đăng nhập thất bại liên tiếp từ địa chỉ IP lạ.', TRUE, 'SYSTEM', 'SYSTEM', 1, DATE_SUB(NOW(), INTERVAL 3 DAY), NOW()),
(@admin_id, 'Nhà cung cấp mới đăng ký tài khoản', 'Nhà hàng Bếp Cơm Thiện Nguyện vừa đăng ký gia nhập mạng lưới FoodShare.', TRUE, 'NEW_SUPPLIER', 'USER', @supplier_id, DATE_SUB(NOW(), INTERVAL 10 DAY), NOW()),

-- Supplier notifications
(@supplier_id, 'Bạn có đơn đặt món mới #FS-ORD-2026-001', 'Khách hàng Phạm Anh Quyền vừa đặt 2 suất Cơm sườn. Vui lòng kiểm tra và xác nhận.', FALSE, 'ORDER', 'ORDER', (SELECT id FROM orders WHERE order_code = 'FS-ORD-2026-001'), DATE_SUB(NOW(), INTERVAL 30 MINUTE), NOW()),
(@supplier_id, 'Yêu cầu rút tiền #PO-001 đã thành công', 'Số tiền 475.000đ đã được chuyển thành công vào tài khoản Vietcombank của bạn.', TRUE, 'PAYMENT', 'PAYMENT', 1, DATE_SUB(NOW(), INTERVAL 9 DAY), NOW()),
(@supplier_id, 'Đánh giá 5 sao mới từ Mái Ấm Hy Vọng', 'Tổ chức đã để lại lời cảm ơn và đánh giá 5 sao cho đơn hàng cơm trưa thiện nguyện.', TRUE, 'REVIEW', 'ORDER', (SELECT id FROM orders WHERE order_code = 'FS-ORD-2026-005'), DATE_SUB(NOW(), INTERVAL 22 HOUR), NOW()),
(@supplier_id, 'Bài đăng món ăn sắp hết hạn sử dụng', 'Món Cơm sườn sốt mật ong của bạn sẽ hết hạn lấy sau 2 giờ nữa.', FALSE, 'REQUEST', 'FOOD_POST', (SELECT MIN(id) FROM food_posts), DATE_SUB(NOW(), INTERVAL 1 HOUR), NOW()),
(@supplier_id, 'Thu nhập đơn hàng #FS-ORD-2026-004 đã được cộng', 'Bạn nhận được 47.500đ (đã khấu trừ 5% phí nền tảng) vào số dư khả dụng.', TRUE, 'PAYMENT', 'ORDER', (SELECT id FROM orders WHERE order_code = 'FS-ORD-2026-004'), DATE_SUB(NOW(), INTERVAL 23 HOUR), NOW()),

-- Recipient notifications
(@recipient_id, 'Đơn hàng #FS-ORD-2026-003 đã sẵn sàng lấy', 'Quán đã chuẩn bị xong phần ăn. Bạn vui lòng ghé địa chỉ 928 Lê Văn Lương trước 13h.', FALSE, 'ORDER', 'ORDER', (SELECT id FROM orders WHERE order_code = 'FS-ORD-2026-003'), DATE_SUB(NOW(), INTERVAL 15 MINUTE), NOW()),
(@recipient_id, 'Hoàn tiền thành công cho đơn #FS-ORD-2026-024', 'Số tiền 25.000đ đã được hoàn trả lại vào ví MoMo của bạn.', TRUE, 'PAYMENT', 'ORDER', (SELECT id FROM orders WHERE order_code = 'FS-ORD-2026-024'), DATE_SUB(NOW(), INTERVAL 5 HOUR), NOW()),
(@recipient_id, 'Món ăn mới ở gần bạn: Bún chả Hà Nội', 'Quán cơm thiện nguyện cách bạn 300m vừa đăng món ăn trợ giá mới!', FALSE, 'REQUEST', 'FOOD_POST', (SELECT MIN(id) FROM food_posts), DATE_SUB(NOW(), INTERVAL 3 HOUR), NOW()),
(@recipient_id, 'Đơn hàng #FS-ORD-2026-004 đã hoàn tất', 'Bạn hãy dành 30 giây đánh giá chất lượng món ăn để giúp cộng đồng nhé!', TRUE, 'ORDER', 'ORDER', (SELECT id FROM orders WHERE order_code = 'FS-ORD-2026-004'), DATE_SUB(NOW(), INTERVAL 23 HOUR), NOW()),
(@recipient_id, 'Khảo sát người dùng thân thiết FoodShare', 'Góp ý trải nghiệm đặt món để nhận voucher miễn phí 100% cho đơn hàng kế tiếp.', TRUE, 'SYSTEM', 'SYSTEM', 2, DATE_SUB(NOW(), INTERVAL 5 DAY), NOW()),

-- Organization notifications
(@organization_id, 'Đơn hàng #FS-ORD-2026-002 đã được quán xác nhận', 'Chủ quán đã xác nhận đơn hàng 2 suất ăn cho mái ấm và đang chuẩn bị.', FALSE, 'ORDER', 'ORDER', (SELECT id FROM orders WHERE order_code = 'FS-ORD-2026-002'), DATE_SUB(NOW(), INTERVAL 50 MINUTE), NOW()),
(@organization_id, 'Nhận thực phẩm cứu trợ miễn phí: Rau củ quả sạch', 'Nông trại Đà Lạt vừa tài trợ 30 suất rau xanh miễn phí 0đ cách bạn 600m.', FALSE, 'REQUEST', 'FOOD_POST', (SELECT MIN(id) FROM food_posts), DATE_SUB(NOW(), INTERVAL 4 HOUR), NOW()),
(@organization_id, 'Hồ sơ tổ chức từ thiện đã được xác minh', 'Chúc mừng! Tài khoản Mái Ấm Hy Vọng đã đạt huy hiệu Verified trên FoodShare.', TRUE, 'SYSTEM', 'USER', @organization_id, DATE_SUB(NOW(), INTERVAL 8 DAY), NOW()),
(@organization_id, 'Cảm ơn nghĩa cử cao đẹp của tổ chức', 'Hơn 200 suất ăn đã được phân phối thành công đến các hoàn cảnh khó khăn tuần qua.', TRUE, 'SYSTEM', 'SYSTEM', 3, DATE_SUB(NOW(), INTERVAL 4 DAY), NOW()),
(@organization_id, 'Báo cáo khiếu nại #002 của bạn đã được giải quyết', 'Admin đã xử lý khiếu nại về chất lượng món canh chua và hoàn tất đối soát.', TRUE, 'REPORT', 'REPORT', 2, DATE_SUB(NOW(), INTERVAL 9 DAY), NOW());

-- ========================================================================
-- 17. THIẾT BỊ ĐĂNG NHẬP & FCM TOKEN (user_devices - 20 dòng, 5 thiết bị/user)
-- device_type hợp lệ: 'MOBILE', 'WEB'
-- ========================================================================
INSERT INTO user_devices (user_id, fcm_token, device_type, device_name, is_active, created_at, updated_at) VALUES
-- Admin devices
(@admin_id, 'fcm-token-admin-chrome-win11-01', 'WEB', 'Google Chrome trên Windows 11 Pro', TRUE, NOW(), NOW()),
(@admin_id, 'fcm-token-admin-macbook-safari-02', 'WEB', 'MacBook Pro M3 Apple Safari', TRUE, NOW(), NOW()),
(@admin_id, 'fcm-token-admin-iphone15promax-03', 'MOBILE', 'Apple iPhone 15 Pro Max', TRUE, NOW(), NOW()),
(@admin_id, 'fcm-token-admin-ipadpro-04', 'MOBILE', 'Apple iPad Pro M2 Cellular', FALSE, NOW(), NOW()),
(@admin_id, 'fcm-token-admin-firefox-ubuntu-05', 'WEB', 'Mozilla Firefox trên Ubuntu 24.04', FALSE, NOW(), NOW()),

-- Supplier devices
(@supplier_id, 'fcm-token-supplier-samsung-s24-01', 'MOBILE', 'Samsung Galaxy S24 Ultra', TRUE, NOW(), NOW()),
(@supplier_id, 'fcm-token-supplier-chrome-pos-02', 'WEB', 'Google Chrome POS Thu Ngân Quán', TRUE, NOW(), NOW()),
(@supplier_id, 'fcm-token-supplier-xiaomi14-03', 'MOBILE', 'Xiaomi 14 Pro Bếp Trưởng', TRUE, NOW(), NOW()),
(@supplier_id, 'fcm-token-supplier-ipad-air-04', 'MOBILE', 'Apple iPad Air 5 Order Món', FALSE, NOW(), NOW()),
(@supplier_id, 'fcm-token-supplier-edge-laptop-05', 'WEB', 'Microsoft Edge Laptop Kế Toán', FALSE, NOW(), NOW()),

-- Recipient devices
(@recipient_id, 'fcm-token-recipient-iphone14pro-01', 'MOBILE', 'Apple iPhone 14 Pro', TRUE, NOW(), NOW()),
(@recipient_id, 'fcm-token-recipient-chrome-pc-02', 'WEB', 'Google Chrome Máy Tính Văn Phòng', TRUE, NOW(), NOW()),
(@recipient_id, 'fcm-token-recipient-galaxy-tab-03', 'MOBILE', 'Samsung Galaxy Tab S9 FE', TRUE, NOW(), NOW()),
(@recipient_id, 'fcm-token-recipient-oppo-reno-04', 'MOBILE', 'Oppo Reno 10 5G', FALSE, NOW(), NOW()),
(@recipient_id, 'fcm-token-recipient-macbook-air-05', 'WEB', 'MacBook Air M1 Của Quyền', FALSE, NOW(), NOW()),

-- Organization devices
(@organization_id, 'fcm-token-org-ipad-pro-01', 'MOBILE', 'Apple iPad Pro Tiếp Nhận Cứu Trợ', TRUE, NOW(), NOW()),
(@organization_id, 'fcm-token-org-chrome-vanphong-02', 'WEB', 'Google Chrome PC Trực Ban Mái Ấm', TRUE, NOW(), NOW()),
(@organization_id, 'fcm-token-org-pixel8pro-03', 'MOBILE', 'Google Pixel 8 Pro Trưởng Đoàn', TRUE, NOW(), NOW()),
(@organization_id, 'fcm-token-org-redmi-note-04', 'MOBILE', 'Xiaomi Redmi Note 13 Tình Nguyện Viên', FALSE, NOW(), NOW()),
(@organization_id, 'fcm-token-org-firefox-laptop-05', 'WEB', 'Mozilla Firefox Laptop Điều Phối', FALSE, NOW(), NOW());

-- ========================================================================
-- 18. CẤU HÌNH HỆ THỐNG (system_configs - 20 dòng tham số quan trọng)
-- ========================================================================
INSERT INTO system_configs (config_key, config_value, description, data_type, is_public, created_at, updated_at) VALUES
('PLATFORM_FEE_PERCENTAGE', '0.05', 'Tỷ lệ phí nền tảng áp dụng cho các đơn hàng có phí (5%)', 'NUMBER', FALSE, NOW(), NOW()),
('MATCHING_MAX_DISTANCE_KM', '10.0', 'Bán kính quét matching tối đa giữa người nhận và quán ăn (km)', 'NUMBER', TRUE, NOW(), NOW()),
('MATCHING_DEFAULT_MAX_ORDERS', '2', 'Số đơn hàng đang xử lý tối đa của một người nhận trước khi bị tạm dừng matching', 'NUMBER', FALSE, NOW(), NOW()),
('MIN_PAYOUT_AMOUNT', '50000', 'Số tiền rút tối thiểu cho mỗi lệnh rút tiền (VNĐ)', 'NUMBER', TRUE, NOW(), NOW()),
('MAX_PAYOUT_AMOUNT', '10000000', 'Số tiền rút tối đa trong một ngày cho mỗi nhà cung cấp (VNĐ)', 'NUMBER', FALSE, NOW(), NOW()),
('MAX_DOCUMENTS', '5', 'Số lượng tài liệu pháp lý tối đa nhà cung cấp được tải lên xác minh', 'NUMBER', TRUE, NOW(), NOW()),
('MAX_DOCUMENT_SIZE_MB', '10', 'Dung lượng file tối đa cho mỗi tài liệu đính kèm (MB)', 'NUMBER', TRUE, NOW(), NOW()),
('MAINTENANCE_MODE', 'false', 'Chế độ bảo trì hệ thống toàn diện', 'BOOLEAN', TRUE, NOW(), NOW()),
('NOTIFICATION_ENABLED', 'true', 'Bật/tắt toàn bộ dịch vụ gửi thông báo Push qua Firebase', 'BOOLEAN', TRUE, NOW(), NOW()),
('MATCHING_FEATURE_ENABLED', 'true', 'Bật/tắt tính năng gợi ý và phân bổ thông minh Matching Engine', 'BOOLEAN', TRUE, NOW(), NOW()),
('SUPPORT_EMAIL', 'support@foodshare.com', 'Email trung tâm hỗ trợ khách hàng và khiếu nại', 'STRING', TRUE, NOW(), NOW()),
('SUPPORT_HOTLINE', '1900-6868', 'Tổng đài hotline tiếp nhận phản ánh chất lượng 24/7', 'STRING', TRUE, NOW(), NOW()),
('OTP_EXPIRATION_SECONDS', '300', 'Thời gian hiệu lực của mã OTP đăng nhập qua SMS (5 phút)', 'NUMBER', FALSE, NOW(), NOW()),
('ORDER_PICKUP_TIMEOUT_MINUTES', '120', 'Thời gian tối đa khách phải đến lấy món kể từ khi có thông báo sẵn sàng', 'NUMBER', TRUE, NOW(), NOW()),
('AUTO_CANCEL_UNPAID_MINUTES', '15', 'Tự động hủy đơn hàng trực tuyến nếu chưa thanh toán sau N phút', 'NUMBER', FALSE, NOW(), NOW()),
('MOMO_PAYMENT_ENABLED', 'true', 'Cổng thanh toán điện tử ví MoMo', 'BOOLEAN', TRUE, NOW(), NOW()),
('ZALOPAY_PAYMENT_ENABLED', 'true', 'Cổng thanh toán điện tử ví ZaloPay', 'BOOLEAN', TRUE, NOW(), NOW()),
('CASH_PAYMENT_ENABLED', 'true', 'Phương thức thanh toán tiền mặt khi nhận đồ ăn', 'BOOLEAN', TRUE, NOW(), NOW()),
('MAX_ACTIVE_POSTS_PER_SUPPLIER', '30', 'Số lượng bài đăng khả dụng tối đa cùng một lúc của mỗi nhà cung cấp', 'NUMBER', FALSE, NOW(), NOW()),
('APP_MINIMUM_VERSION', '1.0.2', 'Phiên bản ứng dụng tối thiểu bắt buộc người dùng nâng cấp', 'STRING', TRUE, NOW(), NOW())
ON DUPLICATE KEY UPDATE 
    config_value = VALUES(config_value),
    description = VALUES(description),
    data_type = VALUES(data_type),
    is_public = VALUES(is_public),
    updated_at = NOW();

-- ========================================================================
-- HOÀN THÀNH TẠO DỮ LIỆU MẪU
-- ========================================================================
