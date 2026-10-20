# Tài khoản, đăng nhập và phân quyền

## Người dùng, tài khoản và vai trò

- Một **người dùng** (`nguoi_dung`) có thể có nhiều **tài khoản** (`tai_khoan`), mỗi tài khoản đúng một vai trò: `KHACH_HANG`, `CHU_CUA_HANG`, `DON_VI_VAN_CHUYEN`, `QUAN_LY`, `QUAN_TRI_VIEN`.
- Đăng ký trên web tạo tài khoản `KHACH_HANG`.
- Vai trò `CHU_CUA_HANG` có được khi khách tự đăng ký gian hàng (`POST /api/store/register`) hoặc do Quản trị viên cấp.
- `QUAN_LY` và `QUAN_TRI_VIEN` chỉ do Quản trị viên cấp (`POST /api/admin/tai-khoan/{maTK}/vai-tro`). Không có API xóa tài khoản; chỉ khóa và mở khóa.

## Đăng ký và xác thực OTP

1. `POST /api/auth/register`: tạo người dùng và tài khoản ở trạng thái chờ xác thực, gửi mã OTP qua Gmail.
2. `POST /api/auth/verify-register-otp`: nhập mã để kích hoạt. Mã sống 5 phút.
3. `POST /api/auth/resend-register-otp`: gửi lại mã; phải chờ 60 giây giữa hai lần gửi.

Quên mật khẩu dùng cùng cơ chế: `POST /api/auth/forgot-password` gửi OTP, `POST /api/auth/resend-forgot-password-otp` gửi lại, `POST /api/auth/reset-password` đặt mật khẩu mới. Đổi mật khẩu khi đã đăng nhập: `POST /api/auth/change-password`.

Trang tương ứng: `/register`, `/verify-register`, `/forgot-password`, `/change-password`.

## Đăng nhập

1. `POST /api/auth/login` với email và mật khẩu.
2. Người dùng chỉ có một tài khoản đang hoạt động thì nhận JWT ngay. Có nhiều tài khoản thì nhận danh sách vai trò; chọn bằng `POST /api/auth/select-role` (dùng mã chọn vai trò do bước 1 trả về) để nhận JWT.
3. Trình duyệt lưu JWT trong `localStorage` và gửi `Authorization: Bearer <JWT>` ở mọi lời gọi API (`static/js/auth.js`). Hạn token đặt ở `jwt.expiration` (mặc định 24 giờ).
4. Sau đăng nhập, trang đích theo vai trò: Khách hàng `/`, Chủ cửa hàng `/store/books`, Quản lý `/management`, Quản trị viên `/admin/dashboard`. Đường dẫn `?redirect=` chỉ được chấp nhận nếu là đường dẫn trong cùng trang web.

Chuyển sang tài khoản vai trò khác của cùng người dùng mà không đăng nhập lại: `POST /api/auth/switch-account` (menu "Chuyển sang…"). `GET /api/profile` trả thêm `taiKhoans` để giao diện biết có những vai trò nào.

`POST /api/auth/logout` thu hồi token: mã định danh (`jti`) của token bị ghi vào danh sách thu hồi đến lúc token hết hạn, nên token đó không dùng lại được.

## Phân quyền

Các trang HTML được phục vụ công khai (chỉ là khung); dữ liệu và thao tác đi qua API có kiểm tra quyền trong `config/auth/SecurityConfig.java`.

| API | Vai trò |
|---|---|
| Đăng ký, OTP, đăng nhập, chọn vai trò, quên mật khẩu; `GET /api/sach/**` | Ai cũng gọi được |
| `/api/wishlist/**`, `/api/recently-viewed/**`, `/api/reviews/**`, `/api/payment/**` | `KHACH_HANG` |
| `POST /api/payment/casso/webhook` | Không cần đăng nhập; bắt buộc chữ ký HMAC-SHA512 hợp lệ |
| `POST /api/store/register` | `KHACH_HANG` |
| `/api/store/**` (còn lại) | `CHU_CUA_HANG` |
| `/api/store/kiem-duyet/**`, `/api/management/**` | `QUAN_LY` |
| `/api/admin/**` | `QUAN_TRI_VIEN` |
| `/api/shipping/{maCode}/**` | Không cần đăng nhập; dữ liệu giới hạn theo mã đơn vị (xem [shipping.md](shipping.md)) |
| `/ws/**` | Bắt tay WebSocket bằng vé dùng một lần (`POST /api/thong-bao/ticket`), không gửi JWT |
| Mọi API còn lại (`/api/order/**`, `/api/profile/**`, `/api/thong-bao/**`...) | Cần đăng nhập; nghiệp vụ tự kiểm tra vai trò (ví dụ chỉ Khách hàng được đặt hàng) |

Lỗi 401 (chưa đăng nhập hoặc token hết hạn) đưa người dùng về `/login` kèm đường dẫn quay lại; lỗi 403 hiện thông báo thiếu quyền ngay trên trang. Quản trị viên muốn duyệt cửa hàng hay xử lý thanh toán phải có thêm tài khoản vai trò Quản lý.

## Tài khoản bị khóa

Quản trị viên khóa (`PUT /api/admin/tai-khoan/{maTK}/khoa`) hoặc mở khóa (`.../mo-khoa`) từng tài khoản. Tài khoản bị khóa không đăng nhập được; các tài khoản vai trò khác của cùng người dùng không bị ảnh hưởng. Mọi thao tác khóa, mở khóa, cấp vai trò đều ghi nhật ký hệ thống.

## File liên quan

| File | Vai trò |
|---|---|
| `controller/auth/AuthController.java` | API đăng ký, OTP, đăng nhập, chọn và chuyển vai trò, mật khẩu |
| `service/auth/AuthService.java`, `JwtService.java` | Nghiệp vụ xác thực, cấp và kiểm tra JWT |
| `config/auth/SecurityConfig.java` | Luật phân quyền theo đường dẫn |
| `controller/identity/AdminTaiKhoanController.java` | Quản trị viên tạo tài khoản, cấp vai trò, khóa/mở khóa |
| `static/js/auth.js` | Lưu token, gắn header, menu theo vai trò, chuyển tài khoản |
