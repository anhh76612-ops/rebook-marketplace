package com.oldbook.repository.payment;

import com.oldbook.entity.payment.ThanhToan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ThanhToanRepository extends JpaRepository<ThanhToan, Integer> {

    boolean existsByDonHangCuaHang_MaDHCHAndLoaiGiaoDich(Integer maDHCH, String loaiGiaoDich);
}
