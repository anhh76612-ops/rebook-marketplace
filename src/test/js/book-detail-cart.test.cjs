'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const { boot, settle } = require('./dom-harness.cjs');

function detailApp(addHandler, role = 'KHACH_HANG') {
  return boot('books/detail.html', ['cart.js'], async call => {
    if (call.url === '/api/profile') { return { hoTen: 'Người mua' }; }
    if (call.url === '/api/order/cart' && call.method === 'GET') { return { soMuc: 0 }; }
    if (call.url === '/api/order/cart/items' && call.method === 'POST') { return addHandler(call); }
    throw new Error('Unexpected API: ' + call.url);
  }, role, { pathname: '/books/42', includeFragments: true });
}

function buyNowButton(app) {
  const button = app.get('buyNowButton');
  assert.ok(button, 'book detail exposes a Buy now button');
  assert.ok(button.hasAttribute('data-add-to-cart'));
  assert.ok(button.hasAttribute('data-buy-now'));
  // The harness reads the source template, before Thymeleaf resolves this attribute.
  button.setAttribute('data-ma-sach', '42');
  return button;
}

test('buy now adds the book once and opens the cart only after the add succeeds', async () => {
  let completeAdd;
  const app = detailApp(() => new Promise(resolve => { completeAdd = resolve; }));
  await settle();
  const button = buyNowButton(app);

  app.trigger(button.querySelector('i') || button);
  app.trigger(button);

  const additions = app.calls.filter(call => call.url === '/api/order/cart/items');
  assert.equal(additions.length, 1, 'a pending request blocks duplicate clicks');
  assert.deepEqual(additions[0].body, { maSach: 42, soLuong: 1 });
  assert.equal(button.disabled, true);
  assert.deepEqual(app.redirects, [], 'cart navigation waits for the server response');

  completeAdd({ soMuc: 2 });
  await settle();

  assert.deepEqual(app.redirects, ['/cart']);
  assert.equal(button.disabled, false);
  assert.ok(app.document.querySelectorAll('.cart-count').every(badge => badge.textContent === '2'));
  assert.equal(app.get('ob-toast-holder'), null, 'buy now navigates directly after success');
});

test('buy now preserves the detail page and shows the server error when adding fails', async () => {
  const app = detailApp(() => ({ httpStatus: 400, message: 'Sách đã hết hàng', data: null }));
  await settle();
  const button = buyNowButton(app);

  app.trigger(button);
  await settle();

  assert.equal(app.calls.filter(call => call.url === '/api/order/cart/items').length, 1);
  assert.deepEqual(app.redirects, []);
  assert.equal(button.disabled, false);
  assert.match(app.get('ob-toast-holder').textContent, /Sách đã hết hàng/);
});

test('buy now sends guests to login with the detail URL and does not request the cart', async () => {
  const app = detailApp(() => { throw new Error('Guests must not add to the cart'); }, null);
  await settle();
  const button = buyNowButton(app);

  app.trigger(button);
  await settle();

  assert.deepEqual(app.redirects, ['/login?redirect=%2Fbooks%2F42']);
  assert.equal(app.calls.some(call => call.url.startsWith('/api/order/cart')), false);
  assert.equal(button.disabled, false);
});

test('regular add to cart retains its success toast without navigating away', async () => {
  const app = detailApp(() => ({ soMuc: 1 }));
  await settle();
  const button = app.document.querySelectorAll('[data-add-to-cart]')
    .find(element => !element.hasAttribute('data-buy-now'));
  assert.ok(button);
  button.setAttribute('data-ma-sach', '42');

  app.trigger(button);
  await settle();

  assert.deepEqual(app.redirects, []);
  assert.equal(button.disabled, false);
  assert.match(app.get('ob-toast-holder').textContent, /Đã thêm sách vào giỏ hàng/);
  assert.equal(app.get('ob-toast-holder').querySelector('a').href, '/cart');
  assert.ok(app.document.querySelectorAll('.cart-count').every(badge => badge.textContent === '1'));
});
