// Optional developer test: node tools/store/tests/report.test.cjs <generated rapport.html>
const fs = require('node:fs');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const html = fs.readFileSync(process.argv[2], 'utf8');
const code = html.match(/<script>([\s\S]*?)<\/script>/)[1];
const ids = {};
function element(tag) {
  return { tag, children: [], textContent: '', value: '', files: [],
    append(...items) { this.children.push(...items); },
    replaceChildren(...items) { this.children = items; },
    setAttribute() {}, click() {} };
}
for (const name of ['identity','metrics','package','automatic','manual','export','import','notice']) ids[name] = element(name);
const blobs = [];
const storage = new Map();
const context = vm.createContext({
  document: {getElementById: id => ids[id], createElement: element, createTextNode: text => ({textContent:text})},
  localStorage: {getItem: key => storage.get(key), setItem: (key, value) => storage.set(key,value)},
  URL: {createObjectURL: blob => {blobs.push(blob); return 'blob:test';}, revokeObjectURL() {}},
  Blob, setTimeout() {}, console
});
(async () => {
  vm.runInContext(code, context);
  assert.equal(ids.manual.children.length,16,'all 16 manual scenarios must render');
  const automatic = vm.runInContext('JSON.stringify(report.Checks)', context);
  assert.equal(ids.automatic.children.length,JSON.parse(automatic).length);
  const detail = ids.manual.children[0];
  const select = detail.children[4].children[0], notes = detail.children[5];
  select.value='PASS'; select.onchange();
  ids.export.onclick(); assert.equal(blobs.length,0,'no undocumented manual PASS export');
  notes.value='Synthetic test observation'; notes.oninput(); ids.export.onclick();
  assert.equal(blobs.length,1);
  const exported=JSON.parse(await blobs[0].text());
  assert.equal(exported.Manual.length,16); assert.equal(exported.Manual[0].Status,'PASS');
  assert.equal(exported.Manual[1].Status,'NOT_RUN');
  ids.import.files=[{size:100,text:async()=>JSON.stringify({...exported,RunId:'other-run'})}];
  await ids.import.onchange(); assert.match(ids.notice.textContent,/ne correspond pas/);
  const accepted={...exported,Automatic:[],Manual:exported.Manual.map((x,i)=>i===0?{...x,Notes:'Restored observation'}:x)};
  ids.import.files=[{size:100,text:async()=>JSON.stringify(accepted)}]; await ids.import.onchange();
  assert.equal(ids.manual.children[0].children[5].value,'Restored observation');
  assert.equal(vm.runInContext('JSON.stringify(report.Checks)',context),automatic,'import must not overwrite automatic results');
  assert.equal(vm.runInContext('report.Manual.length',context),16);
  console.log('PASS: report rendering, all scenarios, documented export, matching import, immutable automatic evidence.');
})().catch(error=>{console.error(error);process.exitCode=1});