/**
 * Byte-mode data encoding for QR Code symbols (ISO/IEC 18004).
 *
 * Only the 8-bit byte segment mode is implemented because every payload this
 * SDK produces (channel login URLs and pairing strings) is arbitrary UTF-8
 * text. The encoder selects the smallest version that fits, builds the data
 * bit stream, splits it into Reed-Solomon blocks, and interleaves the data and
 * error-correction codewords into final transmission order.
 */

import { errorCorrectionCodewords } from './galois.mjs';
import {
    BLOCK_LAYOUT,
    REMAINDER_BITS,
    MIN_VERSION,
    MAX_VERSION,
    dataCodewordCount,
    byteCountBits,
} from './tables.mjs';

const BYTE_MODE_INDICATOR = 0b0100;
const PAD_BYTES = [0xec, 0x11];

const textEncoder = new TextEncoder();

/** Append the low `length` bits of `value` (most significant first). */
function appendBits(bits, value, length) {
    for (let i = length - 1; i >= 0; i -= 1) {
        bits.push((value >>> i) & 1);
    }
}

/** Total bits needed to encode `byteLength` bytes at a given version. */
function requiredBits(version, byteLength) {
    return 4 + byteCountBits(version) + byteLength * 8;
}

/** Smallest version (1..40) that fits the payload at the requested level. */
function selectVersion(byteLength, level) {
    for (let version = MIN_VERSION; version <= MAX_VERSION; version += 1) {
        const capacity = dataCodewordCount(version, level) * 8;
        if (requiredBits(version, byteLength) <= capacity) {
            return version;
        }
    }
    throw new RangeError('Data is too large to encode in a single QR Code symbol.');
}

/** Build the full sequence of data codewords (header + payload + padding). */
function buildDataCodewords(bytes, version, level) {
    const totalCodewords = dataCodewordCount(version, level);
    const capacityBits = totalCodewords * 8;
    const bits = [];

    appendBits(bits, BYTE_MODE_INDICATOR, 4);
    appendBits(bits, bytes.length, byteCountBits(version));
    for (const byte of bytes) {
        appendBits(bits, byte, 8);
    }

    // Terminator: up to four zero bits, but never past capacity.
    appendBits(bits, 0, Math.min(4, capacityBits - bits.length));
    // Pad the final partial codeword with zeros.
    while (bits.length % 8 !== 0) {
        bits.push(0);
    }

    const codewords = [];
    for (let i = 0; i < bits.length; i += 8) {
        let byte = 0;
        for (let j = 0; j < 8; j += 1) {
            byte = (byte << 1) | bits[i + j];
        }
        codewords.push(byte);
    }
    // Fill the remaining capacity with the alternating pad pattern.
    for (let i = 0; codewords.length < totalCodewords; i += 1) {
        codewords.push(PAD_BYTES[i % 2]);
    }
    return codewords;
}

/** Split data codewords into blocks and attach Reed-Solomon EC codewords. */
function buildBlocks(dataCodewords, version, level) {
    const [ecPerBlock, blocks1, data1, blocks2, data2] = BLOCK_LAYOUT[level][version];
    const dataBlocks = [];
    const ecBlocks = [];

    let offset = 0;
    const addBlock = (size) => {
        const block = dataCodewords.slice(offset, offset + size);
        offset += size;
        dataBlocks.push(block);
        ecBlocks.push(errorCorrectionCodewords(block, ecPerBlock));
    };

    for (let i = 0; i < blocks1; i += 1) {
        addBlock(data1);
    }
    for (let i = 0; i < blocks2; i += 1) {
        addBlock(data2);
    }

    return { dataBlocks, ecBlocks, ecPerBlock };
}

/** Interleave block data and EC codewords into final transmission order. */
export function interleaveBlocks(dataBlocks, ecBlocks, ecPerBlock) {
    const result = [];
    const maxData = Math.max(...dataBlocks.map((block) => block.length));
    for (let i = 0; i < maxData; i += 1) {
        for (const block of dataBlocks) {
            if (i < block.length) {
                result.push(block[i]);
            }
        }
    }
    for (let i = 0; i < ecPerBlock; i += 1) {
        for (const block of ecBlocks) {
            result.push(block[i]);
        }
    }
    return result;
}

/**
 * Encode UTF-8 text into the final ordered bit stream plus the chosen version.
 * Returns { version, bits } where `bits` is a flat array of 0/1 ready for
 * placement into the module matrix (remainder bits already appended).
 */
export function encodeText(text, level) {
    const bytes = textEncoder.encode(text);
    const version = selectVersion(bytes.length, level);

    const dataCodewords = buildDataCodewords(bytes, version, level);
    const { dataBlocks, ecBlocks, ecPerBlock } = buildBlocks(dataCodewords, version, level);
    const finalCodewords = interleaveBlocks(dataBlocks, ecBlocks, ecPerBlock);

    const bits = [];
    for (const codeword of finalCodewords) {
        appendBits(bits, codeword, 8);
    }
    for (let i = 0; i < REMAINDER_BITS[version]; i += 1) {
        bits.push(0);
    }

    return { version, bits };
}

// Exposed so the test suite can validate the pure data pipeline in isolation.
export const __test = { selectVersion, buildDataCodewords, buildBlocks, appendBits };
