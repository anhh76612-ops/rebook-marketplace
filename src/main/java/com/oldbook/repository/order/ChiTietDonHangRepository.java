package com.oldbook.repository.order;

import com.oldbook.entity.order.ChiTietDonHang;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChiTietDonHangRepository extends JpaRepository<ChiTietDonHang, Integer> {

    List<ChiTietDonHang> findAllByDonHangCuaHang_MaDHCHOrderByMaCTDHAsc(Integer maDHCH);
}
