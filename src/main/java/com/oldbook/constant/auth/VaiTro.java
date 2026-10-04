package com.oldbook.constant.auth;

public enum VaiTro {

    KHACH_HANG("Khách hàng"),
    CHU_CUA_HANG("Chủ cửa hàng"),
    /**
     * Không còn dùng: đơn vị vận chuyển truy cập bằng link /shipping/{maCode}, không đăng nhập.
     * Giữ lại để tài khoản cũ trong DB vẫn đọc được (JwtAuthenticationFilter gọi VaiTro.valueOf).
     */
    DON_VI_VAN_CHUYEN("Đơn vị vận chuyển"),
    QUAN_LY("Quản lý"),
    QUAN_TRI_VIEN("Quản trị viên");

    private final String tenHienThi;

    VaiTro(String tenHienThi) {
        this.tenHienThi = tenHienThi;
    }

    public String getTenHienThi() {
        return tenHienThi;
    }

    public String getAuthority() {
        return "ROLE_" + name();
    }
}