# OldBook Marketplace - Development Rules

Quy tắc phát triển và chuẩn hóa kiến trúc dự án **OldBook Marketplace**. Mọi thành viên trong đội ngũ kỹ thuật bắt buộc tuân thủ các nguyên tắc này để đảm bảo tính đồng bộ, bảo mật và khả năng mở rộng của hệ thống.

---

## 🏛️ 1. Kiến trúc Dự án (Project Architecture)

* **Phân chia theo Module Nghiệp vụ (Business Module-based)**: Codebase được tổ chức theo từng miền nghiệp vụ (`auth`, `catalog`, `identity`, `store`, `order`, `payment`, `shipping`, `admin`), **không chia theo Actor** (quyền sử dụng).
* **Cấu trúc chuẩn của một Module**:
  ```text
  com.nhom9.oldbook_marketplace.<module>/
  ├── controller/       # Xử lý HTTP Request/Response, validation
  ├── dto/              # Request/Response Data Transfer Objects
  ├── entity/           # JPA Entities ánh xạ Database
  ├── repository/       # Interface thao tác với cơ sở dữ liệu
  └── service/          # Chứa 100% Business Logic nghiệp vụ
  ```
* **Quy tắc Package**: Code mới thuộc nghiệp vụ nào bắt buộc phải đặt đúng package/module đó. Tuyệt đối không thêm class mới vào package cũ chỉ vì thuận tiện.

---

## 👤 2. Định danh, Tài khoản & Phân quyền (Identity & Access)

* **Thực thể Người dùng & Tài khoản**:
  * `NguoiDung`: Đại diện cho người thật (lưu `email`, mật khẩu mã hóa, họ tên, số điện thoại, thông tin cá nhân).
  * `TaiKhoan`: Thuộc về `NguoiDung`. Một `NguoiDung` có thể sở hữu nhiều `TaiKhoan` với các vai trò khác nhau. Mỗi `TaiKhoan` có đúng **1 Vai trò (`VaiTro`)**.
* **Danh sách Vai trò Hợp lệ Enum (`VaiTro.java`)**:
  * `KHACH_HANG`: Khách hàng mua sách.
  * `CHU_CUA_HANG`: Chủ cửa hàng bán sách cũ.
  * `DON_VI_VAN_CHUYEN`: Đối tác giao hàng.
  * `QUAN_LY`: Quản lý nghiệp vụ sàn.
  * `QUAN_TRI_VIEN`: Quản trị viên hệ thống.
  *(Không tự ý thêm role mới trong các module riêng mà chưa thống nhất với nhóm).*
* **Trạng thái Tài khoản Hợp lệ Enum**:
  * `CHO_XAC_THUC`: Đang chờ xác minh OTP/Email.
  * `HOAT_DONG`: Tài khoản đang hoạt động bình thường.
  * `BI_KHOA`: Tài khoản đã bị khóa.

---

## 🔐 3. Bảo mật, Xác thực & Dữ liệu (Security & Data Rules)

* **Xác thực JWT (JSON Web Token)**:
  * JWT chính thức phải chứa 3 thông tin cốt lõi: `maND`, `maTK`, và `vaiTro`.
  * **Chỉ tin tưởng JWT**: Bắt buộc trích xuất danh tính người dùng từ `SecurityContext` / JWT Token. Tuyệt đối **không dùng `maND` do Client gửi** trong Request Body hoặc Params để xác định người dùng đăng nhập.
* **Mật khẩu & Nhật ký**:
  * Mật khẩu phải luôn được mã hóa bằng **BCrypt** trước khi lưu vào Database.
  * Không lưu trữ hoặc log mật khẩu nguyên văn (plaintext) ra console/file log.
* **Phân biệt Rõ ràng**:
  * **Authentication**: Xác định "Người dùng là ai".
  * **Authorization**: Xác định "Người dùng được phép làm gì".

---

## ⚙️ 4. Quy tắc Phân tầng Code (Layering Responsibilities)

| Tầng Code | Trách nhiệm chính | Điều cấm |
| :--- | :--- | :--- |
| **Controller** | Nhận HTTP Request, validate dữ liệu DTO, gọi Service, trả `ApiResponse<T>`. | ❌ Không viết Business Logic trong Controller. |
| **Service** | Xử lý toàn bộ kiểm tra nghiệp vụ, logic xử lý dữ liệu và giao dịch. | ❌ Không trả HTTP Status code trực tiếp. |
| **Repository** | Thao tác CRUD và truy vấn Database qua JPA/JPQL. | ❌ Không đưa Business Logic vào Repository. |
| **Common/DTO** | Chuẩn hóa định dạng phản hồi chung `ApiResponse<T>`. | ❌ Không tự tạo format Response riêng lẻ. |

---

## 🗄️ 5. Cơ sở Dữ liệu & Git Workflow

* **Cơ sở Dữ liệu (Database)**:
  * JPA Entity phải ánh xạ chính xác với Schema Database.
  * Tuyệt đối không tự ý đổi tên cột, khóa chính/ngoại gây ảnh hưởng đến module khác. Mọi thay đổi DB phải được thông báo và đồng bộ với toàn nhóm.
* **Quy trình làm việc Git**:
  * Mỗi thành viên làm việc trên một **Feature Branch** riêng (ví dụ: `feat/auth-jwt`, `feat/store-setup`, `fix/order-calc`).
  * Tuyệt đối **không push/commit trực tiếp lên nhánh `main` / `master`**.
  * Commit Message phải tuân thủ chuẩn **Conventional Commits**:
    * `feat(auth): thêm đăng nhập JWT`
    * `feat(store): thêm chức năng tạo cửa hàng`
    * `fix(order): sửa lỗi tính tổng tiền đơn hàng`
