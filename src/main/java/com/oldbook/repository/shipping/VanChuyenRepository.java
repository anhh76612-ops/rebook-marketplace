package com.oldbook.repository.shipping;

import com.oldbook.entity.shipping.VanChuyen;
import org.springframework.data.domain.Pageable;
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

    boolean existsByMaVanDon(String maVanDon);

    Optional<VanChuyen> findByMaVanDon(String maVanDon);

    /** Mã đơn hàng con của một mã vận đơn (dùng để khóa đơn trước khi đọc lại vận đơn). */
    @Query("select v.donHangCuaHang.maDHCH from VanChuyen v where v.maVanDon = :maVanDon")
    Optional<Integer> findMaDHCHByMaVanDon(@Param("maVanDon") String maVanDon);

    /** Các vận đơn chưa kết thúc (phân trang theo khóa) để đồng bộ định kỳ. */
    @Query("""
            select v from VanChuyen v
            join fetch v.donViVanChuyen
            where v.trangThaiVanChuyen not in :finalStatuses
              and v.maVanChuyen > :afterId
            order by v.maVanChuyen asc
            """)
    List<VanChuyen> findActiveAfter(@Param("finalStatuses") Collection<String> finalStatuses,
                                    @Param("afterId") Integer afterId,
                                    Pageable pageable);

    @Query("""
            select v from VanChuyen v
            join fetch v.donViVanChuyen
            where v.donHangCuaHang.maDHCH = :maDHCH
            """)
    Optional<VanChuyen> findWithCarrierByMaDHCH(@Param("maDHCH") Integer maDHCH);
}
