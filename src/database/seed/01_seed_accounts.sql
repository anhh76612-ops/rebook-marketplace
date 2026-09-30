-- =========================================================
-- OLD BOOK MARKETPLACE
-- Seed tài khoản phát triển (Dành cho SQL Server)
--
-- Admin:
--   email: admin@oldbook.local
--   password: Admin@12345
--
-- Manager:
--   email: manager@oldbook.local
--   password: Manager@12345
--
-- CHỈ DÙNG CHO MÔI TRƯỜNG PHÁT TRIỂN / TEST
-- =========================================================

-- =========================================================
-- 1. TẠO NGƯỜI DÙNG ADMIN
-- =========================================================
IF NOT EXISTS (
    SELECT 1
    FROM nguoi_dung
    WHERE email = N'admin@oldbook.local'
)
BEGIN
INSERT INTO nguoi_dung (
    email,
    mat_khau,
    ho_ten,
    so_dien_thoai,
    ngay_tao
)
VALUES (
           N'admin@oldbook.local',
           -- Mật khẩu gốc: Admin@12345
           N'$2a$10$ThayBangMaHashBcryptTuJavaCuaBan', -- Vui lòng thay bằng mã sinh ra từ Java!
           N'Quản trị viên hệ thống',
           N'0900000001',
           SYSDATETIME()
       );
END
GO

-- =========================================================
-- 2. TẠO TÀI KHOẢN ADMIN
-- =========================================================
INSERT INTO tai_khoan (
    ma_nd,
    vai_tro,
    trang_thai,
    ngay_tao
)
SELECT
    nd.ma_nd,
    'QUAN_TRI_VIEN',
    'HOAT_DONG',
    SYSDATETIME()
FROM nguoi_dung nd
WHERE nd.email = N'admin@oldbook.local'
  AND NOT EXISTS (
    SELECT 1
    FROM tai_khoan tk
    WHERE tk.ma_nd = nd.ma_nd AND tk.vai_tro = 'QUAN_TRI_VIEN'
);
GO

-- =========================================================
-- 3. TẠO NGƯỜI DÙNG MANAGER
-- =========================================================
IF NOT EXISTS (
    SELECT 1
    FROM nguoi_dung
    WHERE email = N'manager@oldbook.local'
)
BEGIN
INSERT INTO nguoi_dung (
    email,
    mat_khau,
    ho_ten,
    so_dien_thoai,
    ngay_tao
)
VALUES (
           N'manager@oldbook.local',
           -- Mật khẩu gốc: Manager@12345
           N'$2a$10$ThayBangMaHashBcryptTuJavaCuaBan', -- Vui lòng thay bằng mã sinh ra từ Java!
           N'Quản lý cửa hàng',
           N'0900000002',
           SYSDATETIME()
       );
END
GO

-- =========================================================
-- 4. TẠO TÀI KHOẢN MANAGER
-- =========================================================
INSERT INTO tai_khoan (
    ma_nd,
    vai_tro,
    trang_thai,
    ngay_tao
)
SELECT
    nd.ma_nd,
    'QUAN_LY',
    'HOAT_DONG',
    SYSDATETIME()
FROM nguoi_dung nd
WHERE nd.email = N'manager@oldbook.local'
  AND NOT EXISTS (
    SELECT 1
    FROM tai_khoan tk
    WHERE tk.ma_nd = nd.ma_nd AND tk.vai_tro = 'QUAN_LY'
);
GO

-- =========================================================
-- 5. KIỂM TRA DỮ LIỆU SEED
-- =========================================================
SELECT
    nd.ma_nd,
    nd.email,
    nd.ho_ten,
    tk.ma_tk,
    tk.vai_tro,
    tk.trang_thai
FROM nguoi_dung nd
         JOIN tai_khoan tk
              ON tk.ma_nd = nd.ma_nd
WHERE nd.email IN (
                   N'admin@oldbook.local',
                   N'manager@oldbook.local'
    )
ORDER BY tk.ma_tk;
GO