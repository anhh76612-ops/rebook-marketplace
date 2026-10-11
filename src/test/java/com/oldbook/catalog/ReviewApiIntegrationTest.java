package com.oldbook.catalog;

import com.oldbook.entity.catalog.CuaHang;
import com.oldbook.entity.catalog.DanhMuc;
import com.oldbook.entity.catalog.Sach;
import com.oldbook.entity.identity.NguoiDung;
import com.oldbook.entity.identity.TaiKhoan;
import com.oldbook.entity.order.ChiTietDonHang;
import com.oldbook.entity.order.DonHang;
import com.oldbook.entity.order.DonHangCuaHang;
import com.oldbook.entity.system.DanhGia;
import com.oldbook.service.auth.EmailService;
import com.oldbook.service.auth.JwtService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.config.import=",
                "spring.datasource.url=jdbc:h2:mem:reviews_${random.uuid};MODE=MSSQLServer;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000"
        })
@ActiveProfiles({"test", "catalog-test"})
class ReviewApiIntegrationTest {

    private static final AtomicInteger PHONE_SEQUENCE = new AtomicInteger();

    @Value("${local.server.port}") private int port;
    @Autowired private ObjectMapper mapper;
    @Autowired private JwtService jwtService;
    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TransactionTemplate transactions;
    @MockitoBean private EmailService emailService;

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private Actor customer;
    private Actor otherCustomer;
    private Actor owner;
    private Integer storeId;
    private Integer categoryId;

    @BeforeEach
    void seed() {
        transactions.executeWithoutResult(status -> {
            customer = actor("KHACH_HANG", "Buyer <script>alert(3)</script>");
            otherCustomer = actor("KHACH_HANG", "Another buyer");
            owner = actor("CHU_CUA_HANG", "Shop owner");
            storeId = persist(CuaHang.builder()
                    .chuShop(entityManager.getReference(NguoiDung.class, owner.maND()))
                    .tenCuaHang("Review shop " + UUID.randomUUID()).trangThaiDuyet("DA_DUYET").build()).getMaCH();
            categoryId = persist(DanhMuc.builder().tenDanhMuc("Review category " + UUID.randomUUID()).build())
                    .getMaDM();
        });
    }

    @Test
    void deliveredPurchasePersistsBuyerAndServerDateAndDisplaysEscapedComment() throws Exception {
        Line line = line(customer, "DA_GIAO", "CHO_THANH_TOAN", null);
        String comment = "Great book <script>alert(2)</script> & careful packaging";
        LocalDateTime before = LocalDateTime.now().minusSeconds(1);
        String body = mapper.writeValueAsString(Map.of(
                "soSao", 5, "binhLuan", comment,
                "maND", otherCustomer.maND(), "maCTDH", Integer.MAX_VALUE,
                "ngayDanhGia", "2000-01-01T00:00:00"));

        JsonNode review = data(post(line.maCTDH(), customer.token(), body));

        assertThat(review.get("maDanhGia").asInt()).isPositive();
        assertThat(review.get("hoTen").asString()).isEqualTo(customer.name());
        assertThat(review.get("soSao").asInt()).isEqualTo(5);
        assertThat(review.get("binhLuan").asString()).isEqualTo(comment);
        assertThat(LocalDateTime.parse(review.get("ngayDanhGia").asString()))
                .isBetween(before, LocalDateTime.now().plusSeconds(1));
        transactions.executeWithoutResult(status -> {
            DanhGia saved = entityManager.find(DanhGia.class, review.get("maDanhGia").asInt());
            assertThat(saved.getNguoiDung().getMaND()).isEqualTo(customer.maND());
            assertThat(saved.getChiTietDonHang().getMaCTDH()).isEqualTo(line.maCTDH());
            assertThat(saved.getBinhLuan()).isEqualTo(comment);
            assertThat(saved.getNgayDanhGia()).isBetween(before, LocalDateTime.now().plusSeconds(1));
        });

        var detail = get("/books/" + line.maSach(), null);
        assertCode(detail, 200);
        assertThat(detail.body()).contains(
                        "Great book &lt;script&gt;alert(2)&lt;/script&gt; &amp; careful packaging",
                        "Buyer &lt;script&gt;alert(3)&lt;/script&gt;")
                .doesNotContain("<script>alert(2)</script>", "<script>alert(3)</script>");
    }

    @Test
    void reviewCreationRequiresAuthenticatedCustomerRole() throws Exception {
        Line line = line(customer, "DA_GIAO", "DA_THANH_TOAN", null);
        String body = body(4, "Good book");
        assertCode(post(line.maCTDH(), null, body), 401);
        assertCode(post(line.maCTDH(), "invalid-token", body), 401);
        assertCode(post(line.maCTDH(), owner.token(), body), 403);
        for (String role : List.of("QUAN_LY", "QUAN_TRI_VIEN")) {
            Actor actor = transactions.execute(status -> actor(role, role));
            assertCode(post(line.maCTDH(), actor.token(), body), 403);
        }
        assertThat(reviewCount(line.maCTDH())).isZero();
    }

    @Test
    void missingAndAnotherBuyersOrderItemAreNotExposedOrReviewed() throws Exception {
        Line own = line(customer, "DA_GIAO", "DA_THANH_TOAN", null);
        Line other = line(otherCustomer, "DA_GIAO", "DA_THANH_TOAN", own.maSach());
        assertCode(post(Integer.MAX_VALUE, customer.token(), body(5, "Missing item")), 404);
        assertCode(post(other.maCTDH(), customer.token(), body(5, "Not my item")), 404);
        assertCode(post(own.maCTDH(), otherCustomer.token(), body(5, "Not my item")), 404);
        assertThat(reviewCount(own.maCTDH())).isZero();
        assertThat(reviewCount(other.maCTDH())).isZero();
        assertThat(orderItem(own, customer.token()).get("coTheDanhGia").asBoolean()).isTrue();
    }

    @Test
    void onlyDeliveredStoreOrdersCanBeReviewed() throws Exception {
        for (String orderStatus : List.of("CHO_XU_LY", "DA_XAC_NHAN", "DANG_LAY_HANG", "DA_LAY_HANG",
                "DANG_GIAO", "DA_HUY", "DA_HOAN_TRA")) {
            Line line = line(customer, orderStatus, "DA_THANH_TOAN", null);
            assertThat(orderItem(line, customer.token()).get("coTheDanhGia").asBoolean())
                    .as(orderStatus).isFalse();
            assertCode(post(line.maCTDH(), customer.token(), body(4, "Not delivered")), 409);
            assertThat(reviewCount(line.maCTDH())).as(orderStatus).isZero();
        }
    }

    @Test
    void deliveredChildRemainsEligibleWhenAnotherShopInTheSameOrderIsInTransit() throws Exception {
        Line delivered = line(customer, "DA_GIAO", "CHO_THANH_TOAN", null);
        Line pending = transactions.execute(status -> {
            var secondOwner = actor("CHU_CUA_HANG", "Second shop owner");
            var secondStore = persist(CuaHang.builder()
                    .chuShop(entityManager.getReference(NguoiDung.class, secondOwner.maND()))
                    .tenCuaHang("Second review shop " + UUID.randomUUID()).trangThaiDuyet("DA_DUYET").build());
            var child = persist(DonHangCuaHang.builder()
                    .donHang(entityManager.getReference(DonHang.class, delivered.maDH())).cuaHang(secondStore)
                    .trangThaiDonHang("DANG_GIAO").trangThaiThanhToanShop("CHO_THANH_TOAN").build());
            var book = persist(Sach.builder().cuaHang(secondStore)
                    .danhMuc(entityManager.getReference(DanhMuc.class, categoryId))
                    .tenSach("Other shop's book").trangThaiDuyet("DA_DUYET").trangThaiBan("DANG_BAN").build());
            var item = persist(ChiTietDonHang.builder().donHangCuaHang(child).sach(book).soLuong(1).build());
            return new Line(delivered.maDH(), child.getMaDHCH(), item.getMaCTDH(), book.getMaSach());
        });

        assertThat(orderItem(delivered, customer.token()).get("coTheDanhGia").asBoolean()).isTrue();
        assertThat(orderItem(pending, customer.token()).get("coTheDanhGia").asBoolean()).isFalse();
        assertCode(post(delivered.maCTDH(), customer.token(), body(5, "Delivered book")), 200);
        assertCode(post(pending.maCTDH(), customer.token(), body(5, "Still in transit")), 409);
    }

    @Test
    void orderResponsesExposeEligibilityThenThePersistedReviewOnBothListAndDetail() throws Exception {
        Line line = line(customer, "DA_GIAO", "DA_THANH_TOAN", null);
        JsonNode before = orderItem(line, customer.token());
        assertThat(before.get("coTheDanhGia").asBoolean()).isTrue();
        assertThat(before.get("danhGia")).isNotNull();
        assertThat(before.get("danhGia").isNull()).isTrue();

        JsonNode created = data(post(line.maCTDH(), customer.token(), body(3, "Honest purchase feedback")));

        for (String path : List.of("/api/order/orders", "/api/order/orders/" + line.maDH())) {
            JsonNode payload = data(get(path, customer.token()));
            JsonNode order = payload.isArray() ? findById(payload, "maDH", line.maDH()) : payload;
            JsonNode item = itemInOrder(order, line);
            assertThat(item.get("coTheDanhGia").asBoolean()).isFalse();
            assertThat(item.get("danhGia").get("maDanhGia").asInt())
                    .isEqualTo(created.get("maDanhGia").asInt());
            assertThat(item.get("danhGia").get("soSao").asInt()).isEqualTo(3);
            assertThat(item.get("danhGia").get("binhLuan").asString()).isEqualTo("Honest purchase feedback");
            assertThat(LocalDateTime.parse(item.get("danhGia").get("ngayDanhGia").asString()))
                    .isCloseTo(LocalDateTime.parse(created.get("ngayDanhGia").asString()), within(1, ChronoUnit.MILLIS));
        }
    }

    @Test
    void onePurchaseLineCanBeReviewedOnceAndAnotherPurchaseOfTheSameBookHasItsOwnReview() throws Exception {
        Line first = line(customer, "DA_GIAO", "DA_THANH_TOAN", null);
        Line second = line(customer, "DA_GIAO", "DA_THANH_TOAN", first.maSach());
        JsonNode firstReview = data(post(first.maCTDH(), customer.token(), body(5, "First purchase")));
        assertCode(post(first.maCTDH(), customer.token(), body(1, "Duplicate attempt")), 409);
        JsonNode secondReview = data(post(second.maCTDH(), customer.token(), body(4, "Second purchase")));

        assertThat(secondReview.get("maDanhGia").asInt()).isNotEqualTo(firstReview.get("maDanhGia").asInt());
        assertThat(reviewCount(first.maCTDH())).isEqualTo(1L);
        assertThat(reviewCount(second.maCTDH())).isEqualTo(1L);
        assertThat(orderItem(first, customer.token()).get("danhGia").get("binhLuan").asString())
                .isEqualTo("First purchase");
        var page = get("/books/" + first.maSach(), null);
        assertCode(page, 200);
        assertThat(page.body()).contains("First purchase", "Second purchase").doesNotContain("Duplicate attempt");
    }

    @Test
    void invalidRatingsCommentsAndMalformedBodiesAreRejectedWithoutPersistingReview() throws Exception {
        Line line = line(customer, "DA_GIAO", "DA_THANH_TOAN", null);
        List<String> invalidBodies = List.of(
                "{\"soSao\":0,\"binhLuan\":\"Review\"}",
                "{\"soSao\":6,\"binhLuan\":\"Review\"}",
                "{\"soSao\":null,\"binhLuan\":\"Review\"}",
                "{\"binhLuan\":\"Review\"}",
                "{\"soSao\":4.5,\"binhLuan\":\"Review\"}",
                "{\"soSao\":\"4\",\"binhLuan\":\"Review\"}",
                "{\"soSao\":true,\"binhLuan\":\"Review\"}",
                "{\"soSao\":{},\"binhLuan\":\"Review\"}",
                "{\"soSao\":5,\"binhLuan\":null}",
                "{\"soSao\":5}",
                body(5, ""), body(5, " \t\n "), body(5, "x".repeat(2001)),
                "{", "null", "");

        for (String invalid : invalidBodies) {
            assertCode(post(line.maCTDH(), customer.token(), invalid), 400);
            assertThat(reviewCount(line.maCTDH())).isZero();
        }
        assertThat(orderItem(line, customer.token()).get("coTheDanhGia").asBoolean()).isTrue();
        assertCode(post(line.maCTDH(), customer.token(), body(1, "x".repeat(2000))), 200);
    }

    @Test
    void concurrentSubmissionsCreateExactlyOneReviewAndReturnAConflictForTheOther() throws Exception {
        Line line = line(customer, "DA_GIAO", "DA_THANH_TOAN", null);
        HttpRequest request = httpRequest("POST", reviewPath(line.maCTDH()), customer.token(), body(5, "Concurrent review"));
        var first = client.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        var second = client.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        var firstResponse = first.get(15, TimeUnit.SECONDS);
        var secondResponse = second.get(15, TimeUnit.SECONDS);
        assertThat(List.of(firstResponse.statusCode(), secondResponse.statusCode()))
                .as(firstResponse.body() + " / " + secondResponse.body()).containsExactlyInAnyOrder(200, 409);
        assertThat(reviewCount(line.maCTDH())).isEqualTo(1L);
        assertThat(orderItem(line, customer.token()).get("coTheDanhGia").asBoolean()).isFalse();
    }

    private Actor actor(String role, String name) {
        var user = persist(NguoiDung.builder().email("review-" + UUID.randomUUID() + "@test.local")
                .matKhau("test-only-hash").hoTen(name)
                .soDienThoai(String.format("07%08d", PHONE_SEQUENCE.incrementAndGet())).build());
        var account = persist(TaiKhoan.builder().nguoiDung(user).vaiTro(role).trangThai("HOAT_DONG").build());
        return new Actor(user.getMaND(), name, jwtService.generateToken(user.getMaND(), account.getMaTK(), role));
    }

    private Line line(Actor buyer, String status, String payment, Integer bookId) {
        return transactions.execute(transaction -> {
            var store = entityManager.getReference(CuaHang.class, storeId);
            var book = bookId == null ? persist(Sach.builder().cuaHang(store)
                    .danhMuc(entityManager.getReference(DanhMuc.class, categoryId)).tenSach("Purchased book")
                    .giaBanCu(new BigDecimal("50000")).giaBia(new BigDecimal("75000"))
                    .soLuongTon(3).trangThaiDuyet("DA_DUYET").trangThaiBan("DANG_BAN").build())
                    : entityManager.getReference(Sach.class, bookId);
            var parent = persist(DonHang.builder().nguoiMua(entityManager.getReference(NguoiDung.class, buyer.maND()))
                    .tongTienHang(new BigDecimal("50000")).tongPhiShip(BigDecimal.ZERO).tongGiamGia(BigDecimal.ZERO)
                    .tongThanhToan(new BigDecimal("50000")).phuongThucTT("COD").trangThaiThanhToan(payment)
                    .tenNguoiNhan("Buyer").soDienThoaiNhan("0900000000").diaChiGiaoChiTiet("1 Test Street").build());
            var child = persist(DonHangCuaHang.builder().donHang(parent).cuaHang(store)
                    .tienHangCH(new BigDecimal("50000")).phiShipCH(BigDecimal.ZERO)
                    .tongTienShopOrder(new BigDecimal("50000")).trangThaiDonHang(status)
                    .trangThaiThanhToanShop(payment)
                    .ngayGiao("DA_GIAO".equals(status) ? LocalDateTime.now().minusHours(1) : null).build());
            var item = persist(ChiTietDonHang.builder().donHangCuaHang(child).sach(book).soLuong(1)
                    .donGia(new BigDecimal("50000")).thanhTien(new BigDecimal("50000"))
                    .tyLeChietKhau(BigDecimal.ZERO).tienChietKhau(BigDecimal.ZERO).build());
            return new Line(parent.getMaDH(), child.getMaDHCH(), item.getMaCTDH(), book.getMaSach());
        });
    }

    private <T> T persist(T entity) {
        entityManager.persist(entity);
        return entity;
    }

    private long reviewCount(Integer itemId) {
        return jdbc.queryForObject("select count(*) from danh_gia where ma_ctdh = ?", Long.class, itemId);
    }

    private JsonNode orderItem(Line line, String token) throws Exception {
        return itemInOrder(findById(data(get("/api/order/orders", token)), "maDH", line.maDH()), line);
    }

    private JsonNode itemInOrder(JsonNode order, Line line) {
        JsonNode store = findById(order.get("storeOrders"), "maDHCH", line.maDHCH());
        return findById(store.get("items"), "maCTDH", line.maCTDH());
    }

    private JsonNode findById(JsonNode items, String field, Integer id) {
        for (JsonNode item : items) {
            if (item.get(field).asInt() == id) return item;
        }
        throw new AssertionError("Missing " + field + "=" + id + " in " + items);
    }

    private String body(int stars, String comment) {
        return mapper.writeValueAsString(Map.of("soSao", stars, "binhLuan", comment));
    }

    private String reviewPath(Integer itemId) {
        return "/api/reviews/order-items/" + itemId;
    }

    private HttpRequest httpRequest(String method, String path, String token, String body) {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).timeout(Duration.ofSeconds(15));
        if (token != null) builder.header("Authorization", "Bearer " + token);
        if (body != null) builder.header("Content-Type", "application/json");
        return builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build();
    }

    private HttpResponse<String> post(Integer itemId, String token, String body) throws Exception {
        return client.send(httpRequest("POST", reviewPath(itemId), token, body),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private HttpResponse<String> get(String path, String token) throws Exception {
        return client.send(httpRequest("GET", path, token, null), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private JsonNode data(HttpResponse<String> response) {
        assertCode(response, 200);
        return mapper.readTree(response.body()).get("data");
    }

    private void assertCode(HttpResponse<String> response, int code) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(code);
        if (response.headers().firstValue("Content-Type").orElse("").contains("json")) {
            JsonNode parsed = mapper.readTree(response.body());
            if (parsed.has("code")) assertThat(parsed.get("code").asInt()).isEqualTo(code);
        }
    }

    private record Actor(Integer maND, String name, String token) { }
    private record Line(Integer maDH, Integer maDHCH, Integer maCTDH, Integer maSach) { }
}
