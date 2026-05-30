/**
 * Arithmetic over the Galois field GF(2^8) used by QR Code Reed-Solomon
 * error correction, as specified in ISO/IEC 18004.
 *
 * The field is built from the primitive polynomial
 *   x^8 + x^4 + x^3 + x^2 + 1  (0x11D)
 * with 2 (alpha) as the generator element. Both the exponent and log
 * lookup tables are derived at module load time so there is no embedded
 * binary blob to maintain.
 */

const PRIMITIVE = 0x11d;
const FIELD_SIZE = 256;

// EXP[i] = alpha^i ; LOG[v] = i such that alpha^i == v
const EXP = new Uint8Array(FIELD_SIZE * 2);
const LOG = new Uint8Array(FIELD_SIZE);

(function buildFieldTables() {
    let value = 1;
    for (let power = 0; power < 255; power += 1) {
        EXP[power] = value;
        LOG[value] = power;
        value <<= 1;
        if (value & 0x100) {
            value ^= PRIMITIVE;
        }
    }
    // Mirror the cycle once more so callers can index with an exponent up to
    // 509 without taking a modulo on every multiply.
    for (let power = 255; power < EXP.length; power += 1) {
        EXP[power] = EXP[power - 255];
    }
})();

/** Multiply two field elements. */
export function fieldMul(a, b) {
    if (a === 0 || b === 0) {
        return 0;
    }
    return EXP[LOG[a] + LOG[b]];
}

/**
 * Build the Reed-Solomon generator polynomial of the given degree.
 * Coefficients are returned most-significant first; the leading
 * coefficient is always 1.
 */
function generatorPolynomial(degree) {
    let poly = [1];
    for (let i = 0; i < degree; i += 1) {
        // Multiply the running product by (x - alpha^i).
        const next = new Array(poly.length + 1).fill(0);
        for (let j = 0; j < poly.length; j += 1) {
            next[j] ^= poly[j];
            next[j + 1] ^= fieldMul(poly[j], EXP[i]);
        }
        poly = next;
    }
    return poly;
}

// Generator polynomials are reused across every block of a symbol, so cache
// them by degree.
const generatorCache = new Map();

function generatorFor(degree) {
    let poly = generatorCache.get(degree);
    if (!poly) {
        poly = generatorPolynomial(degree);
        generatorCache.set(degree, poly);
    }
    return poly;
}

/**
 * Compute the `ecLength` Reed-Solomon error-correction codewords for a block
 * of data codewords. Returns a fresh array of length `ecLength`.
 */
export function errorCorrectionCodewords(data, ecLength) {
    const generator = generatorFor(ecLength);
    const residue = new Array(data.length + ecLength).fill(0);
    for (let i = 0; i < data.length; i += 1) {
        residue[i] = data[i];
    }
    for (let i = 0; i < data.length; i += 1) {
        const factor = residue[i];
        if (factor === 0) {
            continue;
        }
        for (let j = 0; j <= ecLength; j += 1) {
            residue[i + j] ^= fieldMul(generator[j], factor);
        }
    }
    return residue.slice(data.length);
}

// Exposed for tests that want to validate the field directly.
export const __test = { EXP, LOG, generatorPolynomial };
