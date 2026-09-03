# FoodShare API Documentation

Tài liệu này mô tả chi tiết toàn bộ các API của hệ thống FoodShare, bao gồm các quy tắc về xác thực, phân quyền, sở hữu dữ liệu, các luồng trạng thái, và định dạng phản hồi.

## 1. Authentication API
Tất cả các API (ngoại trừ Auth và Public) đều yêu cầu Header: `Authorization: Bearer <token>`

- `POST /api/auth/register`: Đăng ký tài khoản mới.
- `POST /api/auth/login`: Đăng nhập, nhận JWT token.
- `POST /api/auth/google`: Xác thực qua Google OAuth2.
- `POST /api/auth/refresh`: Cấp lại Access Token thông qua Refresh Token.
- `POST /api/auth/logout`: Đăng xuất (xóa token khỏi hệ thống/blacklist).

## 2. User & Profile API
**Quy tắc Sở hữu (Ownership Rule):** Người dùng chỉ có thể xem/cập nhật thông tin hồ sơ của chính mình. Admin có quyền xem tất cả.

- `GET /api/users/me`: Lấy thông tin hồ sơ hiện tại.
- `PUT /api/users/me`: Cập nhật thông tin (Tên, SĐT, Avatar).
- `PATCH /api/users/me/complete-profile`: Bổ sung thông tin đối với tài khoản đăng nhập qua Google.

## 3. FoodPost API (Quản lý Bài đăng)
**Role Requirement:** `SUPPLIER`, `ORGANIZATION`, `ADMIN`. `RECIPIENT` chỉ có quyền Read-only.
**Ownership Rule:** Supplier chỉ có thể sửa/xóa/cập nhật trạng thái bài đăng do chính mình tạo.

- `POST /api/food-posts`: Tạo bài đăng mới. Yêu cầu nhập số lượng, loại (FREE/PAID).
- `GET /api/food-posts`: Lấy danh sách bài đăng (có phân trang, filter theo category, distance, keyword).
- `GET /api/food-posts/{id}`: Xem chi tiết.
- `PUT /api/food-posts/{id}`: Sửa bài đăng.
- `PATCH /api/food-posts/{id}/status`: Cập nhật trạng thái bài đăng (ACTIVE, OUT_OF_STOCK, HIDDEN).

## 4. Order API (Quản lý Đơn hàng)
**Role Requirement:** `RECIPIENT`, `ORGANIZATION`, `SUPPLIER`.
**Luồng trạng thái (State Transition):**
`PENDING` -> `ACCEPTED` / `REJECTED` -> `READY_FOR_PICKUP` -> `DELIVERED` -> `COMPLETED`.
Hoặc `PENDING` -> `CANCELLED` (Bởi người đặt).

- `POST /api/orders`: Tạo đơn hàng (RECIPIENT).
- `POST /api/orders/batch`: Tạo hàng loạt đơn hàng từ giỏ hàng đa nhà cung cấp (ORGANIZATION).
- `GET /api/orders/my`: Lấy danh sách đơn (Người đặt hoặc Người nhận tùy theo role).
- `GET /api/orders/{id}`: Chi tiết đơn hàng.
- `PATCH /api/orders/{id}/accept`: Nhà cung cấp chấp nhận đơn.
- `PATCH /api/orders/{id}/reject`: Nhà cung cấp từ chối (kèm lý do).
- `PATCH /api/orders/{id}/ready`: Cập nhật trạng thái sẵn sàng lấy.
- `PATCH /api/orders/{id}/complete`: Người dùng xác nhận đã nhận hàng thành công.
- `PATCH /api/orders/{id}/cancel`: Người dùng huỷ đơn (chỉ khi đang ở trạng thái PENDING).

## 5. Payment & Payout API
**Validation:** Chỉ áp dụng cho đơn hàng PAID. Đơn hàng FREE thì totalAmount = 0, bỏ qua payment.

- `POST /api/payments/create-intent`: Khởi tạo phiên giao dịch VNPAY/Momo cho đơn hàng.
- `GET /api/payments/callback`: Xử lý kết quả trả về từ Cổng thanh toán. Cập nhật PaymentStatus của đơn.
- `GET /api/payouts/accounts`: Lấy thông tin tài khoản rút tiền của Supplier.
- `POST /api/payouts/request`: Supplier yêu cầu rút tiền từ ví.
- `GET /api/payouts/history`: Lịch sử giao dịch, tính toán Phí Nền Tảng (Platform Fee) và Số thực nhận (Net Amount).

## 6. Notification API
- `GET /api/notifications`: Lấy danh sách thông báo.
- `PATCH /api/notifications/{id}/read`: Đánh dấu đã đọc.
- `PATCH /api/notifications/read-all`: Đánh dấu tất cả đã đọc.

## 7. Review API
- `POST /api/reviews`: Gửi đánh giá cho đơn hàng (chỉ khi Order = COMPLETED).
- `GET /api/reviews/post/{postId}`: Xem các đánh giá của 1 bài đăng.
- `GET /api/reviews/supplier/{supplierId}`: Xem đánh giá của 1 nhà cung cấp.

## 8. Report API
- `POST /api/reports`: Báo cáo nội dung vi phạm (User, FoodPost, Order).
- `GET /api/reports`: (Dành cho Admin) Lấy danh sách báo cáo.

## 9. Admin API (Quản trị hệ thống)
**Role Requirement:** Phải là `ADMIN`.

- `GET /api/admin/statistics/overview`: Thống kê tổng quan (doanh thu, đơn hàng, users).
- `GET /api/admin/users`: Quản lý danh sách người dùng.
- `PATCH /api/admin/users/{id}/activate` / `deactivate`: Khóa/Mở khóa tài khoản.
- `GET /api/admin/reports`: Quản lý báo cáo.
- `PATCH /api/admin/reports/{id}/resolve` / `dismiss`: Xử lý báo cáo.
- `PATCH /api/admin/food-posts/{id}/hide`: Ẩn bài đăng vi phạm.
- `GET /api/admin/settings`: Lấy cấu hình hệ thống.
- `PUT /api/admin/settings`: Cập nhật cấu hình hệ thống.

## 10. Error Response & Validation
Toàn bộ hệ thống trả về HTTP Error Code chuẩn kèm body JSON:
```json
{
  "timestamp": "2026-08-25T14:30:00.000Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Số lượng yêu cầu vượt quá số lượng hiện có",
  "path": "/api/orders"
}
```

- **400 Bad Request:** Lỗi Validation (e.g. `quantity > availableQuantity`).
- **401 Unauthorized:** Token hết hạn, sai hoặc không cung cấp.
- **403 Forbidden:** Tài khoản không đủ quyền (Role) hoặc vi phạm Ownership Rule.
- **404 Not Found:** Không tìm thấy tài nguyên.
- **409 Conflict:** Lỗi chuyển trạng thái không hợp lệ (State Transition Error).
