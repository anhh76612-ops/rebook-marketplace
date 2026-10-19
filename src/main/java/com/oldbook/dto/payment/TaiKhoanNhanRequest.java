package com.oldbook.dto.payment;

import com.oldbook.exception.common.BusinessException;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Nơi nhận tiền (khách nhận hoàn, cửa hàng nhận doanh thu): hợp lệ khi có đủ ba trường tài khoản
 * HOẶC có ảnh mã QR ngân hàng đã tải lên. Có cả hai thì càng tốt: Quản lý đối chiếu được tài khoản.
 */
public record TaiKhoanNhanRequest(
        @Pattern(regexp = "[0-9]{6}", message = "Mã ngân hàng không hợp lệ")
        String maNganHang,

        @Pattern(regexp = "[0-9A-Za-z]{4,30}", message = "Số tài khoản chỉ gồm chữ và số, từ 4 đến 30 ký tự")
        String soTaiKhoan,

        @Size(max = 255, message = "Tên chủ tài khoản không được vượt quá 255 ký tự")
        String tenChuTaiKhoan,

        // Địa chỉ ảnh do API tải ảnh QR trả về (Cloudinary, https).
        @Size(max = 500, message = "Địa chỉ ảnh QR quá dài")
        @Pattern(regexp = "https://\\S+", message = "Ảnh mã QR không hợp lệ")
        String qrNganHangUrl
) {

    public boolean coTaiKhoan() {
        return !trong(maNganHang) && !trong(soTaiKhoan) && !trong(tenChuTaiKhoan);
    }

    public boolean coQr() {
        return !trong(qrNganHangUrl);
    }

    /** Ba trường tài khoản đi cùng nhau; và phải có tài khoản hoặc ảnh QR. */
    public void kiemTra() {
        boolean coMotPhan = !trong(maNganHang) || !trong(soTaiKhoan) || !trong(tenChuTaiKhoan);
        if (coMotPhan && !coTaiKhoan()) {
            throw new BusinessException("Vui lòng nhập đủ ngân hàng, số tài khoản và tên chủ tài khoản");
        }
        if (!coTaiKhoan() && !coQr()) {
            throw new BusinessException("Vui lòng nhập thông tin tài khoản hoặc tải ảnh mã QR ngân hàng");
        }
    }

    public String maNganHangGon() {
        return coTaiKhoan() ? maNganHang.trim() : null;
    }

    public String soTaiKhoanGon() {
        return coTaiKhoan() ? soTaiKhoan.trim() : null;
    }

    public String tenChuTaiKhoanGon() {
        return coTaiKhoan() ? tenChuTaiKhoan.trim() : null;
    }

    public String qrGon() {
        return coQr() ? qrNganHangUrl.trim() : null;
    }

    private static boolean trong(String value) {
        return value == null || value.isBlank();
    }
}
