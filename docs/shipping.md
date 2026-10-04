# Vận chuyển: luồng đơn hàng và cổng đối tác

## Một luồng trạng thái duy nhất

Sau khi cửa hàng xác nhận (`POST /api/store/orders/{maDHCH}/confirm`), đơn được bàn giao cho
đơn vị vận chuyển: tạo `van_chuyen` (CHO_LAY_HANG) + một dòng `lich_su_van_chuyen`.

```
CHO_LAY_HANG → DANG_LAY_HANG → DA_LAY_HANG → DANG_GIAO → DA_GIAO
                                               ↘ GIAO_THAT_BAI → DANG_GIAO | DA_HOAN_TRA
                                               ↘ DA_HOAN_TRA
```
Mỗi trạng thái vận chuyển kéo theo trạng thái đơn hàng con (`TrangThaiVanChuyen.toTrangThaiDonHang`).
Cửa hàng **không** tự đổi trạng thái giao hàng.

Mọi thay đổi đi qua `ShipmentSyncService`:

| Nguồn | Hàm | `lich_su_van_chuyen.nguon` |
|---|---|---|
| Tra cứu API của hãng (định kỳ / nút đồng bộ của shop) | `applyCarrierUpdate` | `POLLING` |
| Hãng thao tác trên cổng `/shipping/{maCode}` | `applyPartnerUpdate` | `DOI_TAC` |
| Hệ thống tự chuyển (xác nhận, hết lượt giao → hoàn hàng) | — | `HE_THONG` |

Cả hai dùng chung: kiểm tra bước chuyển, đếm số lần giao thất bại, tự hoàn hàng khi đạt
`shipping.max-delivery-attempts`, chốt doanh thu COD (`DA_GIAO`) hoặc hoàn kho (`DA_HOAN_TRA`).

## Chọn nguồn cập nhật: `shipping.sync.provider`

| Giá trị | Mã vận đơn | Trạng thái đổi khi |
|---|---|---|
| `portal` (mặc định dự án) | hệ thống sinh | hãng cập nhật trên cổng đối tác |
| `simulator` | hệ thống sinh | mỗi chu kỳ đồng bộ tự tiến 1 bước (demo) |
| `rest` | API hãng cấp | tra cứu API hãng theo `shipping.carriers.<MA>.*` |

Không dùng `simulator` cùng cổng đối tác: giả lập sẽ tự đẩy trạng thái, tranh với thao tác của hãng.

## API cổng đối tác (không cần đăng nhập, giới hạn theo `{maCode}`)

| Method | URL | Ghi chú |
|---|---|---|
| GET | `/api/shipping/{maCode}/requests/pending` | Đơn CHO_LAY_HANG chờ tiếp nhận |
| POST | `/api/shipping/{maCode}/requests` | `{maDHCH, ghiChuGiaoHang?}` → DANG_LAY_HANG |
| GET | `/api/shipping/{maCode}/requests` | Kèm `trangThaiTiepTheo` (bước được phép) |
| GET | `/api/shipping/{maCode}/requests/{id}` | |
| PATCH | `/api/shipping/{maCode}/requests/{id}/status` | `{trangThai, ghiChu?}` |
| PATCH | `/api/shipping/{maCode}/requests/{id}/result` | Như trên, chỉ DA_GIAO / GIAO_THAT_BAI / DA_HOAN_TRA |

Mã vận đơn cố định từ lúc cửa hàng xác nhận (dùng để tra cứu và đối soát COD), cổng không đổi được.

## Dữ liệu cũ

Bản trước dùng `HOAN_TRA` cho cả vận đơn và đơn con. Nếu DB còn dữ liệu đó:
```sql
UPDATE van_chuyen        SET trang_thai_van_chuyen = 'DA_HOAN_TRA' WHERE trang_thai_van_chuyen = 'HOAN_TRA';
UPDATE don_hang_cua_hang SET trang_thai_don_hang   = 'DA_HOAN_TRA' WHERE trang_thai_don_hang   = 'HOAN_TRA';
```
