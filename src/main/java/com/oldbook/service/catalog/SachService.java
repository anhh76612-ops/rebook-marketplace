package com.oldbook.service.catalog;

import org.springframework.stereotype.Service;

import com.oldbook.entity.catalog.Sach;
import com.oldbook.repository.catalog.SachRepository;

import java.util.List;

@Service
public class SachService {

    private final SachRepository sachRepository;

    public SachService(SachRepository sachRepository) {
        this.sachRepository = sachRepository;
    }

    public List<Sach> getAll() {
        return sachRepository.findAll();
    }
}