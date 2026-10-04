package com.oldbook.repository.shipping;

import com.oldbook.entity.shipping.VanChuyen;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface VanChuyenRepository extends JpaRepository<VanChuyen, Integer> {

    Optional<VanChuyen> findByDonHangCuaHang_MaDHCH(Integer maDHCH);

    List<VanChuyen> findAllByDonHangCuaHang_MaDHCHIn(Collection<Integer> maDHCHs);

    // Toàn bộ vận đơn thuộc về MỘT đơn vị vận chuyển (mỗi trang /shipping/{maCode} chỉ thấy đơn của mình).
    @EntityGraph(attributePaths = {"donHangCuaHang", "donHangCuaHang.donHang", "donViVanChuyen"})
    List<VanChuyen> findAllByDonViVanChuyen_MaDVVCOrderByMaVanChuyenDesc(Integer maDVVC);

    @EntityGraph(attributePaths = {"donHangCuaHang", "donHangCuaHang.donHang", "donViVanChuyen"})
    Optional<VanChuyen> findByMaVanChuyenAndDonViVanChuyen_MaDVVC(Integer maVanChuyen, Integer maDVVC);

    // Chỉ lấy mã đơn con (không nạp VanChuyen) để khóa đơn con trước rồi mới đọc vận đơn.
    // Có điều kiện theo đơn vị vận chuyển: hãng khác gọi API sẽ coi như vận đơn không tồn tại.
    @Query("select v.donHangCuaHang.maDHCH from VanChuyen v "
            + "where v.maVanChuyen = :id and v.donViVanChuyen.maDVVC = :maDVVC")
    Optional<Integer> findMaDHCHByIdAndDonVi(@Param("id") Integer id, @Param("maDVVC") Integer maDVVC);

    boolean existsByMaVanDon(String maVanDon);
}
