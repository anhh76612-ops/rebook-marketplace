'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const { boot, settle, event } = require('./dom-harness.cjs');

const API = '/api/thong-bao';

function notice(id, overrides = {}) {
  return {
    maThongBao: id, loai: 'DON_HANG_DOI_TRANG_THAI', mucDo: 'THONG_TIN', tieuDe: 'Thông báo ' + id,
    noiDung: 'Nội dung ' + id, duongDan: '/orders', daDoc: false, ngayTao: '2026-10-12T08:00:00', ...overrides
  };
}

function page(items, totalPages = 1) {
  return { content: items, page: 0, size: 10, totalElements: items.length, totalPages };
}

function bellApp(handler, role = 'KHACH_HANG') {
  const app = boot('fragments/header.html', ['notifications.js'], async call => {
    if (call.url === '/api/profile') { return { hoTen: 'Người dùng' }; }
    return handler(call);
  }, role);
  app.root = app.document.querySelector('[data-notify]');
  app.badge = app.document.querySelector('[data-notify-badge]');
  app.bell = app.document.querySelector('[data-notify-bell]');
  app.items = () => app.document.querySelectorAll('.notify-item');
  app.open = () => app.root.dispatchEvent(event('show.bs.dropdown'));
  return app;
}

test('the bell shows the unread count on load and lists notifications when opened', async () => {
  const app = bellApp(async call => {
    if (call.url === API + '/chua-doc') { return { soLuong: 3 }; }
    if (call.url.startsWith(API + '?')) {
      return page([
        notice(9, { mucDo: 'QUAN_TRONG', tieuDe: '<img src=x onerror=unsafe()>', noiDung: '<script>unsafe()</script>' }),
        notice(8, { daDoc: true }),
        notice(7, { mucDo: 'NGHIEM_TRONG' })
      ]);
    }
    return null;
  });
  await settle();

  assert.equal(app.badge.textContent, '3');
  assert.equal(app.badge.classList.contains('d-none'), false);
  assert.equal(app.bell.getAttribute('aria-label'), 'Thông báo, 3 chưa đọc');
  assert.equal(app.document.querySelector('[data-notify-read-all]').disabled, false);
  // Không có WebSocket trong môi trường này: trang không xin vé và cũng không hỏi máy chủ theo chu kỳ.
  assert.equal(app.calls.some(call => call.url === API + '/ticket'), false);
  assert.equal(app.calls.filter(call => call.url.startsWith(API)).length, 1, 'the list is not loaded until the bell is opened');

  app.open();
  await settle();

  assert.equal(app.calls.find(call => call.url.startsWith(API + '?')).url, API + '?page=0&size=10');
  assert.equal(app.items().length, 3);
  const list = app.document.querySelector('[data-notify-list]');
  assert.equal(list.querySelector('img'), null);
  assert.equal(list.querySelector('script'), null);
  assert.equal(app.items()[0].querySelector('.notify-item-title').textContent, '<img src=x onerror=unsafe()>');
  assert.deepEqual(app.items().map(item => item.classList.contains('is-unread')), [true, false, true]);
  assert.equal(app.items()[0].classList.contains('is-important'), true);
  assert.equal(app.items()[2].classList.contains('is-critical'), true);
  assert.equal(app.document.querySelector('[data-notify-more]').classList.contains('d-none'), true);
});

test('opening a notification marks it read first, then goes only to an internal page', async () => {
  const app = bellApp(async call => {
    if (call.url === API + '/chua-doc') { return { soLuong: 2 }; }
    if (call.url.startsWith(API + '?')) {
      return page([notice(5), notice(4, { duongDan: '//evil.example/steal' }), notice(3, { daDoc: true, duongDan: '/store/books' })]);
    }
    return null;
  });
  await settle();
  app.open();
  await settle();

  app.trigger(app.items()[0].querySelector('.notify-item-title'));
  await settle();
  const read = app.calls.find(call => call.method === 'PUT');
  assert.equal(read.url, API + '/5/da-doc');
  assert.equal(app.badge.textContent, '1');
  assert.equal(app.items()[0].classList.contains('is-unread'), false);
  assert.equal(app.items()[0].querySelector('[data-notify-unread]'), null);
  assert.deepEqual(app.redirects, ['/orders']);

  // Đường dẫn ra ngoài trang web bị bỏ qua: vẫn đánh dấu đã đọc nhưng không chuyển trang.
  app.trigger(app.items()[1]);
  await settle();
  assert.equal(app.badge.classList.contains('d-none'), true);
  assert.deepEqual(app.redirects, ['/orders']);

  // Thông báo đã đọc: mở trang luôn, không gọi máy chủ lần nữa.
  app.trigger(app.items()[2]);
  await settle();
  assert.equal(app.calls.filter(call => call.method === 'PUT').length, 2);
  assert.deepEqual(app.redirects, ['/orders', '/store/books']);
});

test('mark-all-read clears the badge, and more pages are appended on request', async () => {
  let allRead = false;
  const app = bellApp(async call => {
    if (call.url === API + '/chua-doc') { return { soLuong: 12 }; }
    if (call.url === API + '?page=0&size=10') { return page([notice(2, { daDoc: allRead }), notice(1, { daDoc: allRead })], allRead ? 1 : 2); }
    if (call.url === API + '?page=1&size=10') { return page([notice(0)], 2); }
    if (call.url === API + '/da-doc') { allRead = true; return null; }
    return null;
  });
  await settle();
  app.open();
  await settle();

  const more = app.document.querySelector('[data-notify-more]');
  assert.equal(more.classList.contains('d-none'), false);
  app.trigger(more);
  await settle();
  assert.deepEqual(app.items().map(item => item.querySelector('.notify-item-title').textContent),
    ['Thông báo 2', 'Thông báo 1', 'Thông báo 0']);
  assert.equal(more.classList.contains('d-none'), true);

  app.trigger(app.document.querySelector('[data-notify-read-all]'));
  await settle();
  assert.equal(app.calls.find(call => call.url === API + '/da-doc').method, 'PUT');
  assert.equal(app.badge.classList.contains('d-none'), true);
  assert.equal(app.bell.getAttribute('aria-label'), 'Thông báo');
  assert.equal(app.document.querySelector('[data-notify-read-all]').disabled, true);
  assert.ok(app.items().every(item => !item.classList.contains('is-unread')));
});

test('a pushed notification updates the badge at once and offers a link to the page', async () => {
  const sockets = [];
  class FakeSocket {
    constructor(url) { this.url = url; this.readyState = 1; this.sent = []; sockets.push(this); }
    send(data) { this.sent.push(data); }
    close() { this.closed = true; }
  }
  const app = bellApp(async call => {
    if (call.url === API + '/chua-doc') { return { soLuong: 1 }; }
    if (call.url === API + '/ticket') { return { ticket: 'vé một+lần' }; }
    if (call.url.startsWith(API + '?')) { return page([notice(21)]); }
    return null;
  });
  await settle();
  const heard = [];
  app.window.OldBook.notifications.onNew(item => heard.push(item.maThongBao));

  app.window.WebSocket = FakeSocket;
  app.window.OldBook.notifications.connect();
  await settle();

  // JWT không nằm trên URL: socket chỉ mang vé dùng một lần xin qua REST.
  const ticket = app.calls.find(call => call.url === API + '/ticket');
  assert.equal(ticket.method, 'POST');
  assert.match(ticket.headers.Authorization, /^Bearer /);
  assert.equal(sockets.length, 1);
  assert.equal(sockets[0].url, 'ws://localhost:8080/ws/thong-bao?ticket=' + encodeURIComponent('vé một+lần'));
  assert.equal(sockets[0].url.includes(app.window.OldBook.auth.getToken()), false);

  // Gọi lại khi đang kết nối thì không mở thêm kết nối.
  app.window.OldBook.notifications.connect();
  await settle();
  assert.equal(sockets.length, 1);

  sockets[0].onmessage({ data: JSON.stringify({ type: 'THONG_BAO_MOI', chuaDoc: 4, thongBao: notice(21, { tieuDe: 'Đơn #7 đã được xác nhận' }) }) });
  assert.equal(app.badge.textContent, '4');
  assert.deepEqual(heard, [21]);
  const toast = app.get('ob-toast-holder');
  assert.match(toast.textContent, /Đơn #7 đã được xác nhận/);
  assert.equal(toast.querySelector('a').href, '/orders');

  // Tin lạ hoặc hỏng bị bỏ qua.
  sockets[0].onmessage({ data: 'không phải JSON' });
  sockets[0].onmessage({ data: JSON.stringify({ type: 'KHAC' }) });
  assert.equal(app.badge.textContent, '4');
  assert.deepEqual(heard, [21]);

  // Rời trang thì đóng kết nối để trả lượt cho máy chủ.
  app.window.dispatchEvent(event('pagehide'));
  assert.equal(sockets[0].closed, true);
});

test('guests get no bell activity at all', async () => {
  const app = bellApp(async () => { throw new Error('Guest must not call the notification API'); }, null);
  await settle();
  assert.equal(app.calls.some(call => call.url.startsWith(API)), false);
  assert.equal(app.root.classList.contains('d-none'), true);
});
