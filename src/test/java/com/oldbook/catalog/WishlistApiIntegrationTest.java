package com.oldbook.catalog;

import com.oldbook.entity.catalog.CuaHang;
import com.oldbook.entity.catalog.DanhMuc;
import com.oldbook.entity.catalog.Sach;
import com.oldbook.entity.catalog.UserBookKey;
import com.oldbook.entity.identity.NguoiDung;
import com.oldbook.entity.identity.TaiKhoan;
import com.oldbook.repository.catalog.DanhMucRepository;
import com.oldbook.repository.catalog.SachRepository;
import com.oldbook.repository.catalog.YeuThichRepository;
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
                "spring.datasource.url=jdbc:h2:mem:wishlist_${random.uuid};MODE=MSSQLServer;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000"
        })
@ActiveProfiles({"test", "catalog-test"})
class WishlistApiIntegrationTest {

    private static final AtomicInteger PHONE_SEQUENCE = new AtomicInteger();

    @Value("${local.server.port}") private int port;
    @Autowired private ObjectMapper mapper;
    @Autowired private JwtService jwtService;
    @Autowired private NguoiDungRepository users;
    @Autowired private TaiKhoanRepository accounts;
    @Autowired private CuaHangRepository stores;
    @Autowired private DanhMucRepository categories;
    @Autowired private SachRepository books;
    @Autowired private YeuThichRepository favorites;
    @Autowired private EntityManager entityManager;
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
                .tenCuaHang("Wishlist shop " + UUID.randomUUID()).trangThaiDuyet("DA_DUYET").build());
        category = categories.save(DanhMuc.builder().tenDanhMuc("Wishlist " + UUID.randomUUID()).build());
    }

    @Test
    void htmlShellRendersAndAllWishlistOperationsRequireCustomerAuthentication() throws Exception {
        var shell = request("GET", "/wishlist", null);
        assertThat(shell.statusCode()).as(shell.body()).isEqualTo(200);
        assertThat(shell.body()).contains("Sản phẩm yêu thích", "/js/wishlist.js", "/js/auth.js")
                .doesNotContain("th:replace", "fragments/layout");
        assertThat(request("GET", "/js/wishlist.js", null).statusCode()).isEqualTo(200);
        for (String method : List.of("GET", "PUT", "DELETE")) {
            String path = "GET".equals(method) ? "/api/wishlist" : "/api/wishlist/1";
            assertCode(request(method, path, null), 401);
            assertCode(request(method, path, "invalid-token"), 401);
            assertCode(request(method, path, owner.token()), 403);
        }
        for (String role : List.of("QUAN_LY", "QUAN_TRI_VIEN")) {
            assertCode(request("GET", "/api/wishlist", actor(role).token()), 403);
        }
        assertThat(data(request("GET", "/api/wishlist", customer.token())).isEmpty()).isTrue();
    }

    @Test
    void addingRepeatingListingAndRemovingFavoritePersistsAnOrderedUniqueList() throws Exception {
        Sach first = book("Sách yêu thích đầu tiên", "DA_DUYET", "DANG_BAN", 5);
        Sach second = book("Sách yêu thích mới nhất", "DA_DUYET", "DANG_BAN", 2);
        String firstPath = "/api/wishlist/" + first.getMaSach();
        var firstResponse = data(request("PUT", firstPath, customer.token()));
        assertThat(firstResponse.get("maSach").asInt()).isEqualTo(first.getMaSach());
        var originalDate = favorites.findById(new UserBookKey(customer.maND(), first.getMaSach()))
                .orElseThrow().getNgayThem();
        assertCode(request("PUT", "/api/wishlist/" + second.getMaSach(), customer.token()), 200);
        assertCode(request("PUT", firstPath, customer.token()), 200);
        assertThat(favorites.findById(new UserBookKey(customer.maND(), first.getMaSach()))
                .orElseThrow().getNgayThem()).isEqualTo(originalDate);
        var list = data(request("GET", "/api/wishlist", customer.token()));
        assertThat(list.size()).isEqualTo(2);
        assertThat(list.get(0).get("maSach").asInt()).isEqualTo(second.getMaSach());
        assertThat(list.get(1).get("maSach").asInt()).isEqualTo(first.getMaSach());
        assertCode(request("DELETE", firstPath, customer.token()), 200);
        assertCode(request("DELETE", firstPath, customer.token()), 200);
        assertThat(favorites.existsById(new UserBookKey(customer.maND(), first.getMaSach()))).isFalse();
        assertThat(data(request("GET", "/api/wishlist", customer.token())).size()).isEqualTo(1);
        assertCode(request("DELETE", "/api/wishlist/" + second.getMaSach(), customer.token()), 200);
        assertThat(data(request("GET", "/api/wishlist", customer.token())).isEmpty()).isTrue();
    }

    @Test
    void customersOnlyReadAndChangeTheirOwnFavoritesEvenWithAnOwnerQueryParameter() throws Exception {
        Sach book = book("Yêu thích riêng tư", "DA_DUYET", "DANG_BAN", 1);
        String path = "/api/wishlist/" + book.getMaSach();
        assertCode(request("PUT", path, customer.token()), 200);
        assertThat(data(request("GET", "/api/wishlist?maND=" + customer.maND(), otherCustomer.token())).isEmpty()).isTrue();
        assertCode(request("DELETE", path + "?maND=" + customer.maND(), otherCustomer.token()), 200);
        assertThat(favorites.existsById(new UserBookKey(customer.maND(), book.getMaSach()))).isTrue();
        assertCode(request("PUT", path + "?maND=" + customer.maND(), otherCustomer.token()), 200);
        assertThat(favorites.existsById(new UserBookKey(otherCustomer.maND(), book.getMaSach()))).isTrue();
        assertCode(request("DELETE", path, customer.token()), 200);
        assertThat(data(request("GET", "/api/wishlist", customer.token())).isEmpty()).isTrue();
        assertThat(data(request("GET", "/api/wishlist", otherCustomer.token())).size()).isEqualTo(1);
    }

    @Test
    void publicFavoritesIncludeSoldOutBooksAndNullOptionalFieldsWithoutPrivateData() throws Exception {
        Sach soldOut = book("Đã hết hàng", "DA_DUYET", "DANG_BAN", 0);
        soldOut.setLyDoTuChoi("private moderation feedback");
        books.save(soldOut);
        Sach nullable = book("Thiếu thông tin tùy chọn", "DA_DUYET", "DANG_BAN", null);
        nullable.setGiaBanCu(null);
        nullable.setDoMoiPercent(null);
        books.save(nullable);
        var added = request("PUT", "/api/wishlist/" + soldOut.getMaSach(), customer.token());
        assertThat(data(added).get("soLuongTon").asInt()).isZero();
        assertThat(added.body()).doesNotContain("private moderation feedback", "matKhau", "maND", "chuShop", "taiKhoans");
        assertCode(request("PUT", "/api/wishlist/" + nullable.getMaSach(), customer.token()), 200);
        var response = request("GET", "/api/wishlist", customer.token());
        var listed = data(response);
        assertThat(listed.size()).isEqualTo(2);
        assertThat(listed.get(0).get("maSach").asInt()).isEqualTo(nullable.getMaSach());
        assertThat(listed.get(0).get("soLuongTon").asInt()).isZero();
        assertThat(response.body()).doesNotContain("private moderation feedback", "matKhau", "maND", "chuShop", "taiKhoans");
    }

    @Test
    void additionsRequirePublicBooksButHiddenFavoritesCanStillBeRemoved() throws Exception {
        Sach book = book("Sách được ẩn sau khi lưu", "DA_DUYET", "DANG_BAN", 1);
        String path = "/api/wishlist/" + book.getMaSach();
        assertCode(request("PUT", path, customer.token()), 200);
        book.setTrangThaiBan("NGUNG_BAN");
        books.save(book);
        assertThat(data(request("GET", "/api/wishlist", customer.token())).isEmpty()).isTrue();
        assertCode(request("PUT", path, customer.token()), 400);
        assertCode(request("DELETE", path, customer.token()), 200);
        assertThat(favorites.existsById(new UserBookKey(customer.maND(), book.getMaSach()))).isFalse();

        Sach pending = book("Chờ duyệt", "CHO_DUYET", "DANG_BAN", 1);
        assertCode(request("PUT", "/api/wishlist/" + pending.getMaSach(), customer.token()), 400);
        Sach approved = book("Cửa hàng bị ẩn", "DA_DUYET", "DANG_BAN", 1);
        assertCode(request("PUT", "/api/wishlist/" + approved.getMaSach(), customer.token()), 200);
        store.setTrangThaiDuyet("CHO_DUYET");
        stores.save(store);
        assertThat(data(request("GET", "/api/wishlist", customer.token())).isEmpty()).isTrue();
        assertCode(request("PUT", "/api/wishlist/" + approved.getMaSach(), customer.token()), 400);
        assertCode(request("DELETE", "/api/wishlist/" + approved.getMaSach(), customer.token()), 200);
        for (String invalid : List.of("0", "-1", "abc")) {
            assertCode(request("PUT", "/api/wishlist/" + invalid, customer.token()), 400);
            assertCode(request("DELETE", "/api/wishlist/" + invalid, customer.token()), 400);
        }
        assertCode(request("PUT", "/api/wishlist/2147483647", customer.token()), 400);
        assertCode(request("DELETE", "/api/wishlist/2147483647", customer.token()), 200);
    }

    @Test
    void concurrentAdditionsWaitForTheOwnerLockAndProduceOnlyOneFavorite() throws Exception {
        Sach book = book("Yêu thích đồng thời", "DA_DUYET", "DANG_BAN", 1);
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
            var addRequest = httpRequest("PUT", "/api/wishlist/" + book.getMaSach(), customer.token());
            var first = client.sendAsync(addRequest, HttpResponse.BodyHandlers.ofString());
            var second = client.sendAsync(addRequest, HttpResponse.BodyHandlers.ofString());
            assertThatThrownBy(() -> first.get(300, TimeUnit.MILLISECONDS))
                    .isInstanceOf(java.util.concurrent.TimeoutException.class);
            assertThatThrownBy(() -> second.get(300, TimeUnit.MILLISECONDS))
                    .isInstanceOf(java.util.concurrent.TimeoutException.class);
            release.countDown();
            holder.get(10, TimeUnit.SECONDS);
            assertCode(first.get(10, TimeUnit.SECONDS), 200);
            assertCode(second.get(10, TimeUnit.SECONDS), 200);
            assertThat(data(request("GET", "/api/wishlist", customer.token())).size()).isEqualTo(1);
        } finally {
            release.countDown();
            holder.get(10, TimeUnit.SECONDS);
        }
    }

    private Actor actor(String role) {
        var user = users.save(NguoiDung.builder().email("wishlist-" + UUID.randomUUID() + "@test.local")
                .matKhau("test-only-hash").soDienThoai(String.format("08%08d", PHONE_SEQUENCE.incrementAndGet())).build());
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
        // Framework errors for a nonnumeric path do not use the application's ApiResponse shape.
        if (!response.body().contains("\"status\"")) {
            assertThat(mapper.readTree(response.body()).get("code").asInt()).isEqualTo(status);
        }
    }

    private record Actor(Integer maND, String token) { }
}
