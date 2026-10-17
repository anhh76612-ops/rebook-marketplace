package com.oldbook.repository.payment;

import com.oldbook.entity.payment.GiaoDichNganHang;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GiaoDichNganHangRepository extends JpaRepository<GiaoDichNganHang, Integer> {

    Optional<GiaoDichNganHang> findByMaGiaoDichCong(String maGiaoDichCong);

    Page<GiaoDichNganHang> findAllByOrderByMaGiaoDichDesc(Pageable pageable);

    Optional<GiaoDichNganHang> findFirstByPhien_MaPhienAndKetQuaOrderByMaGiaoDichDesc(Integer maPhien, String ketQua);
}
