package com.oldbook.repository.identity;

import com.oldbook.entity.identity.DiaChi;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DiaChiRepository extends JpaRepository<DiaChi, Integer> {

    Optional<DiaChi> findByMaDiaChiAndNguoiDung_MaND(
            Integer maDiaChi,
            Integer maND
    );

    List<DiaChi> findAllByNguoiDung_MaNDOrderByLaMacDinhDescMaDiaChiAsc(Integer maND);
}
