package com.oldbook.config.shipping;

import com.oldbook.constant.shipping.TrangThaiDonViVanChuyen;
import com.oldbook.entity.shipping.DonViVanChuyen;
import com.oldbook.repository.shipping.DonViVanChuyenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

// Tạo sẵn danh sách đơn vị vận chuyển dùng chung toàn sàn nếu chưa có (theo ma_code)
@Component
@RequiredArgsConstructor
public class ShippingDataInitializer implements ApplicationRunner {

    private final DonViVanChuyenRepository donViVanChuyenRepository;

    @Override
    public void run(ApplicationArguments args) {
        List<DonViVanChuyen> defaults = List.of(
                carrier("GHN", "Giao Hàng Nhanh", "25000", 2),
                carrier("GHTK", "Giao Hàng Tiết Kiệm", "20000", 3),
                carrier("VTP", "Viettel Post", "22000", 3),
                carrier("JT", "J&T Express", "18000", 4)
        );

        for (DonViVanChuyen carrier : defaults) {
            if (!donViVanChuyenRepository.existsByMaCode(carrier.getMaCode())) {
                donViVanChuyenRepository.save(carrier);
            }
        }
    }

    private DonViVanChuyen carrier(String maCode, String ten, String phi, int soNgay) {
        return DonViVanChuyen.builder()
                .maCode(maCode)
                .tenDonVi(ten)
                .phiVanChuyen(new BigDecimal(phi))
                .thoiGianGiaoDuKien(soNgay)
                .trangThai(TrangThaiDonViVanChuyen.HOAT_DONG.name())
                .build();
    }
}
