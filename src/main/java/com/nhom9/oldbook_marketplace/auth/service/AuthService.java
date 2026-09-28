package com.nhom9.oldbook_marketplace.auth.service;

import com.nhom9.oldbook_marketplace.auth.dto.LoginRequest;
import com.nhom9.oldbook_marketplace.auth.dto.LoginResponse;
import com.nhom9.oldbook_marketplace.auth.dto.RegisterRequest;
import com.nhom9.oldbook_marketplace.common.exception.BusinessException;
import com.nhom9.oldbook_marketplace.identity.entity.NguoiDung;
import com.nhom9.oldbook_marketplace.identity.entity.TaiKhoan;
import com.nhom9.oldbook_marketplace.identity.repository.NguoiDungRepository;
import com.nhom9.oldbook_marketplace.identity.repository.TaiKhoanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

import com.nhom9.oldbook_marketplace.auth.constant.TrangThaiTaiKhoan;
import com.nhom9.oldbook_marketplace.auth.constant.VaiTro;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final NguoiDungRepository nguoiDungRepository;
    private final TaiKhoanRepository taiKhoanRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    /**
     * Đăng ký người dùng mới.
     *
     * Hiện tại:
     * - Tạo NguoiDung
     * - Mã hóa mật khẩu bằng BCrypt
     * - Tạo TaiKhoan với vai trò Khách hàng
     *
     * OTP sẽ được bổ sung sau khi xây dựng module mã xác thực.
     */
    @Transactional
    public void register(RegisterRequest request) {

        String email = normalizeEmail(request.getEmail());
        String soDienThoai = normalizePhone(request.getSoDienThoai());

        // 1. Kiểm tra email đã tồn tại
        if (nguoiDungRepository.existsByEmail(email)) {
            throw new BusinessException(
                    "Email đã được sử dụng"
            );
        }

        // 2. Kiểm tra số điện thoại nếu người dùng có nhập
        if (soDienThoai != null
                && nguoiDungRepository.existsBySoDienThoai(soDienThoai)) {

            throw new BusinessException(
                    "Số điện thoại đã được sử dụng"
            );
        }

        // 3. Mã hóa mật khẩu bằng BCrypt
        String matKhauMaHoa = passwordEncoder.encode(
                request.getMatKhau()
        );

        // 4. Tạo NguoiDung
        NguoiDung nguoiDung = NguoiDung.builder()
                .email(email)
                .matKhau(matKhauMaHoa)
                .hoTen(request.getHoTen().trim())
                .soDienThoai(soDienThoai)
                .build();

        nguoiDungRepository.save(nguoiDung);

        // 5. Tự động tạo tài khoản Khách hàng
        TaiKhoan taiKhoan = TaiKhoan.builder()
                .nguoiDung(nguoiDung)
                .vaiTro(VaiTro.KHACH_HANG.name())
                .trangThai(TrangThaiTaiKhoan.HOAT_DONG.name())
                .build();

        taiKhoanRepository.save(taiKhoan);
    }

    /**
     * Đăng nhập.
     */
    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {

        String email = normalizeEmail(request.getEmail());

        // 1. Tìm NguoiDung theo email
        NguoiDung nguoiDung = nguoiDungRepository.findByEmail(email)
                .orElseThrow(() ->
                        new BusinessException(
                                "Email hoặc mật khẩu không chính xác"
                        )
                );

        // 2. Kiểm tra mật khẩu bằng BCrypt
        boolean matKhauDung = passwordEncoder.matches(
                request.getMatKhau(),
                nguoiDung.getMatKhau()
        );

        if (!matKhauDung) {
            throw new BusinessException(
                    "Email hoặc mật khẩu không chính xác"
            );
        }

        // 3. Lấy các tài khoản đang hoạt động
        List<TaiKhoan> taiKhoans =
                taiKhoanRepository
                        .findByNguoiDung_MaNDAndTrangThai(
                                nguoiDung.getMaND(),
                                TrangThaiTaiKhoan.HOAT_DONG.name()
                        );

        if (taiKhoans.isEmpty()) {
            throw new BusinessException(
                    "Người dùng không có tài khoản đang hoạt động"
            );
        }

        // 4. Có đúng 1 tài khoản → đăng nhập thẳng
        if (taiKhoans.size() == 1) {

            TaiKhoan taiKhoan = taiKhoans.get(0);

            String token = jwtService.generateToken(
                    nguoiDung.getMaND(),
                    taiKhoan.getMaTK(),
                    taiKhoan.getVaiTro()
            );

            return LoginResponse.builder()
                    .maND(nguoiDung.getMaND())
                    .maTK(taiKhoan.getMaTK())
                    .vaiTro(taiKhoan.getVaiTro())
                    .token(token)
                    .roleSelectionToken(null)
                    .canChonVaiTro(false)
                    .taiKhoans(toTaiKhoanResponse(taiKhoans))
                    .build();
        }

        // 5. Có nhiều tài khoản → cho chọn vai trò
        String roleSelectionToken =
                jwtService.generateRoleSelectionToken(
                        nguoiDung.getMaND()
                );

        return LoginResponse.builder()
                .maND(nguoiDung.getMaND())
                .maTK(null)
                .vaiTro(null)
                .token(null)
                .roleSelectionToken(roleSelectionToken)
                .canChonVaiTro(true)
                .taiKhoans(toTaiKhoanResponse(taiKhoans))
                .build();
    }

    private List<LoginResponse.TaiKhoanResponse> toTaiKhoanResponse(
            List<TaiKhoan> taiKhoans
    ) {
        return taiKhoans.stream()
                .map(taiKhoan ->
                        LoginResponse.TaiKhoanResponse.builder()
                                .maTK(taiKhoan.getMaTK())
                                .vaiTro(taiKhoan.getVaiTro())
                                .trangThai(taiKhoan.getTrangThai())
                                .build()
                )
                .toList();
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizePhone(String soDienThoai) {

        if (soDienThoai == null) {
            return null;
        }

        String phone = soDienThoai.trim();

        if (phone.isEmpty()) {
            return null;
        }

        return phone;
    }

    @Transactional(readOnly = true)
    public LoginResponse selectRole(
            String roleSelectionToken,
            Integer maTK
    ) {

        // 1. Kiểm tra JWT tạm
        if (!jwtService.isTokenValid(roleSelectionToken)) {
            throw new BusinessException(
                    "Phiên chọn vai trò đã hết hạn"
            );
        }

        // 2. Token phải là token dùng cho chọn vai trò
        if (!jwtService.isRoleSelectionToken(roleSelectionToken)) {
            throw new BusinessException(
                    "Token không hợp lệ"
            );
        }

        // 3. Lấy maND từ token
        Integer maND = jwtService.extractMaND(
                roleSelectionToken
        );

        // 4. Tìm TaiKhoan
        TaiKhoan taiKhoan = taiKhoanRepository.findById(maTK)
                .orElseThrow(() ->
                        new BusinessException(
                                "Tài khoản không tồn tại"
                        )
                );

        // 5. Kiểm tra TaiKhoan có thuộc NguoiDung này không
        if (!taiKhoan.getNguoiDung()
                .getMaND()
                .equals(maND)) {

            throw new BusinessException(
                    "Tài khoản không thuộc người dùng này"
            );
        }

        // 6. Kiểm tra trạng thái
        if (!TrangThaiTaiKhoan.HOAT_DONG.name().equals(
                taiKhoan.getTrangThai()
        )) {

            throw new BusinessException(
                    "Tài khoản đang bị khóa hoặc không hoạt động"
            );
        }

        // 7. Tạo JWT chính thức
        String token = jwtService.generateToken(
                maND,
                taiKhoan.getMaTK(),
                taiKhoan.getVaiTro()
        );

        return LoginResponse.builder()
                .maND(maND)
                .maTK(taiKhoan.getMaTK())
                .vaiTro(taiKhoan.getVaiTro())
                .token(token)
                .roleSelectionToken(null)
                .canChonVaiTro(false)
                .taiKhoans(
                        toTaiKhoanResponse(
                                List.of(taiKhoan)
                        )
                )
                .build();
    }
}