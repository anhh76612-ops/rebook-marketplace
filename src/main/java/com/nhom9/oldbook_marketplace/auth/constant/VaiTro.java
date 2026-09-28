package com.nhom9.oldbook_marketplace.auth.constant;

public enum VaiTro {

    KHACH_HANG("Khách hàng"),
    CHU_CUA_HANG("Chủ cửa hàng"),
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