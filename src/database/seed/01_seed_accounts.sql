-- =========================================================
-- OLD BOOK MARKETPLACE
-- Seed tài khoản phát triển
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

CREATE EXTENSION IF NOT EXISTS pgcrypto;


-- =========================================================
-- 1. TẠO NGƯỜI DÙNG ADMIN
-- =========================================================

INSERT INTO nguoi_dung (
    email,
    mat_khau,
    ho_ten,
    so_dien_thoai,
    ngay_tao
)
VALUES (
           'admin@oldbook.local',
           crypt('Admin@12345', gen_salt('bf', 10)),
           'Quản trị viên hệ thống',
           '0900000001',
           CURRENT_TIMESTAMP
       )
    ON CONFLICT (email) DO NOTHING;


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
    CURRENT_TIMESTAMP
FROM nguoi_dung nd
WHERE nd.email = 'admin@oldbook.local'
    ON CONFLICT (ma_nd, vai_tro) DO NOTHING;


-- =========================================================
-- 3. TẠO NGƯỜI DÙNG MANAGER
-- =========================================================

INSERT INTO nguoi_dung (
    email,
    mat_khau,
    ho_ten,
    so_dien_thoai,
    ngay_tao
)
VALUES (
           'manager@oldbook.local',
           crypt('Manager@12345', gen_salt('bf', 10)),
           'Quản lý cửa hàng',
           '0900000002',
           CURRENT_TIMESTAMP
       )
    ON CONFLICT (email) DO NOTHING;


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
    CURRENT_TIMESTAMP
FROM nguoi_dung nd
WHERE nd.email = 'manager@oldbook.local'
    ON CONFLICT (ma_nd, vai_tro) DO NOTHING;


-- =========================================================
-- 5. KIỂM TRA
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
                   'admin@oldbook.local',
                   'manager@oldbook.local'
    )
ORDER BY tk.ma_tk;