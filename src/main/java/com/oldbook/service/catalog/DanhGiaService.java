package com.oldbook.service.catalog;

import com.oldbook.dto.catalog.DanhGiaResponse;
import com.oldbook.repository.system.DanhGiaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class DanhGiaService {
    private final DanhGiaRepository danhGiaRepository;

    public DanhGiaService(DanhGiaRepository danhGiaRepository) {
        this.danhGiaRepository = danhGiaRepository;
    }

    public List<DanhGiaResponse> getByBook(Integer maSach) {
        return danhGiaRepository.findBySach(maSach).stream()
                .map(review -> {
                    String hoTen = review.getNguoiDung().getHoTen();
                    return new DanhGiaResponse(review.getMaDanhGia(),
                            hoTen == null || hoTen.isBlank() ? "Khách hàng" : hoTen,
                            review.getSoSao(), review.getBinhLuan(), review.getNgayDanhGia());
                })
                .toList();
    }
}
