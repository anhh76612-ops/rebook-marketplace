package com.oldbook.dto.shipping;

import com.oldbook.entity.shipping.DonViVanChuyen;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@Builder
public class DonViVanChuyenResponse {
    private Integer maDVVC;
    private String tenDonVi;
    private String soDienThoai;
    private String email;
    private String trangThai;
    private String thongTinTichHop;
    private LocalDate ngayTao;

    public static DonViVanChuyenResponse from(DonViVanChuyen e) {
        return DonViVanChuyenResponse.builder()
                .maDVVC(e.getMaDVVC())
                .tenDonVi(e.getTenDonVi())
                .soDienThoai(e.getSoDienThoai())
                .email(e.getEmail())
                .trangThai(e.getTrangThai())
                .thongTinTichHop(e.getThongTinTichHop())
                .ngayTao(e.getNgayTao())
                .build();
    }
}
