# Giao diện kết nối API

Giao diện Thymeleaf dùng client `static/js/auth.js` để gửi JSON, đọc `ApiResponse.data`, gắn JWT và hiển thị thông báo lỗi từ backend. Các trang quản lý chỉ là HTML khung công khai; dữ liệu cá nhân và thao tác quản lý vẫn đi qua API được bảo vệ. Phiên hết hạn (401) đưa người dùng về đăng nhập với đường dẫn quay lại. Lỗi thiếu quyền (403) hiển thị trên trang.

| Trang | Chức năng đã kết nối |
|---|---|
| `/login` | Đăng nhập, chọn vai trò khi có nhiều tài khoản, đăng xuất từ menu |
| `/admin/accounts` | Quản trị viên xem/lọc tài khoản, cấp tài khoản, thêm vai trò (tạo thêm một tài khoản cho cùng người dùng), khóa/mở khóa |
| `/store/register` | Khách hàng tự đăng ký gian hàng: tạo tài khoản Chủ cửa hàng và cửa hàng chờ duyệt, rồi chuyển sang tài khoản đó |
| `/store/books`, `/store` | Chủ cửa hàng đăng ký/sửa shop, xem trạng thái/lý do từ chối, xem chi tiết sách ở mọi trạng thái, thêm/sửa sách, điều chỉnh tồn kho, bật/ngừng bán, lọc/sắp xếp/phân trang |
| `/store-moderation` | Quản lý lọc/phân trang cửa hàng và sách, xem chi tiết, duyệt/từ chối kèm lý do |
| `/management/**` | Cổng Quản lý: tổng quan, thống kê, thanh toán và đối soát, đối tác vận chuyển, hoa hồng (xem [management.md](management.md)) |
| `/checkout`, `/orders`, `/payment/pay`, `/payment/refunds` | Đặt hàng COD hoặc chuyển khoản, theo dõi và hủy đơn, quét mã VietQR, nhận hoàn tiền (xem [order.md](order.md), [payment.md](payment.md)) |
| `/store/orders` | Chủ cửa hàng xác nhận, hủy đơn và xử lý yêu cầu hủy |
| `/shipping/{maCode}` | Cổng đơn vị vận chuyển (xem [shipping.md](shipping.md)) |
| `/`, `/books`, `/books/tim-kiem`, `/books/{maSach}` | Danh sách/tìm kiếm/chi tiết sách công khai và thêm vào giỏ (đã có) |
| `/wishlist` | Lưu và bỏ yêu thích từ trang chi tiết, xem danh sách sách yêu thích theo tài khoản khách hàng |
| `/cart` | Xem/thêm/sửa/xóa sản phẩm, xóa giỏ (đã có). Trên các trang khác, nút giỏ hàng nổi mở ngăn giỏ hàng (`fragments/cart-drawer.html`) dùng cùng API |

Mỗi vai trò là một tài khoản riêng của cùng người dùng. Vai trò Chủ cửa hàng có được bằng cách tự đăng ký gian hàng ở `/store/register` (`POST /api/store/register`, chỉ tài khoản Khách hàng) hoặc do quản trị viên thêm; vai trò Quản lý, Quản trị viên do quản trị viên thêm (`POST /api/admin/tai-khoan/{maTK}/vai-tro`). Người có nhiều tài khoản chuyển qua lại trong menu tài khoản (`POST /api/auth/switch-account`), không cần đăng nhập lại; `GET /api/profile` trả thêm `taiKhoans` để giao diện biết có những tài khoản nào. Chuông thông báo trên header đọc `/api/thong-bao` và nhận thông báo mới qua WebSocket `/ws/thong-bao`, mở bằng vé dùng một lần từ `POST /api/thong-bao/ticket`. Sửa thông tin cửa hàng hoặc nội dung sách gửi đối tượng về `CHO_DUYET`. Sửa nội dung sách gửi đủ các trường tùy chọn và không gửi tồn kho; tồn kho và trạng thái bán có thao tác riêng. Ảnh dùng URL theo API hiện có.

Đăng ký tài khoản (xác thực OTP qua email), quên mật khẩu, sửa hồ sơ và đổi mật khẩu có trang riêng: `/register`, `/verify-register`, `/forgot-password`, `/profile`, `/change-password`.

Danh sách yêu thích lưu trong bảng `yeu_thich`, gắn với người dùng từ JWT. `GET /api/wishlist` tải sách yêu thích còn được công khai; `PUT /api/wishlist/{maSach}` lưu sách và `DELETE /api/wishlist/{maSach}` bỏ yêu thích. Các thao tác chỉ dành cho tài khoản khách hàng và không nhận mã người dùng từ trình duyệt. Nút yêu thích cập nhật sau khi API thành công; danh sách vẫn được giữ khi tải lại trang hoặc đăng nhập lại.

## Kiểm tra

```powershell
node --test src/test/js/api-ui.test.cjs
./mvnw test
```

Kiểm thử Node chạy các script giao diện thật với DOM/API giả lập để kiểm tra sự kiện, payload và xử lý lỗi. `ApiUiIntegrationTest` chạy HTTP thật với H2 và email giả lập: render trang/tải script, giữ phân quyền API, kiểm tra dữ liệu tạo tài khoản, đổi vai trò và khóa/mở khóa tài khoản. Bộ kiểm thử store/catalog hiện có kiểm tra JWT, quyền sở hữu, tồn kho và kiểm duyệt. Không ghi dữ liệu vào SQL Server hoặc gửi email thật trong kiểm thử.

Kiểm tra thủ công: đăng nhập quản trị viên → cấp vai trò chủ cửa hàng/quản lý → đăng nhập theo vai trò → tạo cửa hàng → duyệt cửa hàng → thêm sách → duyệt sách → tìm/xem sách công khai → đổi tồn/ngừng bán và kiểm tra lại danh sách công khai.
