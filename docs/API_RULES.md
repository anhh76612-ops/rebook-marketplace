# OldBook Marketplace - API Rules

## Base URL

/api

---

## Authentication

API cần xác thực sử dụng:

Authorization: Bearer <JWT>

---

## API công khai

POST /api/auth/register
POST /api/auth/login
POST /api/auth/select-role
POST /api/auth/verify-register-otp
POST /api/auth/resend-register-otp

API xem sách:
GET /api/sach/**
→ Cho phép Guest, không yêu cầu JWT.

Các API khác mặc định yêu cầu JWT nếu SecurityConfig không khai báo public.

---

## JWT chính thức

JWT chứa:

maND
maTK
vaiTro

Không lấy danh tính người dùng từ:

- maND trong request body
- email do client gửi trong request nghiệp vụ

Danh tính phải lấy từ JWT/SecurityContext.

---

## Role

Role code:

KHACH_HANG
CHU_CUA_HANG
DON_VI_VAN_CHUYEN
QUAN_LY
QUAN_TRI_VIEN

---

## Response

API ưu tiên sử dụng:

ApiResponse<T>

Ví dụ:

{
"code": 200,
"message": "Success",
"data": {}
}

---

## HTTP Method

GET:
Lấy dữ liệu

POST:
Tạo dữ liệu / thực hiện nghiệp vụ

PUT:
Cập nhật dữ liệu

PATCH:
Cập nhật một phần

DELETE:
Xóa dữ liệu

---

## Module API

Mỗi module sử dụng prefix riêng.

Ví dụ:

/api/auth
/api/sach
/api/store
/api/order
/api/payment
/api/shipping
/api/admin

Không tùy ý đặt endpoint khác convention của project.