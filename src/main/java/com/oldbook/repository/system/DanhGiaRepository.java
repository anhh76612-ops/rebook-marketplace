package com.oldbook.repository.system;

import com.oldbook.entity.system.DanhGia;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DanhGiaRepository extends JpaRepository<DanhGia, Integer> {
    @EntityGraph(attributePaths = {"nguoiDung", "media"})
    @Query("""
            select dg from DanhGia dg
            where dg.chiTietDonHang.sach.maSach = :maSach
              and dg.soSao between 1 and 5
            order by dg.ngayDanhGia desc, dg.maDanhGia desc
            """)
    List<DanhGia> findBySach(@Param("maSach") Integer maSach);

    boolean existsByChiTietDonHang_MaCTDH(Integer maCTDH);

    @EntityGraph(attributePaths = {"chiTietDonHang", "nguoiDung", "media"})
    List<DanhGia> findAllByChiTietDonHang_DonHangCuaHang_MaDHCHIn(List<Integer> maDHCH);
}
