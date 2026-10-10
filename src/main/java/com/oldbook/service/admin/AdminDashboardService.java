package com.oldbook.service.admin;

import com.oldbook.constant.auth.TrangThaiTaiKhoan;
import com.oldbook.constant.auth.VaiTro;
import com.oldbook.dto.admin.AdminDashboardSummaryResponse;
import com.oldbook.dto.admin.AdminDashboardSummaryResponse.PhanBoVaiTro;
import com.oldbook.repository.admin.AdminDashboardRepository;
import com.oldbook.repository.admin.AdminDashboardRepository.CodeCount;
import com.oldbook.repository.identity.NguoiDungRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminDashboardService {

    private final NguoiDungRepository nguoiDungRepository;
    private final AdminDashboardRepository adminDashboardRepository;

    @Transactional(readOnly = true)
    public AdminDashboardSummaryResponse getSummary() {

        Map<String, Long> theoTrangThai =
                toMap(adminDashboardRepository.countAccountsGroupedByStatus());

        Map<String, Long> theoVaiTro =
                toMap(adminDashboardRepository.countAccountsGroupedByRole());

        // Duyệt theo enum để thứ tự ổn định; vai trò chưa có tài khoản nào thì không hiển thị.
        List<PhanBoVaiTro> phanBoVaiTro = Arrays.stream(VaiTro.values())
                .filter(vaiTro -> theoVaiTro.getOrDefault(vaiTro.name(), 0L) > 0)
                .map(vaiTro -> new PhanBoVaiTro(
                        vaiTro.name(),
                        vaiTro.getTenHienThi(),
                        theoVaiTro.get(vaiTro.name())
                ))
                .toList();

        return AdminDashboardSummaryResponse.builder()
                .tongNguoiDung(nguoiDungRepository.count())
                .tongTaiKhoan(theoTrangThai.values().stream().mapToLong(Long::longValue).sum())
                .taiKhoanHoatDong(count(theoTrangThai, TrangThaiTaiKhoan.HOAT_DONG))
                .taiKhoanBiKhoa(count(theoTrangThai, TrangThaiTaiKhoan.BI_KHOA))
                .taiKhoanChoXacThuc(count(theoTrangThai, TrangThaiTaiKhoan.CHO_XAC_THUC))
                .phanBoVaiTro(phanBoVaiTro)
                .build();
    }

    private Map<String, Long> toMap(List<CodeCount> rows) {
        return rows.stream()
                .collect(Collectors.toMap(CodeCount::getCode, CodeCount::getTotal));
    }

    private long count(Map<String, Long> theoTrangThai, TrangThaiTaiKhoan trangThai) {
        return theoTrangThai.getOrDefault(trangThai.name(), 0L);
    }
}
