# Trang Quản trị

Khu vực của vai trò `QUAN_TRI_VIEN`: tài khoản, nhật ký và báo cáo của hệ thống. Duyệt, tiền và đối tác là việc của Quản lý (xem [management.md](management.md)). Đăng nhập, vai trò và phân quyền xem [auth.md](auth.md).

## Giao diện

- Mọi trang dùng chung `templates/admin/fragments/` (`head`, `sidebar`, `header`, `footer`, `modal`, `notification`), dựng bằng Tailwind CSS (CDN).
- Màu khai báo bằng biến CSS `--ad-*` (ba kênh RGB) trong `admin/fragments/head.html`; cấu hình Tailwind đọc các biến này, nên đổi giao diện chỉ cần đổi biến. Giao diện Tối và Tạp chí nằm ở `static/css/admin-theme.css`.
- Giao diện Mặc định dùng tông nâu đỏ; Tạp chí dùng giấy sáng, sidebar màu mực sepia, nút đỏ gạch như theme Tạp chí của trang sàn. Bộ chọn giao diện ở thanh trên dùng chung lựa chọn với trang sàn và cổng Quản lý (`static/js/theme.js`).
- Tiêu chí màu: vùng lớn (nền, sidebar) ít bão hòa, chữ phụ có tương phản từ khoảng 5:1 trở lên vì trang dùng nhiều chữ cỡ 11–13px.
- Thanh trên có nút **"Xem sàn"** (`/books`); từ trang sàn quay lại bằng mục **"Về trang quản trị"** trong menu tài khoản.

## Các trang

| Trang | Địa chỉ | Nội dung |
|---|---|---|
| Tổng quan | `/admin/dashboard` | Số người dùng, tài khoản theo trạng thái, phân bổ vai trò, nhật ký gần đây có phân trang, thẻ "Sàn đang giữ" |
| Người dùng | `/admin/users`, `/admin/users/{maND}` | Tra cứu người dùng, hồ sơ chi tiết, xuất CSV |
| Tài khoản & Vai trò | `/admin/accounts` | Lọc tài khoản, tạo tài khoản, cấp thêm vai trò, khóa và mở khóa |
| Nhật ký hệ thống | `/admin/audit-logs` | Lọc theo từ khóa, hành động, vai trò người thực hiện, địa chỉ IP, khoảng ngày; xuất CSV |
| Báo cáo doanh thu | `/admin/reports` | GMV và hoa hồng theo ngày, dòng tiền sàn, doanh thu theo danh mục; chỉ xem và xuất CSV |
| Hồ sơ Admin | `/admin/profile` | Sửa thông tin cá nhân, tải ảnh đại diện |

Báo cáo dòng tiền và theo danh mục dùng chung `ThongKeVanHanhService` với trang Thống kê của Quản lý, nên số liệu hai bên khớp nhau. Admin không có nút thao tác trên tiền.

## API

Mọi API dưới `/api/admin/**` chỉ cho `QUAN_TRI_VIEN`.

| Method | URL | Việc |
|---|---|---|
| GET | `/api/admin/dashboard/summary` | Số liệu trang Tổng quan |
| GET | `/api/admin/nguoi-dung/{maND}` | Hồ sơ một người dùng |
| GET | `/api/admin/tai-khoan` | Danh sách tài khoản, có lọc |
| POST | `/api/admin/tai-khoan` | Tạo tài khoản |
| POST | `/api/admin/tai-khoan/{maTK}/vai-tro` | Cấp thêm vai trò (tạo thêm một tài khoản cho cùng người dùng) |
| PUT | `/api/admin/tai-khoan/{maTK}/khoa`, `.../mo-khoa` | Khóa, mở khóa |
| GET | `/api/admin/nhat-ky` | Tìm nhật ký |
| GET | `/api/admin/nhat-ky/summary` | Thống kê nhật ký |
| GET | `/api/admin/nhat-ky/export` | Dữ liệu xuất CSV |
| GET | `/api/admin/bao-cao/doanh-thu?tuNgay&denNgay` | GMV và hoa hồng theo ngày |
| GET | `/api/admin/bao-cao/dong-tien?tuNgay&denNgay` | Dòng tiền sàn |
| GET | `/api/admin/bao-cao/theo-danh-muc?tuNgay&denNgay` | Doanh thu và hoa hồng theo danh mục |

Ngày dạng `yyyy-MM-dd`, cả hai ngày đều tính vào kỳ. File CSV được tạo ở trình duyệt, có BOM để Excel đọc đúng tiếng Việt.

## Nhật ký hệ thống

Bảng `nhat_ky_he_thong` ghi các thao tác quan trọng: duyệt và từ chối cửa hàng, sách; tạo tài khoản, cấp vai trò, khóa, mở khóa; đổi hoa hồng. Thao tác duyệt ghi nhật ký trong cùng transaction; ghi nhật ký lỗi thì thao tác bị hủy.

## File liên quan

| File | Vai trò |
|---|---|
| `controller/admin/` | Trang HTML (`AdminWebController`) và API dashboard, người dùng, nhật ký, báo cáo |
| `controller/identity/AdminTaiKhoanController.java` | API tài khoản và vai trò |
| `templates/admin/` | Các trang và fragment dùng chung |
| `static/js/admin/` | `layout.js` (sidebar, thông báo, hồ sơ) và script từng trang |
| `static/css/admin-theme.css` | Giao diện Tối và Tạp chí |
