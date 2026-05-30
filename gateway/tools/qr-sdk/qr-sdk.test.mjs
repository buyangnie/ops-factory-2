/**
 * Tests for qr-sdk. Run with: node --test gateway/tools/qr-sdk/
 *
 * Correctness is established without any third-party decoder by combining:
 *   - field arithmetic checked against a slow reference multiply,
 *   - the Reed-Solomon divisibility invariant,
 *   - known BCH constants for format / version information,
 *   - pure round-trips of the data pipeline, and
 *   - a full re-read of the placed matrix back into the original bit stream.
 */

import test from 'node:test';
import assert from 'node:assert/strict';

import { fieldMul, errorCorrectionCodewords, __test as galois } from './galois.mjs';
import { encodeText, interleaveBlocks, __test as enc } from './encoder.mjs';
import {
    buildMatrix,
    layoutFunctionModules,
    dataTraversal,
    maskCondition,
    formatInformationBits,
    versionInformationBits,
} from './matrix.mjs';
import { BLOCK_LAYOUT, dataCodewordCount } from './tables.mjs';
import { encode, toDataUrl } from './index.mjs';

// Reference GF(2^8) multiply (Russian-peasant) under the QR primitive 0x11D.
function refMul(a, b) {
    let result = 0;
    while (b > 0) {
        if (b & 1) {
            result ^= a;
        }
        a <<= 1;
        if (a & 0x100) {
            a ^= 0x11d;
        }
        b >>= 1;
    }
    return result;
}

test('GF exp/log tables are mutual inverses', () => {
    for (let v = 1; v < 256; v += 1) {
        assert.equal(galois.EXP[galois.LOG[v]], v);
    }
});

test('fieldMul matches the reference multiply', () => {
    for (let a = 0; a < 256; a += 7) {
        for (let b = 0; b < 256; b += 5) {
            assert.equal(fieldMul(a, b), refMul(a, b));
        }
    }
});

test('Reed-Solomon output is divisible by the generator (remainder zero)', () => {
    // A valid codeword (data || ec) divided by the generator leaves no
    // remainder, which is the defining property of the RS code.
    for (const ecLength of [7, 10, 13, 17, 26, 30]) {
        const data = Array.from({ length: 20 }, (_, i) => (i * 31 + 7) & 0xff);
        const ec = errorCorrectionCodewords(data, ecLength);
        const recomputed = errorCorrectionCodewords(data.concat(ec), ecLength);
        assert.deepEqual(recomputed, new Array(ecLength).fill(0));
    }
});

test('format information matches known BCH constants', () => {
    // (level M, mask 0) is the canonical 0b101010000010010 example.
    assert.equal(formatInformationBits('M', 0), 0b101010000010010);
    // All 32 values must stay inside 15 bits.
    for (const level of ['L', 'M', 'Q', 'H']) {
        for (let mask = 0; mask < 8; mask += 1) {
            const bits = formatInformationBits(level, mask);
            assert.ok(bits >= 0 && bits < (1 << 15));
        }
    }
});

test('version information matches the known constant for version 7', () => {
    assert.equal(versionInformationBits(7), 0x07c94);
});

test('data codeword stream round-trips through the header parser', () => {
    const cases = ['A', 'hello world', 'https://example.com/connect?token=abc123', '微信扫码登录'];
    for (const text of cases) {
        const bytes = [...new TextEncoder().encode(text)];
        const level = 'M';
        const version = enc.selectVersion(bytes.length, level);
        const codewords = enc.buildDataCodewords(bytes, version, level);
        assert.equal(codewords.length, dataCodewordCount(version, level));

        // Re-read the header out of the codeword stream.
        const bits = [];
        for (const cw of codewords) {
            for (let i = 7; i >= 0; i -= 1) {
                bits.push((cw >> i) & 1);
            }
        }
        let pos = 0;
        const read = (n) => {
            let value = 0;
            for (let i = 0; i < n; i += 1) {
                value = (value << 1) | bits[pos++];
            }
            return value;
        };
        assert.equal(read(4), 0b0100, 'byte mode indicator');
        const countBits = version <= 9 ? 8 : 16;
        assert.equal(read(countBits), bytes.length, 'character count');
        const recovered = [];
        for (let i = 0; i < bytes.length; i += 1) {
            recovered.push(read(8));
        }
        assert.deepEqual(recovered, bytes);
    }
});

test('block interleaving is reversible', () => {
    for (const [version, level] of [[1, 'M'], [5, 'Q'], [7, 'H'], [10, 'L']]) {
        const total = dataCodewordCount(version, level);
        const dataCodewords = Array.from({ length: total }, (_, i) => (i * 13 + 1) & 0xff);
        const { dataBlocks, ecBlocks, ecPerBlock } = enc.buildBlocks(dataCodewords, version, level);
        const interleaved = interleaveBlocks(dataBlocks, ecBlocks, ecPerBlock);

        // De-interleave back into per-block data and compare.
        const counts = dataBlocks.map((b) => b.length);
        const recovered = dataBlocks.map(() => []);
        const maxData = Math.max(...counts);
        let idx = 0;
        for (let i = 0; i < maxData; i += 1) {
            for (let b = 0; b < dataBlocks.length; b += 1) {
                if (i < counts[b]) {
                    recovered[b].push(interleaved[idx++]);
                }
            }
        }
        assert.deepEqual(recovered, dataBlocks);
    }
});

test('placed matrix re-reads to the original data bit stream', () => {
    // The strongest end-to-end check: encode, place into the matrix with a
    // fixed mask, then walk the same traversal in reverse, undo the mask, and
    // confirm we recover exactly the bits that were placed.
    const cases = [
        ['A', 'M'],
        ['https://example.com/connect?token=abcdef', 'M'],
        ['微信扫码登录授权 channel', 'Q'],
        ['x'.repeat(300), 'L'],
    ];
    for (const [text, level] of cases) {
        const { version, bits } = encodeText(text, level);
        for (let mask = 0; mask < 8; mask += 1) {
            const { modules, size } = buildMatrix(version, level, bits, mask);
            const { reserved } = layoutFunctionModules(version, level);

            const read = [];
            for (const [r, c] of dataTraversal(size)) {
                if (reserved[r][c]) {
                    continue;
                }
                const unmasked = modules[r][c] !== maskCondition(mask, r, c);
                read.push(unmasked ? 1 : 0);
            }
            assert.equal(read.length, bits.length, `module count v${version}`);
            assert.deepEqual(read, bits, `mask ${mask} round-trip`);
        }
    }
});

test('every version/level block layout sums to the dimensional codeword count', () => {
    // Total codewords implied by the block table must equal the geometric
    // capacity of the symbol (data + EC codewords), a cross-check on the table.
    const functionModules = (version) => {
        const size = version * 4 + 17;
        const { reserved } = layoutFunctionModules(version, 'M');
        let reservedCount = 0;
        for (let r = 0; r < size; r += 1) {
            for (let c = 0; c < size; c += 1) {
                if (reserved[r][c]) reservedCount += 1;
            }
        }
        return reservedCount;
    };
    for (let version = 1; version <= 40; version += 1) {
        const size = version * 4 + 17;
        const dataModules = size * size - functionModules(version);
        const totalCodewords = Math.floor(dataModules / 8);
        for (const level of ['L', 'M', 'Q', 'H']) {
            const [ecPerBlock, b1, d1, b2, d2] = BLOCK_LAYOUT[level][version];
            const blocks = b1 + b2;
            const implied = b1 * d1 + b2 * d2 + blocks * ecPerBlock;
            assert.equal(implied, totalCodewords, `v${version}-${level}`);
        }
    }
});

test('toDataUrl returns an SVG data URL with the expected geometry', async () => {
    const url = await toDataUrl('https://example.com', {
        errorCorrectionLevel: 'M',
        margin: 2,
        width: 320,
    });
    assert.ok(url.startsWith('data:image/svg+xml;base64,'));
    const svg = Buffer.from(url.split(',')[1], 'base64').toString('utf-8');
    assert.ok(svg.includes('width="320"'));

    const { size } = encode('https://example.com', { errorCorrectionLevel: 'M' });
    assert.ok(svg.includes(`viewBox="0 0 ${size + 4} ${size + 4}"`));
});

test('encode is deterministic and picks a valid mask', () => {
    const a = encode('repeatable input', { errorCorrectionLevel: 'M' });
    const b = encode('repeatable input', { errorCorrectionLevel: 'M' });
    assert.equal(a.mask, b.mask);
    assert.ok(a.mask >= 0 && a.mask < 8);
    assert.deepEqual(a.modules, b.modules);
});
