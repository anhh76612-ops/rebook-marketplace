package com.nhom9.oldbook_marketplace.repository;

import com.nhom9.oldbook_marketplace.entity.catalog.Sach;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SachRepository extends JpaRepository<Sach, Integer> {
}