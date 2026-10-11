'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const { boot, settle } = require('./dom-harness.cjs');

const BASE = '/api/management/shipping-carriers';

function carrier(overrides = {}) {
  return {
    maDVVC: 1, maCode: 'GHN', tenDonVi: 'Giao Hàng Nhanh', phiVanChuyen: 25000, thoiGianGiaoDuKien: 2,
    nguoiLienHe: 'Nguyễn Văn A', soDienThoai: '0901234567', email: 'doitac@ghn.vn', diaChi: 'Q.1, TP.HCM',
    website: 'https://ghn.vn', ghiChu: 'Ưu tiên nội thành', thongTinTichHop: 'endpoint-ghn', trangThai: 'HOAT_DONG',
    lyDoTamDung: null, ngayTao: '2026-10-01', ngayCapNhat: '2026-10-02T09:30:00', ngayCapNhatTrangThai: '2026-10-01T08:00:00',
    duongDanTrang: '/shipping/GHN', duongDanApi: '/api/shipping/GHN',
    duongDanTrangDayDu: 'http://localhost:8080/shipping/GHN', duongDanApiDayDu: 'http://localhost:8080/api/shipping/GHN',
    soVanDon: 5, soVanDonDangXuLy: 2, soVanDonDaGiao: 3,
    ...overrides
  };
}

function listResponse(items) {
  const active = items.filter(item => item.trangThai === 'HOAT_DONG').length;
  return {
    thongKe: { tongDoiTac: items.length, dangHopTac: active, tamDung: items.length - active, vanDonDangXuLy: items.reduce((sum, item) => sum + item.soVanDonDangXuLy, 0) },
    danhSach: items
  };
}

function start(role, handler) {
  return boot('shipping/carriers.html', ['management/carriers.js'], handler, role);
}

function rowButton(app, code, action) {
  return app.document.querySelector('tr[data-carrier-code="' + code + '"] [data-carrier-action="' + action + '"]');
}

test('manager sees statistics, escaped rows and filters by keyword and status', async () => {
  const items = [carrier(), carrier({ maDVVC: 2, maCode: 'JT', tenDonVi: '<b>J&T</b>', trangThai: 'NGUNG_HOAT_DONG', email: null, soVanDonDangXuLy: 0 })];
  const app = start('QUAN_LY', async () => listResponse(items));
  await settle();
  const listCalls = () => app.calls.filter(call => call.url.startsWith(BASE));
  assert.equal(listCalls()[0].url, BASE);
  assert.ok(!app.get('carriersContent').classList.contains('d-none'));
  assert.equal(app.get('statTotal').textContent, '2');
  assert.equal(app.get('statActive').textContent, '1');
  assert.equal(app.get('statPaused').textContent, '1');
  assert.equal(app.get('statShipping').textContent, '2');
  assert.match(app.get('carriersRows').innerHTML, /&lt;b&gt;J&amp;T&lt;\/b&gt;/);
  assert.equal(rowButton(app, 'GHN', 'status').textContent, 'Tạm dừng');
  assert.equal(rowButton(app, 'JT', 'status').textContent, 'Kích hoạt');

  app.set('carrierKeyword', '  giao hang ');
  app.set('carrierStatusFilter', 'HOAT_DONG');
  app.trigger('carriersFilters', 'submit');
  await settle();
  assert.equal(listCalls()[1].url, BASE + '?tuKhoa=giao+hang&trangThai=HOAT_DONG');
});

test('non-manager roles are denied without calling the management API', async () => {
  const app = start('KHACH_HANG', async () => listResponse([]));
  await settle();
  assert.equal(app.calls.filter(call => call.url.startsWith(BASE)).length, 0);
  assert.ok(!app.get('carriersDenied').classList.contains('d-none'));
});

test('admin also has access and a 403 from the server shows the denied panel', async () => {
  const app = start('QUAN_TRI_VIEN', async call => call.url.startsWith(BASE) ? { httpStatus: 403, message: 'Không có quyền' } : null);
  await settle();
  assert.ok(!app.get('carriersDenied').classList.contains('d-none'));
  assert.ok(app.get('carriersContent').classList.contains('d-none'));
});

test('creating a carrier sends the full contract and then provides the partner link', async () => {
  let items = [carrier()];
  const created = carrier({ maDVVC: 9, maCode: 'NINJAVAN', tenDonVi: 'Ninja Van', email: 'ops@ninjavan.vn', nguoiLienHe: 'Trần B',
    duongDanTrang: '/shipping/NINJAVAN', duongDanApi: '/api/shipping/NINJAVAN',
    duongDanTrangDayDu: 'http://localhost:8080/shipping/NINJAVAN', duongDanApiDayDu: 'http://localhost:8080/api/shipping/NINJAVAN', soVanDon: 0, soVanDonDangXuLy: 0 });
  const app = start('QUAN_LY', async call => {
    if (call.method === 'POST') { items = [...items, created]; return created; }
    return listResponse(items);
  });
  await settle();
  app.trigger('createCarrierButton');
  assert.ok(app.get('carrierFormModal').classList.contains('show'));
  assert.equal(app.get('carrierCode').disabled, false);
  app.set('carrierCode', 'ninjavan');
  app.set('carrierName', ' Ninja Van ');
  app.set('carrierFee', '30000');
  app.set('carrierDays', '3');
  app.set('carrierContact', 'Trần B');
  app.set('carrierPhone', '0912 345 678');
  app.set('carrierEmail', 'ops@ninjavan.vn');
  app.set('carrierWebsite', 'https://ninjavan.co');
  app.trigger('carrierForm', 'submit');
  await settle();

  const post = app.calls.find(call => call.method === 'POST');
  assert.deepEqual(post.body, {
    maCode: 'NINJAVAN', tenDonVi: 'Ninja Van', phiVanChuyen: 30000, thoiGianGiaoDuKien: 3, nguoiLienHe: 'Trần B',
    soDienThoai: '0912 345 678', email: 'ops@ninjavan.vn', website: 'https://ninjavan.co', diaChi: null, ghiChu: null, thongTinTichHop: null
  });
  assert.ok(!app.get('carrierFormModal').classList.contains('show'));
  assert.ok(app.get('carrierLinkModal').classList.contains('show'));
  assert.equal(app.get('linkPageUrl').value, 'http://localhost:8080/shipping/NINJAVAN');
  assert.equal(app.get('linkApiUrl').value, 'http://localhost:8080/api/shipping/NINJAVAN');
  assert.equal(app.get('linkPageOpen').getAttribute('href'), 'http://localhost:8080/shipping/NINJAVAN');
  const mailto = app.get('linkMailButton').getAttribute('href');
  assert.ok(mailto.startsWith('mailto:ops%40ninjavan.vn?subject='));
  assert.ok(decodeURIComponent(mailto).includes('http://localhost:8080/shipping/NINJAVAN'));
  assert.equal(app.get('statTotal').textContent, '2');
});

test('invalid create input is blocked locally and server errors stay in the form', async () => {
  const app = start('QUAN_LY', async call => call.method === 'POST' ? { httpStatus: 400, message: 'Mã đơn vị vận chuyển đã tồn tại: GHN' } : listResponse([carrier()]));
  await settle();
  app.trigger('createCarrierButton');
  app.set('carrierCode', 'G');
  app.set('carrierName', 'Test');
  app.set('carrierFee', '1000');
  app.set('carrierDays', '2');
  app.trigger('carrierForm', 'submit');
  await settle();
  assert.equal(app.calls.filter(call => call.method === 'POST').length, 0);

  app.set('carrierCode', 'GHN');
  app.trigger('carrierForm', 'submit');
  await settle();
  assert.equal(app.calls.filter(call => call.method === 'POST').length, 1);
  assert.equal(app.get('carrierFormError').textContent, 'Mã đơn vị vận chuyển đã tồn tại: GHN');
  assert.ok(!app.get('carrierFormError').classList.contains('d-none'));
  assert.ok(app.get('carrierFormModal').classList.contains('show'));
  assert.equal(app.get('carrierFormSubmit').disabled, false);
});

test('updating contact information keeps the code locked and preserves every field', async () => {
  const app = start('QUAN_LY', async call => call.method === 'PUT' ? carrier({ email: 'moi@ghn.vn' }) : listResponse([carrier()]));
  await settle();
  app.trigger(rowButton(app, 'GHN', 'edit'));
  assert.equal(app.get('carrierCode').disabled, true);
  assert.equal(app.get('carrierCode').value, 'GHN');
  assert.equal(app.get('carrierIntegration').value, 'endpoint-ghn');
  app.set('carrierEmail', 'moi@ghn.vn');
  app.set('carrierContact', 'Lê C');
  app.trigger('carrierForm', 'submit');
  await settle();
  const put = app.calls.find(call => call.method === 'PUT');
  assert.equal(put.url, BASE + '/GHN');
  assert.deepEqual(put.body, {
    tenDonVi: 'Giao Hàng Nhanh', phiVanChuyen: 25000, thoiGianGiaoDuKien: 2, nguoiLienHe: 'Lê C', soDienThoai: '0901234567',
    email: 'moi@ghn.vn', website: 'https://ghn.vn', diaChi: 'Q.1, TP.HCM', ghiChu: 'Ưu tiên nội thành', thongTinTichHop: 'endpoint-ghn'
  });
  assert.equal(app.calls.filter(call => call.method === 'GET' && call.url.startsWith(BASE)).length, 2);
});

test('pausing requires a reason, warns about open shipments and activation sends no reason', async () => {
  let status = 'HOAT_DONG';
  const app = start('QUAN_LY', async call => {
    if (call.method === 'PATCH') { status = call.body.trangThai; return carrier({ trangThai: status }); }
    return listResponse([carrier({ trangThai: status })]);
  });
  await settle();
  app.trigger(rowButton(app, 'GHN', 'status'));
  assert.equal(app.get('carrierStatusTitle').textContent, 'Tạm dừng hợp tác');
  assert.ok(!app.get('carrierStatusWarning').classList.contains('d-none'));
  assert.match(app.get('carrierStatusWarning').textContent, /2 vận đơn/);
  app.trigger('carrierStatusForm', 'submit');
  await settle();
  assert.equal(app.calls.filter(call => call.method === 'PATCH').length, 0);

  app.set('carrierPauseReason', 'Vi phạm SLA giao hàng');
  app.trigger('carrierStatusForm', 'submit');
  await settle();
  const pause = app.calls.find(call => call.method === 'PATCH');
  assert.equal(pause.url, BASE + '/GHN/status');
  assert.deepEqual(pause.body, { trangThai: 'NGUNG_HOAT_DONG', lyDo: 'Vi phạm SLA giao hàng' });
  assert.equal(rowButton(app, 'GHN', 'status').textContent, 'Kích hoạt');

  app.trigger(rowButton(app, 'GHN', 'status'));
  assert.equal(app.get('carrierStatusTitle').textContent, 'Kích hoạt hợp tác');
  assert.ok(app.get('carrierPauseReasonGroup').classList.contains('d-none'));
  app.trigger('carrierStatusForm', 'submit');
  await settle();
  assert.deepEqual(app.calls.filter(call => call.method === 'PATCH')[1].body, { trangThai: 'HOAT_DONG' });
});

test('detail shows contact data and can switch to the link dialog; carriers without email cannot be mailed', async () => {
  const app = start('QUAN_LY', async () => listResponse([carrier({ email: null, trangThai: 'NGUNG_HOAT_DONG', lyDoTamDung: 'Tạm ngưng hợp đồng' })]));
  await settle();
  app.trigger(rowButton(app, 'GHN', 'detail'));
  assert.equal(app.get('detailContact').textContent, 'Nguyễn Văn A');
  assert.equal(app.get('detailEmail').textContent, '—');
  assert.equal(app.get('detailPauseReason').textContent, 'Tạm ngưng hợp đồng');
  assert.equal(app.get('detailCreated').textContent, '01/10/2026');
  app.trigger('detailLinkButton');
  assert.ok(!app.get('carrierDetailModal').classList.contains('show'));
  assert.ok(app.get('carrierLinkModal').classList.contains('show'));
  assert.ok(!app.get('linkPausedWarning').classList.contains('d-none'));
  assert.ok(app.get('linkMailButton').classList.contains('disabled'));
  assert.equal(app.get('linkMailButton').getAttribute('href'), '#');
});
