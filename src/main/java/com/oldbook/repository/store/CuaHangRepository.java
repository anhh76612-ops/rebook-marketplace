package com.oldbook.repository.store;

import com.oldbook.entity.catalog.CuaHang;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CuaHangRepository extends JpaRepository<CuaHang, Integer>, JpaSpecificationExecutor<CuaHang> {
    Optional<CuaHang> findByChuShop_MaND(Integer maND);

    boolean existsByTenCuaHangIgnoreCase(String tenCuaHang);

    boolean existsByTenCuaHangIgnoreCaseAndMaCHNot(String tenCuaHang, Integer maCH);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CuaHang c where c.maCH = :maCH")
    Optional<CuaHang> findByIdForUpdate(@Param("maCH") Integer maCH);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CuaHang c where c.chuShop.maND = :maND")
    Optional<CuaHang> findByChuShopMaNDForUpdate(@Param("maND") Integer maND);
}
