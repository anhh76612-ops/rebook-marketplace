package com.oldbook.dto.store;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateCuaHangRequest(
        @NotBlank(message = "Tên cửa hàng không được để trống") @Size(max = 255) String tenCuaHang,
        @Size(max = 1000) String moTa,
        @NotBlank(message = "Địa chỉ cửa hàng không được để trống") @Size(max = 500) String diaChiCH,
        @NotBlank(message = "Số điện thoại cửa hàng không được để trống") @Size(max = 20) String soDienThoaiCH,
        // Tài khoản nhận tiền sàn chi cho đơn khách đã chuyển khoản. Form đăng ký bắt buộc nhập;
        // API vẫn nhận yêu cầu để trống cả ba, cửa hàng bổ sung sau qua PUT /api/store/me/bank-account.
        @Pattern(regexp = "[0-9]{6}", message = "Mã ngân hàng không hợp lệ") String maNganHang,
        @Pattern(regexp = "[0-9A-Za-z]{4,30}", message = "Số tài khoản chỉ gồm chữ và số, từ 4 đến 30 ký tự") String soTaiKhoan,
        @Size(max = 255) String tenChuTaiKhoan
) {

    public CreateCuaHangRequest(String tenCuaHang, String moTa, String diaChiCH, String soDienThoaiCH) {
        this(tenCuaHang, moTa, diaChiCH, soDienThoaiCH, null, null, null);
    }
}
