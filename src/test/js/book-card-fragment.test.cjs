'use strict';

// Thẻ sách có hai nơi dựng: js/book-cards.js (trang chủ, yêu thích, đã xem gần đây) và
// templates/fragments/book-card.html (kết quả tìm kiếm, sách cùng danh mục).
// Bài test này giữ cho hai bên cùng một cấu trúc, để thẻ không lệch kiểu giữa các trang.

const test = require('node:test');
const assert = require('node:assert/strict');
const { boot } = require('./dom-harness.cjs');

function cardApp() {
  return boot('fragments/book-card.html', ['book-cards.js'], async () => null, null);
}

// Bộ đọc template của harness tách "th:block" thành thẻ TH có thuộc tính ":block".
const isThymeleafBlock = element => element.tagName === 'TH' && element.hasAttribute(':block');

// Thuộc tính mà Thymeleaf sẽ sinh ra: th:href thành href, và các tên nằm trong th:attr="a=...,b=...".
function attributeNames(element) {
  const names = new Set();
  for (const [name, value] of Object.entries(element.attributes)) {
    if (name === 'class') { continue; }
    if (name === 'th:attr') {
      for (const part of value.split(/,(?![^{]*\})/)) { names.add(part.split('=')[0].trim()); }
    } else if (['th:href', 'th:src', 'th:alt', 'th:title'].includes(name)) {
      names.add(name.slice(3));
    } else if (!name.startsWith('th:')) {
      names.add(name);
    }
  }
  return [...names].sort();
}

// "thẻ.lớp[thuộc tính]" của mọi phần tử bên trong thẻ sách. Lớp màu bìa c0..c5 đổi theo mã sách nên bỏ qua.
function shape(card) {
  const found = new Set();
  const walk = parent => {
    for (const child of parent.children) {
      if (!isThymeleafBlock(child)) {
        const classes = child.className.split(/\s+/).filter(name => name && !/^c\d$/.test(name)).sort();
        found.add(child.tagName.toLowerCase() + classes.map(name => '.' + name).join('') + '[' + attributeNames(child).join(',') + ']');
      }
      walk(child);
    }
  };
  walk(card);
  return [...found].sort();
}

const text = (card, selector) => card.querySelectorAll(selector).map(element => element.textContent.trim());

test('the server-rendered card and the JavaScript card are built from the same elements', () => {
  const app = cardApp();
  const fragment = app.document.querySelector('article');
  assert.ok(fragment, 'fragments/book-card.html has the card');

  // Hai cuốn này đi qua mọi nhánh của thẻ: có ảnh, có giảm giá, sắp hết hàng; và không giá, hết hàng.
  const holder = app.document.createElement('div');
  holder.innerHTML = [
    { maSach: 7, tenSach: 'Sách đủ thông tin', tacGia: 'Tác giả', tenCuaHang: 'Tiệm sách', tenDanhMuc: 'Văn học',
      hinhAnhUrl: '/images/book.jpg', doMoiPercent: 92, tinhTrangVatLy: 'Bìa gọn', giaBia: 100000, giaBanCu: 60000, soLuongTon: 2 },
    { maSach: 8, tenSach: 'Sách thiếu thông tin', giaBanCu: null, soLuongTon: 0 }
  ].map(book => app.window.OldBook.bookCards.render(book)).join('');
  const rendered = holder.querySelectorAll('article');
  assert.equal(rendered.length, 2);

  const fromScript = [...new Set(rendered.flatMap(shape))].sort();
  assert.deepEqual(shape(fragment), fromScript);
});

test('both cards use the same fixed wording', () => {
  const app = cardApp();
  const fragment = app.document.querySelector('article');
  const holder = app.document.createElement('div');
  holder.innerHTML = app.window.OldBook.bookCards.render({ maSach: 7, tenSach: 'Sách', giaBanCu: 60000, soLuongTon: 5 }) +
    app.window.OldBook.bookCards.render({ maSach: 8, tenSach: 'Sách', giaBanCu: null, soLuongTon: 0 });

  for (const selector of ['[data-add-to-cart]', 'a.btn-outline-brown', '.book-price-row .text-muted']) {
    assert.deepEqual(text(fragment, selector), text(holder, selector), selector);
  }
  // Template có sẵn cả hai dòng tồn kho (th:if chọn một); dòng "Hết hàng" phải trùng với thẻ JavaScript.
  assert.ok(text(fragment, '.book-stock-note').includes(text(holder, '.book-stock-note')[0]));
  assert.equal(fragment.querySelector('[data-add-to-cart]').getAttribute('data-so-luong'), '1');
});
