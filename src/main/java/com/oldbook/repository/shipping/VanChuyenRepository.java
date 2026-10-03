package com.oldbook.repository.shipping;

import com.oldbook.entity.shipping.VanChuyen;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface VanChuyenRepository extends JpaRepository<VanChuyen, Integer> {
    Optional<VanChuyen> findByDonHangCuaHang_MaDHCH(Integer maDHCH);
    boolean existsByMaVanDon(String maVanDon);
}
