# OldBook Marketplace - Development Rules

## 1. Kiến trúc project

Project được tổ chức theo module nghiệp vụ.

Mỗi module có thể gồm:

module/
├── controller/
├── dto/
├── entity/
├── repository/
└── service/

Không chia module theo Actor.

Actor là quyền sử dụng hệ thống.
Module phản ánh nghiệp vụ.

---

## 2. Authentication

NguoiDung là người thật.

NguoiDung lưu:
- email
- mật khẩu đã mã hóa
- họ tên
- số điện thoại
- thông tin cá nhân

TaiKhoan thuộc về NguoiDung.

Một NguoiDung có thể có nhiều TaiKhoan.

Mỗi TaiKhoan có đúng một VaiTro.

---

## 3. Vai trò

Các vai trò hợp lệ:

- KHACH_HANG
- CHU_CUA_HANG
- DON_VI_VAN_CHUYEN
- QUAN_LY
- QUAN_TRI_VIEN

Không tự tạo thêm role khác trong module riêng.

Nếu cần thêm role phải thống nhất với nhóm và cập nhật VaiTro.java.

---

## 4. Trạng thái tài khoản

Các trạng thái hợp lệ:

- HOAT_DONG
- BI_KHOA

Không tự tạo tên trạng thái khác.

---

## 5. JWT

JWT chính thức chứa:

- maND
- maTK
- vaiTro

JWT là nguồn xác định người dùng hiện tại.

Không dùng maND do client tự gửi để xác định người dùng.

---

## 6. Password

Không bao giờ lưu mật khẩu dạng nguyên văn.

Mật khẩu phải được xử lý bằng BCrypt.

Không log mật khẩu ra console.

---

## 7. Controller

Controller chịu trách nhiệm:

- nhận HTTP request
- validate request
- gọi Service
- trả HTTP response

Không viết business logic lớn trong Controller.

---

## 8. Service

Service chứa business logic.

Các kiểm tra nghiệp vụ phải đặt ở Service.

---

## 9. Repository

Repository chịu trách nhiệm truy cập database.

Không đặt business logic trong Repository.

---

## 10. API Response

API sử dụng ApiResponse<T> thống nhất.

Không tự tạo response format riêng cho từng module nếu không có lý do rõ ràng.

---

## 11. Authentication và Authorization

Authentication:
- xác định người dùng là ai.

Authorization:
- xác định người dùng được phép làm gì.

Không trộn hai khái niệm này.

---

## 12. Package

Code mới phải đặt đúng module.

Không tạo class mới vào package cũ chỉ vì thuận tiện.

---

## 13. Database

Entity phải phản ánh đúng thiết kế database.

Không tự ý đổi tên cột hoặc quan hệ ảnh hưởng module khác.

Mọi thay đổi DB ảnh hưởng module khác phải báo cho nhóm.

---

## 14. Quy tắc Git

Mỗi thành viên làm trên branch riêng.

Không sửa trực tiếp branch chính nếu chưa thống nhất.

Commit phải mô tả rõ nội dung thay đổi.

Ví dụ:

feat(auth): thêm đăng nhập JWT

feat(store): thêm tạo cửa hàng

fix(order): sửa tính tổng đơn hàng