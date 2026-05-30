/**
 * Module-matrix construction for QR Code symbols (ISO/IEC 18004):
 * function-pattern placement, data zig-zag routing, data masking, mask
 * selection by penalty scoring, and format / version information.
 */

import { EC_LEVELS, ALIGNMENT_CENTERS, moduleSize } from './tables.mjs';

const FINDER_ORIGINS = (size) => [
    [0, 0],
    [0, size - 7],
    [size - 7, 0],
];

function makeGrid(size, fill) {
    const grid = new Array(size);
    for (let r = 0; r < size; r += 1) {
        grid[r] = new Array(size).fill(fill);
    }
    return grid;
}

function getBit(value, index) {
    return (value >>> index) & 1;
}

/** The eight data-mask conditions; true means the module is inverted. */
export function maskCondition(mask, row, col) {
    switch (mask) {
        case 0: return (row + col) % 2 === 0;
        case 1: return row % 2 === 0;
        case 2: return col % 3 === 0;
        case 3: return (row + col) % 3 === 0;
        case 4: return (Math.floor(row / 2) + Math.floor(col / 3)) % 2 === 0;
        case 5: return ((row * col) % 2) + ((row * col) % 3) === 0;
        case 6: return (((row * col) % 2) + ((row * col) % 3)) % 2 === 0;
        case 7: return (((row + col) % 2) + ((row * col) % 3)) % 2 === 0;
        default: throw new RangeError(`Invalid mask pattern: ${mask}`);
    }
}

/** 15-bit format information for a level + mask (BCH(15,5), XOR mask 0x5412). */
export function formatInformationBits(level, mask) {
    const data = (EC_LEVELS[level].formatBits << 3) | mask;
    let remainder = data;
    for (let i = 0; i < 10; i += 1) {
        remainder = (remainder << 1) ^ ((remainder >> 9) * 0x537);
    }
    return ((data << 10) | (remainder & 0x3ff)) ^ 0x5412;
}

/** 18-bit version information for versions >= 7 (BCH(18,6)). */
export function versionInformationBits(version) {
    let remainder = version;
    for (let i = 0; i < 12; i += 1) {
        remainder = (remainder << 1) ^ ((remainder >> 11) * 0x1f25);
    }
    return (version << 12) | (remainder & 0xfff);
}

/**
 * Yield every data-module coordinate in placement order: upward/downward
 * zig-zag through column pairs from the right edge, skipping the vertical
 * timing column (column 6).
 */
export function* dataTraversal(size) {
    for (let right = size - 1; right >= 1; right -= 2) {
        const baseCol = right <= 6 ? right - 1 : right;
        const upward = ((baseCol + 1) & 2) === 0;
        for (let v = 0; v < size; v += 1) {
            const row = upward ? size - 1 - v : v;
            for (let c = 0; c < 2; c += 1) {
                yield [row, baseCol - c];
            }
        }
    }
}

/** Draw finder patterns, separators, timing, alignment and the dark module. */
function drawFunctionPatterns(modules, reserved, size, version) {
    const set = (r, c, dark) => {
        modules[r][c] = dark;
        reserved[r][c] = true;
    };

    // Finder patterns with their one-module separators.
    for (const [fr, fc] of FINDER_ORIGINS(size)) {
        for (let dy = -1; dy <= 7; dy += 1) {
            for (let dx = -1; dx <= 7; dx += 1) {
                const r = fr + dy;
                const c = fc + dx;
                if (r < 0 || r >= size || c < 0 || c >= size) {
                    continue;
                }
                const inFinder = dy >= 0 && dy <= 6 && dx >= 0 && dx <= 6;
                const ring = dy === 0 || dy === 6 || dx === 0 || dx === 6;
                const core = dy >= 2 && dy <= 4 && dx >= 2 && dx <= 4;
                set(r, c, inFinder && (ring || core));
            }
        }
    }

    // Timing patterns fill the gaps left on row 6 and column 6.
    for (let i = 0; i < size; i += 1) {
        if (!reserved[6][i]) {
            set(6, i, i % 2 === 0);
        }
        if (!reserved[i][6]) {
            set(i, 6, i % 2 === 0);
        }
    }

    // Alignment patterns at every centre pair except the three finder corners.
    const centers = ALIGNMENT_CENTERS[version];
    const last = centers[centers.length - 1];
    for (const r of centers) {
        for (const c of centers) {
            const finderCorner = (r === 6 && c === 6)
                || (r === 6 && c === last)
                || (r === last && c === 6);
            if (finderCorner) {
                continue;
            }
            for (let dy = -2; dy <= 2; dy += 1) {
                for (let dx = -2; dx <= 2; dx += 1) {
                    const ringEdge = Math.max(Math.abs(dy), Math.abs(dx)) !== 1;
                    set(r + dy, c + dx, ringEdge);
                }
            }
        }
    }

    // The single dark module next to the bottom-left finder.
    set(size - 8, 8, true);
}

/**
 * Place the 15-bit format information in its two standard copies.
 *
 * Each of the 15 bits appears once in a strip down column 8 (wrapping around
 * the top-left finder and continuing up from the bottom-left finder) and once
 * in a strip along row 8 (from the bottom-right finder across to the top-left
 * finder). Bit 0 is the least-significant bit.
 */
function drawFormatInformation(modules, reserved, size, level, mask) {
    const bits = formatInformationBits(level, mask);
    const set = (r, c, bit) => {
        modules[r][c] = bit === 1;
        reserved[r][c] = true;
    };

    for (let i = 0; i < 15; i += 1) {
        const bit = getBit(bits, i);

        // Vertical strip on column 8, skipping the timing row at index 6.
        let row;
        if (i < 6) {
            row = i;
        } else if (i < 8) {
            row = i + 1;
        } else {
            row = size - 15 + i;
        }
        set(row, 8, bit);

        // Horizontal strip on row 8, skipping the timing column at index 6.
        let col;
        if (i < 8) {
            col = size - 1 - i;
        } else if (i === 8) {
            col = 7;
        } else {
            col = 14 - i;
        }
        set(8, col, bit);
    }

    // The dark module next to the bottom-left finder is always set.
    modules[size - 8][8] = true;
    reserved[size - 8][8] = true;
}

/** Place version-information bits (versions >= 7), two reflected copies. */
function drawVersionInformation(modules, reserved, size, version) {
    if (version < 7) {
        return;
    }
    const bits = versionInformationBits(version);
    for (let i = 0; i < 18; i += 1) {
        const bit = getBit(bits, i);
        const a = size - 11 + (i % 3);
        const b = Math.floor(i / 3);
        modules[a][b] = bit === 1;
        modules[b][a] = bit === 1;
        reserved[a][b] = true;
        reserved[b][a] = true;
    }
}

/** Penalty score (lower is better) used to choose the data mask. */
function penaltyScore(modules, size) {
    let score = 0;

    // Rule 1: runs of five or more same-coloured modules in a row or column.
    const scoreLine = (get) => {
        for (let a = 0; a < size; a += 1) {
            let runColor = get(a, 0);
            let runLength = 1;
            for (let b = 1; b < size; b += 1) {
                const color = get(a, b);
                if (color === runColor) {
                    runLength += 1;
                } else {
                    if (runLength >= 5) {
                        score += 3 + (runLength - 5);
                    }
                    runColor = color;
                    runLength = 1;
                }
            }
            if (runLength >= 5) {
                score += 3 + (runLength - 5);
            }
        }
    };
    scoreLine((a, b) => modules[a][b]);
    scoreLine((a, b) => modules[b][a]);

    // Rule 2: every 2x2 block of a single colour.
    for (let r = 0; r < size - 1; r += 1) {
        for (let c = 0; c < size - 1; c += 1) {
            const m = modules[r][c];
            if (m === modules[r][c + 1] && m === modules[r + 1][c] && m === modules[r + 1][c + 1]) {
                score += 3;
            }
        }
    }

    // Rule 3: finder-like 1:1:3:1:1 patterns flanked by four light modules.
    const patternA = [true, false, true, true, true, false, true, false, false, false, false];
    const patternB = [false, false, false, false, true, false, true, true, true, false, true];
    const matches = (get, a, b) => {
        let hitA = true;
        let hitB = true;
        for (let k = 0; k < 11; k += 1) {
            const value = get(a, b + k);
            if (value !== patternA[k]) hitA = false;
            if (value !== patternB[k]) hitB = false;
        }
        return (hitA ? 1 : 0) + (hitB ? 1 : 0);
    };
    for (let a = 0; a < size; a += 1) {
        for (let b = 0; b <= size - 11; b += 1) {
            score += 40 * matches((x, y) => modules[x][y], a, b);
            score += 40 * matches((x, y) => modules[y][x], a, b);
        }
    }

    // Rule 4: deviation of the dark-module proportion from 50%.
    let dark = 0;
    for (let r = 0; r < size; r += 1) {
        for (let c = 0; c < size; c += 1) {
            if (modules[r][c]) {
                dark += 1;
            }
        }
    }
    const percent = (dark * 100) / (size * size);
    const deviation = Math.floor(Math.abs(percent - 50) / 5);
    score += deviation * 10;

    return score;
}

function cloneGrid(grid) {
    return grid.map((row) => row.slice());
}

/**
 * Lay down everything that does not depend on the data or the mask: function
 * patterns plus reserved format / version areas. Returns the base matrix and
 * the reserved-module map.
 */
export function layoutFunctionModules(version, level) {
    const size = moduleSize(version);
    const baseModules = makeGrid(size, false);
    const reserved = makeGrid(size, false);
    drawFunctionPatterns(baseModules, reserved, size, version);
    // Reserve the format / version areas so data placement skips them.
    drawFormatInformation(baseModules, reserved, size, level, 0);
    drawVersionInformation(baseModules, reserved, size, version);
    return { size, baseModules, reserved };
}

/**
 * Build the complete module matrix for the given version, level and data bit
 * stream. The best mask is chosen automatically unless `forcedMask` is given
 * (used by tests for deterministic output).
 *
 * Returns { modules, size, mask } where modules is a 2-D boolean array
 * (true = dark).
 */
export function buildMatrix(version, level, dataBits, forcedMask = null) {
    const { size, baseModules, reserved } = layoutFunctionModules(version, level);

    // Route the data bit stream through the free modules.
    let cursor = 0;
    for (const [row, col] of dataTraversal(size)) {
        if (reserved[row][col]) {
            continue;
        }
        baseModules[row][col] = cursor < dataBits.length && dataBits[cursor] === 1;
        cursor += 1;
    }

    const finalize = (mask) => {
        const modules = cloneGrid(baseModules);
        for (let r = 0; r < size; r += 1) {
            for (let c = 0; c < size; c += 1) {
                if (!reserved[r][c] && maskCondition(mask, r, c)) {
                    modules[r][c] = !modules[r][c];
                }
            }
        }
        drawFormatInformation(modules, reserved, size, level, mask);
        drawVersionInformation(modules, reserved, size, version);
        return modules;
    };

    if (forcedMask !== null) {
        return { modules: finalize(forcedMask), size, mask: forcedMask };
    }

    let bestMask = 0;
    let bestModules = null;
    let bestScore = Infinity;
    for (let mask = 0; mask < 8; mask += 1) {
        const modules = finalize(mask);
        const score = penaltyScore(modules, size);
        if (score < bestScore) {
            bestScore = score;
            bestMask = mask;
            bestModules = modules;
        }
    }
    return { modules: bestModules, size, mask: bestMask };
}

export const __test = { penaltyScore, makeGrid };
