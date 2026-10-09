// Minimal standalone QR Code Generator (Type 1-10, byte mode)
// Generates SVG representation with zero dependencies
(function (root, factory) {
  const exported = factory();
  if (typeof define === 'function' && define.amd) {
    define([], function () { return exported; });
  }
  if (typeof module === 'object' && module.exports) {
    module.exports = exported;
  }
  if (root) {
    root.QRCodeSVG = exported;
  }
  if (typeof window !== 'undefined') {
    window.QRCodeSVG = exported;
  }
  if (typeof globalThis !== 'undefined') {
    globalThis.QRCodeSVG = exported;
  }
}(typeof globalThis !== 'undefined' ? globalThis : typeof self !== 'undefined' ? self : this, function () {

  // Minimal standard QR Generator
  function QRCode(text, options) {
    options = options || {};
    this.text = text;
    this.size = options.size || 200;
    this.colorDark = options.colorDark || '#000000';
    this.colorLight = options.colorLight || '#ffffff';
    this.correctLevel = options.correctLevel || 1; // 0=M, 1=L, 2=H, 3=Q
  }

  // Simple QR Code matrix generation
  // Uses lightweight Type 4 / Type 6 matrix (fits URLs up to 100+ chars)
  const PAD0 = 0xec;
  const PAD1 = 0x11;

  function QRPolynomial(num, shift) {
    let offset = 0;
    while (offset < num.length && num[offset] === 0) offset++;
    this.num = new Array(num.length - offset + shift);
    for (let i = 0; i < num.length - offset; i++) this.num[i] = num[i + offset];
    for (let i = 0; i < shift; i++) this.num[num.length - offset + i] = 0;
  }
  QRPolynomial.prototype = {
    get: function(index) { return this.num[index]; },
    getLength: function() { return this.num.length; },
    multiply: function(e) {
      const num = new Array(this.getLength() + e.getLength() - 1).fill(0);
      for (let i = 0; i < this.getLength(); i++) {
        for (let j = 0; j < e.getLength(); j++) {
          num[i + j] ^= QRMath.gexp(QRMath.glog(this.get(i)) + QRMath.glog(e.get(j)));
        }
      }
      return new QRPolynomial(num, 0);
    },
    mod: function(e) {
      if (this.getLength() - e.getLength() < 0) return this;
      const ratio = QRMath.glog(this.get(0)) - QRMath.glog(e.get(0));
      const num = new Array(this.getLength());
      for (let i = 0; i < this.getLength(); i++) num[i] = this.get(i);
      for (let i = 0; i < e.getLength(); i++) {
        num[i] ^= QRMath.gexp(QRMath.glog(e.get(i)) + ratio);
      }
      return new QRPolynomial(num, 0).mod(e);
    }
  };

  const QRMath = {
    glog: function(n) {
      if (n < 1) throw new Error("glog(" + n + ")");
      return QRMath.LOG_TABLE[n];
    },
    gexp: function(n) {
      while (n < 0) n += 255;
      while (n >= 255) n -= 255;
      return QRMath.EXP_TABLE[n];
    },
    EXP_TABLE: new Array(256),
    LOG_TABLE: new Array(256)
  };
  for (let i = 0; i < 8; i++) QRMath.EXP_TABLE[i] = 1 << i;
  for (let i = 8; i < 256; i++) {
    QRMath.EXP_TABLE[i] = QRMath.EXP_TABLE[i - 4] ^ QRMath.EXP_TABLE[i - 5] ^ QRMath.EXP_TABLE[i - 6] ^ QRMath.EXP_TABLE[i - 8];
  }
  for (let i = 0; i < 255; i++) QRMath.LOG_TABLE[QRMath.EXP_TABLE[i]] = i;

  // RS Block table [type, ecLevel, totalCount, dataCount]
  const RS_BLOCK_TABLE = [
    // 1-L, 1-M, 1-Q, 1-H
    [1, 1, 26, 19], [1, 0, 26, 16], [1, 3, 26, 13], [1, 2, 26, 9],
    // 2
    [2, 1, 44, 34], [2, 0, 44, 28], [2, 3, 44, 22], [2, 2, 44, 16],
    // 3
    [3, 1, 70, 55], [3, 0, 70, 44], [3, 3, 70, 34], [3, 2, 70, 26],
    // 4
    [4, 1, 100, 80], [4, 0, 100, 64], [4, 3, 100, 48], [4, 2, 100, 36],
    // 5
    [5, 1, 134, 108], [5, 0, 134, 86], [5, 3, 134, 62], [5, 2, 134, 46],
    // 6
    [6, 1, 172, 136], [6, 0, 172, 108], [6, 3, 172, 76], [6, 2, 172, 60],
    // 7
    [7, 1, 196, 156], [7, 0, 196, 124], [7, 3, 196, 88], [7, 2, 196, 66],
    // 8
    [8, 1, 242, 194], [8, 0, 242, 154], [8, 3, 242, 110], [8, 2, 242, 86],
    // 9
    [9, 1, 292, 232], [9, 0, 292, 182], [9, 3, 292, 132], [9, 2, 292, 100],
    // 10
    [10, 1, 346, 274], [10, 0, 346, 216], [10, 3, 346, 154], [10, 2, 346, 122]
  ];

  function getRSBlocks(typeNumber, errorCorrectLevel) {
    for (let i = 0; i < RS_BLOCK_TABLE.length; i++) {
      if (RS_BLOCK_TABLE[i][0] === typeNumber && RS_BLOCK_TABLE[i][1] === errorCorrectLevel) {
        return {
          totalCount: RS_BLOCK_TABLE[i][2],
          dataCount: RS_BLOCK_TABLE[i][3]
        };
      }
    }
    return { totalCount: 346, dataCount: 274 };
  }

  function createData(typeNumber, errorCorrectLevel, text) {
    const rsBlock = getRSBlocks(typeNumber, errorCorrectLevel);
    const buffer = [];
    // 8-bit byte mode (0100)
    buffer.push(0x40 | (text.length >> 4));
    buffer.push(((text.length & 0x0f) << 4) | (text.charCodeAt(0) >> 4));
    for (let i = 0; i < text.length - 1; i++) {
      buffer.push(((text.charCodeAt(i) & 0x0f) << 4) | (text.charCodeAt(i + 1) >> 4));
    }
    buffer.push((text.charCodeAt(text.length - 1) & 0x0f) << 4);

    // Pad
    let padIndex = 0;
    while (buffer.length < rsBlock.dataCount) {
      buffer.push((padIndex % 2 === 0) ? PAD0 : PAD1);
      padIndex++;
    }

    // RS Error Correction calculation
    const ecCount = rsBlock.totalCount - rsBlock.dataCount;
    let ecPoly = new QRPolynomial([1], 0);
    for (let i = 0; i < ecCount; i++) {
      ecPoly = ecPoly.multiply(new QRPolynomial([1, QRMath.gexp(i)], 0));
    }
    const rawPoly = new QRPolynomial(buffer, ecCount);
    const modPoly = rawPoly.mod(ecPoly);

    const result = buffer.slice(0, rsBlock.dataCount);
    for (let i = 0; i < ecCount; i++) {
      const modIndex = i + modPoly.getLength() - ecCount;
      result.push((modIndex >= 0) ? modPoly.get(modIndex) : 0);
    }
    return result;
  }

  function determineTypeNumber(textLength) {
    const needed = textLength + 2;
    for (let t = 1; t <= 10; t++) {
      const b = getRSBlocks(t, 1);
      if (b && b.dataCount >= needed) {
        return Math.max(t, 3); // Minimum type 3 for crisp standard rendering
      }
    }
    return 10;
  }

  function generateMatrix(text) {
    const typeNumber = determineTypeNumber(text.length);
    const moduleCount = typeNumber * 4 + 17;
    const matrix = [];
    for (let r = 0; r < moduleCount; r++) {
      matrix.push(new Array(moduleCount).fill(null));
    }

    // Position detection patterns (7x7 at 3 corners)
    function addFinder(row, col) {
      for (let r = -1; r <= 7; r++) {
        for (let c = -1; c <= 7; c++) {
          if (row + r < 0 || moduleCount <= row + r || col + c < 0 || moduleCount <= col + c) continue;
          if ((0 <= r && r <= 6 && (c === 0 || c === 6)) ||
              (0 <= c && c <= 6 && (r === 0 || r === 6)) ||
              (2 <= r && r <= 4 && 2 <= c && c <= 4)) {
            matrix[row + r][col + c] = true;
          } else {
            matrix[row + r][col + c] = false;
          }
        }
      }
    }
    addFinder(0, 0);
    addFinder(moduleCount - 7, 0);
    addFinder(0, moduleCount - 7);

    // Timing patterns
    for (let i = 8; i < moduleCount - 8; i++) {
      if (matrix[i][6] === null) matrix[i][6] = (i % 2 === 0);
      if (matrix[6][i] === null) matrix[6][i] = (i % 2 === 0);
    }

    // Encoded data
    const data = createData(typeNumber, 1, text);
    let byteIdx = 0;
    let bitIdx = 7;
    let inc = -1;
    let r = moduleCount - 1;

    for (let c = moduleCount - 1; c > 0; c -= 2) {
      if (c === 6) c--;
      while (true) {
        for (let colOffset = 0; colOffset < 2; colOffset++) {
          const col = c - colOffset;
          if (matrix[r][col] === null) {
            let dark = false;
            if (byteIdx < data.length) {
              dark = ((data[byteIdx] >>> bitIdx) & 1) === 1;
            }
            bitIdx--;
            if (bitIdx === -1) {
              byteIdx++;
              bitIdx = 7;
            }
            // Standard mask 0: (row + col) % 2 === 0
            if ((r + col) % 2 === 0) {
              dark = !dark;
            }
            matrix[r][col] = dark;
          }
        }
        r += inc;
        if (r < 0 || moduleCount <= r) {
          r -= inc;
          inc = -inc;
          break;
        }
      }
    }

    return matrix;
  }

  QRCode.prototype.toSVG = function() {
    try {
      const matrix = generateMatrix(this.text);
      const count = matrix.length;
      const cellSize = (this.size / count).toFixed(2);
      let rects = '';
      for (let r = 0; r < count; r++) {
        for (let c = 0; c < count; c++) {
          if (matrix[r][c]) {
            rects += `<rect x="${(c * cellSize).toFixed(2)}" y="${(r * cellSize).toFixed(2)}" width="${cellSize}" height="${cellSize}" fill="${this.colorDark}" />`;
          }
        }
      }
      return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${this.size} ${this.size}" width="${this.size}" height="${this.size}"><rect width="100%" height="100%" fill="${this.colorLight}" rx="12"/>${rects}</svg>`;
    } catch (e) {
      // Fallback clean placeholder SVG with URL
      return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${this.size} ${this.size}" width="${this.size}" height="${this.size}"><rect width="100%" height="100%" fill="${this.colorLight}" rx="12"/><text x="50%" y="50%" dominant-baseline="middle" text-anchor="middle" font-family="sans-serif" font-size="12" fill="${this.colorDark}">${this.text}</text></svg>`;
    }
  };

  return QRCode;
}));
