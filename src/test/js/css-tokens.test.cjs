'use strict';

// Giữ cho css/style.css đổi theme được: màu chỉ khai báo ở :root và các khối theme,
// mỗi theme gán đủ biến màu, và chữ trên nền của từng theme đủ tương phản để đọc.

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const css = fs.readFileSync(path.resolve(__dirname, '../../main/resources/static/css/style.css'), 'utf8')
  .replace(/\/\*[\s\S]*?\*\//g, '')
  .replace(/@import\s+url\('[^']*'\)\s*;/g, '');

const THEMES = { dark: '[data-theme="dark"]', magazine: '[data-theme="magazine"]' };

// Mọi luật dạng "selector { khai báo }", kể cả luật nằm trong @media.
function rules() {
  const found = [];
  for (const match of css.matchAll(/([^{}]+)\{([^{}]*)\}/g)) {
    const selector = match[1].replace(/\s+/g, ' ').trim();
    const declarations = [];
    for (const part of match[2].split(/;(?![^()]*\))/)) {
      const colon = part.indexOf(':');
      if (colon > 0) { declarations.push([part.slice(0, colon).trim(), part.slice(colon + 1).replace(/\s+/g, ' ').trim()]); }
    }
    found.push({ selector, declarations });
  }
  return found;
}

function variablesOf(selector) {
  const variables = {};
  for (const rule of rules()) {
    if (rule.selector !== selector) { continue; }
    for (const [name, value] of rule.declarations) {
      if (name.startsWith('--')) { variables[name] = value; }
    }
  }
  return variables;
}

const root = variablesOf(':root');
const themeVariables = Object.fromEntries(Object.entries(THEMES).map(([name, selector]) => [name, variablesOf(selector)]));
const isVariableBlock = selector => selector === ':root' || Object.values(THEMES).includes(selector);

// Biến màu theo vai trò: mỗi theme phải tự gán. Tên --text (không gạch nối phía sau) là biến bảng màu cũ, không tính.
const ROLE_COLOR = /^--(surface-|text-|border-|footer-|cta|tag-|status-|danger|brand$|price$|star$|focus-ring$|shadow-color$)/;

test('colours are declared only in :root and the theme blocks', () => {
  const offenders = [];
  for (const rule of rules()) {
    if (isVariableBlock(rule.selector)) { continue; }
    for (const [property, value] of rule.declarations) {
      if (/#[0-9a-fA-F]{3,8}\b|rgba?\(/.test(value)) { offenders.push(rule.selector + ' { ' + property + ': ' + value + ' }'); }
    }
  }
  assert.deepEqual(offenders, [], 'dùng biến theo vai trò thay cho mã màu viết trực tiếp');
});

test('every theme assigns every role colour variable', () => {
  const required = Object.keys(root).filter(name => ROLE_COLOR.test(name));
  assert.ok(required.length > 40, 'tìm thấy nhóm biến theo vai trò trong :root');
  for (const [theme, variables] of Object.entries(themeVariables)) {
    const missing = required.filter(name => !(name in variables));
    assert.deepEqual(missing, [], 'theme ' + theme + ' còn thiếu biến');
  }
});

test('theme blocks only reassign variables that exist in :root', () => {
  for (const [theme, variables] of Object.entries(themeVariables)) {
    const unknown = Object.keys(variables).filter(name => !name.startsWith('--bs-') && !(name in root));
    assert.deepEqual(unknown, [], 'theme ' + theme + ' gán biến không có ở :root');
  }
});

// ---------------------------------------------------------------- độ tương phản

function resolve(value, variables, depth = 0) {
  assert.ok(depth < 30, 'vòng lặp var(): ' + value);
  const next = value.replace(/var\(\s*(--[\w-]+)\s*\)/g, (whole, name) => name in variables ? variables[name] : whole);
  return next === value ? value : resolve(next, variables, depth + 1);
}

// Trả về [r, g, b, a] của một màu viết bằng mã hex hoặc rgb()/rgba().
function parseColor(text) {
  const hex = text.match(/^#([0-9a-fA-F]{6})$/);
  if (hex) { return [0, 2, 4].map(i => parseInt(hex[1].slice(i, i + 2), 16)).concat(1); }
  const rgb = text.match(/^rgba?\(([^)]*)\)$/);
  assert.ok(rgb, 'không đọc được màu: ' + text);
  const parts = rgb[1].split(/[,\s/]+/).filter(Boolean).map(Number);
  return [parts[0], parts[1], parts[2], parts.length > 3 ? parts[3] : 1];
}

function luminance([r, g, b]) {
  const channel = value => {
    const c = value / 255;
    return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
  };
  return 0.2126 * channel(r) + 0.7152 * channel(g) + 0.0722 * channel(b);
}

// Tỉ lệ tương phản WCAG; chữ có độ trong suốt được trộn lên nền trước khi tính.
function contrast(foreground, background) {
  const [r, g, b, a] = foreground;
  const mixed = [r, g, b].map((channel, i) => channel * a + background[i] * (1 - a));
  const [light, dark] = [luminance(mixed), luminance(background)].sort((x, y) => y - x);
  return (light + 0.05) / (dark + 0.05);
}

// [chữ, nền]: chữ thường cần từ 4,5 trở lên.
const TEXT_PAIRS = [
  ['text-main', 'surface-page'], ['text-main', 'surface-card'], ['text-main', 'surface-soft'],
  ['text-soft', 'surface-page'], ['text-soft', 'surface-card'],
  ['text-heading', 'surface-page'], ['text-heading', 'surface-card'], ['text-strong', 'surface-card'],
  ['text-accent', 'surface-page'], ['price', 'surface-card'], ['danger', 'surface-card'],
  ['cta-strong-text', 'surface-card'], ['cta-strong-text', 'surface-soft'],
  ['text-on-brand', 'surface-brand'], ['text-on-inverse', 'surface-inverse'], ['text-on-inverse', 'surface-inverse-strong'],
  ['text-on-inverse', 'surface-ink'], ['text-on-inverse-soft', 'surface-inverse-strong'], ['text-on-inverse', 'danger-fill'],
  ['text-on-bar', 'surface-bar'], ['text-on-mark', 'surface-mark'],
  ['text-on-footer', 'footer-from'], ['text-on-footer', 'footer-to'], ['footer-accent', 'footer-from'],
  ['text-on-footer-strong', 'footer-to'],
  ['tag-text', 'tag-bg'], ['status-ok-text', 'status-ok-bg'], ['status-bad-text', 'status-bad-bg'],
  ['status-neutral-text', 'status-neutral-bg']
];

// Chữ đậm trên nút: từ 3 trở lên.
const BUTTON_PAIRS = [
  ['cta-text', 'cta'], ['cta-hover-text', 'cta-hover'], ['cta-text', 'cta-alt'], ['cta-text', 'cta-alt-hover'],
  ['text-on-inverse', 'cta-strong'], ['text-on-inverse', 'cta-strong-hover']
];

// Theme Mặc định giữ nguyên màu đang có theo yêu cầu, kể cả vài cặp vốn chưa đạt 4,5.
// Ghi lại ở đây để chúng không tệ thêm, và để ai sửa màu mặc định biết chỗ nào còn yếu.
const DEFAULT_KNOWN = {
  'text-accent/surface-page': 4.3,       // màu liên kết ô-liu trên nền trang
  'status-ok-text/status-ok-bg': 3.6,    // viên "hoàn tất" chữ ô-liu trên nền xanh nhạt
  'text-on-footer/footer-from': 4.1,     // chữ kem ở mép trên của footer (nền chuyển màu)
  'footer-accent/footer-from': 2.3       // tiêu đề cột footer ở mép trên của nền chuyển màu
};

for (const theme of ['default', 'dark', 'magazine']) {
  test('text stays readable on its background in the ' + theme + ' theme', () => {
    const variables = { ...root, ...(themeVariables[theme] || {}) };
    const color = name => parseColor(resolve('var(--' + name + ')', variables));
    const weak = [];

    const check = (pairs, minimum) => {
      for (const [foreground, background] of pairs) {
        const key = foreground + '/' + background;
        const required = theme === 'default' && key in DEFAULT_KNOWN ? DEFAULT_KNOWN[key] : minimum;
        const ratio = contrast(color(foreground), color(background));
        if (ratio < required) { weak.push(key + ' = ' + ratio.toFixed(2) + ' (cần ' + required + ')'); }
      }
    };
    check(TEXT_PAIRS, 4.5);
    check(BUTTON_PAIRS, 3);

    assert.deepEqual(weak, []);
  });
}

test('the magazine theme is square and flat, the others are not', () => {
  const magazine = themeVariables.magazine;
  const radii = Object.keys(root).filter(name => name.startsWith('--radius-') && name !== '--radius-round');
  assert.ok(radii.length >= 8);
  for (const name of radii) {
    assert.equal(magazine[name], '0', name);
    assert.notEqual(root[name], '0', name + ' mặc định vẫn bo góc');
  }
  // Ảnh đại diện vẫn tròn; bóng tắt bằng một biến duy nhất.
  assert.equal('--radius-round' in magazine, false);
  assert.equal(magazine['--shadow-color'], 'transparent');
  for (const name of ['--bs-border-radius', '--bs-border-radius-sm', '--bs-border-radius-lg', '--bs-border-radius-pill']) {
    assert.equal(magazine[name], '0', name);
  }
  assert.equal(radii.some(name => name in themeVariables.dark), false, 'theme tối không đổi hình khối');
});
