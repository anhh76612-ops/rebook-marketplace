package com.oldbook.repository.order;

import com.oldbook.entity.order.DonHang;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DonHangRepository extends JpaRepository<DonHang, Integer> {

    @Query("""
            select distinct dh from DonHang dh
            left join fetch dh.diaChiGiao
            where dh.nguoiMua.maND = :maND
            order by dh.ngayTao desc, dh.maDH desc
            """)
    List<DonHang> findAllByNguoiMua(@Param("maND") Integer maND);
}
