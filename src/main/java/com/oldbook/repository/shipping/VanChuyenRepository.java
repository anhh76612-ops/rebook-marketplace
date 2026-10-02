package com.oldbook.repository.shipping;

import com.oldbook.entity.shipping.VanChuyen;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface VanChuyenRepository extends JpaRepository<VanChuyen, Integer> {

    Optional<VanChuyen> findByDonHangCuaHang_MaDHCH(Integer maDHCH);

    List<VanChuyen> findAllByDonHangCuaHang_MaDHCHIn(Collection<Integer> maDHCHs);

    boolean existsByMaVanDon(String maVanDon);
}
