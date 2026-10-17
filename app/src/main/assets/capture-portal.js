(function () {
  'use strict';
  if (location.protocol !== 'https:' || location.hostname !== 'erp.sxcpatna.edu.in' || (location.port && location.port !== '443')) return null;
  const visible = el => !!(el.getClientRects().length && getComputedStyle(el).visibility !== 'hidden');
  const text = el => (el && el.textContent || '').replace(/\s+/g, ' ').trim().slice(0, 500);
  // Never read input values, cookies, tokens, form submissions or browser storage.
  const password = Array.from(document.querySelectorAll('input[type="password"]')).some(visible);
  if (password) return {url: location.href, title: document.title, text: '', fields: {}, tables: [], links: [], hasPasswordInput: true};
  const fields = {};
  const tables = Array.from(document.querySelectorAll('table')).filter(visible).slice(0, 12).map(table => {
    const rows = Array.from(table.rows).slice(0, 250).map(row => Array.from(row.cells).slice(0, 30).map(text));
    rows.filter(row => row.length === 2 && row[0].length < 80).forEach(row => { fields[row[0]] = row[1]; });
    const prior = table.previousElementSibling;
    return {context: [text(table.caption), prior && /^H[1-6]$/.test(prior.tagName) ? text(prior) : ''].join(' '), rows};
  });
  document.querySelectorAll('dt').forEach(dt => { if (visible(dt) && dt.nextElementSibling && dt.nextElementSibling.tagName === 'DD') fields[text(dt)] = text(dt.nextElementSibling); });
  const clone = document.body.cloneNode(true);
  clone.querySelectorAll('input, textarea, script, style, noscript, [contenteditable]').forEach(el => el.remove());
  const bodyText = (clone.innerText || clone.textContent || '').slice(0, 60000);
  const links = Array.from(document.querySelectorAll('a[href]')).filter(visible).filter(a => /attendance|my profile|student profile/i.test(text(a))).map(a => ({label: text(a), url: a.href})).filter(a => {
    try { const u = new URL(a.url); return u.origin === 'https://erp.sxcpatna.edu.in'; } catch (_) { return false; }
  }).slice(0, 12);
  return {url: location.href, title: document.title, text: bodyText, fields, tables, links, hasPasswordInput: false};
})()
