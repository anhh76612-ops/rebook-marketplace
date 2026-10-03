entity/shipping/DonViVanChuyen.java	Thêm maCode, phiVanChuyen, thoiGianGiaoDuKien
entity/shipping/VanChuyen.java	Thêm ngayTao

Mô tả chi tiết chức năng đặt hàng – OldBook (rebook-marketplace)
Tài liệu mô tả luồng đặt hàng sau khi bổ sung ô tích chọn sản phẩm trong giỏ hàng: từ lúc thêm sách vào giỏ, chọn sản phẩm, chọn địa chỉ/vận chuyển, đến khi hệ thống tạo đơn.
---
1. Tổng quan luồng đặt hàng
```
Trang sách ──(Thêm vào giỏ)──► /cart ──(tích chọn + Đặt hàng)──► /checkout ──(Xác nhận)──► /order-success
                                  ▲                                  │
                                  └────── nếu chưa chọn gì ◄─────────┘
```
Bước	Trang	Việc người dùng làm
1	Chi tiết/danh sách sách	Bấm "Thêm vào giỏ"
2	`/cart`	Tăng/giảm số lượng, xóa dòng, tích chọn sản phẩm muốn mua
3	`/checkout`	Chọn địa chỉ nhận hàng, chọn đơn vị vận chuyển + ghi chú cho từng cửa hàng, chọn thanh toán
4	`/order-success`	Xem kết quả đơn vừa tạo
5	`/orders`	Xem lại danh sách đơn đã đặt
Điều kiện: phải đăng nhập bằng tài khoản khách hàng (`KHACH_HANG`). Tài khoản vai trò khác sẽ thấy thông báo "chỉ dành cho khách hàng".
---
2. Bước 1 – Thêm sách vào giỏ
Nút có thuộc tính `data-add-to-cart`, `data-ma-sach`, `data-so-luong` (xử lý trong `static/js/cart.js`).
Gọi API: `POST /api/order/cart/items` với body `{ "maSach": 12, "soLuong": 1 }`.
Backend (`GioHangService.addItem`) kiểm tra:
Sách đã duyệt, đang bán, cửa hàng đã duyệt.
Sách có giá, còn hàng.
Không được mua sách của chính cửa hàng mình.
Tổng số lượng trong giỏ không vượt tồn kho.
Nếu sách đã có trong giỏ thì cộng dồn số lượng vào dòng cũ.
---
3. Bước 2 – Trang giỏ hàng `/cart` (có ô tích chọn)
3.1. Dữ liệu hiển thị
Trang gọi `GET /api/order/cart` và nhận về danh sách dòng (`items`). Mỗi dòng có: `maCTGioHang`, `tenSach`, `donGia`, `soLuong`, `thanhTien`, `maCH`, `tenCuaHang`, `canhBao`, `coTheMua`.
`coTheMua = false` khi: sách ngừng bán, hết hàng, hoặc số lượng trong giỏ vượt tồn kho (kèm nội dung `canhBao`).
3.2. Quy tắc tích chọn
Tình huống	Hành vi
Vào giỏ lần đầu	Tất cả sản phẩm mua được được tích sẵn
Sản phẩm có cảnh báo (`coTheMua = false`)	Ô tích bị khóa, không tính vào tổng tiền
Ô "chọn tất cả" (đầu bảng)	Tích/bỏ tích mọi sản phẩm mua được; hiện trạng thái một phần khi chỉ chọn một số
Tăng/giảm số lượng, xóa dòng	Giữ nguyên các lựa chọn hiện có; dòng đã xóa hoặc không còn mua được tự bị bỏ khỏi lựa chọn
Không tích gì	Nút Đặt hàng bị vô hiệu hóa, hiện gợi ý "Hãy tích chọn sản phẩm muốn đặt hàng"
3.3. Tóm tắt đơn hàng
Khối bên phải chỉ tính các sản phẩm đã tích:
Sản phẩm đã chọn (số dòng)
Số lượng sách
Tạm tính / Tổng cộng
3.4. Khi bấm "Đặt hàng"
Lấy danh sách `maCTGioHang` của các sản phẩm đã tích.
Lưu vào `sessionStorage` với khóa `ob_checkout_items`, ví dụ `[15, 18, 21]`.
Chuyển sang `/checkout`.
> Dùng `sessionStorage` nên lựa chọn chỉ tồn tại trong tab hiện tại và tự mất khi đóng tab.
---
4. Bước 3 – Trang đặt hàng `/checkout`
4.1. Tải dữ liệu
Đọc `ob_checkout_items`. Nếu rỗng → tự chuyển về `/cart`.
Gọi song song 3 API:
`GET /api/order/cart` – giỏ hàng hiện tại
`GET /api/profile/addresses` – địa chỉ nhận hàng của người dùng
`GET /api/order/shipping-carriers` – các đơn vị vận chuyển
Lọc giỏ hàng, chỉ giữ dòng mua được và nằm trong danh sách đã chọn. Số lượng, tiền hàng và tổng tiền được tính lại trên các dòng này.
Nếu sau khi lọc không còn dòng nào → hiện "Giỏ hàng đang trống".
4.2. Các phần của form
a) Địa chỉ nhận hàng
Danh sách địa chỉ (radio), địa chỉ mặc định được chọn sẵn.
Có thể thêm địa chỉ mới ngay trong modal (`POST /api/profile/addresses`), sau khi lưu sẽ tự chọn địa chỉ vừa thêm.
Chưa có địa chỉ → hiện cảnh báo, không đặt được.
b) Vận chuyển & ghi chú theo từng cửa hàng
Các sản phẩm đã chọn được gom theo cửa hàng (`maCH`).
Mỗi cửa hàng có:
Ô chọn đơn vị vận chuyển (bắt buộc), hiển thị phí và thời gian giao dự kiến.
Ô ghi chú cho cửa hàng (tối đa 1000 ký tự).
Mỗi cửa hàng giao hàng riêng nên phí ship tính theo từng cửa hàng.
c) Thanh toán
Hiện chỉ hỗ trợ COD (thanh toán khi nhận hàng). Voucher/mã giảm giá chưa áp dụng.
d) Tóm tắt
Tiền hàng = tổng thành tiền các sản phẩm đã chọn.
Phí vận chuyển = tổng phí các đơn vị vận chuyển đã chọn (mỗi cửa hàng một khoản).
Giảm giá = 0 đ.
Tổng thanh toán = Tiền hàng + Phí vận chuyển − Giảm giá.
4.3. Kiểm tra phía giao diện trước khi gửi
Chưa chọn địa chỉ → "Vui lòng chọn địa chỉ giao hàng."
Còn cửa hàng chưa chọn đơn vị vận chuyển → "Vui lòng chọn đơn vị vận chuyển cho từng cửa hàng." (và focus vào ô thiếu).
---
5. Bước 4 – Gửi yêu cầu tạo đơn
`POST /api/order/orders` (cần JWT của khách hàng)
```json
{
  "maDiaChi": 3,
  "phuongThucTT": "COD",
  "maCTGioHangs": [15, 18, 21],
  "storeOrders": [
    { "maCH": 2, "maDVVC": 1, "ghiChu": "Giao giờ hành chính" },
    { "maCH": 5, "maDVVC": 2, "ghiChu": null }
  ]
}
```
Trường	Ý nghĩa	Ràng buộc
`maDiaChi`	Địa chỉ giao hàng	Bắt buộc, phải thuộc tài khoản đang đăng nhập
`phuongThucTT`	Phương thức thanh toán	Bắt buộc, hiện chỉ `COD`
`maCTGioHangs`	Các dòng giỏ hàng đã tích chọn	Bắt buộc, ít nhất 1 phần tử
`storeOrders`	Vận chuyển + ghi chú theo từng cửa hàng	Bắt buộc, mỗi cửa hàng của sản phẩm đã chọn phải có đúng 1 mục
---
6. Bước 5 – Backend xử lý (`DonHangService.createOrder`)
Toàn bộ chạy trong một transaction: lỗi ở bất kỳ bước nào thì không có gì được ghi.
Kiểm tra thanh toán: chỉ nhận COD.
Kiểm tra người dùng và địa chỉ: địa chỉ phải thuộc người dùng này.
Khóa giỏ hàng (`findByIdForUpdate`) để hai request đặt hàng đồng thời không dùng chung một giỏ.
Lọc theo lựa chọn:
Lấy mọi dòng trong giỏ, chỉ giữ dòng có `maCTGioHang` nằm trong `maCTGioHangs`.
Nếu có dòng được chọn mà không còn trong giỏ (đã xóa ở tab khác...) → báo lỗi "Sản phẩm được chọn không còn trong giỏ hàng. Vui lòng quay lại giỏ hàng và chọn lại".
Khóa từng sách (`findByIdForUpdate`) rồi kiểm tra lại (`validateCanOrder`): còn bán, có giá, đủ tồn kho, không phải sách của chính shop mình. Đơn giá lấy tại thời điểm đặt (`giaBanCu`).
Gom theo cửa hàng và đối chiếu `storeOrders`: mỗi cửa hàng đúng một lựa chọn, không có cửa hàng "lạ", đơn vị vận chuyển phải đang hoạt động.
Tính tiền: tiền hàng và phí ship cho từng cửa hàng, sau đó cộng thành tổng của đơn.
Ghi dữ liệu:
Bảng/Entity	Nội dung
`DonHang` (đơn tổng)	Người mua, tổng tiền hàng, tổng phí ship, giảm giá, tổng thanh toán, COD, trạng thái thanh toán `CHO_THANH_TOAN`, ảnh chụp địa chỉ giao (tên, SĐT, tỉnh/quận/phường, chi tiết)
`DonHangCuaHang` (đơn theo cửa hàng)	Mỗi cửa hàng một bản ghi: đơn vị vận chuyển, tiền hàng, phí ship, ghi chú, trạng thái `CHO_XU_LY`
`ChiTietDonHang`	Mỗi sách một dòng: số lượng, đơn giá, thành tiền
Trừ tồn kho của từng sách theo số lượng đặt.
Xóa khỏi giỏ chỉ các dòng đã đặt (`deleteAll(cartItems)`). Sản phẩm không được tích vẫn nằm trong giỏ.
Trả về `OrderResponse` (đơn tổng kèm danh sách đơn theo cửa hàng).
Cấu trúc đơn hàng
```
DonHang (đơn tổng của khách)
 ├── DonHangCuaHang (cửa hàng A) ── ChiTietDonHang (sách 1), ChiTietDonHang (sách 2)
 └── DonHangCuaHang (cửa hàng B) ── ChiTietDonHang (sách 3)
```
Vòng đời trạng thái đơn cửa hàng (`TrangThaiDonHang`)
`CHO_XU_LY` → `DA_XAC_NHAN` → `DANG_GIAO` → `DA_GIAO` (hoặc `DA_HUY`). Đơn mới tạo luôn ở `CHO_XU_LY`.
---
7. Bước 6 – Sau khi đặt
Giao diện xóa `ob_checkout_items` khỏi `sessionStorage`.
Chuyển tới `/order-success?maDH=<mã đơn>`; trang này gọi `GET /api/order/orders/{maDH}` để hiển thị chi tiết.
Danh sách đơn của khách: `GET /api/order/orders` (trang `/orders`). Khách chỉ xem được đơn của chính mình.
Quay lại `/cart`: chỉ còn những sản phẩm chưa đặt.
---
8. Ví dụ minh họa
Giỏ hàng có 4 dòng:
maCTGioHang	Sách	Cửa hàng	SL	Thành tiền	Ghi chú
15	Sách A	Shop 2	1	50.000 đ	
18	Sách B	Shop 2	2	80.000 đ	
21	Sách C	Shop 5	1	60.000 đ	
24	Sách D	Shop 5	1	40.000 đ	Hết hàng (bị khóa)
Khách tích 15, 18, 21 (dòng 24 không tích được):
Tóm tắt giỏ: 3 sản phẩm, 4 cuốn, tạm tính 190.000 đ.
Checkout hiện 2 cửa hàng (Shop 2 gồm A, B; Shop 5 gồm C), mỗi nơi chọn đơn vị vận chuyển riêng.
Giả sử phí ship Shop 2 là 20.000 đ, Shop 5 là 25.000 đ → tổng thanh toán 235.000 đ.
Hệ thống tạo 1 `DonHang`, 2 `DonHangCuaHang`, 3 `ChiTietDonHang`; trừ tồn kho A, B, C.
Giỏ hàng sau đó chỉ còn dòng 24.
---
9. Các thông báo lỗi thường gặp
Thông báo	Nguyên nhân
Vui lòng chọn ít nhất một sản phẩm để đặt hàng	`maCTGioHangs` rỗng/thiếu
Sản phẩm được chọn không còn trong giỏ hàng...	Dòng đã bị xóa hoặc không thuộc giỏ của bạn
Vui lòng chọn đơn vị vận chuyển cho cửa hàng "..."	Thiếu lựa chọn vận chuyển của một cửa hàng
Cửa hàng #X không có trong giỏ hàng của bạn	`storeOrders` chứa cửa hàng không có trong các sản phẩm đã chọn
Sách "..." không đủ tồn kho. Còn N cuốn	Tồn kho giảm sau khi thêm vào giỏ
Sách "..." hiện không còn được bán	Sách/cửa hàng bị ẩn hoặc ngừng bán
Đơn vị vận chuyển không tồn tại hoặc đã ngừng hoạt động	Đơn vị vận chuyển bị tắt
Bạn không thể mua sách của chính cửa hàng mình	Người mua là chủ cửa hàng
Địa chỉ giao hàng không tồn tại hoặc không thuộc tài khoản của bạn	`maDiaChi` không hợp lệ
---
10. Danh sách file liên quan
File	Vai trò
`templates/cart.html`	Giỏ hàng, ô tích chọn, tóm tắt theo sản phẩm đã chọn, lưu lựa chọn
`templates/checkout.html`	Lọc sản phẩm đã chọn, địa chỉ, vận chuyển theo cửa hàng, gửi yêu cầu tạo đơn
`static/js/cart.js`	Gọi API giỏ hàng, nút thêm vào giỏ, badge số lượng (không đổi)
`dto/order/CreateOrderRequest.java`	Request tạo đơn, có thêm `maCTGioHangs`
`service/order/DonHangService.java`	Tạo đơn: lọc dòng đã chọn, chỉ xóa các dòng đã đặt
`controller/order/DonHangController.java`	Endpoint `/api/order/orders` (không đổi)
`service/order/GioHangService.java`	Nghiệp vụ giỏ hàng (không đổi)
11. Lưu ý khi kiểm thử
Tích một phần sản phẩm rồi đặt: phần còn lại phải còn trong giỏ.
Mở `/checkout` trực tiếp khi chưa chọn gì: phải quay về `/cart`.
Mở giỏ ở hai tab, xóa một dòng ở tab 1 rồi đặt ở tab 2: phải báo lỗi chọn lại.
Chọn sản phẩm của nhiều cửa hàng: kiểm tra có nhiều `DonHangCuaHang` và phí ship cộng đúng.
Giảm tồn kho của một sách xuống dưới số lượng trong giỏ: dòng đó bị khóa và không tích được.