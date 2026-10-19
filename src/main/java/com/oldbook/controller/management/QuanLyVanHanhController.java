package com.oldbook.controller.management;

import com.oldbook.dto.common.ApiResponse;
import com.oldbook.dto.management.QuanLyDto.DoiHoaHongRequest;
import com.oldbook.dto.management.QuanLyDto.DotChiRequest;
import com.oldbook.dto.management.QuanLyDto.DotChiXacNhanRequest;
import com.oldbook.dto.management.QuanLyDto.DotChiXemTruoc;
import com.oldbook.dto.management.QuanLyDto.HoaHongDanhMuc;
import com.oldbook.dto.management.QuanLyDto.HoaHongLichSu;
import com.oldbook.dto.management.QuanLyDto.HuyHieu;
import com.oldbook.dto.management.QuanLyDto.KetQuaNhac;
import com.oldbook.dto.management.QuanLyDto.NhomChi;
import com.oldbook.dto.management.QuanLyDto.TongQuan;
import com.oldbook.dto.management.ThongKeDto.ThongKe;
import com.oldbook.filter.auth.JwtAuthenticationFilter.AuthenticatedUserDetails;
import com.oldbook.service.management.QuanLyChiTraService;
import com.oldbook.service.management.QuanLyHoaHongService;
import com.oldbook.service.management.QuanLyTongQuanService;
import com.oldbook.service.management.ThongKeVanHanhService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** API của cổng Quản lý: tổng quan, chi trả cho cửa hàng theo đợt, nhắc khai tài khoản, hoa hồng. Quyền do luật /api/management/** kiểm. */
@RestController
@RequestMapping("/api/management")
@RequiredArgsConstructor
public class QuanLyVanHanhController {

    private final QuanLyTongQuanService tongQuanService;
    private final QuanLyChiTraService chiTraService;
    private final QuanLyHoaHongService hoaHongService;
    private final ThongKeVanHanhService thongKeService;

    @GetMapping("/overview")
    public ResponseEntity<ApiResponse<TongQuan>> tongQuan() {
        return ok(tongQuanService.tongQuan());
    }

    @GetMapping("/overview/badges")
    public ResponseEntity<ApiResponse<HuyHieu>> huyHieu() {
        return ok(tongQuanService.huyHieu());
    }

    // ---------------- Chi trả cho cửa hàng ----------------

    @GetMapping("/payments/store-payouts")
    public ResponseEntity<ApiResponse<List<NhomChi>>> chiTheoCuaHang(
            @RequestParam(required = false) String trangThai) {
        return ok(chiTraService.theoCuaHang(trangThai));
    }

    @PostMapping("/payments/store-payouts/preview")
    public ResponseEntity<ApiResponse<DotChiXemTruoc>> xemTruoc(@RequestBody DotChiRequest request) {
        return ok(chiTraService.xemTruoc(request.maYeuCau()));
    }

    @PostMapping("/payments/store-payouts/done")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> xacNhan(@RequestBody DotChiXacNhanRequest request) {
        int soKhoan = chiTraService.xacNhanDaChuyen(maTK(), request.maDotChi(), request.maYeuCau());
        return ok(Map.of("soKhoan", soKhoan));
    }

    @PostMapping("/payments/stores/{maCH}/remind-bank")
    public ResponseEntity<ApiResponse<KetQuaNhac>> nhacCuaHang(@PathVariable Integer maCH) {
        return ok(chiTraService.nhacCuaHang(maCH));
    }

    @PostMapping("/payments/refunds/{maYeuCau}/remind-bank")
    public ResponseEntity<ApiResponse<KetQuaNhac>> nhacKhach(@PathVariable Integer maYeuCau) {
        return ok(chiTraService.nhacKhachHoanTien(maYeuCau));
    }

    // ---------------- Thống kê ----------------

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<ThongKe>> thongKe(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate tuNgay,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate denNgay) {
        return ok(thongKeService.thongKe(tuNgay, denNgay));
    }

    @PostMapping("/stats/stores/{maCH}/remind-confirm")
    public ResponseEntity<ApiResponse<KetQuaNhac>> nhacXacNhan(@PathVariable Integer maCH) {
        return ok(thongKeService.nhacXacNhan(maCH));
    }

    // ---------------- Hoa hồng ----------------

    @GetMapping("/commissions")
    public ResponseEntity<ApiResponse<List<HoaHongDanhMuc>>> hoaHong() {
        return ok(hoaHongService.danhSach());
    }

    @PutMapping("/commissions/{maDM}")
    public ResponseEntity<ApiResponse<HoaHongDanhMuc>> doiHoaHong(
            @PathVariable Integer maDM, @RequestBody DoiHoaHongRequest request) {
        return ok(hoaHongService.doiTyLe(maTK(), maDM, request.tyLe(), request.ghiChu()));
    }

    @GetMapping("/commissions/{maDM}/history")
    public ResponseEntity<ApiResponse<List<HoaHongLichSu>>> lichSuHoaHong(@PathVariable Integer maDM) {
        return ok(hoaHongService.lichSu(maDM));
    }

    private static <T> ResponseEntity<ApiResponse<T>> ok(T data) {
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    private static Integer maTK() {
        return ((AuthenticatedUserDetails) SecurityContextHolder.getContext().getAuthentication().getDetails()).maTK();
    }
}
