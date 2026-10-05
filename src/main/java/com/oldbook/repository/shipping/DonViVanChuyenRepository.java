package com.oldbook.repository.shipping;

import com.oldbook.entity.shipping.DonViVanChuyen;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DonViVanChuyenRepository extends JpaRepository<DonViVanChuyen, Integer> {

    List<DonViVanChuyen> findAllByTrangThaiOrderByTenDonViAsc(String trangThai);

    List<DonViVanChuyen> findAllByOrderByMaDVVCAsc();

    Optional<DonViVanChuyen> findByMaDVVCAndTrangThai(Integer maDVVC, String trangThai);

    // Mã đơn vị vận chuyển (ma_code) là khóa định danh công khai: dùng trong URL trang /shipping/{maCode}
    // và các API /api/shipping/{maCode}/... Luôn lưu dạng IN HOA (xem DonViVanChuyenResolver.normalize).
    Optional<DonViVanChuyen> findByMaCode(String maCode);

    boolean existsByMaCode(String maCode);

}
