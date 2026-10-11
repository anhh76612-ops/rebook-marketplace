package com.oldbook.repository.notification;

import com.oldbook.entity.identity.TaiKhoan;
import com.oldbook.entity.notification.ThongBao;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ThongBaoRepository extends JpaRepository<ThongBao, Integer> {

    Page<ThongBao> findByTaiKhoanNhan_MaTKOrderByNgayTaoDescMaThongBaoDesc(
            Integer maTK,
            Pageable pageable
    );

    long countByTaiKhoanNhan_MaTKAndDaDocFalse(Integer maTK);

    // Tìm kèm mã tài khoản nhận để không ai đọc hay sửa được thông báo của người khác.
    Optional<ThongBao> findByMaThongBaoAndTaiKhoanNhan_MaTK(
            Integer maThongBao,
            Integer maTK
    );

    @Modifying
    @Query("""
            update ThongBao t
            set t.daDoc = true, t.ngayDoc = :luc
            where t.taiKhoanNhan.maTK = :maTK
              and t.daDoc = false
            """)
    int markAllRead(
            @Param("maTK") Integer maTK,
            @Param("luc") LocalDateTime luc
    );

    // Danh sách người nhận theo vai trò. Đặt ở đây để không phải sửa TaiKhoanRepository của module identity.
    @Query("""
            select t
            from TaiKhoan t
            where t.vaiTro = :vaiTro
              and t.trangThai = :trangThai
            """)
    List<TaiKhoan> findAccountsByRoleAndStatus(
            @Param("vaiTro") String vaiTro,
            @Param("trangThai") String trangThai
    );
}
