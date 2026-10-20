# Cổng Quản lý

Khu vực làm việc của vai trò `QUAN_LY`: duyệt, tiền, đối tác. Quản trị viên lo phần hệ thống (xem [admin.md](admin.md)).

## Khung trang

- Mọi trang dùng chung `templates/fragments/manager/layout.html`: sidebar trái, thanh trên (tiêu đề trang, làm mới, đổi giao diện, chuông thông báo, nút "Xem sàn").
- Màu và bố cục ở `static/css/manager.css`, mọi màu là biến `--mg-*` dưới `body.manager-shell`. Có ba giao diện Mặc định / Tối / Tạp chí, dùng chung lựa chọn với trang sàn (`static/js/theme.js`).
- Màn hình hẹp: 768–1279px sidebar thu thành thanh icon; dưới 768px sidebar thành ngăn trượt.
- Từ trang sàn quay lại bằng mục **"Về cổng quản lý"** trong menu tài khoản.

## Chuyển trang không tải lại

`static/js/management/shell.js` bắt click trên link có `data-shell-link`:

1. Tải HTML trang đích, chỉ lấy `<main id="managerMain">` và `<title>`, thay vào trang hiện tại, đổi địa chỉ bằng `history.pushState`.
2. Chạy lại các `<script data-page-script>` của trang đích.
3. Phát sự kiện `manager:leave` trước khi thay nội dung để trang cũ dọn hẹn giờ, bỏ đăng ký.
4. Nút Back/Forward tải lại theo địa chỉ. Lỗi mạng, hoặc trang trả về không có `#managerMain` (ví dụ bị đẩy ra trang đăng nhập), thì tải lại trang bình thường.

Thẻ con trong một trang (ví dụ 3 thẻ của Thanh toán) ghi vào `?tab=` để F5 vẫn mở đúng thẻ.

## Các trang

| Trang | Địa chỉ | Nội dung |
|---|---|---|
| Tổng quan | `/management` | Thẻ chỉ số (chờ duyệt, hoàn tiền chờ xử lý, cửa hàng chờ chi, hoa hồng tháng, giao dịch lệch), bấm vào mở đúng hàng đợi; 5 việc mới nhất; dòng tiền tháng; tỉ lệ giao thành công theo hãng |
| Thống kê | `/management/stats` | Theo kỳ (7 ngày, 30 ngày, tháng, quý, tùy chọn): dòng tiền sàn, theo cửa hàng, đơn chờ xác nhận quá 48 giờ (có nút nhắc), theo đơn vị vận chuyển, hủy và hoàn, theo danh mục, hàng đợi kiểm duyệt |
| Thanh toán & Đối soát | `/management/payments?tab=payouts\|refunds\|transactions` | Chi cho cửa hàng, hoàn tiền cho khách, sổ giao dịch ngân hàng |
| Kiểm duyệt | `/store-moderation` | Duyệt cửa hàng và sách (xem [catalog-store.md](catalog-store.md)) |
| Đơn vị vận chuyển | `/management/shipping-carriers` | Thêm, sửa, tạm dừng đối tác (xem [shipping.md](shipping.md)) |
| Hoa hồng | `/management/commissions` | Tỷ lệ hoa hồng theo danh mục, kèm lịch sử thay đổi |

## Thanh toán & Đối soát

### Chi cho cửa hàng (`tab=payouts`)

- Khoản chi (`yeu_cau_chuyen_tien`, loại `CHI_CUA_HANG`) được lập khi một đơn chuyển khoản giao thành công, số tiền = `doanhThuShop`.
- Danh sách gom theo cửa hàng; mở dòng để xem và tích từng đơn. Mỗi lần chỉ tích được đơn của **một** cửa hàng.
- **Tạo đợt chi VietQR:** máy chủ tính tổng, sinh **mã đợt chi** `DC` + 8 ký tự từ danh sách khoản và tổng tiền, trả mã VietQR với nội dung là mã đợt chi. Quản lý chuyển khoản rồi bấm **Xác nhận đã chuyển**; máy chủ tính lại mã, khớp thì đánh dấu cả đợt `DA_CHUYEN` trong một transaction, ghi `thanh_toan` và báo cho cửa hàng.
- Nếu trong lúc đó có khoản đã bị đổi hoặc đã được Quản lý khác đánh dấu thì mã không còn khớp và việc xác nhận bị từ chối.
- Cửa hàng chưa khai tài khoản thì có nút **Gửi thông báo cập nhật STK** (mỗi người tối đa một lần trong 1 giờ). Cửa hàng chỉ tải ảnh QR mà không có số tài khoản thì hộp thoại hiện ảnh đó để Quản lý tự nhập.

### Hoàn tiền cho khách (`tab=refunds`)

- Khoản hoàn (`HOAN_TIEN_KHACH`) được lập khi đơn chuyển khoản bị hủy, giao không thành công, hoặc tiền về mà không tạo được đơn.
- Tài khoản nhận được điền sẵn theo thứ tự: tài khoản khách chọn lúc hủy đơn (lưu trên `phien_thanh_toan`), rồi tài khoản đã chuyển tiền (Casso gửi kèm). Có tài khoản thì khoản vào thẳng `CHO_XU_LY`, chưa có thì `CHO_THONG_TIN` và Quản lý có nút nhắc khách.
- Lọc theo trạng thái và từ khóa (mã đơn, tên, SĐT, email khách).

### Giao dịch ngân hàng (`tab=transactions`)

Sổ mọi giao dịch Casso đã nhận, lọc theo từ khóa, kết quả đối soát (`KHOP`, `CAN_HOAN_TIEN`, `KHONG_KHOP`, `DANG_XU_LY`) và khoảng ngày, kèm ba số tóm tắt: số giao dịch, tiền khớp đơn, tiền lệch chờ xử lý.

Luồng tiền tổng thể xem [payment.md](payment.md).

## Hoa hồng

- Mỗi danh mục có một cấu hình `HOAT_DONG` trong `cau_hinh_chiet_khau`; chưa có thì dùng mặc định 5% (`marketplace.commission.default-rate`).
- Sửa tỷ lệ (0–100%, kèm ghi chú không bắt buộc): đóng bản ghi cũ (`ngay_ket_thuc`, `NGUNG`), thêm bản ghi mới có người sửa (`ma_tk_cap_nhat`), ghi nhật ký hệ thống. Tỷ lệ mới trùng tỷ lệ đang áp dụng thì bị từ chối.
- Tỷ lệ được lấy **lúc đơn giao thành công**, nên % mới áp cho các đơn giao thành công sau thời điểm lưu.

## Thống kê và liên kết với Admin

`service/management/ThongKeVanHanhService` chỉ đọc, dùng chung cho trang Thống kê của Quản lý và báo cáo của Admin (`/api/admin/bao-cao/dong-tien`, `/api/admin/bao-cao/theo-danh-muc`), nên con số hai bên luôn khớp. Hoa hồng tính trên đơn giao thành công, theo ngày giao.

Sàn đang giữ = khách đã trả vào − đã hoàn − đã chi cửa hàng − hoa hồng − phí ship của đơn đã giao.

Quản lý xem để xử lý việc; Admin chỉ xem và xuất CSV.

## Cập nhật thời gian thực

Dùng kênh WebSocket có sẵn `/ws/thong-bao` qua `window.OldBook.notifications` (`static/js/notifications.js`). `static/js/management/realtime.js` giữ **một** listener và phân phối cho trang đang mở, gộp các tin đến trong 1 giây thành một lần tải lại, giữ nguyên bộ lọc và ô đang tích.

| Tin | Tải lại |
|---|---|
| Thông báo `GIAN_HANG_CHO_DUYET`, `SACH_CHO_DUYET` | Kiểm duyệt, số trên sidebar, Tổng quan |
| Thông báo `CAN_HOAN_TIEN` | Thẻ Hoàn tiền, Tổng quan |
| Thông báo `CAN_CHI_CUA_HANG` | Thẻ Chi cho cửa hàng, Tổng quan |
| Thông báo `GIAO_DICH_LECH` (giao dịch không khớp hoặc phải hoàn) | Thẻ Giao dịch ngân hàng, Tổng quan |
| Tin `type: "VAN_HANH_THAY_DOI"` kèm `phan` (`payouts`, `refunds`, `transactions`, `commissions`, `moderation`) | Phần ghi trong `phan` |

`VAN_HANH_THAY_DOI` không lưu vào bảng thông báo; `service/management/VanHanhPusher` gửi tới mọi tài khoản Quản lý sau khi transaction commit (khách khai tài khoản nhận, một Quản lý bấm "Đã chuyển", đổi hoa hồng...). Nhờ vậy hai Quản lý mở cùng lúc đều thấy thay đổi mà không phải F5.

## API

Mọi API dưới `/api/management/**` chỉ cho `QUAN_LY`.

| Method | URL | Việc |
|---|---|---|
| GET | `/api/management/overview` | Số liệu trang Tổng quan |
| GET | `/api/management/overview/badges` | Số trên sidebar |
| GET | `/api/management/stats?tuNgay&denNgay` | Thống kê vận hành |
| POST | `/api/management/stats/stores/{maCH}/remind-confirm` | Nhắc cửa hàng xác nhận đơn quá 48 giờ |
| GET | `/api/management/payments/store-payouts` | Khoản chi gom theo cửa hàng |
| POST | `/api/management/payments/store-payouts/preview` | `{maYeuCau[]}` → mã đợt chi, tổng tiền, mã VietQR |
| POST | `/api/management/payments/store-payouts/done` | `{maDotChi, maYeuCau[]}` → đánh dấu cả đợt đã chuyển |
| POST | `/api/management/payments/stores/{maCH}/remind-bank` | Nhắc cửa hàng khai tài khoản |
| POST | `/api/management/payments/refunds/{maYeuCau}/remind-bank` | Nhắc khách khai tài khoản |
| GET | `/api/management/payments/transfers?loai&trangThai&tuKhoa` | Danh sách hoàn tiền hoặc chi tiền |
| POST | `/api/management/payments/transfers/{maYeuCau}/done` | Đánh dấu một khoản đã chuyển |
| GET | `/api/management/payments/transactions?tuKhoa&ketQua&tuNgay&denNgay` | Sổ giao dịch ngân hàng |
| GET | `/api/management/commissions` | Tỷ lệ hoa hồng theo danh mục |
| PUT | `/api/management/commissions/{maDM}` | Đổi tỷ lệ |
| GET | `/api/management/commissions/{maDM}/history` | Lịch sử thay đổi của danh mục |
| GET, POST, PUT, PATCH | `/api/management/shipping-carriers/**` | Đối tác vận chuyển |

## File liên quan

| File | Vai trò |
|---|---|
| `controller/management/ManagerWebController.java` | Trang HTML Tổng quan, Thống kê, Hoa hồng |
| `controller/management/QuanLyVanHanhController.java` | API Tổng quan, Thống kê, chi gộp, nhắc, hoa hồng |
| `controller/payment/QuanLyThanhToanController.java` | API hoàn tiền, chi tiền, giao dịch ngân hàng |
| `service/management/` | `QuanLyTongQuanService`, `ThongKeVanHanhService`, `QuanLyChiTraService`, `QuanLyHoaHongService`, `VanHanhPusher` |
| `templates/fragments/manager/layout.html` | Khung trang |
| `templates/management/`, `templates/payment/management.html` | Các trang |
| `static/js/management/` | `shell.js`, `realtime.js`, script từng trang |
| `static/css/manager.css` | Màu và bố cục |
