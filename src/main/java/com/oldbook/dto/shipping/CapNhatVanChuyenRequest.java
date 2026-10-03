package com.oldbook.dto.shipping;

import jakarta.validation.constraints.NotBlank;
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

    private Integer soLanGiaoThatBai;

    private BigDecimal tienHoanTra;

    @Size(max = 255)
    private String maVanDon;
}
