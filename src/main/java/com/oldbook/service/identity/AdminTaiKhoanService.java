package com.oldbook.service.identity;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.oldbook.constant.auth.TrangThaiTaiKhoan;
import com.oldbook.constant.auth.VaiTro;
import com.oldbook.dto.identity.AdminTaiKhoanResponse;
import com.oldbook.dto.identity.AddRoleRequest;
import com.oldbook.dto.identity.CreateTaiKhoanRequest;
import com.oldbook.dto.identity.LockTaiKhoanRequest;
import com.oldbook.entity.identity.NguoiDung;
import com.oldbook.entity.identity.TaiKhoan;
import com.oldbook.entity.system.NhatKyHeThong;
import com.oldbook.exception.common.BusinessException;
import com.oldbook.repository.identity.NguoiDungRepository;
import com.oldbook.repository.identity.TaiKhoanRepository;
import com.oldbook.repository.system.NhatKyHeThongRepository;
import com.oldbook.service.admin.AdminThongBaoService;
import com.oldbook.service.auth.EmailService;
import com.oldbook.service.auth.TaiKhoanTokenService;
import com.oldbook.service.notification.NghiepVuThongBaoService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.locks.Lock;

@Service
@RequiredArgsConstructor
public class AdminTaiKhoanService {

    private final NguoiDungRepository nguoiDungRepository;
    private final TaiKhoanRepository taiKhoanRepository;
    private final NhatKyHeThongRepository nhatKyHeThongRepository;

    private final PasswordEncoder passwordEncoder;
    private final TaiKhoanTokenService taiKhoanTokenService;

    private final EmailService emailService;

    private final AdminThongBaoService adminThongBaoService;
    private final NghiepVuThongBaoService nghiepVuThongBaoService;

    @Transactional(readOnly = true)
    public List<AdminTaiKhoanResponse> getAll(
            String tuKhoa,
            String vaiTro,
            String trangThai
    ) {

        String keyword =
                tuKhoa == null
                        ? ""
                        : tuKhoa.trim().toLowerCase(Locale.ROOT);

        String roleFilter =
                vaiTro == null
                        ? ""
                        : vaiTro.trim();

        String statusFilter =
                trangThai == null
                        ? ""
                        : trangThai.trim();

        return taiKhoanRepository.findAll()
                .stream()
                .filter(tk -> matchesKeyword(tk, keyword))
                .filter(tk ->
                        roleFilter.isBlank()
                                || roleFilter.equals(
                                tk.getVaiTro()
                        )
                )
                .filter(tk ->
                        statusFilter.isBlank()
                                || statusFilter.equals(
                                tk.getTrangThai()
                        )
                )
                .map(this::toResponse)
                .toList();
    }

    private boolean matchesKeyword(
            TaiKhoan taiKhoan,
            String keyword
    ) {

        if (keyword.isBlank()) {
            return true;
        }

        NguoiDung nd = taiKhoan.getNguoiDung();

        return contains(nd.getEmail(), keyword)
                || contains(nd.getHoTen(), keyword)
                || contains(
                nd.getSoDienThoai(),
                keyword
        )
                || String.valueOf(
                taiKhoan.getMaTK()
        ).contains(keyword)
                || String.valueOf(
                nd.getMaND()
        ).contains(keyword);
    }

    private boolean contains(
            String value,
            String keyword
    ) {

        return value != null
                && value.toLowerCase(
                Locale.ROOT
        ).contains(keyword);
    }

    @Transactional
    public AdminTaiKhoanResponse create(
            CreateTaiKhoanRequest request,
            Integer maTKAdmin,
            String ip
    ) {

        String email =
                request.getEmail()
                        .trim()
                        .toLowerCase(Locale.ROOT);

        String soDienThoai =
                normalizePhone(
                        request.getSoDienThoai()
                );

        VaiTro vaiTro = parseRole(
                request.getVaiTro()
        );

        // Email không được trùng
        if (nguoiDungRepository.existsByEmail(email)) {
            throw new BusinessException(
                    "Email đã được sử dụng"
            );
        }

        // Số điện thoại nếu có nhập không được trùng
        if (soDienThoai != null
                && nguoiDungRepository
                .existsBySoDienThoai(soDienThoai)) {

            throw new BusinessException(
                    "Số điện thoại đã được sử dụng"
            );
        }

        NguoiDung nguoiDung =
                NguoiDung.builder()
                        .email(email)
                        .matKhau(
                                passwordEncoder.encode(
                                        request.getMatKhau()
                                )
                        )
                        .hoTen(
                                request.getHoTen().trim()
                        )
                        .soDienThoai(soDienThoai)
                        .build();

        nguoiDung =
                nguoiDungRepository.save(
                        nguoiDung
                );

        TaiKhoan taiKhoan =
                TaiKhoan.builder()
                        .nguoiDung(nguoiDung)
                        .vaiTro(vaiTro.name())
                        .trangThai(
                                TrangThaiTaiKhoan
                                        .HOAT_DONG.name()
                        )
                        .build();

        taiKhoan =
                taiKhoanRepository.save(
                        taiKhoan
                );

        ghiNhatKy(
                maTKAdmin,
                "TAO_TAI_KHOAN",
                taiKhoan.getMaTK(),
                "Tạo tài khoản mới với vai trò "
                        + vaiTro.name(),
                ip
        );

        adminThongBaoService.taiKhoanDuocTao(maTKAdmin, taiKhoan);

        emailService.sendAdminAccountCreatedEmail(
                nguoiDung.getEmail(),
                nguoiDung.getHoTen(),
                vaiTro.name()
        );


        return toResponse(taiKhoan);
    }

    @Transactional
    public void lock(
            Integer maTKTarget,
            LockTaiKhoanRequest request,
            Integer maTKAdmin,
            String ip
    ) {

        // Không được tự khóa
        if (maTKTarget.equals(maTKAdmin)) {
            throw new BusinessException(
                    "Hệ thống không cho phép Quản trị viên tự khóa tài khoản của chính mình"
            );
        }

        TaiKhoan taiKhoan =
                getTaiKhoan(maTKTarget);

        if (TrangThaiTaiKhoan.BI_KHOA.name()
                .equals(taiKhoan.getTrangThai())) {

            throw new BusinessException(
                    "Tài khoản đã bị khóa"
            );
        }

        taiKhoan.setTrangThai(
                TrangThaiTaiKhoan.BI_KHOA.name()
        );

        taiKhoanRepository.save(taiKhoan);

        // Vô hiệu mọi JWT đang tồn tại
        taiKhoanTokenService.invalidateAllTokens(
                maTKTarget
        );

        ghiNhatKy(
                maTKAdmin,
                "KHOA_TAI_KHOAN",
                maTKTarget,
                "Khóa tài khoản. Lý do: "
                        + request.getLyDo().trim(),
                ip
        );

        adminThongBaoService.taiKhoanBiKhoa(maTKAdmin, taiKhoan, request.getLyDo().trim());

        emailService.sendAccountLockedEmail(
                taiKhoan.getNguoiDung().getEmail(),
                request.getLyDo().trim()
        );
    }

    @Transactional
    public void unlock(
            Integer maTKTarget,
            Integer maTKAdmin,
            String ip
    ) {

        TaiKhoan taiKhoan =
                getTaiKhoan(maTKTarget);

        if (!TrangThaiTaiKhoan.BI_KHOA.name()
                .equals(taiKhoan.getTrangThai())) {

            throw new BusinessException(
                    "Tài khoản hiện không bị khóa"
            );
        }

        taiKhoan.setTrangThai(
                TrangThaiTaiKhoan.HOAT_DONG.name()
        );

        taiKhoanRepository.save(taiKhoan);

        // Không xóa invalidBefore.
        // Token cũ vẫn không được phép sống lại.
        ghiNhatKy(
                maTKAdmin,
                "MO_KHOA_TAI_KHOAN",
                maTKTarget,
                "Mở khóa tài khoản",
                ip
        );

        adminThongBaoService.taiKhoanDuocMoKhoa(maTKAdmin, taiKhoan);

        emailService.sendAccountUnlockedEmail(
                taiKhoan.getNguoiDung().getEmail()
        );
    }

    /**
     * Thêm một vai trò cho người dùng sở hữu tài khoản maTKTarget.
     * Mỗi vai trò là một tài khoản riêng của cùng người dùng, nên tài khoản đang có được giữ nguyên:
     * giỏ hàng, đơn hàng hay cửa hàng gắn với nó không bị ảnh hưởng.
     */
    @Transactional
    public AdminTaiKhoanResponse addRole(
            Integer maTKTarget,
            AddRoleRequest request,
            Integer maTKAdmin,
            String ip
    ) {

        TaiKhoan taiKhoanGoc =
                getTaiKhoan(maTKTarget);

        VaiTro vaiTroMoi =
                parseRole(request.getVaiTro());

        // Chưa xác thực email thì chưa được có tài khoản hoạt động nào:
        // nếu không, thêm vai trò sẽ thành đường vòng bỏ qua bước xác thực.
        if (TrangThaiTaiKhoan.CHO_XAC_THUC.name()
                .equals(taiKhoanGoc.getTrangThai())) {

            throw new BusinessException(
                    "Người dùng chưa xác thực email nên chưa thể thêm vai trò"
            );
        }

        NguoiDung nguoiDung =
                taiKhoanGoc.getNguoiDung();

        if (taiKhoanRepository
                .existsByNguoiDung_MaNDAndVaiTro(
                        nguoiDung.getMaND(),
                        vaiTroMoi.name()
                )) {

            throw new BusinessException(
                    "Người dùng đã có tài khoản với vai trò "
                            + vaiTroMoi.getTenHienThi()
            );
        }

        TaiKhoan taiKhoanMoi =
                taiKhoanRepository.save(
                        TaiKhoan.builder()
                                .nguoiDung(nguoiDung)
                                .vaiTro(vaiTroMoi.name())
                                .trangThai(
                                        TrangThaiTaiKhoan
                                                .HOAT_DONG.name()
                                )
                                .build()
                );

        ghiNhatKy(
                maTKAdmin,
                "THEM_VAI_TRO",
                taiKhoanMoi.getMaTK(),
                "Thêm vai trò "
                        + vaiTroMoi.name()
                        + " cho người dùng đang có tài khoản #"
                        + maTKTarget,
                ip
        );

        adminThongBaoService.vaiTroDuocThem(maTKAdmin, taiKhoanMoi);
        nghiepVuThongBaoService.vaiTroDuocThem(taiKhoanMoi);

        return toResponse(taiKhoanMoi);
    }

    private TaiKhoan getTaiKhoan(
            Integer maTK
    ) {

        return taiKhoanRepository.findById(maTK)
                .orElseThrow(() ->
                        new BusinessException(
                                "Tài khoản không tồn tại"
                        )
                );
    }

    private VaiTro parseRole(
            String vaiTro
    ) {

        VaiTro role;
        try {
            role = VaiTro.valueOf(
                    vaiTro.trim()
                            .toUpperCase(Locale.ROOT)
            );

        } catch (Exception e) {

            throw new BusinessException(
                    "Vai trò không hợp lệ"
            );
        }

        // Đơn vị vận chuyển không còn dùng tài khoản: truy cập bằng link /shipping/{maCode}.
        if (role == VaiTro.DON_VI_VAN_CHUYEN) {
            throw new BusinessException(
                    "Đơn vị vận chuyển không dùng tài khoản. Hãy thêm đối tác ở mục quản lý đơn vị vận chuyển."
            );
        }

        return role;
    }

    private String normalizePhone(
            String soDienThoai
    ) {

        if (soDienThoai == null) {
            return null;
        }

        String phone =
                soDienThoai.trim();

        return phone.isBlank()
                ? null
                : phone;
    }

    private AdminTaiKhoanResponse toResponse(
            TaiKhoan taiKhoan
    ) {

        NguoiDung nd =
                taiKhoan.getNguoiDung();

        return AdminTaiKhoanResponse.builder()
                .maTK(taiKhoan.getMaTK())
                .maND(nd.getMaND())
                .email(nd.getEmail())
                .hoTen(nd.getHoTen())
                .soDienThoai(nd.getSoDienThoai())
                .vaiTro(taiKhoan.getVaiTro())
                .trangThai(taiKhoan.getTrangThai())
                .ngayTao(taiKhoan.getNgayTao())
                .build();
    }

    private void ghiNhatKy(
            Integer maTKAdmin,
            String hanhDong,
            Integer maDoiTuong,
            String moTa,
            String ip
    ) {

        TaiKhoan admin =
                taiKhoanRepository.findById(maTKAdmin)
                        .orElseThrow(() ->
                                new BusinessException(
                                        "Tài khoản quản trị không tồn tại"
                                )
                        );

        NhatKyHeThong log =
                NhatKyHeThong.builder()
                        .taiKhoan(admin)
                        .hanhDong(hanhDong)
                        .loaiDoiTuong("TAI_KHOAN")
                        .maDoiTuong(maDoiTuong)
                        .moTa(moTa)
                        .diaChiIp(ip)
                        .thoiGian(LocalDateTime.now())
                        .build();

        nhatKyHeThongRepository.save(log);
    }
}