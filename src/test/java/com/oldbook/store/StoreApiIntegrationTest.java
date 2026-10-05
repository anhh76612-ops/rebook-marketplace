package com.oldbook.store;

import com.oldbook.filter.auth.JwtAuthenticationFilter.AuthenticatedUserDetails;
import com.oldbook.service.auth.JwtService;
import com.oldbook.entity.catalog.CuaHang;
import com.oldbook.entity.catalog.DanhMuc;
import com.oldbook.entity.catalog.Sach;
import com.oldbook.repository.catalog.DanhMucRepository;
import com.oldbook.repository.catalog.SachRepository;
import com.oldbook.entity.identity.NguoiDung;
import com.oldbook.entity.identity.TaiKhoan;
import com.oldbook.repository.identity.NguoiDungRepository;
import com.oldbook.repository.identity.TaiKhoanRepository;
import com.oldbook.repository.order.ChiTietGioHangRepository;
import com.oldbook.repository.order.GioHangRepository;
import com.oldbook.repository.store.CuaHangRepository;
import com.oldbook.service.store.KiemDuyetService;
import com.oldbook.repository.system.NhatKyHeThongRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
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
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.config.import=")
@ActiveProfiles({"test", "catalog-test"})
class StoreApiIntegrationTest {

    @Value("${local.server.port}")
    private int port;
    @Autowired private ObjectMapper mapper;
    @Autowired private JwtService jwtService;
    @Autowired private NguoiDungRepository nguoiDungRepository;
    @Autowired private TaiKhoanRepository taiKhoanRepository;
    @Autowired private CuaHangRepository cuaHangRepository;
    @Autowired private DanhMucRepository danhMucRepository;
    @Autowired private SachRepository sachRepository;
    @Autowired private NhatKyHeThongRepository nhatKyRepository;
    @Autowired private ChiTietGioHangRepository chiTietGioHangRepository;
    @Autowired private GioHangRepository gioHangRepository;
    @Autowired private TransactionTemplate transactions;
    @Autowired private KiemDuyetService kiemDuyetService;

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private Actor owner;
    private Actor otherOwner;
    private Actor reviewer;
    private Actor customer;
    private DanhMuc category;
    private int actorSequence;

    @BeforeEach
    void seed() {
        SecurityContextHolder.clearContext();
        chiTietGioHangRepository.deleteAllInBatch();
        gioHangRepository.deleteAllInBatch();
        nhatKyRepository.deleteAllInBatch();
        sachRepository.deleteAllInBatch();
        cuaHangRepository.deleteAllInBatch();
        taiKhoanRepository.deleteAllInBatch();
        nguoiDungRepository.deleteAllInBatch();
        danhMucRepository.deleteAllInBatch();
        owner = actor("CHU_CUA_HANG", "owner@test.local");
        otherOwner = actor("CHU_CUA_HANG", "other@test.local");
        reviewer = actor("QUAN_LY", "reviewer@test.local");
        customer = actor("KHACH_HANG", "customer@test.local");
        category = danhMucRepository.save(DanhMuc.builder().tenDanhMuc("Lập trình").build());
    }

    @Test
    void publicCatalogFiltersBothApprovalsAndSaleStateWithoutExposingIdentity() throws Exception {
        CuaHang approved = store(owner, "DA_DUYET");
        CuaHang pending = store(otherOwner, "CHO_DUYET");
        Sach visible = book(approved, "Java cơ bản", "DA_DUYET", "DANG_BAN", 5);
        visible.setLyDoTuChoi("private moderation feedback");
        sachRepository.save(visible);
        Sach unavailable = book(approved, "Java chờ duyệt", "CHO_DUYET", "DANG_BAN", 2);
        book(approved, "Java ngừng bán", "DA_DUYET", "NGUNG_BAN", 2);
        Sach parentPending = book(pending, "Java shop chưa duyệt", "DA_DUYET", "DANG_BAN", 2);

        HttpResponse<String> response = request("GET", "/api/sach/tim-kiem?tuKhoa=java&giaTu=50000&giaDen=90000", null, null);
        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode data = json(response).get("data");
        assertThat(data.get("totalElements").asLong()).isEqualTo(1);
        assertThat(data.get("content").get(0).get("maSach").asInt()).isEqualTo(visible.getMaSach());
        assertThat(response.body()).doesNotContain("matKhau", "maND", "chuShop", "taiKhoans", "private moderation feedback");
        assertThat(request("GET", "/api/sach/" + unavailable.getMaSach(), null, null).statusCode()).isEqualTo(400);
        assertThat(request("GET", "/api/sach/" + parentPending.getMaSach(), null, null).statusCode()).isEqualTo(400);
        assertThat(request("GET", "/api/sach/danh-muc", null, null).statusCode()).isEqualTo(200);
    }

    @Test
    void legacyCatalogKeepsArrayContractAndAccentInsensitiveTitleAuthorAndStoreSearch() throws Exception {
        CuaHang approved = store(owner, "DA_DUYET");
        approved.setTenCuaHang("Tiệm sách Sài Gòn");
        cuaHangRepository.save(approved);
        Sach visible = book(approved, "Đắc nhân tâm", "DA_DUYET", "DANG_BAN", 5);
        visible.setTacGia("Nguyễn Văn A");
        visible.setLyDoTuChoi("private legacy moderation feedback");
        sachRepository.save(visible);
        book(approved, "Sách chưa công khai", "CHO_DUYET", "DANG_BAN", 5);
        book(store(otherOwner, "DA_DUYET"), "Sách khác", "DA_DUYET", "DANG_BAN", 5);

        HttpResponse<String> title = request("GET", "/api/sach?q=dac%20nhan&category=lap%20trinh", null, null);
        assertCode(title, 200);
        assertThat(json(title).get("data").isArray()).isTrue();
        assertThat(json(title).get("data").size()).isEqualTo(1);
        assertThat(json(title).get("data").get(0).get("maSach").asInt()).isEqualTo(visible.getMaSach());
        assertThat(title.body()).doesNotContain("matKhau", "maND", "chuShop", "taiKhoans",
                "private legacy moderation feedback");
        assertThat(json(request("GET", "/api/sach?q=nguyen", null, null)).get("data").get(0)
                .get("maSach").asInt()).isEqualTo(visible.getMaSach());
        var storeMatches = json(request("GET", "/api/sach?q=sai%20gon", null, null)).get("data");
        assertThat(storeMatches.size()).isEqualTo(1);
        assertThat(storeMatches.get(0).get("maSach").asInt()).isEqualTo(visible.getMaSach());
    }

    @Test
    void legacyCatalogBookCanStillBeAddedToExistingCustomerCart() throws Exception {
        CuaHang shop = store(owner, "DA_DUYET");
        Sach visible = book(shop, "Java cho giỏ hàng", "DA_DUYET", "DANG_BAN", 5);
        Sach pending = book(shop, "Java chờ duyệt", "CHO_DUYET", "DANG_BAN", 5);
        var catalog = json(request("GET", "/api/sach?q=java", null, null)).get("data");
        assertThat(catalog.isArray()).isTrue();
        assertThat(catalog.size()).isEqualTo(1);
        int bookId = catalog.get(0).get("maSach").asInt();
        assertThat(bookId).isEqualTo(visible.getMaSach());

        var added = request("POST", "/api/order/cart/items", customer.token(),
                Map.of("maSach", bookId, "soLuong", 2));
        assertCode(added, 200);
        var cart = json(added).get("data");
        assertThat(cart.get("tongSoLuong").asInt()).isEqualTo(2);
        assertThat(cart.get("items").get(0).get("maSach").asInt()).isEqualTo(bookId);
        assertThat(cart.get("items").get(0).get("coTheMua").asBoolean()).isTrue();
        assertCode(request("GET", "/api/order/cart", customer.token(), null), 200);
        assertCode(request("GET", "/api/order/cart", null, null), 401);
        assertCode(request("POST", "/api/order/cart/items", owner.token(),
                Map.of("maSach", bookId, "soLuong", 1)), 403);
        assertCode(request("POST", "/api/order/cart/items", customer.token(),
                Map.of("maSach", pending.getMaSach(), "soLuong", 1)), 400);
    }

    @Test
    void publicPagesRenderRealBookFieldsAndUnavailableDetailReturns404() throws Exception {
        CuaHang shop = store(owner, "DA_DUYET");
        Sach visible = book(shop, "Java thực hành", "DA_DUYET", "DANG_BAN", 5);
        Sach pending = book(shop, "Sách chưa công khai", "CHO_DUYET", "DANG_BAN", 5);
        HttpResponse<String> listing = request("GET", "/books/tim-kiem?tuKhoa=Java", null, null);
        assertThat(listing.statusCode()).isEqualTo(200);
        assertThat(listing.body()).contains("Java thực hành", "/books/" + visible.getMaSach()).doesNotContain("Sách chưa công khai");
        HttpResponse<String> detail = request("GET", "/books/" + visible.getMaSach(), null, null);
        assertThat(detail.statusCode()).isEqualTo(200);
        assertThat(detail.body()).contains("Java thực hành", "Lập trình", shop.getTenCuaHang());
        var existingCatalog = request("GET", "/books?q=java", null, null);
        assertThat(existingCatalog.statusCode()).isEqualTo(200);
        assertThat(existingCatalog.body()).contains("bookGrid", "catalog.js");
        assertThat(request("GET", "/cart", null, null).statusCode()).isEqualTo(200);
        assertThat(request("GET", "/", null, null).statusCode()).isEqualTo(200);
        assertThat(request("GET", "/books/" + pending.getMaSach(), null, null).statusCode()).isEqualTo(404);
    }

    @Test
    void searchPresetFiltersKeepSelectionsAndResultsWhenFollowingPagination() throws Exception {
        CuaHang shop = store(owner, "DA_DUYET");
        Sach newBook = book(shop, "Java 1 như mới", "DA_DUYET", "DANG_BAN", 5);
        newBook.setDoMoiPercent(new BigDecimal("90"));
        sachRepository.save(newBook);
        Sach fairBook = book(shop, "Java 2 khá", "DA_DUYET", "DANG_BAN", 5);
        fairBook.setDoMoiPercent(new BigDecimal("69.99"));
        sachRepository.save(fairBook);
        book(shop, "Java 3 tốt", "DA_DUYET", "DANG_BAN", 5);

        String path = "/books/tim-kiem?tuKhoa=Java&maDM=" + category.getMaDM()
                + "&maCH=" + shop.getMaCH()
                + "&tinhTrang=NHU_MOI&tinhTrang=KHA&khoangGia=TU_50000_DEN_150000"
                + "&conHang=true&sort=ten-az&size=1";
        var first = request("GET", path, null, null);
        assertThat(first.statusCode()).as(first.body()).isEqualTo(200);
        assertThat(first.body()).contains("2 sách phù hợp", "Java 1 như mới")
                .doesNotContain("Java 2 khá", "Java 3 tốt");
        assertThat(inputTag(first.body(), "conditionNew")).contains("checked=\"checked\"");
        assertThat(inputTag(first.body(), "conditionFair")).contains("checked=\"checked\"");
        assertThat(inputTag(first.body(), "conditionGood")).doesNotContain("checked=");
        assertThat(inputTag(first.body(), "priceMedium")).contains("checked=\"checked\"");
        assertThat(inputTag(first.body(), "priceLow")).doesNotContain("checked=");
        assertThat(inputTag(first.body(), "priceHigh")).doesNotContain("checked=");

        var nextLink = Pattern.compile("<a\\b[^>]*href=\"([^\"]*)\"[^>]*>Sau</a>").matcher(first.body());
        assertThat(nextLink.find()).isTrue();
        String nextPath = nextLink.group(1).replace("&amp;", "&");
        assertThat(nextPath).contains("tinhTrang=NHU_MOI", "tinhTrang=KHA",
                "khoangGia=TU_50000_DEN_150000", "tuKhoa=Java", "maDM=" + category.getMaDM(),
                "maCH=" + shop.getMaCH(), "conHang=true", "sort=ten-az", "size=1", "page=1");
        var second = request("GET", nextPath, null, null);
        assertThat(second.statusCode()).as(second.body()).isEqualTo(200);
        assertThat(second.body()).contains("2 sách phù hợp", "Java 2 khá")
                .doesNotContain("Java 1 như mới", "Java 3 tốt");
        assertThat(inputTag(second.body(), "conditionNew")).contains("checked=\"checked\"");
        assertThat(inputTag(second.body(), "priceMedium")).contains("checked=\"checked\"");

        var cleared = request("GET", "/books/tim-kiem", null, null);
        assertThat(cleared.statusCode()).as(cleared.body()).isEqualTo(200);
        assertThat(cleared.body()).contains("3 sách phù hợp", "Java 3 tốt");
        assertThat(inputTag(cleared.body(), "conditionNew")).doesNotContain("checked=");
        assertThat(inputTag(cleared.body(), "priceMedium")).doesNotContain("checked=");
    }

    @Test
    void storeRoutesReturnJson401And403ForInvalidIdentityAndWrongRoles() throws Exception {
        assertCode(request("GET", "/api/store/me", null, null), 401);
        assertCode(request("GET", "/api/store/me", "bad-token", null), 401);
        assertCode(request("GET", "/api/store/me", jwtService.generateRoleSelectionToken(owner.maND()), null), 401);
        assertCode(request("GET", "/api/store/me", customer.token(), null), 403);
        assertCode(request("GET", "/api/store/kiem-duyet/sach", owner.token(), null), 403);
        assertCode(request("GET", "/api/store/me", reviewer.token(), null), 403);
        assertCode(request("GET", "/api/store/kiem-duyet/sach", reviewer.token(), null), 200);
        Actor admin = actor("QUAN_TRI_VIEN", "admin@test.local");
        assertCode(request("GET", "/api/store/kiem-duyet/cua-hang", admin.token(), null), 200);
    }

    @Test
    void ownerCreatesShopAndBookThenReviewerPublishesAndContentEditRequiresReapproval() throws Exception {
        var createdShop = request("POST", "/api/store", owner.token(), Map.of(
                "tenCuaHang", "Shop sách", "diaChiCH", "TP Hồ Chí Minh", "soDienThoaiCH", "0901234567"));
        assertThat(createdShop.statusCode()).isEqualTo(200);
        int shopId = json(createdShop).get("data").get("maCH").asInt();
        assertThat(json(createdShop).get("data").get("trangThaiDuyet").asText()).isEqualTo("CHO_DUYET");
        assertCode(request("POST", "/api/store/me/sach", owner.token(), bookBody("Java", true)), 400);
        assertCode(request("POST", "/api/store/kiem-duyet/cua-hang/" + shopId + "/duyet", reviewer.token(), null), 200);

        var createdBook = request("POST", "/api/store/me/sach", owner.token(), bookBody("Java", true));
        assertCode(createdBook, 200);
        int bookId = json(createdBook).get("data").get("maSach").asInt();
        assertThat(json(createdBook).get("data").get("trangThaiDuyet").asText()).isEqualTo("CHO_DUYET");
        assertCode(request("GET", "/api/sach/" + bookId, null, null), 400);
        assertCode(request("POST", "/api/store/kiem-duyet/sach/" + bookId + "/duyet", reviewer.token(), null), 200);
        assertCode(request("GET", "/api/sach/" + bookId, null, null), 200);

        var edited = request("PUT", "/api/store/me/sach/" + bookId, owner.token(), bookBody("Java nâng cao", false));
        assertCode(edited, 200);
        assertThat(json(edited).get("data").get("trangThaiDuyet").asText()).isEqualTo("CHO_DUYET");
        assertThat(json(edited).get("data").get("soLuongTon").asInt()).isEqualTo(10);
        assertCode(request("GET", "/api/sach/" + bookId, null, null), 400);
        assertThat(nhatKyRepository.count()).isEqualTo(2);
    }

    @Test
    void otherOwnerCannotReadOrMutateBooksAndOwnListIsScoped() throws Exception {
        CuaHang ownShop = store(owner, "DA_DUYET");
        CuaHang foreignShop = store(otherOwner, "DA_DUYET");
        Sach ownBook = book(ownShop, "Sách của tôi", "CHO_DUYET", "DANG_BAN", 4);
        Sach foreignBook = book(foreignShop, "Sách shop khác", "DA_DUYET", "DANG_BAN", 9);
        String foreignPath = "/api/store/me/sach/" + foreignBook.getMaSach();
        assertCode(request("GET", foreignPath, owner.token(), null), 403);
        assertCode(request("PATCH", foreignPath + "/ton-kho", owner.token(), Map.of("soLuongTon", 0)), 403);
        assertCode(request("PUT", foreignPath, owner.token(), bookBody("Đã sửa", false)), 403);
        var listed = json(request("GET", "/api/store/me/sach", owner.token(), null)).get("data");
        assertThat(listed.get("totalElements").asInt()).isEqualTo(1);
        assertThat(listed.get("content").get(0).get("maSach").asInt()).isEqualTo(ownBook.getMaSach());
        assertThat(sachRepository.findById(foreignBook.getMaSach()).orElseThrow().getSoLuongTon()).isEqualTo(9);
    }

    @Test
    void stockAndSaleUpdatesPreserveApprovalAndNegativeStockIsRejected() throws Exception {
        Sach book = book(store(owner, "DA_DUYET"), "Java", "DA_DUYET", "DANG_BAN", 4);
        String path = "/api/store/me/sach/" + book.getMaSach();
        assertCode(request("PATCH", path + "/ton-kho", owner.token(), Map.of("soLuongTon", -1)), 400);
        var zero = request("PATCH", path + "/ton-kho", owner.token(), Map.of("soLuongTon", 0));
        assertCode(zero, 200);
        assertThat(json(zero).get("data").get("trangThaiDuyet").asText()).isEqualTo("DA_DUYET");
        assertCode(request("GET", "/api/sach/" + book.getMaSach(), null, null), 200);
        assertThat(json(request("GET", "/api/sach/tim-kiem?conHang=true", null, null))
                .get("data").get("totalElements").asLong()).isZero();
        assertCode(request("PATCH", path + "/trang-thai-ban", owner.token(), Map.of("trangThaiBan", "NGUNG_BAN")), 200);
        assertCode(request("GET", "/api/sach/" + book.getMaSach(), null, null), 400);
        assertThat(sachRepository.findById(book.getMaSach()).orElseThrow().getTrangThaiDuyet()).isEqualTo("DA_DUYET");
    }

    @Test
    void rejectionReasonAndResubmissionAreAvailableToOwner() throws Exception {
        CuaHang shop = store(owner, "CHO_DUYET");
        String moderationPath = "/api/store/kiem-duyet/cua-hang/" + shop.getMaCH();
        assertCode(request("POST", moderationPath + "/tu-choi", reviewer.token(), Map.of("lyDo", "  ")), 400);
        assertCode(request("POST", moderationPath + "/tu-choi", reviewer.token(), Map.of("lyDo", "Thiếu địa chỉ")), 200);
        var myShop = json(request("GET", "/api/store/me", owner.token(), null)).get("data");
        assertThat(myShop.get("lyDoTuChoi").asText()).isEqualTo("Thiếu địa chỉ");
        assertCode(request("PUT", "/api/store/me", owner.token(), Map.of(
                "tenCuaHang", shop.getTenCuaHang(), "diaChiCH", "Địa chỉ đã bổ sung", "soDienThoaiCH", "0901234567")), 200);
        assertThat(cuaHangRepository.findById(shop.getMaCH()).orElseThrow().getTrangThaiDuyet()).isEqualTo("CHO_DUYET");
        assertThat(nhatKyRepository.findAll().getFirst().getMoTa()).isEqualTo("Thiếu địa chỉ");
    }

    @Test
    void invalidFiltersAndBodyTypesReturn400InsteadOf500() throws Exception {
        assertCode(request("GET", "/api/sach/tim-kiem?page=-1", null, null), 400);
        assertCode(request("GET", "/api/sach/tim-kiem?size=101", null, null), 400);
        assertCode(request("GET", "/api/sach/tim-kiem?maDM=abc", null, null), 400);
        assertCode(request("GET", "/api/sach/tim-kiem?giaTu=100&giaDen=10", null, null), 400);
        assertCode(request("GET", "/api/sach/tim-kiem?sort=chuShop.matKhau", null, null), 400);
        assertCode(request("GET", "/api/store/kiem-duyet/sach?trangThaiDuyet=UNKNOWN", reviewer.token(), null), 400);
        Sach book = book(store(owner, "DA_DUYET"), "Java", "DA_DUYET", "DANG_BAN", 4);
        assertCode(request("PATCH", "/api/store/me/sach/" + book.getMaSach() + "/ton-kho",
                owner.token(), Map.of("soLuongTon", "abc")), 400);
        HttpRequest malformed = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/store"))
                .header("Authorization", "Bearer " + owner.token()).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{")).build();
        assertCode(client.send(malformed, HttpResponse.BodyHandlers.ofString()), 400);
    }

    @Test
    void concurrentModeratorsCanApproveEachBookOnlyOnce() throws Exception {
        CuaHang shop = store(owner, "DA_DUYET");
        Sach book = book(shop, "Java", "CHO_DUYET", "DANG_BAN", 3);
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        var holder = CompletableFuture.runAsync(() -> transactions.executeWithoutResult(status -> {
            cuaHangRepository.findByIdForUpdate(shop.getMaCH()).orElseThrow();
            locked.countDown();
            try {
                if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Lock test timed out");
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(exception);
            }
        }));
        HttpRequest approval = httpRequest("POST", "/api/store/kiem-duyet/sach/" + book.getMaSach() + "/duyet", reviewer.token(), null);
        try {
            assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();
            var first = client.sendAsync(approval, HttpResponse.BodyHandlers.ofString());
            var second = client.sendAsync(approval, HttpResponse.BodyHandlers.ofString());
            assertThatThrownBy(() -> first.get(300, TimeUnit.MILLISECONDS))
                    .isInstanceOf(java.util.concurrent.TimeoutException.class);
            assertThatThrownBy(() -> second.get(300, TimeUnit.MILLISECONDS))
                    .isInstanceOf(java.util.concurrent.TimeoutException.class);
            release.countDown();
            holder.get(10, TimeUnit.SECONDS);
            assertThat(List.of(first.get(10, TimeUnit.SECONDS).statusCode(), second.get(10, TimeUnit.SECONDS).statusCode()))
                    .containsExactlyInAnyOrder(200, 400);
        } finally {
            release.countDown();
            holder.get(10, TimeUnit.SECONDS);
        }
        assertThat(sachRepository.findById(book.getMaSach()).orElseThrow().getTrangThaiDuyet()).isEqualTo("DA_DUYET");
        assertThat(nhatKyRepository.count()).isEqualTo(1);
    }

    @Test
    void stockUpdateWaitsForTransactionLockAndThenUsesCurrentState() throws Exception {
        CuaHang shop = store(owner, "DA_DUYET");
        Sach book = book(shop, "Java", "DA_DUYET", "DANG_BAN", 3);
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CompletableFuture<Void> holder = CompletableFuture.runAsync(() -> transactions.executeWithoutResult(status -> {
            cuaHangRepository.findByIdForUpdate(shop.getMaCH()).orElseThrow();
            Sach current = sachRepository.findByIdForUpdate(book.getMaSach()).orElseThrow();
            current.setSoLuongTon(7);
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
            var update = client.sendAsync(httpRequest("PATCH", "/api/store/me/sach/" + book.getMaSach() + "/ton-kho",
                    owner.token(), Map.of("soLuongTon", 11)), HttpResponse.BodyHandlers.ofString());
            assertThatThrownBy(() -> update.get(300, TimeUnit.MILLISECONDS))
                    .isInstanceOf(java.util.concurrent.TimeoutException.class);
            release.countDown();
            holder.get(10, TimeUnit.SECONDS);
            assertCode(update.get(10, TimeUnit.SECONDS), 200);
            assertThat(sachRepository.findById(book.getMaSach()).orElseThrow().getSoLuongTon()).isEqualTo(11);
        } finally {
            release.countDown();
            holder.get(10, TimeUnit.SECONDS);
        }
    }

    @Test
    void approvalRollsBackWhenAuditCannotBePersisted() {
        Sach book = book(store(owner, "DA_DUYET"), "Java", "CHO_DUYET", "DANG_BAN", 3);
        var authentication = new UsernamePasswordAuthenticationToken(reviewer.maND(), null,
                List.of(new SimpleGrantedAuthority("ROLE_QUAN_LY")));
        authentication.setDetails(new AuthenticatedUserDetails(reviewer.maND(), Integer.MAX_VALUE, "QUAN_LY"));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        try {
            assertThatThrownBy(() -> kiemDuyetService.duyetSach(book.getMaSach())).isInstanceOf(RuntimeException.class);
        } finally {
            SecurityContextHolder.clearContext();
        }
        assertThat(sachRepository.findById(book.getMaSach()).orElseThrow().getTrangThaiDuyet()).isEqualTo("CHO_DUYET");
        assertThat(nhatKyRepository.count()).isZero();
    }

    private Actor actor(String role, String email) {
        NguoiDung user = nguoiDungRepository.save(NguoiDung.builder().email(email).matKhau("test-only-hash")
                .soDienThoai(String.format("09%08d", ++actorSequence)).build());
        TaiKhoan account = taiKhoanRepository.save(TaiKhoan.builder().nguoiDung(user).vaiTro(role).trangThai("HOAT_DONG").build());
        return new Actor(user.getMaND(), account.getMaTK(), jwtService.generateToken(user.getMaND(), account.getMaTK(), role));
    }

    private String inputTag(String html, String id) {
        var input = Pattern.compile("<input\\b[^>]*id=\"" + Pattern.quote(id) + "\"[^>]*>").matcher(html);
        assertThat(input.find()).as("Rendered input " + id).isTrue();
        return input.group();
    }

    private CuaHang store(Actor actor, String approval) {
        return cuaHangRepository.save(CuaHang.builder().chuShop(nguoiDungRepository.getReferenceById(actor.maND()))
                .tenCuaHang("Shop " + actor.maND()).trangThaiDuyet(approval).build());
    }

    private Sach book(CuaHang shop, String name, String approval, String sale, int stock) {
        return sachRepository.save(Sach.builder().cuaHang(shop).danhMuc(category).tenSach(name)
                .giaBanCu(new BigDecimal("75000")).doMoiPercent(new BigDecimal("80"))
                .trangThaiDuyet(approval).trangThaiBan(sale).soLuongTon(stock).build());
    }

    private Map<String, Object> bookBody(String name, boolean includeStock) {
        var fields = new java.util.HashMap<String, Object>();
        fields.put("maDM", category.getMaDM());
        fields.put("tenSach", name);
        fields.put("giaBanCu", 75000);
        fields.put("doMoiPercent", 80);
        if (includeStock) fields.put("soLuongTon", 10);
        return fields;
    }

    private HttpRequest httpRequest(String method, String path, String token, Object body) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).timeout(Duration.ofSeconds(15));
        if (token != null) builder.header("Authorization", "Bearer " + token);
        if (body != null) builder.header("Content-Type", "application/json");
        return builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))).build();
    }

    private HttpResponse<String> request(String method, String path, String token, Object body) throws Exception {
        return client.send(httpRequest(method, path, token, body), HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode json(HttpResponse<String> response) {
        return mapper.readTree(response.body());
    }

    private void assertCode(HttpResponse<String> response, int code) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(code);
        assertThat(json(response).get("code").asInt()).isEqualTo(code);
    }

    private record Actor(Integer maND, Integer maTK, String token) { }
}
