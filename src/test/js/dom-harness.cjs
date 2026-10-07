// Merged from both test-harness variants.
// Keeps the superset of capabilities: fragment inclusion, inline-script execution,
// configurable test location, sessionToken helper, and the shared DOM shim.

'use strict';

// A dependency-free DOM shim populated from the application's real templates.
// It supports the form/event APIs used by these pages; it does not test layout.
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const root = path.resolve(__dirname, '../../..');
const decode = text => String(text).replace(/&(?:amp|lt|gt|quot|#39|nbsp);/g, entity => ({
  '&amp;': '&', '&lt;': '<', '&gt;': '>', '&quot;': '"', '&#39;': "'", '&nbsp;': ' '
}[entity]));

class Element {
  constructor(tag, owner, attributes = {}) {
    this.tagName = tag.toUpperCase();
    this.ownerDocument = owner;
    this.attributes = attributes;
    this.children = [];
    this.parentNode = null;
    this.listeners = new Map();
    this.style = {};
    this._value = attributes.value || '';
    this._text = '';
    this._html = '';
    this.disabled = 'disabled' in attributes;
    this.checked = 'checked' in attributes;
    this.customValidity = '';
    this.classList = {
      contains: name => this.className.split(/\s+/).includes(name),
      add: (...names) => { this.className = [...new Set(this.className.split(/\s+/).filter(Boolean).concat(names))].join(' '); },
      remove: (...names) => { this.className = this.className.split(/\s+/).filter(name => name && !names.includes(name)).join(' '); },
      toggle: (name, force) => {
        const add = force === undefined ? !this.classList.contains(name) : !!force;
        this.classList[add ? 'add' : 'remove'](name);
        return add;
      }
    };
  }
  get id() { return this.attributes.id || ''; }
  set id(value) { this.attributes.id = value; }
  get className() { return this.attributes.class || ''; }
  set className(value) { this.attributes.class = String(value); }
  get required() { return this.hasAttribute('required'); }
  set required(value) { if (value) { this.setAttribute('required', ''); } else { this.removeAttribute('required'); } }
  get value() {
    if (this.tagName === 'SELECT') {
      if (this._selection !== undefined) { return this._selection; }
      const option = this.querySelector('option[selected]') || this.querySelector('option');
      return option ? option.getAttribute('value') || option.textContent : '';
    }
    return this._value;
  }
  set value(value) {
    if (this.tagName === 'SELECT') { this._selection = String(value); }
    else { this._value = String(value); }
  }
  get textContent() { return this._text + this.children.map(child => child.textContent).join(''); }
  set textContent(text) { this._text = String(text); this.children = []; this._html = ''; }
  get innerHTML() { return this._html; }
  set innerHTML(html) { this._html = String(html); this._text = ''; this.children = []; parseInto(this, this._html); }
  get elements() {
    const controls = this.querySelectorAll('input, select, textarea, button, fieldset');
    for (const control of controls) {
      const name = control.getAttribute('name') || control.id;
      if (name) { controls[name] = control; }
    }
    return controls;
  }
  get dataset() {
    const result = {};
    for (const [key, value] of Object.entries(this.attributes)) {
      if (key.startsWith('data-')) { result[key.slice(5).replace(/-([a-z])/g, (_, letter) => letter.toUpperCase())] = value; }
    }
    return result;
  }
  getAttribute(name) { return Object.hasOwn(this.attributes, name) ? this.attributes[name] : null; }
  setAttribute(name, value) { this.attributes[name] = String(value); }
  removeAttribute(name) { delete this.attributes[name]; }
  hasAttribute(name) { return Object.hasOwn(this.attributes, name); }
  appendChild(child) { child.parentNode = this; this.children.push(child); return child; }
  removeChild(child) { this.children.splice(this.children.indexOf(child), 1); child.parentNode = null; }
  addEventListener(type, callback) {
    if (!this.listeners.has(type)) { this.listeners.set(type, []); }
    this.listeners.get(type).push(callback);
  }
  dispatchEvent(event) {
    if (!event.target) { event.target = this; }
    event.currentTarget = this;
    for (const listener of this.listeners.get(event.type) || []) {
      listener.call(this, event);
      if (event.immediateStopped) { break; }
    }
    if (event.bubbles !== false && !event.stopped && this.parentNode) { this.parentNode.dispatchEvent(event); }
    return !event.defaultPrevented;
  }
  matches(selector) { return selector.split(',').some(part => matchesSelector(this, part.trim())); }
  closest(selector) {
    for (let element = this; element && element.tagName; element = element.parentNode) {
      if (element.matches(selector)) { return element; }
    }
    return null;
  }
  querySelectorAll(selector) {
    const found = [];
    const walk = parent => {
      for (const child of parent.children) {
        if (child.matches(selector)) { found.push(child); }
        walk(child);
      }
    };
    walk(this);
    return found;
  }
  querySelector(selector) { return this.querySelectorAll(selector)[0] || null; }
  focus() { this.ownerDocument.activeElement = this; }
  setCustomValidity(message) { this.customValidity = message; }
  reportValidity() {
    if (this.tagName === 'FORM') { return this.elements.every(control => control.reportValidity()); }
    if (!['INPUT', 'SELECT', 'TEXTAREA'].includes(this.tagName) || this.disabled) { return true; }
    if (this.customValidity || (this.hasAttribute('required') && !this.value)) { return false; }
    const minimum = this.getAttribute('min');
    const maximum = this.getAttribute('max');
    const pattern = this.getAttribute('pattern');
    if (this.value && minimum !== null && Number(this.value) < Number(minimum)) { return false; }
    if (this.value && maximum !== null && Number(this.value) > Number(maximum)) { return false; }
    if (this.value && pattern && !(new RegExp('^(?:' + pattern + ')$')).test(this.value)) { return false; }
    if (this.value && this.getAttribute('type') === 'email' && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(this.value)) { return false; }
    if (this.value && this.getAttribute('minlength') && this.value.length < Number(this.getAttribute('minlength'))) { return false; }
    return true;
  }
  reset() {
    for (const control of this.elements) {
      if (['INPUT', 'TEXTAREA'].includes(control.tagName)) { control.value = control.getAttribute('value') || ''; }
      if (control.tagName === 'SELECT') { delete control._selection; }
      control.customValidity = '';
    }
  }
}

function matchesSimple(element, selector) {
  const tag = selector.match(/^[a-zA-Z][\w-]*/);
  if (tag && element.tagName !== tag[0].toUpperCase()) { return false; }
  const id = selector.match(/#([\w-]+)/);
  if (id && element.id !== id[1]) { return false; }
  for (const match of selector.matchAll(/\.([\w-]+)/g)) {
    if (!element.classList.contains(match[1])) { return false; }
  }
  for (const match of selector.matchAll(/\[([^\]=\s]+)(?:\s*=\s*["']?([^\]"']*)["']?)?\]/g)) {
    if (!element.hasAttribute(match[1]) || (match[2] !== undefined && element.getAttribute(match[1]) !== match[2])) { return false; }
  }
  return true;
}

function matchesSelector(element, selector) {
  const parts = selector.split(/\s+(?![^\[]*\])/);
  if (!matchesSimple(element, parts.pop())) { return false; }
  let ancestor = element.parentNode;
  while (parts.length) {
    const part = parts.pop();
    while (ancestor && (!ancestor.tagName || !matchesSimple(ancestor, part))) { ancestor = ancestor.parentNode; }
    if (!ancestor) { return false; }
    ancestor = ancestor.parentNode;
  }
  return true;
}

function parseInto(parent, html) {
  const stack = [parent];
  const voidTags = new Set(['INPUT', 'IMG', 'LINK', 'META', 'BR', 'HR', 'SOURCE']);
  for (const token of html.match(/<!--[\s\S]*?-->|<![^>]*>|<(?:[^>"']|"[^"]*"|'[^']*')+>|[^<]+/g) || []) {
    if (token.startsWith('<!')) { continue; }
    if (token.startsWith('</')) { if (stack.length > 1) { stack.pop(); } continue; }
    if (token.startsWith('<')) {
      const match = token.match(/^<([\w-]+)([\s\S]*?)\/?\s*>$/);
      if (!match) { continue; }
      const attributes = {};
      for (const attr of match[2].matchAll(/([^\s=]+)(?:\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s]+)))?/g)) {
        attributes[attr[1]] = decode(attr[2] ?? attr[3] ?? attr[4] ?? '');
      }
      const child = new Element(match[1], parent.ownerDocument, attributes);
      stack[stack.length - 1].appendChild(child);
      if (!voidTags.has(child.tagName) && !token.endsWith('/>')) { stack.push(child); }
    } else { stack[stack.length - 1]._text += decode(token); }
  }
}

class Document extends Element {
  constructor(html) {
    super('document', null);
    this.ownerDocument = this;
    this.readyState = 'complete';
    parseInto(this, html);
    this.body = this.querySelector('body');
  }
  getElementById(id) { return this.querySelector('#' + id); }
  createElement(tag) { return new Element(tag, this); }
}

function event(type, extra = {}) {
  return {
    type, bubbles: true, defaultPrevented: false,
    preventDefault() { this.defaultPrevented = true; },
    stopPropagation() { this.stopped = true; },
    stopImmediatePropagation() { this.stopped = true; this.immediateStopped = true; },
    ...extra
  };
}

function sessionToken(role, claims = {}) {
  const payload = { maND: 1, maTK: 2, vaiTro: role, exp: Math.floor(Date.now() / 1000) + 3600, ...claims };
  return 'header.' + Buffer.from(JSON.stringify(payload)).toString('base64url') + '.signature';
}

function boot(template, scripts, handler, role = 'KHACH_HANG', options = {}) {
  let html = fs.readFileSync(path.join(root, 'src/main/resources/templates', template), 'utf8');
  if (options.includeFragments) {
    for (const name of ['header', 'footer']) {
      const fragment = fs.readFileSync(path.join(root, 'src/main/resources/templates/fragments', name + '.html'), 'utf8');
      const contents = fragment.match(new RegExp('<' + name + '\\b[\\s\\S]*?</' + name + '>'))[0];
      html = html.replace(new RegExp('<div th:replace="~\\{fragments/' + name + ' :: ' + name + '\\}"></div>'), contents);
    }
  }
  const document = new Document(html.replace(/<script\b[^>]*>[\s\S]*?<\/script>/gi, ''));
  if (options.setupDocument) { options.setupDocument(document); }
  const calls = [];
  const redirects = [];
  const windowListeners = new Map();
  const storage = new Map(role ? [['oldbook_token', sessionToken(role)]] : []);
  class Modal {
    constructor(element) { this.element = element; }
    show() { this.element.classList.add('show'); this.element.dispatchEvent(event('shown.bs.modal')); }
    hide() {
      if (this.element.dispatchEvent(event('hide.bs.modal'))) {
        this.element.classList.remove('show'); this.element.dispatchEvent(event('hidden.bs.modal'));
      }
    }
    static getOrCreateInstance(element) { return new Modal(element); }
  }
  const window = {
    document, bootstrap: { Modal },
    addEventListener(type, callback) {
      if (!windowListeners.has(type)) { windowListeners.set(type, []); }
      windowListeners.get(type).push(callback);
    },
    dispatchEvent(event) {
      for (const listener of windowListeners.get(event.type) || []) { listener.call(window, event); }
    },
    location: {
      origin: 'http://localhost:8080', pathname: options.pathname || '/test', search: options.search || '',
      assign: url => redirects.push(url), replace: url => redirects.push(url)
    },
    localStorage: { getItem: key => storage.get(key) || null, setItem: (key, value) => storage.set(key, value), removeItem: key => storage.delete(key) },
    atob: value => Buffer.from(value, 'base64').toString('binary'),
    setTimeout: () => 0, clearTimeout() {}, setInterval: () => 0, confirm: () => true,
    fetch: async (url, init) => {
      const call = { url, method: init.method, body: init.body === undefined ? undefined : JSON.parse(init.body), headers: { ...init.headers } };
      calls.push(call);
      const result = await handler(call, document);
      const status = result && result.httpStatus || 200;
      return { ok: status >= 200 && status < 300, status, text: async () => JSON.stringify({ code: status, data: result && result.data !== undefined ? result.data : result, message: result && result.message }) };
    }
  };
  const context = vm.createContext({ window, document, bootstrap: window.bootstrap, URL, URLSearchParams, console, setTimeout: window.setTimeout, clearTimeout: window.clearTimeout, setInterval: window.setInterval });
  for (const file of ['auth.js', ...scripts]) {
    vm.runInContext(fs.readFileSync(path.join(root, 'src/main/resources/static/js', file), 'utf8'), context, { filename: file });
  }
  if (options.runInline) {
    let index = 0;
    for (const match of html.matchAll(/<script\b([^>]*)>([\s\S]*?)<\/script>/gi)) {
      if (!/\bsrc\s*=/.test(match[1])) {
        vm.runInContext(match[2], context, { filename: template + ':inline-' + (++index) });
      }
    }
  }
  return {
    document, window, calls, redirects,
    get: id => document.getElementById(id),
    set(id, value) { const element = document.getElementById(id); if (!element) { throw new Error('Missing template element: ' + id); } element.value = value; },
    trigger(id, type = 'click') {
      const element = typeof id === 'string' ? document.getElementById(id) : id;
      if (!element) { throw new Error('Missing interaction target: ' + id); }
      element.dispatchEvent(event(type));
    }
  };
}

async function settle() { for (let round = 0; round < 5; round++) { await new Promise(resolve => setImmediate(resolve)); } }

module.exports = { boot, settle, event, sessionToken };
