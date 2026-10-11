package com.oldbook.dto.shipping;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class DanhSachDonViVanChuyenResponse {
    private ThongKeDonViVanChuyenResponse thongKe;
    private List<QuanLyDonViVanChuyenResponse> danhSach;
}
