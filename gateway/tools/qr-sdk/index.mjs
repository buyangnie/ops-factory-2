/**
 * qr-sdk — a small, dependency-free QR Code generator.
 *
 * Clean-room implementation of QR Code symbol generation per the public
 * ISO/IEC 18004 standard. It supports only what ops-factory channels need:
 * byte-mode (UTF-8) payloads rendered to an SVG data URL. The public API
 * mirrors the `toDataURL(text, options)` shape used elsewhere so call sites
 * stay familiar.
 *
 * Example:
 *   import { toDataUrl } from '../qr-sdk/index.mjs';
 *   const url = await toDataUrl('https://example.com', {
 *       errorCorrectionLevel: 'M', margin: 2, width: 320,
 *   });
 */

import { EC_LEVELS } from './tables.mjs';
import { encodeText } from './encoder.mjs';
import { buildMatrix } from './matrix.mjs';
import { renderDataUrl } from './render.mjs';

const DEFAULT_MARGIN = 4;
const DEFAULT_SCALE = 4;

// Accept short codes and the common long names for the level option.
const LEVEL_ALIASES = {
    l: 'L', low: 'L',
    m: 'M', medium: 'M',
    q: 'Q', quartile: 'Q',
    h: 'H', high: 'H',
};

function normalizeLevel(level) {
    if (!level) {
        return 'M';
    }
    const resolved = LEVEL_ALIASES[String(level).toLowerCase()];
    if (!resolved || !EC_LEVELS[resolved]) {
        throw new RangeError(`Unknown error-correction level: ${level}`);
    }
    return resolved;
}

/**
 * Encode text into a QR module matrix.
 *
 * @returns {{ modules: boolean[][], size: number, version: number,
 *             mask: number, level: string }}
 */
export function encode(text, options = {}) {
    if (typeof text !== 'string' || text.length === 0) {
        throw new TypeError('QR text must be a non-empty string.');
    }
    const level = normalizeLevel(options.errorCorrectionLevel);
    const { version, bits } = encodeText(text, level);
    const forcedMask = Number.isInteger(options.mask) ? options.mask : null;
    const { modules, size, mask } = buildMatrix(version, level, bits, forcedMask);
    return { modules, size, version, mask, level };
}

/**
 * Encode text and return an SVG `data:` URL suitable for an <img> src.
 *
 * Async to match the signature of the library it replaces, even though the
 * work is synchronous.
 *
 * @param {string} text
 * @param {object} [options]
 * @param {('L'|'M'|'Q'|'H'|string)} [options.errorCorrectionLevel='M']
 * @param {number} [options.margin=4] - quiet-zone width in modules.
 * @param {number} [options.width] - image edge length in pixels.
 * @returns {Promise<string>}
 */
export async function toDataUrl(text, options = {}) {
    const { modules, size } = encode(text, options);
    const margin = Number.isFinite(options.margin) ? options.margin : DEFAULT_MARGIN;
    const width = Number.isFinite(options.width)
        ? options.width
        : (size + margin * 2) * DEFAULT_SCALE;
    return renderDataUrl(modules, { margin, width });
}

export default { encode, toDataUrl };
