package com.oldbook.repository.order;

import com.oldbook.entity.order.DonHang;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DonHangRepository extends JpaRepository<DonHang, Integer> {

    @Query("""
            select distinct dh from DonHang dh
            left join fetch dh.diaChiGiao
            where dh.nguoiMua.maND = :maND
            order by dh.ngayTao desc, dh.maDH desc
            """)
    List<DonHang> findAllByNguoiMua(@Param("maND") Integer maND);

    /** Gỡ liên kết địa chỉ khỏi các đơn cũ (thông tin giao hàng vẫn còn trong bản snapshot của đơn). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update DonHang dh set dh.diaChiGiao = null where dh.diaChiGiao.maDiaChi = :maDiaChi")
    int clearDiaChiGiao(@Param("maDiaChi") Integer maDiaChi);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select dh from DonHang dh where dh.maDH = :maDH")
    Optional<DonHang> findByIdForUpdate(@Param("maDH") Integer maDH);
}
