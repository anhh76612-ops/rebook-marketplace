# Đặt hàng

Luồng đặt hàng từ lúc thêm sách vào giỏ, tích chọn sản phẩm, chọn địa chỉ và vận chuyển, đến khi hệ thống tạo đơn. Hủy đơn xem [order-cancel.md](order-cancel.md); thanh toán chuyển khoản xem [payment.md](payment.md).

## 1. Tổng quan

```
Trang sách ──(Thêm vào giỏ)──► /cart ──(tích chọn + Đặt hàng)──► /checkout ──(Xác nhận)──► /order-success
                                  ▲                                  │
                                  └────── nếu chưa chọn gì ◄─────────┘
```

| Bước | Trang | Người dùng làm |
|---|---|---|
| 1 | Chi tiết hoặc danh sách sách | Bấm "Thêm vào giỏ" |
| 2 | `/cart` | Tăng/giảm số lượng, xóa dòng, tích chọn sản phẩm muốn mua |
| 3 | `/checkout` | Chọn địa chỉ, chọn đơn vị vận chuyển và ghi chú cho từng cửa hàng, chọn cách thanh toán |
| 4 | `/order-success` | Xem đơn vừa tạo |
| 5 | `/orders` | Xem lại các đơn đã đặt |

Phải đăng nhập bằng tài khoản Khách hàng (`KHACH_HANG`); tài khoản vai trò khác thấy thông báo "chỉ dành cho khách hàng".

## 2. Thêm sách vào giỏ

Nút có thuộc tính `data-add-to-cart`, `data-ma-sach`, `data-so-luong` (xử lý trong `static/js/cart.js`) và gọi `POST /api/order/cart/items` với body `{ "maSach": 12, "soLuong": 1 }`.

`GioHangService.addItem` kiểm tra:

- Sách đã duyệt, đang bán, cửa hàng đã duyệt.
- Sách có giá và còn hàng.
- Không mua sách của chính cửa hàng mình.
- Tổng số lượng trong giỏ không vượt tồn kho.

Sách đã có trong giỏ thì cộng dồn vào dòng cũ.

## 3. Trang giỏ hàng `/cart`

### 3.1. Dữ liệu

Trang gọi `GET /api/order/cart`, nhận danh sách dòng (`items`). Mỗi dòng có `maCTGioHang`, `tenSach`, `donGia`, `soLuong`, `thanhTien`, `maCH`, `tenCuaHang`, `canhBao`, `coTheMua`.

`coTheMua = false` khi sách ngừng bán, hết hàng, hoặc số lượng trong giỏ vượt tồn kho (kèm lý do trong `canhBao`).

### 3.2. Quy tắc tích chọn

| Tình huống | Hành vi |
|---|---|
| Vào giỏ lần đầu | Mọi sản phẩm mua được đều được tích sẵn |
| Sản phẩm có cảnh báo (`coTheMua = false`) | Ô tích bị khóa, không tính vào tổng tiền |
| Ô "chọn tất cả" | Tích/bỏ tích mọi sản phẩm mua được; hiện trạng thái một phần khi chỉ chọn một số |
| Tăng/giảm số lượng, xóa dòng | Giữ lựa chọn hiện có; dòng đã xóa hoặc không còn mua được tự bị bỏ khỏi lựa chọn |
| Không tích gì | Nút Đặt hàng bị khóa, hiện gợi ý "Hãy tích chọn sản phẩm muốn đặt hàng" |

### 3.3. Tóm tắt

Khối bên phải chỉ tính sản phẩm đã tích: số dòng, số cuốn, tạm tính và tổng cộng.

### 3.4. Bấm "Đặt hàng"

Danh sách `maCTGioHang` đã tích được lưu vào `sessionStorage` với khóa `ob_checkout_items` (ví dụ `[15, 18, 21]`), rồi chuyển sang `/checkout`. Lựa chọn chỉ tồn tại trong tab hiện tại và mất khi đóng tab.

## 4. Trang đặt hàng `/checkout`

### 4.1. Tải dữ liệu

Đọc `ob_checkout_items`; rỗng thì quay về `/cart`. Gọi song song:

- `GET /api/order/cart`: giỏ hàng hiện tại
- `GET /api/profile/addresses`: địa chỉ nhận hàng
- `GET /api/order/shipping-carriers`: đơn vị vận chuyển đang hoạt động

Chỉ giữ dòng mua được và nằm trong danh sách đã chọn; số lượng và tiền được tính lại trên các dòng này. Không còn dòng nào thì hiện "Giỏ hàng đang trống".

### 4.2. Các phần của form

**Địa chỉ nhận hàng.** Danh sách địa chỉ dạng chọn một, địa chỉ mặc định được chọn sẵn. Thêm địa chỉ mới ngay trong hộp thoại (`POST /api/profile/addresses`); lưu xong thì tự chọn địa chỉ đó. Chưa có địa chỉ thì không đặt được.

**Vận chuyển và ghi chú theo từng cửa hàng.** Sản phẩm được gom theo cửa hàng (`maCH`). Mỗi cửa hàng có ô chọn đơn vị vận chuyển (bắt buộc, kèm phí và thời gian giao dự kiến) và ô ghi chú (tối đa 1000 ký tự). Mỗi cửa hàng giao riêng nên phí ship tính riêng.

**Thanh toán.**

- **COD:** thanh toán khi nhận hàng; gửi `POST /api/order/orders` như mục 5.
- **Chuyển khoản VietQR:** chỉ hiện khi sàn đã cấu hình tài khoản ngân hàng. Trang gửi cùng nội dung tới `POST /api/payment/sessions`; đơn chỉ được tạo khi tiền về. Xem [payment.md](payment.md).

Voucher chưa áp dụng.

**Tóm tắt.**

- Tiền hàng = tổng thành tiền các sản phẩm đã chọn
- Phí vận chuyển = tổng phí các đơn vị vận chuyển đã chọn (mỗi cửa hàng một khoản)
- Giảm giá = 0 đ
- Tổng thanh toán = Tiền hàng + Phí vận chuyển − Giảm giá

### 4.3. Kiểm tra trước khi gửi

- Chưa chọn địa chỉ: "Vui lòng chọn địa chỉ giao hàng."
- Còn cửa hàng chưa chọn đơn vị vận chuyển: "Vui lòng chọn đơn vị vận chuyển cho từng cửa hàng." (con trỏ chuyển tới ô thiếu)

## 5. Yêu cầu tạo đơn

`POST /api/order/orders` (cần JWT của khách hàng):

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

| Trường | Ý nghĩa | Ràng buộc |
|---|---|---|
| `maDiaChi` | Địa chỉ giao hàng | Bắt buộc, thuộc tài khoản đang đăng nhập |
| `phuongThucTT` | Phương thức thanh toán | Bắt buộc. API này chỉ nhận `COD`; đơn chuyển khoản đi qua phiên thanh toán |
| `maCTGioHangs` | Các dòng giỏ hàng đã tích | Bắt buộc, ít nhất 1 phần tử |
| `storeOrders` | Vận chuyển và ghi chú theo cửa hàng | Bắt buộc, mỗi cửa hàng của sản phẩm đã chọn có đúng 1 mục |

## 6. Backend xử lý (`DonHangService.createOrder`)

Toàn bộ chạy trong một transaction; lỗi ở bất kỳ bước nào thì không có gì được ghi.

1. Kiểm tra phương thức thanh toán (chỉ `COD`).
2. Kiểm tra địa chỉ thuộc người dùng này.
3. Khóa giỏ hàng (`findByIdForUpdate`) để hai yêu cầu đặt hàng cùng lúc không dùng chung một giỏ.
4. Chỉ giữ dòng có `maCTGioHang` nằm trong `maCTGioHangs`. Có dòng được chọn mà không còn trong giỏ (đã xóa ở tab khác) thì báo lỗi "Sản phẩm được chọn không còn trong giỏ hàng. Vui lòng quay lại giỏ hàng và chọn lại".
5. Khóa từng sách (`findByIdForUpdate`) rồi kiểm tra lại (`validateCanOrder`): còn bán, có giá, đủ tồn kho, không phải sách của chính mình. Đơn giá lấy tại thời điểm đặt (`giaBanCu`).
6. Gom theo cửa hàng và đối chiếu `storeOrders`: mỗi cửa hàng đúng một lựa chọn, không có cửa hàng lạ, đơn vị vận chuyển đang hoạt động.
7. Tính tiền hàng và phí ship từng cửa hàng, cộng thành tổng của đơn.
8. Ghi dữ liệu:

   | Bảng | Nội dung |
   |---|---|
   | `DonHang` (đơn tổng) | Người mua, tổng tiền hàng, tổng phí ship, giảm giá, tổng thanh toán, phương thức, trạng thái thanh toán `CHO_THANH_TOAN`, bản chụp địa chỉ giao (tên, SĐT, tỉnh/quận/phường, chi tiết) |
   | `DonHangCuaHang` (đơn theo cửa hàng) | Mỗi cửa hàng một dòng: đơn vị vận chuyển, tiền hàng, phí ship, ghi chú, trạng thái `CHO_XU_LY` |
   | `ChiTietDonHang` | Mỗi sách một dòng: số lượng, đơn giá, thành tiền |

9. Trừ tồn kho từng sách.
10. Chỉ xóa khỏi giỏ các dòng đã đặt; sản phẩm không tích vẫn nằm trong giỏ.
11. Trả về `OrderResponse` (đơn tổng kèm danh sách đơn theo cửa hàng).

Đơn chuyển khoản dùng chung các bước kiểm tra này (`DonHangService.createPaidOrder`), tạo đơn ở trạng thái thanh toán `DA_THANH_TOAN`.

### Cấu trúc đơn hàng

```
DonHang (đơn tổng của khách)
 ├── DonHangCuaHang (cửa hàng A) ── ChiTietDonHang (sách 1), ChiTietDonHang (sách 2)
 └── DonHangCuaHang (cửa hàng B) ── ChiTietDonHang (sách 3)
```

### Vòng đời đơn cửa hàng (`TrangThaiDonHang`)

```
CHO_XU_LY → DA_XAC_NHAN → DANG_LAY_HANG → DA_LAY_HANG → DANG_GIAO → DA_GIAO
                                                                  ↘ DA_HOAN_TRA
CHO_XU_LY / DA_XAC_NHAN → DA_HUY
```

Đơn mới luôn ở `CHO_XU_LY`. Từ `DA_XAC_NHAN` trở đi, trạng thái do đơn vị vận chuyển cập nhật (xem [shipping.md](shipping.md)).

## 7. Sau khi đặt

- Giao diện xóa `ob_checkout_items` khỏi `sessionStorage`.
- Chuyển tới `/order-success?maDH=<mã đơn>`; trang này gọi `GET /api/order/orders/{maDH}` để hiện chi tiết.
- Danh sách đơn của khách: `GET /api/order/orders` (trang `/orders`); khách chỉ xem được đơn của mình.
- Quay lại `/cart` chỉ còn những sản phẩm chưa đặt.

## 8. Ví dụ

Giỏ hàng có 4 dòng:

| `maCTGioHang` | Sách | Cửa hàng | SL | Thành tiền | Ghi chú |
|---|---|---|---|---|---|
| 15 | Sách A | Shop 2 | 1 | 50.000 đ | |
| 18 | Sách B | Shop 2 | 2 | 80.000 đ | |
| 21 | Sách C | Shop 5 | 1 | 60.000 đ | |
| 24 | Sách D | Shop 5 | 1 | 40.000 đ | Hết hàng (bị khóa) |

Khách tích 15, 18, 21 (dòng 24 không tích được):

- Tóm tắt giỏ: 3 sản phẩm, 4 cuốn, tạm tính 190.000 đ.
- Checkout hiện 2 cửa hàng (Shop 2 gồm A, B; Shop 5 gồm C), mỗi nơi chọn đơn vị vận chuyển riêng.
- Phí ship Shop 2 là 20.000 đ, Shop 5 là 25.000 đ thì tổng thanh toán là 235.000 đ.
- Hệ thống tạo 1 `DonHang`, 2 `DonHangCuaHang`, 3 `ChiTietDonHang`; trừ tồn kho A, B, C.
- Giỏ hàng sau đó chỉ còn dòng 24.

## 9. Thông báo lỗi thường gặp

| Thông báo | Nguyên nhân |
|---|---|
| Vui lòng chọn ít nhất một sản phẩm để đặt hàng | `maCTGioHangs` rỗng hoặc thiếu |
| Sản phẩm được chọn không còn trong giỏ hàng... | Dòng đã bị xóa hoặc không thuộc giỏ của bạn |
| Vui lòng chọn đơn vị vận chuyển cho cửa hàng "..." | Thiếu lựa chọn vận chuyển của một cửa hàng |
| Cửa hàng #X không có trong giỏ hàng của bạn | `storeOrders` có cửa hàng không nằm trong sản phẩm đã chọn |
| Sách "..." không đủ tồn kho. Còn N cuốn | Tồn kho giảm sau khi thêm vào giỏ |
| Sách "..." hiện không còn được bán | Sách hoặc cửa hàng bị ẩn, ngừng bán |
| Đơn vị vận chuyển không tồn tại hoặc đã ngừng hoạt động | Đơn vị vận chuyển bị tắt |
| Bạn không thể mua sách của chính cửa hàng mình | Người mua là chủ cửa hàng của sách |
| Địa chỉ giao hàng không tồn tại hoặc không thuộc tài khoản của bạn | `maDiaChi` không hợp lệ |

## 10. File liên quan

| File | Vai trò |
|---|---|
| `templates/cart.html` | Giỏ hàng, ô tích chọn, tóm tắt theo sản phẩm đã chọn, lưu lựa chọn |
| `templates/checkout.html` | Lọc sản phẩm đã chọn, địa chỉ, vận chuyển theo cửa hàng, chọn thanh toán, gửi yêu cầu |
| `static/js/cart.js` | API giỏ hàng, nút thêm vào giỏ, số trên biểu tượng giỏ |
| `dto/order/CreateOrderRequest.java` | Yêu cầu tạo đơn |
| `service/order/DonHangService.java` | Tạo đơn: lọc dòng đã chọn, chỉ xóa dòng đã đặt |
| `controller/order/DonHangController.java` | Endpoint `/api/order/orders` |
| `service/order/GioHangService.java` | Nghiệp vụ giỏ hàng |

## 11. Lưu ý khi kiểm thử

- Tích một phần sản phẩm rồi đặt: phần còn lại phải còn trong giỏ.
- Mở `/checkout` trực tiếp khi chưa chọn gì: phải quay về `/cart`.
- Mở giỏ ở hai tab, xóa một dòng ở tab 1 rồi đặt ở tab 2: phải báo lỗi chọn lại.
- Chọn sản phẩm của nhiều cửa hàng: phải có nhiều `DonHangCuaHang` và phí ship cộng đúng.
- Giảm tồn kho một sách xuống dưới số lượng trong giỏ: dòng đó bị khóa và không tích được.
