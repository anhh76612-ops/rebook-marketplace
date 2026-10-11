package com.oldbook.admin;

import com.oldbook.entity.catalog.CuaHang;
import com.oldbook.entity.identity.NguoiDung;
import com.oldbook.entity.identity.TaiKhoan;
import com.oldbook.entity.order.DonHang;
import com.oldbook.entity.order.DonHangCuaHang;
import com.oldbook.repository.identity.NguoiDungRepository;
import com.oldbook.repository.identity.TaiKhoanRepository;
import com.oldbook.service.auth.EmailService;
import com.oldbook.service.auth.JwtService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.config.import=")
@ActiveProfiles({"test", "catalog-test"})
class AdminReportApiIntegrationTest {

    private static final String REVENUE = "/api/admin/bao-cao/doanh-thu";

    // Các lớp test dùng chung một H2 và số điện thoại là duy nhất: mỗi lớp giữ một đầu số riêng, lớp này dùng 05.
    private static final AtomicInteger PHONE_SEQUENCE = new AtomicInteger();

    @Value("${local.server.port}") private int port;
    @Autowired private ObjectMapper mapper;
    @Autowired private NguoiDungRepository users;
    @Autowired private TaiKhoanRepository accounts;
    @Autowired private JwtService jwtService;
    @Autowired private EntityManager entityManager;
    @Autowired private PlatformTransactionManager transactionManager;
    @MockitoBean private EmailService emailService;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @Test
    void reportShellRendersWithTheSharedAdminLayout() throws Exception {
        var response = request("/admin/reports", null);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        assertThat(response.body())
                .contains("Báo cáo doanh thu sàn - OldBook", "id=\"reportExport\"", "id=\"adminNotifyPanel\"",
                        "/js/auth.js", "/js/admin/layout.js", "/js/admin/reports.js")
                .doesNotContain("th:replace", "th:fragment");
        assertThat(request("/js/admin/reports.js", null).statusCode()).isEqualTo(200);
    }

    @Test
    void revenueReportIsOnlyAvailableToAdministratorsAndValidatesTheRange() throws Exception {
        assertThat(request(REVENUE, null).statusCode()).isEqualTo(401);
        assertThat(request(REVENUE, token("QUAN_LY")).statusCode()).isEqualTo(403);

        String adminToken = token("QUAN_TRI_VIEN");
        LocalDate today = LocalDate.now();

        // Không truyền ngày: 30 ngày gần nhất, tính cả hôm nay.
        JsonNode byDefault = report(adminToken, "");
        assertThat(byDefault.get("denNgay").asText()).isEqualTo(today.toString());
        assertThat(byDefault.get("tuNgay").asText()).isEqualTo(today.minusDays(29).toString());
        assertThat(byDefault.get("points").size()).isEqualTo(30);

        // Một ngày duy nhất và đủ 366 ngày đều hợp lệ; ngược ngày hoặc dài hơn thì bị từ chối.
        assertThat(report(adminToken, "?tuNgay=" + today + "&denNgay=" + today).get("points").size()).isEqualTo(1);
        assertThat(report(adminToken, "?tuNgay=" + today.minusDays(365) + "&denNgay=" + today).get("points").size()).isEqualTo(366);
        assertThat(request(REVENUE + "?tuNgay=" + today + "&denNgay=" + today.minusDays(1), adminToken).statusCode()).isEqualTo(400);
        assertThat(request(REVENUE + "?tuNgay=" + today.minusDays(366) + "&denNgay=" + today, adminToken).statusCode()).isEqualTo(400);
    }

    @Test
    void revenueCountsOnlyDeliveredShopOrdersByDeliveryDay() throws Exception {
        String adminToken = token("QUAN_TRI_VIEN");
        LocalDate today = LocalDate.now();
        LocalDate firstDay = today.minusDays(6);
        String week = "?tuNgay=" + firstDay + "&denNgay=" + today;
        String month = "?tuNgay=" + today.minusDays(29) + "&denNgay=" + today;

        JsonNode weekBefore = report(adminToken, week);
        JsonNode monthBefore = report(adminToken, month);

        // Hai đơn nằm đúng hai mép của kỳ 7 ngày, một đơn ngay trước kỳ đó.
        shopOrder("DA_GIAO", "200000", "10000", today.atStartOfDay());
        shopOrder("DA_GIAO", "100000", "5000", firstDay.atStartOfDay());
        shopOrder("DA_GIAO", "50000", "2500", firstDay.atStartOfDay().minusSeconds(1));
        // Các trạng thái khác không bao giờ được tính.
        shopOrder("DA_HUY", "999000", "0", null);
        shopOrder("DA_HOAN_TRA", "888000", "0", null);
        shopOrder("DANG_GIAO", "777000", "0", null);

        JsonNode weekAfter = report(adminToken, week);
        assertThat(weekAfter.get("points").size()).isEqualTo(7);
        for (int i = 0; i < 7; i++) {
            assertThat(weekAfter.get("points").get(i).get("ngay").asText()).isEqualTo(firstDay.plusDays(i).toString());
        }
        assertPointDelta(weekBefore, weekAfter, today, 1, "200000", "10000");
        assertPointDelta(weekBefore, weekAfter, firstDay, 1, "100000", "5000");
        assertPointDelta(weekBefore, weekAfter, today.minusDays(3), 0, "0", "0");
        assertThat(weekAfter.get("tongSoDon").asLong() - weekBefore.get("tongSoDon").asLong()).isEqualTo(2);
        assertThat(decimalDelta(weekBefore, weekAfter, "tongGmv")).isEqualByComparingTo("300000");
        assertThat(decimalDelta(weekBefore, weekAfter, "tongHoaHong")).isEqualByComparingTo("15000");

        JsonNode monthAfter = report(adminToken, month);
        assertThat(monthAfter.get("tongSoDon").asLong() - monthBefore.get("tongSoDon").asLong()).isEqualTo(3);
        assertThat(decimalDelta(monthBefore, monthAfter, "tongGmv")).isEqualByComparingTo("350000");
        assertThat(decimalDelta(monthBefore, monthAfter, "tongHoaHong")).isEqualByComparingTo("17500");

        // Tổng của kỳ luôn bằng tổng các ngày trong kỳ.
        BigDecimal sum = BigDecimal.ZERO;
        for (JsonNode point : monthAfter.get("points")) {
            sum = sum.add(point.get("hoaHong").decimalValue());
        }
        assertThat(sum).isEqualByComparingTo(monthAfter.get("tongHoaHong").decimalValue());
    }

    private void shopOrder(String status, String goods, String commission, LocalDateTime deliveredAt) {
        new TransactionTemplate(transactionManager).executeWithoutResult(transaction -> {
            CuaHang store = CuaHang.builder().chuShop(user())
                    .tenCuaHang("Cửa hàng báo cáo " + UUID.randomUUID()).trangThaiDuyet("DA_DUYET").build();
            entityManager.persist(store);
            DonHang order = DonHang.builder().nguoiMua(user()).build();
            entityManager.persist(order);
            // Phí vận chuyển khác 0 để chắc GMV không cộng nhầm phí này.
            entityManager.persist(DonHangCuaHang.builder().donHang(order).cuaHang(store)
                    .tienHangCH(new BigDecimal(goods)).phiShipCH(new BigDecimal("30000"))
                    .tongTienShopOrder(new BigDecimal(goods).add(new BigDecimal("30000")))
                    .commissionTien(new BigDecimal(commission))
                    .trangThaiDonHang(status).ngayGiao(deliveredAt).build());
        });
    }

    private JsonNode report(String token, String query) throws Exception {
        var response = request(REVENUE + query, token);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        return mapper.readTree(response.body()).get("data");
    }

    private void assertPointDelta(JsonNode before, JsonNode after, LocalDate day, long orders, String gmv, String commission) {
        JsonNode from = point(before, day);
        JsonNode to = point(after, day);
        assertThat(to.get("soDon").asLong() - from.get("soDon").asLong()).as(day + " soDon").isEqualTo(orders);
        assertThat(to.get("gmv").decimalValue().subtract(from.get("gmv").decimalValue())).as(day + " gmv").isEqualByComparingTo(gmv);
        assertThat(to.get("hoaHong").decimalValue().subtract(from.get("hoaHong").decimalValue())).as(day + " hoaHong").isEqualByComparingTo(commission);
    }

    private JsonNode point(JsonNode report, LocalDate day) {
        for (JsonNode point : report.get("points")) {
            if (day.toString().equals(point.get("ngay").asText())) {
                return point;
            }
        }
        throw new AssertionError("Thiếu ngày " + day + " trong báo cáo");
    }

    private BigDecimal decimalDelta(JsonNode before, JsonNode after, String field) {
        return after.get(field).decimalValue().subtract(before.get(field).decimalValue());
    }

    private NguoiDung user() {
        return users.save(NguoiDung.builder().email(UUID.randomUUID() + "@test.local")
                .matKhau("test-only-hash").soDienThoai(String.format("05%08d", PHONE_SEQUENCE.incrementAndGet())).build());
    }

    private String token(String role) {
        NguoiDung user = user();
        TaiKhoan account = accounts.save(TaiKhoan.builder().nguoiDung(user).vaiTro(role).trangThai("HOAT_DONG").build());
        return jwtService.generateToken(user.getMaND(), account.getMaTK(), role);
    }

    private HttpResponse<String> request(String path, String token) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).timeout(Duration.ofSeconds(15));
        if (token != null) builder.header("Authorization", "Bearer " + token);
        return client.send(builder.GET().build(), HttpResponse.BodyHandlers.ofString());
    }
}
