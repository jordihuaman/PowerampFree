// evidence-only: list exported dynamic symbols (JNI + DSP entry points) of a native ELF .so
// usage: node tools/elf_syms.js <path.so> [filterRegex]
const fs = require('fs');
const path = process.argv[2];
const filter = process.argv[3] ? new RegExp(process.argv[3]) : null;
const b = fs.readFileSync(path);

const is64 = b.readUInt8(4) === 2;
if (!is64) throw new Error('expecting ELF64');
const e_shoff = b.readBigUInt64LE(0x28);
const e_shentsize = b.readUInt16LE(0x3a);
const e_shnum = b.readUInt16LE(0x3c);

const sections = [];
for (let i = 0; i < e_shnum; i++) {
  const off = Number(e_shoff) + i * e_shentsize;
  sections.push({
    name_off: b.readUInt32LE(off),
    type: b.readUInt32LE(off + 4),
    addr: Number(b.readBigUInt64LE(off + 16)),
    offset: Number(b.readBigUInt64LE(off + 24)),
    size: Number(b.readBigUInt64LE(off + 32)),
    link: b.readUInt32LE(off + 40),
    entsize: Number(b.readBigUInt64LE(off + 56)),
  });
}
const shstr = sections[sections.findIndex(s => true) ? 0 : 0]; // placeholder
// section[0] is reserved; e_shstrndx at 0x3e
const e_shstrndx = b.readUInt16LE(0x3e);
const strsec = sections[e_shstrndx];
const secName = (s) => {
  const end = strsec.offset + s.name_off;
  let e = end; while (b[e] !== 0 && e - strsec.offset < strsec.size) e++;
  return b.subarray(end, e).toString();
};
const dynsym = sections.find(s => secName(s) === '.dynsym' && s.type === 11); // SHT_DYNSYM
const dynstr = sections[dynsym.link] || sections.find(s => secName(s) === '.dynstr');
const symName = (off) => {
  let e = dynstr.offset + off;
  const start = e;
  while (b[e] !== 0) e++;
  return b.subarray(start, e).toString();
};

const out = { defined: [], undefined: [] };
const n = dynsym.size / dynsym.entsize;
for (let i = 0; i < n; i++) {
  const off = dynsym.offset + i * dynsym.entsize;
  const nameOff = b.readUInt32LE(off);
  const info = b.readUInt8(off + 12);
  const shndx = b.readUInt16LE(off + 6); // ELF64 Sym: st_name(0..3) st_info(4) st_other(5) st_shndx(6..7) st_value(8..15) st_size(16..23)
  const value = Number(b.readBigUInt64LE(off + 8));
  const name = symName(nameOff);
  if (!name) continue;
  const defined = shndx !== 0 && value !== 0;
  const bind = info >> 4;
  const rec = { name, bind: bind === 0 ? 'LOCAL' : bind === 1 ? 'GLOBAL' : bind === 2 ? 'WEAK' : 'b' + bind, defined, value };
  if (filter && !filter.test(name)) continue;
  (defined ? out.defined : out.undefined).push(rec);
}
console.log(JSON.stringify(path));
console.log('defined:', out.defined.length, ' undefined:', out.undefined.length);
for (const r of out.defined) console.log(`  ${r.value.toString(16).padStart(8,'0')}  ${r.name}`);
