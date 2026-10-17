'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const { boot, settle, event } = require('./dom-harness.cjs');

// Trang theo dõi đơn của khách và của cửa hàng tự tải lại khi máy chủ báo đơn thay đổi qua WebSocket.

const TICKET_URL = '/api/thong-bao/ticket';

function liveApp(seller) {
  let status = 'CHO_XU_LY';
  const store = () => ({
    maDHCH: 2, tenCuaHang: 'Hiệu sách', trangThaiDonHang: status,
    items: [{ maSach: 3, tenSach: 'Sách cũ', donGia: 50000, soLuong: 1 }]
  });
  const listUrl = seller ? '/api/store/orders' : '/api/order/orders';

  const app = boot(seller ? 'store-orders.html' : 'orders.html', ['notifications.js', 'order-tracking.js'], async call => {
    if (call.url === '/api/thong-bao/chua-doc') { return { soLuong: 0 }; }
    if (call.url === TICKET_URL) { return { ticket: 've-' + (app.calls.filter(c => c.url === TICKET_URL).length) }; }
    if (call.url === '/api/store/orders/products') { return []; }
    if (call.url === listUrl) {
      return seller ? [{ maDH: 1, storeOrder: store() }] : [{ maDH: 1, storeOrders: [store()] }];
    }
    return null;
  }, seller ? 'CHU_CUA_HANG' : 'KHACH_HANG', { runInline: true });

  const sockets = [];
  class FakeSocket {
    constructor(url) { this.url = url; this.readyState = 1; sockets.push(this); }
    send() { }
    close() { this.closed = true; }
  }
  app.window.WebSocket = FakeSocket;

  // Hẹn giờ giả: test tự chạy các hàm đã hẹn để kiểm soát thời điểm tải lại.
  let queued = [];
  app.window.setTimeout = fn => { queued.push(fn); return queued.length; };
  app.window.clearTimeout = () => { };

  Object.assign(app, {
    sockets,
    listLoads: () => app.calls.filter(call => call.url === listUrl).length,
    setStatus: value => { status = value; },
    pending: () => queued.length,
    async flush() {
      const run = queued;
      queued = [];
      run.forEach(fn => fn());
      await settle();
    },
    push(message) { sockets[sockets.length - 1].onmessage({ data: JSON.stringify(message) }); },
    // Trang khách có thêm nhãn thanh toán ở đầu thẻ; nhãn trạng thái của đơn con luôn là nhãn cuối.
    pill: () => Array.from(app.get('ordersList').querySelectorAll('.order-pill')).pop().textContent
  });
  return app;
}

for (const seller of [false, true]) {
  const page = seller ? 'seller orders' : 'customer orders';

  test(page + ' reload by themselves when the server pushes an order change', async () => {
    const app = liveApp(seller);
    await settle();
    assert.equal(app.listLoads(), 1);
    assert.equal(app.pill(), 'Chờ xử lý');

    app.window.OldBook.notifications.connect();
    await settle();
    assert.equal(app.sockets.length, 1);

    app.setStatus('DA_XAC_NHAN');
    app.push({ type: 'DON_HANG_THAY_DOI', maDH: 1, maDHCH: 2 });
    // Gom tin dồn dập: nhiều tin liên tiếp chỉ hẹn một lần tải.
    app.push({ type: 'DON_HANG_THAY_DOI', maDH: 1, maDHCH: 2 });
    app.push({ type: 'DON_HANG_THAY_DOI', maDH: 1, maDHCH: 2 });
    assert.equal(app.pending(), 1);
    assert.equal(app.listLoads(), 1, 'nothing is fetched until the short delay passes');

    await app.flush();
    assert.equal(app.listLoads(), 2);
    assert.equal(app.pill(), 'Đã xác nhận');
  });

  test(page + ' ignore pushes that are not order changes', async () => {
    const app = liveApp(seller);
    await settle();
    app.window.OldBook.notifications.connect();
    await settle();

    app.push({ type: 'THONG_BAO_MOI', chuaDoc: 1, thongBao: { maThongBao: 5, tieuDe: 'Khác' } });
    app.push({ type: 'KHAC' });
    app.sockets[0].onmessage({ data: 'không phải JSON' });

    assert.equal(app.pending(), 0);
    assert.equal(app.listLoads(), 1);
  });

  test(page + ' reload once after the socket reconnects because changes may have been missed', async () => {
    const app = liveApp(seller);
    await settle();
    app.window.OldBook.notifications.connect();
    await settle();
    app.sockets[0].onopen();
    assert.equal(app.pending(), 0, 'the very first connection has nothing to catch up on');

    app.sockets[0].onclose();
    await app.flush();                 // hẹn giờ nối lại sau 5 giây
    assert.equal(app.sockets.length, 2);

    app.setStatus('DANG_GIAO');
    app.sockets[1].onopen();
    assert.equal(app.pending(), 1);
    await app.flush();
    assert.equal(app.listLoads(), 2);
    assert.equal(app.pill(), 'Đang giao');
  });
}

test('live refresh waits while the page is busy and while the tab is hidden', async () => {
  const app = liveApp(false);
  await settle();
  app.window.OldBook.notifications.connect();
  await settle();

  let busy = true;
  let runs = 0;
  app.window.OldBook.tracking.liveRefresh(() => { runs++; }, () => !busy);

  app.push({ type: 'DON_HANG_THAY_DOI', maDH: 1, maDHCH: 2 });
  await app.flush();
  assert.equal(runs, 0, 'busy page is not refreshed');
  assert.equal(app.pending(), 1, 'and tries again later');

  busy = false;
  await app.flush();
  assert.equal(runs, 1);

  app.document.hidden = true;
  app.push({ type: 'DON_HANG_THAY_DOI', maDH: 1, maDHCH: 2 });
  await app.flush();
  assert.equal(runs, 1, 'hidden tab does not fetch');

  app.document.hidden = false;
  app.document.dispatchEvent(event('visibilitychange'));
  await app.flush();
  assert.equal(runs, 2, 'refreshes when the tab becomes visible again');
});
