package com.oldbook.repository.catalog;

import com.oldbook.entity.catalog.Sach;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SachRepository extends JpaRepository<Sach, Integer> {

    @Query("""
            select s from Sach s
            join fetch s.cuaHang ch
            join fetch s.danhMuc dm
            where s.trangThaiDuyet = :duyet
              and s.trangThaiBan = :ban
              and ch.trangThaiDuyet = :duyet
            order by s.ngayTao desc, s.maSach desc
            """)
    List<Sach> findCongKhai(
            @Param("duyet") String duyet,
            @Param("ban") String ban
    );
}
