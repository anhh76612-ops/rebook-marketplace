package com.oldbook.service.order;

import com.oldbook.constant.auth.VaiTro;
import com.oldbook.constant.catalog.TrangThaiBan;
import com.oldbook.constant.order.TrangThaiDonHang;
import com.oldbook.constant.order.TrangThaiYeuCauHuy;
import com.oldbook.constant.shipping.TrangThaiDonViVanChuyen;
import com.oldbook.constant.catalog.TrangThaiDuyet;
import com.oldbook.dto.order.*;
import com.oldbook.entity.catalog.CuaHang;
import com.oldbook.entity.catalog.Sach;
import com.oldbook.entity.identity.DiaChi;
import com.oldbook.entity.identity.NguoiDung;
import com.oldbook.entity.order.*;
import com.oldbook.entity.shipping.DonViVanChuyen;
import com.oldbook.exception.common.BusinessException;
import com.oldbook.repository.catalog.SachRepository;
import com.oldbook.repository.identity.DiaChiRepository;
import com.oldbook.repository.identity.NguoiDungRepository;
import com.oldbook.repository.order.*;
import com.oldbook.repository.shipping.DonViVanChuyenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DonHangService {

    private static final String PHUONG_THUC_COD = "COD";
    private static final String TRANG_THAI_TT_CHO = "CHO_THANH_TOAN";
    private static final String TRANG_THAI_DON_MOI = TrangThaiDonHang.CHO_XU_LY.name();

    private final GioHangRepository gioHangRepository;
    private final ChiTietGioHangRepository chiTietGioHangRepository;
    private final DonHangRepository donHangRepository;
    private final DonHangCuaHangRepository donHangCuaHangRepository;
    private final ChiTietDonHangRepository chiTietDonHangRepository;
    private final DiaChiRepository diaChiRepository;
    private final NguoiDungRepository nguoiDungRepository;
    private final SachRepository sachRepository;
    private final DonViVanChuyenRepository donViVanChuyenRepository;
    private final OrderMapper orderMapper;
    private final YeuCauHuyDonRepository yeuCauHuyDonRepository;
    private final OrderSettlementService settlementService;

    @Transactional
    public OrderResponse createOrder(Integer maND, CreateOrderRequest request) {
        if (!PHUONG_THUC_COD.equalsIgnoreCase(request.getPhuongThucTT().trim())) {
            throw new BusinessException("Phương thức thanh toán hiện hỗ trợ: COD");
        }

        NguoiDung nguoiMua = nguoiDungRepository.findById(maND)
                .orElseThrow(() -> new BusinessException("Người dùng không tồn tại"));

        DiaChi diaChi = diaChiRepository
                .findByMaDiaChiAndNguoiDung_MaND(request.getMaDiaChi(), maND)
                .orElseThrow(() -> new BusinessException(
                        "Địa chỉ giao hàng không tồn tại hoặc không thuộc tài khoản của bạn"
                ));

        GioHang gioHang = gioHangRepository.findByMaND(maND)
                .orElseThrow(() -> new BusinessException("Giỏ hàng đang trống"));

        // Khóa giỏ hàng để không cho hai request checkout đồng thời dùng cùng một giỏ.
        gioHang = gioHangRepository.findByIdForUpdate(gioHang.getMaGioHang())
                .orElseThrow(() -> new BusinessException("Giỏ hàng không tồn tại"));

        List<ChiTietGioHang> allCartItems =
                chiTietGioHangRepository.findAllByGioHang(gioHang.getMaGioHang());

        if (allCartItems.isEmpty()) {
            throw new BusinessException("Giỏ hàng đang trống");
        }

        // Chỉ đặt các dòng giỏ hàng mà khách đã tích chọn.
        Set<Integer> selectedIds = new HashSet<>(request.getMaCTGioHangs());

        List<ChiTietGioHang> cartItems = allCartItems.stream()
                .filter(ct -> selectedIds.contains(ct.getMaCTGioHang()))
                .toList();

        if (cartItems.isEmpty() || cartItems.size() != selectedIds.size()) {
            throw new BusinessException(
                    "Sản phẩm được chọn không còn trong giỏ hàng. Vui lòng quay lại giỏ hàng và chọn lại"
            );
        }

        List<CheckoutLine> lines = new ArrayList<>();
        for (ChiTietGioHang cartItem : cartItems) {
            Integer maSach = cartItem.getSach().getMaSach();

            Sach sach = sachRepository.findByIdForUpdate(maSach)
                    .orElseThrow(() -> new BusinessException(
                            "Sách không tồn tại: " + maSach
                    ));

            validateCanOrder(sach, maND, cartItem.getSoLuong());

            BigDecimal donGia = sach.getGiaBanCu();
            BigDecimal thanhTien = donGia.multiply(
                    BigDecimal.valueOf(cartItem.getSoLuong())
            );

            lines.add(new CheckoutLine(cartItem, sach, donGia, thanhTien));
        }

        // Gom sách theo cửa hàng: mỗi cửa hàng là một đơn hàng con riêng.
        Map<Integer, List<CheckoutLine>> linesByStore = lines.stream()
                .collect(Collectors.groupingBy(
                        l -> l.sach().getCuaHang().getMaCH(),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        // Mỗi cửa hàng phải có đúng một lựa chọn (đơn vị vận chuyển + ghi chú riêng).
        Map<Integer, StoreOrderOptionRequest> optionsByStore =
                indexStoreOptions(request.getStoreOrders(), linesByStore);

        Map<Integer, DonViVanChuyen> carrierCache = new HashMap<>();
        List<StoreDraft> drafts = new ArrayList<>();

        for (Map.Entry<Integer, List<CheckoutLine>> entry : linesByStore.entrySet()) {
            List<CheckoutLine> storeLines = entry.getValue();
            StoreOrderOptionRequest option = optionsByStore.get(entry.getKey());

            DonViVanChuyen carrier = carrierCache.computeIfAbsent(
                    option.getMaDVVC(), this::loadActiveCarrier
            );

            BigDecimal tienHangCH = storeLines.stream()
                    .map(CheckoutLine::thanhTien)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal phiShipCH = Optional.ofNullable(carrier.getPhiVanChuyen())
                    .orElse(BigDecimal.ZERO);

            drafts.add(new StoreDraft(
                    storeLines.get(0).sach().getCuaHang(),
                    storeLines,
                    carrier,
                    normalizeNote(option.getGhiChu()),
                    tienHangCH,
                    phiShipCH
            ));
        }

        BigDecimal tongTienHang = drafts.stream()
                .map(StoreDraft::tienHang)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal tongPhiShip = drafts.stream()
                .map(StoreDraft::phiShip)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal tongGiamGia = BigDecimal.ZERO;
        BigDecimal tongThanhToan = tongTienHang
                .add(tongPhiShip)
                .subtract(tongGiamGia);

        DonHang donHang = DonHang.builder()
                .nguoiMua(nguoiMua)
                .giamGiaSan(BigDecimal.ZERO)
                .tongTienHang(tongTienHang)
                .tongPhiShip(tongPhiShip)
                .tongGiamGia(tongGiamGia)
                .tongThanhToan(tongThanhToan)
                .phuongThucTT(PHUONG_THUC_COD)
                .trangThaiThanhToan(TRANG_THAI_TT_CHO)
                .diaChiGiao(diaChi)
                .tenNguoiNhan(diaChi.getTenNguoiNhan())
                .soDienThoaiNhan(diaChi.getSoDienThoai())
                .tinhThanhGiao(diaChi.getTinhThanh())
                .quanHuyenGiao(diaChi.getQuanHuyen())
                .phuongXaGiao(diaChi.getPhuongXa())
                .diaChiGiaoChiTiet(diaChi.getDiaChiChiTiet())
                .build();

        donHang = donHangRepository.save(donHang);

        for (StoreDraft draft : drafts) {
            DonHangCuaHang dhch = DonHangCuaHang.builder()
                    .donHang(donHang)
                    .cuaHang(draft.cuaHang())
                    .donViVanChuyen(draft.carrier())
                    .giamGiaShop(BigDecimal.ZERO)
                    .giamGiaSanPhanBo(BigDecimal.ZERO)
                    .tienHangCH(draft.tienHang())
                    .phiShipCH(draft.phiShip())
                    .commissionTien(BigDecimal.ZERO)
                    .tongTienShopOrder(draft.tienHang().add(draft.phiShip()))
                    .trangThaiDonHang(TRANG_THAI_DON_MOI)
                    .trangThaiThanhToanShop(TRANG_THAI_TT_CHO)
                    .tienHoanTra(BigDecimal.ZERO)
                    .ghiChu(draft.ghiChu())
                    .build();

            dhch = donHangCuaHangRepository.save(dhch);

            for (CheckoutLine line : draft.lines()) {
                ChiTietDonHang chiTiet = ChiTietDonHang.builder()
                        .donHangCuaHang(dhch)
                        .sach(line.sach())
                        .soLuong(line.cartItem().getSoLuong())
                        .donGia(line.donGia())
                        .thanhTien(line.thanhTien())
                        .tyLeChietKhau(BigDecimal.ZERO)
                        .tienChietKhau(BigDecimal.ZERO)
                        .build();

                chiTietDonHangRepository.save(chiTiet);

                // Đã khóa bản ghi Sach trong transaction nên không xảy ra trừ tồn kiểu lost-update.
                line.sach().setSoLuongTon(
                        line.sach().getSoLuongTon() - line.cartItem().getSoLuong()
                );
                sachRepository.save(line.sach());
            }
        }

        // Chỉ xóa các dòng đã đặt; sản phẩm không được chọn vẫn còn trong giỏ.
        chiTietGioHangRepository.deleteAll(cartItems);
        chiTietGioHangRepository.flush();

        return buildOrderResponse(donHang);
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getMyOrders(Integer maND) {
        return donHangRepository.findAllByNguoiMua(maND).stream()
                .map(this::buildOrderResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public OrderResponse getMyOrder(Integer maND, Integer maDH) {
        DonHang donHang = donHangRepository.findById(maDH)
                .orElseThrow(() -> new BusinessException("Đơn hàng không tồn tại"));

        if (donHang.getNguoiMua() == null
                || !maND.equals(donHang.getNguoiMua().getMaND())) {
            throw new BusinessException("Bạn không có quyền xem đơn hàng này");
        }

        return buildOrderResponse(donHang);
    }

    /**
     * Khách hủy đơn hàng con: đơn chưa xác nhận thì hủy ngay; đã xác nhận thì gửi yêu cầu để cửa hàng duyệt.
     */
    @Transactional
    public OrderResponse cancelStoreOrder(Integer maND, Integer maDHCH, String lyDo) {
        String reason = CancelOrderRequest.normalize(lyDo);

        DonHangCuaHang order = donHangCuaHangRepository.findByIdForUpdate(maDHCH)
                .filter(o -> o.getDonHang().getNguoiMua() != null
                        && maND.equals(o.getDonHang().getNguoiMua().getMaND()))
                .orElseThrow(() -> new BusinessException("Đơn hàng không tồn tại"));

        String status = order.getTrangThaiDonHang();

        if (TrangThaiDonHang.CHO_XU_LY.name().equals(status)) {
            settlementService.settleCancelled(order, VaiTro.KHACH_HANG.name(), reason);
        } else if (TrangThaiDonHang.DA_XAC_NHAN.name().equals(status)) {
            if (yeuCauHuyDonRepository.existsByDonHangCuaHang_MaDHCH(maDHCH)) {
                throw new BusinessException("Bạn đã gửi yêu cầu hủy cho đơn hàng này");
            }
            yeuCauHuyDonRepository.save(YeuCauHuyDon.builder()
                    .donHangCuaHang(order)
                    .nguoiDung(nguoiDungRepository.getReferenceById(maND))
                    .lyDo(reason)
                    .trangThaiYeuCau(TrangThaiYeuCauHuy.CHO_DUYET.name())
                    .build());
        } else {
            throw new BusinessException("Đơn hàng đã được đơn vị vận chuyển tiếp nhận, không thể hủy");
        }

        return buildOrderResponse(order.getDonHang());
    }

    private Map<Integer, StoreOrderOptionRequest> indexStoreOptions(
            List<StoreOrderOptionRequest> options,
            Map<Integer, List<CheckoutLine>> linesByStore
    ) {
        if (options == null || options.isEmpty()) {
            throw new BusinessException(
                    "Vui lòng chọn đơn vị vận chuyển cho từng cửa hàng"
            );
        }

        Map<Integer, StoreOrderOptionRequest> byStore = new LinkedHashMap<>();
        for (StoreOrderOptionRequest option : options) {
            if (!linesByStore.containsKey(option.getMaCH())) {
                throw new BusinessException(
                        "Cửa hàng #" + option.getMaCH() + " không có trong giỏ hàng của bạn"
                );
            }
            if (byStore.put(option.getMaCH(), option) != null) {
                throw new BusinessException(
                        "Mỗi cửa hàng chỉ được chọn một đơn vị vận chuyển"
                );
            }
        }

        for (Map.Entry<Integer, List<CheckoutLine>> entry : linesByStore.entrySet()) {
            if (!byStore.containsKey(entry.getKey())) {
                CuaHang cuaHang = entry.getValue().get(0).sach().getCuaHang();
                throw new BusinessException(
                        "Vui lòng chọn đơn vị vận chuyển cho cửa hàng \""
                                + cuaHang.getTenCuaHang() + "\""
                );
            }
        }

        return byStore;
    }

    private DonViVanChuyen loadActiveCarrier(Integer maDVVC) {
        return donViVanChuyenRepository
                .findByMaDVVCAndTrangThai(maDVVC, TrangThaiDonViVanChuyen.HOAT_DONG.name())
                .orElseThrow(() -> new BusinessException(
                        "Đơn vị vận chuyển không tồn tại hoặc đã ngừng hoạt động"
                ));
    }

    private String normalizeNote(String note) {
        if (note == null) {
            return null;
        }
        String trimmed = note.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void validateCanOrder(Sach sach, Integer maND, Integer soLuong) {
        if (soLuong == null || soLuong <= 0) {
            throw new BusinessException("Số lượng đặt hàng không hợp lệ");
        }

        if (!TrangThaiDuyet.DA_DUYET.name().equals(sach.getTrangThaiDuyet())
                || !TrangThaiBan.DANG_BAN.name().equals(sach.getTrangThaiBan())
                || sach.getCuaHang() == null
                || !TrangThaiDuyet.DA_DUYET.name().equals(
                        sach.getCuaHang().getTrangThaiDuyet())) {
            throw new BusinessException(
                    "Sách \"" + sach.getTenSach() + "\" hiện không còn được bán"
            );
        }

        if (sach.getGiaBanCu() == null
                || sach.getGiaBanCu().signum() <= 0) {
            throw new BusinessException(
                    "Sách \"" + sach.getTenSach() + "\" chưa có giá bán hợp lệ"
            );
        }

        int ton = Optional.ofNullable(sach.getSoLuongTon()).orElse(0);
        if (ton < soLuong) {
            throw new BusinessException(
                    "Sách \"" + sach.getTenSach()
                            + "\" không đủ tồn kho. Còn " + ton + " cuốn"
            );
        }

        NguoiDung chuShop = sach.getCuaHang().getChuShop();
        if (chuShop != null && maND.equals(chuShop.getMaND())) {
            throw new BusinessException(
                    "Bạn không thể mua sách của chính cửa hàng mình"
            );
        }
    }

    private OrderResponse buildOrderResponse(DonHang donHang) {
        List<DonHangCuaHang> stores =
                donHangCuaHangRepository.findAllByDonHang_MaDHOrderByMaDHCHAsc(
                        donHang.getMaDH()
                );

        List<StoreOrderResponse> storeResponses =
                orderMapper.toStoreOrderResponses(stores);

        return OrderResponse.builder()
                .maDH(donHang.getMaDH())
                .maNDNguoiMua(donHang.getNguoiMua().getMaND())
                .tongTienHang(donHang.getTongTienHang())
                .tongPhiShip(donHang.getTongPhiShip())
                .tongGiamGia(donHang.getTongGiamGia())
                .tongThanhToan(donHang.getTongThanhToan())
                .phuongThucTT(donHang.getPhuongThucTT())
                .trangThaiThanhToan(donHang.getTrangThaiThanhToan())
                .maDiaChi(donHang.getDiaChiGiao() == null
                        ? null : donHang.getDiaChiGiao().getMaDiaChi())
                .tenNguoiNhan(donHang.getTenNguoiNhan())
                .soDienThoaiNhan(donHang.getSoDienThoaiNhan())
                .tinhThanhGiao(donHang.getTinhThanhGiao())
                .quanHuyenGiao(donHang.getQuanHuyenGiao())
                .phuongXaGiao(donHang.getPhuongXaGiao())
                .diaChiGiaoChiTiet(donHang.getDiaChiGiaoChiTiet())
                .ngayTao(donHang.getNgayTao())
                .storeOrders(storeResponses)
                .build();
    }

    private record StoreDraft(
            CuaHang cuaHang,
            List<CheckoutLine> lines,
            DonViVanChuyen carrier,
            String ghiChu,
            BigDecimal tienHang,
            BigDecimal phiShip
    ) {}

    private record CheckoutLine(
            ChiTietGioHang cartItem,
            Sach sach,
            BigDecimal donGia,
            BigDecimal thanhTien
    ) {}
}