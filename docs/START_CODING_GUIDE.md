# OldBook Marketplace - Start Coding Guide

## 1. Mục đích

File này là hướng dẫn chung cho thành viên trước khi bắt đầu code một chức năng/module mới.

Mục tiêu:
- Không tự ý tạo kiến trúc riêng cho từng module.
- Không lặp lại Authentication, JWT, Response hoặc xử lý lỗi đã có.
- Mỗi thành viên có thể phát triển module của mình độc lập nhưng vẫn tích hợp được với phần còn lại.

---

## 2. Việc bắt buộc trước khi code

### Bước 1 - Cập nhật code mới nhất

```bash
git checkout main
git pull
```

Sau đó tạo branch riêng:

```bash
git checkout -b feat/<ten-module>
```

Ví dụ:

```bash
git checkout -b feat/store
```

Không code trực tiếp trên `main`.

### Bước 2 - Đọc tài liệu chung

Đọc theo thứ tự:

1. `docs/DEVELOPMENT_RULES.md`
2. `docs/API_RULES.md`
3. File yêu cầu/use case của chức năng mình phụ trách
4. File repomix-output.xml

Đặc biệt phải kiểm tra:
- Vai trò trong `auth/constant/VaiTro.java`
- Trạng thái trong `auth/constant/TrangThaiTaiKhoan.java`
- JWT/Security trong `auth/config/` và `auth/filter/`
- `ApiResponse` trong `common/dto/`
- Exception chung trong `common/exception/`

### Bước 3 - Chạy project trước khi sửa

```bash
.\gradlew.bat clean build
.\gradlew.bat bootRun
```

Kiểm tra ứng dụng khởi động bình thường.

Kiểm tra tối thiểu:
- API public hoạt động.
- API yêu cầu đăng nhập vẫn yêu cầu JWT.
- Không có lỗi compile trước khi bắt đầu code.

---

## 3. Trước khi tạo class: đọc yêu cầu trước

Mỗi chức năng phải bắt đầu từ yêu cầu/use case, không bắt đầu từ việc tạo Entity.

Xác định trước:

1. Actor nào sử dụng?
2. Điều kiện trước khi thực hiện?
3. Input là gì?
4. Luồng chính là gì?
5. Luồng ngoại lệ là gì?
6. Output là gì?
7. Có quyền đăng nhập hay role nào cần thiết?
8. Dữ liệu nào cần đọc/ghi trong database?

Ví dụ:

```text
Use Case: Tạo cửa hàng
Actor: Người dùng đã đăng nhập
Role: CHU_CUA_HANG / theo quy tắc nghiệp vụ đã thống nhất
Input: thông tin cửa hàng
Output: cửa hàng mới
Database: cua_hang
Authentication: JWT
```

Nếu yêu cầu chưa rõ, hỏi nhóm trước khi thay đổi DB hoặc API.

---

## 4. Quy trình triển khai một chức năng

Thực hiện theo thứ tự:

```text
Yêu cầu / Use Case
        ↓
Thiết kế dữ liệu
        ↓
Entity
        ↓
Request / Response DTO
        ↓
Repository
        ↓
Service
        ↓
Controller
        ↓
Validation + Exception
        ↓
Test API
        ↓
Review + Commit
```

Không bỏ qua bước phân tích nghiệp vụ chỉ vì chức năng nhỏ.

---

## 5. Cấu trúc module

Module mới đặt dưới:

```text
src/main/java/com/nhom9/oldbook_marketplace/<module>/
```

Cấu trúc chuẩn:

```text
<module>/
├── controller/
├── dto/
├── entity/
├── repository/
└── service/
```

Không bắt buộc module nào cũng phải có đủ 5 package ngay từ đầu. Chỉ tạo những tầng thực sự cần.

Module phản ánh nghiệp vụ, không phản ánh Actor.

Ví dụ đúng:

```text
store/
order/
catalog/
payment/
shipping/
```

Không tạo:

```text
customer/
seller/
manager/
admin/
```

chỉ vì đó là Actor.

---

## 6. Entity

Chỉ tạo/sửa Entity sau khi đã kiểm tra thiết kế dữ liệu.

Quy tắc:
- Không tự ý đổi tên bảng/cột đang được module khác sử dụng.
- Không tự ý thay đổi quan hệ giữa Entity.
- Nếu thay đổi DB ảnh hưởng module khác, phải báo nhóm trước.
- Không đặt business logic lớn trong Entity.

Nếu một bảng đã tồn tại trong project, đọc Entity hiện tại trước khi tạo mới.

---

## 7. DTO

Không dùng Entity làm request body nếu chức năng cần dữ liệu riêng.

Ví dụ:

```text
CreateStoreRequest
UpdateStoreRequest
StoreResponse
```

DTO chịu trách nhiệm mô tả dữ liệu API.

Validation đặt ở DTO khi phù hợp:

```java
@NotBlank
@Email
@Size
@NotNull
```

---

## 8. Repository

Repository chỉ phụ trách truy cập database.

Ví dụ:

```java
public interface SachRepository extends JpaRepository<Sach, Integer> {
}
```

Không đặt business logic vào Repository.

---

## 9. Service

Service là nơi xử lý business logic.

Ví dụ:

```text
Controller
   ↓
Service
   ├── kiểm tra quyền/nghiệp vụ
   ├── kiểm tra điều kiện
   ├── tính toán
   └── gọi Repository
```

Các lỗi nghiệp vụ dùng:

```java
throw new BusinessException("Nội dung lỗi");
```

Không tự tạo một hệ thống exception riêng cho module nếu chưa thống nhất với nhóm.

---

## 10. Controller

Controller chỉ nên:
- Nhận HTTP request.
- Validate input.
- Gọi Service.
- Trả response.

Không viết business logic lớn trong Controller.

Response ưu tiên dùng:

```java
ApiResponse<T>
```

Không tự tạo response format khác nếu không có lý do rõ ràng.

---

## 11. Exception dùng chung

Project đã có:

```text
common/exception/
├── BusinessException.java
└── GlobalExceptionHandler.java
```

Sử dụng `BusinessException` cho lỗi nghiệp vụ.

Ví dụ:

```java
if (store == null) {
    throw new BusinessException("Cửa hàng không tồn tại");
}
```

`GlobalExceptionHandler` chịu trách nhiệm chuyển exception thành `ApiResponse`.

Không cần viết `try/catch` lặp lại ở từng Controller cho lỗi nghiệp vụ đã được handler chung xử lý.

---

## 12. Authentication và JWT

Không tự tạo cơ chế đăng nhập riêng cho module.

Authentication hiện tại dùng:

```text
Email + mật khẩu
       ↓
BCrypt
       ↓
JWT
       ↓
JwtAuthenticationFilter
       ↓
SecurityContext
```

JWT chính thức chứa:

```text
maND
maTK
vaiTro
```

Không xác định người dùng bằng `maND` do client tự gửi trong request nghiệp vụ.

Khi cần biết người đang đăng nhập, lấy thông tin từ JWT/SecurityContext.

---

## 13. Role

Role hợp lệ phải dùng `VaiTro.java`:

```text
KHACH_HANG
CHU_CUA_HANG
DON_VI_VAN_CHUYEN
QUAN_LY
QUAN_TRI_VIEN
```

Không tự tạo tên mới như:

```text
SELLER
OWNER
SHOP
USER
ADMINISTRATOR
```

Nếu cần thêm role, phải thống nhất với nhóm và sửa `VaiTro.java` trước.

---

## 14. Public API và API cần đăng nhập

Khách vãng lai được phép xem dữ liệu sách công khai.

Hiện tại SecurityConfig cho phép:

```text
GET /api/sach/**
```

mà không cần JWT.

Các API nghiệp vụ khác mặc định yêu cầu JWT nếu chưa được khai báo `permitAll()`.

Ví dụ:

```text
GET  /api/sach/**          → Public
POST /api/order            → cần JWT
GET  /api/profile          → cần JWT
POST /api/store            → cần JWT
```

Quyền theo role chỉ thêm khi nghiệp vụ/API đã được xác định rõ.

---

## 15. Đặt API

Base URL:

```text
/api
```

Module dùng prefix riêng:

```text
/api/auth
/api/sach
/api/store
/api/order
/api/payment
/api/shipping
/api/admin
```

HTTP method:

```text
GET     → lấy dữ liệu
POST    → tạo dữ liệu / thực hiện nghiệp vụ
PUT     → cập nhật toàn bộ
PATCH   → cập nhật một phần
DELETE  → xóa
```

Không đặt endpoint tùy ý khác convention của project.

---

## 16. Test sau khi code

Mỗi chức năng tối thiểu phải test:

### Happy path

Luồng thành công bình thường.

### Validation

Dữ liệu thiếu/sai định dạng.

### Business exception

Vi phạm quy tắc nghiệp vụ.

### Authentication

Không có JWT khi API yêu cầu đăng nhập.

### Authorization

Đúng JWT nhưng sai role.

### Database

Kiểm tra dữ liệu thật được tạo/cập nhật/xóa đúng.

Có thể sử dụng Postman và pgAdmin để test.

---

## 17. Thymeleaf

Project có thể dùng Thymeleaf cho giao diện theo yêu cầu hệ thống.

Backend module nên hoàn thiện nghiệp vụ/API trước.

Chỉ tạo:

```text
resources/templates/
```

khi phần giao diện của chức năng được phân công.

Không đưa business logic vào HTML/Thymeleaf.

---

## 18. Git trước khi tạo Pull Request

Kiểm tra:

```bash
git status
git diff
gradlew.bat clean build
```

Sau đó commit:

```bash
git add .
git commit -m "feat(<module>): <mo ta>"
git push origin feat/<module>
```

Ví dụ:

```text
feat(store): them dang ky cua hang
feat(order): them tao don hang
feat(catalog): them tim kiem sach
fix(auth): sua xu ly token het han
```

---

## 19. Trước khi Merge

Thành viên phải tự kiểm tra:

- [ ] Đọc `DEVELOPMENT_RULES.md`.
- [ ] Đọc `API_RULES.md`.
- [ ] Không tự tạo Authentication/JWT riêng.
- [ ] Không tự tạo role mới.
- [ ] Không lấy `maND` từ request body để xác định người dùng.
- [ ] Dùng `ApiResponse<T>`.
- [ ] Dùng `BusinessException` cho lỗi nghiệp vụ.
- [ ] Controller không chứa business logic lớn.
- [ ] Repository không chứa business logic.
- [ ] Test API bằng Postman.
- [ ] Kiểm tra database bằng pgAdmin khi có thay đổi dữ liệu.
- [ ] `gradlew.bat clean build` thành công.
- [ ] Không có thay đổi ngoài phạm vi module nếu chưa thống nhất.

---

## 20. Khi cần sửa code dùng chung

Các file sau được xem là code dùng chung:

```text
auth/config/*
auth/constant/*
auth/filter/*
auth/service/JwtService.java
common/dto/ApiResponse.java
common/exception/*
```

Nếu cần sửa các file này:

1. Báo nhóm trước.
2. Nêu rõ lý do.
3. Kiểm tra ảnh hưởng tới các module khác.
4. Build/test toàn project trước khi merge.

---

## 21. Quy tắc cuối cùng

Trước khi code, luôn trả lời được 5 câu:

```text
1. Tôi đang làm Use Case nào?
2. Actor nào được phép thực hiện?
3. Dữ liệu nào được đọc/ghi?
4. API nào được tạo/sửa?
5. Quy tắc nào của project tôi phải tuân theo?
```

Nếu chưa trả lời được 5 câu này thì chưa nên bắt đầu tạo code.
