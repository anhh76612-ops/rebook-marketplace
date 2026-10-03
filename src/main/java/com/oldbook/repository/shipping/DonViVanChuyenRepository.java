package com.oldbook.repository.shipping;

import com.oldbook.entity.shipping.DonViVanChuyen;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DonViVanChuyenRepository extends JpaRepository<DonViVanChuyen, Integer> {

    List<DonViVanChuyen> findAllByTrangThaiOrderByTenDonViAsc(String trangThai);

    Optional<DonViVanChuyen> findByMaDVVCAndTrangThai(Integer maDVVC, String trangThai);

    boolean existsByMaCode(String maCode);

}
