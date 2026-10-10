# Kế hoạch hoàn thiện Dashboard Admin – OldBook

> **Phạm vi hiện tại:** Chỉ hoàn thiện Dashboard của Quản trị viên và những phần backend trực tiếp phục vụ dashboard. Không mở rộng sang việc hoàn thiện toàn bộ các màn hình admin hoặc dashboard của Quản lý.

## 1. Mục tiêu

Xây dựng dashboard lấy dữ liệu thật từ SQL Server, giúp Quản trị viên theo dõi tình hình tài khoản, hoạt động kinh doanh toàn sàn và nhật ký hệ thống. Giao diện cần dựa trên mockup hiện có, thống nhất với layout admin và có trạng thái tải dữ liệu, lỗi, dữ liệu rỗng rõ ràng.

## 2. Phạm vi dashboard

### 2.1. Các khu vực chính

1. **Thông tin tổng quan:** lời chào, tên/email admin lấy từ tài khoản thật, thời điểm cập nhật dữ liệu và nút làm mới.
2. **Thẻ KPI:**
   - Tổng số người dùng (`NguoiDung`).
   - Số tài khoản đang hoạt động (`TaiKhoan`).
   - Số tài khoản bị khóa (`TaiKhoan`).
   - Số đơn giao thành công.
   - GMV – tổng giá trị giao dịch theo định nghĩa được chốt cho dự án.
   - Hoa hồng sàn đã ghi nhận.
3. **Biểu đồ phân bố vai trò:** thống kê theo vai trò tài khoản thực tế trong `TaiKhoan`.
4. **Biểu đồ hoạt động theo thời gian:** cho chọn 7 ngày hoặc 30 ngày; thể hiện số đơn, GMV và hoa hồng theo ngày.
5. **Thao tác nhanh:** chỉ điều hướng đến các màn hình đã có như quản lý tài khoản hoặc nhật ký; không triển khai lại nghiệp vụ của các màn hình đó bên trong dashboard.
6. **Nhật ký gần đây:** tối đa 10 bản ghi mới nhất từ `NhatKyHeThong`, gồm thời điểm, hành động, người thực hiện và đối tượng liên quan; có liên kết đến trang nhật ký.

### 2.2. Chỉ số theo dõi chờ xử lý — cân nhắc

Có thể bổ sung các bộ đếm chỉ đọc cho cửa hàng/sách chờ duyệt và yêu cầu hủy đơn chờ xử lý nếu chúng hữu ích cho việc giám sát toàn sàn, dữ liệu đã có và quy tắc đếm được xác nhận. Admin chỉ theo dõi số lượng; nghiệp vụ duyệt sách/cửa hàng vẫn thuộc vai trò **Quản lý**. Không biến dashboard thành màn hình xử lý đơn hàng.

### 2.3. Ngoài phạm vi

- Hoàn thiện các trang Người dùng, Tài khoản & Vai trò, Nhật ký hệ thống độc lập, Cấu hình hệ thống.
- Xây dựng trang Báo cáo & Xuất dữ liệu riêng.
- Xây dựng dashboard cho Quản lý.
- Triển khai toàn bộ hệ thống thông báo dùng chung.
- Thêm chức năng quản trị đơn hàng hoặc kiểm duyệt trực tiếp từ dashboard.
- Thêm các chỉ số máy chủ như CPU/RAM, trạng thái sao lưu nếu chưa có nguồn dữ liệu giám sát thực tế.

**Lưu ý thông báo:** chuông thông báo trên header hiện mới là thành phần giao diện. Hệ thống thông báo cần phục vụ mọi vai trò trên website (ví dụ khách hàng nhận tin về sách mới/đơn hàng, chủ cửa hàng nhận tin về đơn mới/yêu cầu hủy/đánh giá). Đây là một công việc riêng, không nằm trong phạm vi hoàn thiện dashboard lần này; không hiển thị số thông báo giả trên dashboard/header.

## 3. Các vấn đề đã ghi nhận cần kiểm tra

Các mục dưới đây được ghi nhận trong tài liệu rà soát/code snapshot; cần xác minh lại trên nhánh code hiện tại trước khi sửa.

### 3.1. Vấn đề trực tiếp của dashboard

- `src/main/resources/templates/admin/dashboard.html` đang là khung rỗng/chưa hoàn thiện: phần `<head>` trống và phần `<main>` còn hướng dẫn chèn nội dung.
- Chưa có API dashboard tổng hợp KPI, dữ liệu biểu đồ và hàng đợi/công việc cần theo dõi.
- Tên và email admin trong `AdminWebController` đang được hard-code; cần lấy từ ngữ cảnh đăng nhập hoặc dịch vụ hồ sơ phù hợp.
- Mockup có một số dữ liệu/trạng thái không có nguồn dữ liệu đáng tin cậy trong hệ thống, chẳng hạn trạng thái sao lưu, CPU/RAM hoặc lịch sử phiên. Không hiển thị nếu chưa có nguồn thật.
- Chuông trên header đang là thành phần giao diện; không được coi là hệ thống thông báo đã hoạt động.

### 3.2. Vấn đề nền tảng có thể chặn việc kiểm thử dashboard

- Tài liệu ghi nhận route `/admin/accounts` từng bị ánh xạ trùng giữa `AccountWebController` và `AdminWebController`; nếu vẫn còn, ứng dụng có thể không khởi động được.
- `SecurityConfig` được ghi nhận có route khai báo lặp; cần rà soát nếu gây xung đột hoặc làm quyền không nhất quán.
- Các route HTML `/admin/**` từng được ghi nhận chưa có cơ chế bảo vệ thống nhất ở phía server; không được chỉ dựa vào JavaScript để bảo vệ API/dữ liệu. API dashboard phải yêu cầu vai trò `QUAN_TRI_VIEN` ở backend.
- Bộ lọc xác thực phía giao diện từng chỉ xử lý 401 mà chưa xử lý 403 đồng nhất. Dashboard cần thể hiện trạng thái không có quyền phù hợp.
- Dữ liệu admin trong layout hiện dùng dữ liệu mẫu. Cần tránh để thông tin giả xuất hiện như dữ liệu thật.

**Nguyên tắc:** chỉ sửa các lỗi nền tảng thực sự còn tồn tại và trực tiếp cản trở khởi động, phân quyền hoặc kiểm thử dashboard; không tranh thủ tái cấu trúc tất cả trang admin trong đợt này.

## 4. API dự kiến

Các endpoint dưới đây là **đề xuất mới**, không phải xác nhận rằng dự án đã có sẵn.

| Endpoint | Mục đích |
|---|---|
| `GET /api/admin/dashboard/summary` | Trả về các KPI tổng quan và các bộ đếm phụ đã được chốt phạm vi. |
| `GET /api/admin/dashboard/charts?period=7d` | Trả dữ liệu biểu đồ 7 ngày. |
| `GET /api/admin/dashboard/charts?period=30d` | Trả dữ liệu biểu đồ 30 ngày. |
| `GET /api/admin/dashboard/recent-logs?size=10` | Trả tối đa 10 nhật ký mới nhất. |

Có thể gộp hoặc tách endpoint tùy cấu trúc triển khai hiện tại, nhưng DTO và quy tắc dữ liệu phải rõ ràng. Không tải toàn bộ bảng về JavaScript để tự thống kê; ưu tiên truy vấn tổng hợp ở database/repository.

## 5. Quy tắc dữ liệu cần chốt

- **Người dùng và tài khoản là hai khái niệm khác nhau:** tổng người dùng lấy từ `NguoiDung`; số lượng hoạt động/bị khóa và phân bố vai trò lấy từ `TaiKhoan`. Một người có thể có nhiều tài khoản theo vai trò, vì vậy không dùng hai tổng số thay thế cho nhau.
- **Số đơn giao thành công:** thống nhất sử dụng đơn vị thống kê phù hợp với mô hình đơn tổng/đơn cửa hàng. Nếu theo số đơn cửa hàng đã giao thành công thì cần ghi rõ trên giao diện.
- **Mốc thời gian:** doanh số/hoa hồng theo ngày giao thành công (ngày ghi nhận chốt); số đơn phát sinh nếu cần thì dùng ngày tạo đơn và chú thích riêng.
- **GMV:** phải xác định rõ giá trị nào được cộng (ví dụ giá trị hàng hóa, cách tính giảm giá/vận chuyển, xử lý hủy/hoàn trả) trước khi viết truy vấn. Không đồng nhất GMV với doanh thu hoa hồng sàn.
- **Hoa hồng:** tuân theo logic `OrderSettlementService`, trong đó hoa hồng được chốt khi đơn giao thành công; các đơn hoàn trả được xử lý riêng. Không cộng đơn đang chờ, đã hủy hoặc hoàn trả như doanh thu đã chốt.
- **So sánh kỳ trước:** chỉ hiển thị tăng/giảm khi đã có công thức và dữ liệu của kỳ đối chiếu đầy đủ; không tạo phần trăm giả.
- **Biểu đồ 7/30 ngày:** cần trả đủ các mốc ngày, kể cả ngày không có giao dịch (giá trị 0), theo múi giờ và quy tắc thời gian nhất quán.

## 6. Kế hoạch thực hiện

### Giai đoạn 1 — Rà soát và hoàn thiện khung giao diện

- Chạy test hiện có để ghi nhận trạng thái ban đầu.
- Xác minh lỗi route trùng và cấu hình bảo mật có còn tồn tại không; sửa nếu chúng chặn ứng dụng/dashboard.
- Dùng mockup dashboard `code(3).html` làm cơ sở, đưa **phần nội dung dashboard** vào `templates/admin/dashboard.html`.
- Tái sử dụng sidebar, header và fragment chung; không sao chép trùng layout.
- Loại bỏ nội dung chỉ là placeholder và các số liệu tĩnh dễ gây hiểu nhầm.

**Đầu ra:** truy cập được `/admin` hoặc `/admin/dashboard`, giao diện nhất quán và bố cục đáp ứng các kích thước màn hình cần hỗ trợ.

### Giai đoạn 2 — Xây API và service dashboard

- Tạo `AdminDashboardController`, `AdminDashboardService`, DTO chuyên biệt và truy vấn tổng hợp cần thiết.
- Triển khai API summary, charts và recent logs.
- Tính dữ liệu ở backend/database; tránh `findAll()` và lọc toàn bộ dữ liệu ở phía client.
- Bảo vệ endpoint bằng vai trò `QUAN_TRI_VIEN`.

**Đầu ra:** các API trả dữ liệu thật, có kiểu dữ liệu và quy tắc tính toán nhất quán.

### Giai đoạn 3 — Hoàn thiện KPI và biểu đồ

- Kết nối KPI với dữ liệu thật.
- Kết nối biểu đồ phân bố vai trò.
- Kết nối biểu đồ 7/30 ngày.
- Thêm so sánh kỳ trước chỉ khi có dữ liệu và công thức chính xác.
- Đối chiếu số liệu API với truy vấn SQL và các ca đơn giao thành công, đơn hủy, đơn hoàn trả.

**Đầu ra:** KPI và biểu đồ phản ánh đúng dữ liệu và nghiệp vụ OldBook.

### Giai đoạn 4 — Kết nối giao diện

- Tạo file JavaScript riêng, ví dụ `static/js/admin-dashboard.js`.
- Tải KPI, biểu đồ và nhật ký qua API.
- Định dạng tiền VND, số lượng và ngày giờ.
- Thêm nút làm mới và cập nhật trạng thái lần tải gần nhất.
- Xử lý loading, lỗi API, dữ liệu rỗng, 401 (phiên không hợp lệ/hết hạn) và 403 (không có quyền).
- Đảm bảo lỗi tải dữ liệu không khiến dữ liệu cũ bị hiển thị như vừa cập nhật thành công.

**Đầu ra:** dashboard hoạt động hoàn chỉnh với dữ liệu thật; các nút điều hướng có tác dụng thực tế.

### Giai đoạn 5 — Kiểm thử và nghiệm thu

- Bổ sung kiểm thử API cho quyền truy cập, KPI, biểu đồ và nhật ký gần đây.
- Bổ sung/điều chỉnh kiểm thử frontend bằng bộ test Node/DOM harness hiện có.
- Chạy lại kiểm thử Maven và Node.
- Kiểm tra thủ công bằng tài khoản quản trị viên và tài khoản vai trò khác.

**Đầu ra:** dashboard có thể nghiệm thu độc lập mà không yêu cầu hoàn thành các trang admin khác.

## 7. Checklist nghiệm thu

- [ ] Ứng dụng khởi động được; không còn route trùng gây lỗi khởi động.
- [ ] `/admin` và `/admin/dashboard` hiển thị đúng dashboard, không lặp sidebar/header.
- [ ] Tên/email admin lấy từ dữ liệu thật, không hard-code.
- [ ] KPI lấy từ SQL Server và phân biệt đúng người dùng với tài khoản.
- [ ] Biểu đồ phân bố vai trò dựa trên dữ liệu thật.
- [ ] Bộ lọc 7 ngày/30 ngày hoạt động và trả đủ các ngày trong kỳ.
- [ ] GMV và hoa hồng khớp quy tắc đã chốt; không ghi nhận sai đơn hủy/hoàn trả.
- [ ] Nhật ký hiển thị tối đa 10 bản ghi mới nhất từ nguồn dữ liệu thật.
- [ ] Loading, lỗi, dữ liệu rỗng, 401 và 403 được xử lý rõ ràng.
- [ ] Các API dashboard từ chối người dùng không có vai trò `QUAN_TRI_VIEN`.
- [ ] Nút làm mới và các liên kết thao tác nhanh hoạt động.
- [ ] Test Node và Maven chạy thành công; mọi lỗi còn lại được ghi nhận rõ.
- [ ] Không còn số liệu giả, trạng thái sao lưu giả hoặc số thông báo giả trên dashboard.

## 8. Thứ tự ưu tiên

1. **P0:** ứng dụng khởi động được; xác minh/sửa route trùng và bảo mật cần thiết; hoàn thiện khung dashboard.
2. **P1:** API summary và KPI thật.
3. **P1:** API biểu đồ 7/30 ngày và quy tắc GMV/hoa hồng.
4. **P1:** nhật ký gần đây và kết nối frontend.
5. **P2:** chỉ số theo dõi cửa hàng/sách/yêu cầu hủy đang chờ nếu chốt cần thiết.
6. **P2:** so sánh kỳ trước và cải thiện hiệu năng sau khi có số liệu kiểm thử.

## 9. Tệp/thành phần dự kiến liên quan

- `src/main/resources/templates/admin/dashboard.html`
- `src/main/resources/templates/admin/fragments/header.html`
- `src/main/resources/templates/admin/fragments/sidebar.html`
- `src/main/resources/static/js/admin-dashboard.js` (dự kiến mới)
- `src/main/java/com/oldbook/controller/admin/AdminDashboardController.java` (dự kiến mới)
- `src/main/java/com/oldbook/service/admin/AdminDashboardService.java` (dự kiến mới)
- Các DTO/dashboard response và phương thức truy vấn tổng hợp trong repository tương ứng (dự kiến bổ sung)
- `SecurityConfig` và test liên quan, chỉ sửa phần cần thiết để dashboard hoạt động an toàn.

> Danh sách lớp/tệp là đề xuất triển khai. Trước khi tạo mới cần rà soát cấu trúc hiện tại để tránh trùng tên hoặc trùng chức năng.
