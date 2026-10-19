package com.oldbook.payment;

import com.oldbook.entity.catalog.CuaHang;
import com.oldbook.entity.catalog.DanhMuc;
import com.oldbook.entity.catalog.Sach;
import com.oldbook.entity.identity.DiaChi;
import com.oldbook.entity.identity.NguoiDung;
import com.oldbook.entity.identity.TaiKhoan;
import com.oldbook.entity.notification.ThongBao;
import com.oldbook.entity.order.ChiTietGioHang;
import com.oldbook.entity.order.DonHang;
import com.oldbook.entity.order.DonHangCuaHang;
import com.oldbook.entity.order.GioHang;
import com.oldbook.entity.payment.GiaoDichNganHang;
import com.oldbook.entity.payment.PhienThanhToan;
import com.oldbook.entity.payment.ThanhToan;
import com.oldbook.entity.payment.YeuCauChuyenTien;
import com.oldbook.entity.shipping.DonViVanChuyen;
import com.oldbook.entity.shipping.VanChuyen;
import com.oldbook.repository.catalog.DanhMucRepository;
import com.oldbook.repository.catalog.SachRepository;
import com.oldbook.repository.identity.DiaChiRepository;
import com.oldbook.repository.identity.NguoiDungRepository;
import com.oldbook.repository.identity.TaiKhoanRepository;
import com.oldbook.repository.notification.ThongBaoRepository;
import com.oldbook.repository.order.ChiTietGioHangRepository;
import com.oldbook.repository.order.DonHangCuaHangRepository;
import com.oldbook.repository.order.DonHangRepository;
import com.oldbook.repository.order.GioHangRepository;
import com.oldbook.repository.payment.GiaoDichNganHangRepository;
import com.oldbook.repository.payment.PhienThanhToanRepository;
import com.oldbook.repository.payment.ThanhToanRepository;
import com.oldbook.repository.payment.YeuCauChuyenTienRepository;
import com.oldbook.repository.shipping.DonViVanChuyenRepository;
import com.oldbook.repository.shipping.VanChuyenRepository;
import com.oldbook.repository.store.CuaHangRepository;
import com.oldbook.service.auth.JwtService;
import com.oldbook.service.order.DonHangService;
import com.oldbook.service.order.StoreOrderService;
import com.oldbook.service.payment.CassoSignatureVerifier;
import com.oldbook.service.payment.PhienThanhToanService;
import com.oldbook.service.shipping.ShipmentTrackingService;
import com.oldbook.service.shipping.ShippingCarrierClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Thanh toán chuyển khoản từ đầu đến cuối: giữ hàng, tiền về qua webhook Casso, hoàn tiền, chi cho cửa hàng,
 * và đơn COD vẫn chạy như cũ. Mỗi sách giá 100.000 đ, phí vận chuyển GHN 25.000 đ, hoa hồng mặc định 5%.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.config.import=",
                "shipping.sync.polling-enabled=false",
                "payment.bank.bin=970422",
                "payment.bank.account-no=0123456789",
                "payment.bank.account-name=OLDBOOK TEST",
                "payment.casso.checksum-key=" + PaymentFlowIntegrationTest.KHOA,
                // Tác vụ dọn phiên hết hạn chỉ chạy khi test tự gọi.
                "payment.expiry-check-ms=3600000"
        })
@ActiveProfiles({"test", "catalog-test"})
class PaymentFlowIntegrationTest {

    static final String KHOA = "khoa-bao-mat-thu-nghiem";

    private static final BigDecimal GIA_SACH = new BigDecimal("100000");
    private static final BigDecimal PHI_SHIP = new BigDecimal("25000");
    private static final String LY_DO_HUY = "Tôi đặt nhầm cuốn sách này và muốn hủy đơn để chọn lại bản khác.";

    /** Hãng vận chuyển giả: ghi lại số tiền thu hộ được giao và trả về trạng thái test đặt sẵn. */
    @TestConfiguration
    static class CarrierConfig {
        @Bean
        @Primary
        RecordingCarrierClient recordingCarrierClient() {
            return new RecordingCarrierClient();
        }
    }

    static class RecordingCarrierClient implements ShippingCarrierClient {
        final Map<String, String> statusByCode = new ConcurrentHashMap<>();
        final Map<Integer, BigDecimal> codByOrder = new ConcurrentHashMap<>();
        final AtomicInteger seq = new AtomicInteger();

        @Override
        public CarrierShipment createShipment(CarrierShipmentRequest request) {
            codByOrder.put(request.maDHCH(), request.tienThuHo());
            return new CarrierShipment("PAY-TEST-" + seq.incrementAndGet());
        }

        @Override
        public Optional<CarrierTracking> track(VanChuyen shipment) {
            String raw = statusByCode.get(shipment.getMaVanDon());
            return raw == null ? Optional.empty() : Optional.of(new CarrierTracking(raw, null, null));
        }
    }

    record Actor(Integer maND, Integer maTK, String token) {
    }

    @Value("${local.server.port}")
    private int port;
    @Autowired private ObjectMapper mapper;
    @Autowired private JwtService jwtService;
    @Autowired private TransactionTemplate transactions;
    @Autowired private RecordingCarrierClient carrier;
    @Autowired private PhienThanhToanService phienService;
    @Autowired private DonHangService donHangService;
    @Autowired private StoreOrderService storeOrderService;
    @Autowired private ShipmentTrackingService trackingService;
    @Autowired private NguoiDungRepository nguoiDungRepository;
    @Autowired private TaiKhoanRepository taiKhoanRepository;
    @Autowired private DiaChiRepository diaChiRepository;
    @Autowired private CuaHangRepository cuaHangRepository;
    @Autowired private DanhMucRepository danhMucRepository;
    @Autowired private SachRepository sachRepository;
    @Autowired private GioHangRepository gioHangRepository;
    @Autowired private ChiTietGioHangRepository chiTietGioHangRepository;
    @Autowired private DonViVanChuyenRepository donViVanChuyenRepository;
    @Autowired private DonHangRepository donHangRepository;
    @Autowired private DonHangCuaHangRepository donHangCuaHangRepository;
    @Autowired private VanChuyenRepository vanChuyenRepository;
    @Autowired private ThanhToanRepository thanhToanRepository;
    @Autowired private PhienThanhToanRepository phienRepository;
    @Autowired private GiaoDichNganHangRepository giaoDichRepository;
    @Autowired private YeuCauChuyenTienRepository yeuCauRepository;
    @Autowired private ThongBaoRepository thongBaoRepository;

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private static final AtomicInteger SEQ = new AtomicInteger();
    private static final AtomicLong MA_GIAO_DICH = new AtomicLong(700000);

    private DanhMuc category;
    private DonViVanChuyen ghn;
    private Actor buyer;
    private Actor owner;
    private Actor manager;
    private CuaHang shop;
    private Sach book;
    private DiaChi address;

    @BeforeEach
    void seed() {
        if (category == null) {
            category = danhMucRepository.save(DanhMuc.builder().tenDanhMuc("Thanh toán " + SEQ.incrementAndGet()).build());
        }
        ghn = donViVanChuyenRepository.findAll().stream()
                .filter(c -> "GHN".equals(c.getMaCode())).findFirst().orElseThrow();

        buyer = actor("KHACH_HANG");
        owner = actor("CHU_CUA_HANG");
        manager = actor("QUAN_LY");
        shop = shop(owner);
        book = book(shop, 1);
        address = diaChiRepository.save(DiaChi.builder()
                .nguoiDung(nguoiDungRepository.getReferenceById(buyer.maND()))
                .tenNguoiNhan("Nguyễn Văn Khách").soDienThoai("0911111111")
                .tinhThanh("TP.HCM").quanHuyen("Thủ Đức").phuongXa("Linh Chiểu")
                .diaChiChiTiet("1 Võ Văn Ngân").laMacDinh(true).build());
    }

    // ------------------------------------------------------------------
    // Tạo phiên và giữ hàng
    // ------------------------------------------------------------------

    @Test
    void creatingASessionHoldsStockButKeepsTheCartAndCreatesNoOrder() throws Exception {
        Integer line = addToCart(buyer, book, 1);
        long ordersBefore = donHangRepository.count();

        var response = request("POST", "/api/payment/sessions", buyer.token(), orderBody(buyer, line));
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        JsonNode session = json(response).get("data");

        assertThat(session.get("trangThai").asText()).isEqualTo("CHO_THANH_TOAN");
        assertThat(session.get("soTien").decimalValue()).isEqualByComparingTo("125000");
        assertThat(session.get("maThanhToan").asText()).matches("OB[2-9A-HJ-NP-Z]{8}");
        assertThat(session.get("conLaiGiay").asLong()).isBetween(240L, 300L);
        assertThat(session.get("qrUrl").asText())
                .startsWith("https://img.vietqr.io/image/970422-0123456789-compact2.png")
                .contains("amount=125000")
                .contains("addInfo=" + session.get("maThanhToan").asText());

        assertThat(stock(book)).isZero();
        assertThat(chiTietGioHangRepository.findById(line)).isPresent();
        assertThat(donHangRepository.count()).isEqualTo(ordersBefore);

        // Trang QR hỏi lại trạng thái bằng đúng tài khoản đó; người khác không xem được.
        Integer maPhien = session.get("maPhien").asInt();
        assertThat(json(request("GET", "/api/payment/sessions/" + maPhien, buyer.token(), null))
                .get("data").get("trangThai").asText()).isEqualTo("CHO_THANH_TOAN");
        assertThat(request("GET", "/api/payment/sessions/" + maPhien, actor("KHACH_HANG").token(), null).statusCode())
                .isEqualTo(400);
    }

    @Test
    void aSecondBuyerCannotStartPayingForTheLastCopyWhileItIsHeld() throws Exception {
        createSession(buyer, addToCart(buyer, book, 1));

        Actor other = actor("KHACH_HANG");
        diaChiRepository.save(DiaChi.builder().nguoiDung(nguoiDungRepository.getReferenceById(other.maND()))
                .tenNguoiNhan("Khách hai").soDienThoai("0922222222").tinhThanh("Hà Nội")
                .diaChiChiTiet("2 Tràng Tiền").laMacDinh(true).build());
        Integer otherLine = addToCart(other, book, 1);
        DiaChi otherAddress = diaChiRepository.findAll().stream()
                .filter(d -> d.getNguoiDung().getMaND().equals(other.maND())).findFirst().orElseThrow();

        var response = request("POST", "/api/payment/sessions", other.token(),
                orderBody(otherAddress.getMaDiaChi(), List.of(otherLine), List.of(shop)));

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(json(response).get("message").asText()).contains("không đủ tồn kho");
        assertThat(stock(book)).isZero();
    }

    @Test
    void startingAgainReleasesTheBuyersOwnPreviousHold() throws Exception {
        Integer line = addToCart(buyer, book, 1);
        JsonNode first = createSession(buyer, line);
        JsonNode second = createSession(buyer, line);

        assertThat(phien(first).getTrangThai()).isEqualTo("DA_HUY");
        assertThat(phien(second).getTrangThai()).isEqualTo("CHO_THANH_TOAN");
        assertThat(stock(book)).isZero();

        assertThat(request("DELETE", "/api/payment/sessions/" + second.get("maPhien").asInt(), buyer.token(), null)
                .statusCode()).isEqualTo(200);
        assertThat(phien(second).getTrangThai()).isEqualTo("DA_HUY");
        assertThat(stock(book)).isEqualTo(1);
    }

    @Test
    void expiredSessionReturnsTheStock() throws Exception {
        JsonNode session = createSession(buyer, addToCart(buyer, book, 1));
        expire(session);

        phienService.donPhienHetHan();

        assertThat(phien(session).getTrangThai()).isEqualTo("HET_HAN");
        assertThat(stock(book)).isEqualTo(1);

        // Chạy lại cũng không cộng tồn kho lần hai.
        phienService.donPhienHetHan();
        assertThat(stock(book)).isEqualTo(1);
    }

    // ------------------------------------------------------------------
    // Tiền về
    // ------------------------------------------------------------------

    @Test
    void paidWebhookCreatesThePrepaidOrderExactlyOnce() throws Exception {
        Integer line = addToCart(buyer, book, 1);
        JsonNode session = createSession(buyer, line);
        long id = MA_GIAO_DICH.incrementAndGet();
        String body = cassoBody(id, "MBVCB.99887766. " + session.get("maThanhToan").asText() + " mua sách cũ", "125000");

        assertThat(webhook(body).statusCode()).isEqualTo(200);

        PhienThanhToan paid = phien(session);
        assertThat(paid.getTrangThai()).isEqualTo("DA_THANH_TOAN");
        DonHang order = transactions.execute(s -> donHangRepository.findById(
                phienRepository.findById(paid.getMaPhien()).orElseThrow().getDonHang().getMaDH()).orElseThrow());
        assertThat(order.getPhuongThucTT()).isEqualTo("CHUYEN_KHOAN");
        assertThat(order.getTrangThaiThanhToan()).isEqualTo("DA_THANH_TOAN");
        assertThat(order.getTongThanhToan()).isEqualByComparingTo("125000");
        DonHangCuaHang child = children(order).get(0);
        assertThat(child.getTrangThaiDonHang()).isEqualTo("CHO_XU_LY");
        assertThat(child.getTrangThaiThanhToanShop()).isEqualTo("DA_THANH_TOAN");

        // Hàng giữ được chuyển thành hàng đã bán: không bị trừ hai lần, giỏ mất dòng đã mua.
        assertThat(stock(book)).isZero();
        assertThat(chiTietGioHangRepository.findById(line)).isEmpty();
        assertThat(payments(order)).extracting(ThanhToan::getLoaiGiaoDich).containsExactly("KHACH_CHUYEN_KHOAN");
        assertThat(ledger(id).getKetQua()).isEqualTo("KHOP");
        assertThat(notifications(buyer)).contains("THANH_TOAN_THANH_CONG");

        // Casso gửi lại đúng giao dịch đó.
        long orders = donHangRepository.count();
        assertThat(webhook(body).statusCode()).isEqualTo(200);
        assertThat(donHangRepository.count()).isEqualTo(orders);
        assertThat(yeuCauRepository.findAllByLoaiAndNguoiNhan_MaNDOrderByMaYeuCauDesc("HOAN_TIEN_KHACH", buyer.maND()))
                .isEmpty();

        // Trang QR thấy mã đơn để chuyển sang trang đặt hàng thành công.
        JsonNode status = json(request("GET", "/api/payment/sessions/" + paid.getMaPhien(), buyer.token(), null)).get("data");
        assertThat(status.get("maDH").asInt()).isEqualTo(order.getMaDH());
    }

    @Test
    void webhookWithWrongOrMissingSignatureIsRejectedAndChangesNothing() throws Exception {
        JsonNode session = createSession(buyer, addToCart(buyer, book, 1));
        long ledgerBefore = giaoDichRepository.count();
        String body = cassoBody(MA_GIAO_DICH.incrementAndGet(), session.get("maThanhToan").asText(), "125000");

        assertThat(send(body, "t=1,v1=" + "0".repeat(128)).statusCode()).isEqualTo(401);
        assertThat(send(body, null).statusCode()).isEqualTo(401);
        // Chữ ký đúng cho nội dung khác thì cũng không dùng lại được.
        String otherSignature = signature(cassoBody(1, "khac", "1"));
        assertThat(send(body, otherSignature).statusCode()).isEqualTo(401);

        assertThat(giaoDichRepository.count()).isEqualTo(ledgerBefore);
        assertThat(phien(session).getTrangThai()).isEqualTo("CHO_THANH_TOAN");
    }

    @Test
    void underpaymentCreatesNoOrderReleasesStockAndRaisesARefund() throws Exception {
        Integer line = addToCart(buyer, book, 1);
        JsonNode session = createSession(buyer, line);
        long orders = donHangRepository.count();
        long id = MA_GIAO_DICH.incrementAndGet();

        assertThat(webhook(cassoBody(id, session.get("maThanhToan").asText(), "100000")).statusCode()).isEqualTo(200);

        assertThat(donHangRepository.count()).isEqualTo(orders);
        assertThat(phien(session).getTrangThai()).isEqualTo("CAN_HOAN_TIEN");
        assertThat(stock(book)).isEqualTo(1);
        assertThat(chiTietGioHangRepository.findById(line)).isPresent();
        assertThat(ledger(id).getKetQua()).isEqualTo("CAN_HOAN_TIEN");

        YeuCauChuyenTien refund = refunds(buyer).get(0);
        assertThat(refund.getSoTien()).isEqualByComparingTo("100000");
        assertThat(refund.getTrangThai()).isEqualTo("CHO_THONG_TIN");
        // Tài khoản người chuyển được điền sẵn, nhưng khách vẫn phải xác nhận.
        assertThat(refund.getSoTaiKhoan()).isEqualTo("8888888888");
        assertThat(notifications(buyer)).contains("CAN_TAI_KHOAN_HOAN_TIEN");
        assertThat(notifications(manager)).contains("CAN_HOAN_TIEN");
    }

    @Test
    void paymentForACartThatChangedMeanwhileIsRefundedInFull() throws Exception {
        Integer line = addToCart(buyer, book, 1);
        JsonNode session = createSession(buyer, line);
        chiTietGioHangRepository.deleteById(line);
        long orders = donHangRepository.count();

        webhook(cassoBody(MA_GIAO_DICH.incrementAndGet(), session.get("maThanhToan").asText(), "125000"));

        assertThat(donHangRepository.count()).isEqualTo(orders);
        assertThat(phien(session).getTrangThai()).isEqualTo("CAN_HOAN_TIEN");
        assertThat(stock(book)).isEqualTo(1);
        assertThat(refunds(buyer).get(0).getSoTien()).isEqualByComparingTo("125000");
    }

    @Test
    void latePaymentAfterExpiryStillCreatesTheOrderWhenTheBookIsAvailable() throws Exception {
        Integer line = addToCart(buyer, book, 1);
        JsonNode session = createSession(buyer, line);
        expire(session);
        phienService.donPhienHetHan();
        assertThat(stock(book)).isEqualTo(1);

        webhook(cassoBody(MA_GIAO_DICH.incrementAndGet(), session.get("maThanhToan").asText(), "125000"));

        assertThat(phien(session).getTrangThai()).isEqualTo("DA_THANH_TOAN");
        assertThat(stock(book)).isZero();
        assertThat(refunds(buyer)).isEmpty();
    }

    @Test
    void secondPaymentForAnAlreadyPaidCodeKeepsTheOrderAndRefundsTheExtraTransfer() throws Exception {
        JsonNode session = createSession(buyer, addToCart(buyer, book, 1));
        String code = session.get("maThanhToan").asText();
        webhook(cassoBody(MA_GIAO_DICH.incrementAndGet(), code, "125000"));
        long orders = donHangRepository.count();

        webhook(cassoBody(MA_GIAO_DICH.incrementAndGet(), code, "125000"));

        assertThat(donHangRepository.count()).isEqualTo(orders);
        assertThat(phien(session).getTrangThai()).isEqualTo("DA_THANH_TOAN");
        assertThat(stock(book)).isZero();
        assertThat(refunds(buyer)).hasSize(1);
        assertThat(refunds(buyer).get(0).getSoTien()).isEqualByComparingTo("125000");
    }

    @Test
    void transfersWithoutAPaymentCodeAreOnlyRecorded() throws Exception {
        long id = MA_GIAO_DICH.incrementAndGet();
        long orders = donHangRepository.count();

        assertThat(webhook(cassoBody(id, "chuyen tien an trua", "50000")).statusCode()).isEqualTo(200);
        assertThat(ledger(id).getKetQua()).isEqualTo("KHONG_KHOP");
        assertThat(donHangRepository.count()).isEqualTo(orders);
    }

    // ------------------------------------------------------------------
    // Hoàn tiền
    // ------------------------------------------------------------------

    @Test
    void cancellingAPrepaidOrderRaisesARefundThatTheManagerSettles() throws Exception {
        DonHangCuaHang child = paidOrder(buyer, book);

        donHangService.cancelStoreOrder(buyer.maND(), child.getMaDHCH(), LY_DO_HUY);

        DonHangCuaHang cancelled = donHangCuaHangRepository.findById(child.getMaDHCH()).orElseThrow();
        assertThat(cancelled.getTrangThaiDonHang()).isEqualTo("DA_HUY");
        assertThat(cancelled.getTienHoanTra()).isEqualByComparingTo("125000");
        assertThat(parentStatus(child)).isEqualTo("KHONG_THANH_TOAN");
        assertThat(stock(book)).isEqualTo(1);

        YeuCauChuyenTien refund = refunds(buyer).get(0);
        assertThat(refund.getSoTien()).isEqualByComparingTo("125000");
        // Casso có gửi kèm tài khoản người chuyển nên khoản hoàn vào thẳng CHO_XU_LY, không cần khách nhập lại.
        assertThat(refund.getTrangThai()).isEqualTo("CHO_XU_LY");
        assertThat(refund.getSoTaiKhoan()).isEqualTo("8888888888");
        assertThat(notifications(buyer)).doesNotContain("CAN_TAI_KHOAN_HOAN_TIEN");
        assertThat(notifications(manager)).contains("CAN_HOAN_TIEN");

        // Khách vẫn đổi được sang tài khoản khác trước khi Quản lý chuyển.
        var account = request("PUT", "/api/payment/refunds/" + refund.getMaYeuCau() + "/account", buyer.token(),
                Map.of("maNganHang", "970415", "soTaiKhoan", "1029384756", "tenChuTaiKhoan", "NGUYEN VAN KHACH"));
        assertThat(account.statusCode()).as(account.body()).isEqualTo(200);
        assertThat(json(account).get("data").get("trangThai").asText()).isEqualTo("CHO_XU_LY");

        // Quản lý thấy khoản này kèm mã QR tới tài khoản của khách với nội dung HT<mã>.
        JsonNode list = json(request("GET", "/api/management/payments/transfers?loai=HOAN_TIEN_KHACH&trangThai=CHO_XU_LY",
                manager.token(), null)).get("data").get("content");
        JsonNode row = find(list, refund.getMaYeuCau());
        assertThat(row.get("noiDungChuyenKhoan").asText()).isEqualTo("HT" + refund.getMaYeuCau());
        assertThat(row.get("qrUrl").asText())
                .startsWith("https://img.vietqr.io/image/970415-1029384756-compact2.png")
                .contains("amount=125000").contains("addInfo=HT" + refund.getMaYeuCau());

        var done = request("POST", "/api/management/payments/transfers/" + refund.getMaYeuCau() + "/done",
                manager.token(), null);
        assertThat(done.statusCode()).as(done.body()).isEqualTo(200);
        assertThat(yeuCauRepository.findById(refund.getMaYeuCau()).orElseThrow().getTrangThai()).isEqualTo("DA_CHUYEN");
        assertThat(notifications(buyer)).contains("DA_HOAN_TIEN");
        assertThat(shopOrderLedger(child)).contains("HOAN_TIEN");

        // Bấm lần hai bị từ chối: không ghi hai lần.
        assertThat(request("POST", "/api/management/payments/transfers/" + refund.getMaYeuCau() + "/done",
                manager.token(), null).statusCode()).isEqualTo(400);
    }

    @Test
    void failedDeliveryOfAPrepaidOrderRaisesARefund() throws Exception {
        DonHangCuaHang child = paidOrder(buyer, book);
        String tracking = confirm(child);

        deliver(child, tracking, "picked", "delivering");
        // Số lần giao lại trước khi hoàn hàng là cấu hình của phần vận chuyển, nên cứ báo thất bại cho tới khi đơn bị hoàn.
        for (int lan = 0; lan < 10 && !"DA_HOAN_TRA".equals(orderStatus(child)); lan++) {
            deliver(child, tracking, "delivery_fail");
            if (!"DA_HOAN_TRA".equals(orderStatus(child))) {
                deliver(child, tracking, "delivering");
            }
        }

        assertThat(orderStatus(child)).isEqualTo("DA_HOAN_TRA");
        assertThat(refunds(buyer)).hasSize(1);
        assertThat(refunds(buyer).get(0).getSoTien()).isEqualByComparingTo("125000");
        assertThat(parentStatus(child)).isEqualTo("KHONG_THANH_TOAN");
        assertThat(stock(book)).isEqualTo(1);
    }

    @Test
    void cancellingOneShopOfATwoShopOrderRefundsOnlyThatShop() throws Exception {
        Actor owner2 = actor("CHU_CUA_HANG");
        CuaHang shop2 = shop(owner2);
        Sach book2 = book(shop2, 1);
        Integer line1 = addToCart(buyer, book, 1);
        Integer line2 = addToCart(buyer, book2, 1);

        var response = request("POST", "/api/payment/sessions", buyer.token(),
                orderBody(address.getMaDiaChi(), List.of(line1, line2), List.of(shop, shop2)));
        JsonNode session = json(response).get("data");
        // Một mã QR cho cả hai cửa hàng.
        assertThat(session.get("soTien").decimalValue()).isEqualByComparingTo("250000");
        webhook(cassoBody(MA_GIAO_DICH.incrementAndGet(), session.get("maThanhToan").asText(), "250000"));

        DonHang order = orderOf(session);
        List<DonHangCuaHang> kids = children(order);
        assertThat(kids).hasSize(2);

        donHangService.cancelStoreOrder(buyer.maND(), kids.get(0).getMaDHCH(), LY_DO_HUY);

        assertThat(refunds(buyer)).hasSize(1);
        assertThat(refunds(buyer).get(0).getSoTien()).isEqualByComparingTo("125000");
        assertThat(donHangRepository.findById(order.getMaDH()).orElseThrow().getTrangThaiThanhToan())
                .isEqualTo("THANH_TOAN_MOT_PHAN");
        assertThat(donHangCuaHangRepository.findById(kids.get(1).getMaDHCH()).orElseThrow().getTrangThaiThanhToanShop())
                .isEqualTo("DA_THANH_TOAN");
    }

    // ------------------------------------------------------------------
    // Giao thành công: không thu hộ, chi cho cửa hàng
    // ------------------------------------------------------------------

    @Test
    void deliveredPrepaidOrderCollectsNothingAndRaisesAPayoutForTheShop() throws Exception {
        DonHangCuaHang child = paidOrder(buyer, book);
        String tracking = confirm(child);
        assertThat(carrier.codByOrder.get(child.getMaDHCH())).isEqualByComparingTo("0");

        deliver(child, tracking, "picked", "delivering", "delivered");

        DonHangCuaHang done = donHangCuaHangRepository.findById(child.getMaDHCH()).orElseThrow();
        assertThat(done.getTrangThaiDonHang()).isEqualTo("DA_GIAO");
        assertThat(done.getCommissionTien()).isEqualByComparingTo("5000");
        assertThat(done.getDoanhThuShop()).isEqualByComparingTo("95000");
        assertThat(parentStatus(child)).isEqualTo("DA_THANH_TOAN");
        assertThat(shopOrderLedger(child))
                .containsExactlyInAnyOrder("HOA_HONG", "DOANH_THU_SHOP");

        YeuCauChuyenTien payout = transactions.execute(s -> yeuCauRepository
                .findAllByLoaiAndNguoiNhan_MaNDOrderByMaYeuCauDesc("CHI_CUA_HANG", owner.maND()).get(0));
        assertThat(payout.getSoTien()).isEqualByComparingTo("95000");
        assertThat(payout.getTrangThai()).isEqualTo("CHO_XU_LY");
        assertThat(notifications(manager)).contains("CAN_CHI_CUA_HANG");
        assertThat(notifications(owner)).contains("CAN_TAI_KHOAN_NHAN_TIEN");

        // Cửa hàng chưa khai tài khoản thì chưa đánh dấu đã chi được.
        String donePath = "/api/management/payments/transfers/" + payout.getMaYeuCau() + "/done";
        assertThat(request("POST", donePath, manager.token(), null).statusCode()).isEqualTo(400);

        transactions.executeWithoutResult(s -> {
            CuaHang current = cuaHangRepository.findById(shop.getMaCH()).orElseThrow();
            current.setMaNganHang("970436");
            current.setSoTaiKhoan("0071000123456");
            current.setTenChuTaiKhoan("TRAN THI CHU SHOP");
            cuaHangRepository.save(current);
        });

        JsonNode row = find(json(request("GET", "/api/management/payments/transfers?loai=CHI_CUA_HANG",
                manager.token(), null)).get("data").get("content"), payout.getMaYeuCau());
        assertThat(row.get("qrUrl").asText())
                .startsWith("https://img.vietqr.io/image/970436-0071000123456-compact2.png")
                .contains("amount=95000").contains("addInfo=CT" + payout.getMaYeuCau());

        assertThat(request("POST", donePath, manager.token(), null).statusCode()).isEqualTo(200);
        assertThat(notifications(owner)).contains("DA_CHI_CUA_HANG");
    }

    // ------------------------------------------------------------------
    // Cổng Quản lý: chi gộp theo cửa hàng, nhắc khai tài khoản, lọc giao dịch, hoa hồng
    // ------------------------------------------------------------------

    private void giveShopABankAccount() {
        transactions.executeWithoutResult(s -> {
            CuaHang current = cuaHangRepository.findById(shop.getMaCH()).orElseThrow();
            current.setMaNganHang("970436");
            current.setSoTaiKhoan("0071000123456");
            current.setTenChuTaiKhoan("TRAN THI CHU SHOP");
            cuaHangRepository.save(current);
        });
    }

    private DonHangCuaHang deliveredPaidOrder(Sach what) throws Exception {
        DonHangCuaHang child = paidOrder(buyer, what);
        deliver(child, confirm(child), "picked", "delivering", "delivered");
        return child;
    }

    private List<Integer> payoutIds(Actor who) {
        return transactions.execute(s -> yeuCauRepository
                .findAllByLoaiAndNguoiNhan_MaNDOrderByMaYeuCauDesc("CHI_CUA_HANG", who.maND()).stream()
                .map(YeuCauChuyenTien::getMaYeuCau).toList());
    }

    @Test
    void managerSettlesSeveralOrdersOfOneShopInASingleBatch() throws Exception {
        Sach second = book(shop, 1);
        deliveredPaidOrder(book);
        deliveredPaidOrder(second);
        giveShopABankAccount();
        List<Integer> ids = payoutIds(owner);
        assertThat(ids).hasSize(2);

        // Gom nhóm theo cửa hàng: một dòng cửa hàng, hai đơn con, tổng thực nhận 190.000.
        JsonNode groups = json(request("GET", "/api/management/payments/store-payouts", manager.token(), null)).get("data");
        JsonNode group = null;
        for (JsonNode g : groups) {
            if (g.get("maCH").asInt() == shop.getMaCH()) { group = g; }
        }
        assertThat(group).isNotNull();
        assertThat(group.get("donCon")).hasSize(2);
        assertThat(group.get("tongThucNhan").decimalValue()).isEqualByComparingTo("190000");
        assertThat(group.get("tongHoaHong").decimalValue()).isEqualByComparingTo("10000");
        assertThat(group.get("coTaiKhoan").asBoolean()).isTrue();

        JsonNode preview = json(request("POST", "/api/management/payments/store-payouts/preview", manager.token(),
                Map.of("maYeuCau", ids))).get("data");
        String maDotChi = preview.get("maDotChi").asText();
        assertThat(maDotChi).startsWith("DC").hasSize(10);
        assertThat(preview.get("tongTien").decimalValue()).isEqualByComparingTo("190000");
        assertThat(preview.get("qrUrl").asText()).contains("amount=190000").contains("addInfo=" + maDotChi);

        // Mã đợt chi sai (hoặc danh sách đã đổi) thì bị từ chối và không khoản nào bị ghi.
        assertThat(request("POST", "/api/management/payments/store-payouts/done", manager.token(),
                Map.of("maDotChi", "DCSAISAI2", "maYeuCau", ids)).statusCode()).isEqualTo(400);
        assertThat(request("POST", "/api/management/payments/store-payouts/done", manager.token(),
                Map.of("maDotChi", maDotChi, "maYeuCau", List.of(ids.get(0)))).statusCode()).isEqualTo(400);
        Boolean allStillWaiting = transactions.execute(s -> yeuCauRepository.findAllById(ids).stream()
                .allMatch(y -> "CHO_XU_LY".equals(y.getTrangThai())));
        assertThat(allStillWaiting).isTrue();

        assertThat(request("POST", "/api/management/payments/store-payouts/done", manager.token(),
                Map.of("maDotChi", maDotChi, "maYeuCau", ids)).statusCode()).isEqualTo(200);

        transactions.executeWithoutResult(s -> {
            for (YeuCauChuyenTien y : yeuCauRepository.findAllById(ids)) {
                assertThat(y.getTrangThai()).isEqualTo("DA_CHUYEN");
                assertThat(y.getMaDotChi()).isEqualTo(maDotChi);
                assertThat(thanhToanRepository.existsByDonHangCuaHang_MaDHCHAndLoaiGiaoDich(
                        y.getDonHangCuaHang().getMaDHCH(), "CHI_CUA_HANG")).isTrue();
            }
        });
        assertThat(notifications(owner)).contains("DA_CHI_CUA_HANG");

        // Chuyển lần hai bị từ chối.
        assertThat(request("POST", "/api/management/payments/store-payouts/done", manager.token(),
                Map.of("maDotChi", maDotChi, "maYeuCau", ids)).statusCode()).isEqualTo(400);
    }

    @Test
    void aBatchCannotMixShopsOrIncludeAnythingButShopPayouts() throws Exception {
        Actor owner2 = actor("CHU_CUA_HANG");
        CuaHang shop2 = shop(owner2);
        Sach book2 = book(shop2, 1);
        deliveredPaidOrder(book);
        // Đơn của cửa hàng thứ hai
        var created = request("POST", "/api/payment/sessions", buyer.token(),
                orderBody(address.getMaDiaChi(), List.of(addToCart(buyer, book2, 1)), List.of(shop2)));
        assertThat(created.statusCode()).as(created.body()).isEqualTo(200);
        JsonNode session = json(created).get("data");
        webhook(cassoBody(MA_GIAO_DICH.incrementAndGet(), session.get("maThanhToan").asText(),
                session.get("soTien").decimalValue().toPlainString()));
        DonHangCuaHang other = children(orderOf(session)).get(0);
        deliver(other, confirm(other), "picked", "delivering", "delivered");
        giveShopABankAccount();

        List<Integer> mixed = new ArrayList<>(payoutIds(owner));
        mixed.addAll(payoutIds(owner2));
        assertThat(mixed).hasSize(2);
        assertThat(request("POST", "/api/management/payments/store-payouts/preview", manager.token(),
                Map.of("maYeuCau", mixed)).statusCode()).isEqualTo(400);
        assertThat(request("POST", "/api/management/payments/store-payouts/preview", manager.token(),
                Map.of("maYeuCau", List.of())).statusCode()).isEqualTo(400);
        // Cửa hàng chưa khai tài khoản thì chưa tạo được đợt chi.
        assertThat(request("POST", "/api/management/payments/store-payouts/preview", manager.token(),
                Map.of("maYeuCau", payoutIds(owner2))).statusCode()).isEqualTo(400);
        assertThat(request("POST", "/api/management/payments/store-payouts/preview", manager.token(),
                Map.of("maYeuCau", payoutIds(owner))).statusCode()).isEqualTo(200);
    }

    @Test
    void remindingForABankAccountIsLimitedToOncePerHour() throws Exception {
        deliveredPaidOrder(book);

        String remindStore = "/api/management/payments/stores/" + shop.getMaCH() + "/remind-bank";
        assertThat(request("POST", remindStore, manager.token(), null).statusCode()).isEqualTo(200);
        assertThat(notifications(owner)).contains("NHAC_KHAI_TAI_KHOAN");
        assertThat(request("POST", remindStore, manager.token(), null).statusCode()).isEqualTo(400);

        // Khách có khoản hoàn đang chờ khai tài khoản: nhắc một lần, lần hai trong giờ bị từ chối.
        Integer line = addToCart(buyer, book(shop, 1), 1);
        JsonNode session = createSession(buyer, line);
        webhook(cassoBody(MA_GIAO_DICH.incrementAndGet(), session.get("maThanhToan").asText(), "100000"));
        YeuCauChuyenTien refund = transactions.execute(s -> refunds(buyer).get(0));
        assertThat(refund.getTrangThai()).isEqualTo("CHO_THONG_TIN");
        String remindRefund = "/api/management/payments/refunds/" + refund.getMaYeuCau() + "/remind-bank";
        assertThat(request("POST", remindRefund, manager.token(), null).statusCode()).isEqualTo(200);
        assertThat(notifications(buyer)).contains("NHAC_KHAI_TAI_KHOAN");
        assertThat(request("POST", remindRefund, manager.token(), null).statusCode()).isEqualTo(400);

        // Cửa hàng đã khai tài khoản thì không cần nhắc nữa.
        giveShopABankAccount();
        assertThat(request("POST", remindStore, manager.token(), null).statusCode()).isEqualTo(400);
    }

    @Test
    void unmatchedTransfersNotifyManagersAndTheLedgerCanBeFiltered() throws Exception {
        long lech = MA_GIAO_DICH.incrementAndGet();
        assertThat(webhook(cassoBody(lech, "chuyen tien an trua ZZTEST", "50000")).statusCode()).isEqualTo(200);
        assertThat(notifications(manager)).contains("GIAO_DICH_LECH");

        DonHangCuaHang child = paidOrder(buyer, book);
        assertThat(child).isNotNull();

        JsonNode all = json(request("GET", "/api/management/payments/transactions?tuKhoa=ZZTEST", manager.token(), null))
                .get("data");
        assertThat(all.get("content")).hasSize(1);
        assertThat(all.get("tomTat").get("tongSo").asInt()).isEqualTo(1);
        assertThat(all.get("tomTat").get("tongTienLech").decimalValue()).isEqualByComparingTo("50000");
        assertThat(all.get("tomTat").get("tongTienKhop").decimalValue()).isEqualByComparingTo("0");

        JsonNode matched = json(request("GET", "/api/management/payments/transactions?ketQua=KHOP&size=100",
                manager.token(), null)).get("data");
        assertThat(matched.get("tomTat").get("tongTienKhop").decimalValue()).isGreaterThanOrEqualTo(new BigDecimal("125000"));
        for (JsonNode row : matched.get("content")) {
            assertThat(row.get("ketQua").asText()).isEqualTo("KHOP");
        }

        // Lọc theo ngày: kỳ trong quá khứ không có giao dịch nào.
        JsonNode old = json(request("GET", "/api/management/payments/transactions?tuNgay=2001-01-01&denNgay=2001-01-02",
                manager.token(), null)).get("data");
        assertThat(old.get("content")).isEmpty();
        assertThat(old.get("tomTat").get("tongSo").asInt()).isZero();
    }

    @Test
    void refundListCanBeSearchedAndCarriesTheCancellationReason() throws Exception {
        DonHangCuaHang child = paidOrder(buyer, book);
        var cancelled = request("POST", "/api/order/orders/store-orders/" + child.getMaDHCH() + "/cancel",
                buyer.token(), Map.of("lyDo", LY_DO_HUY));
        assertThat(cancelled.statusCode()).as(cancelled.body()).isEqualTo(200);

        JsonNode hit = json(request("GET", "/api/management/payments/transfers?loai=HOAN_TIEN_KHACH&tuKhoa="
                + child.getMaDHCH(), manager.token(), null)).get("data").get("content");
        assertThat(hit).hasSize(1);
        assertThat(hit.get(0).get("huyBoi").asText()).isEqualTo("KHACH_HANG");
        assertThat(hit.get(0).get("lyDoHuy").asText()).isEqualTo(LY_DO_HUY);

        JsonNode miss = json(request("GET", "/api/management/payments/transfers?loai=HOAN_TIEN_KHACH&tuKhoa=khongcoai",
                manager.token(), null)).get("data").get("content");
        assertThat(miss).isEmpty();
    }

    @Test
    void changingACommissionClosesTheOldRecordAndAppliesToLaterDeliveries() throws Exception {
        String url = "/api/management/commissions/" + category.getMaDM();

        assertThat(request("PUT", url, manager.token(), Map.of("tyLe", 10, "ghiChu", "Khuyến khích bán")).statusCode())
                .isEqualTo(200);
        DonHangCuaHang first = deliveredPaidOrder(book);
        assertThat(donHangCuaHangRepository.findById(first.getMaDHCH()).orElseThrow().getCommissionTien())
                .isEqualByComparingTo("10000");

        assertThat(request("PUT", url, manager.token(), Map.of("tyLe", 20)).statusCode()).isEqualTo(200);
        DonHangCuaHang second = deliveredPaidOrder(book(shop, 1));
        assertThat(donHangCuaHangRepository.findById(second.getMaDHCH()).orElseThrow().getCommissionTien())
                .isEqualByComparingTo("20000");
        // Đơn đã giao trước khi đổi giữ nguyên tỷ lệ cũ.
        assertThat(donHangCuaHangRepository.findById(first.getMaDHCH()).orElseThrow().getCommissionTien())
                .isEqualByComparingTo("10000");

        JsonNode history = json(request("GET", url + "/history", manager.token(), null)).get("data");
        assertThat(history).hasSize(2);
        assertThat(history.get(0).get("tyLe").decimalValue()).isEqualByComparingTo("20");
        assertThat(history.get(0).get("ngayKetThuc").isNull()).isTrue();
        assertThat(history.get(1).get("tyLe").decimalValue()).isEqualByComparingTo("10");
        assertThat(history.get(1).get("ngayKetThuc").isNull()).isFalse();
        assertThat(history.get(1).get("ghiChu").asText()).isEqualTo("Khuyến khích bán");

        // Đổi sang đúng tỷ lệ đang áp dụng, hoặc ngoài khoảng 0-100 thì bị từ chối.
        assertThat(request("PUT", url, manager.token(), Map.of("tyLe", 20)).statusCode()).isEqualTo(400);
        assertThat(request("PUT", url, manager.token(), Map.of("tyLe", 101)).statusCode()).isEqualTo(400);
        assertThat(request("PUT", url, manager.token(), Map.of("tyLe", -1)).statusCode()).isEqualTo(400);

        JsonNode list = json(request("GET", "/api/management/commissions", manager.token(), null)).get("data");
        for (JsonNode row : list) {
            if (row.get("maDM").asInt() == category.getMaDM()) {
                assertThat(row.get("tyLe").decimalValue()).isEqualByComparingTo("20");
                assertThat(row.get("macDinh").asBoolean()).isFalse();
            }
        }
    }

    @Test
    void overviewAndManagementApisAreForManagersOnly() throws Exception {
        DonHangCuaHang child = deliveredPaidOrder(book);
        assertThat(child).isNotNull();

        JsonNode overview = json(request("GET", "/api/management/overview", manager.token(), null)).get("data");
        assertThat(overview.get("cuaHangChoChi").asInt()).isGreaterThanOrEqualTo(1);
        assertThat(overview.get("tongTienChoChi").decimalValue()).isGreaterThanOrEqualTo(new BigDecimal("95000"));
        assertThat(overview.get("hoaHongThangNay").decimalValue()).isGreaterThanOrEqualTo(new BigDecimal("5000"));
        assertThat(overview.get("hangDoi").size()).isBetween(1, 5);
        assertThat(request("GET", "/api/management/overview/badges", manager.token(), null).statusCode()).isEqualTo(200);

        Actor admin = actor("QUAN_TRI_VIEN");
        for (String path : List.of("/api/management/overview", "/api/management/overview/badges",
                "/api/management/commissions", "/api/management/payments/store-payouts")) {
            assertThat(request("GET", path, buyer.token(), null).statusCode()).as(path).isEqualTo(403);
            assertThat(request("GET", path, owner.token(), null).statusCode()).as(path).isEqualTo(403);
            assertThat(request("GET", path, admin.token(), null).statusCode()).as(path).isEqualTo(403);
        }
        assertThat(request("PUT", "/api/management/commissions/" + category.getMaDM(), buyer.token(),
                Map.of("tyLe", 3)).statusCode()).isEqualTo(403);
        assertThat(request("POST", "/api/management/payments/store-payouts/done", owner.token(),
                Map.of("maDotChi", "DCXXXXXXXX", "maYeuCau", List.of(1))).statusCode()).isEqualTo(403);

        // Các trang HTML của cổng là công khai; dữ liệu mới cần đăng nhập.
        for (String page : List.of("/management", "/management/commissions", "/management/payments")) {
            assertThat(request("GET", page, null, null).statusCode()).as(page).isEqualTo(200);
        }
    }

    // ------------------------------------------------------------------
    // Ảnh mã QR thay cho số tài khoản; tài khoản hoàn ưu tiên khi hủy đơn; thống kê vận hành
    // ------------------------------------------------------------------

    private static final String ANH_QR = "https://res.cloudinary.com/oldbook/image/upload/oldbook/bank-qr/mau.png";

    @Test
    void storeCanReceivePayoutsWithOnlyAQrImage() throws Exception {
        String url = "/api/store/me/bank-account";

        // Thiếu cả tài khoản lẫn ảnh, thiếu một phần tài khoản, hoặc ảnh không phải https đều bị từ chối.
        assertThat(request("PUT", url, owner.token(), Map.of("tenChuTaiKhoan", "")).statusCode()).isEqualTo(400);
        assertThat(request("PUT", url, owner.token(), Map.of("maNganHang", "970436")).statusCode()).isEqualTo(400);
        assertThat(request("PUT", url, owner.token(), Map.of("qrNganHangUrl", "http://example.com/qr.png")).statusCode())
                .isEqualTo(400);

        var saved = request("PUT", url, owner.token(), Map.of("qrNganHangUrl", ANH_QR));
        assertThat(saved.statusCode()).as(saved.body()).isEqualTo(200);
        assertThat(json(saved).get("data").get("qrNganHangUrl").asText()).isEqualTo(ANH_QR);

        deliveredPaidOrder(book);
        // Có ảnh QR là Quản lý chi được: không còn bị chặn vì "chưa khai tài khoản".
        List<Integer> ids = payoutIds(owner);
        JsonNode preview = json(request("POST", "/api/management/payments/store-payouts/preview", manager.token(),
                Map.of("maYeuCau", ids))).get("data");
        assertThat(preview.get("qrUrl").isNull()).isTrue();
        assertThat(preview.get("anhQrNganHang").asText()).isEqualTo(ANH_QR);

        JsonNode groups = json(request("GET", "/api/management/payments/store-payouts", manager.token(), null)).get("data");
        for (JsonNode g : groups) {
            if (g.get("maCH").asInt() == shop.getMaCH()) {
                assertThat(g.get("coTaiKhoan").asBoolean()).isTrue();
                assertThat(g.get("soTaiKhoan").isNull()).isTrue();
                assertThat(g.get("qrNganHangUrl").asText()).isEqualTo(ANH_QR);
            }
        }
        assertThat(request("POST", "/api/management/payments/store-payouts/done", manager.token(),
                Map.of("maDotChi", preview.get("maDotChi").asText(), "maYeuCau", ids)).statusCode()).isEqualTo(200);

        // Đổi lại sang đủ tài khoản thì chuyển khoản dựng được VietQR.
        assertThat(request("PUT", url, owner.token(), Map.of("maNganHang", "970436", "soTaiKhoan", "0071000123456",
                "tenChuTaiKhoan", "TRAN THI CHU SHOP")).statusCode()).isEqualTo(200);
    }

    @Test
    void customerCanReceiveARefundWithOnlyAQrImage() throws Exception {
        JsonNode session = createSession(buyer, addToCart(buyer, book, 1));
        webhook(cassoBody(MA_GIAO_DICH.incrementAndGet(), session.get("maThanhToan").asText(), "100000"));
        YeuCauChuyenTien refund = transactions.execute(s -> refunds(buyer).get(0));
        String url = "/api/payment/refunds/" + refund.getMaYeuCau() + "/account";

        assertThat(request("PUT", url, buyer.token(), Map.of("soTaiKhoan", "123456789")).statusCode()).isEqualTo(400);
        var saved = request("PUT", url, buyer.token(), Map.of("qrNganHangUrl", ANH_QR));
        assertThat(saved.statusCode()).as(saved.body()).isEqualTo(200);
        assertThat(json(saved).get("data").get("trangThai").asText()).isEqualTo("CHO_XU_LY");

        JsonNode row = find(json(request("GET", "/api/management/payments/transfers?loai=HOAN_TIEN_KHACH&trangThai=CHO_XU_LY",
                manager.token(), null)).get("data").get("content"), refund.getMaYeuCau());
        assertThat(row.get("qrNganHangUrl").asText()).isEqualTo(ANH_QR);
        assertThat(row.get("soTaiKhoan").isNull()).isTrue();
        assertThat(request("POST", "/api/management/payments/transfers/" + refund.getMaYeuCau() + "/done",
                manager.token(), null).statusCode()).isEqualTo(200);
    }

    @Test
    void cancellingUsesThePreferredRefundAccountTheCustomerChose() throws Exception {
        DonHangCuaHang child = paidOrder(buyer, book);
        Integer maDH = child.getDonHang().getMaDH();
        String url = "/api/payment/orders/" + maDH + "/refund-account";

        // Hộp thoại hủy: hệ thống đã biết tài khoản người chuyển (che bớt số).
        JsonNode info = json(request("GET", url, buyer.token(), null)).get("data");
        assertThat(info.get("chuyenKhoan").asBoolean()).isTrue();
        assertThat(info.get("soTaiKhoanDaChuyenRutGon").asText()).isEqualTo("***8888");
        assertThat(info.get("soTaiKhoanUuTien").isNull()).isTrue();

        // Chỉ chủ đơn mới xem hoặc đặt được; thiếu thông tin thì bị từ chối.
        Actor stranger = actor("KHACH_HANG");
        assertThat(request("GET", url, stranger.token(), null).statusCode()).isEqualTo(400);
        assertThat(request("PUT", url, stranger.token(), Map.of("qrNganHangUrl", ANH_QR)).statusCode()).isEqualTo(400);
        assertThat(request("PUT", url, buyer.token(), Map.of("maNganHang", "970436")).statusCode()).isEqualTo(400);
        assertThat(request("PUT", url, owner.token(), Map.of("qrNganHangUrl", ANH_QR)).statusCode()).isEqualTo(403);

        var saved = request("PUT", url, buyer.token(), Map.of("maNganHang", "970436", "soTaiKhoan", "0071000123456",
                "tenChuTaiKhoan", "NGUYEN VAN KHACH", "qrNganHangUrl", ANH_QR));
        assertThat(saved.statusCode()).as(saved.body()).isEqualTo(200);
        assertThat(json(saved).get("data").get("soTaiKhoanUuTien").asText()).isEqualTo("0071000123456");

        // API hủy giữ nguyên; khoản hoàn được điền sẵn theo tài khoản khách chọn, không theo người chuyển.
        donHangService.cancelStoreOrder(buyer.maND(), child.getMaDHCH(), LY_DO_HUY);
        YeuCauChuyenTien refund = transactions.execute(s -> refunds(buyer).get(0));
        assertThat(refund.getTrangThai()).isEqualTo("CHO_XU_LY");
        assertThat(refund.getMaNganHang()).isEqualTo("970436");
        assertThat(refund.getSoTaiKhoan()).isEqualTo("0071000123456");
        assertThat(refund.getTenChuTaiKhoan()).isEqualTo("NGUYEN VAN KHACH");
        assertThat(refund.getQrNganHangUrl()).isEqualTo(ANH_QR);
    }

    @Test
    void cancellingWithoutAnyKnownAccountStillWaitsForTheCustomer() throws Exception {
        // Đơn hủy khi chưa có giao dịch nào kèm tài khoản và khách chưa chọn: khoản hoàn chờ khách nhập, khách được nhắc.
        DonHangCuaHang child = paidOrder(buyer, book);
        transactions.executeWithoutResult(s -> {
            for (GiaoDichNganHang g : giaoDichRepository.findAll()) {
                if (g.getPhien() != null && g.getPhien().getDonHang() != null
                        && g.getPhien().getDonHang().getMaDH().equals(child.getDonHang().getMaDH())) {
                    g.setSoTaiKhoanChuyen(null);
                    g.setMaNganHangChuyen(null);
                    g.setTenNguoiChuyen(null);
                    giaoDichRepository.save(g);
                }
            }
        });
        JsonNode info = json(request("GET", "/api/payment/orders/" + child.getDonHang().getMaDH() + "/refund-account",
                buyer.token(), null)).get("data");
        assertThat(info.get("soTaiKhoanDaChuyenRutGon").isNull()).isTrue();

        donHangService.cancelStoreOrder(buyer.maND(), child.getMaDHCH(), LY_DO_HUY);
        YeuCauChuyenTien refund = transactions.execute(s -> refunds(buyer).get(0));
        assertThat(refund.getTrangThai()).isEqualTo("CHO_THONG_TIN");
        assertThat(notifications(buyer)).contains("CAN_TAI_KHOAN_HOAN_TIEN");
    }

    @Test
    void codOrdersHaveNoRefundAccount() throws Exception {
        Integer line = addToCart(buyer, book, 1);
        Map<String, Object> cod = new java.util.HashMap<>(orderBody(buyer, line));
        cod.put("phuongThucTT", "COD");
        var created = request("POST", "/api/order/orders", buyer.token(), cod);
        assertThat(created.statusCode()).as(created.body()).isEqualTo(200);
        Integer maDH = json(created).get("data").get("maDH").asInt();

        JsonNode info = json(request("GET", "/api/payment/orders/" + maDH + "/refund-account", buyer.token(), null)).get("data");
        assertThat(info.get("chuyenKhoan").asBoolean()).isFalse();
        assertThat(request("PUT", "/api/payment/orders/" + maDH + "/refund-account", buyer.token(),
                Map.of("qrNganHangUrl", ANH_QR)).statusCode()).isEqualTo(400);
    }

    @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Test
    void statsShareNumbersWithTheAdminReportAndManagersCanNudgeOverdueShops() throws Exception {
        deliveredPaidOrder(book);
        DonHangCuaHang waiting = paidOrder(buyer, book(shop, 1));
        jdbc.update("update don_hang set ngay_tao = ? where ma_dh = ?",
                java.sql.Timestamp.valueOf(LocalDateTime.now().minusHours(60)), waiting.getDonHang().getMaDH());

        Actor admin = actor("QUAN_TRI_VIEN");
        String today = java.time.LocalDate.now().toString();
        String range = "?tuNgay=" + today + "&denNgay=" + today;

        JsonNode stats = json(request("GET", "/api/management/stats" + range, manager.token(), null)).get("data");
        JsonNode revenue = json(request("GET", "/api/admin/bao-cao/doanh-thu" + range, admin.token(), null)).get("data");
        JsonNode cashflow = json(request("GET", "/api/admin/bao-cao/dong-tien" + range, admin.token(), null)).get("data");
        JsonNode byCategory = json(request("GET", "/api/admin/bao-cao/theo-danh-muc" + range, admin.token(), null)).get("data");

        // Hoa hồng ở Thống kê của Quản lý khớp báo cáo doanh thu và báo cáo dòng tiền của Admin.
        assertThat(stats.get("dongTien").get("hoaHong").decimalValue()).isEqualByComparingTo(revenue.get("tongHoaHong").decimalValue());
        assertThat(cashflow.get("hoaHong").decimalValue()).isEqualByComparingTo(revenue.get("tongHoaHong").decimalValue());
        assertThat(cashflow.get("sanDangGiu").decimalValue()).isEqualByComparingTo(stats.get("dongTien").get("sanDangGiu").decimalValue());
        assertThat(stats.get("dongTien").get("hoaHong").decimalValue()).isGreaterThanOrEqualTo(new BigDecimal("5000"));
        BigDecimal categoryCommission = BigDecimal.ZERO;
        for (JsonNode row : byCategory) {
            categoryCommission = categoryCommission.add(row.get("hoaHong").decimalValue());
        }
        assertThat(categoryCommission).isEqualByComparingTo(revenue.get("tongHoaHong").decimalValue());

        JsonNode mine = null;
        for (JsonNode row : stats.get("cuaHang")) {
            if (row.get("maCH").asInt() == shop.getMaCH()) { mine = row; }
        }
        assertThat(mine).isNotNull();
        assertThat(mine.get("soDonHoanTat").asInt()).isEqualTo(1);
        // Đơn chờ xác nhận được lùi ngày đặt ra ngoài kỳ "hôm nay", nên chỉ đơn đã giao nằm trong tỉ lệ của kỳ.
        assertThat(mine.get("tongDon").asInt()).isEqualTo(1);

        // Đơn đặt quá 48 giờ mà chưa xác nhận: hiện trong danh sách quá hạn, có thể nhắc, mỗi giờ một lần.
        boolean overdue = false;
        for (JsonNode row : stats.get("donQuaHan")) {
            if (row.get("maDHCH").asInt() == waiting.getMaDHCH()) { overdue = true; }
        }
        assertThat(overdue).isTrue();
        String remind = "/api/management/stats/stores/" + shop.getMaCH() + "/remind-confirm";
        assertThat(request("POST", remind, manager.token(), null).statusCode()).isEqualTo(200);
        assertThat(notifications(owner)).contains("NHAC_XAC_NHAN_DON");
        assertThat(request("POST", remind, manager.token(), null).statusCode()).isEqualTo(400);

        // Kỳ sai bị từ chối; Admin xem được báo cáo nhưng không gọi được API của Quản lý; khách không xem được gì.
        assertThat(request("GET", "/api/management/stats?tuNgay=2026-02-01&denNgay=2026-01-01", manager.token(), null)
                .statusCode()).isEqualTo(400);
        assertThat(request("GET", "/api/management/stats", admin.token(), null).statusCode()).isEqualTo(403);
        assertThat(request("GET", "/api/admin/bao-cao/dong-tien", manager.token(), null).statusCode()).isEqualTo(403);
        assertThat(request("GET", "/api/management/stats", buyer.token(), null).statusCode()).isEqualTo(403);
        assertThat(request("POST", remind, admin.token(), null).statusCode()).isEqualTo(403);
    }

    @Test
    void uploadingABankQrIsLimitedByRole() throws Exception {
        // Tải ảnh: khách chỉ qua /api/payment/bank-qr, chủ cửa hàng chỉ qua /api/store/me/bank-qr.
        assertThat(request("POST", "/api/payment/bank-qr", null, null).statusCode()).isEqualTo(401);
        assertThat(request("POST", "/api/payment/bank-qr", owner.token(), null).statusCode()).isEqualTo(403);
        assertThat(request("POST", "/api/store/me/bank-qr", buyer.token(), null).statusCode()).isEqualTo(403);
        assertThat(request("POST", "/api/store/me/bank-qr", manager.token(), null).statusCode()).isEqualTo(403);
    }

    // ------------------------------------------------------------------
    // COD không đổi, phân quyền
    // ------------------------------------------------------------------

    @Test
    void codOrdersBehaveAsBefore() throws Exception {
        Integer line = addToCart(buyer, book, 1);

        var prepaidThroughOrderApi = request("POST", "/api/order/orders", buyer.token(), orderBody(buyer, line));
        assertThat(prepaidThroughOrderApi.statusCode()).isEqualTo(400);
        assertThat(json(prepaidThroughOrderApi).get("message").asText()).contains("COD");
        assertThat(stock(book)).isEqualTo(1);

        Map<String, Object> cod = new java.util.HashMap<>(orderBody(buyer, line));
        cod.put("phuongThucTT", "COD");
        var created = request("POST", "/api/order/orders", buyer.token(), cod);
        assertThat(created.statusCode()).as(created.body()).isEqualTo(200);
        JsonNode order = json(created).get("data");
        assertThat(order.get("phuongThucTT").asText()).isEqualTo("COD");
        assertThat(order.get("trangThaiThanhToan").asText()).isEqualTo("CHO_THANH_TOAN");

        DonHangCuaHang child = children(donHangRepository.findById(order.get("maDH").asInt()).orElseThrow()).get(0);
        String tracking = confirm(child);
        assertThat(carrier.codByOrder.get(child.getMaDHCH())).isEqualByComparingTo("125000");

        deliver(child, tracking, "picked", "delivering", "delivered");

        assertThat(shopOrderLedger(child))
                .containsExactlyInAnyOrder("THU_HO_COD", "HOA_HONG", "DOANH_THU_SHOP");
        assertThat(yeuCauRepository.findAllByLoaiAndNguoiNhan_MaNDOrderByMaYeuCauDesc("CHI_CUA_HANG", owner.maND()))
                .isEmpty();
        assertThat(parentStatus(child)).isEqualTo("DA_THANH_TOAN");
    }

    @Test
    void paymentEndpointsEnforceRoles() throws Exception {
        Integer line = addToCart(buyer, book, 1);

        assertThat(request("POST", "/api/payment/sessions", null, orderBody(buyer, line)).statusCode()).isEqualTo(401);
        assertThat(request("POST", "/api/payment/sessions", owner.token(), orderBody(buyer, line)).statusCode()).isEqualTo(403);
        assertThat(request("GET", "/api/management/payments/transfers?loai=HOAN_TIEN_KHACH", buyer.token(), null)
                .statusCode()).isEqualTo(403);
        assertThat(request("GET", "/api/management/payments/transactions", manager.token(), null).statusCode())
                .isEqualTo(200);

        JsonNode config = json(request("GET", "/api/payment/config", buyer.token(), null)).get("data");
        assertThat(config.get("chuyenKhoan").asBoolean()).isTrue();
        assertThat(config.get("soPhut").asInt()).isEqualTo(5);

        // Các trang là HTML công khai; dữ liệu mới cần đăng nhập.
        assertThat(request("GET", "/payment/pay?maPhien=1", null, null).statusCode()).isEqualTo(200);
        assertThat(request("GET", "/payment/refunds", null, null).statusCode()).isEqualTo(200);
        assertThat(request("GET", "/management/payments", null, null).statusCode()).isEqualTo(200);
    }

    // ------------------------------------------------------------------
    // Trợ giúp
    // ------------------------------------------------------------------

    private Actor actor(String role) {
        int n = SEQ.incrementAndGet();
        NguoiDung user = nguoiDungRepository.save(NguoiDung.builder().email("pay" + n + "@payment.test")
                .matKhau("test-only-hash").hoTen("Người dùng " + n)
                .soDienThoai(String.format("04%08d", n)).build());
        TaiKhoan account = taiKhoanRepository.save(
                TaiKhoan.builder().nguoiDung(user).vaiTro(role).trangThai("HOAT_DONG").build());
        return new Actor(user.getMaND(), account.getMaTK(),
                jwtService.generateToken(user.getMaND(), account.getMaTK(), role));
    }

    private CuaHang shop(Actor shopOwner) {
        return cuaHangRepository.save(CuaHang.builder()
                .chuShop(nguoiDungRepository.getReferenceById(shopOwner.maND()))
                .tenCuaHang("Tiệm sách " + SEQ.incrementAndGet()).trangThaiDuyet("DA_DUYET").build());
    }

    private Sach book(CuaHang ofShop, int stock) {
        return sachRepository.save(Sach.builder().cuaHang(ofShop).danhMuc(category)
                .tenSach("Truyện Kiều bản " + SEQ.incrementAndGet()).giaBanCu(GIA_SACH)
                .trangThaiDuyet("DA_DUYET").trangThaiBan("DANG_BAN").soLuongTon(stock).build());
    }

    private Integer addToCart(Actor who, Sach what, int quantity) {
        GioHang cart = gioHangRepository.findByMaND(who.maND()).orElseGet(() -> gioHangRepository.save(
                GioHang.builder().nguoiDung(nguoiDungRepository.getReferenceById(who.maND())).build()));
        return chiTietGioHangRepository.save(
                ChiTietGioHang.builder().gioHang(cart).sach(what).soLuong(quantity).build()).getMaCTGioHang();
    }

    private Map<String, Object> orderBody(Actor who, Integer line) {
        return orderBody(address.getMaDiaChi(), List.of(line), List.of(shop));
    }

    private Map<String, Object> orderBody(Integer maDiaChi, List<Integer> lines, List<CuaHang> shops) {
        List<Map<String, Object>> storeOrders = new ArrayList<>();
        for (CuaHang s : shops) {
            storeOrders.add(Map.of("maCH", s.getMaCH(), "maDVVC", ghn.getMaDVVC(), "ghiChu", "Giao giờ hành chính"));
        }
        return Map.of("maDiaChi", maDiaChi, "phuongThucTT", "CHUYEN_KHOAN",
                "maCTGioHangs", lines, "storeOrders", storeOrders);
    }

    private JsonNode createSession(Actor who, Integer line) throws Exception {
        var response = request("POST", "/api/payment/sessions", who.token(), orderBody(who, line));
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        return json(response).get("data");
    }

    /** Một đơn hàng con đã thanh toán trước, đi đúng đường thật: phiên rồi webhook. */
    private DonHangCuaHang paidOrder(Actor who, Sach what) throws Exception {
        JsonNode session = createSession(who, addToCart(who, what, 1));
        webhook(cassoBody(MA_GIAO_DICH.incrementAndGet(), session.get("maThanhToan").asText(),
                session.get("soTien").decimalValue().toPlainString()));
        return children(orderOf(session)).get(0);
    }

    private DonHang orderOf(JsonNode session) {
        return transactions.execute(s -> donHangRepository.findById(
                phienRepository.findById(session.get("maPhien").asInt()).orElseThrow().getDonHang().getMaDH())
                .orElseThrow());
    }

    private List<DonHangCuaHang> children(DonHang order) {
        return donHangCuaHangRepository.findAllByDonHang_MaDHOrderByMaDHCHAsc(order.getMaDH());
    }

    private String orderStatus(DonHangCuaHang child) {
        return donHangCuaHangRepository.findById(child.getMaDHCH()).orElseThrow().getTrangThaiDonHang();
    }

    private String parentStatus(DonHangCuaHang child) {
        return transactions.execute(s -> donHangCuaHangRepository.findById(child.getMaDHCH()).orElseThrow()
                .getDonHang().getTrangThaiThanhToan());
    }

    private String confirm(DonHangCuaHang child) {
        Integer ownerId = transactions.execute(s -> donHangCuaHangRepository.findById(child.getMaDHCH()).orElseThrow()
                .getCuaHang().getChuShop().getMaND());
        storeOrderService.confirmOrder(ownerId, child.getMaDHCH());
        return vanChuyenRepository.findByDonHangCuaHang_MaDHCH(child.getMaDHCH()).orElseThrow().getMaVanDon();
    }

    private void deliver(DonHangCuaHang child, String tracking, String... rawStatuses) {
        for (String raw : rawStatuses) {
            carrier.statusByCode.put(tracking, raw);
            trackingService.pullLatest(child.getMaDHCH());
        }
    }

    private void expire(JsonNode session) {
        transactions.executeWithoutResult(s -> {
            PhienThanhToan p = phienRepository.findById(session.get("maPhien").asInt()).orElseThrow();
            p.setHetHanLuc(LocalDateTime.now().minusMinutes(1));
            phienRepository.save(p);
        });
    }

    private PhienThanhToan phien(JsonNode session) {
        return phienRepository.findById(session.get("maPhien").asInt()).orElseThrow();
    }

    private int stock(Sach what) {
        return sachRepository.findById(what.getMaSach()).orElseThrow().getSoLuongTon();
    }

    private List<ThanhToan> payments(DonHang order) {
        return transactions.execute(s -> thanhToanRepository.findAll().stream()
                .filter(t -> t.getDonHang().getMaDH().equals(order.getMaDH())).toList());
    }

    /** Các loại dòng trong sổ thanh_toan gắn với một đơn hàng con. */
    private List<String> shopOrderLedger(DonHangCuaHang child) {
        return transactions.execute(s -> thanhToanRepository.findAll().stream()
                .filter(t -> t.getDonHangCuaHang() != null
                        && t.getDonHangCuaHang().getMaDHCH().equals(child.getMaDHCH()))
                .map(ThanhToan::getLoaiGiaoDich).toList());
    }

    private GiaoDichNganHang ledger(long cassoId) {
        return giaoDichRepository.findByMaGiaoDichCong(String.valueOf(cassoId)).orElseThrow();
    }

    private List<YeuCauChuyenTien> refunds(Actor who) {
        return yeuCauRepository.findAllByLoaiAndNguoiNhan_MaNDOrderByMaYeuCauDesc("HOAN_TIEN_KHACH", who.maND());
    }

    private List<String> notifications(Actor who) {
        return thongBaoRepository
                .findByTaiKhoanNhan_MaTKOrderByNgayTaoDescMaThongBaoDesc(who.maTK(), PageRequest.of(0, 50))
                .map(ThongBao::getLoai).getContent();
    }

    private static JsonNode find(JsonNode rows, Integer maYeuCau) {
        for (JsonNode row : rows) {
            if (row.get("maYeuCau").asInt() == maYeuCau) {
                return row;
            }
        }
        throw new AssertionError("Không thấy khoản " + maYeuCau + " trong " + rows);
    }

    /** Nội dung theo đúng mẫu Webhook V2 của Casso: data là một đối tượng, có chữ tiếng Việt. */
    private static String cassoBody(long id, String description, String amount) {
        return "{\"error\":0,\"data\":{\"id\":" + id
                + ",\"reference\":\"FT" + id + "\""
                + ",\"description\":\"" + description + "\""
                + ",\"amount\":" + amount
                + ",\"runningBalance\":25000000"
                + ",\"transactionDateTime\":\"2026-10-12 15:36:21\""
                + ",\"accountNumber\":\"0123456789\""
                + ",\"bankName\":\"MBBank\",\"bankAbbreviation\":\"MB\""
                + ",\"virtualAccountNumber\":\"\",\"virtualAccountName\":\"\""
                + ",\"counterAccountName\":\"NGUYEN VAN KHACH\""
                + ",\"counterAccountNumber\":\"8888888888\""
                + ",\"counterAccountBankId\":\"970415\""
                + ",\"counterAccountBankName\":\"VietinBank\"}}";
    }

    private static String signature(String body) throws Exception {
        String t = String.valueOf(System.currentTimeMillis());
        return "t=" + t + ",v1=" + CassoSignatureVerifier.tinhChuKy(KHOA, t, body);
    }

    private HttpResponse<String> webhook(String body) throws Exception {
        return send(body, signature(body));
    }

    private HttpResponse<String> send(String body, String signatureHeader) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/payment/casso/webhook"))
                .timeout(Duration.ofSeconds(15)).header("Content-Type", "application/json");
        if (signatureHeader != null) {
            builder.header("X-Casso-Signature", signatureHeader);
        }
        return client.send(builder.POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> request(String method, String path, String token, Object body) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).timeout(Duration.ofSeconds(15));
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        if (body != null) {
            builder.header("Content-Type", "application/json");
        }
        return client.send(builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode json(HttpResponse<String> response) {
        return mapper.readTree(response.body());
    }
}
