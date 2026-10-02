package com.oldbook.constant.shipping;

/**
 * Trạng thái giao hàng của một đơn hàng con (đơn của từng cửa hàng).
 * Chỉ tồn tại sau khi chủ cửa hàng đã xác nhận đơn.
 */
public enum TrangThaiVanChuyen {

    CHO_LAY_HANG("Chờ lấy hàng"),
    DANG_GIAO("Đang giao"),
    DA_GIAO("Đã giao"),
    GIAO_THAT_BAI("Giao thất bại");

    private final String tenHienThi;

    TrangThaiVanChuyen(String tenHienThi) {
        this.tenHienThi = tenHienThi;
    }

    public String getTenHienThi() {
        return tenHienThi;
    }

    /** Các bước chuyển trạng thái hợp lệ. */
    public boolean canMoveTo(TrangThaiVanChuyen next) {
        return switch (this) {
            case CHO_LAY_HANG -> next == DANG_GIAO;
            case DANG_GIAO -> next == DA_GIAO || next == GIAO_THAT_BAI;
            case GIAO_THAT_BAI -> next == DANG_GIAO;
            case DA_GIAO -> false;
        };
    }
}
