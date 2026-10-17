package com.oldbook.order;

import com.oldbook.entity.catalog.CuaHang;
import com.oldbook.entity.catalog.DanhMuc;
import com.oldbook.entity.catalog.Sach;
import com.oldbook.entity.identity.NguoiDung;
import com.oldbook.entity.order.ChiTietDonHang;
import com.oldbook.entity.order.DonHang;
import com.oldbook.entity.order.DonHangCuaHang;
import com.oldbook.entity.payment.CauHinhChietKhau;
import com.oldbook.entity.shipping.DonViVanChuyen;
import com.oldbook.entity.shipping.VanChuyen;
import com.oldbook.dto.shipping.CapNhatVanChuyenRequest;
import com.oldbook.dto.shipping.TiepNhanVanChuyenRequest;
import com.oldbook.exception.common.BusinessException;
import com.oldbook.repository.catalog.DanhMucRepository;
import com.oldbook.repository.catalog.SachRepository;
import com.oldbook.repository.identity.NguoiDungRepository;
import com.oldbook.repository.order.ChiTietDonHangRepository;
import com.oldbook.repository.order.DonHangCuaHangRepository;
import com.oldbook.repository.order.DonHangRepository;
import com.oldbook.repository.payment.CauHinhChietKhauRepository;
import com.oldbook.repository.payment.ThanhToanRepository;
import com.oldbook.repository.shipping.DonViVanChuyenRepository;
import com.oldbook.repository.shipping.LichSuVanChuyenRepository;
import com.oldbook.repository.shipping.VanChuyenRepository;
import com.oldbook.repository.store.CuaHangRepository;
import com.oldbook.service.order.StoreOrderService;
import com.oldbook.service.shipping.ShipmentSyncService.SyncResult;
import com.oldbook.service.shipping.ShipmentTrackingService;
import com.oldbook.service.shipping.ShippingCarrierClient;
import com.oldbook.service.shipping.VanChuyenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Luồng: chủ shop xác nhận -> hệ thống gọi API đơn vị vận chuyển lấy trạng thái -> chốt doanh thu/hoàn kho. */
@SpringBootTest(properties = {
        "spring.config.import=",
        "shipping.sync.polling-enabled=false"
})
@ActiveProfiles({"test", "catalog-test"})
class ShippingSyncIntegrationTest {

    /** Đơn vị vận chuyển giả: test đặt trước trạng thái gốc mà "API" sẽ trả về cho từng mã vận đơn. */
    @TestConfiguration
    static class ScriptedCarrierConfig {
        @Bean
        @Primary
        ScriptedCarrierClient scriptedCarrierClient() {
            return new ScriptedCarrierClient();
        }
    }

    static class ScriptedCarrierClient implements ShippingCarrierClient {
        final Map<String, String> statusByCode = new ConcurrentHashMap<>();
        final AtomicInteger seq = new AtomicInteger();

        @Override
        public CarrierShipment createShipment(CarrierShipmentRequest request) {
            return new CarrierShipment("GHN-TEST-" + seq.incrementAndGet());
        }

        @Override
        public Optional<CarrierTracking> track(VanChuyen shipment) {
            String raw = statusByCode.get(shipment.getMaVanDon());
            return raw == null ? Optional.empty() : Optional.of(new CarrierTracking(raw, null, null));
        }
    }

    @Autowired private ScriptedCarrierClient carrier;
    @Autowired private ShipmentTrackingService trackingService;
    @Autowired private StoreOrderService storeOrderService;
    @Autowired private NguoiDungRepository nguoiDungRepository;
    @Autowired private CuaHangRepository cuaHangRepository;
    @Autowired private DanhMucRepository danhMucRepository;
    @Autowired private SachRepository sachRepository;
    @Autowired private DonHangRepository donHangRepository;
    @Autowired private DonHangCuaHangRepository donHangCuaHangRepository;
    @Autowired private ChiTietDonHangRepository chiTietDonHangRepository;
    @Autowired private VanChuyenRepository vanChuyenRepository;
    @Autowired private LichSuVanChuyenRepository lichSuVanChuyenRepository;
    @Autowired private ThanhToanRepository thanhToanRepository;
    @Autowired private CauHinhChietKhauRepository chietKhauRepository;
    @Autowired private DonViVanChuyenRepository donViVanChuyenRepository;
    @Autowired private VanChuyenService vanChuyenService;

    private NguoiDung owner;
    private Sach book;
    private DonHang parent;
    private DonHangCuaHang order;
    private String trackingCode;

    @BeforeEach
    void seed() {
        thanhToanRepository.deleteAllInBatch();
        lichSuVanChuyenRepository.deleteAllInBatch();
        vanChuyenRepository.deleteAllInBatch();
        chiTietDonHangRepository.deleteAllInBatch();
        donHangCuaHangRepository.deleteAllInBatch();
        donHangRepository.deleteAllInBatch();
        chietKhauRepository.deleteAllInBatch();
        sachRepository.deleteAllInBatch();
        cuaHangRepository.deleteAllInBatch();
        danhMucRepository.deleteAllInBatch();
        nguoiDungRepository.deleteAllInBatch();

        owner = nguoiDungRepository.save(NguoiDung.builder().email("owner@ship.test")
                .matKhau("x").soDienThoai("0900000001").build());
        NguoiDung buyer = nguoiDungRepository.save(NguoiDung.builder().email("buyer@ship.test")
                .matKhau("x").soDienThoai("0900000002").build());
        CuaHang shop = cuaHangRepository.save(CuaHang.builder().chuShop(owner)
                .tenCuaHang("Shop giao hang").trangThaiDuyet("DA_DUYET").build());
        DanhMuc category = danhMucRepository.save(DanhMuc.builder().tenDanhMuc("Văn học").build());

        // Cấu hình hoa hồng 10% cho danh mục
        chietKhauRepository.save(CauHinhChietKhau.builder().danhMuc(category)
                .tyLeChietKhau(new BigDecimal("10.00")).trangThai("HOAT_DONG")
                .ngayBatDau(LocalDateTime.now().minusDays(1)).build());

        // Đã đặt 2 cuốn nên tồn kho còn 3 (ban đầu 5)
        book = sachRepository.save(Sach.builder().cuaHang(shop).danhMuc(category).tenSach("Nhà giả kim")
                .giaBanCu(new BigDecimal("100000")).trangThaiDuyet("DA_DUYET").trangThaiBan("DANG_BAN")
                .soLuongTon(3).build());

        DonViVanChuyen ghn = donViVanChuyenRepository.findAll().stream()
                .filter(c -> "GHN".equals(c.getMaCode())).findFirst().orElseThrow();

        parent = donHangRepository.save(DonHang.builder().nguoiMua(buyer)
                .tongTienHang(new BigDecimal("200000")).tongPhiShip(new BigDecimal("25000"))
                .tongGiamGia(BigDecimal.ZERO).tongThanhToan(new BigDecimal("225000"))
                .phuongThucTT("COD").trangThaiThanhToan("CHO_THANH_TOAN")
                .tenNguoiNhan("Nguyen Van A").soDienThoaiNhan("0911111111")
                .diaChiGiaoChiTiet("1 Vo Van Ngan").tinhThanhGiao("TP.HCM").build());

        order = donHangCuaHangRepository.save(DonHangCuaHang.builder().donHang(parent).cuaHang(shop)
                .donViVanChuyen(ghn).giamGiaShop(BigDecimal.ZERO).giamGiaSanPhanBo(BigDecimal.ZERO)
                .tienHangCH(new BigDecimal("200000")).phiShipCH(new BigDecimal("25000"))
                .commissionTien(BigDecimal.ZERO).tongTienShopOrder(new BigDecimal("225000"))
                .trangThaiDonHang("CHO_XU_LY").trangThaiThanhToanShop("CHO_THANH_TOAN")
                .tienHoanTra(BigDecimal.ZERO).build());

        chiTietDonHangRepository.save(ChiTietDonHang.builder().donHangCuaHang(order).sach(book)
                .soLuong(2).donGia(new BigDecimal("100000")).thanhTien(new BigDecimal("200000"))
                .tyLeChietKhau(BigDecimal.ZERO).tienChietKhau(BigDecimal.ZERO).build());

        storeOrderService.confirmOrder(owner.getMaND(), order.getMaDHCH());
        VanChuyen vc = vanChuyenRepository.findByDonHangCuaHang_MaDHCH(order.getMaDHCH()).orElseThrow();
        trackingCode = vc.getMaVanDon();
    }

    @Test
    void confirmingHandsOrderToCarrier() {
        assertThat(orderStatus()).isEqualTo("DA_XAC_NHAN");
        VanChuyen vc = vanChuyenRepository.findByDonHangCuaHang_MaDHCH(order.getMaDHCH()).orElseThrow();
        assertThat(vc.getTrangThaiVanChuyen()).isEqualTo("CHO_LAY_HANG");
        assertThat(trackingCode).startsWith("GHN-TEST-");
        assertThat(lichSuVanChuyenRepository.count()).isEqualTo(1);
    }

    @Test
    void successfulDeliveryRecordsRevenueAndCommissionExactlyOnce() {
        sync("picking");
        assertThat(orderStatus()).isEqualTo("DANG_LAY_HANG");
        sync("picked");
        assertThat(orderStatus()).isEqualTo("DA_LAY_HANG");
        sync("delivering");
        assertThat(orderStatus()).isEqualTo("DANG_GIAO");
        assertThat(sync("delivered")).isEqualTo(SyncResult.APPLIED);

        DonHangCuaHang done = donHangCuaHangRepository.findById(order.getMaDHCH()).orElseThrow();
        assertThat(done.getTrangThaiDonHang()).isEqualTo("DA_GIAO");
        assertThat(done.getTrangThaiThanhToanShop()).isEqualTo("DA_THANH_TOAN");
        assertThat(done.getCommissionTien()).isEqualByComparingTo("20000");   // 10% x 200.000
        assertThat(done.getDoanhThuShop()).isEqualByComparingTo("180000");    // 200.000 - hoa hồng
        assertThat(done.getNgayGiao()).isNotNull();
        assertThat(donHangRepository.findById(parent.getMaDH()).orElseThrow().getTrangThaiThanhToan())
                .isEqualTo("DA_THANH_TOAN");
        assertThat(sachRepository.findById(book.getMaSach()).orElseThrow().getSoLuongTon()).isEqualTo(3);
        assertThat(thanhToanRepository.count()).isEqualTo(3);

        // Đơn đã kết thúc thì không tra cứu nữa; kết quả lặp hoặc lạc hậu cũng không chốt tiền lần hai.
        assertThat(sync("delivered")).isEqualTo(SyncResult.NO_CHANGE);
        assertThat(sync("picking")).isEqualTo(SyncResult.NO_CHANGE);
        assertThat(orderStatus()).isEqualTo("DA_GIAO");
        assertThat(thanhToanRepository.count()).isEqualTo(3);
    }

    @Test
    void failedDeliveryIsRetriedAndThirdFailureReturnsGoodsToStockWithoutRevenue() {
        sync("picked");
        sync("delivering");

        // Lần 1, 2: giao thất bại rồi giao lại, đơn con vẫn "Đang giao", chưa hoàn kho.
        for (int lan = 1; lan <= 2; lan++) {
            assertThat(sync("delivery_fail")).isEqualTo(SyncResult.APPLIED);
            assertThat(shipmentStatus()).isEqualTo("GIAO_THAT_BAI");
            assertThat(failCount()).isEqualTo(lan);
            assertThat(orderStatus()).isEqualTo("DANG_GIAO");
            assertThat(sachRepository.findById(book.getMaSach()).orElseThrow().getSoLuongTon()).isEqualTo(3);

            assertThat(sync("delivering")).isEqualTo(SyncResult.APPLIED);
            assertThat(shipmentStatus()).isEqualTo("DANG_GIAO");
        }

        // Hãng không tự hoàn trả được khi chưa hết lượt.
        assertThat(sync("returned")).isEqualTo(SyncResult.IGNORED_INVALID_TRANSITION);
        assertThat(shipmentStatus()).isEqualTo("DANG_GIAO");

        // Lần 3: tự động hoàn trả.
        assertThat(sync("delivery_fail")).isEqualTo(SyncResult.APPLIED);
        assertThat(shipmentStatus()).isEqualTo("DA_HOAN_TRA");
        assertThat(failCount()).isEqualTo(3);

        DonHangCuaHang returned = donHangCuaHangRepository.findById(order.getMaDHCH()).orElseThrow();
        assertThat(returned.getTrangThaiDonHang()).isEqualTo("DA_HOAN_TRA");
        assertThat(returned.getTrangThaiThanhToanShop()).isEqualTo("KHONG_THANH_TOAN");
        assertThat(returned.getCommissionTien()).isEqualByComparingTo("0");
        assertThat(returned.getDoanhThuShop()).isEqualByComparingTo("0");
        assertThat(returned.getNgayHoanTra()).isNotNull();
        assertThat(sachRepository.findById(book.getMaSach()).orElseThrow().getSoLuongTon()).isEqualTo(5);
        assertThat(thanhToanRepository.count()).isZero();
        assertThat(donHangRepository.findById(parent.getMaDH()).orElseThrow().getTrangThaiThanhToan())
                .isEqualTo("KHONG_THANH_TOAN");

        // Đã kết thúc: lần tra cứu sau không cộng tồn kho lần hai.
        assertThat(sync("returned")).isEqualTo(SyncResult.NO_CHANGE);
        assertThat(sachRepository.findById(book.getMaSach()).orElseThrow().getSoLuongTon()).isEqualTo(5);
    }

    @Test
    void unknownOrOutOfOrderCarrierStatusesAreIgnoredAndPollingProcessesActiveShipments() {
        assertThat(sync("some_new_status")).isEqualTo(SyncResult.IGNORED_UNKNOWN_STATUS);
        assertThat(orderStatus()).isEqualTo("DA_XAC_NHAN");

        // Chưa có thông tin mới từ đơn vị vận chuyển thì giữ nguyên
        carrier.statusByCode.remove(trackingCode);
        assertThat(trackingService.pullLatest(order.getMaDHCH())).isEqualTo(SyncResult.NO_CHANGE);

        // Đồng bộ định kỳ quét các vận đơn chưa kết thúc
        carrier.statusByCode.put(trackingCode, "delivering");
        assertThat(trackingService.pollActiveShipments()).isEqualTo(1);
        assertThat(orderStatus()).isEqualTo("DANG_GIAO");
        carrier.statusByCode.put(trackingCode, "delivered");
        assertThat(trackingService.pollActiveShipments()).isEqualTo(1);
        assertThat(orderStatus()).isEqualTo("DA_GIAO");
        assertThat(trackingService.pollActiveShipments()).isZero();
    }

    // ---- cổng đối tác /shipping/{maCode}: dùng chung luồng với đồng bộ API ----

    @Test
    void partnerPortalDrivesSameOrderFlowAndSettlement() {
        assertThat(vanChuyenService.getYeuCauChuaTiepNhan("GHN"))
                .extracting(r -> r.getMaDHCH()).containsExactly(order.getMaDHCH());

        TiepNhanVanChuyenRequest accept = new TiepNhanVanChuyenRequest();
        accept.setMaDHCH(order.getMaDHCH());
        var vc = vanChuyenService.tiepNhan("ghn", accept);           // mã không phân biệt hoa thường
        assertThat(vc.getTrangThaiVanChuyen()).isEqualTo("DANG_LAY_HANG");
        assertThat(vc.getMaVanDon()).isEqualTo(trackingCode);         // giữ mã cấp lúc cửa hàng xác nhận
        assertThat(orderStatus()).isEqualTo("DANG_LAY_HANG");
        assertThat(vanChuyenService.getYeuCauChuaTiepNhan("GHN")).isEmpty();

        portal(vc.getMaVanChuyen(), "DA_LAY_HANG");
        portal(vc.getMaVanChuyen(), "DANG_GIAO");
        var done = portal(vc.getMaVanChuyen(), "DA_GIAO");
        assertThat(done.getTrangThaiTiepTheo()).isEmpty();

        DonHangCuaHang settled = donHangCuaHangRepository.findById(order.getMaDHCH()).orElseThrow();
        assertThat(settled.getTrangThaiDonHang()).isEqualTo("DA_GIAO");
        assertThat(settled.getDoanhThuShop()).isEqualByComparingTo("180000");
        assertThat(thanhToanRepository.count()).isEqualTo(3);
        // xác nhận + 4 lần cập nhật trên cổng
        assertThat(lichSuVanChuyenRepository.count()).isEqualTo(5);
    }

    @Test
    void partnerPortalRejectsOtherCarrierAndInvalidSteps() {
        Integer maVanChuyen = vanChuyenRepository.findByDonHangCuaHang_MaDHCH(order.getMaDHCH())
                .orElseThrow().getMaVanChuyen();

        TiepNhanVanChuyenRequest accept = new TiepNhanVanChuyenRequest();
        accept.setMaDHCH(order.getMaDHCH());
        assertThatThrownBy(() -> vanChuyenService.tiepNhan("JT", accept))
                .isInstanceOf(BusinessException.class).hasMessageContaining("không tồn tại");
        assertThatThrownBy(() -> vanChuyenService.getById("JT", maVanChuyen))
                .isInstanceOf(BusinessException.class);

        // Chưa lấy hàng thì không báo giao thành công được.
        assertThatThrownBy(() -> portal(maVanChuyen, "DA_GIAO"))
                .isInstanceOf(BusinessException.class).hasMessageContaining("Không thể chuyển");
        assertThat(orderStatus()).isEqualTo("DA_XAC_NHAN");
        assertThat(thanhToanRepository.count()).isZero();
    }

    @Test
    void partnerReportingFailedDeliveryAutoReturnsLikeApiSync() {
        TiepNhanVanChuyenRequest accept = new TiepNhanVanChuyenRequest();
        accept.setMaDHCH(order.getMaDHCH());
        Integer id = vanChuyenService.tiepNhan("GHN", accept).getMaVanChuyen();
        portal(id, "DANG_GIAO");

        // max-delivery-attempts = 3: hai lần đầu giao thất bại thì chỉ được giao lại.
        var fail1 = portal(id, "GIAO_THAT_BAI");
        assertThat(fail1.getTrangThaiVanChuyen()).isEqualTo("GIAO_THAT_BAI");
        assertThat(fail1.getSoLanGiaoThatBai()).isEqualTo(1);
        assertThat(fail1.getSoLanGiaoToiDa()).isEqualTo(3);
        assertThat(fail1.getTrangThaiTiepTheo()).containsExactly("DANG_GIAO");
        assertThat(orderStatus()).isEqualTo("DANG_GIAO");

        // Hãng không tự chọn hoàn trả được.
        assertThatThrownBy(() -> portal(id, "DA_HOAN_TRA"))
                .isInstanceOf(BusinessException.class).hasMessageContaining("tự động hoàn trả");

        var retry1 = portal(id, "DANG_GIAO");
        assertThat(retry1.getGhiChuGiaoHang()).isEqualTo("Giao lại lần 2/3");
        portal(id, "GIAO_THAT_BAI");
        portal(id, "DANG_GIAO");
        assertThat(sachRepository.findById(book.getMaSach()).orElseThrow().getSoLuongTon()).isEqualTo(3);

        // Lần 3: tự chuyển hoàn hàng, cộng lại tồn kho.
        var result = portal(id, "GIAO_THAT_BAI");
        assertThat(result.getTrangThaiVanChuyen()).isEqualTo("DA_HOAN_TRA");
        assertThat(result.getSoLanGiaoThatBai()).isEqualTo(3);
        assertThat(result.getTrangThaiTiepTheo()).isEmpty();
        assertThat(orderStatus()).isEqualTo("DA_HOAN_TRA");
        assertThat(sachRepository.findById(book.getMaSach()).orElseThrow().getSoLuongTon()).isEqualTo(5);
        // xác nhận + tiếp nhận + DANG_GIAO + 3 x GIAO_THAT_BAI + 2 x giao lại + hoàn trả (HE_THONG)
        assertThat(lichSuVanChuyenRepository.count()).isEqualTo(9);
    }

    // ---- helpers ----

    private com.oldbook.dto.shipping.VanChuyenResponse portal(Integer maVanChuyen, String trangThai) {
        CapNhatVanChuyenRequest req = new CapNhatVanChuyenRequest();
        req.setTrangThai(trangThai);
        return vanChuyenService.capNhat("GHN", maVanChuyen, req);
    }

    private String shipmentStatus() {
        return vanChuyenRepository.findByDonHangCuaHang_MaDHCH(order.getMaDHCH()).orElseThrow().getTrangThaiVanChuyen();
    }

    private Integer failCount() {
        return vanChuyenRepository.findByDonHangCuaHang_MaDHCH(order.getMaDHCH()).orElseThrow().getSoLanGiaoThatBai();
    }

    private String orderStatus() {
        return donHangCuaHangRepository.findById(order.getMaDHCH()).orElseThrow().getTrangThaiDonHang();
    }

    /** Đơn vị vận chuyển "báo" trạng thái gốc mới, rồi hệ thống gọi API tra cứu. */
    private SyncResult sync(String rawStatus) {
        carrier.statusByCode.put(trackingCode, rawStatus);
        return trackingService.pullLatest(order.getMaDHCH());
    }
}
