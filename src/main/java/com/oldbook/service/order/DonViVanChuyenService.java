package com.oldbook.service.order;

import com.oldbook.constant.shipping.TrangThaiDonViVanChuyen;
import com.oldbook.dto.shipping.ShippingCarrierResponse;
import com.oldbook.repository.shipping.DonViVanChuyenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DonViVanChuyenService {

    private final DonViVanChuyenRepository donViVanChuyenRepository;

    /** Danh sách đơn vị vận chuyển dùng chung toàn sàn, áp dụng cho mọi cửa hàng. */
    @Transactional(readOnly = true)
    public List<ShippingCarrierResponse> getActiveCarriers() {
        return donViVanChuyenRepository
                .findAllByTrangThaiOrderByTenDonViAsc(TrangThaiDonViVanChuyen.HOAT_DONG.name())
                .stream()
                .map(c -> ShippingCarrierResponse.builder()
                        .maDVVC(c.getMaDVVC())
                        .maCode(c.getMaCode())
                        .tenDonVi(c.getTenDonVi())
                        .phiVanChuyen(c.getPhiVanChuyen())
                        .thoiGianGiaoDuKien(c.getThoiGianGiaoDuKien())
                        .build())
                .toList();
    }
}
