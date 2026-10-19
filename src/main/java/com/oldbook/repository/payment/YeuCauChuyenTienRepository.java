package com.oldbook.repository.payment;

import com.oldbook.entity.payment.YeuCauChuyenTien;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface YeuCauChuyenTienRepository extends JpaRepository<YeuCauChuyenTien, Integer> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select y from YeuCauChuyenTien y where y.maYeuCau = :maYeuCau")
    Optional<YeuCauChuyenTien> findByIdForUpdate(@Param("maYeuCau") Integer maYeuCau);

    // Mỗi đơn hàng con chỉ có một khoản hoàn và một khoản chi.
    boolean existsByLoaiAndDonHangCuaHang_MaDHCH(String loai, Integer maDHCH);

    boolean existsByGiaoDichNganHang_MaGiaoDich(Integer maGiaoDich);

    List<YeuCauChuyenTien> findAllByLoaiAndNguoiNhan_MaNDOrderByMaYeuCauDesc(String loai, Integer maND);

    @Query("""
            select y from YeuCauChuyenTien y
            where y.loai = :loai and (:trangThai is null or y.trangThai = :trangThai)
            order by y.maYeuCau desc
            """)
    Page<YeuCauChuyenTien> timTheoLoai(
            @Param("loai") String loai, @Param("trangThai") String trangThai, Pageable pageable);

    /** Như timTheoLoai, thêm lọc từ khóa: mã đơn, tên / SĐT / email người nhận. tuKhoa rỗng thì không lọc. */
    @Query("""
            select y from YeuCauChuyenTien y
              join y.nguoiNhan n
              left join y.donHangCuaHang d
              left join d.donHang dh
            where y.loai = :loai and (:trangThai is null or y.trangThai = :trangThai)
              and (:tuKhoa = ''
                   or lower(n.hoTen) like :mau or lower(n.email) like :mau or coalesce(n.soDienThoai, '') like :mau
                   or cast(d.maDHCH as string) = :tuKhoa or cast(dh.maDH as string) = :tuKhoa)
            order by y.maYeuCau desc
            """)
    Page<YeuCauChuyenTien> timTheoLoaiVaTuKhoa(
            @Param("loai") String loai, @Param("trangThai") String trangThai,
            @Param("tuKhoa") String tuKhoa, @Param("mau") String mau, Pageable pageable);

    /** Mọi khoản chi cho cửa hàng ở một trạng thái, kèm đơn con và cửa hàng để gom nhóm. */
    @Query("""
            select y from YeuCauChuyenTien y
              join fetch y.donHangCuaHang d
              join fetch d.cuaHang c
              join fetch c.chuShop
              join fetch d.donHang
            where y.loai = :loai and y.trangThai = :trangThai
            order by c.maCH, d.ngayGiao, y.maYeuCau
            """)
    List<YeuCauChuyenTien> timChiTheoTrangThai(@Param("loai") String loai, @Param("trangThai") String trangThai);

    @Query("""
            select y from YeuCauChuyenTien y
              left join fetch y.donHangCuaHang d
              left join fetch d.cuaHang
            where y.maYeuCau in :ids
            order by y.maYeuCau
            """)
    List<YeuCauChuyenTien> timChiTheoMa(@Param("ids") List<Integer> ids);
}
