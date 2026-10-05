package com.oldbook.repository.order;

import com.oldbook.entity.order.DonHangCuaHang;


import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DonHangCuaHangRepository extends JpaRepository<DonHangCuaHang, Integer> {

    List<DonHangCuaHang> findAllByDonHang_MaDHOrderByMaDHCHAsc(Integer maDH);

    List<DonHangCuaHang> findAllByCuaHang_MaCHOrderByMaDHCHDesc(Integer maCH);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from DonHangCuaHang d where d.maDHCH = :maDHCH")
    Optional<DonHangCuaHang> findByIdForUpdate(@Param("maDHCH") Integer maDHCH);

}
