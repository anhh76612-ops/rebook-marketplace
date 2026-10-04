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
    // Đơn vị vận chuyển khách đã chọn cho đơn này
    private Integer maDVVC;
    private String maCode;
    private String tenDonVi;
    private String trangThaiDonHang;
    private String trangThaiThanhToan;
    private BigDecimal tongTien;
    private BigDecimal phiShip;
    private LocalDateTime ngayCapNhat;
    private String ghiChu;
}
