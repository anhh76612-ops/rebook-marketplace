package com.oldbook.repository.shipping;

import com.oldbook.entity.shipping.LichSuVanChuyen;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface LichSuVanChuyenRepository extends JpaRepository<LichSuVanChuyen, Integer> {

    List<LichSuVanChuyen> findAllByVanChuyen_MaVanChuyenInOrderByThoiGianAscMaLichSuAsc(
            Collection<Integer> maVanChuyens);
}
