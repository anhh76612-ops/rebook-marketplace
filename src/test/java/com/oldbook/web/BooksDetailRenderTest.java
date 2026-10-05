package com.oldbook.web;

import com.oldbook.entity.catalog.CuaHang;
import com.oldbook.entity.catalog.DanhMuc;
import com.oldbook.entity.catalog.Sach;
import com.oldbook.entity.identity.NguoiDung;
import com.oldbook.entity.order.ChiTietDonHang;
import com.oldbook.entity.order.DonHang;
import com.oldbook.entity.order.DonHangCuaHang;
import com.oldbook.entity.system.DanhGia;
import com.oldbook.service.auth.EmailService;
import com.oldbook.service.catalog.SachService;
import jakarta.persistence.EntityManager;
import jakarta.servlet.ServletContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.config.import=")
@ActiveProfiles({"test", "catalog-test"})
class BooksDetailRenderTest {
    private static final AtomicInteger PHONE_SEQUENCE = new AtomicInteger();

    @Value("${local.server.port}") private int port;
    @Autowired private EntityManager entityManager;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private SachService sachService;
    @Autowired private SpringTemplateEngine templateEngine;
    @Autowired private ServletContext servletContext;
    @MockitoBean private EmailService emailService;

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @Test
    void detailRendersRealReviewsAndRelatedBooksWithoutInjectingTheirContent() throws Exception {
        var fixture = new TransactionTemplate(transactionManager).execute(status -> {
            var customer = user("Khách <script>alert(1)</script>");
            var store = store(customer);
            var category = category();
            var book = book(store, category, "Sách <em>kiểm thử</em>", "https://example.test/book.jpg");
            var related = book(store, category, "Sách liên quan", "/images/related-book.jpg");
            var unsafeRelated = book(store, category, "Sách không có ảnh an toàn", "//example.test/unsafe.jpg");
            var hidden = book(store, category, "Sách chưa duyệt không được gợi ý", null);
            hidden.setTrangThaiDuyet("CHO_DUYET");
            book(store, category(), "Sách khác danh mục không được gợi ý", null);
            var order = persist(DonHang.builder().nguoiMua(customer).build());
            var shopOrder = persist(DonHangCuaHang.builder().donHang(order).cuaHang(store).build());
            var item = persist(ChiTietDonHang.builder().donHangCuaHang(shopOrder).sach(book).soLuong(1).build());
            persist(DanhGia.builder().chiTietDonHang(item).nguoiDung(customer).soSao(4)
                    .binhLuan("Nội dung <script>alert(2)</script> & đóng gói tốt").build());
            entityManager.flush();
            return new Fixture(book.getMaSach(), related.getMaSach(), unsafeRelated.getMaSach());
        });

        var response = detail(fixture.bookId());
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        assertThat(response.body()).contains(
                        "Đánh giá từ khách hàng", "Sách cùng danh mục", "Yêu thích",
                        "Sách &lt;em&gt;kiểm thử&lt;/em&gt;", "Khách &lt;script&gt;alert(1)&lt;/script&gt;",
                        "Nội dung &lt;script&gt;alert(2)&lt;/script&gt; &amp; đóng gói tốt",
                        "src=\"https://example.test/book.jpg\"", "src=\"/images/related-book.jpg\"",
                        "href=\"/books/" + fixture.relatedId() + "\"",
                        "href=\"/books/" + fixture.unsafeRelatedId() + "\"",
                        "data-add-to-cart", "data-toggle-wishlist", "aria-pressed=\"false\"",
                        "aria-label=\"4 trên 5 sao\"", "50.000 đ", "Mua ngay", "data-buy-now")
                .doesNotContain("<script>alert(1)</script>", "<script>alert(2)</script>",
                        "src=\"//example.test/unsafe.jpg\"", "Sách chưa duyệt không được gợi ý",
                        "Sách khác danh mục không được gợi ý", "th:replace", "th:each");
        assertCompleteDetail(response.body());
        assertThat(buyNowButton(response.body())).doesNotContain("disabled");
    }

    @Test
    void detailHandlesEmptySectionsAndUnavailablePriceAndStock() throws Exception {
        Integer bookId = new TransactionTemplate(transactionManager).execute(status -> {
            var book = book(store(user("Chủ cửa hàng")), category(), "Sách hết hàng", "javascript:alert(3)");
            book.setGiaBanCu(null);
            book.setGiaBia(null);
            book.setSoLuongTon(0);
            book.setDoMoiPercent(null);
            entityManager.flush();
            return book.getMaSach();
        });

        var response = detail(bookId);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        assertThat(response.body()).contains("Đánh giá từ khách hàng", "Hết hàng", "Yêu thích",
                        "Chưa có đánh giá nào cho sách này.", "Chưa có sách khác trong danh mục này.")
                .doesNotContain("src=\"javascript:alert(3)\"", "th:replace", "th:each");
        assertCompleteDetail(response.body());
        assertThat(buyNowButton(response.body())).contains("disabled");
    }

    @Test
    void templateStillRendersToTheFooterWhenReviewAndRelatedModelsAreMissingOrNull() {
        var book = new TransactionTemplate(transactionManager).execute(status -> {
            var entity = book(store(user("Chủ cửa hàng")), category(), "Sách kiểm tra model", null);
            entityManager.flush();
            return sachService.getDetail(entity.getMaSach());
        });

        for (boolean explicitNullLists : new boolean[]{false, true}) {
            var model = new HashMap<String, Object>();
            model.put("book", book);
            if (explicitNullLists) {
                model.put("reviews", null);
                model.put("related", null);
            }
            var request = new MockHttpServletRequest(servletContext);
            request.setRequestURI("/books/" + book.maSach());
            var exchange = JakartaServletWebApplication.buildApplication(servletContext)
                    .buildExchange(request, new MockHttpServletResponse());
            var context = new WebContext(exchange, Locale.forLanguageTag("vi"), model);

            String html = templateEngine.process("books/detail", context);

            assertThat(html).contains("50.000 đ", "Mua ngay", "Yêu thích", "data-buy-now",
                            "Chưa có đánh giá nào cho sách này.", "Chưa có sách khác trong danh mục này.")
                    .doesNotContain("th:replace", "th:each");
            assertCompleteDetail(html);
        }
    }

    @Test
    void missingAndHiddenBooksStillRenderNotFound() throws Exception {
        Integer hiddenId = new TransactionTemplate(transactionManager).execute(status -> {
            var book = book(store(user("Chủ cửa hàng")), category(), "Sách chờ duyệt", null);
            book.setTrangThaiDuyet("CHO_DUYET");
            entityManager.flush();
            return book.getMaSach();
        });
        for (int bookId : new int[]{Integer.MAX_VALUE, hiddenId}) {
            var response = detail(bookId);
            assertThat(response.statusCode()).as(response.body()).isEqualTo(404);
            assertThat(response.body()).contains("Không tìm thấy sách")
                    .doesNotContain("data-add-to-cart", "th:replace");
        }
    }

    private HttpResponse<String> detail(int bookId) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/books/" + bookId))
                .timeout(Duration.ofSeconds(15)).GET().build();
        return client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private void assertCompleteDetail(String html) {
        assertThat(html).contains("Giá bán:", "Giới thiệu sách", "Đánh giá từ khách hàng",
                "Sách cùng danh mục", "Tất cả quyền được bảo lưu.", "/js/cart.js");
        assertThat(html.stripTrailing()).endsWith("</html>");
    }

    private String buyNowButton(String html) {
        var matcher = java.util.regex.Pattern.compile("<button\\b[^>]*\\bid=\"buyNowButton\"[^>]*>")
                .matcher(html);
        assertThat(matcher.find()).as("Buy now button exists").isTrue();
        return matcher.group();
    }

    private NguoiDung user(String name) {
        return persist(NguoiDung.builder().email("detail-" + UUID.randomUUID() + "@test.local")
                .matKhau("test-only-hash").hoTen(name)
                .soDienThoai(String.format("08%08d", PHONE_SEQUENCE.incrementAndGet())).build());
    }

    private CuaHang store(NguoiDung owner) {
        return persist(CuaHang.builder().chuShop(owner).tenCuaHang("Cửa hàng kiểm thử " + owner.getMaND())
                .trangThaiDuyet("DA_DUYET").build());
    }

    private DanhMuc category() {
        return persist(DanhMuc.builder().tenDanhMuc("Văn học " + UUID.randomUUID()).build());
    }

    private Sach book(CuaHang store, DanhMuc category, String title, String image) {
        return persist(Sach.builder().cuaHang(store).danhMuc(category).tenSach(title)
                .giaBanCu(new BigDecimal("50000")).giaBia(new BigDecimal("100000"))
                .doMoiPercent(new BigDecimal("90")).soLuongTon(2).hinhAnhUrl(image)
                .trangThaiDuyet("DA_DUYET").trangThaiBan("DANG_BAN").build());
    }

    private <T> T persist(T entity) {
        entityManager.persist(entity);
        return entity;
    }

    private record Fixture(int bookId, int relatedId, int unsafeRelatedId) {}
}
