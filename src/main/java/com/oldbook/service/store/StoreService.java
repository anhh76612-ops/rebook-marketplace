package com.oldbook.service.store;

import com.oldbook.constant.auth.TrangThaiTaiKhoan;
import com.oldbook.constant.auth.VaiTro;
import com.oldbook.constant.catalog.TrangThaiDuyet;
import com.oldbook.entity.catalog.CuaHang;
import com.oldbook.exception.common.BusinessException;
import com.oldbook.entity.identity.NguoiDung;
import com.oldbook.entity.identity.TaiKhoan;
import com.oldbook.repository.identity.NguoiDungRepository;
import com.oldbook.repository.identity.TaiKhoanRepository;
import com.oldbook.dto.payment.TaiKhoanNhanRequest;
import com.oldbook.dto.store.CreateCuaHangRequest;
import com.oldbook.dto.store.CuaHangResponse;
import com.oldbook.dto.store.DangKyGianHangResponse;
import com.oldbook.dto.store.UpdateCuaHangRequest;
import com.oldbook.repository.store.CuaHangRepository;
import com.oldbook.security.store.StoreAccess;
import com.oldbook.service.notification.NghiepVuThongBaoService;
import com.oldbook.repository.system.NhatKyHeThongRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StoreService {
    private final CuaHangRepository cuaHangRepository;
    private final NguoiDungRepository nguoiDungRepository;
    private final TaiKhoanRepository taiKhoanRepository;
    private final StoreAccess storeAccess;
    private final NhatKyHeThongRepository nhatKyHeThongRepository;
    private final NghiepVuThongBaoService nghiepVuThongBaoService;

    @Transactional(readOnly = true)
    public CuaHangResponse getMyStore() {
        Integer maND = storeAccess.requireOwner();
        CuaHang cuaHang = cuaHangRepository.findByChuShop_MaND(maND)
                .orElseThrow(() -> new BusinessException("Bạn chưa đăng ký cửa hàng"));
        CuaHangResponse response = CuaHangResponse.from(cuaHang);
        if (TrangThaiDuyet.TU_CHOI.name().equals(cuaHang.getTrangThaiDuyet())) {
            return nhatKyHeThongRepository
                    .findFirstByLoaiDoiTuongAndMaDoiTuongAndHanhDongOrderByThoiGianDescMaNhatKyDesc(
                            "CUA_HANG", cuaHang.getMaCH(), "TU_CHOI_CUA_HANG")
                    .map(log -> response.withLyDoTuChoi(log.getMoTa()))
                    .orElse(response);
        }
        return response;
    }

    @Transactional
    public CuaHangResponse create(CreateCuaHangRequest request) {
        Integer maND = storeAccess.requireOwner();
        return CuaHangResponse.from(taoCuaHang(maND, request));
    }

    /**
     * Khách hàng tự đăng ký gian hàng: tạo thêm tài khoản Chủ cửa hàng cho chính người đó
     * và một cửa hàng chờ duyệt, trong cùng một giao dịch.
     * Tài khoản mới dùng được ngay để theo dõi trạng thái; việc đăng sách vẫn phải chờ quản lý duyệt cửa hàng.
     */
    @Transactional
    public DangKyGianHangResponse registerAsCustomer(CreateCuaHangRequest request) {
        Integer maND = storeAccess.requireCustomer();
        if (taiKhoanRepository.existsByNguoiDung_MaNDAndVaiTro(maND, VaiTro.CHU_CUA_HANG.name())) {
            throw new BusinessException("Bạn đã có tài khoản chủ cửa hàng. Hãy chuyển sang tài khoản đó để quản lý gian hàng.");
        }
        CuaHang cuaHang = taoCuaHang(maND, request);
        TaiKhoan taiKhoan = taiKhoanRepository.save(TaiKhoan.builder()
                .nguoiDung(cuaHang.getChuShop())
                .vaiTro(VaiTro.CHU_CUA_HANG.name())
                .trangThai(TrangThaiTaiKhoan.HOAT_DONG.name())
                .build());
        return new DangKyGianHangResponse(taiKhoan.getMaTK(), CuaHangResponse.from(cuaHang));
    }

    private CuaHang taoCuaHang(Integer maND, CreateCuaHangRequest request) {
        if (cuaHangRepository.findByChuShop_MaND(maND).isPresent()) {
            throw new BusinessException("Bạn đã có cửa hàng");
        }
        String ten = requireText(request.tenCuaHang(), "Tên cửa hàng", 255);
        if (cuaHangRepository.existsByTenCuaHangIgnoreCase(ten)) {
            throw new BusinessException("Tên cửa hàng đã được sử dụng");
        }
        NguoiDung owner = nguoiDungRepository.findById(maND)
                .orElseThrow(() -> new BusinessException("Người dùng không tồn tại"));
        CuaHang cuaHang = CuaHang.builder()
                .chuShop(owner)
                .tenCuaHang(ten)
                .moTa(optionalText(request.moTa(), "Mô tả", 1000))
                .diaChiCH(requireText(request.diaChiCH(), "Địa chỉ cửa hàng", 500))
                .soDienThoaiCH(requireText(request.soDienThoaiCH(), "Số điện thoại cửa hàng", 20))
                .trangThaiDuyet(TrangThaiDuyet.CHO_DUYET.name())
                .build();
        if (request.maNganHang() != null || request.soTaiKhoan() != null || request.tenChuTaiKhoan() != null) {
            ganTaiKhoanNhanTien(cuaHang, request.maNganHang(), request.soTaiKhoan(), request.tenChuTaiKhoan());
        }
        CuaHang daLuu = cuaHangRepository.save(cuaHang);
        nghiepVuThongBaoService.gianHangChoDuyet(daLuu, false);
        return daLuu;
    }

    /**
     * Đổi tài khoản nhận tiền sàn chi. Khác với sửa thông tin cửa hàng, việc này không đưa cửa hàng
     * về trạng thái chờ duyệt: tài khoản ngân hàng không phải nội dung cần kiểm duyệt.
     */
    @Transactional
    public CuaHangResponse capNhatTaiKhoanNhanTien(TaiKhoanNhanRequest request) {
        Integer maND = storeAccess.requireOwner();
        CuaHang cuaHang = cuaHangRepository.findByChuShopMaNDForUpdate(maND)
                .orElseThrow(() -> new BusinessException("Bạn chưa đăng ký cửa hàng"));
        // Có đủ ba trường tài khoản hoặc có ảnh mã QR là hợp lệ; đổi sang chỉ ảnh QR thì xóa tài khoản cũ.
        request.kiemTra();
        cuaHang.setMaNganHang(request.maNganHangGon());
        cuaHang.setSoTaiKhoan(request.soTaiKhoanGon());
        cuaHang.setTenChuTaiKhoan(request.tenChuTaiKhoanGon());
        cuaHang.setQrNganHangUrl(request.qrGon());
        return CuaHangResponse.from(cuaHangRepository.save(cuaHang));
    }

    // Ba trường đi cùng nhau: thiếu một trường thì không chuyển khoản được.
    private void ganTaiKhoanNhanTien(CuaHang cuaHang, String maNganHang, String soTaiKhoan, String tenChuTaiKhoan) {
        cuaHang.setMaNganHang(requireText(maNganHang, "Ngân hàng", 20));
        cuaHang.setSoTaiKhoan(requireText(soTaiKhoan, "Số tài khoản", 50));
        cuaHang.setTenChuTaiKhoan(requireText(tenChuTaiKhoan, "Tên chủ tài khoản", 255));
    }

    @Transactional
    public CuaHangResponse updateMyStore(UpdateCuaHangRequest request) {
        Integer maND = storeAccess.requireOwner();
        CuaHang cuaHang = cuaHangRepository.findByChuShopMaNDForUpdate(maND)
                .orElseThrow(() -> new BusinessException("Bạn chưa đăng ký cửa hàng"));
        String ten = requireText(request.tenCuaHang(), "Tên cửa hàng", 255);
        if (cuaHangRepository.existsByTenCuaHangIgnoreCaseAndMaCHNot(ten, cuaHang.getMaCH())) {
            throw new BusinessException("Tên cửa hàng đã được sử dụng");
        }
        cuaHang.setTenCuaHang(ten);
        cuaHang.setMoTa(optionalText(request.moTa(), "Mô tả", 1000));
        cuaHang.setDiaChiCH(requireText(request.diaChiCH(), "Địa chỉ cửa hàng", 500));
        cuaHang.setSoDienThoaiCH(requireText(request.soDienThoaiCH(), "Số điện thoại cửa hàng", 20));
        cuaHang.setTrangThaiDuyet(TrangThaiDuyet.CHO_DUYET.name());
        CuaHang daLuu = cuaHangRepository.save(cuaHang);
        nghiepVuThongBaoService.gianHangChoDuyet(daLuu, true);
        return CuaHangResponse.from(daLuu);
    }

    private String requireText(String value, String field, int maxLength) {
        String normalized = optionalText(value, field, maxLength);
        if (normalized == null) {
            throw new BusinessException(field + " không được để trống");
        }
        return normalized;
    }

    private String optionalText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new BusinessException(field + " vượt quá " + maxLength + " ký tự");
        }
        return normalized;
    }
}
