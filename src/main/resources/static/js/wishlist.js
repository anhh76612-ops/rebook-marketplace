(function () {
  'use strict';

  var OB = window.OldBook;
  var grid = document.getElementById('wishlistGrid');
  var toggles = document.querySelectorAll('[data-toggle-wishlist]');
  if (!OB || !OB.bookCards || (!grid && !toggles.length && !document.getElementById('bookGrid')) || OB.wishlist) { return; }

  var Auth = OB.auth;
  var ROLE = OB.ROLE_CUSTOMER;
  var books = [];
  var known = false;
  var loading = false;
  var failedLoad = false;
  var pending = Object.create(null);
  var BASE = '/api/wishlist';

  var Wishlist = {
    get: function () { return OB.request('GET', BASE); },
    add: function (id) { return OB.request('PUT', BASE + '/' + encodeURIComponent(id)); },
    remove: function (id) { return OB.request('DELETE', BASE + '/' + encodeURIComponent(id)); },
    refreshButtons: updateButtons
  };
  OB.wishlist = Wishlist;

  function $(id) { return document.getElementById(id); }
  function visible(id, show) {
    var element = $(id);
    if (element) { element.classList.toggle('d-none', !show); }
  }

  function bookId(value) {
    var id = Number(value);
    return Number.isSafeInteger(id) && id > 0 ? id : null;
  }

  function isSaved(id) {
    return books.some(function (book) { return Number(book.maSach) === id; });
  }

  function updateButtons() {
    var buttons = document.querySelectorAll('[data-toggle-wishlist]');
    for (var i = 0; i < buttons.length; i++) {
      var button = buttons[i];
      var id = bookId(button.getAttribute('data-ma-sach'));
      var busy = loading || !!pending[id];
      button.disabled = busy;
      button.setAttribute('aria-busy', String(busy));
      var saved = known && isSaved(id);
      button.setAttribute('aria-pressed', String(saved));
      button.classList.toggle('active', saved);
      if (button.classList.contains('wishlist-mini')) {
        button.setAttribute('aria-label', saved ? 'Bỏ yêu thích sách' : 'Yêu thích sách');
        button.setAttribute('title', saved ? 'Bỏ yêu thích' : 'Yêu thích');
      }
      var heart = button.querySelector('i');
      if (heart) {
        heart.classList.toggle('bi-heart-fill', saved);
        heart.classList.toggle('bi-heart', !saved);
      }
      var label = button.querySelector('[data-wishlist-label]');
      if (label) { label.textContent = !known && failedLoad ? 'Thử lại' : saved ? 'Đã yêu thích' : 'Yêu thích'; }
    }
    if ($('wishlistRetry')) { $('wishlistRetry').disabled = loading; }
  }

  function render() {
    if (grid) {
      grid.innerHTML = books.map(function (book) { return OB.bookCards.render(book); }).join('');
    }
    if ($('wishlistCount')) { $('wishlistCount').textContent = books.length + ' cuốn'; }
    visible('wishlistEmpty', known && books.length === 0);
    updateButtons();
  }

  function clearError() {
    visible('wishlistError', false);
    visible('wishlistRetry', false);
  }

  function reportError(err, retry) {
    if (Auth.handleAuthError(err, ROLE)) { return; }
    var message = err.message || 'Không tải được danh sách yêu thích. Vui lòng thử lại.';
    if ($('wishlistError')) { $('wishlistError').textContent = message; }
    visible('wishlistError', true);
    visible('wishlistRetry', retry);
    OB.toast(message, 'danger', { duration: 5000 });
  }

  function load() {
    if (loading || Object.keys(pending).length) { return; }
    loading = true;
    clearError();
    visible('wishlistLoading', true);
    updateButtons();
    Wishlist.get().then(function (items) {
      books = (items || []).filter(function (book) { return book && bookId(book.maSach); });
      known = true;
      failedLoad = false;
      render();
    }, function (err) {
      failedLoad = true;
      reportError(err, true);
    }).then(function () {
      loading = false;
      visible('wishlistLoading', false);
      updateButtons();
    });
  }

  function mutate(id, remove) {
    pending[id] = true;
    clearError();
    updateButtons();
    (remove ? Wishlist.remove(id) : Wishlist.add(id)).then(function (book) {
      books = books.filter(function (item) { return Number(item.maSach) !== id; });
      if (!remove && book && bookId(book.maSach)) { books.unshift(book); }
      render();
      OB.toast(remove ? 'Đã bỏ sách khỏi danh sách yêu thích.' : 'Đã thêm sách vào danh sách yêu thích.', 'success',
        remove ? {} : { link: { text: 'Xem danh sách yêu thích', href: '/wishlist' } });
    }, function (err) {
      reportError(err, false);
    }).then(function () {
      delete pending[id];
      updateButtons();
    });
  }

  document.addEventListener('click', function (event) {
    var button = event.target.closest ? event.target.closest('[data-toggle-wishlist], #wishlistRetry') : null;
    if (!button) { return; }
    event.preventDefault();
    if (button.disabled) { return; }
    if (!Auth.isLoggedIn()) { Auth.redirectToLogin(); return; }
    if (!Auth.hasRole(ROLE)) {
      OB.toast('Chỉ tài khoản khách hàng mới sử dụng được danh sách yêu thích.', 'warning');
      return;
    }
    if (button.id === 'wishlistRetry' || !known) { load(); return; }
    var id = bookId(button.getAttribute('data-ma-sach'));
    if (!id) { OB.toast('Không xác định được sách cần yêu thích.', 'danger'); return; }
    if (pending[id]) { return; }
    mutate(id, isSaved(id));
  });

  function init() {
    if (!Auth.isLoggedIn()) { return; }
    if (!Auth.hasRole(ROLE)) {
      visible('wishlistLoading', false);
      visible('wishlistAccessError', true);
      return;
    }
    load();
  }

  window.addEventListener('pageshow', function (event) {
    if (event.persisted) { init(); }
  });

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
