package com.oldbook.service.shipping;

import com.oldbook.constant.order.TrangThaiDonHang;
import com.oldbook.constant.shipping.TrangThaiVanChuyen;
import com.oldbook.entity.order.DonHangCuaHang;
import com.oldbook.entity.shipping.VanChuyen;
import com.oldbook.exception.common.BusinessException;
import com.oldbook.repository.order.DonHangCuaHangRepository;
import com.oldbook.repository.shipping.VanChuyenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Nơi duy nhất đổi trạng thái vận đơn, dùng chung cho cửa hàng và đơn vị vận chuyển
 * để hai bên cùng một state machine, cùng cách đồng bộ sang đơn hàng con.
 * Người gọi phải đã khóa đơn con (DonHangCuaHangRepository.findByIdForUpdate) trong cùng giao dịch.
 */
@Component
@RequiredArgsConstructor
public class VanChuyenTrangThaiHandler {

    private final VanChuyenRepository vanChuyenRepository;
    private final DonHangCuaHangRepository donHangCuaHangRepository;

    public TrangThaiVanChuyen parse(String value) {
        TrangThaiVanChuyen status = TrangThaiVanChuyen.from(value);
        if (status == null) {
            throw new BusinessException("Trạng thái giao hàng không hợp lệ: " + value);
        }
        return status;
    }

    /**
     * @param ghiChu      null = giữ nguyên; chuỗi rỗng = xóa ghi chú
     * @param tienHoanTra chỉ có tác dụng khi chuyển sang HOAN_TRA
     */
    public void apply(DonHangCuaHang order, VanChuyen vanChuyen, TrangThaiVanChuyen next,
                      String ghiChu, BigDecimal tienHoanTra) {
        TrangThaiVanChuyen current = parse(vanChuyen.getTrangThaiVanChuyen());

        if (!current.canMoveTo(next)) {
            throw new BusinessException(
                    "Không thể chuyển trạng thái giao hàng từ \""
                            + current.getTenHienThi() + "\" sang \"" + next.getTenHienThi() + "\"");
        }

        vanChuyen.setTrangThaiVanChuyen(next.name());

        // Số lần giao thất bại do hệ thống tự đếm, không nhận từ client.
        if (next == TrangThaiVanChuyen.GIAO_THAT_BAI) {
            int soLan = vanChuyen.getSoLanGiaoThatBai() == null ? 0 : vanChuyen.getSoLanGiaoThatBai();
            vanChuyen.setSoLanGiaoThatBai(soLan + 1);
        }

        if (ghiChu != null) {
            String note = ghiChu.trim();
            vanChuyen.setGhiChuGiaoHang(note.isEmpty() ? null : note);
        }

        // Đồng bộ trạng thái đơn hàng con để khách và cửa hàng thấy đúng trạng thái.
        TrangThaiDonHang orderStatus = next.donHangTuongUng();
        if (orderStatus != null) {
            order.setTrangThaiDonHang(orderStatus.name());
        }
        if (next == TrangThaiVanChuyen.HOAN_TRA && tienHoanTra != null) {
            order.setTienHoanTra(tienHoanTra);
        }

        vanChuyenRepository.save(vanChuyen);
        donHangCuaHangRepository.save(order);
    }
}
