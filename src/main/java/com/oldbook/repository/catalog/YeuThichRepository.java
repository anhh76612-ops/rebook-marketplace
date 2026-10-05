package com.oldbook.repository.catalog;

import com.oldbook.entity.catalog.UserBookKey;
import com.oldbook.entity.catalog.YeuThich;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface YeuThichRepository extends JpaRepository<YeuThich, UserBookKey> {

    @Query("""
            select y from YeuThich y
            join fetch y.sach s
            join fetch s.cuaHang ch
            join fetch s.danhMuc dm
            where y.id.maND = :maND
              and s.trangThaiDuyet = :duyet
              and s.trangThaiBan = :ban
              and ch.trangThaiDuyet = :duyet
            order by y.ngayThem desc, y.id.maSach desc
            """)
    List<YeuThich> findCongKhaiByMaND(@Param("maND") Integer maND,
                                    @Param("duyet") String duyet,
                                    @Param("ban") String ban);
}
