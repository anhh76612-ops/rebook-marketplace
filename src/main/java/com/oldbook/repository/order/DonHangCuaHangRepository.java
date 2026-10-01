package com.oldbook.repository.order;

import com.oldbook.entity.order.DonHangCuaHang;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DonHangCuaHangRepository extends JpaRepository<DonHangCuaHang, Integer> {

    List<DonHangCuaHang> findAllByDonHang_MaDHOrderByMaDHCHAsc(Integer maDH);
}
