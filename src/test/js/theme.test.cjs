'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { boot, settle, event } = require('./dom-harness.cjs');

const KEY = 'oldbook_theme';
const source = fs.readFileSync(path.resolve(__dirname, '../../main/resources/static/js/theme.js'), 'utf8');

// theme.js chạy ở đầu header, trước mọi script khác, nên ở đây tự chạy nó sau khi đã đặt sẵn lựa chọn đã lưu.
function themedPage(saved, template = 'fragments/header.html') {
  const app = boot(template, [], async call => call.url === '/api/profile' ? { hoTen: 'Người dùng' } : null, null, {
    setupDocument: document => { document.documentElement = document.createElement('html'); }
  });
  if (saved !== undefined) { app.window.localStorage.setItem(KEY, saved); }
  app.run = () => vm.runInNewContext(source, { window: app.window, document: app.document }, { filename: 'theme.js' });
  app.run();
  app.html = app.document.documentElement;
  app.option = theme => app.document.querySelector('[data-theme-option="' + theme + '"]');
  app.current = () => app.document.querySelectorAll('[data-theme-option]')
    .filter(option => option.classList.contains('is-current')).map(option => option.getAttribute('data-theme-option'));
  return app;
}

test('a first-time visitor gets the default theme with no attribute on the page', () => {
  const app = themedPage();
  assert.equal(app.html.hasAttribute('data-theme'), false);
  assert.equal(app.html.hasAttribute('data-bs-theme'), false);
  assert.deepEqual(app.current(), ['default']);
  assert.equal(app.option('default').getAttribute('aria-checked'), 'true');
  assert.equal(app.option('dark').getAttribute('aria-checked'), 'false');
  assert.equal(app.window.OldBookTheme.get(), 'default');
});

test('the saved theme is applied as soon as the script runs, and only dark turns on the Bootstrap dark mode', () => {
  const dark = themedPage('dark');
  assert.equal(dark.html.getAttribute('data-theme'), 'dark');
  assert.equal(dark.html.getAttribute('data-bs-theme'), 'dark');
  assert.deepEqual(dark.current(), ['dark']);

  const magazine = themedPage('magazine');
  assert.equal(magazine.html.getAttribute('data-theme'), 'magazine');
  assert.equal(magazine.html.hasAttribute('data-bs-theme'), false);
  assert.deepEqual(magazine.current(), ['magazine']);
});

test('an unknown saved value falls back to the default theme', () => {
  for (const saved of ['neon', '', 'DARK', '<script>']) {
    const app = themedPage(saved);
    assert.equal(app.html.hasAttribute('data-theme'), false, saved);
    assert.deepEqual(app.current(), ['default'], saved);
  }
});

test('choosing a theme switches at once, remembers the choice, and default clears it', () => {
  const app = themedPage();

  app.trigger(app.option('magazine').querySelector('.theme-swatch'));
  assert.equal(app.html.getAttribute('data-theme'), 'magazine');
  assert.equal(app.window.localStorage.getItem(KEY), 'magazine');
  assert.deepEqual(app.current(), ['magazine']);

  app.trigger(app.option('dark'));
  assert.equal(app.html.getAttribute('data-theme'), 'dark');
  assert.equal(app.html.getAttribute('data-bs-theme'), 'dark');
  assert.equal(app.window.localStorage.getItem(KEY), 'dark');

  app.trigger(app.option('default'));
  assert.equal(app.html.hasAttribute('data-theme'), false);
  assert.equal(app.html.hasAttribute('data-bs-theme'), false);
  assert.equal(app.window.localStorage.getItem(KEY), null);
  assert.deepEqual(app.current(), ['default']);
});

test('a change made in another tab is picked up, and loading the script twice changes nothing', () => {
  const app = themedPage('dark');

  app.window.localStorage.setItem(KEY, 'magazine');
  app.window.dispatchEvent(event('storage', { key: KEY }));
  assert.equal(app.html.getAttribute('data-theme'), 'magazine');
  assert.equal(app.html.hasAttribute('data-bs-theme'), false);

  app.window.dispatchEvent(event('storage', { key: 'oldbook_token' }));
  assert.equal(app.html.getAttribute('data-theme'), 'magazine');

  // Trang hồ sơ nạp theme.js hai lần (một lần ở đầu trang, một lần khi header được chép ra từ <template>).
  const api = app.window.OldBookTheme;
  app.run();
  assert.equal(app.window.OldBookTheme, api);
  app.trigger(app.option('dark'));
  assert.equal(app.window.localStorage.getItem(KEY), 'dark', 'only one click handler is attached');
});

test('theme.js never creates window.OldBook, so auth.js still starts normally', async () => {
  const app = themedPage('dark');
  await settle();
  assert.equal(typeof app.window.OldBook.auth.getToken, 'function');
  assert.equal('auth' in app.window.OldBookTheme, false);
});

test('store header carries the full picker and the manager layout offer all three themes', () => {
  for (const [template, options] of [['fragments/store/header.html', ['default', 'dark', 'magazine']], ['fragments/manager/layout.html', ['default', 'dark', 'magazine']]]) {
    const app = themedPage(options.includes('magazine') ? 'magazine' : 'dark', template);
    assert.deepEqual(app.document.querySelectorAll('[data-theme-option]').map(option => option.getAttribute('data-theme-option')),
      options, template);
    assert.deepEqual(app.current(), [options.includes('magazine') ? 'magazine' : 'dark'], template);
  }
});
