// ref: evidence-only helper — reads "PaP" preset blobs shipped inside assets/auto_eq.db
// Purpose: understand DATA SEMANTICS (biquad params / frequency curves). The wire format
// itself is Poweramp's proprietary serialization: our reconstruction will use its own format.
const fs = require('fs');
const { execSync } = require('child_process');

// pull blobs out of the sqlite file by searching for the PaP magic
const db = fs.readFileSync('extract/Poweramp-build-1032-uni_autoeq.db');
const needle = Buffer.from('50615001', 'hex'); // "PaP" + version byte 0x01

let hits = [];
for (let i = 0; i < db.length - 4; i++) {
  if (db[i] === needle[0] && db[i+1] === needle[1] && db[i+2] === needle[2] && db[i+3] === needle[3]) hits.push(i);
}
console.log('PaP-background occurrences:', hits.length);

function hexAt(off, len) { return db.subarray(off, off + len); }

// Walk one non-parametric blob (row 1) and one parametric blob (row 2).
// Since we don't know exact record size yet, print the first 96 bytes as:
//   little-endian signed int32   |   little-endian float32  => see which reads look sane
function dump(off) {
  console.log('--- blob @', off, '---');
  for (let j = 0; j < 96; j += 4) {
    const i32 = db.readInt32LE(off + j);
    const f32 = db.readFloatLE(off + j);
    console.log(`  +${String(j).padStart(3)} : i32=${String(i32).padEnd(12)} f32=${f32.toFixed(6).padEnd(10)} hex=${db.subarray(off+j, off+j+4).toString('hex')}`);
  }
}
dump(hits[0]);
dump(hits[1]);

// Statistical: count i32 values that look like AudioEQ frequencies (20..21000)
const freqLike = new Set();
for (const off of hits.slice(0, 200)) {
  for (let j = 0; j < 300 && off + j + 4 <= db.length; j += 4) {
    const v = db.readInt32LE(off + j);
    if (v >= 20 && v <= 20500) freqLike.add(v);
  }
}
const sorted = [...freqLike].sort((a,b) => a-b);
console.log('distinct i32 in [20..20500] across 200 blobs:', sorted.length);
console.log(sorted.join(', '));
