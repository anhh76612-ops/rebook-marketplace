package com.oldbook.service.store;

import com.oldbook.filter.auth.JwtAuthenticationFilter.AuthenticatedUserDetails;
import com.oldbook.constant.catalog.TrangThaiDuyet;
import com.oldbook.dto.catalog.PageResponse;
import com.oldbook.dto.catalog.SachResponse;
import com.oldbook.entity.catalog.CuaHang;
import com.oldbook.entity.catalog.Sach;
import com.oldbook.repository.catalog.SachRepository;
import com.oldbook.service.catalog.SachMapper;
import com.oldbook.exception.common.BusinessException;
import com.oldbook.repository.identity.TaiKhoanRepository;
import com.oldbook.dto.store.CuaHangResponse;
import com.oldbook.dto.store.KiemDuyetSearchRequest;
import com.oldbook.dto.store.TuChoiRequest;
import com.oldbook.repository.store.CuaHangRepository;
import com.oldbook.security.store.StoreAccess;
import com.oldbook.entity.system.NhatKyHeThong;
import com.oldbook.repository.system.NhatKyHeThongRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class KiemDuyetService {

    private final CuaHangRepository cuaHangRepository;
    private final SachRepository sachRepository;
    private final TaiKhoanRepository taiKhoanRepository;
    private final NhatKyHeThongRepository nhatKyHeThongRepository;
    private final StoreAccess storeAccess;
    private final EntityManager entityManager;

    public PageResponse<CuaHangResponse> timCuaHang(KiemDuyetSearchRequest request) {
        storeAccess.requireModerator();
        validateSearch(request);
        Specification<CuaHang> specification = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("trangThaiDuyet"), request.getTrangThaiDuyet()));
            if (request.getMaCH() != null) {
                predicates.add(cb.equal(root.get("maCH"), request.getMaCH()));
            }
            if (hasKeyword(request)) {
                predicates.add(cb.like(cb.lower(root.get("tenCuaHang")), likeKeyword(request), '\\'));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return PageResponse.of(cuaHangRepository.findAll(specification, pageRequest(request, "maCH"))
                .map(this::storeResponse));
    }

    public PageResponse<SachResponse> timSach(KiemDuyetSearchRequest request) {
        storeAccess.requireModerator();
        validateSearch(request);
        Specification<Sach> specification = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("trangThaiDuyet"), request.getTrangThaiDuyet()));
            if (request.getMaCH() != null) {
                predicates.add(cb.equal(root.get("cuaHang").get("maCH"), request.getMaCH()));
            }
            if (hasKeyword(request)) {
                String keyword = likeKeyword(request);
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("tenSach")), keyword, '\\'),
                        cb.like(cb.lower(root.get("tacGia")), keyword, '\\')));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return PageResponse.of(sachRepository.findAll(specification, pageRequest(request, "maSach"))
                .map(SachMapper::toResponse));
    }

    public CuaHangResponse chiTietCuaHang(Integer maCH) {
        storeAccess.requireModerator();
        validateId(maCH, "Mã cửa hàng");
        return storeResponse(cuaHangRepository.findById(maCH)
                .orElseThrow(() -> new BusinessException("Không tìm thấy cửa hàng")));
    }

    public SachResponse chiTietSach(Integer maSach) {
        storeAccess.requireModerator();
        validateId(maSach, "Mã sách");
        return SachMapper.toResponse(sachRepository.findById(maSach)
                .orElseThrow(() -> new BusinessException("Không tìm thấy sách")));
    }

    @Transactional
    public CuaHangResponse duyetCuaHang(Integer maCH) {
        AuthenticatedUserDetails actor = storeAccess.requireModerator();
        CuaHang cuaHang = lockStore(maCH);
        requirePending(cuaHang.getTrangThaiDuyet(), "Cửa hàng");
        cuaHang.setTrangThaiDuyet(TrangThaiDuyet.DA_DUYET.name());
        cuaHangRepository.save(cuaHang);
        audit(actor, "DUYET_CUA_HANG", "CUA_HANG", maCH, "Phê duyệt cửa hàng");
        return CuaHangResponse.from(cuaHang);
    }

    @Transactional
    public CuaHangResponse tuChoiCuaHang(Integer maCH, TuChoiRequest request) {
        AuthenticatedUserDetails actor = storeAccess.requireModerator();
        String lyDo = rejectionReason(request);
        CuaHang cuaHang = lockStore(maCH);
        requirePending(cuaHang.getTrangThaiDuyet(), "Cửa hàng");
        cuaHang.setTrangThaiDuyet(TrangThaiDuyet.TU_CHOI.name());
        cuaHangRepository.save(cuaHang);
        // Cửa hàng chưa có cột lý do; nhật ký lưu lý do trong cùng giao dịch.
        audit(actor, "TU_CHOI_CUA_HANG", "CUA_HANG", maCH, lyDo);
        return CuaHangResponse.from(cuaHang).withLyDoTuChoi(lyDo);
    }

    @Transactional
    public SachResponse duyetSach(Integer maSach) {
        AuthenticatedUserDetails actor = storeAccess.requireModerator();
        Sach sach = lockBookAndStore(maSach);
        requirePending(sach.getTrangThaiDuyet(), "Sách");
        if (!TrangThaiDuyet.DA_DUYET.name().equals(sach.getCuaHang().getTrangThaiDuyet())) {
            throw new BusinessException("Cửa hàng phải được phê duyệt trước khi duyệt sách");
        }
        sach.setTrangThaiDuyet(TrangThaiDuyet.DA_DUYET.name());
        sach.setLyDoTuChoi(null);
        sachRepository.save(sach);
        audit(actor, "DUYET_SACH", "SACH", maSach, "Phê duyệt sách");
        return SachMapper.toResponse(sach);
    }

    @Transactional
    public SachResponse tuChoiSach(Integer maSach, TuChoiRequest request) {
        AuthenticatedUserDetails actor = storeAccess.requireModerator();
        String lyDo = rejectionReason(request);
        Sach sach = lockBookAndStore(maSach);
        requirePending(sach.getTrangThaiDuyet(), "Sách");
        sach.setTrangThaiDuyet(TrangThaiDuyet.TU_CHOI.name());
        sach.setLyDoTuChoi(lyDo);
        sachRepository.save(sach);
        audit(actor, "TU_CHOI_SACH", "SACH", maSach, lyDo);
        return SachMapper.toResponse(sach);
    }

    private CuaHang lockStore(Integer maCH) {
        validateId(maCH, "Mã cửa hàng");
        return cuaHangRepository.findByIdForUpdate(maCH)
                .orElseThrow(() -> new BusinessException("Không tìm thấy cửa hàng"));
    }

    private Sach lockBookAndStore(Integer maSach) {
        validateId(maSach, "Mã sách");
        Sach current = sachRepository.findById(maSach)
                .orElseThrow(() -> new BusinessException("Không tìm thấy sách"));
        if (current.getCuaHang() == null) {
            throw new BusinessException("Sách không thuộc cửa hàng hợp lệ");
        }
        Integer maCH = current.getCuaHang().getMaCH();
        // Thứ tự khóa thống nhất với quản lý sách/tồn kho: cửa hàng trước, sách sau.
        CuaHang cuaHang = lockStore(maCH);
        Sach locked = sachRepository.findByIdForUpdate(maSach)
                .orElseThrow(() -> new BusinessException("Không tìm thấy sách"));
        // Lần đọc tìm cửa hàng có thể đã đưa sách vào persistence context trước khi chờ khóa.
        entityManager.refresh(locked);
        if (locked.getCuaHang() == null || !maCH.equals(locked.getCuaHang().getMaCH())) {
            throw new BusinessException("Cửa hàng của sách đã thay đổi, vui lòng thử lại");
        }
        // Dùng chính trạng thái của cửa hàng đã được khóa để kiểm tra điều kiện duyệt.
        locked.setCuaHang(cuaHang);
        return locked;
    }

    private void audit(AuthenticatedUserDetails actor, String hanhDong,
                       String loaiDoiTuong, Integer maDoiTuong, String moTa) {
        nhatKyHeThongRepository.save(NhatKyHeThong.builder()
                .taiKhoan(taiKhoanRepository.getReferenceById(actor.maTK()))
                .hanhDong(hanhDong)
                .loaiDoiTuong(loaiDoiTuong)
                .maDoiTuong(maDoiTuong)
                .moTa(moTa)
                .build());
    }

    private CuaHangResponse storeResponse(CuaHang cuaHang) {
        CuaHangResponse response = CuaHangResponse.from(cuaHang);
        if (!TrangThaiDuyet.TU_CHOI.name().equals(cuaHang.getTrangThaiDuyet())) {
            return response;
        }
        String lyDo = nhatKyHeThongRepository
                .findFirstByLoaiDoiTuongAndMaDoiTuongAndHanhDongOrderByThoiGianDescMaNhatKyDesc(
                        "CUA_HANG", cuaHang.getMaCH(), "TU_CHOI_CUA_HANG")
                .map(NhatKyHeThong::getMoTa)
                .orElse(null);
        return response.withLyDoTuChoi(lyDo);
    }

    private void requirePending(String trangThai, String doiTuong) {
        if (!TrangThaiDuyet.CHO_DUYET.name().equals(trangThai)) {
            throw new BusinessException(doiTuong + " không ở trạng thái chờ duyệt");
        }
    }

    private String rejectionReason(TuChoiRequest request) {
        if (request == null || request.lyDo() == null || request.lyDo().isBlank()) {
            throw new BusinessException("Lý do từ chối không được để trống");
        }
        if (request.lyDo().length() > 500) {
            throw new BusinessException("Lý do từ chối không được vượt quá 500 ký tự");
        }
        return request.lyDo().trim();
    }

    private void validateId(Integer value, String field) {
        if (value == null || value <= 0) {
            throw new BusinessException(field + " phải lớn hơn 0");
        }
    }

    private void validateSearch(KiemDuyetSearchRequest request) {
        if (request == null || request.getPage() == null || request.getPage() < 0
                || request.getSize() == null || request.getSize() < 1 || request.getSize() > 100) {
            throw new BusinessException("Trang và kích thước trang không hợp lệ (tối đa 100)");
        }
        if ((long) request.getPage() * request.getSize() > Integer.MAX_VALUE) {
            throw new BusinessException("Trang yêu cầu vượt quá giới hạn phân trang");
        }
        if (request.getTrangThaiDuyet() == null
                || !List.of("CHO_DUYET", "DA_DUYET", "TU_CHOI").contains(request.getTrangThaiDuyet())) {
            throw new BusinessException("Trạng thái duyệt không hợp lệ");
        }
        if (request.getMaCH() != null) {
            validateId(request.getMaCH(), "Mã cửa hàng");
        }
        if (request.getTuKhoa() != null && request.getTuKhoa().length() > 255) {
            throw new BusinessException("Từ khóa không được vượt quá 255 ký tự");
        }
    }

    private boolean hasKeyword(KiemDuyetSearchRequest request) {
        return request.getTuKhoa() != null && !request.getTuKhoa().isBlank();
    }

    private String likeKeyword(KiemDuyetSearchRequest request) {
        String keyword = request.getTuKhoa().trim().toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
                .replace("[", "\\[");
        return "%" + keyword + "%";
    }

    private PageRequest pageRequest(KiemDuyetSearchRequest request, String idField) {
        return PageRequest.of(request.getPage(), request.getSize(),
                Sort.by(Sort.Order.asc("ngayTao"), Sort.Order.asc(idField)));
    }
}
