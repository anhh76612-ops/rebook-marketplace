'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const { boot, settle, sessionToken } = require('./dom-harness.cjs');
const page = items => ({ content: items, page: 0, size: 20, totalElements: items.length, totalPages: items.length ? 1 : 0 });
const profile = { hoTen: 'Người dùng dùng chung' };
const roleHomes = {
  KHACH_HANG: '/', CHU_CUA_HANG: '/vendor/books', QUAN_LY: '/store-moderation',
  QUAN_TRI_VIEN: '/admin/accounts', DON_VI_VAN_CHUYEN: '/'
};
const managementLinks = {
  CHU_CUA_HANG: [['/vendor/books', 'Cửa hàng của tôi']],
  QUAN_LY: [['/store-moderation', 'Kiểm duyệt cửa hàng và sách']],
  QUAN_TRI_VIEN: [['/admin/accounts', 'Quản lý tài khoản'], ['/store-moderation', 'Kiểm duyệt cửa hàng và sách']]
};
const purchaseLinks = [['/wishlist', 'Sản phẩm yêu thích'], ['/cart', 'Giỏ hàng'], ['/orders', 'Theo dõi đơn hàng']];
const accountPurchaseLinks = [['/orders', 'Đơn hàng của tôi'], ['/wishlist', 'Sản phẩm yêu thích'], ['/cart', 'Giỏ hàng']];

function shown(element) {
  for (let current = element; current; current = current.parentNode) {
    if (current.hidden || current.classList.contains('d-none')) { return false; }
  }
  return true;
}

function visibleRoleLinks(app) {
  return app.document.querySelectorAll('[data-roles]').filter(shown).map(element => {
    const anchor = element.querySelector('a');
    const target = anchor.getAttribute('href') || anchor.getAttribute('th:href').slice(2, -1);
    const label = anchor.textContent.replace(/\s+/g, ' ').trim().replace(/^0\s*/, '') || anchor.getAttribute('title');
    return [target, label];
  });
}

test('shared auth preserves a forbidden session and redirects only for unauthorized responses', () => {
  const app = boot('fragments/header.html', [], async () => null, 'CHU_CUA_HANG');
  const client = app.window.OldBook;
  const originalToken = client.auth.getToken();
  assert.equal(client.auth.handleAuthError(new client.ApiError(403, 'Không có quyền'), 'CHU_CUA_HANG'), false);
  assert.equal(client.auth.getToken(), originalToken);
  assert.deepEqual(app.redirects, []);
  assert.equal(client.auth.handleAuthError(new client.ApiError(401, 'Phiên hết hạn')), true);
  assert.equal(client.auth.getToken(), null);
  assert.deepEqual(app.redirects, ['/login?redirect=%2Ftest']);
});

test('header and footer expose actual destinations and labels for every role and retain guest shopping links', async () => {
  for (const role of [null, ...Object.keys(roleHomes)]) {
    const header = boot('fragments/header.html', [], async () => profile, role);
    const footer = boot('fragments/footer.html', [], async () => profile, role);
    await settle();
    const management = managementLinks[role] || [];
    const headerExpected = role === null ? purchaseLinks : role === 'KHACH_HANG'
      ? [...accountPurchaseLinks, ...purchaseLinks] : [...management, ...management];
    const footerExpected = role === null || role === 'KHACH_HANG'
      ? [purchaseLinks[0], accountPurchaseLinks[0], purchaseLinks[1]] : management;
    assert.deepEqual(visibleRoleLinks(header), headerExpected, 'header for ' + role);
    assert.deepEqual(visibleRoleLinks(footer), footerExpected, 'footer for ' + role);
    for (const app of [header, footer]) {
      assert.equal(app.window.OldBook.auth.homeUrl(), roleHomes[role] || '/');
      assert.ok(app.document.querySelectorAll('[data-auth="guest"]').every(element => shown(element) === !role));
      assert.ok(app.document.querySelectorAll('[data-auth="user"]').every(element => shown(element) === !!role));
      assert.ok(app.document.querySelectorAll('[data-roles]').every(element => element.hidden === element.classList.contains('d-none')));
    }
    assert.equal(header.document.querySelector('[data-user-role]').textContent, role ? header.window.OldBook.roleLabels[role] : '');
    assert.equal(header.document.querySelector('[data-user-name]').textContent, role ? profile.hoTen : 'Tài khoản');
    for (const selector of ['.topbar', '.user-func']) {
      assert.doesNotMatch(header.document.querySelector(selector).className, /\bd-(?:none|sm-none|md-none|lg-none|xl-none)\b/, 'mobile account access remains available');
    }
    if (!role) {
      for (const element of header.document.querySelectorAll('[data-allow-guest]')) {
        assert.ok(element.querySelector('a').hasAttribute('data-requires-login'));
      }
    }
  }
});

test('saving a different role updates navigation and role labels immediately, and logout restores guest navigation', async () => {
  const app = boot('fragments/header.html', [], async call => call.url === '/api/profile' ? profile : null, 'KHACH_HANG');
  await settle();
  const auth = app.window.OldBook.auth;
  for (const role of ['QUAN_TRI_VIEN', 'CHU_CUA_HANG', 'QUAN_LY', 'DON_VI_VAN_CHUYEN', 'KHACH_HANG']) {
    auth.saveSession({ token: sessionToken(role) });
    const management = managementLinks[role] || [];
    assert.deepEqual(visibleRoleLinks(app), role === 'KHACH_HANG' ? [...accountPurchaseLinks, ...purchaseLinks] : [...management, ...management]);
    assert.equal(app.document.querySelector('[data-user-role]').textContent, app.window.OldBook.roleLabels[role]);
    assert.equal(app.document.querySelector('[data-user-name]').textContent, app.window.OldBook.roleLabels[role]);
    assert.equal(auth.homeUrl(), roleHomes[role]);
  }
  const token = auth.getToken();
  app.trigger(app.document.querySelector('[data-action="logout"]'));
  await settle();
  assert.equal(auth.getToken(), null);
  assert.deepEqual(app.redirects, ['/']);
  assert.equal(app.calls.find(call => call.url === '/api/auth/logout').headers.Authorization, 'Bearer ' + token);
  app.window.OldBook.renderHeader();
  assert.deepEqual(visibleRoleLinks(app), purchaseLinks);
  assert.equal(app.document.querySelector('[data-user-role]').textContent, '');
});

test('switch role revokes the current session and returns to login for a new role selection', async () => {
  const app = boot('fragments/header.html', [], async call => call.url === '/api/profile' ? { ...profile, avatarUrl: '/images/avatar.jpg' } : null, 'QUAN_TRI_VIEN');
  await settle();
  const token = app.window.OldBook.auth.getToken();
  const switchRole = app.document.querySelector('[data-action="switch-role"]');
  assert.ok(shown(switchRole));
  app.trigger(switchRole.querySelector('i'));
  await settle();
  assert.deepEqual(app.redirects, ['/login']);
  assert.equal(app.window.OldBook.auth.getToken(), null);
  assert.equal(app.window.localStorage.getItem('oldbook_name'), null);
  assert.equal(app.window.localStorage.getItem('oldbook_avatar'), null);
  const logout = app.calls.find(call => call.url === '/api/auth/logout');
  assert.equal(logout.method, 'POST');
  assert.equal(logout.headers.Authorization, 'Bearer ' + token);
});

test('real admin API rejection preserves a forbidden session while unauthorized rejection forces login', async () => {
  for (const status of [403, 401]) {
    const app = boot('admin-accounts.html', ['admin-accounts.js'], async call => {
      if (call.url === '/api/profile') { return profile; }
      return { httpStatus: status, data: null, message: status === 403 ? 'Không có quyền' : 'Phiên hết hạn' };
    }, 'QUAN_TRI_VIEN');
    const token = app.window.OldBook.auth.getToken();
    await settle();
    assert.equal(app.calls.filter(call => call.url.startsWith('/api/admin/tai-khoan')).length, 1);
    if (status === 403) {
      assert.equal(app.window.OldBook.auth.getToken(), token);
      assert.deepEqual(app.redirects, []);
      assert.equal(app.get('accountsDenied').classList.contains('d-none'), false);
      assert.equal(app.get('accountsContent').classList.contains('d-none'), true);
    } else {
      assert.equal(app.window.OldBook.auth.getToken(), null);
      assert.deepEqual(app.redirects, ['/login?redirect=%2Ftest']);
    }
  }
});

function loginApp(role, search = '', multiRole = false) {
  return boot('auth/login.html', [], async call => {
    if (call.url === '/api/profile') { return profile; }
    if (call.url === '/api/auth/login') {
      return multiRole ? {
        canChonVaiTro: true, roleSelectionToken: 'role-selection-token',
        taiKhoans: Object.keys(roleHomes).map((vaiTro, index) => ({ maTK: index + 10, vaiTro }))
      } : { canChonVaiTro: false, token: sessionToken(role) };
    }
    if (call.url === '/api/auth/select-role') { return { token: sessionToken(role) }; }
    throw new Error('Unexpected login API: ' + call.url);
  }, null, { pathname: '/login', search, includeFragments: true, runInline: true });
}

async function submitLogin(app) {
  app.set('loginEmail', ' user@example.com ');
  app.set('loginPassword', 'Book12345');
  app.trigger('loginForm', 'submit');
  await settle();
  const login = app.calls.find(call => call.url === '/api/auth/login');
  assert.deepEqual(login.body, { email: 'user@example.com', matKhau: 'Book12345' });
  assert.equal(login.headers.Authorization, undefined);
}

test('actual single-role login script saves the session and defaults to each role home', async () => {
  for (const [role, target] of Object.entries(roleHomes)) {
    const app = loginApp(role);
    await submitLogin(app);
    assert.equal(app.window.OldBook.auth.getUser().vaiTro, role);
    assert.deepEqual(app.redirects, [target]);
    assert.equal(app.document.querySelector('[data-user-role]').textContent, app.window.OldBook.roleLabels[role]);
    assert.equal(app.calls.find(call => call.url === '/api/profile').headers.Authorization, 'Bearer ' + app.window.OldBook.auth.getToken());
  }
});

test('actual login script honors a safe redirect and falls back to role home for external redirects', async () => {
  for (const target of ['/books?q=java', 'https://example.com/steal', '//example.com/steal', '/\\example.com/steal', '']) {
    const app = loginApp('QUAN_TRI_VIEN', '?redirect=' + encodeURIComponent(target));
    assert.equal(app.get('loginNotice').classList.contains('d-none'), false);
    await submitLogin(app);
    assert.deepEqual(app.redirects, [target === '/books?q=java' ? target : '/admin/accounts']);
  }
});

test('actual multi-role picker selects the exact account using the selection token before redirecting by its final role', async () => {
  const cases = Object.entries(roleHomes).map(([role, target]) => [role, '', target]);
  cases.push(['CHU_CUA_HANG', '?redirect=%2Fbooks%3Fq%3Djava', '/books?q=java']);
  cases.push(['QUAN_LY', '?redirect=https%3A%2F%2Fexample.com', '/store-moderation']);
  for (const [role, search, target] of cases) {
    const app = loginApp(role, search, true);
    await submitLogin(app);
    assert.equal(app.window.OldBook.auth.getToken(), null);
    assert.deepEqual(app.redirects, []);
    assert.equal(app.get('rolePicker').classList.contains('d-none'), false);
    assert.equal(app.get('loginForm').classList.contains('d-none'), true);
    const buttons = app.get('roleList').querySelectorAll('button');
    assert.deepEqual(buttons.map(button => button.textContent), Object.keys(roleHomes).map(value => app.window.OldBook.roleLabels[value]));
    const index = Object.keys(roleHomes).indexOf(role);
    app.trigger(buttons[index]);
    await settle();
    const select = app.calls.find(call => call.url === '/api/auth/select-role');
    assert.deepEqual(select.body, { maTK: index + 10 });
    assert.equal(select.headers.Authorization, 'Bearer role-selection-token');
    assert.equal(app.window.OldBook.auth.getUser().vaiTro, role);
    assert.deepEqual(app.redirects, [target]);
  }
});

test('already authenticated login page uses its role home or a safe explicit redirect without posting credentials', async () => {
  for (const [search, target] of [['', '/vendor/books'], ['?redirect=%2Fbooks', '/books'], ['?redirect=%2F%2Fevil.example', '/vendor/books']]) {
    const app = boot('auth/login.html', [], async () => profile, 'CHU_CUA_HANG', { pathname: '/login', search, runInline: true });
    await settle();
    assert.deepEqual(app.redirects, [target]);
    assert.equal(app.calls.some(call => call.url === '/api/auth/login'), false);
  }
});

test('moderation escapes review data and rejects with a reason before refreshing the list', async () => {
  const store = { maCH: 7, tenCuaHang: '<script>unsafe()</script>', moTa: '<img src=x onerror=unsafe()>', diaChiCH: 'HCM', trangThaiDuyet: 'CHO_DUYET', lyDoTuChoi: '<b>lý do cũ</b>' };
  const app = boot('store-moderation.html', ['store-moderation.js'], async call => {
    if (call.url === '/api/profile') { return profile; }
    if (call.url.startsWith('/api/store/kiem-duyet/cua-hang?')) { return page(store.trangThaiDuyet === 'CHO_DUYET' ? [store] : []); }
    if (call.url.startsWith('/api/store/kiem-duyet/sach?')) { return page([]); }
    if (call.url === '/api/store/kiem-duyet/cua-hang/7/tu-choi') { store.trangThaiDuyet = 'TU_CHOI'; return store; }
    if (call.url === '/api/store/kiem-duyet/cua-hang/7') { return store; }
    throw new Error('Unexpected API: ' + call.url);
  }, 'QUAN_LY');
  await settle();
  assert.equal(app.get('storeReviewRows').querySelector('script'), null);
  assert.match(app.get('storeReviewRows').textContent, /<script>unsafe\(\)<\/script>/);
  app.trigger(app.get('storeReviewRows').querySelector('[data-review-id]'));
  await settle();
  assert.equal(app.get('reviewDetailBody').querySelector('img'), null);
  assert.match(app.get('reviewDetailBody').innerHTML, /&lt;b&gt;lý do cũ&lt;\/b&gt;/);
  app.set('reviewRejectReason', '   ');
  app.trigger('reviewRejectForm', 'submit');
  assert.equal(app.calls.filter(call => call.method === 'POST').length, 0);
  app.set('reviewRejectReason', 'a'.repeat(501));
  app.trigger('reviewRejectReason', 'input');
  app.trigger('reviewRejectForm', 'submit');
  assert.equal(app.calls.filter(call => call.method === 'POST').length, 0);
  app.set('reviewRejectReason', '  <b>Thiếu thông tin</b>  ');
  app.trigger('reviewRejectReason', 'input');
  app.trigger('reviewRejectForm', 'submit');
  app.trigger('reviewRejectForm', 'submit');
  assert.equal(app.calls.filter(call => call.method === 'POST').length, 1);
  assert.equal(app.get('reviewRejectButton').disabled, true);
  await settle();
  assert.deepEqual(app.calls.find(call => call.method === 'POST').body, { lyDo: '<b>Thiếu thông tin</b>' });
  assert.equal(app.calls.filter(call => call.url.startsWith('/api/store/kiem-duyet/cua-hang?')).length, 2);
  assert.equal(app.get('storeReviewEmpty').classList.contains('d-none'), false);
});

test('moderation approves a pending book through its API and guards access by role', async () => {
  const book = { maSach: 9, maCH: 7, tenSach: 'Sách cần duyệt', trangThaiDuyet: 'CHO_DUYET', hinhAnhUrl: 'javascript:unsafe()' };
  const app = boot('store-moderation.html', ['store-moderation.js'], async call => {
    if (call.url === '/api/profile') { return profile; }
    if (call.url.startsWith('/api/store/kiem-duyet/cua-hang?')) { return page([]); }
    if (call.url.startsWith('/api/store/kiem-duyet/sach?')) { return page(book.trangThaiDuyet === 'CHO_DUYET' ? [book] : []); }
    if (call.url === '/api/store/kiem-duyet/sach/9/duyet') { book.trangThaiDuyet = 'DA_DUYET'; return book; }
    if (call.url === '/api/store/kiem-duyet/sach/9') { return book; }
    throw new Error('Unexpected API: ' + call.url);
  }, 'QUAN_TRI_VIEN');
  await settle();
  app.trigger(app.get('bookReviewRows').querySelector('[data-review-id]'));
  await settle();
  assert.equal(app.get('reviewDetailBody').querySelector('img'), null, 'unsafe image protocol is not rendered');
  app.trigger('reviewApproveButton');
  await settle();
  const approval = app.calls.find(call => call.method === 'POST');
  assert.equal(approval.url, '/api/store/kiem-duyet/sach/9/duyet');
  assert.equal(approval.body, undefined);
  assert.equal(app.calls.filter(call => call.url.startsWith('/api/store/kiem-duyet/sach?')).length, 2);
  const forbidden = boot('store-moderation.html', ['store-moderation.js'], async call => {
    if (call.url === '/api/profile') { return profile; }
    throw new Error('Must not request moderation API');
  });
  await settle();
  assert.equal(forbidden.calls.some(call => call.url.startsWith('/api/store/kiem-duyet/')), false);
  assert.match(forbidden.get('moderationAccessError').textContent, /quyền kiểm duyệt/);
});

test('seller edits preserve optional content, omit stock, and update stock/sale separately', async () => {
  let book = {
    maSach: 41, maDM: 3, tenDanhMuc: 'Văn học', tenSach: 'Tên trước', tacGia: 'Tác giả', nhaXuatBan: 'NXB Trẻ', namXuatBan: 2020,
    giaBia: 150000, giaBanCu: 75000, doMoiPercent: 92.5, tinhTrangVatLy: 'Không rách trang', moTaChiTiet: 'Mô tả giữ lại',
    hinhAnhUrl: '/images/existing.jpg', soLuongTon: 12, trangThaiDuyet: 'DA_DUYET', trangThaiBan: 'DANG_BAN'
  };
  const app = boot('vendor-books.html', ['store.js'], async call => {
    if (call.url === '/api/profile') { return profile; }
    if (call.url === '/api/store/me') { return { maCH: 7, tenCuaHang: 'Cửa hàng', trangThaiDuyet: 'DA_DUYET' }; }
    if (call.url.startsWith('/api/store/me/sach?')) { return page([book]); }
    if (call.url === '/api/sach/danh-muc') { return [{ maDM: 3, tenDanhMuc: 'Văn học' }]; }
    if (call.url === '/api/store/me/sach/41') {
      if (call.method === 'PUT') { book = { ...book, ...call.body, trangThaiDuyet: 'CHO_DUYET' }; }
      return book;
    }
    if (call.url === '/api/store/me/sach/41/ton-kho') { book = { ...book, ...call.body }; return book; }
    if (call.url === '/api/store/me/sach/41/trang-thai-ban') { book = { ...book, ...call.body }; return book; }
    throw new Error('Unexpected API: ' + call.url);
  }, 'CHU_CUA_HANG');
  await settle();
  app.trigger(app.get('sellerBooksRows').querySelector('[data-book-action="edit"]'));
  await settle();
  assert.equal(app.get('bookDescription').value, 'Mô tả giữ lại');
  assert.equal(app.get('bookPublisher').value, 'NXB Trẻ');
  assert.equal(app.get('bookImageUrl').value, '/images/existing.jpg');
  assert.equal(app.get('bookInitialStock').disabled, true);
  app.set('bookTitle', 'Tên sau');
  app.trigger('sellerBookForm', 'submit');
  app.trigger('sellerBookForm', 'submit');
  assert.equal(app.calls.filter(call => call.method === 'PUT').length, 1, 'editing blocks duplicate saves');
  new app.window.bootstrap.Modal(app.get('sellerBookModal')).hide();
  assert.equal(app.get('sellerBookModal').classList.contains('show'), true, 'pending save blocks user dismissal');
  await settle();
  const update = app.calls.find(call => call.method === 'PUT');
  assert.deepEqual(update.body, {
    maDM: 3, tenSach: 'Tên sau', tacGia: 'Tác giả', nhaXuatBan: 'NXB Trẻ', namXuatBan: 2020,
    giaBia: 150000, giaBanCu: 75000, doMoiPercent: 92.5, tinhTrangVatLy: 'Không rách trang', moTaChiTiet: 'Mô tả giữ lại', hinhAnhUrl: '/images/existing.jpg'
  });
  assert.equal(Object.hasOwn(update.body, 'soLuongTon'), false);
  assert.equal(book.soLuongTon, 12);
  assert.equal(app.get('sellerBookModal').classList.contains('show'), false, 'successful save dismisses the modal');
  app.trigger(app.get('sellerBooksRows').querySelector('[data-book-action="stock"]'));
  app.set('stockQuantity', '5');
  app.trigger('sellerStockForm', 'submit');
  await settle();
  assert.equal(app.get('sellerStockModal').classList.contains('show'), false);
  app.trigger(app.get('sellerBooksRows').querySelector('[data-book-action="sale"]'));
  await settle();
  assert.deepEqual(app.calls.filter(call => call.method === 'PATCH').map(call => [call.url, call.body]), [
    ['/api/store/me/sach/41/ton-kho', { soLuongTon: 5 }],
    ['/api/store/me/sach/41/trang-thai-ban', { trangThaiBan: 'NGUNG_BAN' }]
  ]);
  assert.equal(app.calls.filter(call => call.url.startsWith('/api/store/me/sach?')).length, 4, 'each successful mutation refreshes the actual list');
});

test('seller preserves visibility of pending store books while preventing writes', async () => {
  const app = boot('vendor-books.html', ['store.js'], async call => {
    if (call.url === '/api/profile') { return profile; }
    if (call.url === '/api/store/me') { return { maCH: 7, tenCuaHang: 'Chờ duyệt', trangThaiDuyet: 'CHO_DUYET' }; }
    if (call.url.startsWith('/api/store/me/sach?')) { return page([{ maSach: 41, tenSach: 'Sách hiện có', soLuongTon: 4 }]); }
    throw new Error('Unexpected API: ' + call.url);
  }, 'CHU_CUA_HANG');
  await settle();
  assert.equal(app.get('sellerBooks').classList.contains('d-none'), false);
  assert.equal(app.get('addBook').disabled, true);
  assert.ok(app.get('sellerBooksRows').querySelectorAll('[data-book-action]').filter(button => button.getAttribute('data-book-action') !== 'view').every(button => button.disabled));
  app.trigger(app.get('sellerBooksRows').querySelector('[data-book-action="sale"]'));
  await settle();
  assert.ok(app.calls.every(call => call.method === 'GET'));
});

test('pending and rejected stores can read full private book details without enabling writes or loading categories', async () => {
  for (const storeStatus of ['CHO_DUYET', 'TU_CHOI']) {
    const book = {
      maSach: 41, maDM: 3, tenDanhMuc: 'Văn học', tenSach: 'Sách riêng', tacGia: 'Tác giả', nhaXuatBan: 'NXB Trẻ', namXuatBan: 2020,
      giaBia: 150000, giaBanCu: 75000, doMoiPercent: 92.5, tinhTrangVatLy: 'Không rách trang', moTaChiTiet: 'Mô tả đầy đủ của sách riêng',
      hinhAnhUrl: '/images/existing.jpg', soLuongTon: 4, trangThaiDuyet: 'TU_CHOI', trangThaiBan: 'NGUNG_BAN', lyDoTuChoi: '<script>Thiếu mô tả</script>'
    };
    const app = boot('vendor-books.html', ['store.js'], async call => {
      if (call.url === '/api/profile') { return profile; }
      if (call.url === '/api/store/me') { return { maCH: 7, tenCuaHang: 'Cửa hàng', trangThaiDuyet: storeStatus }; }
      if (call.url.startsWith('/api/store/me/sach?')) { return page([book]); }
      if (call.url === '/api/store/me/sach/41' && call.method === 'GET') { return book; }
      throw new Error('Readonly details must not request categories or write data: ' + call.method + ' ' + call.url);
    }, 'CHU_CUA_HANG');
    await settle();
    const view = app.get('sellerBooksRows').querySelector('[data-book-action="view"]');
    assert.ok(view, 'private detail action is rendered for ' + storeStatus);
    assert.equal(view.disabled, false);
    app.trigger(view);
    await settle();
    assert.equal(app.get('sellerBookModal').classList.contains('show'), true);
    assert.equal(app.get('bookDescription').value, book.moTaChiTiet);
    assert.equal(app.get('bookPublisher').value, book.nhaXuatBan);
    assert.equal(app.get('bookImageUrl').value, book.hinhAnhUrl);
    assert.equal(app.get('bookCategory').value, String(book.maDM));
    assert.equal(app.get('bookFields').disabled, true);
    assert.equal(app.get('saveBook').disabled, true);
    assert.equal(app.get('saveBook').classList.contains('d-none'), true);
    assert.equal(app.get('bookViewDetails').classList.contains('d-none'), false);
    assert.match(app.get('bookViewStock').textContent, /4/);
    assert.match(app.get('bookViewStatus').textContent, /Từ chối/);
    assert.match(app.get('bookViewRejection').textContent, /<script>Thiếu mô tả<\/script>/);
    assert.equal(app.get('bookViewRejection').querySelector('script'), null);
    app.trigger('sellerBookForm', 'submit');
    app.trigger('sellerStockForm', 'submit');
    app.trigger('addBook');
    app.trigger(app.get('sellerBooksRows').querySelector('[data-book-action="sale"]'));
    await settle();
    assert.equal(app.calls.filter(call => call.url === '/api/store/me/sach/41').length, 1);
    assert.ok(app.calls.every(call => call.method === 'GET'));
    assert.equal(app.calls.some(call => call.url === '/api/sach/danh-muc'), false);
  }
});

test('seller blocks another stock mutation until the successful write finishes refreshing its list', async () => {
  const book = { maSach: 41, tenSach: 'Sách', soLuongTon: 4, trangThaiBan: 'DANG_BAN', trangThaiDuyet: 'DA_DUYET' };
  let listRequests = 0;
  let releaseRefresh;
  const refreshPending = new Promise(resolve => { releaseRefresh = resolve; });
  const app = boot('vendor-books.html', ['store.js'], async call => {
    if (call.url === '/api/profile') { return profile; }
    if (call.url === '/api/store/me') { return { maCH: 7, tenCuaHang: 'Cửa hàng', trangThaiDuyet: 'DA_DUYET' }; }
    if (call.url.startsWith('/api/store/me/sach?')) {
      listRequests++;
      if (listRequests > 1) { await refreshPending; }
      return page([book]);
    }
    if (call.url === '/api/store/me/sach/41/ton-kho') { book.soLuongTon = call.body.soLuongTon; return book; }
    throw new Error('Unexpected API: ' + call.url);
  }, 'CHU_CUA_HANG');
  await settle();
  app.trigger(app.get('sellerBooksRows').querySelector('[data-book-action="stock"]'));
  app.set('stockQuantity', '6');
  app.trigger('sellerStockForm', 'submit');
  await settle();
  assert.equal(listRequests, 2);
  assert.equal(app.get('sellerStockModal').classList.contains('show'), false);
  assert.equal(app.get('saveStock').disabled, true);
  app.trigger('sellerStockForm', 'submit');
  assert.equal(app.calls.filter(call => call.method === 'PATCH').length, 1);
  releaseRefresh();
  await settle();
  assert.equal(app.get('saveStock').disabled, false);
  assert.equal(app.get('sellerBooksRows').querySelector('[data-book-action="stock"]').disabled, false);
});

test('moderation surfaces server rejection and recovers controls without reporting success', async () => {
  const book = { maSach: 9, maCH: 7, tenSach: 'Sách', trangThaiDuyet: 'CHO_DUYET' };
  const app = boot('store-moderation.html', ['store-moderation.js'], async call => {
    if (call.url === '/api/profile') { return profile; }
    if (call.url.startsWith('/api/store/kiem-duyet/cua-hang?')) { return page([]); }
    if (call.url.startsWith('/api/store/kiem-duyet/sach?')) { return page([book]); }
    if (call.url === '/api/store/kiem-duyet/sach/9') { return book; }
    if (call.url === '/api/store/kiem-duyet/sach/9/duyet') {
      return { httpStatus: 400, message: 'Cửa hàng phải được phê duyệt trước khi duyệt sách', data: null };
    }
    throw new Error('Unexpected API: ' + call.url);
  }, 'QUAN_LY');
  await settle();
  app.trigger(app.get('bookReviewRows').querySelector('[data-review-id]'));
  await settle();
  app.trigger('reviewApproveButton');
  await settle();
  assert.match(app.get('reviewDetailError').textContent, /Cửa hàng phải được phê duyệt/);
  assert.equal(app.get('reviewApproveButton').disabled, false);
  assert.equal(app.get('reviewDetailRetry').classList.contains('d-none'), false);
  assert.equal(app.get('reviewDetailModal').classList.contains('show'), true);
  assert.equal(app.document.getElementById('ob-toast-holder'), null);
  assert.equal(app.calls.filter(call => call.url.startsWith('/api/store/kiem-duyet/sach?')).length, 1);
});

test('admin consumes an array, paginates locally, safely displays details, and sends exact mutation contracts', async () => {
  let accounts = Array.from({ length: 12 }, (_, index) => ({
    maTK: index + 1, maND: index + 101, hoTen: index === 0 ? '<img src=x onerror=unsafe()>' : 'Tài khoản ' + index,
    email: 'user' + index + '@example.com', vaiTro: index === 1 ? 'QUAN_TRI_VIEN' : 'KHACH_HANG', trangThai: 'HOAT_DONG'
  }));
  const app = boot('admin-accounts.html', ['admin-accounts.js'], async call => {
    if (call.url === '/api/profile') { return profile; }
    if (call.method === 'GET') { return accounts; }
    if (call.method === 'POST') { accounts.push({ maTK: 20, maND: 120, ...call.body, trangThai: 'HOAT_DONG' }); return accounts.at(-1); }
    const id = Number(call.url.match(/tai-khoan\/(\d+)\//)[1]);
    const account = accounts.find(item => item.maTK === id);
    if (call.url.endsWith('/vai-tro')) { account.vaiTro = call.body.vaiTro; }
    if (call.url.endsWith('/khoa')) { account.trangThai = 'BI_KHOA'; }
    if (call.url.endsWith('/mo-khoa')) { account.trangThai = 'HOAT_DONG'; }
    return account;
  }, 'QUAN_TRI_VIEN');
  await settle();
  assert.equal(app.calls.find(call => call.url.startsWith('/api/admin/tai-khoan')).url, '/api/admin/tai-khoan');
  assert.equal(app.get('accountsRows').querySelectorAll('tr').length, 10);
  assert.equal(app.get('accountsRows').querySelector('img'), null);
  assert.equal(app.get('accountsRows').querySelector('[data-account-id="2"] [data-account-action="status"]').disabled, true, 'cannot self-lock');
  app.trigger('accountsNext');
  assert.equal(app.get('accountsRows').querySelectorAll('tr').length, 2);
  assert.equal(app.calls.filter(call => call.url.startsWith('/api/admin/tai-khoan')).length, 1, 'array pagination does not call server pagination');
  app.trigger('accountsPrevious');
  app.trigger(app.get('accountsRows').querySelector('[data-account-id="1"] [data-account-action="detail"]'));
  assert.equal(app.get('detailName').textContent, '<img src=x onerror=unsafe()>');
  assert.equal(app.get('detailName').querySelector('img'), null);
  app.set('accountKeyword', ' user ');
  app.set('accountRoleFilter', 'KHACH_HANG');
  app.set('accountStatusFilter', 'HOAT_DONG');
  app.trigger('accountsFilters', 'submit');
  await settle();
  const query = new URL(app.calls.at(-1).url, 'http://localhost').searchParams;
  assert.deepEqual(Object.fromEntries(query), { tuKhoa: 'user', vaiTro: 'KHACH_HANG', trangThai: 'HOAT_DONG' });
  app.trigger('createAccountButton');
  app.set('createAccountName', ' Người mới ');
  app.set('createAccountEmail', 'new@example.com');
  app.set('createAccountPhone', '');
  app.set('createAccountPassword', 'Book12345');
  app.set('createAccountRole', 'CHU_CUA_HANG');
  app.trigger('createAccountForm', 'submit');
  app.trigger('createAccountForm', 'submit');
  assert.equal(app.calls.filter(call => call.method === 'POST').length, 1);
  await settle();
  assert.deepEqual(app.calls.find(call => call.method === 'POST').body, { hoTen: 'Người mới', email: 'new@example.com', soDienThoai: null, matKhau: 'Book12345', vaiTro: 'CHU_CUA_HANG' });
  app.trigger(app.get('accountsRows').querySelector('[data-account-id="1"] [data-account-action="role"]'));
  app.set('accountNewRole', 'QUAN_LY');
  app.trigger('accountRoleForm', 'submit');
  await settle();
  app.trigger(app.get('accountsRows').querySelector('[data-account-id="1"] [data-account-action="status"]'));
  app.set('accountLockReason', '   ');
  app.trigger('accountStatusForm', 'submit');
  assert.equal(app.calls.filter(call => call.url.endsWith('/khoa')).length, 0);
  app.set('accountLockReason', ' Vi phạm nội quy ');
  app.trigger('accountStatusForm', 'submit');
  await settle();
  app.trigger(app.get('accountsRows').querySelector('[data-account-id="1"] [data-account-action="status"]'));
  app.trigger('accountStatusForm', 'submit');
  await settle();
  assert.deepEqual(app.calls.filter(call => call.method === 'PUT').map(call => [call.url, call.body]), [
    ['/api/admin/tai-khoan/1/vai-tro', { vaiTro: 'QUAN_LY' }],
    ['/api/admin/tai-khoan/1/khoa', { lyDo: 'Vi phạm nội quy' }],
    ['/api/admin/tai-khoan/1/mo-khoa', undefined]
  ]);
});

test('admin role guard blocks API access and self-role changes force reauthentication', async () => {
  const forbidden = boot('admin-accounts.html', ['admin-accounts.js'], async call => {
    if (call.url === '/api/profile') { return profile; }
    throw new Error('Must not query admin API');
  }, 'QUAN_LY');
  await settle();
  assert.equal(forbidden.calls.some(call => call.url.startsWith('/api/admin/tai-khoan')), false);
  assert.equal(forbidden.get('accountsDenied').classList.contains('d-none'), false);
  const self = { maTK: 2, hoTen: 'Quản trị viên', email: 'admin@example.com', vaiTro: 'QUAN_TRI_VIEN', trangThai: 'HOAT_DONG' };
  const app = boot('admin-accounts.html', ['admin-accounts.js'], async call => call.url === '/api/profile' ? profile : call.method === 'GET' ? [self] : { ...self, vaiTro: call.body.vaiTro }, 'QUAN_TRI_VIEN');
  await settle();
  app.trigger(app.get('accountsRows').querySelector('[data-account-action="role"]'));
  app.set('accountNewRole', 'QUAN_LY');
  app.trigger('accountRoleForm', 'submit');
  await settle();
  assert.equal(app.window.localStorage.getItem('oldbook_token'), null);
  assert.equal(app.redirects.at(-1), '/login?redirect=%2F');
  assert.equal(app.calls.filter(call => call.method === 'GET' && call.url.startsWith('/api/admin/tai-khoan')).length, 1);
});
