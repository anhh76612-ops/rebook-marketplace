package com.oldbook.repository.order;

import com.oldbook.entity.order.YeuCauHuyDon;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface YeuCauHuyDonRepository extends JpaRepository<YeuCauHuyDon, Integer> {

    List<YeuCauHuyDon> findAllByDonHangCuaHang_MaDHCHIn(Collection<Integer> maDHCHs);

    boolean existsByDonHangCuaHang_MaDHCH(Integer maDHCH);

    boolean existsByDonHangCuaHang_MaDHCHAndTrangThaiYeuCau(Integer maDHCH, String trangThaiYeuCau);

    Optional<YeuCauHuyDon> findFirstByDonHangCuaHang_MaDHCHAndTrangThaiYeuCau(Integer maDHCH, String trangThaiYeuCau);
}
