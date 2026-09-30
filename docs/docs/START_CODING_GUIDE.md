# 🚀 OldBook Marketplace - Start Coding Guide

Hướng dẫn nhập cuộc (Onboarding Guide) dành cho tất cả thành viên phát triển hệ thống **OldBook Marketplace**. File này quy định các bước chuẩn bị, quy trình phân tích và tiêu chuẩn viết code trước khi bạn bắt đầu xây dựng bất kỳ tính năng hay module nào.

---

## 🎯 Mục Tiêu & Triết Lý Phát Triển

* **Kiến trúc đồng nhất**: Đảm bảo toàn bộ các module tuân thủ cấu trúc layered architecture chung của dự án.
* **Tái sử dụng thành phần hệ thống**: Không tự ý chế lại các cơ chế Xác thực (Authentication), Phân quyền (Authorization), Quản lý Session/JWT, Chuẩn phản hồi (`ApiResponse`) hay Xử lý lỗi (`GlobalExceptionHandler`).
* **Độc lập & Tích hợp**: Mỗi developer có thể phát triển module nghiệp vụ của mình một cách độc lập nhưng vẫn tích hợp mượt mà vào hệ thống chung.

---

## ⚡ 3 Bước Bắt Bắt Bắt Đầu Code

### 1. Cập nhật Codebase & Tạo Nhánh Tính Năng
Tuyệt đối **không code trực tiếp trên nhánh `main`**.

```bash
# 1. Chuyển về main và cập nhật mới nhất
git checkout main
git pull origin main

# 2. Tạo nhánh feature theo tên module
git checkout -b feat/<ten-module>

# Ví dụ
git checkout -b feat/store
git checkout -b feat/order
```

### 2. Đọc Tài Liệu & Kiểm Tra Component Dùng Chung
Đọc theo đúng thứ tự ưu tiên trước khi viết dòng code đầu tiên:
1. `docs/DEVELOPMENT_RULES.md` — Quy tắc phát triển chung.
2. `docs/API_RULES.md` — Quy ước thiết kế & chuẩn RESTful API.
3. Tài liệu Yêu cầu / Use Case của chức năng phụ trách.
4. File `repomix-output.xml` (hoặc `repomix-output.txt`) — Tra cứu cấu trúc & code hiện tại.

**Các thành phần dùng chung cần kiểm tra trước:**
* **Vai trò**: `com.nhom9.oldbook_marketplace.auth.constant.VaiTro`
* **Trạng thái tài khoản**: `com.nhom9.oldbook_marketplace.auth.constant.TrangThaiTaiKhoan`
* **Bảo mật & JWT**: Các class trong package `auth.config` và `auth.filter`
* **Chuẩn Response**: `com.nhom9.oldbook_marketplace.common.dto.ApiResponse`
* **Xử lý Exception**: `com.nhom9.oldbook_marketplace.common.exception` (`BusinessException`, `GlobalExceptionHandler`)

### 3. Kiểm Tra Local Build
Đảm bảo ứng dụng chạy thành công trên máy local trước khi thực hiện thay đổi:

```bash
# Clean và build dự án
.\gradlew.bat clean build

# Khởi chạy Backend Application
.\gradlew.bat bootRun
```
> **Checklist nhanh**:
> - API Public (`GET /api/sach/**`) truy cập bình thường không cần token.
> - API Yêu cầu xác thực trả về `401 Unauthorized` nếu không gửi JWT.
> - Dự án build thành công 100%, không có lỗi biên dịch.

---

## 🧠 5 Câu Hỏi Bắt Buộc & Luồng Phân Tích Nghiệp Vụ

Trước khi tạo bất kỳ class hay file code nào, bạn phải trả lời được **5 câu hỏi "Vàng"**:

```text
┌─────────────────────────────────────────────────────────────────────────┐
│ 1. Tôi đang làm Use Case nào?                                           │
│ 2. Actor / Vai trò (Role) nào được phép thực hiện?                      │
│ 3. Dữ liệu nào cần Đọc / Ghi vào Database?                              │
│ 4. Endpoint API nào cần Tạo hoặc Cập nhật?                              │
│ 5. Quy tắc chung nào của dự án tôi phải tuân theo?                      │
└─────────────────────────────────────────────────────────────────────────┘
```

### Quy Trình Triển Khai Chức Năng (Workflow 9 Bước)

```text
Phân tích Use Case
       │
       ▼
Thiết kế Dữ liệu (Schema)
       │
       ▼
Tạo / Cập nhật Entity
       │
       ▼
Định nghĩa DTO (Request / Response)
       │
       ▼
Xây dựng Repository (JPA / JPQL)
       │
       ▼
Viết Business Logic tại Service
       │
       ▼
Khai báo Controller (Endpoint & Validation)
       │
       ▼
Kiểm thử API (Postman / Database)
       │
       ▼
Review & Commit (Conventional Commits)
```

---

## 🧩 Quy Chuẩn Cấu Trúc Module

Mỗi module nghiệp vụ mới được đặt trong thư mục:
`src/main/java/com/nhom9/oldbook_marketplace/<module>/`

### Cấu Trúc Phân Tầng Chuẩn
```text
<module>/
├── controller/     ──► Tiếp nhận HTTP Request, Validate, Gọi Service, Trả ApiResponse
├── dto/            ──► Data Transfer Objects (Request/Response) & Validation Annotations
├── entity/         ──► JPA Entities ánh xạ Cơ sở dữ liệu
├── repository/     ──► Spring Data JPA Interface truy xuất Database
└── service/        ──► Chứa 100% Business Logic & Throw BusinessException
```

> 📌 **Lưu ý**:
> - Module được tổ chức theo **Miền nghiệp vụ** (ví dụ: `store/`, `order/`, `payment/`, `shipping/`), **không** tạo package theo Actor (như `customer/`, `seller/`, `admin/`).
> - Chỉ tạo những tầng thực sự cần thiết, không bắt buộc tạo đủ 5 package nếu module nhỏ.

---

## 🏗️ Quy Tắc Cho Từng Tầng Code

### 1. Entity Layer
* Đọc kỹ Entity hiện có và Database Schema trước khi sửa đổi.
* Không tự ý đổi tên bảng, tên cột hay mối quan hệ (`@OneToMany`, `@ManyToOne`) gây ảnh hưởng module khác.
* Báo với toàn nhóm nếu có thay đổi cấu trúc bảng dùng chung.

### 2. DTO Layer & Validation
* Tuyệt đối **không dùng Entity trực tiếp** làm Request Body trong API.
* Dùng DTO riêng cho từng tác vụ (ví dụ: `CreateStoreRequest`, `UpdateStoreRequest`, `StoreResponse`).
* Đặt annotation validation trực tiếp trên DTO: `@NotBlank`, `@NotNull`, `@Email`, `@Size`,...

### 3. Repository Layer
* Chỉ phụ trách các thao tác I/O với Database.
* Không đặt logic nghiệp vụ hay tính toán vào Repository.

### 4. Service Layer
* Là nơi duy nhất tập trung Business Logic.
* Kiểm tra điều kiện, phân quyền nghiệp vụ và tính toán dữ liệu tại đây.
* Khi vi phạm quy tắc nghiệp vụ, throw `BusinessException`:
  ```java
  if (cuaHang == null) {
      throw new BusinessException("Cửa hàng không tồn tại");
  }
  ```

### 5. Controller Layer
* Giữ Controller mỏng (Lean Controller): chỉ nhận request, validate DTO, gọi Service và bọc kết quả vào `ApiResponse<T>`.
* Trả về kết quả qua `ApiResponse`:
  ```java
  return ApiResponse.success("Tạo cửa hàng thành công", storeResponse);
  ```

---

## 🔒 Quy Tắc Bảo Mật & Xác Thực JWT

* **Cơ chế xác thực**: Email/Password ➔ BCrypt Hash ➔ Bearer JWT ➔ `JwtAuthenticationFilter` ➔ `SecurityContext`.
* **Trích xuất danh tính**: Danh tính người dùng (`maND`, `maTK`, `vaiTro`) **phải lấy từ JWT/SecurityContext**. Tuyệt đối không lấy `maND` từ Request Body do Client gửi lên.
* **Mã Role hợp lệ**: Sử dụng Enum trong `VaiTro.java`:
  * `KHACH_HANG`
  * `CHU_CUA_HANG`
  * `DON_VI_VAN_CHUYEN`
  * `QUAN_LY`
  * `QUAN_TRI_VIEN`
* **API Security Config**:
  * **Public**: `GET /api/sach/**` (Khách vãng lai xem thông tin sách).
  * **Protected**: Mọi API khác mặc định yêu cầu Header `Authorization: Bearer <JWT>`.

---

## 🧪 Checklist Kiểm Thử API (Testing)

Mỗi API sau khi hoàn thành bắt buộc phải pass các kịch bản test sau trên Postman / pgAdmin:

| Kịch Bản Test | Mô Tả Yêu Cầu |
| :--- | :--- |
| **Happy Path** | Đưa input đúng, dữ liệu lưu vào DB thành công, trả về code 200/201. |
| **Validation Test** | Truyền input thiếu/sai định dạng, trả về lỗi validation rõ ràng. |
| **Business Exception** | Vi phạm quy tắc (vd: trùng email, số lượng < 0), throw `BusinessException`. |
| **Authentication Test** | Không truyền Token vào API bảo mật ➔ Trả về `401 Unauthorized`. |
| **Authorization Test** | Truyền Token đúng nhưng sai Role ➔ Trả về `403 Forbidden`. |
| **Database Verification** | Kiểm tra trực tiếp trên pgAdmin xem record được Insert/Update chuẩn xác. |

---

## 🛠️ Quy Trình Commit & PR (Git Protocol)

### 1. Format Commit Message
Tuân thủ chuẩn **Conventional Commits**:

```bash
git add .
git commit -m "feat(<module>): <mô tả ngắn gọn bằng tiếng Việt hoặc tiếng Anh>"
git push origin feat/<module>

# Ví dụ
git commit -m "feat(store): thêm API đăng ký cửa hàng"
git commit -m "feat(order): thêm logic tính tổng tiền đơn hàng"
git commit -m "fix(auth): sửa lỗi xử lý token hết hạn"
```

### 2. Checklist Soát Lỗi Trước Khi Tạo Pull Request (Pre-Merge Checklist)

- [ ] Đã đọc `DEVELOPMENT_RULES.md` và `API_RULES.md`.
- [ ] Không tự chế cơ chế Auth/JWT hay Role mới ngoài `VaiTro.java`.
- [ ] Lấy danh tính người dùng từ JWT, không lấy `maND` từ Request Body.
- [ ] Mọi API đều phản hồi bằng `ApiResponse<T>`.
- [ ] Dùng `BusinessException` cho lỗi nghiệp vụ.
- [ ] Controller mỏng, không chứa business logic lớn.
- [ ] Repository chỉ làm nhiệm vụ truy vấn DB.
- [ ] Lệnh `.\gradlew.bat clean build` chạy thành công 100% không lỗi.
- [ ] Đã test thành công các kịch bản API trên Postman và pgAdmin.

---

## ⚠️ Quy Định Khi Sửa Đổi Code Dùng Chung (Shared Components)

Các thành phần thuộc phạm vi dùng chung bao gồm:
* `auth/config/*` & `auth/filter/*`
* `auth/constant/*` (`VaiTro.java`, `TrangThaiTaiKhoan.java`)
* `auth/service/JwtService.java`
* `common/dto/ApiResponse.java`
* `common/exception/*` (`BusinessException.java`, `GlobalExceptionHandler.java`)

> 🚨 **Quy tắc bắt buộc**: Khi muốn chỉnh sửa bất kỳ file nào ở trên:
> 1. Thông báo và thảo luận trước với cả nhóm.
> 2. Nêu rõ lý do và đánh giá mức độ ảnh hưởng đến các module khác.
> 3. Build & Test lại toàn bộ hệ thống trước khi gửi Yêu cầu Merge (PR).
