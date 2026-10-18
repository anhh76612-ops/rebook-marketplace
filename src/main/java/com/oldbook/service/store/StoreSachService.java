package com.oldbook.service.store;

import com.oldbook.constant.catalog.TrangThaiBan;
import com.oldbook.constant.catalog.TrangThaiDuyet;
import com.oldbook.dto.catalog.PageResponse;
import com.oldbook.dto.catalog.SachResponse;
import com.oldbook.entity.catalog.CuaHang;
import com.oldbook.entity.catalog.DanhMuc;
import com.oldbook.entity.catalog.Sach;
import com.oldbook.entity.catalog.SachHinhAnh;
import com.oldbook.repository.catalog.DanhMucRepository;
import com.oldbook.repository.catalog.SachRepository;
import com.oldbook.service.catalog.SachMapper;
import com.oldbook.exception.common.BusinessException;
import com.oldbook.dto.store.CreateSachRequest;
import com.oldbook.dto.store.UpdateSachRequest;
import com.oldbook.dto.store.UpdateTonKhoRequest;
import com.oldbook.dto.store.UpdateTrangThaiBanRequest;
import com.oldbook.repository.store.CuaHangRepository;
import com.oldbook.security.store.StoreAccess;
import com.oldbook.service.notification.NghiepVuThongBaoService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class StoreSachService {
    private static final int MAX_IMAGES = 10;
    private static final Set<String> SORT_FIELDS = Set.of("maSach", "tenSach", "giaBanCu", "soLuongTon", "ngayTao");

    private final CuaHangRepository cuaHangRepository;
    private final SachRepository sachRepository;
    private final DanhMucRepository danhMucRepository;
    private final StoreAccess storeAccess;
    private final NghiepVuThongBaoService nghiepVuThongBaoService;

    @Transactional(readOnly = true)
    public PageResponse<SachResponse> listMyBooks(int page, int size, String sort, String trangThaiDuyet) {
        Integer maND = storeAccess.requireOwner();
        CuaHang cuaHang = getMyStore(maND, false);
        PageRequest pageable = pageable(page, size, sort);
        String approval = parseApproval(trangThaiDuyet);
        Specification<Sach> spec = (root, query, builder) -> {
            var owned = builder.equal(root.get("cuaHang").get("maCH"), cuaHang.getMaCH());
            return approval == null ? owned
                    : builder.and(owned, builder.equal(root.get("trangThaiDuyet"), approval));
        };
        return PageResponse.of(sachRepository.findAll(spec, pageable).map(SachMapper::toResponse));
    }

    @Transactional(readOnly = true)
    public SachResponse getMyBook(Integer maSach) {
        Integer maND = storeAccess.requireOwner();
        CuaHang cuaHang = getMyStore(maND, false);
        return SachMapper.toResponse(getOwnedBook(maSach, cuaHang, false));
    }

    @Transactional
    public SachResponse create(CreateSachRequest request) {
        Integer maND = storeAccess.requireOwner();
        CuaHang cuaHang = getApprovedStoreForUpdate(maND);
        validateStock(request.soLuongTon());
        Sach sach = Sach.builder().cuaHang(cuaHang).build();
        applyContent(sach, new UpdateSachRequest(request.maDM(), request.tenSach(), request.tacGia(),
                request.nhaXuatBan(), request.namXuatBan(), request.giaBia(), request.giaBanCu(),
                request.doMoiPercent(), request.tinhTrangVatLy(), request.moTaChiTiet(), request.hinhAnhUrl(), request.hinhAnhUrls()));
        sach.setSoLuongTon(request.soLuongTon());
        sach.setTrangThaiDuyet(TrangThaiDuyet.CHO_DUYET.name());
        sach.setTrangThaiBan(TrangThaiBan.DANG_BAN.name());
        Sach daLuu = sachRepository.save(sach);
        nghiepVuThongBaoService.sachChoDuyet(daLuu, false);
        return SachMapper.toResponse(daLuu);
    }

    @Transactional
    public SachResponse update(Integer maSach, UpdateSachRequest request) {
        Integer maND = storeAccess.requireOwner();
        CuaHang cuaHang = getApprovedStoreForUpdate(maND);
        Sach sach = getOwnedBook(maSach, cuaHang, true);
        applyContent(sach, request);
        sach.setTrangThaiDuyet(TrangThaiDuyet.CHO_DUYET.name());
        sach.setLyDoTuChoi(null);
        Sach daLuu = sachRepository.save(sach);
        nghiepVuThongBaoService.sachChoDuyet(daLuu, true);
        return SachMapper.toResponse(daLuu);
    }

    @Transactional
    public SachResponse updateStock(Integer maSach, UpdateTonKhoRequest request) {
        Integer maND = storeAccess.requireOwner();
        validateStock(request.soLuongTon());
        CuaHang cuaHang = getApprovedStoreForUpdate(maND);
        Sach sach = getOwnedBook(maSach, cuaHang, true);
        sach.setSoLuongTon(request.soLuongTon());
        return SachMapper.toResponse(sachRepository.save(sach));
    }

    @Transactional
    public SachResponse updateSaleStatus(Integer maSach, UpdateTrangThaiBanRequest request) {
        Integer maND = storeAccess.requireOwner();
        String status = request.trangThaiBan();
        if (!TrangThaiBan.DANG_BAN.name().equals(status) && !TrangThaiBan.NGUNG_BAN.name().equals(status)) {
            throw new BusinessException("Trạng thái bán phải là DANG_BAN hoặc NGUNG_BAN");
        }
        CuaHang cuaHang = getApprovedStoreForUpdate(maND);
        Sach sach = getOwnedBook(maSach, cuaHang, true);
        sach.setTrangThaiBan(status);
        return SachMapper.toResponse(sachRepository.save(sach));
    }

    private CuaHang getMyStore(Integer maND, boolean forUpdate) {
        return (forUpdate ? cuaHangRepository.findByChuShopMaNDForUpdate(maND)
                : cuaHangRepository.findByChuShop_MaND(maND))
                .orElseThrow(() -> new BusinessException("Bạn chưa đăng ký cửa hàng"));
    }

    private CuaHang getApprovedStoreForUpdate(Integer maND) {
        // All seller and moderation mutations lock the store before locking its book.
        CuaHang cuaHang = getMyStore(maND, true);
        if (!TrangThaiDuyet.DA_DUYET.name().equals(cuaHang.getTrangThaiDuyet())) {
            throw new BusinessException("Cửa hàng phải được phê duyệt trước khi quản lý sản phẩm");
        }
        return cuaHang;
    }

    private Sach getOwnedBook(Integer maSach, CuaHang cuaHang, boolean forUpdate) {
        if (maSach == null || maSach <= 0) {
            throw new BusinessException("Mã sách không hợp lệ");
        }
        Sach sach = (forUpdate ? sachRepository.findByIdForUpdate(maSach) : sachRepository.findById(maSach))
                .orElseThrow(() -> new BusinessException("Sách không tồn tại"));
        if (sach.getCuaHang() == null || !cuaHang.getMaCH().equals(sach.getCuaHang().getMaCH())) {
            throw new AccessDeniedException("Bạn không có quyền quản lý sách của cửa hàng khác");
        }
        return sach;
    }

    private void applyContent(Sach sach, UpdateSachRequest request) {
        if (request.maDM() == null || request.maDM() <= 0) {
            throw new BusinessException("Danh mục không hợp lệ");
        }
        if (request.tenSach() == null || request.tenSach().isBlank()) {
            throw new BusinessException("Tên sách không được để trống");
        }
        if (request.giaBanCu() == null || request.giaBanCu().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("Giá bán phải lớn hơn 0");
        }
        if (request.giaBia() != null && request.giaBia().signum() < 0) {
            throw new BusinessException("Giá bìa không được âm");
        }
        if (request.doMoiPercent() != null && (request.doMoiPercent().signum() < 0
                || request.doMoiPercent().compareTo(BigDecimal.valueOf(100)) > 0)) {
            throw new BusinessException("Độ mới phải từ 0 đến 100 phần trăm");
        }
        DanhMuc danhMuc = danhMucRepository.findById(request.maDM())
                .orElseThrow(() -> new BusinessException("Danh mục không tồn tại"));
        sach.setDanhMuc(danhMuc);
        sach.setTenSach(request.tenSach().trim());
        sach.setTacGia(optionalText(request.tacGia()));
        sach.setNhaXuatBan(optionalText(request.nhaXuatBan()));
        sach.setNamXuatBan(request.namXuatBan());
        sach.setGiaBia(request.giaBia());
        sach.setGiaBanCu(request.giaBanCu());
        sach.setDoMoiPercent(request.doMoiPercent());
        sach.setTinhTrangVatLy(optionalText(request.tinhTrangVatLy()));
        sach.setMoTaChiTiet(optionalText(request.moTaChiTiet()));
        applyImages(sach, imageUrls(request));
    }

    private List<String> imageUrls(UpdateSachRequest request) {
        List<String> source = request.hinhAnhUrls() != null ? request.hinhAnhUrls()
                : (request.hinhAnhUrl() == null ? List.of() : List.of(request.hinhAnhUrl()));
        var urls = new LinkedHashSet<String>();
        for (String value : source) {
            String url = optionalText(value);
            if (url == null) {
                continue;
            }
            if (url.length() > 500) {
                throw new BusinessException("Đường dẫn ảnh không được vượt quá 500 ký tự");
            }
            urls.add(url);
        }
        if (urls.size() > MAX_IMAGES) {
            throw new BusinessException("Mỗi sách chỉ được có tối đa " + MAX_IMAGES + " ảnh");
        }
        return new ArrayList<>(urls);
    }

    private void applyImages(Sach sach, List<String> urls) {
        sach.setHinhAnhUrl(urls.isEmpty() ? null : urls.get(0));
        sach.getHinhAnhs().clear();
        for (int i = 0; i < urls.size(); i++) {
            sach.getHinhAnhs().add(SachHinhAnh.builder().sach(sach).url(urls.get(i)).thuTu(i).build());
        }
    }

    private void validateStock(Integer stock) {
        if (stock == null || stock < 0) {
            throw new BusinessException("Số lượng tồn phải là số nguyên không âm");
        }
    }

    private PageRequest pageable(int page, int size, String sort) {
        if (page < 0 || size < 1 || size > 100) {
            throw new BusinessException("Trang phải từ 0 và kích thước trang phải từ 1 đến 100");
        }
        if ((long) page * size > Integer.MAX_VALUE) {
            throw new BusinessException("Trang yêu cầu vượt quá giới hạn phân trang");
        }
        String[] parts = (sort == null || sort.isBlank() ? "ngayTao,desc" : sort).split(",", -1);
        if (parts.length > 2 || !SORT_FIELDS.contains(parts[0].trim())) {
            throw new BusinessException("Trường sắp xếp không hợp lệ");
        }
        String direction = parts.length == 1 ? "asc" : parts[1].trim().toLowerCase(Locale.ROOT);
        if (!"asc".equals(direction) && !"desc".equals(direction)) {
            throw new BusinessException("Chiều sắp xếp phải là asc hoặc desc");
        }
        Sort order = Sort.by(Sort.Direction.fromString(direction), parts[0].trim());
        if (!"maSach".equals(parts[0].trim())) {
            order = order.and(Sort.by(Sort.Direction.DESC, "maSach"));
        }
        return PageRequest.of(page, size, order);
    }

    private String parseApproval(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return TrangThaiDuyet.valueOf(value.trim().toUpperCase(Locale.ROOT)).name();
        } catch (IllegalArgumentException e) {
            throw new BusinessException("Trạng thái duyệt không hợp lệ");
        }
    }

    private String optionalText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
