package com.oldbook.repository.identity;

import com.oldbook.entity.identity.DiaChi;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DiaChiRepository extends JpaRepository<DiaChi, Integer> {

    List<DiaChi> findAllByNguoiDung_MaNDOrderByLaMacDinhDescMaDiaChiDesc(
            Integer maND
    );

    Optional<DiaChi> findByMaDiaChiAndNguoiDung_MaND(
            Integer maDiaChi,
            Integer maND
    );
}