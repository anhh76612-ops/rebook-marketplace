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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.config.import=",
                "spring.datasource.url=jdbc:h2:mem:rating_filter;MODE=MSSQLServer;DB_CLOSE_DELAY=-1"
        })
@ActiveProfiles({"test", "catalog-test"})
class SachRatingFilterIntegrationTest {
    private static final AtomicInteger PHONE_SEQUENCE = new AtomicInteger();
    @Value("${local.server.port}") private int port;
    @Autowired private EntityManager entityManager;
    @Autowired private TransactionTemplate transactions;
    @Autowired private ObjectMapper mapper;
    @Autowired private JwtService jwtService;
    @MockitoBean private EmailService emailService;

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private NguoiDung customer;
    private CuaHang store;
    private DanhMuc category;
    private String token;

    @BeforeEach
    void setUp() {
        transactions.executeWithoutResult(status -> {
            customer = user();
            var account = persist(TaiKhoan.builder().nguoiDung(customer)
                    .vaiTro("KHACH_HANG").trangThai("HOAT_DONG").build());
            token = jwtService.generateToken(customer.getMaND(), account.getMaTK(), "KHACH_HANG");
            store = store("DA_DUYET");
            category = persist(DanhMuc.builder().tenDanhMuc("Rating " + UUID.randomUUID()).build());
        });
    }

    @Test
    void thresholdsUseAverageOfValidReviewsRatherThanAnyIndividualRating() throws Exception {
        var books = transactions.execute(status -> List.of(
                rated("Five", 5, 5),
                rated("Four", 3, 5),
                rated("Below four", 3, 4, 4),
                rated("Mixed", 1, 5),
                rated("Legacy", 4, 4, 0, 6, null),
                rated("Invalid only", 0, 6, null),
                rated("Unrated")));

        assertThat(ids(search("soSaoTu=5"))).containsExactly(books.get(0).getMaSach());
        assertThat(ids(search("soSaoTu=4"))).containsExactlyInAnyOrder(
                books.get(0).getMaSach(), books.get(1).getMaSach(), books.get(4).getMaSach());
        assertThat(ids(search("soSaoTu=3"))).containsExactlyInAnyOrder(
                books.get(0).getMaSach(), books.get(1).getMaSach(), books.get(2).getMaSach(),
                books.get(3).getMaSach(), books.get(4).getMaSach());
        for (int threshold : List.of(1, 2)) {
            assertThat(ids(search("soSaoTu=" + threshold))).hasSize(5)
                    .doesNotContain(books.get(5).getMaSach(), books.get(6).getMaSach());
        }
        assertThat(ids(search(""))).hasSize(7);
        assertThat(ids(search("soSaoTu="))).hasSize(7);
    }

    @Test
    void ratingCombinesWithOtherFiltersAndPublicVisibility() throws Exception {
        Integer matchingId = transactions.execute(status -> {
            var matching = rated("target matching", 5, 4);
            rated("target low rating", 3, 4);
            rated("target wrong price", 5).setGiaBanCu(new BigDecimal("80000"));
            rated("target wrong condition", 5).setDoMoiPercent(new BigDecimal("50"));
            rated("target sold out", 5).setSoLuongTon(0);
            rated("different keyword", 5);
            var wrongCategory = rated("target wrong category", 5);
            wrongCategory.setDanhMuc(persist(DanhMuc.builder().tenDanhMuc("Other " + UUID.randomUUID()).build()));
            rated("target hidden book", 5).setTrangThaiDuyet("CHO_DUYET");
            rated("target paused book", 5).setTrangThaiBan("NGUNG_BAN");
            rated("target hidden store", 5).setCuaHang(store("CHO_DUYET"));
            rated("target other store", 5).setCuaHang(store("DA_DUYET"));
            return matching.getMaSach();
        });

        JsonNode result = search("soSaoTu=4&tuKhoa=target&maDM=" + category.getMaDM()
                + "&giaTu=60000&giaDen=60000&doMoiTu=95&doMoiDen=95"
                + "&tinhTrang=NHU_MOI&khoangGia=TU_50000_DEN_150000&conHang=true");
        assertThat(ids(result)).containsExactly(matchingId);
        assertThat(result.get("totalElements").asLong()).isEqualTo(1);
    }

    @Test
    void paginationCountsBooksOnceAndPreservesStableSorting() throws Exception {
        List<Integer> expected = transactions.execute(status -> {
            var first = rated("First", 4, 5, 4, 5, 4, 5);
            var second = rated("Second", 4);
            var third = rated("Third", 5);
            rated("Low rating", 2);
            rated("Unrated");
            return List.of(first.getMaSach(), second.getMaSach(), third.getMaSach());
        });
        for (int page = 0; page < expected.size(); page++) {
            JsonNode result = search("soSaoTu=4&sort=gia-tang&size=1&page=" + page);
            assertThat(ids(result)).containsExactly(expected.get(page));
            assertThat(result.get("totalElements").asLong()).isEqualTo(3);
            assertThat(result.get("totalPages").asInt()).isEqualTo(3);
            assertThat(result.get("page").asInt()).isEqualTo(page);
        }
    }

    @Test
    void invalidRatingThresholdsReturn400OnPublicApiAndSearchPage() throws Exception {
        for (String invalid : List.of("0", "-1", "6", "4.5", "abc", "2147483648")) {
            for (String route : List.of("/api/sach/tim-kiem", "/books/tim-kiem")) {
                var response = get(route + "?soSaoTu=" + invalid);
                assertThat(response.statusCode()).as(response.body()).isEqualTo(400);
                assertThat(mapper.readTree(response.body()).get("code").asInt()).isEqualTo(400);
            }
        }
    }

    @Test
    void searchFormRetainsThresholdInSelectionAndPaginationAndResetClearsIt() throws Exception {
        transactions.executeWithoutResult(status -> {
            rated("target first", 5).setGiaBanCu(new BigDecimal("40000"));
            rated("target second", 4).setGiaBanCu(new BigDecimal("40000"));
            rated("target third", 4, 5).setGiaBanCu(new BigDecimal("40000"));
        });
        String query = "?soSaoTu=4&tuKhoa=target&maCH=" + store.getMaCH()
                + "&maDM=" + category.getMaDM()
                + "&khoangGia=DUOI_50000&conHang=true&sort=gia-tang&size=1&page=1";
        var response = get("/books/tim-kiem" + query);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        String html = response.body();
        assertThat(html).contains("Đánh giá trung bình", "ratingFilterHelp");
        int field = html.indexOf("id=\"soSaoTu\"");
        assertThat(field).isPositive();
        String select = html.substring(html.lastIndexOf("<select", field), html.indexOf("</select>", field));
        assertThat(select).contains("name=\"soSaoTu\"", "Tất cả đánh giá", "5 sao", "4 sao trở lên",
                "3 sao trở lên", "2 sao trở lên", "1 sao trở lên");
        assertThat(Pattern.compile("<option[^>]*value=\"4\"[^>]*selected[^>]*>").matcher(select).find()).isTrue();
        var anchors = Pattern.compile("<a\\b[^>]*>").matcher(html);
        var paginationLinks = new ArrayList<String>();
        String reset = null;
        while (anchors.find()) {
            String anchor = anchors.group().replace("&amp;", "&");
            if (anchor.contains("class=\"page-link\"")) paginationLinks.add(anchor);
            if (anchor.contains("class=\"btn-reset")) reset = anchor;
        }
        assertThat(paginationLinks).hasSize(2).allSatisfy(anchor ->
                assertThat(anchor).contains("soSaoTu=4", "tuKhoa=target", "maCH=" + store.getMaCH(),
                        "maDM=" + category.getMaDM(), "khoangGia=DUOI_50000", "conHang=true", "sort=gia-tang", "size=1"));
        assertThat(paginationLinks.getFirst()).contains("page=0");
        assertThat(paginationLinks.getLast()).contains("page=2");
        assertThat(reset).contains("href=\"/books/tim-kiem\"").doesNotContain("soSaoTu");
        assertThat(html).doesNotContain("th:field", "th:each", "th:replace");
        assertThat(get("/books").body()).contains("soSaoTu=5", "soSaoTu=4", "soSaoTu=3", "Lọc sách theo đánh giá");
    }

    @Test
    void filterImmediatelyReflectsNewlySubmittedCustomerReviews() throws Exception {
        Sach book = transactions.execute(status -> rated("New reviews"));
        assertThat(ids(search("soSaoTu=4"))).isEmpty();
        submit(book, 5);
        assertThat(ids(search("soSaoTu=5"))).containsExactly(book.getMaSach());
        submit(book, 1);
        assertThat(ids(search("soSaoTu=4"))).isEmpty();
        assertThat(ids(search("soSaoTu=3"))).containsExactly(book.getMaSach());
    }

    private void submit(Sach book, int stars) throws Exception {
        Integer lineId = transactions.execute(status -> line(book).getMaCTDH());
        var request = HttpRequest.newBuilder(URI.create(base() + "/api/reviews/order-items/" + lineId))
                .timeout(Duration.ofSeconds(15)).header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(Map.of(
                        "soSao", stars, "binhLuan", "Verified customer review")))).build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
    }

    private Sach rated(String title, Integer... stars) {
        var book = persist(Sach.builder().cuaHang(store).danhMuc(category).tenSach(title)
                .giaBanCu(new BigDecimal("60000")).doMoiPercent(new BigDecimal("95"))
                .soLuongTon(5).trangThaiDuyet("DA_DUYET").trangThaiBan("DANG_BAN").build());
        for (Integer rating : stars) {
            persist(DanhGia.builder().chiTietDonHang(line(book)).nguoiDung(customer)
                    .soSao(rating).binhLuan("Purchased book review").build());
        }
        return book;
    }

    private ChiTietDonHang line(Sach book) {
        var parent = persist(DonHang.builder().nguoiMua(customer).build());
        var child = persist(DonHangCuaHang.builder().donHang(parent).cuaHang(store)
                .trangThaiDonHang("DA_GIAO").build());
        return persist(ChiTietDonHang.builder().donHangCuaHang(child)
                .sach(entityManager.getReference(Sach.class, book.getMaSach())).soLuong(1).build());
    }

    private NguoiDung user() {
        return persist(NguoiDung.builder().email("rating-" + UUID.randomUUID() + "@test.local")
                .matKhau("test-only-hash").hoTen("Rating filter customer")
                .soDienThoai(String.format("03%08d", PHONE_SEQUENCE.incrementAndGet())).build());
    }

    private CuaHang store(String state) {
        return persist(CuaHang.builder().chuShop(user()).tenCuaHang("Rating shop " + UUID.randomUUID())
                .trangThaiDuyet(state).build());
    }

    private <T> T persist(T entity) {
        entityManager.persist(entity);
        return entity;
    }

    private String base() { return "http://localhost:" + port; }

    private HttpResponse<String> get(String path) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(base() + path)).timeout(Duration.ofSeconds(15)).GET().build();
        return client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private JsonNode search(String query) throws Exception {
        var response = get("/api/sach/tim-kiem?maCH=" + store.getMaCH()
                + (query.isEmpty() ? "" : "&" + query));
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        return mapper.readTree(response.body()).get("data");
    }

    private List<Integer> ids(JsonNode page) {
        var result = new ArrayList<Integer>();
        for (JsonNode book : page.get("content")) result.add(book.get("maSach").asInt());
        return result;
    }
}
