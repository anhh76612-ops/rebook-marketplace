package com.oldbook.constant.shipping;

import com.oldbook.constant.order.TrangThaiDonHang;

/**
 * Trạng thái giao hàng của một đơn hàng con, do đơn vị vận chuyển báo về.
 * Chỉ tồn tại sau khi chủ cửa hàng đã xác nhận đơn và đơn đã được chuyển cho đơn vị vận chuyển.
 */
public enum TrangThaiVanChuyen {

    /** Đơn vị vận chuyển đã tiếp nhận đơn, chưa phân công lấy hàng. */
    CHO_LAY_HANG("Chờ lấy hàng", TrangThaiDonHang.DA_XAC_NHAN),
    DANG_LAY_HANG("Đang lấy hàng", TrangThaiDonHang.DANG_LAY_HANG),
    DA_LAY_HANG("Đã lấy hàng", TrangThaiDonHang.DA_LAY_HANG),
    DANG_GIAO("Đang giao", TrangThaiDonHang.DANG_GIAO),
    DA_GIAO("Đã giao", TrangThaiDonHang.DA_GIAO),
    /** Một lần giao không thành công; đơn hàng con vẫn ở trạng thái "Đang giao" cho tới khi hoàn trả. */
    GIAO_THAT_BAI("Giao thất bại", TrangThaiDonHang.DANG_GIAO),
    DA_HOAN_TRA("Đã hoàn trả", TrangThaiDonHang.DA_HOAN_TRA),
    /** Chỉ do hệ thống đặt khi cửa hàng đồng ý hủy đơn trước lúc hãng lấy hàng; hãng không tự chuyển sang trạng thái này. */
    DA_HUY("Đã hủy", TrangThaiDonHang.DA_HUY);

    private final String tenHienThi;
    private final TrangThaiDonHang trangThaiDonHang;

    TrangThaiVanChuyen(String tenHienThi, TrangThaiDonHang trangThaiDonHang) {
        this.tenHienThi = tenHienThi;
        this.trangThaiDonHang = trangThaiDonHang;
    }

    public String getTenHienThi() {
        return tenHienThi;
    }

    /** Trạng thái đơn hàng con tương ứng với trạng thái giao hàng này. */
    public TrangThaiDonHang toTrangThaiDonHang() {
        return trangThaiDonHang;
    }

    public boolean isFinal() {
        return this == DA_GIAO || this == DA_HOAN_TRA || this == DA_HUY;
    }

    public boolean canMoveTo(TrangThaiVanChuyen next) {
        return switch (this) {
            case CHO_LAY_HANG -> next == DANG_LAY_HANG || next == DA_LAY_HANG || next == DANG_GIAO;
            case DANG_LAY_HANG -> next == DA_LAY_HANG || next == DANG_GIAO;
            case DA_LAY_HANG -> next == DANG_GIAO;
            case DANG_GIAO -> next == DA_GIAO || next == GIAO_THAT_BAI || next == DA_HOAN_TRA;
            case GIAO_THAT_BAI -> next == DANG_GIAO || next == DA_HOAN_TRA;
            case DA_GIAO, DA_HOAN_TRA, DA_HUY -> false;
        };
    }

    public static TrangThaiVanChuyen fromName(String value) {
        if (value == null) {
            return null;
        }
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
