package com.oldbook.dto.admin;

import com.oldbook.dto.identity.AdminTaiKhoanResponse;
import com.oldbook.dto.identity.DiaChiResponse;
import com.oldbook.dto.store.CuaHangResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class AdminNguoiDungProfileResponse {

    private Integer maND;

    private String email;

    private String hoTen;

    private String soDienThoai;

    private String avatarUrl;

    private String tieuSu;

    private LocalDateTime ngayTao;

    private List<AdminTaiKhoanResponse> taiKhoans;

    private List<DiaChiResponse> diaChis;

    private CuaHangResponse cuaHang;

    private List<AdminNhatKyResponse> hoatDongGanDay;
}
