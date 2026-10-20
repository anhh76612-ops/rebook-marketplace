# Thanh toán chuyển khoản bằng VietQR

Hướng dẫn bật và thử chức năng thanh toán chuyển khoản trên máy local, và nối với Casso khi web đã deploy.

## Chức năng làm gì

Khách chọn "Chuyển khoản bằng mã VietQR" ở trang thanh toán. Hệ thống giữ sách 5 phút và hiện mã QR tới tài khoản ngân hàng của sàn. Khi ngân hàng báo tiền về, đơn hàng mới được tạo. Chưa trả tiền thì chưa có đơn và sách vẫn nằm trong giỏ.

| Dòng tiền | Khi nào | Ai thực hiện |
|---|---|---|
| Khách trả sàn | Lúc đặt hàng | Tự động: Casso gọi webhook |
| Sàn hoàn khách | Đơn đã trả bị hủy, giao không thành công, hoặc tiền về mà không tạo được đơn | Quản lý chuyển khoản tay rồi bấm "Xác nhận đã chuyển" |
| Sàn chi cửa hàng | Đơn đã trả được giao thành công (tiền hàng trừ hoa hồng) | Quản lý gộp các đơn của một cửa hàng thành một đợt chi, chuyển khoản tay rồi bấm "Xác nhận đã chuyển" |

## 1. Cấu hình

Thêm vào file `.env` ở thư mục gốc (mẫu đầy đủ ở `.env.example`):

```properties
payment.bank.bin=970422
payment.bank.account-no=<số tài khoản của sàn>
payment.bank.account-name=<tên chủ tài khoản>
payment.casso.checksum-key=<một chuỗi ngẫu nhiên>
payment.session-minutes=5
```

| Dòng | Ý nghĩa |
|---|---|
| `payment.bank.bin` | Mã BIN 6 số của ngân hàng. Danh sách có trong `src/main/resources/static/js/banks.js` |
| `payment.bank.account-no`, `payment.bank.account-name` | Tài khoản nhận tiền của sàn, hiện trên mã QR |
| `payment.casso.checksum-key` | Khóa kiểm chữ ký webhook. Máy local dùng chuỗi ngẫu nhiên (`openssl rand -hex 24`); nối Casso thật thì dùng "Key bảo mật" của Casso |
| `payment.session-minutes` | Thời gian giữ sách và thời gian sống của mã QR |

Lưu ý:

- **Bỏ trống ba dòng `payment.bank.*` thì trang thanh toán chỉ còn COD.** Máy ai chưa cấu hình vẫn chạy bình thường.
- **Trong `.env` phải viết tên có dấu chấm** như trên. Trên máy chủ (AWS) thì đặt biến môi trường `PAYMENT_BANK_BIN`, `PAYMENT_BANK_ACCOUNT_NO`, `PAYMENT_BANK_ACCOUNT_NAME`, `PAYMENT_CASSO_CHECKSUM_KEY`.
- **Sửa `.env` xong phải khởi động lại ứng dụng.**
- **Đừng quét mã chuyển tiền thật khi còn dùng số tài khoản thử:** tiền sẽ đi vào tài khoản của người lạ.

Lần chạy đầu sau khi có code này, ứng dụng tự tạo ba bảng `phien_thanh_toan`, `giao_dich_ngan_hang`, `yeu_cau_chuyen_tien` và ba cột tài khoản ngân hàng trên `cua_hang`.

## 2. Chạy ứng dụng

Có hai cách. Cả hai đều đọc phần `payment.*` trong `.env` khi chạy từ thư mục gốc.

**Với database SQL Server của bạn** (cổng 8080):

```bash
./mvnw spring-boot:run
```

**Với dữ liệu mẫu, không cần SQL Server** (cổng 18090, dữ liệu mất khi tắt):

```bash
./mvnw -q spring-boot:test-run "-Dspring-boot.run.profiles=test,catalog-test,demo" "-Dspring-boot.run.arguments=--server.port=18090"
```

Cách thứ hai có sẵn tài khoản khách, người bán (có cửa hàng và sách), quản lý và quản trị. Email và mật khẩu chung ghi ở đầu file `src/test/java/com/oldbook/DemoDataSeeder.java`.

## 3. Thử trên giao diện

Cần ba vai trò: một **khách hàng** có địa chỉ giao hàng, một **cửa hàng đã duyệt** có sách đang bán, và một **quản lý**.

### 3.1. Khách trả tiền

1. Đăng nhập tài khoản khách, thêm một cuốn sách vào giỏ, mở trang thanh toán.
2. Chọn **Chuyển khoản bằng mã VietQR** rồi bấm **Đặt hàng**.
3. Trang quét mã hiện mã QR, số tiền, nội dung chuyển khoản dạng `OBxxxxxxxx` và đồng hồ đếm ngược. Lúc này chưa có đơn hàng; sách vẫn trong giỏ nhưng đã được giữ.
4. Giả lập ngân hàng báo tiền về (mở một cửa sổ lệnh khác ở thư mục gốc):

   ```bash
   node docs/payment/gia-lap-casso.cjs OBxxxxxxxx 275000
   ```

   Thay mã và số tiền bằng đúng giá trị trên trang. Chạy bằng dữ liệu mẫu thì thêm cổng ở cuối: `node docs/payment/gia-lap-casso.cjs OBxxxxxxxx 275000 18090`.
5. Trong vài giây trang tự chuyển sang **Đặt hàng thành công**. Đơn có phương thức "Chuyển khoản", trạng thái "Đã thanh toán".

### 3.2. Các tình huống khác ở bước trả tiền

| Thử | Cách làm | Kết quả |
|---|---|---|
| Hết hạn | Để trang quét mã quá 5 phút | Trang báo mã hết hạn; sách bán lại được cho người khác |
| Hủy | Bấm **Hủy thanh toán** | Sách hết bị giữ, vẫn nằm trong giỏ |
| Chuyển thiếu tiền | Chạy script với số tiền nhỏ hơn | Không có đơn; khách có một khoản hoàn bằng đúng số đã chuyển |
| Tiền về muộn | Chạy script sau khi mã đã hết hạn | Còn sách thì đơn vẫn được tạo; hết sách thì thành khoản hoàn |
| Gửi trùng | Một giao dịch Casso gửi hai lần | Chỉ có một đơn (script mỗi lần chạy là một giao dịch mới, nên chạy hai lần sẽ thành "trả hai lần" và lần sau được hoàn) |

### 3.3. Hoàn tiền

1. Khách mở **Đơn hàng của tôi**, bấm **Hủy đơn** ở đơn vừa trả tiền (đơn chưa được cửa hàng xác nhận thì hủy ngay; lý do cần ít nhất 50 ký tự). Hộp thoại hủy cho biết tiền sẽ hoàn về tài khoản đã chuyển; muốn nhận ở tài khoản khác thì bấm **Hoàn về tài khoản khác** và nhập số tài khoản hoặc tải ảnh QR.
2. Khoản hoàn được lập với tài khoản đã điền sẵn. Chưa có tài khoản nào thì khách vào menu tài khoản → **Hoàn tiền của tôi** để khai.
3. Đăng nhập tài khoản quản lý, vào cổng Quản lý → **Thanh toán & Đối soát** → thẻ **Hoàn tiền cho khách**.
4. Mở mã QR tới tài khoản của khách (nội dung `HTx`), chuyển khoản, rồi bấm **Xác nhận đã chuyển**. Khách nhận thông báo đã hoàn tiền.

### 3.4. Chi tiền cho cửa hàng

1. Chủ cửa hàng mở **Đơn hàng cửa hàng**, bấm **Xác nhận đơn** cho một đơn đã trả tiền.
2. Mở cổng của hãng vận chuyển (ví dụ `/shipping/GHN`) và cập nhật đơn đến trạng thái **Đã giao**. Đơn trả trước có số tiền thu hộ bằng 0.
3. Quản lý vào **Thanh toán & Đối soát** → thẻ **Chi cho cửa hàng**: khoản chi được gom theo cửa hàng, mỗi đơn một dòng bằng tiền hàng trừ hoa hồng.
4. Cửa hàng chưa khai tài khoản thì Quản lý bấm **Gửi thông báo cập nhật STK**; chủ cửa hàng vào trang cửa hàng, bấm **Khai tài khoản nhận tiền** (nhập số tài khoản hoặc tải ảnh QR). Việc này không đưa cửa hàng về chờ duyệt.
5. Quản lý tích các đơn của một cửa hàng, bấm **Tạo đợt chi VietQR**: mã QR có tổng tiền và nội dung là mã đợt chi `DC…`. Chuyển khoản rồi bấm **Xác nhận đã chuyển**; cả đợt được đánh dấu cùng lúc và cửa hàng nhận thông báo. Chi tiết ở [management.md](management.md).

### 3.5. Sổ giao dịch

Thẻ **Giao dịch ngân hàng** trong Thanh toán & Đối soát liệt kê mọi giao dịch đã nhận, kèm kết quả đối soát: khớp và đã tạo đơn, không khớp mã nào, hoặc phải hoàn tiền. Lọc được theo từ khóa, kết quả và khoảng ngày. Giao dịch lệch được báo ngay cho Quản lý qua thông báo.

## 4. Nối Casso thật

Casso chỉ gọi được địa chỉ HTTPS công khai, nên làm bước này sau khi web đã deploy.

1. Người giữ tài khoản ngân hàng của sàn đăng ký Casso và liên kết tài khoản đó.
2. Trong Casso: **Kết nối → Tích hợp → Thêm tích hợp → Webhook V2**, chọn tài khoản ngân hàng.
3. Webhook URL: `https://<địa chỉ web>/api/payment/casso/webhook`.
4. Chép **Key bảo mật** Casso sinh ra vào biến môi trường `PAYMENT_CASSO_CHECKSUM_KEY` trên máy chủ, cùng ba biến tài khoản ngân hàng, rồi khởi động lại ứng dụng.
5. Bấm **Gọi thử** trong Casso. Thành công thì giao dịch thử hiện trong thẻ **Giao dịch ngân hàng** với kết quả "Không khớp".
6. Mua thử một cuốn sách giá thấp bằng chuyển khoản thật.

Bước 5 và 6 chưa được kiểm chứng với Casso thật. Phần kiểm chữ ký làm theo mã mẫu Java của Casso.

## 5. Khi gặp lỗi

| Hiện tượng | Nguyên nhân thường gặp |
|---|---|
| Trang thanh toán không có lựa chọn chuyển khoản | Thiếu một trong ba dòng `payment.bank.*`, hoặc chưa khởi động lại sau khi sửa `.env`, hoặc đang đăng nhập không phải tài khoản khách |
| Script trả `HTTP 401` | Khóa trong `.env` khác khóa ứng dụng đang dùng; khởi động lại ứng dụng |
| Script báo không gọi được ứng dụng | Sai cổng: thêm cổng ở cuối lệnh |
| Script trả 200 nhưng trang quét mã không đổi | Sai mã thanh toán. Xem thẻ **Giao dịch ngân hàng**: giao dịch đó sẽ ghi "Không khớp" |
| Tiền về nhưng thành khoản hoàn | Số tiền nhỏ hơn số cần trả, giá sách hoặc giỏ hàng đã đổi sau khi tạo mã, hoặc sách đã hết |
| Trong lúc chờ quét mã, sách trong giỏ hiện "hết hàng" | Đúng thiết kế: tồn kho đang bị trừ tạm để giữ sách cho chính bạn |

## 6. Giới hạn

- Hoàn tiền và chi tiền là chuyển khoản tay; Casso chỉ đọc giao dịch, không chuyển tiền được.
- Phí vận chuyển của đơn trả trước nằm lại ở sàn; việc sàn trả hãng vận chuyển chưa được làm.
- Khoản chi cho cửa hàng chỉ lập cho đơn trả trước. Đơn COD giữ cách ghi nhận cũ.
