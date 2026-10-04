package com.oldbook.dto.shipping;

import com.oldbook.entity.shipping.DonViVanChuyen;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Builder
public class DonViVanChuyenResponse {
    private Integer maDVVC;
    private String maCode;
    private String tenDonVi;
    private BigDecimal phiVanChuyen;
    private Integer thoiGianGiaoDuKien;
    private String soDienThoai;
    private String email;
    private String trangThai;
    private String thongTinTichHop;
    private LocalDate ngayTao;

    // Sinh tự động từ maCode: không lưu DB, thêm hãng mới là có ngay trang và API riêng.
    private String duongDanTrang;
    private String duongDanApi;

    public static String pageUrl(String maCode) {
        return "/shipping/" + maCode;
    }

    public static String apiUrl(String maCode) {
        return "/api/shipping/" + maCode;
    }

    public static DonViVanChuyenResponse from(DonViVanChuyen e) {
        return DonViVanChuyenResponse.builder()
                .maDVVC(e.getMaDVVC())
                .maCode(e.getMaCode())
                .tenDonVi(e.getTenDonVi())
                .phiVanChuyen(e.getPhiVanChuyen())
                .thoiGianGiaoDuKien(e.getThoiGianGiaoDuKien())
                .soDienThoai(e.getSoDienThoai())
                .email(e.getEmail())
                .trangThai(e.getTrangThai())
                .thongTinTichHop(e.getThongTinTichHop())
                .ngayTao(e.getNgayTao())
                .duongDanTrang(e.getMaCode() == null ? null : pageUrl(e.getMaCode()))
                .duongDanApi(e.getMaCode() == null ? null : apiUrl(e.getMaCode()))
                .build();
    }
}
