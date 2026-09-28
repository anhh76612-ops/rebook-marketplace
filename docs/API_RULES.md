# OldBook Marketplace - Quy ước & Chuẩn thiết kế API (API Rules)

Tài liệu quy định toàn bộ chuẩn thiết kế RESTful API, quy ước endpoint, cấu trúc request/response, phân quyền và danh sách API cho dự án **OldBook Marketplace**.

---

## 🚀 Nguyên tắc cốt lõi

* **Base URL**: Tất cả API trong hệ thống đều bắt đầu bằng prefix `/api`
* **Chuẩn Response**: 100% API trả về cấu trúc thống nhất qua `ApiResponse<T>`
* **Xác thực danh tính**: Dùng `Authorization: Bearer <JWT>`. Danh tính người dùng (`maND`, `maTK`) bắt buộc lấy từ JWT/SecurityContext, **tuyệt đối không nhận `maND` từ Request Body**
* **Xử lý lỗi**: Ném lỗi nghiệp vụ qua `BusinessException`, hệ thống tự động catch và format response tại `GlobalExceptionHandler`

---

## 📌 Quy ước Endpoint theo Module

Mỗi module nghiệp vụ sử dụng một prefix riêng biệt dưới `/api`. **Không tự ý đổi convention hoặc chia prefix theo Actor (customer, seller, admin).**

| Prefix Endpoint | Module Nghiệp vụ | Mô tả chức năng |
| :--- | :--- | :--- |
| `/api/auth` | Authentication & Security | Đăng ký, đăng nhập, OTP, đổi/quên mật khẩu, chọn vai trò |
| `/api/sach` | Catalog (Sách & Danh mục) | Tra cứu sách, xem chi tiết, danh mục sản phẩm |
| `/api/profile` | Identity (Hồ sơ cá nhân) | Xem và cập nhật thông tin cá nhân của tài khoản hiện tại |
| `/api/store` | Store Management | Đăng ký cửa hàng, quản lý gian hàng, sản phẩm của shop |
| `/api/order` | Order & Cart | Giỏ hàng, đặt hàng, xử lý đơn hàng, hủy đơn |
| `/api/payment` | Payment & Promotion | Thanh toán, lịch sử giao dịch, mã giảm giá, chiết khấu |
| `/api/shipping` | Shipping | Vận chuyển, tính phí ship, đơn vị giao hàng |
| `/api/admin` | System Administration | Quản trị hệ thống, quản lý tài khoản, nhật ký hệ thống |

---

## 🔄 Quy ước HTTP Method & Status Code

| HTTP Method | Mục đích sử dụng | HTTP Status Trả về |
| :--- | :--- | :--- |
| `GET` | Lấy danh sách hoặc chi tiết dữ liệu | `200 OK` |
| `POST` | Tạo mới tài nguyên / Thực hiện tác vụ nghiệp vụ | `200 OK` hoặc `201 Created` |
| `PUT` | Cập nhật toàn bộ thông tin tài nguyên | `200 OK` |
| `PATCH` | Cập nhật một phần thông tin tài nguyên | `200 OK` |
| `DELETE` | Xóa tài nguyên | `200 OK` |

### Mã Lỗi HTTP & Ý nghĩa
* `200 OK` / `201 Created`: Yêu cầu xử lý thành công.
* `400 Bad Request`: Lỗi dữ liệu đầu vào (Validation) hoặc lỗi quy tắc nghiệp vụ (`BusinessException`).
* `401 Unauthorized`: Thiếu Bearer Token, Token không hợp lệ hoặc đã hết hạn/bị thu hồi.
* `403 Forbidden`: Token hợp lệ nhưng tài khoản không có Role được cấp quyền thực hiện.

---

## 📦 Chuẩn Cấu trúc Response (`ApiResponse<T>`)

Tất cả các API Controller phải trả về kiểu `ApiResponse<T>` với định dạng JSON chuẩn:

### 1. Phản hồi thành công có dữ liệu
```json
{
  "code": 200,
  "message": "Success",
  "data": {
    "maND": 1,
    "email": "user@oldbook.local",
    "hoTen": "Nguyễn Văn A"
  }
}
```

### 2. Phản hồi thành công không kèm dữ liệu (Thông báo)
```json
{
  "code": 200,
  "message": "Đổi mật khẩu thành công",
  "data": null
}
```

### 3. Phản hồi lỗi (Nghép vụ hoặc Validation)
```json
{
  "code": 400,
  "message": "Email hoặc mật khẩu không chính xác",
  "data": null
}
```

---

## 🔐 Xác thực & Phân quyền (Authentication & Authorization)

### Format Header
```http
Authorization: Bearer <JWT_TOKEN>
```

### Quy tắc Token & Claims
* **JWT chính thức** chứa các claim: `maND` (mã người dùng), `maTK` (mã tài khoản), `vaiTro` (mã vai trò).
* **Trường hợp người dùng có nhiều Vai trò**: Sau khi đăng nhập bằng `/api/auth/login`, API trả về `roleSelectionToken` (JWT tạm 5 phút). Client gọi `/api/auth/select-role` kèm `maTK` để nhận **JWT chính thức**.

### Danh sách Vai trò hợp lệ (`VaiTro.java`)
Hệ thống quản lý vai trò nghiêm ngặt theo mã enum chuẩn:
* `KHACH_HANG` (Khách mua sách)
* `CHU_CUA_HANG` (Chủ gian hàng sách cũ)
* `DON_VI_VAN_CHUYEN` (Đối tác giao hàng)
* `QUAN_LY` (Quản lý hệ thống)
* `QUAN_TRI_VIEN` (Quản trị viên / Admin)

*Lưu ý: Không tự ý dùng tên vai trò tiếng Anh như `USER`, `SELLER`, `ADMIN`.*

---

## 📋 Danh sách API đã triển khai (API Reference)

### 1. Authentication & Security (`/api/auth`)

| Method | Endpoint | Quyền truy cập | Mô tả |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/auth/register` | Public | Đăng ký tài khoản mới (khách hàng) |
| `POST` | `/api/auth/verify-register-otp` | Public | Xác thực OTP đăng ký |
| `POST` | `/api/auth/resend-register-otp` | Public | Gửi lại mã OTP đăng ký |
| `POST` | `/api/auth/login` | Public | Đăng nhập tài khoản |
| `POST` | `/api/auth/select-role` | Temporary Token | Chọn vai trò khi đăng nhập (đối với người dùng đa vai trò) |
| `POST` | `/api/auth/logout` | Bearer JWT | Đăng xuất (thu hồi JWT jti) |
| `POST` | `/api/auth/forgot-password` | Public | Yêu cầu gửi OTP quên mật khẩu |
| `POST` | `/api/auth/resend-forgot-password-otp` | Public | Gửi lại OTP quên mật khẩu |
| `POST` | `/api/auth/reset-password` | Public | Đặt lại mật khẩu bằng mã OTP |
| `POST` | `/api/auth/change-password` | Bearer JWT | Đổi mật khẩu tài khoản đang đăng nhập |

### 2. Catalog - Sách (`/api/sach`)

| Method | Endpoint | Quyền truy cập | Mô tả |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/sach` | Public (Guest) | Lấy danh sách sản phẩm sách công khai |

### 3. Identity - Hồ sơ cá nhân (`/api/profile`)

| Method | Endpoint | Quyền truy cập | Mô tả |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/profile` | Bearer JWT | Lấy thông tin cá nhân của người dùng hiện tại |
| `PUT` | `/api/profile` | Bearer JWT | Cập nhật thông tin cá nhân (Họ tên, SĐT, Avatar) |

### 4. Admin - Quản trị hệ thống (`/api/admin/tai-khoan`)

| Method | Endpoint | Quyền truy cập | Mô tả |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/admin/tai-khoan` | QUAN_TRI_VIEN | Tra cứu / Lọc danh sách tài khoản (`tuKhoa`, `vaiTro`, `trangThai`) |
| `POST` | `/api/admin/tai-khoan` | QUAN_TRI_VIEN | Tạo tài khoản mới cho hệ thống |
| `PUT` | `/api/admin/tai-khoan/{maTK}/khoa` | QUAN_TRI_VIEN | Khóa tài khoản người dùng |
| `PUT` | `/api/admin/tai-khoan/{maTK}/mo-khoa` | QUAN_TRI_VIEN | Mở khóa tài khoản |
| `PUT` | `/api/admin/tai-khoan/{maTK}/vai-tro` | QUAN_TRI_VIEN | Thay đổi vai trò của tài khoản |

---

## ⚠️ Checklist Quy định An toàn cho Lập trình viên

1. **Validation**: Mọi DTO nhận dữ liệu vào phải annotate kiểm tra bằng `@NotBlank`, `@Email`, `@Size`, `@Pattern`, `@NotNull`.
2. **Controller gọn nhẹ**: Controller chỉ nhận request, validate DTO, gọi Service và trả về `ApiResponse<T>`. **Không viết business logic trong Controller.**
3. **Bảo mật danh tính**: Luôn lấy `maND` / `maTK` từ `SecurityContextHolder` (thÔNG qua JWT token). Không chấp nhận client tự truyền `maND` trong JSON body để thực hiện thao tác nghiệp vụ.
4. **Throw Exception nhất quán**: Khi phát hiện lỗi nghiệp vụ (không tìm thấy data, sai mật khẩu, tài khoản bị khóa...), sử dụng:
   ```java
   throw new BusinessException("Mô tả lỗi trực quan cho người dùng");
   ```
