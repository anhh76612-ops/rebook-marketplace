package com.oldbook.dto.shipping;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TiepNhanVanChuyenRequest {
    @NotNull
    private Integer maDHCH;

    @NotNull
    private Integer maDVVC;

    @Size(max = 255)
    private String maVanDon;

    @Size(max = 50)
    private String trangThaiVanChuyen;

    @Size(max = 500)
    private String ghiChuGiaoHang;
}
