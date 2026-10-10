'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const { boot, settle } = require('./dom-harness.cjs');

const SUMMARY_URL = '/api/admin/dashboard/summary';
const profile = { hoTen: 'Quản trị viên', email: 'admin@example.com' };
const summary = {
  tongNguoiDung: 1248, tongTaiKhoan: 1360, taiKhoanHoatDong: 1320, taiKhoanBiKhoa: 34, taiKhoanChoXacThuc: 6,
  phanBoVaiTro: [
    { vaiTro: 'KHACH_HANG', tenHienThi: 'Khách hàng', soLuong: 1020 },
    { vaiTro: 'CHU_CUA_HANG', tenHienThi: 'Chủ cửa hàng <b>', soLuong: 340 }
  ]
};

const LOGS_URL = '/api/admin/nhat-ky?page=0&size=10';
const logsPage = items => ({ content: items, page: 0, size: 10, totalElements: items.length, totalPages: items.length ? 1 : 0 });

// handler trả lời API summary; logsHandler trả lời API nhật ký (mặc định: chưa có nhật ký nào).
function dashboard(handler, role = 'QUAN_TRI_VIEN', logsHandler = async () => logsPage([])) {
  return boot('admin/dashboard.html', ['admin/layout.js', 'admin/dashboard.js'], async call => {
    if (call.url === '/api/profile') { return profile; }
    return call.url === LOGS_URL ? logsHandler(call) : handler(call);
  }, role, { pathname: '/admin/dashboard' });
}

const summaryCalls = app => app.calls.filter(call => call.url === SUMMARY_URL);
const logsCalls = app => app.calls.filter(call => call.url === LOGS_URL);
const text = (app, id) => app.get(id).textContent.replace(/\s+/g, ' ').trim();

test('admin dashboard renders KPIs and role distribution from the summary API', async () => {
  const app = dashboard(async () => summary);
  await settle();

  assert.equal(summaryCalls(app).length, 1);
  assert.equal(summaryCalls(app)[0].headers.Authorization, 'Bearer ' + app.window.OldBook.auth.getToken());

  assert.equal(text(app, 'kpiNguoiDung'), '1.248');
  assert.equal(text(app, 'kpiTaiKhoan'), '1.360');
  assert.equal(text(app, 'kpiChoXacThuc'), '6');
  assert.equal(text(app, 'kpiHoatDong'), '1.320');
  assert.equal(text(app, 'kpiHoatDongTyLe'), '97,1%');
  assert.equal(text(app, 'kpiBiKhoa'), '34');
  assert.equal(text(app, 'kpiBiKhoaTyLe'), '2,5%');

  assert.equal(text(app, 'dashRoleTotal'), 'Tổng 1.360 tài khoản');
  assert.equal(text(app, 'dashRoleCount'), '2');
  assert.match(text(app, 'dashRoleList'), /Khách hàng\s*75% \(1\.020 TK\)/);
  assert.equal(app.get('dashRoleList').querySelector('b'), null, 'role labels are escaped');
  assert.equal(app.get('dashRoleDonut').querySelectorAll('circle').length, 3);
  assert.equal(app.get('dashRoleEmpty').classList.contains('hidden'), true);

  assert.match(text(app, 'dashUpdatedAt'), /^Cập nhật lúc /);
  assert.equal(app.get('dashError').classList.contains('hidden'), true);
  assert.equal(app.get('dashRefresh').disabled, false);
});

test('admin dashboard never queries the API for guests or other roles', async () => {
  const fail = async () => { throw new Error('Must not query the dashboard API'); };

  const guest = dashboard(fail, null, fail);
  await settle();
  assert.deepEqual(guest.redirects, ['/login?redirect=%2Fadmin%2Fdashboard']);
  assert.equal(guest.calls.length, 0);

  const manager = dashboard(fail, 'QUAN_LY', fail);
  await settle();
  assert.equal(summaryCalls(manager).length, 0);
  assert.equal(logsCalls(manager).length, 0);
  assert.deepEqual(manager.redirects, []);
  assert.match(manager.document.querySelector('main').textContent, /Bạn không có quyền truy cập khu vực quản trị/);
  assert.equal(manager.get('kpiNguoiDung'), null);
});

test('a failed refresh keeps the last good numbers and does not look like a fresh update', async () => {
  let response = summary;
  const app = dashboard(async () => response);
  await settle();
  const updatedAt = text(app, 'dashUpdatedAt');

  response = { httpStatus: 500, data: null, message: 'Máy chủ gặp lỗi' };
  app.trigger('dashRefresh');
  await settle();

  assert.equal(summaryCalls(app).length, 2);
  assert.equal(text(app, 'kpiNguoiDung'), '1.248');
  assert.equal(text(app, 'dashUpdatedAt'), updatedAt);
  assert.equal(app.get('dashError').classList.contains('hidden'), false);
  assert.equal(text(app, 'dashErrorText'), 'Máy chủ gặp lỗi');
  assert.equal(app.get('dashRefresh').disabled, false);

  response = summary;
  app.trigger('dashRetry');
  await settle();
  assert.equal(app.get('dashError').classList.contains('hidden'), true);
});

test('server rejections are handled by status: 403 shows the denied screen, 401 returns to login', async () => {
  const reject = (status, message) => async () => ({ httpStatus: status, data: null, message });

  const forbidden = dashboard(reject(403, 'Không có quyền'), 'QUAN_TRI_VIEN', reject(403, 'Không có quyền'));
  const token = forbidden.window.OldBook.auth.getToken();
  await settle();
  assert.equal(forbidden.window.OldBook.auth.getToken(), token);
  assert.deepEqual(forbidden.redirects, []);
  assert.match(forbidden.document.querySelector('main').textContent, /Bạn không có quyền truy cập khu vực quản trị/);

  // Hai API được gọi song song nên cả hai cùng nhận 401; mọi lần chuyển hướng đều phải về trang đăng nhập.
  const expired = dashboard(reject(401, 'Phiên hết hạn'), 'QUAN_TRI_VIEN', reject(401, 'Phiên hết hạn'));
  await settle();
  assert.equal(expired.window.OldBook.auth.getToken(), null);
  assert.deepEqual([...new Set(expired.redirects)], ['/login?redirect=%2Fadmin%2Fdashboard']);
});

test('recent logs list the newest entries as escaped text and reload with the refresh button', async () => {
  const logs = [
    { maNhatKy: 9, maTK: 2, vaiTro: 'QUAN_TRI_VIEN', tenNguoiThucHien: 'Hoàng An', emailNguoiThucHien: 'an@example.com',
      hanhDong: 'KHOA_TAI_KHOAN', loaiDoiTuong: 'TAI_KHOAN', maDoiTuong: 25, moTa: '<img src=x onerror=unsafe()>', thoiGian: '2026-10-10T10:42:00' },
    { maNhatKy: 8, maTK: 7, vaiTro: 'QUAN_LY', tenNguoiThucHien: null, emailNguoiThucHien: 'manager@example.com',
      hanhDong: 'DUYET_CUA_HANG', loaiDoiTuong: null, maDoiTuong: null, moTa: null, thoiGian: '2026-10-10T09:30:00' }
  ];
  const app = dashboard(async () => summary, 'QUAN_TRI_VIEN', async () => logsPage(logs));
  await settle();

  assert.equal(logsCalls(app).length, 1);
  const box = app.get('dashLogs');
  assert.equal(box.getAttribute('aria-busy'), 'false');
  assert.equal(box.querySelector('img'), null, 'log descriptions are escaped');
  // Tên hiển thị ưu tiên họ tên, thiếu thì dùng email.
  assert.deepEqual(box.querySelectorAll('strong').map(actor => actor.textContent), ['Hoàng An', 'manager@example.com']);
  const content = text(app, 'dashLogs');
  for (const expected of ['KHOA_TAI_KHOAN', 'TAI_KHOAN #25', 'Quản trị viên', 'DUYET_CUA_HANG', 'Quản lý', '10/10/2026', '<img src=x onerror=unsafe()>']) {
    assert.ok(content.includes(expected), 'shows ' + expected);
  }
  assert.ok(content.indexOf('KHOA_TAI_KHOAN') < content.indexOf('DUYET_CUA_HANG'), 'keeps the server order (newest first)');

  app.trigger('dashRefresh');
  await settle();
  assert.equal(summaryCalls(app).length, 2);
  assert.equal(logsCalls(app).length, 2);
});

test('recent logs show an empty state, and a failure can be retried without touching the KPIs', async () => {
  const empty = dashboard(async () => summary);
  await settle();
  assert.equal(text(empty, 'dashLogs'), 'Chưa có hoạt động nào được ghi nhận.');

  let response = { httpStatus: 500, data: null, message: 'Máy chủ gặp lỗi' };
  const app = dashboard(async () => summary, 'QUAN_TRI_VIEN', async () => response);
  await settle();
  assert.match(text(app, 'dashLogs'), /Không tải được nhật ký: Máy chủ gặp lỗi/);
  assert.equal(text(app, 'kpiNguoiDung'), '1.248');
  assert.equal(app.get('dashError').classList.contains('hidden'), true);

  response = logsPage([{ maNhatKy: 1, maTK: 2, vaiTro: 'QUAN_TRI_VIEN', tenNguoiThucHien: 'Hoàng An', hanhDong: 'DANG_NHAP', thoiGian: '2026-10-10T08:00:00' }]);
  app.trigger(app.get('dashLogs').querySelector('[data-logs-retry]'));
  await settle();
  assert.equal(logsCalls(app).length, 2);
  assert.equal(summaryCalls(app).length, 1);
  assert.match(text(app, 'dashLogs'), /DANG_NHAP/);
  assert.equal(app.get('dashLogs').querySelector('strong').textContent, 'Hoàng An');
});

test('shared admin modal resolves with the chosen action and treats text as plain text', async () => {
  const app = boot('admin/fragments/modal.html', ['admin/layout.js'], async () => profile, 'QUAN_TRI_VIEN');
  const ui = app.window.AdminUI;

  const confirmed = ui.confirm({ title: 'Khóa tài khoản', message: '<img src=x onerror=unsafe()>', danger: true });
  assert.equal(app.get('adminModal').classList.contains('hidden'), false);
  assert.equal(app.get('adminModalTitle').textContent, 'Khóa tài khoản');
  assert.equal(app.get('adminModalBody').querySelector('img'), null);
  const buttons = app.get('adminModalFooter').querySelectorAll('button');
  assert.deepEqual(buttons.map(button => button.textContent), ['Hủy', 'Xác nhận']);
  app.trigger(buttons[1]);
  assert.equal(await confirmed, true);
  assert.equal(app.get('adminModal').classList.contains('hidden'), true);

  const dismissed = ui.confirm({ message: 'Tiếp tục?' });
  app.trigger('btnCloseAdminModal');
  assert.equal(await dismissed, false);
});
