package com.nhom9.oldbook_marketplace.catalog.service;

import com.nhom9.oldbook_marketplace.catalog.dto.SachResponse;
import com.nhom9.oldbook_marketplace.catalog.entity.Sach;

public final class SachMapper {
    private SachMapper() {
    }

    public static SachResponse toResponse(Sach sach) {
        return toResponse(sach, true);
    }

    public static SachResponse toPublicResponse(Sach sach) {
        return toResponse(sach, false);
    }

    private static SachResponse toResponse(Sach sach, boolean includeRejectionReason) {
        return new SachResponse(
                sach.getMaSach(), sach.getCuaHang().getMaCH(), sach.getCuaHang().getTenCuaHang(),
                sach.getDanhMuc().getMaDM(), sach.getDanhMuc().getTenDanhMuc(),
                sach.getTenSach(), sach.getTacGia(), sach.getNhaXuatBan(), sach.getNamXuatBan(),
                sach.getGiaBia(), sach.getGiaBanCu(), sach.getDoMoiPercent(),
                sach.getTinhTrangVatLy(), sach.getMoTaChiTiet(), sach.getSoLuongTon(),
                sach.getHinhAnhUrl(), sach.getTrangThaiDuyet(), sach.getTrangThaiBan(),
                includeRejectionReason ? sach.getLyDoTuChoi() : null, sach.getNgayTao()
        );
    }
}
