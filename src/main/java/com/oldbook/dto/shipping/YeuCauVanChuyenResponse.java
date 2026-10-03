package com.oldbook.dto.shipping;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
public class YeuCauVanChuyenResponse {
    private Integer maDHCH;
    private Integer maDH;
    private String trangThaiDonHang;
    private String trangThaiThanhToan;
    private BigDecimal tongTien;
    private BigDecimal phiShip;
    private LocalDateTime ngayCapNhat;
    private String ghiChu;
}
