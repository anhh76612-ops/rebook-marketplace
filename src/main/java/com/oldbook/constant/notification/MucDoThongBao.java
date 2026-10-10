package com.oldbook.constant.notification;

public enum MucDoThongBao {

    // Cần xử lý ngay
    NGHIEM_TRONG("Nghiêm trọng"),

    // Cần kiểm tra sớm
    QUAN_TRONG("Quan trọng"),

    // Chỉ cần biết
    THONG_TIN("Thông tin");

    private final String tenHienThi;

    MucDoThongBao(String tenHienThi) {
        this.tenHienThi = tenHienThi;
    }

    public String getTenHienThi() {
        return tenHienThi;
    }
}
