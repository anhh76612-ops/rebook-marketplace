package com.oldbook.service.order;

import com.oldbook.constant.auth.VaiTro;
import com.oldbook.entity.catalog.CuaHang;
import com.oldbook.entity.identity.NguoiDung;
import com.oldbook.entity.order.DonHang;
import com.oldbook.entity.order.DonHangCuaHang;
import com.oldbook.entity.shipping.DonViVanChuyen;
import com.oldbook.repository.identity.TaiKhoanRepository;
import com.oldbook.service.shipping.DonViVanChuyenResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Báo cho khách mua và chủ cửa hàng biết một đơn hàng con vừa thay đổi, để trang theo dõi đơn của họ
 * tự cập nhật. Các service đơn hàng chỉ gọi một dòng vào đây, không tự lo chuyện socket.
 *
 * Phải gọi bên trong giao dịch đang sửa đơn: sự kiện chỉ được đẩy đi sau khi giao dịch đó commit,
 * nên trang tải lại luôn thấy dữ liệu mới và không bao giờ nhận tin về một thay đổi bị rollback.
 */
@Service
@RequiredArgsConstructor
public class DonHangRealtimeService {

    private final TaiKhoanRepository taiKhoanRepository;
    private final ApplicationEventPublisher eventPublisher;

    public void baoThayDoi(DonHangCuaHang order) {
        if (order == null || order.getMaDHCH() == null) {
            return;
        }

        DonHang donHang = order.getDonHang();
        CuaHang cuaHang = order.getCuaHang();

        // Thông báo gắn với tài khoản theo vai trò: khách và chủ cửa hàng là hai tài khoản khác nhau
        // ngay cả khi cùng một người, nên mỗi vai trò chỉ nhận tin ở trang của vai trò đó.
        Set<Integer> nguoiNhan = new LinkedHashSet<>();
        if (donHang != null) {
            themTaiKhoan(nguoiNhan, donHang.getNguoiMua(), VaiTro.KHACH_HANG);
        }
        if (cuaHang != null) {
            themTaiKhoan(nguoiNhan, cuaHang.getChuShop(), VaiTro.CHU_CUA_HANG);
        }
        if (nguoiNhan.isEmpty()) {
            return;
        }

        eventPublisher.publishEvent(new DonHangThayDoiEvent(
                donHang == null ? null : donHang.getMaDH(),
                order.getMaDHCH(),
                Set.copyOf(nguoiNhan)
        ));
    }

    /**
     * Báo cho cổng của đơn vị vận chuyển biết danh sách đơn của hãng vừa đổi (cửa hàng xác nhận hoặc hủy đơn).
     * Cổng không đăng nhập nên có kênh riêng theo mã hãng. Cũng phải gọi bên trong giao dịch đang sửa đơn.
     */
    public void baoDonViVanChuyen(DonHangCuaHang order) {
        DonViVanChuyen carrier = order == null ? null : order.getDonViVanChuyen();
        String maCode = carrier == null ? null : DonViVanChuyenResolver.normalize(carrier.getMaCode());
        if (maCode != null) {
            eventPublisher.publishEvent(new DonViVanChuyenThayDoiEvent(maCode));
        }
    }

    private void themTaiKhoan(Set<Integer> nguoiNhan, NguoiDung nguoiDung, VaiTro vaiTro) {
        if (nguoiDung == null) {
            return;
        }
        taiKhoanRepository
                .findByNguoiDung_MaNDAndVaiTro(nguoiDung.getMaND(), vaiTro.name())
                .ifPresent(taiKhoan -> nguoiNhan.add(taiKhoan.getMaTK()));
    }
}
