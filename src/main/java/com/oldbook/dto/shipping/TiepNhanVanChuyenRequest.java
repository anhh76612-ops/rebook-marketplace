package com.oldbook.dto.shipping;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Đơn vị vận chuyển tiếp nhận đơn. Đơn vị nhận được xác định bằng {maCode} trên URL
 * (POST /api/shipping/{maCode}/requests), nên body không còn trường maDVVC.
 */
@Getter
@Setter
public class TiepNhanVanChuyenRequest {
    @NotNull
    private Integer maDHCH;

    @Size(max = 255)
    private String maVanDon;

    @Size(max = 500)
    private String ghiChuGiaoHang;
}
