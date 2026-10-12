'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const { boot, settle } = require('./dom-harness.cjs');

function catalogApp(books, options = {}, role = null) {
  return boot('books/catalog.html', ['book-cards.js', 'catalog.js', 'cart.js'], async call => {
    if (call.url === '/api/profile') { return { hoTen: 'Người mua' }; }
    if (call.url === '/api/order/cart' && call.method === 'GET') { return { soMuc: 0 }; }
    if (call.url === '/api/order/cart/items') { return { soMuc: 1 }; }
    if (call.url.startsWith('/api/sach')) { return books; }
    throw new Error('Unexpected API: ' + call.url);
  }, role, { pathname: '/', ...options });
}

function book(id, overrides = {}) {
  return {
    maSach: id, tenSach: 'Sách ' + id, tacGia: 'Tác giả', tenCuaHang: 'Cửa hàng',
    tenDanhMuc: 'Văn học', soLuongTon: 5, giaBanCu: id * 10000,
    ngayTao: '2026-10-01T10:00:00', ...overrides
  };
}

function ids(app, grid) {
  return app.get(grid).querySelectorAll('.book-title a').map(link => Number(link.getAttribute('href').split('/').pop()));
}

test('home shows the index sections with affordable, newest and book-set grids', async () => {
  const books = Array.from({ length: 10 }, (_, i) => book(i + 1, {
    tenDanhMuc: i >= 3 ? 'Bộ sách' : 'Văn học'
  }));
  books[0].soLuongTon = 0;
  books[1].ngayTao = '2026-10-03T10:00:00';
  const app = catalogApp(books);
  await settle();

  assert.equal(app.get('catalogTitle').textContent, 'Sách mới');
  assert.equal(app.get('catalogHomeIntro').classList.contains('d-none'), false);
  assert.equal(app.get('catalogHomeOutro').classList.contains('d-none'), false);
  assert.equal(app.get('catalogSeeAll').classList.contains('d-none'), false);
  assert.deepEqual(ids(app, 'cheapBookGrid'), [2, 3, 4, 5]);
  assert.deepEqual(ids(app, 'bookGrid'), [2, 10, 9, 8, 7, 6, 5, 4]);
  assert.deepEqual(ids(app, 'bookSetGrid'), [10, 9, 8, 7, 6, 5]);
  assert.equal(app.get('cheapBookGrid').children[0].className, 'col-6 col-md-3');
  assert.equal(app.get('bookGrid').children[0].className, 'col-6 col-md-4 col-lg-3');
  assert.equal(app.get('bookSetGrid').children[0].className, 'col-6 col-md-4 col-lg-2');
  assert.equal(app.get('catalogLoading').classList.contains('d-none'), true);
  assert.equal(app.calls.filter(call => call.url.startsWith('/api/sach')).length, 1);
  for (const id of ['cheapBooksSection', 'bookSetsSection']) {
    assert.equal(app.get(id).classList.contains('d-none'), false);
  }
});

test('home figures, shop list and quick category filter come from the loaded books', async () => {
  const books = [
    book(1, { tenCuaHang: 'Tiệm A', tenDanhMuc: 'Văn học', ngayTao: '2026-10-01T10:00:00' }),
    book(2, { tenCuaHang: 'Tiệm A', tenDanhMuc: 'Kinh tế', ngayTao: '2026-10-02T10:00:00' }),
    book(3, { tenCuaHang: 'Tiệm B', tenDanhMuc: 'Văn học', ngayTao: '2026-10-03T10:00:00' }),
    book(4, { tenCuaHang: '<b>Tiệm C</b>', tenDanhMuc: 'Văn học', ngayTao: '2026-10-04T10:00:00' })
  ];
  const app = catalogApp(books);
  await settle();

  assert.equal(app.get('homeStatBooks').textContent, '4');
  assert.equal(app.get('homeStatShops').textContent, '3');
  assert.equal(app.get('homeStats').classList.contains('d-none'), false);
  assert.equal(app.get('shopCount').textContent, '3 cửa hàng');
  const shops = app.get('shopList').querySelectorAll('.shop-network-item').map(item => item.textContent);
  assert.deepEqual(shops, ['Tiệm A2 đầu sách', '<b>Tiệm C</b>1 đầu sách', 'Tiệm B1 đầu sách']);
  assert.equal(app.get('shopList').querySelector('b'), null);

  const chips = app.get('newBooksFilter').querySelectorAll('[data-new-category]');
  assert.deepEqual(chips.map(chip => chip.textContent), ['Tất cả', 'Văn học', 'Kinh tế']);
  assert.equal(app.get('newBooksFilter').classList.contains('d-none'), false);
  assert.deepEqual(ids(app, 'bookGrid'), [4, 3, 2, 1]);

  app.trigger(chips[1]);
  assert.deepEqual(ids(app, 'bookGrid'), [4, 3, 1]);
  assert.deepEqual(chips.map(chip => chip.classList.contains('active')), [false, true, false]);
  assert.deepEqual(chips.map(chip => chip.getAttribute('aria-pressed')), ['false', 'true', 'false']);
  assert.equal(app.calls.filter(call => call.url.startsWith('/api/sach')).length, 1, 'filtering does not hit the server again');

  app.trigger(chips[0]);
  assert.deepEqual(ids(app, 'bookGrid'), [4, 3, 2, 1]);
});

test('cards show the discount against the cover price and warn when stock is low', async () => {
  const app = catalogApp([
    book(1, { giaBia: 100000, giaBanCu: 60000, soLuongTon: 2, doMoiPercent: 91.6, tenDanhMuc: 'Văn học' }),
    book(2, { giaBia: 50000, giaBanCu: 50000, soLuongTon: 9 }),
    book(3, { giaBia: null, giaBanCu: 30000, soLuongTon: 0 })
  ], { pathname: '/books' });
  await settle();

  const cards = app.get('bookGrid').querySelectorAll('.book-card');
  assert.equal(cards[0].querySelector('.price-off').textContent, '-40%');
  assert.equal(cards[0].querySelector('.price-old').textContent, '100.000 đ');
  assert.equal(cards[0].querySelector('.book-stock-note').textContent, 'Chỉ còn 2 cuốn');
  assert.equal(cards[0].querySelector('.condition-badge').textContent, 'Độ mới 92%');
  assert.equal(cards[0].querySelector('.book-category-tag').textContent, 'Văn học');
  assert.ok(cards[0].querySelector('[data-add-to-cart]'));

  // Giá bán không thấp hơn giá bìa, hoặc không có giá bìa: không hiện mức giảm.
  assert.equal(cards[1].querySelector('.price-off'), null);
  assert.equal(cards[1].querySelector('.book-stock-note'), null);
  assert.equal(cards[2].querySelector('.price-off'), null);
  assert.equal(cards[2].querySelector('.book-stock-note').textContent, 'Hết hàng');
  assert.equal(cards[2].querySelector('[data-add-to-cart]'), null);
});

test('all-books and filtered routes retain the complete catalog and API query', async () => {
  const books = Array.from({ length: 10 }, (_, i) => book(i + 1));
  for (const options of [
    { pathname: '/books' },
    { pathname: '/books', search: '?q=Tr%E1%BA%BB&category=V%C4%83n%20h%E1%BB%8Dc' },
    { pathname: '/', search: '?category=V%C4%83n%20h%E1%BB%8Dc' }
  ]) {
    const app = catalogApp(books, options);
    await settle();

    assert.equal(ids(app, 'bookGrid').length, 10);
    assert.equal(app.get('catalogHomeIntro').classList.contains('d-none'), true);
    assert.equal(app.get('catalogHomeOutro').classList.contains('d-none'), true);
    const api = new URL(app.calls.find(call => call.url.startsWith('/api/sach')).url, 'http://localhost');
    const query = new URLSearchParams(options.search || '');
    assert.equal(api.searchParams.get('q'), query.get('q'));
    assert.equal(api.searchParams.get('category'), query.get('category'));
    if (query.has('q')) {
      assert.equal(app.get('catalogTitle').textContent, 'Kết quả cho "Trẻ" trong Văn học');
    }
  }
});

test('empty sections are hidden entirely and catalog failures finish loading with their messages', async () => {
  const empty = catalogApp([]);
  await settle();
  assert.equal(empty.get('catalogEmpty').classList.contains('d-none'), false);
  // Khối không có sách thì ẩn cả tiêu đề, không để lại một khối rỗng.
  for (const id of ['cheapBooksSection', 'bookSetsSection', 'homeStats', 'newBooksFilter']) {
    assert.equal(empty.get(id).classList.contains('d-none'), true, id);
  }
  assert.equal(empty.get('catalogLoading').classList.contains('d-none'), true);

  const filtered = catalogApp([], { pathname: '/books', search: '?q=missing' });
  await settle();
  assert.equal(filtered.get('catalogEmptyText').textContent, 'Không tìm thấy sách phù hợp.');

  const failed = catalogApp({ httpStatus: 500, message: 'Không thể tải sách', data: null });
  await settle();
  assert.equal(failed.get('catalogLoading').classList.contains('d-none'), true);
  assert.equal(failed.get('catalogAlert').classList.contains('d-none'), false);
  assert.equal(failed.get('catalogAlert').textContent, 'Không thể tải sách');
});

test('home cards escape content, reject unsafe images and retain add-to-cart actions', async () => {
  const app = catalogApp([book(42, {
    tenSach: '<script>unsafe()</script>', tenDanhMuc: 'Bộ sách',
    hinhAnhUrl: 'javascript:unsafe()', tenCuaHang: '<img src=x onerror=unsafe()>'
  })], {}, 'KHACH_HANG');
  await settle();

  for (const id of ['cheapBookGrid', 'bookGrid', 'bookSetGrid']) {
    const grid = app.get(id);
    assert.equal(grid.querySelector('script'), null);
    assert.equal(grid.querySelector('img'), null);
    assert.equal(grid.querySelector('.book-title').textContent, '<script>unsafe()</script>');
    app.trigger(grid.querySelector('[data-add-to-cart]'));
    await settle();
  }
  const additions = app.calls.filter(call => call.url === '/api/order/cart/items');
  assert.equal(additions.length, 3);
  assert.ok(additions.every(call => call.body.maSach === 42 && call.body.soLuong === 1));
});
