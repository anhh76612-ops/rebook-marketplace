'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const { boot, settle } = require('./dom-harness.cjs');

// Ba trang của cổng Quản lý: Tổng quan, Thống kê, Hoa hồng.

const shown = (app, id) => !app.get(id).classList.contains('d-none');
const pageCalls = app => app.calls.filter(call => call.url !== '/api/profile');

// ---------------------------------------------------------------------------
// Tổng quan
// ---------------------------------------------------------------------------

const OVERVIEW = {
  choDuyetCuaHang: 2, choDuyetSach: 3, hoanTienCho: 4, hoanTienChoKhachKhai: 1, cuaHangChoChi: 2, khoanChoChi: 5,
  tongTienChoChi: 475000, hoaHongThangNay: 25000, giaoDichLech: 1,
  hangDoi: [{ loai: 'GIAO_DICH_LECH', tieuDe: 'Giao dịch ngân hàng bị lệch', moTa: 'Giao dịch #1 · <b>50.000 đ</b>', duongDan: '/management/payments?tab=transactions', nut: 'Xem giao dịch', thoiGian: '2026-10-12T15:36:21' }],
  dongTien: { khachDaTra: 500000, daHoan: 100000, daChiCuaHang: 200000, hoaHong: 25000 },
  vanChuyen: [{ tenDonVi: 'GHN', tongDon: 10, giaoThanhCong: 9, hoanTra: 1 }]
};

test('overview shows five clickable KPIs, the queue as escaped text and the carrier success rate', async () => {
  const app = boot('management/overview.html', ['management/overview.js'], async () => OVERVIEW, 'QUAN_LY', { pathname: '/management' });
  await settle();

  assert.equal(pageCalls(app)[0].url, '/api/management/overview');
  const kpis = app.get('ovKpis').querySelectorAll('a.mg-kpi');
  assert.equal(kpis.length, 5);
  assert.deepEqual(kpis.map(link => link.getAttribute('href')), [
    '/store-moderation', '/management/payments?tab=refunds', '/management/payments?tab=payouts',
    '/management/commissions', '/management/payments?tab=transactions'
  ]);
  assert.ok(kpis.every(link => link.hasAttribute('data-shell-link')));
  assert.match(kpis[0].textContent, /5/);
  assert.match(kpis[0].textContent, /2 cửa hàng · 3 sách/);
  assert.match(kpis[2].textContent, /475[.,]000/);
  assert.ok(kpis[4].classList.contains('is-alert'));

  const row = app.get('ovQueue').querySelector('li');
  assert.equal(row.querySelector('b'), null, 'mô tả việc không được thành HTML');
  assert.equal(row.querySelector('a').getAttribute('href'), '/management/payments?tab=transactions');
  assert.match(app.get('ovCarriers').textContent, /GHN/);
  assert.match(app.get('ovCarriers').textContent, /90% thành công/);
});

test('overview is denied to other roles without calling the API, and offers a retry on errors', async () => {
  const denied = boot('management/overview.html', ['management/overview.js'], async () => OVERVIEW, 'KHACH_HANG');
  await settle();
  assert.ok(shown(denied, 'ovDenied'));
  assert.deepEqual(pageCalls(denied), []);

  let fail = true;
  const app = boot('management/overview.html', ['management/overview.js'], async () => (fail ? { httpStatus: 500, message: 'Lỗi máy chủ' } : OVERVIEW), 'QUAN_LY');
  await settle();
  assert.ok(shown(app, 'ovError'));
  assert.equal(app.get('ovErrorText').textContent, 'Lỗi máy chủ');
  fail = false;
  app.trigger('ovRetry');
  await settle();
  assert.ok(!shown(app, 'ovError'));
  assert.equal(app.get('ovKpis').querySelectorAll('a.mg-kpi').length, 5);
});

// ---------------------------------------------------------------------------
// Thống kê
// ---------------------------------------------------------------------------

const STATS = {
  tuNgay: '2026-09-21', denNgay: '2026-10-20',
  dongTien: { khachDaTra: 500000, daHoan: 100000, daChiCuaHang: 190000, hoaHong: 10000, phiShipDonDaGiao: 25000, sanDangGiu: 175000, giaoDichLech: 2 },
  cuaHang: [{ maCH: 3, tenCuaHang: '<i>Tiệm A</i>', doanhThu: 200000, soDonHoanTat: 2, tongDon: 4, soDonHuyBoiShop: 1, tyLeHuyBoiShop: 25.0, gioXacNhanTrungBinh: 3.5, diemDanhGia: 4.5, soDanhGia: 6 },
    { maCH: 4, tenCuaHang: 'Tiệm B', doanhThu: 0, soDonHoanTat: 0, tongDon: 1, soDonHuyBoiShop: 0, tyLeHuyBoiShop: 0, gioXacNhanTrungBinh: null, diemDanhGia: null, soDanhGia: 0 }],
  donQuaHan: [{ maDHCH: 11, maDH: 5, maCH: 3, tenCuaHang: 'Tiệm A', ngayDat: '2026-10-10T10:00:00', gioChoXacNhan: 60 },
    { maDHCH: 12, maDH: 6, maCH: 3, tenCuaHang: 'Tiệm A', ngayDat: '2026-10-11T10:00:00', gioChoXacNhan: 50 }],
  vanChuyen: [{ tenDonVi: 'GHN', soDon: 10, giaoThanhCong: 9, hoanTra: 1, tyLeThanhCong: 90.0, tyLeHoan: 10.0, gioGiaoTrungBinh: 30.0 }],
  huyHoan: { huyTheoBen: [{ ben: 'KHACH_HANG', soDon: 3 }, { ben: 'CHU_CUA_HANG', soDon: 1 }], hoanTheoTuan: [{ tuNgay: '2026-10-12', soTien: 100000, soKhoan: 1 }], gioXuLyHoanTrungBinh: 12.5 },
  danhMuc: [{ maDM: 1, tenDanhMuc: 'Văn học', doanhThu: 200000, hoaHong: 10000, tyLeDangApDung: 5 }],
  kiemDuyet: { cuaHangCho: 1, sachCho: 2, tyLeTuChoi: 20.0 }
};

function stats(handler) {
  return boot('management/stats.html', ['management/stats.js'], handler, 'QUAN_LY', { pathname: '/management/stats' });
}

test('stats shows cash flow, stores, overdue orders grouped by shop, carriers and has no CSV export', async () => {
  const app = stats(async () => STATS);
  await settle();

  assert.match(pageCalls(app)[0].url, /^\/api\/management\/stats\?tuNgay=\d{4}-\d{2}-\d{2}&denNgay=\d{4}-\d{2}-\d{2}$/);
  assert.match(app.get('stFlow').textContent, /Sàn đang giữ/);
  assert.match(app.get('stFlow').textContent, /175[.,]000/);
  assert.ok(shown(app, 'stMismatch'));
  assert.equal(app.get('stStores').querySelector('i'), null, 'tên cửa hàng không được thành HTML');
  assert.match(app.get('stStores').textContent, /3,5 giờ|3\.5 giờ/);
  assert.match(app.get('stStores').textContent, /—/, 'cửa hàng chưa có đánh giá và chưa xác nhận đơn nào hiện dấu gạch');
  // Hai đơn quá hạn của cùng một cửa hàng gom thành một dòng với một nút nhắc.
  assert.equal(app.get('stLate').querySelectorAll('li').length, 1);
  assert.match(app.get('stLate').textContent, /2 đơn · lâu nhất 60 giờ/);
  assert.match(app.get('stCarriers').textContent, /90/);
  assert.match(app.get('stCancelBy').textContent, /Khách hàng/);
  assert.match(app.get('stRefundTime').textContent, /12,5 giờ|12\.5 giờ/);
  assert.match(app.get('stModeration').textContent, /20/);
  assert.equal(app.document.querySelector('[data-export], #reportExport'), null, 'Quản lý không có nút xuất CSV');
});

test('stats reminds an overdue shop once, switches periods and applies a custom range', async () => {
  const app = stats(async call => (call.method === 'POST' ? { daNhacLuc: '2026-10-20T10:00:00' } : STATS));
  await settle();

  app.trigger(app.document.querySelector('[data-remind="3"]'));
  await settle();
  assert.deepEqual(pageCalls(app).filter(call => call.method === 'POST').map(call => call.url),
    ['/api/management/stats/stores/3/remind-confirm']);

  app.trigger(app.document.querySelector('[data-range="7d"]'));
  await settle();
  const sevenDays = pageCalls(app).filter(call => call.method === 'GET').at(-1).url;
  const [from, to] = [/tuNgay=([\d-]+)/.exec(sevenDays)[1], /denNgay=([\d-]+)/.exec(sevenDays)[1]];
  assert.equal(Math.round((new Date(to) - new Date(from)) / 86400000), 6);

  app.trigger(app.document.querySelector('[data-range="custom"]'));
  assert.ok(shown(app, 'stCustom'));
  app.set('stFrom', '2026-01-01');
  app.set('stTo', '2026-03-31');
  app.trigger('stApply');
  await settle();
  assert.match(pageCalls(app).filter(call => call.method === 'GET').at(-1).url, /tuNgay=2026-01-01&denNgay=2026-03-31/);
});

test('stats is for managers only', async () => {
  for (const role of ['KHACH_HANG', 'QUAN_TRI_VIEN']) {
    const app = boot('management/stats.html', ['management/stats.js'], async () => STATS, role);
    await settle();
    assert.ok(shown(app, 'stDenied'), role);
    assert.deepEqual(pageCalls(app), []);
  }
});

// ---------------------------------------------------------------------------
// Hoa hồng
// ---------------------------------------------------------------------------

const COMMISSIONS = [
  { maDM: 1, tenDanhMuc: 'Văn học', tyLe: 5, macDinh: true, soCuaHang: 2, apDungTu: null, suaLanCuoi: null, nguoiSua: null, ghiChu: null },
  { maDM: 2, tenDanhMuc: 'Kinh tế', tyLe: 8, macDinh: false, soCuaHang: 1, apDungTu: '2026-10-01T09:00:00', suaLanCuoi: '2026-10-01T09:00:00', nguoiSua: 'Quản Lý Mẫu', ghiChu: '<b>thử</b>' }
];

test('commissions lists categories with the default marker and edits a rate only after confirming', async () => {
  let rows = COMMISSIONS;
  const app = boot('management/commissions.html', ['management/commissions.js'], async call => {
    if (call.method === 'PUT') { rows = [{ ...COMMISSIONS[0], tyLe: 7, macDinh: false }, COMMISSIONS[1]]; return rows[0]; }
    return rows;
  }, 'QUAN_LY', { pathname: '/management/commissions' });
  await settle();

  assert.match(app.get('cmRows').textContent, /mặc định/);
  assert.equal(app.get('cmRows').querySelector('b'), null, 'ghi chú không được thành HTML');
  assert.match(app.get('cmContent').textContent, /giao thành công/);

  app.trigger(app.document.querySelector('[data-edit="1"]'));
  assert.ok(app.get('cmEditModal').classList.contains('show'));
  assert.equal(app.get('cmRate').value, '5');

  // Tỷ lệ ngoài 0-100 bị chặn tại chỗ.
  app.set('cmRate', '120');
  app.trigger('cmEditForm', 'submit');
  await settle();
  assert.ok(shown(app, 'cmEditError'));
  assert.equal(app.calls.filter(call => call.method === 'PUT').length, 0);

  app.set('cmRate', '7');
  app.set('cmNote', 'Khuyến khích');
  app.window.OldBook.confirm = async () => false;
  app.trigger('cmEditForm', 'submit');
  await settle();
  assert.equal(app.calls.filter(call => call.method === 'PUT').length, 0);

  app.window.OldBook.confirm = async () => true;
  app.trigger('cmEditForm', 'submit');
  await settle();
  const put = app.calls.find(call => call.method === 'PUT');
  assert.equal(put.url, '/api/management/commissions/1');
  assert.deepEqual({ ...put.body }, { tyLe: 7, ghiChu: 'Khuyến khích' });
  assert.match(app.get('cmRows').querySelector('tr').textContent, /7%/);
});

test('commissions history opens in a dialog and other roles are denied', async () => {
  const app = boot('management/commissions.html', ['management/commissions.js'], async call => {
    if (call.url.endsWith('/history')) {
      return [{ maCauHinh: 2, tyLe: 8, trangThai: 'HOAT_DONG', ngayBatDau: '2026-10-01T09:00:00', ngayKetThuc: null, nguoiSua: 'Quản Lý Mẫu', ghiChu: null }];
    }
    return COMMISSIONS;
  }, 'QUAN_LY');
  await settle();

  app.trigger(app.document.querySelector('[data-history="2"]'));
  await settle();
  assert.ok(app.get('cmHistoryModal').classList.contains('show'));
  assert.match(app.get('cmHistoryRows').textContent, /8%/);
  assert.match(app.get('cmHistoryRows').textContent, /Đang áp dụng/);

  const denied = boot('management/commissions.html', ['management/commissions.js'], async () => COMMISSIONS, 'CHU_CUA_HANG');
  await settle();
  assert.ok(shown(denied, 'cmDenied'));
  assert.deepEqual(pageCalls(denied), []);
});
