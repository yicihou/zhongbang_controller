// 核对 app.json 注册的页面都有文件，并且代码里的跳转都能对上
const fs = require('fs');
const path = require('path');

const root = path.join(__dirname, '..');
const app = JSON.parse(fs.readFileSync(path.join(root, 'app.json'), 'utf8'));
const pages = new Set(app.pages);
let failed = 0;

function bad(msg) {
  failed += 1;
  console.log('  ❌', msg);
}

app.pages.forEach((p) => {
  ['js', 'wxml', 'json'].forEach((ext) => {
    const file = path.join(root, p + '.' + ext);
    if (!fs.existsSync(file)) bad('缺少文件 ' + p + '.' + ext);
  });
});
console.log('  页面', app.pages.length, '个，tabBar', app.tabBar.list.length, '项');

app.tabBar.list.forEach((tab) => {
  if (!pages.has(tab.pagePath)) bad('tabBar 未注册 ' + tab.pagePath);
});

function walk(dir, acc) {
  fs.readdirSync(dir).forEach((name) => {
    const p = path.join(dir, name);
    if (fs.statSync(p).isDirectory()) walk(p, acc);
    else if (/\.(js|wxml)$/.test(name)) acc.push(p);
  });
  return acc;
}

const files = walk(path.join(root, 'pages'), []).concat(walk(path.join(root, 'components'), []));
const re = /['"`]\/pages\/[A-Za-z0-9_\-\/]+/g;
files.forEach((file) => {
  const text = fs.readFileSync(file, 'utf8');
  const found = text.match(re) || [];
  found.forEach((raw) => {
    const url = raw.slice(1).split('?')[0];
    if (url.endsWith('/')) return;
    const page = url.replace(/^\//, '');
    if (!pages.has(page)) bad(path.relative(root, file) + ' 跳到未注册页面 ' + url);
  });
});

// 每个页面脚本至少能被 node 解析
app.pages.forEach((p) => {
  try {
    require('child_process').execFileSync(process.execPath, ['--check', path.join(root, p + '.js')], { stdio: 'pipe' });
  } catch (e) {
    bad('语法错误 ' + p + '.js');
  }
});

if (failed === 0) console.log('  ✅ 页面注册与跳转一致');
console.log(failed === 0 ? 'PASS' : 'FAIL ' + failed);
process.exit(failed === 0 ? 0 : 1);
