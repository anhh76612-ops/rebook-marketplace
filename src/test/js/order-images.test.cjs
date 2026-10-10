'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const { boot, settle } = require('./dom-harness.cjs');

const title = 'Sách "hay" <&>';
const imageUrl = 'https://example.com/cover.jpg?size=70&title="book"';

function orderApp(seller, url = imageUrl) {
  const store = {
    maDHCH: 2, tenCuaHang: 'Hiệu sách', trangThaiDonHang: 'CHO_XU_LY',
    items: [{ maSach: 3, tenSach: title, hinhAnhUrl: url, donGia: 50000, soLuong: 1 }]
  };
  return boot(seller ? 'store-orders.html' : 'orders.html', ['order-tracking.js'], async call => {
    assert.equal(call.url, seller ? '/api/store/orders' : '/api/order/orders');
    return seller ? [{ maDH: 1, storeOrder: store }] : [{ maDH: 1, storeOrders: [store] }];
  }, seller ? 'CHU_CUA_HANG' : 'KHACH_HANG', { runInline: true });
}

for (const seller of [false, true]) {
  const page = seller ? 'seller orders' : 'customer orders';

  test(page + ' renders the API cover in all orders and status tabs', async () => {
    const app = orderApp(seller);
    await settle();
    const assertCover = () => {
      const image = app.get('ordersList').querySelector('img');
      assert.ok(image);
      assert.equal(image.getAttribute('src'), imageUrl);
      assert.equal(image.getAttribute('alt'), title);
      assert.equal(image.getAttribute('referrerpolicy'), 'no-referrer');
      assert.equal(image.hasAttribute('onerror'), false);
    };
    assertCover();
    app.trigger(app.get('orderTabs').querySelector('[data-tab="CHO_XU_LY"]'));
    assertCover();

    const image = app.get('ordersList').querySelector('img');
    app.trigger(image, 'error');
    assert.equal(image.classList.contains('d-none'), true);
    assert.equal(image.parentNode.classList.contains('mini-cover'), true);
  });

  test(page + ' keeps a placeholder when the cover is missing or unsafe', async () => {
    for (const url of [null, '', 'javascript:alert(1)', '//example.com/cover.jpg']) {
      const app = orderApp(seller, url);
      await settle();
      assert.equal(app.get('ordersList').querySelector('img'), null);
      assert.ok(app.get('ordersList').querySelector('.mini-cover'));
      assert.ok(app.get('ordersList').textContent.includes(title));
    }
  });

  test(page + ' supports local cover URLs', async () => {
    const app = orderApp(seller, '/images/book.jpg');
    await settle();
    assert.equal(app.get('ordersList').querySelector('img').getAttribute('src'), '/images/book.jpg');
  });
}
