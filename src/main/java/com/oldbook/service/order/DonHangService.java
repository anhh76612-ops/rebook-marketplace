package com.oldbook.service.order;

import com.oldbook.constant.catalog.TrangThaiBan;
import com.oldbook.constant.catalog.TrangThaiDuyet;
import com.oldbook.dto.order.*;
import com.oldbook.entity.catalog.Sach;
import com.oldbook.entity.identity.DiaChi;
import com.oldbook.entity.identity.NguoiDung;
import com.oldbook.entity.order.*;
import com.oldbook.exception.common.BusinessException;
import com.oldbook.repository.catalog.SachRepository;
import com.oldbook.repository.identity.DiaChiRepository;
import com.oldbook.repository.identity.NguoiDungRepository;
import com.oldbook.repository.order.*;
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
    private static final String TRANG_THAI_DON_MOI = "CHO_XU_LY";
    private static final BigDecimal PHI_SHIP_MAC_DINH = BigDecimal.ZERO;

    private final GioHangRepository gioHangRepository;
    private final ChiTietGioHangRepository chiTietGioHangRepository;
    private final DonHangRepository donHangRepository;
    private final DonHangCuaHangRepository donHangCuaHangRepository;
    private final ChiTietDonHangRepository chiTietDonHangRepository;
    private final DiaChiRepository diaChiRepository;
    private final NguoiDungRepository nguoiDungRepository;
    private final SachRepository sachRepository;

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

        List<ChiTietGioHang> cartItems =
                chiTietGioHangRepository.findAllByGioHang(gioHang.getMaGioHang());

        if (cartItems.isEmpty()) {
            throw new BusinessException("Giỏ hàng đang trống");
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

        BigDecimal tongTienHang = lines.stream()
                .map(CheckoutLine::thanhTien)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal tongPhiShip = PHI_SHIP_MAC_DINH;
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

        Map<Integer, List<CheckoutLine>> linesByStore = lines.stream()
                .collect(Collectors.groupingBy(
                        l -> l.sach().getCuaHang().getMaCH(),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        for (Map.Entry<Integer, List<CheckoutLine>> entry : linesByStore.entrySet()) {
            Integer maCH = entry.getKey();
            List<CheckoutLine> storeLines = entry.getValue();

            BigDecimal tienHangCH = storeLines.stream()
                    .map(CheckoutLine::thanhTien)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal phiShipCH = PHI_SHIP_MAC_DINH;
            BigDecimal tongShop = tienHangCH.add(phiShipCH);

            DonHangCuaHang dhch = DonHangCuaHang.builder()
                    .donHang(donHang)
                    .cuaHang(storeLines.get(0).sach().getCuaHang())
                    .giamGiaShop(BigDecimal.ZERO)
                    .giamGiaSanPhanBo(BigDecimal.ZERO)
                    .tienHangCH(tienHangCH)
                    .phiShipCH(phiShipCH)
                    .commissionTien(BigDecimal.ZERO)
                    .tongTienShopOrder(tongShop)
                    .trangThaiDonHang(TRANG_THAI_DON_MOI)
                    .trangThaiThanhToanShop(TRANG_THAI_TT_CHO)
                    .tienHoanTra(BigDecimal.ZERO)
                    .ghiChu(request.getGhiChu())
                    .build();

            dhch = donHangCuaHangRepository.save(dhch);

            for (CheckoutLine line : storeLines) {
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

        chiTietGioHangRepository.deleteAllByGioHang(gioHang.getMaGioHang());

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

        List<StoreOrderResponse> storeResponses = stores.stream()
                .map(store -> {
                    List<OrderItemResponse> items =
                            chiTietDonHangRepository
                                    .findAllByDonHangCuaHang_MaDHCHOrderByMaCTDHAsc(
                                            store.getMaDHCH()
                                    )
                                    .stream()
                                    .map(ct -> OrderItemResponse.builder()
                                            .maCTDH(ct.getMaCTDH())
                                            .maSach(ct.getSach().getMaSach())
                                            .tenSach(ct.getSach().getTenSach())
                                            .soLuong(ct.getSoLuong())
                                            .donGia(ct.getDonGia())
                                            .thanhTien(ct.getThanhTien())
                                            .build())
                                    .toList();

                    return StoreOrderResponse.builder()
                            .maDHCH(store.getMaDHCH())
                            .maCH(store.getCuaHang().getMaCH())
                            .tenCuaHang(store.getCuaHang().getTenCuaHang())
                            .tienHangCH(store.getTienHangCH())
                            .phiShipCH(store.getPhiShipCH())
                            .tongTienShopOrder(store.getTongTienShopOrder())
                            .trangThaiDonHang(store.getTrangThaiDonHang())
                            .trangThaiThanhToanShop(store.getTrangThaiThanhToanShop())
                            .items(items)
                            .build();
                })
                .toList();

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

    private record CheckoutLine(
            ChiTietGioHang cartItem,
            Sach sach,
            BigDecimal donGia,
            BigDecimal thanhTien
    ) {}
}
