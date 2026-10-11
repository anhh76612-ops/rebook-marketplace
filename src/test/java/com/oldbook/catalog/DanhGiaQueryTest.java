package com.oldbook.catalog;

import com.oldbook.entity.catalog.CuaHang;
import com.oldbook.entity.catalog.DanhMuc;
import com.oldbook.entity.catalog.Sach;
import com.oldbook.entity.identity.NguoiDung;
import com.oldbook.entity.order.ChiTietDonHang;
import com.oldbook.entity.order.DonHang;
import com.oldbook.entity.order.DonHangCuaHang;
import com.oldbook.entity.system.DanhGia;
import com.oldbook.service.catalog.DanhGiaService;
import com.oldbook.service.catalog.ReviewMediaUploadService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(showSql = false, properties = "spring.config.import=")
@Import(DanhGiaService.class)
@ActiveProfiles({"test", "catalog-test"})
class DanhGiaQueryTest {
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private DanhGiaService danhGiaService;
    @MockitoBean
    private ReviewMediaUploadService reviewMediaUploadService;

    private NguoiDung customer;
    private CuaHang store;
    private DanhMuc category;
    private DonHangCuaHang storeOrder;

    @BeforeEach
    void setUp() {
        customer = NguoiDung.builder().email("reviews@test.local").matKhau("test-hash")
                .hoTen("Người mua").build();
        entityManager.persist(customer);
        store = CuaHang.builder().chuShop(customer).tenCuaHang("Review shop")
                .trangThaiDuyet("DA_DUYET").build();
        entityManager.persist(store);
        category = DanhMuc.builder().tenDanhMuc("Reviews category").build();
        entityManager.persist(category);
        var order = DonHang.builder().nguoiMua(customer).build();
        entityManager.persist(order);
        storeOrder = DonHangCuaHang.builder().donHang(order).cuaHang(store).build();
        entityManager.persist(storeOrder);
    }

    @Test
    void reviewsBelongToRequestedBookHaveValidStarsAndNewestFirst() {
        var current = book("Current");
        var first = review(current, 4, "First review");
        var latest = review(current, 5, "Latest review");
        review(current, 0, "Invalid rating");
        review(current, 6, "Invalid rating");
        review(current, null, "Missing rating");
        review(book("Other"), 5, "Other book");
        entityManager.flush();
        entityManager.clear();

        var reviews = danhGiaService.getByBook(current.getMaSach());

        assertThat(reviews).extracting(response -> response.maDanhGia())
                .containsExactly(latest.getMaDanhGia(), first.getMaDanhGia());
        assertThat(reviews.getFirst().hoTen()).isEqualTo("Người mua");
        assertThat(reviews.getFirst().soSao()).isEqualTo(5);
        assertThat(reviews.getFirst().binhLuan()).isEqualTo("Latest review");
        assertThat(reviews.getFirst().ngayDanhGia()).isNotNull();
        assertThat(danhGiaService.getByBook(Integer.MAX_VALUE)).isEmpty();
    }

    @Test
    void unnamedCustomerUsesDisplayFallback() {
        customer.setHoTen(" ");
        var current = book("Current");
        review(current, 5, "Review");
        entityManager.flush();
        entityManager.clear();

        assertThat(danhGiaService.getByBook(current.getMaSach()).getFirst().hoTen())
                .isEqualTo("Khách hàng");
    }

    private Sach book(String title) {
        var book = Sach.builder().cuaHang(store).danhMuc(category).tenSach(title)
                .trangThaiDuyet("DA_DUYET").trangThaiBan("DANG_BAN").build();
        entityManager.persist(book);
        return book;
    }

    private DanhGia review(Sach book, Integer stars, String comment) {
        var line = ChiTietDonHang.builder().donHangCuaHang(storeOrder).sach(book).soLuong(1).build();
        entityManager.persist(line);
        var review = DanhGia.builder().chiTietDonHang(line).nguoiDung(customer)
                .soSao(stars).binhLuan(comment).build();
        entityManager.persist(review);
        return review;
    }
}
