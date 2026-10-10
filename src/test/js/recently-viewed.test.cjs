'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const { boot, settle } = require('./dom-harness.cjs');

function book(id = 42, overrides = {}) {
  return {
    maSach: id, tenSach: 'Tôi Thấy Hoa Vàng Trên Cỏ Xanh', tacGia: 'Nguyễn Nhật Ánh',
    tenCuaHang: 'Hiệu sách cũ', soLuongTon: 2, giaBanCu: 50000, doMoiPercent: 95,
    ...overrides
  };
}

function appFor(template, historyHandler, options = {}) {
  return boot(template, ['book-cards.js', 'cart.js', 'wishlist.js', 'recently-viewed.js'], async call => {
    if (call.url === '/api/profile') { return { hoTen: 'Người mua' }; }
    if (call.url === '/api/order/cart' && call.method === 'GET') { return { soMuc: 0 }; }
    if (call.url === '/api/order/cart/items' && call.method === 'POST') { return { soMuc: 1 }; }
    if (call.url.startsWith('/api/wishlist')) {
      return options.wishlistHandler ? options.wishlistHandler(call) : [];
    }
    if (call.url.startsWith('/api/recently-viewed')) { return historyHandler(call); }
    throw new Error('Unexpected API: ' + call.url);
  }, Object.hasOwn(options, 'role') ? options.role : 'KHACH_HANG', {
    pathname: options.pathname || (template === 'books/detail.html' ? '/books/42' : '/wishlist'),
    includeFragments: true,
    setupDocument(document) {
      if (template === 'books/detail.html') {
        const main = document.querySelector('main');
        if (options.viewedBookId == null) { main.removeAttribute('data-viewed-book-id'); }
        else { main.setAttribute('data-viewed-book-id', options.viewedBookId); }
        document.getElementById('wishlistButton').setAttribute('data-ma-sach', '42');
      }
      if (options.setupDocument) { options.setupDocument(document); }
    }
  });
}

function historyCalls(app, method) {
  return app.calls.filter(call => call.url.startsWith('/api/recently-viewed') && (!method || call.method === method));
}

function wishlistCalls(app, method) {
  return app.calls.filter(call => call.url.startsWith('/api/wishlist') && (!method || call.method === method));
}

function shown(app, id) { return !app.get(id).classList.contains('d-none'); }
function favoriteHeart(app, id) {
  return app.get('wishlistGrid').querySelector('.book-cover-wrap [data-toggle-wishlist][data-ma-sach="' + id + '"]');
}

function assertNoHistoryHearts(app) {
  const grid = app.get('recentlyViewedGrid');
  assert.equal(grid.querySelectorAll('[data-toggle-wishlist], [data-remove-wishlist]').length, 0);
  assert.equal(grid.querySelectorAll('.wishlist-mini').length, 0);
  assert.equal(grid.querySelectorAll('.bi-heart, .bi-heart-fill, .bi-heartbreak').length, 0);
}

test('a rendered detail book is recorded once per visit for an authenticated customer', async () => {
  for (let visit = 0; visit < 2; visit++) {
    const app = appFor('books/detail.html', () => ({ data: null }), { viewedBookId: '42' });
    await settle();
    assert.equal(historyCalls(app).length, 1);
    assert.equal(historyCalls(app, 'PUT')[0].url, '/api/recently-viewed/42');
    assert.match(historyCalls(app)[0].headers.Authorization, /^Bearer /);
    assert.equal(historyCalls(app, 'GET').length, 0, 'detail pages only record their displayed book');
    assert.deepEqual(app.redirects, []);
  }
});

test('view tracking requires a valid rendered book marker and never infers a book from the URL', async () => {
  for (const viewedBookId of [undefined, '', '0', '-1', '42.5', 'not-a-book', '9007199254740992']) {
    const app = appFor('books/detail.html', () => { throw new Error('Invalid book must not be tracked'); }, { viewedBookId });
    await settle();
    assert.equal(historyCalls(app).length, 0, String(viewedBookId));
  }
  const irrelevant = appFor('books/catalog.html', () => { throw new Error('Unrelated page must not request history'); }, {
    pathname: '/books/42'
  });
  await settle();
  assert.equal(historyCalls(irrelevant).length, 0);
});

test('guests and other account roles do not record or request customer view history', async () => {
  for (const role of [null, 'CHU_CUA_HANG', 'QUAN_TRI_VIEN']) {
    for (const template of ['books/detail.html', 'wishlist.html']) {
      const app = appFor(template, () => { throw new Error('Ineligible history request'); }, { role, viewedBookId: '42' });
      await settle();
      assert.equal(historyCalls(app).length, 0, template + ': ' + role);
      if (role) { assert.equal(app.window.OldBook.auth.isLoggedIn(), true); }
    }
  }
});

test('a failed view record leaves book detail and its other actions usable', async () => {
  const app = appFor('books/detail.html', () => ({ httpStatus: 503, message: 'Chưa lưu được lịch sử', data: null }), {
    viewedBookId: '42', wishlistHandler: () => [book()]
  });
  await settle();
  assert.equal(historyCalls(app, 'PUT').length, 1);
  assert.equal(app.get('wishlistButton').disabled, false);
  assert.equal(app.get('wishlistButton').getAttribute('aria-pressed'), 'true');
  assert.equal(app.window.OldBook.auth.isLoggedIn(), true);
  assert.deepEqual(app.redirects, []);
});

test('history preserves newest-first server order, deduplicates books and limits the grid to twenty', async () => {
  const ordered = Array.from({ length: 25 }, (_, index) => book(100 - index));
  const app = appFor('wishlist.html', () => [null, book(0), ordered[0], ordered[0], ...ordered.slice(1)]);
  assert.equal(shown(app, 'recentlyViewedLoading'), true);
  await settle();
  const links = app.get('recentlyViewedGrid').querySelectorAll('.book-title a');
  assert.equal(links.length, 20);
  assert.deepEqual(links.map(link => link.getAttribute('href')), ordered.slice(0, 20).map(item => '/books/' + item.maSach));
  assert.equal(app.get('recentlyViewedCount').textContent, '20 cuốn');
  assert.equal(historyCalls(app, 'GET').length, 1);
  assert.equal(historyCalls(app, 'PUT').length, 0);
  assert.equal(shown(app, 'recentlyViewedLoading'), false);
  assert.equal(shown(app, 'recentlyViewedEmpty'), false);
  assert.equal(shown(app, 'recentlyViewedError'), false);
  assertNoHistoryHearts(app);
});

test('an empty history has its own empty state without hiding saved favorites', async () => {
  const app = appFor('wishlist.html', () => [], { wishlistHandler: () => [book()] });
  await settle();
  assert.equal(app.get('recentlyViewedCount').textContent, '0 cuốn');
  assert.equal(shown(app, 'recentlyViewedEmpty'), true);
  assert.equal(shown(app, 'recentlyViewedLoading'), false);
  assert.equal(shown(app, 'recentlyViewedError'), false);
  assert.equal(app.get('wishlistGrid').querySelectorAll('.book-card').length, 1);
  assert.equal(shown(app, 'wishlistEmpty'), false);
});

test('a history load failure shows retry rather than an empty result and blocks duplicate retries', async () => {
  let reads = 0;
  let completeRetry;
  const app = appFor('wishlist.html', () => {
    if (++reads === 1) { return { httpStatus: 503, message: 'Không tải được lịch sử', data: null }; }
    return new Promise(resolve => { completeRetry = resolve; });
  });
  await settle();
  assert.equal(shown(app, 'recentlyViewedError'), true);
  assert.match(app.get('recentlyViewedError').textContent, /Không tải được lịch sử/);
  assert.equal(shown(app, 'recentlyViewedRetry'), true);
  assert.equal(shown(app, 'recentlyViewedEmpty'), false);
  assert.equal(shown(app, 'recentlyViewedLoading'), false);
  app.trigger('recentlyViewedRetry');
  app.trigger('recentlyViewedRetry');
  assert.equal(historyCalls(app, 'GET').length, 2);
  assert.equal(app.get('recentlyViewedRetry').disabled, true);
  completeRetry([book()]);
  await settle();
  assert.equal(shown(app, 'recentlyViewedError'), false);
  assert.equal(shown(app, 'recentlyViewedRetry'), false);
  assert.equal(shown(app, 'recentlyViewedEmpty'), false);
  assert.equal(shown(app, 'recentlyViewedLoading'), false);
  assert.equal(app.get('recentlyViewedCount').textContent, '1 cuốn');
  assertNoHistoryHearts(app);
});

test('history cards escape untrusted text and cover URLs while retaining missing-data and sold-out books', async () => {
  const unsafe = ['javascript:alert(1)', 'data:image/svg+xml,<svg onload=alert(1)>', '//evil.example/image', '/\\evil.example/image'];
  const items = unsafe.map((hinhAnhUrl, index) => book(index + 1, {
    tenSach: '<script>alert(1)</script>', tacGia: '<img src=x onerror=alert(1)>',
    tenCuaHang: '"><img src=x onerror=alert(1)>', hinhAnhUrl
  }));
  items.push(book(9, { hinhAnhUrl: 'https://example.test/cover.jpg?q="quoted"' }));
  items.push(book(10, { soLuongTon: 0 }));
  items.push(book(11, { giaBanCu: null, tacGia: null, tenCuaHang: null, hinhAnhUrl: null }));
  const app = appFor('wishlist.html', () => items);
  await settle();
  const grid = app.get('recentlyViewedGrid');
  assert.equal(grid.querySelectorAll('script').length, 0);
  assert.equal(grid.querySelectorAll('img').length, 1);
  assert.equal(grid.querySelector('img').getAttribute('src'), 'https://example.test/cover.jpg?q="quoted"');
  assert.equal(grid.querySelectorAll('[onerror]').length, 0);
  assert.equal(grid.querySelector('.book-title').textContent, '<script>alert(1)</script>');
  const soldOut = grid.children[5];
  const missingPrice = grid.children[6];
  assert.equal(soldOut.querySelector('[data-add-to-cart]'), null);
  assert.match(soldOut.textContent, /Hết hàng/);
  assert.equal(missingPrice.querySelector('[data-add-to-cart]'), null);
  assert.match(missingPrice.textContent, /Chưa cập nhật/);
  assert.equal(soldOut.querySelector('a.btn').getAttribute('href'), '/books/10');
  assertNoHistoryHearts(app);
  assert.equal(app.get('recentlyViewedCount').textContent, '7 cuốn');
});

test('available history cards use the shared cart action without changing history', async () => {
  const app = appFor('wishlist.html', () => [book()]);
  await settle();
  app.trigger(app.get('recentlyViewedGrid').querySelector('[data-add-to-cart]'));
  await settle();
  const add = app.calls.find(call => call.url === '/api/order/cart/items');
  assert.deepEqual(add.body, { maSach: 42, soLuong: 1 });
  assert.equal(app.get('recentlyViewedCount').textContent, '1 cuốn');
  assert.equal(historyCalls(app).length, 1);
  assertNoHistoryHearts(app);
});

test('recent history remains free of hearts regardless of which list finishes loading first', async () => {
  for (const historyFirst of [true, false]) {
    let completeHistory;
    let completeWishlist;
    const app = appFor('wishlist.html', () => new Promise(resolve => { completeHistory = resolve; }), {
      wishlistHandler: () => new Promise(resolve => { completeWishlist = resolve; })
    });
    if (historyFirst) {
      completeHistory([book()]);
      await settle();
      assertNoHistoryHearts(app);
      completeWishlist([book()]);
    } else {
      completeWishlist([book()]);
      await settle();
      completeHistory([book()]);
    }
    await settle();
    assertNoHistoryHearts(app);
    const heart = favoriteHeart(app, 42);
    assert.equal(heart.disabled, false);
    assert.equal(heart.getAttribute('aria-pressed'), 'true');
    assert.equal(heart.classList.contains('active'), true);
    assert.ok(heart.querySelector('.bi-heart-fill'));
  }
});

test('removing a favorite through its cover heart keeps the same book in recent history without hearts', async () => {
  let favorites = [book()];
  const app = appFor('wishlist.html', () => [book()], {
    wishlistHandler(call) {
      if (call.method === 'GET') { return favorites; }
      if (call.method === 'DELETE') { favorites = []; return { data: null }; }
      throw new Error('Unexpected wishlist method');
    }
  });
  await settle();
  assert.equal(favoriteHeart(app, 42).getAttribute('aria-pressed'), 'true');
  assert.equal(app.get('wishlistGrid').querySelectorAll('.book-card').length, 1);
  assert.equal(app.get('wishlistGrid').querySelector('[data-remove-wishlist]'), null);
  assert.equal(app.get('recentlyViewedGrid').querySelector('[data-remove-wishlist]'), null);
  assert.equal(app.get('wishlistGrid').querySelector('.book-info [data-toggle-wishlist]'), null);
  assertNoHistoryHearts(app);
  app.trigger(favoriteHeart(app, 42).querySelector('i'));
  await settle();
  assert.equal(wishlistCalls(app, 'DELETE').length, 1);
  assert.equal(wishlistCalls(app, 'DELETE')[0].url, '/api/wishlist/42');
  assert.equal(wishlistCalls(app, 'PUT').length, 0);
  assert.equal(app.get('wishlistGrid').children.length, 0);
  assert.equal(shown(app, 'wishlistEmpty'), true);
  assert.equal(app.get('recentlyViewedGrid').querySelectorAll('.book-card').length, 1);
  assert.equal(app.get('recentlyViewedCount').textContent, '1 cuốn');
  assertNoHistoryHearts(app);
  assert.equal(app.get('recentlyViewedGrid').querySelector('.book-title a').getAttribute('href'), '/books/42');
  assert.equal(historyCalls(app).length, 1);
});

test('history loaded after a retry has no hearts while favorite cover hearts remain usable', async () => {
  let reads = 0;
  const app = appFor('wishlist.html', () => ++reads === 1 ? { httpStatus: 503, data: null } : [book()], {
    wishlistHandler: () => [book()]
  });
  await settle();
  app.trigger('recentlyViewedRetry');
  await settle();
  assertNoHistoryHearts(app);
  assert.equal(favoriteHeart(app, 42).getAttribute('aria-pressed'), 'true');
  assert.equal(favoriteHeart(app, 42).disabled, false);
});

test('restoring the wishlist from browser cache reloads newest views without a duplicate initial read', async () => {
  let current = [book(42), book(43)];
  let favorites = [book(42)];
  const app = appFor('wishlist.html', () => current.slice(), { wishlistHandler: () => favorites.slice() });
  await settle();
  assert.equal(favoriteHeart(app, 42).getAttribute('aria-pressed'), 'true');
  assertNoHistoryHearts(app);
  app.window.dispatchEvent({ type: 'pageshow', persisted: false });
  await settle();
  assert.equal(historyCalls(app, 'GET').length, 1, 'the normal initial pageshow must not load history twice');
  assert.equal(wishlistCalls(app, 'GET').length, 1);
  current = [book(44), book(42), book(43)];
  favorites = [book(44)];
  app.window.dispatchEvent({ type: 'pageshow', persisted: true });
  await settle();
  assert.equal(historyCalls(app, 'GET').length, 2);
  assert.deepEqual(app.get('recentlyViewedGrid').querySelectorAll('.book-title a').map(link => link.getAttribute('href')),
    ['/books/44', '/books/42', '/books/43']);
  assert.equal(app.get('recentlyViewedCount').textContent, '3 cuốn');
  assert.equal(wishlistCalls(app, 'GET').length, 2);
  assert.equal(favoriteHeart(app, 44).getAttribute('aria-pressed'), 'true');
  assert.equal(favoriteHeart(app, 42), null);
  assertNoHistoryHearts(app);
  assert.equal(app.get('wishlistGrid').querySelector('.book-title a').getAttribute('href'), '/books/44');
});

test('restoring a rendered detail from browser cache records its new visit without duplicate initial tracking', async () => {
  const app = appFor('books/detail.html', () => ({ data: null }), { viewedBookId: '42' });
  await settle();
  app.window.dispatchEvent({ type: 'pageshow', persisted: false });
  await settle();
  assert.equal(historyCalls(app, 'PUT').length, 1);
  app.window.dispatchEvent({ type: 'pageshow', persisted: true });
  await settle();
  assert.equal(historyCalls(app, 'PUT').length, 2);
  assert.ok(historyCalls(app, 'PUT').every(call => call.url === '/api/recently-viewed/42'));
  assert.equal(historyCalls(app, 'GET').length, 0);

  app.window.localStorage.removeItem('oldbook_token');
  app.window.dispatchEvent({ type: 'pageshow', persisted: true });
  await settle();
  assert.equal(historyCalls(app, 'PUT').length, 2, 'a cached detail cannot record after its user logs out');
});

test('cached pages restored for guests or other roles never request customer history', async () => {
  for (const role of [null, 'CHU_CUA_HANG', 'QUAN_TRI_VIEN']) {
    for (const template of ['books/detail.html', 'wishlist.html']) {
      const app = appFor(template, () => { throw new Error('Ineligible cached history request'); }, { role, viewedBookId: '42' });
      await settle();
      app.window.dispatchEvent({ type: 'pageshow', persisted: false });
      app.window.dispatchEvent({ type: 'pageshow', persisted: true });
      await settle();
      assert.equal(historyCalls(app).length, 0, template + ': ' + role);
    }
  }
});
