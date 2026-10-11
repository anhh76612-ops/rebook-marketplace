'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const { boot, settle } = require('./dom-harness.cjs');

const profile = { hoTen: 'Quản trị viên', email: 'admin@example.com' };
const UNREAD_URL = '/api/thong-bao/chua-doc';
const listUrl = page => '/api/thong-bao?page=' + page + '&size=10';

const notification = (id, extra = {}) => ({
  maThongBao: id, loai: 'KHOA_TAI_KHOAN', mucDo: 'QUAN_TRONG', tieuDe: 'Thông báo ' + id, noiDung: 'Nội dung ' + id,
  duongDan: '/admin/accounts?tuKhoa=user' + id + '%40example.com', daDoc: false, ngayTao: '2026-10-10T10:00:00', ...extra
});
const page = (items, totalPages = 1) => ({ content: items, page: 0, size: 10, totalElements: items.length, totalPages });

// state: { unread, pages: [[...], [...]] } là "database" giả; các lời gọi PUT cập nhật state này.
function header(state, role = 'QUAN_TRI_VIEN') {
  return boot('admin/fragments/header.html', ['admin/layout.js'], async call => {
    if (call.url === '/api/profile') { return profile; }
    if (call.url === UNREAD_URL) { return { soLuong: state.unread }; }
    const list = call.url.match(/^\/api\/thong-bao\?page=(\d+)&size=10$/);
    if (list && call.method === 'GET') { return page(state.pages[Number(list[1])] || [], state.pages.length); }
    const one = call.url.match(/^\/api\/thong-bao\/(\d+)\/da-doc$/);
    if (one && call.method === 'PUT') { state.unread -= 1; return null; }
    if (call.url === '/api/thong-bao/da-doc' && call.method === 'PUT') {
      state.unread = 0;
      state.pages.forEach(items => items.forEach(item => { item.daDoc = true; }));
      return null;
    }
    throw new Error('Unexpected API call: ' + call.method + ' ' + call.url);
  }, role, { pathname: '/admin/dashboard' });
}

const items = app => app.get('adminNotifyList').querySelectorAll('button');
const callsTo = (app, method, url) => app.calls.filter(call => call.method === method && call.url === url);

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

test('the bell shows the real unread count and stays quiet when there is nothing new', async () => {
  const busy = header({ unread: 3, pages: [[]] });
  await settle();
  assert.equal(busy.get('adminNotifyBadge').textContent, '3');
  assert.equal(busy.get('adminNotifyBadge').classList.contains('hidden'), false);
  assert.equal(busy.get('btnNotifyReadAll').disabled, false);
  assert.equal(busy.get('adminNotifyPanel').classList.contains('hidden'), true);
  assert.equal(callsTo(busy, 'GET', listUrl(0)).length, 0, 'the list is only loaded when the bell is opened');

  const quiet = header({ unread: 0, pages: [[]] });
  await settle();
  assert.equal(quiet.get('adminNotifyBadge').classList.contains('hidden'), true);
  assert.equal(quiet.get('btnNotifyReadAll').disabled, true);

  const flooded = header({ unread: 250, pages: [[]] });
  await settle();
  assert.equal(flooded.get('adminNotifyBadge').textContent, '99+');

  // Vai trò khác không dùng chuông của khu vực admin.
  const manager = header({ unread: 5, pages: [[]] }, 'QUAN_LY');
  await settle();
  assert.equal(manager.calls.some(call => call.url.startsWith('/api/thong-bao')), false);
});

test('opening the bell lists notifications as plain text with their level, and loads further pages on demand', async () => {
  const state = {
    unread: 2,
    pages: [
      [notification(12, { mucDo: 'NGHIEM_TRONG', tieuDe: 'Tài khoản Quản trị viên bị khóa', noiDung: 'Lý do: <img src=x onerror=unsafe()>' }),
        notification(11, { mucDo: 'THONG_TIN', daDoc: true })],
      [notification(10)]
    ]
  };
  const app = header(state);
  await settle();

  app.trigger('btnNotifications');
  await settle();
  assert.equal(app.get('adminNotifyPanel').classList.contains('hidden'), false);
  assert.equal(app.get('btnNotifications').getAttribute('aria-expanded'), 'true');
  assert.equal(items(app).length, 2);

  const [first, second] = items(app);
  assert.equal(first.querySelector('img'), null, 'notification text is never parsed as HTML');
  for (const expected of ['Tài khoản Quản trị viên bị khóa', 'Lý do: <img src=x onerror=unsafe()>', 'Nghiêm trọng', 'Chưa đọc']) {
    assert.ok(first.textContent.includes(expected), 'shows ' + expected);
  }
  assert.ok(second.textContent.includes('Thông tin'));
  assert.equal(second.textContent.includes('Chưa đọc'), false);

  assert.equal(app.get('btnNotifyMore').classList.contains('hidden'), false);
  app.trigger('btnNotifyMore');
  await settle();
  assert.deepEqual(items(app).map(item => item.getAttribute('data-notify-id')), ['12', '11', '10']);
  assert.equal(app.get('btnNotifyMore').classList.contains('hidden'), true);

  // Bấm ra ngoài thì đóng; mở lại thì tải lại từ trang đầu.
  app.trigger(app.document.querySelector('input'));
  assert.equal(app.get('adminNotifyPanel').classList.contains('hidden'), true);
  app.trigger('btnNotifications');
  await settle();
  assert.equal(items(app).length, 2);
});

test('clicking a notification marks it read, lowers the badge and opens only internal links', async () => {
  const state = { unread: 2, pages: [[notification(7), notification(6, { duongDan: 'https://evil.example/steal' }), notification(5, { daDoc: true })]] };
  const app = header(state);
  await settle();
  app.trigger('btnNotifications');
  await settle();

  app.trigger(items(app)[0]);
  await settle();
  assert.equal(callsTo(app, 'PUT', '/api/thong-bao/7/da-doc').length, 1);
  assert.equal(app.get('adminNotifyBadge').textContent, '1');
  assert.equal(items(app)[0].textContent.includes('Chưa đọc'), false);
  assert.deepEqual(app.redirects, ['/admin/accounts?tuKhoa=user7%40example.com']);

  // Đường dẫn ra ngoài trang web thì chỉ đánh dấu đã đọc, không chuyển trang.
  app.trigger(items(app)[1]);
  await settle();
  assert.equal(callsTo(app, 'PUT', '/api/thong-bao/6/da-doc').length, 1);
  assert.equal(app.get('adminNotifyBadge').classList.contains('hidden'), true);
  assert.equal(app.redirects.length, 1);

  // Thông báo đã đọc: mở thẳng trang, không gọi lại API đánh dấu.
  app.trigger(items(app)[2]);
  await settle();
  assert.equal(app.calls.filter(call => call.method === 'PUT').length, 2);
  assert.equal(app.redirects.length, 2);
});

test('mark all as read clears the badge and reloads the list', async () => {
  const state = { unread: 2, pages: [[notification(3), notification(2)]] };
  const app = header(state);
  await settle();
  app.trigger('btnNotifications');
  await settle();

  app.trigger('btnNotifyReadAll');
  await settle();
  assert.equal(callsTo(app, 'PUT', '/api/thong-bao/da-doc').length, 1);
  assert.equal(app.get('adminNotifyBadge').classList.contains('hidden'), true);
  assert.equal(app.get('btnNotifyReadAll').disabled, true);
  assert.equal(callsTo(app, 'GET', listUrl(0)).length, 2);
  assert.equal(items(app).some(item => item.textContent.includes('Chưa đọc')), false);
  assert.equal(app.get('adminNotifyPanel').classList.contains('hidden'), false);
});
