(function () {
  'use strict';
  if (location.protocol !== 'https:' || location.hostname !== 'erp.sxcpatna.edu.in' || (location.port && location.port !== '443')) return null;
  const visible = el => !!(el.getClientRects().length && getComputedStyle(el).visibility !== 'hidden');
  const text = el => (el && el.textContent || '').replace(/\s+/g, ' ').trim().slice(0, 500);
  // Never read input values, cookies, tokens, form submissions or browser storage.
  const documents = [document];
  document.querySelectorAll('iframe').forEach(frame => {
    try { if (visible(frame) && frame.contentDocument && frame.contentWindow.location.origin === location.origin) documents.push(frame.contentDocument); } catch (_) { /* Cross-origin frames are not read. */ }
  });
  const password = documents.some(doc => Array.from(doc.querySelectorAll('input[type="password"]')).some(visible));
  if (password) return {url: location.href, title: document.title, text: '', fields: {}, tables: [], links: [], hasPasswordInput: true};
  const fields = {};
  const tables = documents.flatMap(doc => Array.from(doc.querySelectorAll('table'))).filter(visible).slice(0, 20).map(table => {
    const rows = Array.from(table.rows).slice(0, 250).map(row => Array.from(row.cells).slice(0, 30).map(text));
    rows.filter(row => row.length >= 2 && row.length <= 8 && row.length % 2 === 0).forEach(row => {
      for (let i = 0; i < row.length; i += 2) if (row[i].length < 80) fields[row[i]] = row[i + 1];
    });
    const prior = table.previousElementSibling;
    const heading = table.closest('section, .panel, .card')?.querySelector('h1,h2,h3,h4,.panel-title,.card-title');
    return {context: [text(table.caption), prior && /^H[1-6]$/.test(prior.tagName) ? text(prior) : '', text(heading)].join(' '), rows};
  });
  documents.forEach(doc => {
    doc.querySelectorAll('dt,label,strong,b').forEach(label => {
      if (!visible(label) || text(label).length > 60 || !label.nextElementSibling) return;
      const next = label.nextElementSibling;
      if (!next.matches('input,textarea,select,[contenteditable]') && visible(next) && text(next).length < 200) fields[text(label)] = text(next);
    });
  });
  const bodyText = documents.map(doc => {
    const lines = [];
    const walker = doc.createTreeWalker(doc.body, 4);
    let node;
    while ((node = walker.nextNode()) && lines.length < 2000) {
      const p = node.parentElement;
      if (p && visible(p) && !p.closest('input,textarea,script,style,noscript,[contenteditable]')) {
        const line = node.textContent.trim(); if (line) lines.push(line);
      }
    }
    return lines.join('\n');
  }).join('\n').slice(0, 60000);
  // Collapsed menus may contain valid attendance links even before the student expands them.
  const links = documents.flatMap(doc => Array.from(doc.querySelectorAll('a[href]'))).filter(a => a.getAttribute('href').trim() !== '#' && /attendance|profile/i.test(text(a) + ' ' + a.getAttribute('href'))).map(a => ({label: text(a), url: a.href})).filter(a => {
    try { const u = new URL(a.url); return u.origin === 'https://erp.sxcpatna.edu.in'; } catch (_) { return false; }
  }).slice(0, 24);
  return {url: location.href, title: document.title, text: bodyText, fields, tables, links, hasPasswordInput: false};
})()
