package com.oldbook.service.shipping;

import com.oldbook.constant.shipping.TrangThaiDonViVanChuyen;
import com.oldbook.entity.shipping.DonViVanChuyen;
import com.oldbook.exception.common.BusinessException;
import com.oldbook.repository.shipping.DonViVanChuyenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Xác định đơn vị vận chuyển từ mã (ma_code) nằm trên URL.
 * Đơn vị vận chuyển không còn đăng nhập bằng tài khoản: ai có link /shipping/{maCode} thì vào được trang
 * của hãng đó, và mọi API vận chuyển đều đi kèm {maCode} để chỉ thao tác trên đơn của đúng hãng.
 */
@Component
@RequiredArgsConstructor
public class DonViVanChuyenResolver {

    /** Chỉ cho phép ký tự an toàn trên URL: chữ, số, gạch ngang, gạch dưới. */
    public static final Pattern MA_CODE_PATTERN = Pattern.compile("^[A-Z0-9_-]{2,30}$");

    private final DonViVanChuyenRepository donViRepository;

    /** Chuẩn hóa mã về dạng IN HOA; trả null nếu sai định dạng. /shipping/ghn và /shipping/GHN là một. */
    public static String normalize(String maCode) {
        if (maCode == null) {
            return null;
        }
        String code = maCode.trim().toUpperCase(Locale.ROOT);
        return MA_CODE_PATTERN.matcher(code).matches() ? code : null;
    }

    public Optional<DonViVanChuyen> find(String maCode) {
        String code = normalize(maCode);
        return code == null ? Optional.empty() : donViRepository.findByMaCode(code);
    }

    public DonViVanChuyen require(String maCode) {
        return find(maCode).orElseThrow(() -> new BusinessException("Đơn vị vận chuyển không tồn tại"));
    }

    /** Dùng cho các API nghiệp vụ: hãng ngừng hoạt động thì không được tiếp nhận/cập nhật đơn. */
    public DonViVanChuyen requireActive(String maCode) {
        DonViVanChuyen dvvc = require(maCode);
        if (!isActive(dvvc)) {
            throw new BusinessException("Đơn vị vận chuyển đang ngừng hoạt động");
        }
        return dvvc;
    }

    public static boolean isActive(DonViVanChuyen dvvc) {
        return TrangThaiDonViVanChuyen.HOAT_DONG.name().equalsIgnoreCase(dvvc.getTrangThai());
    }
}
