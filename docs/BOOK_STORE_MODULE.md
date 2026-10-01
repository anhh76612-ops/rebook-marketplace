# Module sách và cửa hàng

## Chạy và kiểm thử

Dự án dùng Maven, Java 21 trở lên. Cấu hình compiler khai báo Lombok annotation processor rõ ràng để build được cả trên JDK 23 trở lên ([Lombok Maven](https://projectlombok.org/setup/maven)).

```powershell
mvn clean verify
mvn spring-boot:run
```

Nếu Maven trên máy này trỏ nhầm cache vào `C:\.m2\repository`, chỉ định cache người dùng:

```powershell
mvn "-Dmaven.repo.local=$env:USERPROFILE\.m2\repository" clean verify
```

Kiểm thử tự động dùng profile `test,catalog-test` và database H2 trong bộ nhớ, không ghi vào SQL Server thật. Khi chạy ứng dụng, datasource vẫn lấy cấu hình SQL Server hiện có. Kiểm tra kết nối database trước khi chạy ứng dụng.

Trang công khai:

- `/` hoặc `/books`: tìm kiếm, lọc, sắp xếp, phân trang sách thật.
- `/books/{maSach}`: chi tiết sách; sách không công khai trả trang 404.

Quản lý shop và kiểm duyệt được triển khai bằng REST API. Dùng Postman hoặc frontend gọi các API dưới đây.

## Trạng thái và quyền

Các trạng thái được lưu bằng chuỗi trong các cột hiện có; không đổi schema:

- Duyệt cửa hàng/sách: `CHO_DUYET`, `DA_DUYET`, `TU_CHOI`.
- Bán sách: `DANG_BAN`, `NGUNG_BAN`.
- Dữ liệu mới hết hàng khi `soLuongTon = 0`, không tạo trạng thái bán riêng. Bộ lọc cũng xem tồn null hoặc âm trong dữ liệu cũ là hết hàng; thao tác ghi mới không cho tồn âm.

Cửa hàng và sách mới ở `CHO_DUYET`. Người bán không nhận quyền tự duyệt qua request body. Mỗi người dùng tối đa một cửa hàng theo khóa unique hiện có.

`CHU_CUA_HANG` được đăng ký/xem/sửa cửa hàng của mình. Cửa hàng phải được duyệt trước khi thêm/sửa sách, thay tồn kho hoặc đổi trạng thái bán. Người bán vẫn xem được sách của mình ở tất cả trạng thái.

`QUAN_LY` và `QUAN_TRI_VIEN` được kiểm duyệt. Tài khoản phải có vai trò tương ứng từ module tài khoản hiện có; đăng ký khách hàng không tự cấp quyền chủ shop hay quản lý.

Sửa thông tin cửa hàng hoặc nội dung sách đưa đối tượng về `CHO_DUYET`. Sửa nội dung sách giữ nguyên tồn kho/trạng thái bán và xóa lý do từ chối cũ. Điều chỉnh tồn kho hoặc ngừng/bật bán giữ nguyên trạng thái duyệt. Chỉ đối tượng `CHO_DUYET` được duyệt/từ chối; duyệt sách yêu cầu cửa hàng đã `DA_DUYET`.

Sách công khai phải đồng thời có sách `DA_DUYET`, cửa hàng `DA_DUYET` và sách `DANG_BAN`. Quy tắc áp dụng cho cả danh sách, chi tiết API và trang web. Sách hết hàng vẫn được hiển thị; có thể lọc `conHang=true`. API công khai không trả dữ liệu tài khoản/chủ shop hay lý do kiểm duyệt.

Dữ liệu cũ có trạng thái null hoặc dùng mã khác không tự được công khai. Cần đối chiếu và thống nhất mã trạng thái của dữ liệu cũ với nhóm; module không tự sửa dữ liệu hiện có.

## API tra cứu công khai

| Method | Endpoint | Nội dung |
|---|---|---|
| GET | `/api/sach` | Tìm kiếm/lọc/phân trang |
| GET | `/api/sach/{maSach}` | Chi tiết sách công khai |
| GET | `/api/sach/danh-muc` | Danh sách danh mục |

Tham số `/api/sach`:

| Tham số | Ý nghĩa |
|---|---|
| `tuKhoa` | Tìm trong tên sách, tác giả, nhà xuất bản; ký tự wildcard được hiểu là ký tự thường |
| `maDM`, `maCH` | Lọc theo danh mục/cửa hàng |
| `giaTu`, `giaDen` | Khoảng giá bán cũ, gồm hai đầu mút |
| `doMoiTu`, `doMoiDen` | Khoảng độ mới từ 0 đến 100 |
| `conHang` | `true`: tồn > 0; `false`: hết hàng |
| `page`, `size` | Mặc định 0/12; size từ 1 đến 100 |
| `sort` | `moi-nhat` (mặc định), `cu-nhat`, `gia-tang`, `gia-giam`, `ten-az` |

Ví dụ:

```http
GET /api/sach?tuKhoa=java&giaTu=20000&giaDen=100000&conHang=true&page=0&size=12&sort=gia-tang
```

Danh sách sách trả về `ApiResponse<PageResponse<SachResponse>>`. **`data` hiện là đối tượng phân trang**, thay cho mảng entity trước đây:

```json
{
  "code": 200,
  "message": "Success",
  "data": {
    "content": [],
    "page": 0,
    "size": 12,
    "totalElements": 0,
    "totalPages": 0
  }
}
```

## API chủ cửa hàng

Tất cả request cần `Authorization: Bearer <JWT>` của `CHU_CUA_HANG`. Danh tính và quyền sở hữu được lấy từ SecurityContext, không nhận `maND`, chủ shop hoặc trạng thái duyệt từ client.

| Method | Endpoint | Nội dung |
|---|---|---|
| POST | `/api/store` | Đăng ký cửa hàng |
| GET | `/api/store/me` | Cửa hàng của mình; gồm lý do từ chối nếu có |
| PUT | `/api/store/me` | Sửa và gửi lại cửa hàng để duyệt |
| GET | `/api/store/me/sach` | Sách của shop, phân trang/lọc trạng thái duyệt |
| GET | `/api/store/me/sach/{maSach}` | Chi tiết sách của shop |
| POST | `/api/store/me/sach` | Thêm sách chờ duyệt |
| PUT | `/api/store/me/sach/{maSach}` | Sửa toàn bộ nội dung sách và gửi duyệt lại |
| PATCH | `/api/store/me/sach/{maSach}/ton-kho` | Đặt số lượng tồn hiện tại |
| PATCH | `/api/store/me/sach/{maSach}/trang-thai-ban` | Bật/ngừng bán |

Danh sách riêng hỗ trợ `page=0`, `size=20` (tối đa 100), `trangThaiDuyet` tùy chọn, `sort=ngayTao,desc`. Trường sort hợp lệ: `maSach`, `tenSach`, `giaBanCu`, `soLuongTon`, `ngayTao`; chiều `asc` hoặc `desc`.

Body tạo/sửa cửa hàng:

```json
{
  "tenCuaHang": "Tiệm sách cũ",
  "moTa": "Sách đã qua sử dụng",
  "diaChiCH": "TP Hồ Chí Minh",
  "soDienThoaiCH": "0901234567"
}
```

Body thêm sách (`maDM` phải tồn tại, giá bán > 0, tồn không âm):

```json
{
  "maDM": 1,
  "tenSach": "Lập trình Java",
  "tacGia": "Nguyễn Văn A",
  "nhaXuatBan": "NXB Giáo dục",
  "namXuatBan": 2024,
  "giaBia": 150000,
  "giaBanCu": 75000,
  "doMoiPercent": 80,
  "tinhTrangVatLy": "Còn nguyên trang",
  "moTaChiTiet": "Sách có ghi chú ở vài trang",
  "soLuongTon": 10,
  "hinhAnhUrl": "https://example.com/book.jpg"
}
```

Body sửa sách dùng các trường nội dung ở trên, **không có `soLuongTon`**. Trường tùy chọn bị bỏ qua trong PUT sẽ được xóa về null; gửi đầy đủ các trường muốn giữ lại. Tồn kho và trạng thái bán có API riêng:

```json
{"soLuongTon": 12}
```

```json
{"trangThaiBan": "NGUNG_BAN"}
```

Tồn kho được cập nhật theo số lượng tuyệt đối, không phải số lượng cộng/trừ. Module không tự trừ kho khi đặt hàng; module đơn hàng cần tích hợp giao dịch và cùng quy tắc khóa cửa hàng trước, sách sau. Ngừng bán thay cho xóa cứng để giữ các tham chiếu/lịch sử đơn hàng.

## API kiểm duyệt

Tất cả request cần JWT của `QUAN_LY` hoặc `QUAN_TRI_VIEN`.

| Method | Endpoint | Nội dung |
|---|---|---|
| GET | `/api/store/kiem-duyet/cua-hang` | Danh sách cửa hàng |
| GET | `/api/store/kiem-duyet/sach` | Danh sách sách |
| GET | `/api/store/kiem-duyet/cua-hang/{maCH}` | Chi tiết cửa hàng |
| GET | `/api/store/kiem-duyet/sach/{maSach}` | Chi tiết sách |
| POST | `/api/store/kiem-duyet/cua-hang/{maCH}/duyet` | Duyệt cửa hàng |
| POST | `/api/store/kiem-duyet/cua-hang/{maCH}/tu-choi` | Từ chối cửa hàng |
| POST | `/api/store/kiem-duyet/sach/{maSach}/duyet` | Duyệt sách |
| POST | `/api/store/kiem-duyet/sach/{maSach}/tu-choi` | Từ chối sách |

Danh sách mặc định `trangThaiDuyet=CHO_DUYET`, `page=0`, `size=20`; hỗ trợ lọc `tuKhoa`, `maCH`, trạng thái duyệt. Sắp xếp cố định theo ngày tạo tăng dần, rồi mã đối tượng.

Body từ chối:

```json
{"lyDo": "Ảnh không khớp với thông tin sách"}
```

Lý do bắt buộc, tối đa 500 ký tự. Mỗi lần duyệt/từ chối ghi `NhatKyHeThong` cùng giao dịch với chuyển trạng thái. Lý do từ chối sách nằm trong trường hiện có; lý do từ chối cửa hàng được lưu trong nhật ký và hiển thị lại qua DTO, không thêm cột mới.

## Phân quyền, lỗi và kiểm thử

Security chain riêng chỉ áp dụng `/api/store/**`; tái sử dụng JWT filter hiện có. Danh sách riêng/kiểm duyệt không nằm dưới prefix GET công khai `/api/sach/**`.

- HTTP 400: sai dữ liệu, JSON sai định dạng/kiểu, bộ lọc không hợp lệ, thiếu đối tượng hoặc vi phạm trạng thái nghiệp vụ.
- HTTP 401: thiếu/hỏng/hết hạn JWT hoặc JWT chọn vai trò tạm khi gọi API store.
- HTTP 403: sai vai trò hoặc truy cập sách của shop khác.

API dùng `ApiResponse<T>` hiện có. Không sửa cơ chế auth/common dùng chung. Hai HTTP request duyệt cùng một sách chỉ một request thành công; request còn lại nhận lỗi trạng thái. Nếu ghi nhật ký thất bại, chuyển trạng thái được rollback.

Bộ kiểm thử gồm truy vấn JPA, validation/service unit tests, luồng HTTP với JWT thật, chống truy cập shop khác, cập nhật kho và duyệt đồng thời, rollback audit, và render trang công khai. H2 kiểm tra nghiệp vụ và truy vấn; vẫn cần kiểm tra smoke trên SQL Server của nhóm, nhất là collation/dữ liệu tiếng Việt, các mã trạng thái cũ và kế hoạch tích hợp module đơn hàng.

Kết quả xác minh bản triển khai: Maven `verify` thành công, 106 tests, 0 failures/errors/skipped. Build được chạy trong bản sao tạm để giữ nguyên các output đã staged trong `target` của workspace.

Luồng kiểm tra thủ công: chủ shop tạo cửa hàng → quản lý duyệt cửa hàng → chủ shop thêm sách → quản lý duyệt sách → khách tìm/xem chi tiết → chủ shop đổi tồn kho/ngừng bán → kiểm tra lại hiển thị công khai.
