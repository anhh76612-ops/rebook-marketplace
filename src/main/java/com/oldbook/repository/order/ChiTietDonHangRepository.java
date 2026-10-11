package com.oldbook.repository.order;

import com.oldbook.entity.order.ChiTietDonHang;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChiTietDonHangRepository extends JpaRepository<ChiTietDonHang, Integer> {

    List<ChiTietDonHang> findAllByDonHangCuaHang_MaDHCHOrderByMaCTDHAsc(Integer maDHCH);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select ct from ChiTietDonHang ct where ct.maCTDH = :maCTDH")
    Optional<ChiTietDonHang> findByIdForUpdate(@Param("maCTDH") Integer maCTDH);
}
