/**
 * Render a QR module matrix to an SVG image encoded as a base64 data URL.
 *
 * SVG is used so the result is crisp at any display size and stays tiny on the
 * wire. The output is consumed directly as an <img> src, so it renders
 * identically to a raster data URL for the channel login flows.
 */

const DARK = '#000000';
const LIGHT = '#ffffff';

function toBase64(text) {
    if (typeof Buffer !== 'undefined') {
        return Buffer.from(text, 'utf-8').toString('base64');
    }
    // Browser fallback (UTF-8 safe).
    const bytes = new TextEncoder().encode(text);
    let binary = '';
    for (const byte of bytes) {
        binary += String.fromCharCode(byte);
    }
    return btoa(binary);
}

/**
 * Build the SVG markup for a matrix.
 *
 * @param {boolean[][]} modules - 2-D matrix, true = dark module.
 * @param {object} options
 * @param {number} options.margin - quiet-zone width in modules.
 * @param {number} options.width - image edge length in pixels.
 */
export function renderSvg(modules, { margin, width }) {
    const count = modules.length;
    const total = count + margin * 2;

    // Build one path covering all dark modules in module-space coordinates.
    let path = '';
    for (let r = 0; r < count; r += 1) {
        for (let c = 0; c < count; c += 1) {
            if (modules[r][c]) {
                path += `M${c + margin} ${r + margin}h1v1h-1z`;
            }
        }
    }

    return [
        `<svg xmlns="http://www.w3.org/2000/svg" width="${width}" height="${width}"`,
        ` viewBox="0 0 ${total} ${total}" shape-rendering="crispEdges">`,
        `<rect width="${total}" height="${total}" fill="${LIGHT}"/>`,
        `<path d="${path}" fill="${DARK}"/>`,
        '</svg>',
    ].join('');
}

/** Render a matrix to a `data:image/svg+xml;base64,...` URL. */
export function renderDataUrl(modules, options) {
    const svg = renderSvg(modules, options);
    return `data:image/svg+xml;base64,${toBase64(svg)}`;
}
