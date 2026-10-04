package com.oldbook.dto.shipping;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CapNhatVanChuyenRequest {
    @NotBlank
    @Size(max = 50)
    private String trangThai;

    @Size(max = 500)
    private String ghiChu;

    // Không còn dùng: số lần giao thất bại do hệ thống tự đếm. Giữ trường để client cũ không lỗi.
    private Integer soLanGiaoThatBai;

    // Chỉ có tác dụng khi trạng thái là HOAN_TRA
    @PositiveOrZero
    private BigDecimal tienHoanTra;

    @Size(max = 255)
    private String maVanDon;
}
