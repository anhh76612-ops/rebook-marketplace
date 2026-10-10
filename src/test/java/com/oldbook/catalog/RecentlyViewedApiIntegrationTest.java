package com.oldbook.catalog;

import com.oldbook.entity.catalog.CuaHang;
import com.oldbook.entity.catalog.DanhMuc;
import com.oldbook.entity.catalog.Sach;
import com.oldbook.entity.catalog.SachDaXem;
import com.oldbook.entity.catalog.UserBookKey;
import com.oldbook.entity.identity.NguoiDung;
import com.oldbook.entity.identity.TaiKhoan;
import com.oldbook.repository.catalog.DanhMucRepository;
import com.oldbook.repository.catalog.SachRepository;
import com.oldbook.repository.identity.NguoiDungRepository;
import com.oldbook.repository.identity.TaiKhoanRepository;
import com.oldbook.repository.store.CuaHangRepository;
import com.oldbook.service.auth.EmailService;
import com.oldbook.service.auth.JwtService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
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
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.config.import=",
                "spring.datasource.url=jdbc:h2:mem:recently_viewed_${random.uuid};MODE=MSSQLServer;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000"
        })
@ActiveProfiles({"test", "catalog-test"})
class RecentlyViewedApiIntegrationTest {

    private static final AtomicInteger PHONE_SEQUENCE = new AtomicInteger();

    @Value("${local.server.port}") private int port;
    @Autowired private ObjectMapper mapper;
    @Autowired private JwtService jwtService;
    @Autowired private NguoiDungRepository users;
    @Autowired private TaiKhoanRepository accounts;
    @Autowired private CuaHangRepository stores;
    @Autowired private DanhMucRepository categories;
    @Autowired private SachRepository books;
    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TransactionTemplate transactions;
    @MockitoBean private EmailService emailService;

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private Actor customer;
    private Actor otherCustomer;
    private Actor owner;
    private CuaHang store;
    private DanhMuc category;

    @BeforeEach
    void seed() {
        customer = actor("KHACH_HANG");
        otherCustomer = actor("KHACH_HANG");
        owner = actor("CHU_CUA_HANG");
        store = stores.save(CuaHang.builder().chuShop(users.getReferenceById(owner.maND()))
                .tenCuaHang("Recently viewed shop " + UUID.randomUUID()).trangThaiDuyet("DA_DUYET").build());
        category = categories.save(DanhMuc.builder().tenDanhMuc("Recently viewed " + UUID.randomUUID()).build());
    }

    @Test
    void wishlistRendersHistoryAndOnlyRealDetailPagesExposeTheTrackingHook() throws Exception {
        var shell = request("GET", "/wishlist", null);
        assertThat(shell.statusCode()).as(shell.body()).isEqualTo(200);
        assertThat(shell.body()).contains("recentlyViewedSection", "recentlyViewedGrid", "recentlyViewedEmpty",
                        "recentlyViewedLoading", "recentlyViewedError", "recentlyViewedRetry",
                        "/js/recently-viewed.js", "/js/auth.js")
                .doesNotContain("th:replace", "fragments/layout", "data-viewed-book-id");
        assertThat(request("GET", "/js/recently-viewed.js", null).statusCode()).isEqualTo(200);

        Sach book = book("Real detail page", "DA_DUYET", "DANG_BAN", 1);
        var detail = request("GET", "/books/" + book.getMaSach(), null);
        assertThat(detail.statusCode()).as(detail.body()).isEqualTo(200);
        assertThat(detail.body()).contains("data-viewed-book-id=\"" + book.getMaSach() + "\"",
                        "/js/recently-viewed.js")
                .doesNotContain("th:attr=");
        var missingDetail = request("GET", "/books/2147483647", null);
        assertThat(missingDetail.statusCode()).isEqualTo(404);
        assertThat(missingDetail.body()).doesNotContain("data-viewed-book-id");
        assertThat(request("GET", "/books", null).body()).doesNotContain("data-viewed-book-id");
    }

    @Test
    void allHistoryOperationsRequireAnAuthenticatedCustomer() throws Exception {
        for (String method : List.of("GET", "PUT")) {
            String path = "GET".equals(method) ? "/api/recently-viewed" : "/api/recently-viewed/1";
            assertCode(request(method, path, null), 401);
            assertCode(request(method, path, "invalid-token"), 401);
            assertCode(request(method, path, owner.token()), 403);
            for (String role : List.of("QUAN_LY", "QUAN_TRI_VIEN")) {
                assertCode(request(method, path, actor(role).token()), 403);
            }
        }
        assertThat(data(request("GET", "/api/recently-viewed", customer.token())).isEmpty()).isTrue();
    }

    @Test
    void viewingAndRevisitingPersistOneRowPerBookAndRefreshTheNewestFirstOrder() throws Exception {
        Sach first = book("First visit", "DA_DUYET", "DANG_BAN", 3);
        Sach second = book("Second visit", "DA_DUYET", "DANG_BAN", 2);
        String firstPath = "/api/recently-viewed/" + first.getMaSach();
        var response = data(request("PUT", firstPath, customer.token()));
        assertThat(response.get("maSach").asInt()).isEqualTo(first.getMaSach());
        assertThat(lastViewed(customer.maND(), first.getMaSach())).isNotNull();
        LocalDateTime originalVisit = LocalDateTime.of(2020, 1, 1, 12, 0);
        jdbc.update("update sach_da_xem set lan_xem_gan_nhat = ? where ma_nd = ? and ma_sach = ?",
                originalVisit, customer.maND(), first.getMaSach());
        assertCode(request("PUT", "/api/recently-viewed/" + second.getMaSach(), customer.token()), 200);
        assertThat(bookIds(data(request("GET", "/api/recently-viewed", customer.token()))))
                .containsExactly(second.getMaSach(), first.getMaSach());

        assertCode(request("PUT", firstPath, customer.token()), 200);
        assertThat(lastViewed(customer.maND(), first.getMaSach())).isAfter(originalVisit);
        assertThat(lastViewed(customer.maND(), first.getMaSach()))
                .isAfterOrEqualTo(lastViewed(customer.maND(), second.getMaSach()));
        assertThat(historyCount(customer.maND())).isEqualTo(2L);
        assertThat(bookIds(data(request("GET", "/api/recently-viewed", customer.token()))))
                .containsExactly(first.getMaSach(), second.getMaSach());
    }

    @Test
    void accountsCannotReadOrWriteAnotherCustomersHistoryBySpoofingMaND() throws Exception {
        Sach book = book("Private viewing history", "DA_DUYET", "DANG_BAN", 1);
        String path = "/api/recently-viewed/" + book.getMaSach();
        assertCode(request("PUT", path, customer.token()), 200);
        LocalDateTime original = lastViewed(customer.maND(), book.getMaSach());
        assertThat(data(request("GET", "/api/recently-viewed?maND=" + customer.maND(), otherCustomer.token()))
                .isEmpty()).isTrue();
        assertCode(request("PUT", path + "?maND=" + customer.maND(), otherCustomer.token()), 200);
        assertThat(lastViewed(customer.maND(), book.getMaSach())).isEqualTo(original);
        assertThat(lastViewed(otherCustomer.maND(), book.getMaSach())).isNotNull();
        assertThat(historyCount(customer.maND())).isEqualTo(1L);
        assertThat(historyCount(otherCustomer.maND())).isEqualTo(1L);

        Sach second = book("Other customer's book", "DA_DUYET", "DANG_BAN", 1);
        assertCode(request("PUT", "/api/recently-viewed/" + second.getMaSach(), otherCustomer.token()), 200);
        assertThat(bookIds(data(request("GET", "/api/recently-viewed", customer.token()))))
                .containsExactly(book.getMaSach());
        assertThat(bookIds(data(request("GET", "/api/recently-viewed", otherCustomer.token()))))
                .containsExactly(second.getMaSach(), book.getMaSach());
    }

    @Test
    void listsAtMostTwentyVisibleBooksAndFiltersUnavailableBooksBeforeLimiting() throws Exception {
        List<Sach> visited = new ArrayList<>();
        for (int index = 0; index < 24; index++) {
            Sach book = book("Visit " + index, "DA_DUYET", "DANG_BAN", 1);
            visited.add(book);
            assertCode(request("PUT", "/api/recently-viewed/" + book.getMaSach(), customer.token()), 200);
            // Give each visit a distinct timestamp independently of database clock precision.
            jdbc.update("update sach_da_xem set lan_xem_gan_nhat = ? where ma_nd = ? and ma_sach = ?",
                    LocalDateTime.of(2020, 1, 1, 0, 0).plusMinutes(index), customer.maND(), book.getMaSach());
        }
        assertThat(bookIds(data(request("GET", "/api/recently-viewed", customer.token()))))
                .containsExactlyElementsOf(visited.subList(4, 24).reversed().stream().map(Sach::getMaSach).toList());

        for (Sach book : visited.subList(20, 24)) {
            book.setTrangThaiBan("NGUNG_BAN");
            books.save(book);
        }
        assertThat(bookIds(data(request("GET", "/api/recently-viewed", customer.token()))))
                .containsExactlyElementsOf(visited.subList(0, 20).reversed().stream().map(Sach::getMaSach).toList());
    }

    @Test
    void publicHistoryRetainsSoldOutBooksButHidesUnavailableBooksAndStores() throws Exception {
        Sach soldOut = book("Sold out", "DA_DUYET", "DANG_BAN", 0);
        soldOut.setLyDoTuChoi("private moderation feedback");
        books.save(soldOut);
        var added = request("PUT", "/api/recently-viewed/" + soldOut.getMaSach(), customer.token());
        assertThat(data(added).get("soLuongTon").asInt()).isZero();
        assertThat(added.body()).doesNotContain("private moderation feedback", "matKhau", "maND", "chuShop", "taiKhoans");

        Sach hidden = book("Hidden after viewing", "DA_DUYET", "DANG_BAN", 1);
        String hiddenPath = "/api/recently-viewed/" + hidden.getMaSach();
        assertCode(request("PUT", hiddenPath, customer.token()), 200);
        hidden.setTrangThaiBan("NGUNG_BAN");
        books.save(hidden);
        assertCode(request("PUT", hiddenPath, customer.token()), 400);
        var visible = request("GET", "/api/recently-viewed", customer.token());
        assertThat(bookIds(data(visible))).containsExactly(soldOut.getMaSach());
        assertThat(visible.body()).doesNotContain("private moderation feedback", "matKhau", "maND", "chuShop", "taiKhoans");

        Sach pending = book("Pending moderation", "CHO_DUYET", "DANG_BAN", 1);
        assertCode(request("PUT", "/api/recently-viewed/" + pending.getMaSach(), customer.token()), 400);
        assertThat(lastViewed(customer.maND(), pending.getMaSach())).isNull();
        store.setTrangThaiDuyet("CHO_DUYET");
        stores.save(store);
        assertThat(data(request("GET", "/api/recently-viewed", customer.token())).isEmpty()).isTrue();
        assertCode(request("PUT", "/api/recently-viewed/" + soldOut.getMaSach(), customer.token()), 400);
    }

    @Test
    void invalidOrMissingBookIdsDoNotCreateHistory() throws Exception {
        for (String invalid : List.of("0", "-1", "abc", "2147483648", "2147483647")) {
            assertCode(request("PUT", "/api/recently-viewed/" + invalid, customer.token()), 400);
        }
        assertThat(historyCount(customer.maND())).isZero();
        assertThat(data(request("GET", "/api/recently-viewed", customer.token())).isEmpty()).isTrue();
    }

    @Test
    void concurrentFirstViewsWaitForTheCustomerLockAndPersistOnlyOneHistoryEntry() throws Exception {
        Sach book = book("Concurrent views", "DA_DUYET", "DANG_BAN", 1);
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        var holder = CompletableFuture.runAsync(() -> transactions.executeWithoutResult(status -> {
            entityManager.find(NguoiDung.class, customer.maND(), LockModeType.PESSIMISTIC_WRITE);
            locked.countDown();
            try {
                if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Lock test timed out");
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(exception);
            }
        }));
        try {
            assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();
            var putRequest = httpRequest("PUT", "/api/recently-viewed/" + book.getMaSach(), customer.token());
            var first = client.sendAsync(putRequest, HttpResponse.BodyHandlers.ofString());
            var second = client.sendAsync(putRequest, HttpResponse.BodyHandlers.ofString());
            assertThatThrownBy(() -> first.get(300, TimeUnit.MILLISECONDS))
                    .isInstanceOf(java.util.concurrent.TimeoutException.class);
            assertThatThrownBy(() -> second.get(300, TimeUnit.MILLISECONDS))
                    .isInstanceOf(java.util.concurrent.TimeoutException.class);
            release.countDown();
            holder.get(10, TimeUnit.SECONDS);
            assertCode(first.get(10, TimeUnit.SECONDS), 200);
            assertCode(second.get(10, TimeUnit.SECONDS), 200);
            assertThat(historyCount(customer.maND())).isEqualTo(1L);
            assertThat(bookIds(data(request("GET", "/api/recently-viewed", customer.token()))))
                    .containsExactly(book.getMaSach());
        } finally {
            release.countDown();
            holder.get(10, TimeUnit.SECONDS);
        }
    }

    private LocalDateTime lastViewed(Integer maND, Integer maSach) {
        return transactions.execute(status -> {
            var visit = entityManager.find(SachDaXem.class, new UserBookKey(maND, maSach));
            return visit == null ? null : visit.getLanXemGanNhat();
        });
    }

    private long historyCount(Integer maND) {
        return jdbc.queryForObject("select count(*) from sach_da_xem where ma_nd = ?", Long.class, maND);
    }

    private List<Integer> bookIds(JsonNode response) {
        List<Integer> ids = new ArrayList<>();
        response.forEach(book -> ids.add(book.get("maSach").asInt()));
        return ids;
    }

    private Actor actor(String role) {
        var user = users.save(NguoiDung.builder().email("recently-viewed-" + UUID.randomUUID() + "@test.local")
                .matKhau("test-only-hash").soDienThoai(String.format("09%08d", PHONE_SEQUENCE.incrementAndGet())).build());
        var account = accounts.save(TaiKhoan.builder().nguoiDung(user).vaiTro(role).trangThai("HOAT_DONG").build());
        return new Actor(user.getMaND(), jwtService.generateToken(user.getMaND(), account.getMaTK(), role));
    }

    private Sach book(String name, String approval, String sale, Integer stock) {
        return books.save(Sach.builder().cuaHang(store).danhMuc(category).tenSach(name)
                .giaBanCu(new BigDecimal("75000")).doMoiPercent(new BigDecimal("80"))
                .trangThaiDuyet(approval).trangThaiBan(sale).soLuongTon(stock).build());
    }

    private HttpRequest httpRequest(String method, String path, String token) {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).timeout(Duration.ofSeconds(15));
        if (token != null) builder.header("Authorization", "Bearer " + token);
        return builder.method(method, HttpRequest.BodyPublishers.noBody()).build();
    }

    private HttpResponse<String> request(String method, String path, String token) throws Exception {
        return client.send(httpRequest(method, path, token), HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode data(HttpResponse<String> response) {
        assertCode(response, 200);
        return mapper.readTree(response.body()).get("data");
    }

    private void assertCode(HttpResponse<String> response, int status) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
        // Framework errors for malformed path variables do not use the application's ApiResponse shape.
        if (!response.body().contains("\"status\"")) {
            assertThat(mapper.readTree(response.body()).get("code").asInt()).isEqualTo(status);
        }
    }

    private record Actor(Integer maND, String token) { }
}
