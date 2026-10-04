package com.oldbook.constant.shipping;

import com.oldbook.constant.order.TrangThaiDonHang;

/**
 * Trạng thái giao hàng của một đơn hàng con (đơn của từng cửa hàng).
 * Dùng chung cho cửa hàng, đơn vị vận chuyển và khách hàng.
 * Chỉ tồn tại sau khi chủ cửa hàng đã xác nhận đơn.
 */
public enum TrangThaiVanChuyen {

    CHO_LAY_HANG("Chờ lấy hàng"),
    DANG_GIAO("Đang giao"),
    DA_GIAO("Đã giao"),
    GIAO_THAT_BAI("Giao thất bại"),
    HOAN_TRA("Hoàn trả");

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
            case GIAO_THAT_BAI -> next == DANG_GIAO || next == HOAN_TRA;
            case DA_GIAO, HOAN_TRA -> false;
        };
    }

    /** Trạng thái kết quả giao hàng (đơn vị vận chuyển báo về cho cửa hàng/khách). */
    public boolean laKetQua() {
        return this == DA_GIAO || this == GIAO_THAT_BAI || this == HOAN_TRA;
    }

    /**
     * Trạng thái đơn hàng con tương ứng khi vận đơn chuyển sang trạng thái này.
     * Trả về null nếu không đổi trạng thái đơn (GIAO_THAT_BAI vẫn là "đang giao" để khách còn thấy đơn ở tab Đang giao).
     */
    public TrangThaiDonHang donHangTuongUng() {
        return switch (this) {
            case DANG_GIAO -> TrangThaiDonHang.DANG_GIAO;
            case DA_GIAO -> TrangThaiDonHang.DA_GIAO;
            case HOAN_TRA -> TrangThaiDonHang.HOAN_TRA;
            default -> null;
        };
    }

    /**
     * Đọc trạng thái từ chuỗi, kèm tên cũ của luồng đơn vị vận chuyển (dữ liệu đã lưu trước đây).
     * Trả về null nếu không hợp lệ.
     */
    public static TrangThaiVanChuyen from(String value) {
        if (value == null) {
            return null;
        }
        String v = value.trim().toUpperCase();
        switch (v) {
            case "DA_TIEP_NHAN":
                return CHO_LAY_HANG;
            case "DA_LAY_HANG":
            case "DANG_VAN_CHUYEN":
                return DANG_GIAO;
            case "GIAO_THANH_CONG":
                return DA_GIAO;
            default:
                try {
                    return valueOf(v);
                } catch (IllegalArgumentException ex) {
                    return null;
                }
        }
    }
}
