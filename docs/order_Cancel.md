# Đặt hàng và hủy đơn hàng – OldBook (rebook-marketplace)

Tài liệu mô tả quy trình từ lúc khách bấm đặt hàng đến khi đơn bị hủy: ai được hủy, hủy khi nào, hệ thống xử lý tồn kho và thanh toán ra sao.
Phần giỏ hàng/checkout phía giao diện xem thêm `docs/order.md`; phần vận chuyển xem `docs/shipping.md`.

---

## 1. Tổng quan

- Thanh toán hiện **chỉ có COD**. Không có hoàn tiền (`tienHoanTra` luôn bằng 0).
- Một lần đặt hàng tạo **1 `DonHang` (đơn tổng)** và **N `DonHangCuaHang` (đơn con, mỗi cửa hàng một đơn)**.
- **Trạng thái, giao hàng và hủy đều xử lý ở đơn con (`maDHCH`)**. Hủy một đơn con không ảnh hưởng đơn con khác của cùng đơn tổng.
- Tồn kho **trừ ngay khi đặt**, **cộng lại khi hủy hoặc hoàn trả**.
- Lý do hủy **bắt buộc 50–500 ký tự**.
- Khi đơn vị vận chuyển đã tiếp nhận đơn (từ `DANG_LAY_HANG` trở đi) thì không còn hủy được.

```
DonHang (đơn tổng của khách)
 ├── DonHangCuaHang (cửa hàng A) ── ChiTietDonHang ...   ── VanChuyen (sau khi shop xác nhận)
 └── DonHangCuaHang (cửa hàng B) ── ChiTietDonHang ...
```

### Trạng thái đơn con (`TrangThaiDonHang`)

| Trạng thái | Ý nghĩa |
|---|---|
| `CHO_XU_LY` | Mới đặt, chờ cửa hàng xác nhận |
| `DA_XAC_NHAN` | Cửa hàng đã xác nhận, đã có vận đơn `CHO_LAY_HANG` |
| `DANG_LAY_HANG` | Đơn vị vận chuyển đã tiếp nhận, đi lấy hàng |
| `DA_LAY_HANG` | Đã lấy hàng |
| `DANG_GIAO` | Đang giao (kể cả khi một lần giao thất bại) |
| `DA_GIAO` | Giao thành công (kết thúc) |
| `DA_HOAN_TRA` | Hoàn hàng về cửa hàng (kết thúc) |
| `DA_HUY` | Đã hủy (kết thúc) |

### Trạng thái thanh toán (`TrangThaiThanhToan`)

Dùng cho đơn con (`trangThaiThanhToanShop`) và đơn tổng (`trangThaiThanhToan`): `CHO_THANH_TOAN`, `DA_THANH_TOAN`, `THANH_TOAN_MOT_PHAN`, `KHONG_THANH_TOAN`.

---

## 2. Quy trình đặt hàng

### 2.1. Luồng tổng quát

```
/cart ──(tích chọn + Đặt hàng)──► /checkout ──POST /api/order/orders──► /order-success
                                                                         │
                                                              /orders (xem và hủy đơn)
```

### 2.2. API khách hàng (`DonHangController`, chỉ vai trò `KHACH_HANG`)

| Method | URL | Việc làm |
|---|---|---|
| POST | `/api/order/orders` | Đặt hàng |
| GET | `/api/order/orders` | Danh sách đơn của mình |
| GET | `/api/order/orders/{maDH}` | Chi tiết đơn (chỉ xem được đơn của chính mình) |
| POST | `/api/order/orders/store-orders/{maDHCH}/cancel` | Hủy đơn con (xem mục 3) |

### 2.3. Request đặt hàng (`CreateOrderRequest`)

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
| `maDiaChi` | Địa chỉ giao hàng | Bắt buộc, phải thuộc tài khoản đang đăng nhập |
| `phuongThucTT` | Phương thức thanh toán | Bắt buộc, hiện chỉ `COD` |
| `maCTGioHangs` | Các dòng giỏ hàng đã tích chọn | Bắt buộc, ít nhất 1 phần tử |
| `storeOrders` | Đơn vị vận chuyển + ghi chú từng cửa hàng | Bắt buộc, mỗi cửa hàng của sản phẩm đã chọn có đúng 1 mục; `ghiChu` tối đa 1000 ký tự |

### 2.4. Backend xử lý (`DonHangService.createOrder`)

Toàn bộ chạy trong một transaction: lỗi ở bất kỳ bước nào thì không có gì được ghi (kể cả tồn kho).

1. Kiểm tra thanh toán: chỉ nhận COD.
2. Kiểm tra người dùng và địa chỉ (địa chỉ phải thuộc người dùng này).
3. Khóa giỏ hàng (`findByIdForUpdate`) để hai request đặt hàng đồng thời không dùng chung một giỏ.
4. Lọc các dòng giỏ theo `maCTGioHangs`. Nếu số dòng khớp khác số id gửi lên (có dòng đã bị xóa...) thì báo lỗi.
5. Khóa từng sách (`findByIdForUpdate`) rồi kiểm tra lại (`validateCanOrder`):
   - số lượng > 0;
   - sách `DA_DUYET` và `DANG_BAN`, cửa hàng `DA_DUYET`;
   - sách có giá > 0 (`giaBanCu`);
   - đủ tồn kho;
   - không phải sách của chính cửa hàng mình.
   Đơn giá lấy tại thời điểm đặt.
6. Gom theo cửa hàng và đối chiếu `storeOrders`: mỗi cửa hàng đúng một lựa chọn, không có cửa hàng lạ, đơn vị vận chuyển phải `HOAT_DONG`.
7. Tính tiền: phí ship lấy theo đơn vị vận chuyển **của từng cửa hàng**.
8. Ghi dữ liệu:

| Bảng/Entity | Nội dung lúc tạo |
|---|---|
| `DonHang` | Tổng tiền hàng, tổng phí ship, giảm giá 0, `tongThanhToan = tiền hàng + phí ship`, COD, `CHO_THANH_TOAN`, ảnh chụp địa chỉ giao |
| `DonHangCuaHang` | Mỗi cửa hàng một bản ghi: đơn vị vận chuyển, `tienHangCH`, `phiShipCH`, `tongTienShopOrder`, ghi chú, trạng thái `CHO_XU_LY`, `CHO_THANH_TOAN` |
| `ChiTietDonHang` | Mỗi sách một dòng: số lượng, đơn giá, thành tiền |

9. **Trừ tồn kho** từng sách theo số lượng đặt.
10. Chỉ xóa khỏi giỏ các dòng đã đặt; sản phẩm không tích vẫn còn trong giỏ.
11. Trả `OrderResponse` (đơn tổng kèm `storeOrders[]`).

> Voucher/khuyến mãi chưa áp dụng: mọi khoản giảm giá đều bằng 0.

---

## 3. Quy trình hủy đơn

### 3.1. Quy tắc chung

- Lý do hủy (`CancelOrderRequest.lyDo`) dài **50–500 ký tự** sau khi trim. Ngắn hơn → "Lý do phải có ít nhất 50 ký tự".
- Mọi lần hủy thật sự đều đi qua `OrderSettlementService.settleCancelled(order, huyBoi, lyDo)`:
  1. Cộng lại `soLuongTon` cho từng dòng sách (khóa sách trước khi cộng).
  2. Đặt `trangThaiDonHang = DA_HUY`, ghi `ngayHuy`, `huyBoi` (`KHACH_HANG` hoặc `CHU_CUA_HANG`), `lyDoHuy`.
  3. Đặt `commissionTien`, `doanhThuShop`, `tienHoanTra` = 0 và `trangThaiThanhToanShop = KHONG_THANH_TOAN`.
  4. Tính lại `trangThaiThanhToan` của đơn tổng từ các đơn con.
- Hàm này **chạy lặp không gây hại**: đơn đã `DA_HUY` thì bỏ qua, không cộng tồn kho hai lần.
- Đơn của người khác hoặc shop khác luôn báo "Đơn hàng không tồn tại" để không lộ dữ liệu.

### 3.2. Ai được hủy khi nào

| Trạng thái đơn con | Khách hủy | Chủ cửa hàng hủy |
|---|---|---|
| `CHO_XU_LY` | **Hủy ngay** | **Hủy ngay** |
| `DA_XAC_NHAN` (vận đơn còn `CHO_LAY_HANG`) | **Gửi yêu cầu hủy**, chờ cửa hàng duyệt | **Hủy ngay**, vận đơn chuyển `DA_HUY` |
| `DANG_LAY_HANG`, `DA_LAY_HANG`, `DANG_GIAO`, `DA_GIAO`, `DA_HOAN_TRA`, `DA_HUY` | Không hủy được | Không hủy được |

Thông báo khi không hủy được: khách thấy "Đơn hàng đã được đơn vị vận chuyển tiếp nhận, không thể hủy"; cửa hàng thấy "Đơn vị vận chuyển đã tiếp nhận đơn, không thể hủy".

### 3.3. Khách hủy (`DonHangService.cancelStoreOrder`)

`POST /api/order/orders/store-orders/{maDHCH}/cancel` với body `{ "lyDo": "..." }`.

1. Kiểm tra lý do; khóa đơn con và kiểm tra đơn thuộc khách này.
2. Rẽ nhánh theo trạng thái:

| Trạng thái | Kết quả |
|---|---|
| `CHO_XU_LY` | Hủy ngay (`huyBoi = KHACH_HANG`) |
| `DA_XAC_NHAN` | Tạo `YeuCauHuyDon` trạng thái `CHO_DUYET`. **Đơn vẫn `DA_XAC_NHAN`.** Nếu đơn đã có yêu cầu hủy (bất kỳ trạng thái nào) → "Bạn đã gửi yêu cầu hủy cho đơn hàng này" |
| Khác | Báo không thể hủy |

3. Trả `OrderResponse`. Giao diện đọc `storeOrders[].yeuCauHuy` (`maYeuCau`, `lyDo`, `lyDoPhanHoi`, `trangThaiYeuCau`, `ngayGui`, `ngayXuLy`) để hiển thị trạng thái yêu cầu.

### 3.4. Cửa hàng hủy chủ động (`StoreOrderService.cancelOrder`)

`POST /api/store/orders/{maDHCH}/cancel` với body `{ "lyDo": "..." }`.

| Trạng thái | Xử lý |
|---|---|
| `CHO_XU_LY` | `settleCancelled` với `huyBoi = CHU_CUA_HANG` |
| `DA_XAC_NHAN` và (chưa có vận đơn hoặc vận đơn `CHO_LAY_HANG`) | `settleCancelled`; vận đơn chuyển `DA_HUY` kèm dòng lịch sử "Đơn bị cửa hàng hủy"; nếu đang có yêu cầu hủy `CHO_DUYET` thì tự chuyển `DA_DUYET` |
| Khác | Lỗi "Đơn vị vận chuyển đã tiếp nhận đơn, không thể hủy" |

### 3.5. Cửa hàng xử lý yêu cầu hủy của khách

**Đồng ý:** `POST /api/store/orders/{maDHCH}/cancel-request/approve` (không cần body)

- Phải có yêu cầu `CHO_DUYET`, nếu không → "Đơn hàng không có yêu cầu hủy đang chờ xử lý".
- Chỉ hủy được khi đơn `DA_XAC_NHAN` hoặc `DANG_LAY_HANG` **và** vận đơn chưa lấy hàng (không có / `CHO_LAY_HANG` / `DANG_LAY_HANG`). Ngược lại → "Đơn vị vận chuyển đã lấy hàng, không thể hủy. Vui lòng từ chối yêu cầu hủy".
- Kết quả: `settleCancelled` với `huyBoi = KHACH_HANG` và lý do của khách, vận đơn chuyển `DA_HUY` ("Đơn bị hủy theo yêu cầu của khách"), yêu cầu chuyển `DA_DUYET`.

**Từ chối:** `POST /api/store/orders/{maDHCH}/cancel-request/reject` với body `{ "lyDo": "..." }` (50–500 ký tự)

- Yêu cầu chuyển `TU_CHOI`, lưu `lyDoPhanHoi` và `ngayXuLy`.
- Đơn giữ nguyên `DA_XAC_NHAN` và tiếp tục luồng giao hàng.

### 3.6. Yêu cầu hủy và đơn vị vận chuyển

- Khi đơn có yêu cầu hủy `CHO_DUYET`, cổng đối tác **không cho tiếp nhận** ("Khách hàng đang yêu cầu hủy đơn này, vui lòng chờ cửa hàng xử lý") và **ẩn đơn đó** khỏi danh sách chờ tiếp nhận (`/requests/pending`).
- Đồng bộ API/polling của hãng (`applyCarrierUpdate`) không kiểm tra yêu cầu hủy. Vì vậy đơn vẫn có thể sang `DANG_LAY_HANG` khi còn yêu cầu chờ duyệt; lúc đó nhánh kiểm tra "đã lấy hàng" của mục 3.5 mới có tác dụng.

### 3.7. Sơ đồ

```
                       khách / shop hủy ngay
         ┌─────────────────────────────────────────────┐
         │                                             ▼
    CHO_XU_LY ──shop xác nhận──► DA_XAC_NHAN ──shop hủy / shop đồng ý yêu cầu──► DA_HUY
                                   │    ▲
            khách gửi yêu cầu hủy  │    │ shop từ chối (đơn không đổi)
                                   ▼    │
                        YeuCauHuyDon: CHO_DUYET → DA_DUYET | TU_CHOI
                                   │
                     đơn vị vận chuyển tiếp nhận (hết đường hủy)
                                   ▼
          DANG_LAY_HANG → DA_LAY_HANG → DANG_GIAO ──► DA_GIAO
                                           │  ▲
                                    GIAO_THAT_BAI ─(quá số lần)─► DA_HOAN_TRA
```

---

## 4. Ảnh hưởng đến tồn kho và thanh toán

| Sự kiện | Tồn kho | Thanh toán đơn con | Ghi nhận khác |
|---|---|---|---|
| Đặt hàng | Trừ | `CHO_THANH_TOAN` | |
| Hủy (`settleCancelled`) | Cộng lại | `KHONG_THANH_TOAN` | `ngayHuy`, `huyBoi`, `lyDoHuy` |
| Hoàn trả (`settleReturned`) | Cộng lại | `KHONG_THANH_TOAN` | `ngayHoanTra` |
| Giao thành công (`settleDelivered`) | Không đổi | `DA_THANH_TOAN` | Hoa hồng, doanh thu shop, 3 dòng `ThanhToan` (`THU_HO_COD`, `HOA_HONG`, `DOANH_THU_SHOP`) |

Trạng thái thanh toán của đơn tổng được tính lại từ các đơn con sau mỗi lần chốt:

| Điều kiện | Đơn tổng |
|---|---|
| Tất cả đơn con `DA_GIAO` | `DA_THANH_TOAN` |
| Có đơn con `DA_GIAO` nhưng chưa phải tất cả | `THANH_TOAN_MOT_PHAN` |
| Không có đơn nào giao, tất cả đã kết thúc | `KHONG_THANH_TOAN` |
| Còn lại | `CHO_THANH_TOAN` |

Hoa hồng = thành tiền × tỷ lệ chiết khấu của danh mục (`CauHinhChietKhau`), mặc định `marketplace.commission.default-rate = 5.00` (%). Doanh thu shop = tiền hàng − giảm giá shop − hoa hồng (không âm).

---

## 5. Các thông báo lỗi thường gặp

| Thông báo | Nguyên nhân |
|---|---|
| Phương thức thanh toán hiện hỗ trợ: COD | `phuongThucTT` khác COD |
| Sản phẩm được chọn không còn trong giỏ hàng... | Dòng giỏ đã bị xóa hoặc không thuộc giỏ của bạn |
| Vui lòng chọn đơn vị vận chuyển cho cửa hàng "..." | Thiếu lựa chọn vận chuyển của một cửa hàng |
| Mỗi cửa hàng chỉ được chọn một đơn vị vận chuyển | `storeOrders` trùng `maCH` |
| Sách "..." không đủ tồn kho. Còn N cuốn | Tồn kho giảm sau khi thêm vào giỏ |
| Sách "..." hiện không còn được bán | Sách/cửa hàng bị ẩn hoặc ngừng bán |
| Bạn không thể mua sách của chính cửa hàng mình | Người mua là chủ cửa hàng |
| Lý do phải có ít nhất 50 ký tự / từ 50 đến 500 ký tự | Lý do hủy không đủ độ dài |
| Bạn đã gửi yêu cầu hủy cho đơn hàng này | Đơn đã có yêu cầu hủy (kể cả đã bị từ chối) |
| Đơn hàng đã được đơn vị vận chuyển tiếp nhận, không thể hủy | Khách hủy khi đơn đã qua `DA_XAC_NHAN` |
| Đơn vị vận chuyển đã tiếp nhận đơn, không thể hủy | Cửa hàng hủy khi vận đơn không còn `CHO_LAY_HANG` |
| Đơn vị vận chuyển đã lấy hàng, không thể hủy. Vui lòng từ chối yêu cầu hủy | Cửa hàng đồng ý yêu cầu nhưng hàng đã được lấy |
| Đơn hàng không có yêu cầu hủy đang chờ xử lý | Duyệt/từ chối khi không có yêu cầu `CHO_DUYET` |
| Đơn hàng không tồn tại | Mã đơn sai hoặc đơn thuộc người/shop khác |

---

## 6. Danh sách file liên quan

| File | Vai trò |
|---|---|
| `service/order/DonHangService.java` | Đặt hàng (`createOrder`), khách hủy (`cancelStoreOrder`) |
| `service/order/StoreOrderService.java` | Shop xác nhận, hủy, đồng ý/từ chối yêu cầu hủy |
| `service/order/OrderSettlementService.java` | `settleCancelled`, `settleReturned`, `settleDelivered`, tính trạng thái thanh toán đơn tổng |
| `service/shipping/ShipmentSyncService.java` | Điểm duy nhất áp trạng thái giao hàng, gọi chốt tiền/hoàn kho |
| `service/shipping/VanChuyenService.java` | Cổng đối tác; chặn tiếp nhận khi có yêu cầu hủy |
| `controller/order/DonHangController.java` | API khách `/api/order/orders/**` |
| `controller/order/StoreOrderController.java` | API cửa hàng `/api/store/orders/**` |
| `entity/order/YeuCauHuyDon.java` | Yêu cầu hủy của khách |
| `constant/order/*.java`, `constant/shipping/TrangThaiVanChuyen.java` | Các enum trạng thái |
| `dto/order/CancelOrderRequest.java`, `CancelRequestResponse.java`, `CreateOrderRequest.java` | Request/response |
| `templates/orders.html`, `templates/store-orders.html` | Giao diện khách và cửa hàng (hủy, duyệt yêu cầu) |

---

## 7. Lưu ý khi kiểm thử

- Đặt nhiều cửa hàng một lần: có nhiều `DonHangCuaHang`, phí ship cộng đúng; hủy một đơn con thì đơn con kia không đổi.
- Hủy đơn `CHO_XU_LY` (cả khách và shop): đơn thành `DA_HUY`, tồn kho được cộng lại đúng số lượng.
- Khách hủy đơn `DA_XAC_NHAN`: chỉ tạo yêu cầu `CHO_DUYET`, đơn chưa hủy; gửi lần hai phải báo lỗi.
- Shop đồng ý yêu cầu: đơn `DA_HUY` với `huyBoi = KHACH_HANG`, vận đơn `DA_HUY`, tồn kho được cộng lại.
- Shop từ chối yêu cầu: đơn vẫn `DA_XAC_NHAN`, khách không gửi lại được yêu cầu mới.
- Có yêu cầu `CHO_DUYET`: hãng không tiếp nhận được và đơn không hiện trong danh sách chờ.
- Hủy đơn đã `DANG_LAY_HANG` trở đi: cả khách và shop đều bị từ chối.
- Bấm hủy hai lần liên tiếp (hoặc hai tab): tồn kho chỉ cộng lại một lần.
- Lý do dưới 50 ký tự: bị từ chối ở cả giao diện và backend.