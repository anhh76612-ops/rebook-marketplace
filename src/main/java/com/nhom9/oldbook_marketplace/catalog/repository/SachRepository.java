package com.nhom9.oldbook_marketplace.catalog.repository;

import com.nhom9.oldbook_marketplace.catalog.entity.Sach;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SachRepository extends JpaRepository<Sach, Integer>, JpaSpecificationExecutor<Sach> {
    @Override
    @EntityGraph(attributePaths = {"cuaHang", "danhMuc"})
    Page<Sach> findAll(Specification<Sach> specification, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"cuaHang", "danhMuc"})
    Optional<Sach> findOne(Specification<Sach> specification);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Sach s where s.maSach = :maSach")
    Optional<Sach> findByIdForUpdate(@Param("maSach") Integer maSach);
}
