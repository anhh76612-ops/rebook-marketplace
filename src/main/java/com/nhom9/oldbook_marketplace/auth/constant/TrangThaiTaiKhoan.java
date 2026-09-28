package com.nhom9.oldbook_marketplace.auth.constant;

public enum TrangThaiTaiKhoan {

    HOAT_DONG("Hoạt động"),
    BI_KHOA("Bị khóa");

    private final String tenHienThi;

    TrangThaiTaiKhoan(String tenHienThi) {
        this.tenHienThi = tenHienThi;
    }

    public String getTenHienThi() {
        return tenHienThi;
    }
}