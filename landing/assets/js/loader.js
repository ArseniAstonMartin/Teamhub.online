/* Teamhub page loader: animated bento logo, shown only when a page is slow to load.
   Adapted from the standalone bento-loader prototype. */
(() => {
'use strict';
if (!window.Path2D || !window.requestAnimationFrame) return;

const SHOW_AFTER = 300;   // мс: быстрые загрузки не видят лоадер вообще
const MIN_SHOWN  = 700;   // мс: показанный лоадер не мигает, а доигрывает
const MAX_SHOWN  = 20000; // мс: страховка, если load так и не пришёл

const root = document.documentElement;
// offline.html: лоадер показывается сразу, на белом фоне, и не скрывается
const ALWAYS = root.hasAttribute('data-loader-always');
const NAV_KEY = 'th-loader-nav';
const NAV_TTL = 15000;   // мс: флаг перехода живёт недолго, чтобы не поймать старый
// переход по внутренней ссылке: старая страница уже показала лоадер, новая подхватывает его с той же фазы
let navStart = 0;
try {
  const v = +sessionStorage.getItem(NAV_KEY);
  sessionStorage.removeItem(NAV_KEY);
  if (v && Date.now() - v >= 0 && Date.now() - v < NAV_TTL) navStart = v;
} catch (e) {}
const FRESH = document.readyState !== 'complete';   // у уже загруженной страницы лоадер включается только по клику

/* ───────────────────────── Настройки ───────────────────────── */
const CFG = {
  stepPeriod: 1.5,     // сек на один «шаг» (все 6 блоков смещаются на соседний паз)
  moveDur:    0.95,    // сек движения одного блока
  stagger:    0.09,    // сек задержки между блоками внутри шага (каскад)
  rConvex:    50,      // радиус выпуклых скруглений (ед. дизайна)
  rConcave:   34,      // радиус вогнутых скруглений
  maxDPR:     2,
  maxPixels:  2560 * 1440
};
const STEPS = 6;
const LOOP  = CFG.stepPeriod * STEPS;   // длительность бесшовного цикла (9 с)

/* ───────────────────────── Геометрия слотов ─────────────────────────
   Каждая форма — «прямоугольник с вырезами», 11 параметров:
   [x0, y0, x1, y1, wTL, hTL, wTR, hTR, n0, n1, nd]
   wTL/hTL — вырез в левом верхнем углу, wTR/hTR — в правом верхнем,
   n0..n1 / nd — паз сверху (по центру) глубиной nd.
   Все формы имеют одинаковую топологию, поэтому морфинг — это
   линейная интерполяция параметров: углы всегда остаются прямыми
   и жёстко скруглёнными, а форма «перетекает» между пазами.        */
const DW = 940, DH = 865;
const SLOTS = [
  /* 0 длинный верхний   */ [   0,   0, 680, 235,   0,   0,   0,   0, 340, 340,   0],
  /* 1 малый верхний     */ [ 710,   0, 940, 235,   0,   0,   0,   0, 825, 825,   0],
  /* 2 правый Г-образный */ [ 530, 260, 940, 540, 150, 125,   0,   0, 810, 810,   0],
  /* 3 нижний правый     */ [ 710, 565, 940, 865,   0,   0,   0,   0, 825, 825,   0],
  /* 4 П-образный        */ [   0, 565, 680, 865,   0,   0,   0,   0, 410, 530, 200],
  /* 5 левый Г-образный  */ [   0, 260, 410, 540,   0,   0, 150, 125, 130, 130,   0]
];
// Порядок слотов — по часовой стрелке. Блок i стартует в слоте i
// и каждый шаг переезжает в следующий слот.

/* ───────────────────────── Цвета ───────────────────────── */
const WHITE = [255, 255, 255];
const mixc = (c, t, to) => [c[0] + (to[0]-c[0])*t, c[1] + (to[1]-c[1])*t, c[2] + (to[2]-c[2])*t];
const rgb  = c => `rgb(${c[0]|0},${c[1]|0},${c[2]|0})`;
const rgba = (c, a) => `rgba(${c[0]|0},${c[1]|0},${c[2]|0},${a})`;

const BASE = [
  [ 60, 124, 238],   // 0 светло-синий (длинный)
  [ 74, 196, 236],   // 1 бирюзовый
  [ 33,  64, 216],   // 2 насыщенный синий (правый Г)
  [ 82, 200, 152],   // 3 мятно-зелёный
  [ 20,  46, 132],   // 4 тёмно-синий (П)
  [ 50, 100, 234]    // 5 ярко-синий (левый Г)
];
const BLOCKS = BASE.map(c => ({
  mid: c,
  hi:  mixc(c, 0.22, WHITE),
  lo:  mixc(c, 0.22, [8, 16, 74]),
  rim: mixc(c, 0.55, [4, 12, 76])
}));

/* ───────────────────────── Физика движения ─────────────────────────
   ease(): лёгкая «замашка» назад (anticipation) → резкий импульс →
   затухающая пружина с едва заметным отскоком (~1.5 %) → ровно 1.      */
const clamp  = (x, a, b) => x < a ? a : x > b ? b : x;
const smooth = u => u*u*(3 - 2*u);
const ZETA = 0.8, OMEGA = 8.5;
const WD = OMEGA * Math.sqrt(1 - ZETA*ZETA), KS = ZETA*OMEGA/WD;
const spring = u => {
  const e = Math.exp(-ZETA*OMEGA*u);
  return 1 - e*(Math.cos(WD*u) + KS*Math.sin(WD*u));
};
const SPR1 = spring(1);
const ANT = 0.14, ANT_AMT = 0.05;
function ease(t) {
  if (t <= 0) return 0;
  if (t >= 1) return 1;
  if (t < ANT) return -ANT_AMT * smooth(t / ANT);
  const u = (t - ANT) / (1 - ANT);
  const h = spring(u) + (1 - SPR1) * smooth(u);   // ровно 1 в конце
  return -ANT_AMT + (1 + ANT_AMT) * h;
}

/* ───────────────────────── Canvas / ресайз ───────────────────────── */
const canvas = document.createElement('canvas');
const ctx = canvas.getContext('2d', { alpha: false });
const PAD = 170;
const LOGO_PX = 200;   // ширина лого на экране, css px
let dpr = Math.min(window.devicePixelRatio || 1, CFG.maxDPR);
let CW = 0, CH = 0, S = 1, OX = 0, OY = 0;

function resize() {
  const cw = window.innerWidth, ch = window.innerHeight;
  let d = dpr;
  if (cw*ch*d*d > CFG.maxPixels) d = Math.sqrt(CFG.maxPixels / (cw*ch));
  canvas.width  = Math.max(2, Math.round(cw * d));
  canvas.height = Math.max(2, Math.round(ch * d));
  CW = canvas.width; CH = canvas.height;
  S  = Math.min(CW / (DW + 2*PAD), CH / (DH + 2*PAD), LOGO_PX * d / DW);
  OX = (CW - DW*S) / 2;
  OY = (CH - DH*S) / 2;
}

/* Матовая зернистость (генерируется один раз) */
const grainPat = (() => {
  const n = document.createElement('canvas'); n.width = n.height = 160;
  const g = n.getContext('2d'), im = g.createImageData(160, 160), d = im.data;
  for (let i = 0; i < d.length; i += 4) {
    const v = Math.random() < 0.5 ? 255 : 0;
    d[i] = d[i+1] = d[i+2] = v;
    d[i+3] = Math.random() * 15;
  }
  g.putImageData(im, 0, 0);
  return ctx.createPattern(n, 'repeat');
})();

/* ───────────────────────── Построение контура ───────────────────────── */
function shapePts(p) {
  let [x0, y0, x1, y1, wl, hl, wr, hr, n0, n1, nd] = p;
  const W = Math.max(x1 - x0, 1), H = Math.max(y1 - y0, 1);
  wl = clamp(wl, 0, W);  wr = clamp(wr, 0, W - wl);
  hl = clamp(hl, 0, H * 0.98);  hr = clamp(hr, 0, H * 0.98);
  n0 = clamp(n0, x0 + wl, x1 - wr);
  n1 = clamp(n1, n0, x1 - wr);
  nd = clamp(nd, 0, H * 0.98);
  return [
    [x0, y0 + hl], [x0 + wl, y0 + hl], [x0 + wl, y0],
    [n0, y0], [n0, y0 + nd], [n1, y0 + nd], [n1, y0],
    [x1 - wr, y0], [x1 - wr, y0 + hr], [x1, y0 + hr],
    [x1, y1], [x0, y1]
  ];
}

// убираем совпадающие и коллинеарные вершины (вырожденные при морфинге)
function clean(pts) {
  const a = pts.slice(), eps = 0.05 * S;
  let changed = true;
  while (changed && a.length > 3) {
    changed = false;
    for (let i = 0; i < a.length; i++) {
      const p = a[(i + a.length - 1) % a.length], v = a[i], q = a[(i + 1) % a.length];
      const ax = v[0]-p[0], ay = v[1]-p[1], bx = q[0]-v[0], by = q[1]-v[1];
      const la = Math.hypot(ax, ay), lb = Math.hypot(bx, by);
      if (la < eps || Math.abs(ax*by - ay*bx) < 1e-3 * la * lb) { a.splice(i, 1); changed = true; break; }
    }
  }
  return a;
}

// скруглённый многоугольник; радиус автоматически ужимается на коротких рёбрах
function buildPath(a) {
  const n = a.length;
  if (n < 3) return null;
  let area = 0, minx = 1e9, miny = 1e9, maxx = -1e9, maxy = -1e9;
  for (let i = 0; i < n; i++) {
    const v = a[i], q = a[(i + 1) % n];
    area += v[0]*q[1] - q[0]*v[1];
    if (v[0] < minx) minx = v[0]; if (v[0] > maxx) maxx = v[0];
    if (v[1] < miny) miny = v[1]; if (v[1] > maxy) maxy = v[1];
  }
  const P = new Path2D();
  for (let i = 0; i < n; i++) {
    const p = a[(i + n - 1) % n], v = a[i], q = a[(i + 1) % n];
    let ax = p[0]-v[0], ay = p[1]-v[1], bx = q[0]-v[0], by = q[1]-v[1];
    const la = Math.hypot(ax, ay), lb = Math.hypot(bx, by);
    ax /= la; ay /= la; bx /= lb; by /= lb;
    const cross = (v[0]-p[0])*(q[1]-v[1]) - (v[1]-p[1])*(q[0]-v[0]);
    const r = (cross*area > 0 ? CFG.rConvex : CFG.rConcave) * S;
    const th = Math.acos(clamp(ax*bx + ay*by, -1, 1));
    const tn = Math.tan(th / 2);
    let d = tn > 1e-6 ? r / tn : 0;
    d = Math.min(d, 0.5 * Math.min(la, lb));
    const rr = d * tn;
    const sx = v[0] + ax*d, sy = v[1] + ay*d, ex = v[0] + bx*d, ey = v[1] + by*d;
    if (i === 0) P.moveTo(sx, sy); else P.lineTo(sx, sy);
    if (rr > 0.01) P.arcTo(v[0], v[1], ex, ey, rr); else P.lineTo(v[0], v[1]);
  }
  P.closePath();
  return { path: P, minx, miny, maxx, maxy, w: maxx - minx, h: maxy - miny,
           cx: (minx + maxx) / 2, cy: (miny + maxy) / 2 };
}

/* ───────────────────────── Отрисовка блока ───────────────────────── */
// Падающая тень. Свет неподвижен (сверху), тень зависит только от «подъёма» блока.
function shadowPass(blk, mul) {
  if (mul <= 0.001) return;
  const L = blk.lift;
  ctx.save();
  ctx.fillStyle = rgb(blk.col.mid);
  // рассеянная
  ctx.shadowColor = `rgba(36,60,150,${((0.22 - 0.05*L) * mul).toFixed(3)})`;
  ctx.shadowBlur = (48 + 46*L) * S;
  ctx.shadowOffsetX = (4 + 8*L) * S;
  ctx.shadowOffsetY = (24 + 34*L) * S;
  ctx.fill(blk.path);
  // контактная
  ctx.shadowColor = `rgba(16,34,120,${((0.30 - 0.10*L) * mul).toFixed(3)})`;
  ctx.shadowBlur = (12 + 16*L) * S;
  ctx.shadowOffsetX = (1 + 3*L) * S;
  ctx.shadowOffsetY = (6 + 10*L) * S;
  ctx.fill(blk.path);
  ctx.restore();
}

function bodyPass(blk) {
  const { path, cx, cy, w, h, col, lift, rot } = blk;

  // объёмный градиент: ось повёрнута вместе с блоком относительно неподвижного света
  const a = 0.30 + rot * 1.8;
  const dx = Math.sin(a), dy = Math.cos(a);
  const half = (Math.abs(dy)*h + Math.abs(dx)*w) / 2;
  const g = ctx.createLinearGradient(cx - dx*half, cy - dy*half, cx + dx*half, cy + dy*half);
  g.addColorStop(0,    rgb(mixc(col.hi, 0.10 * lift, WHITE)));
  g.addColorStop(0.55, rgb(col.mid));
  g.addColorStop(1,    rgb(col.lo));
  ctx.fillStyle = g;
  ctx.fill(path);

  ctx.save();
  ctx.clip(path);

  // внутренние тени: рамка с дыркой по форме блока, её тень падает внутрь
  const m = 150 * S;
  const frame = new Path2D();
  frame.rect(blk.minx - m, blk.miny - m, w + 2*m, h + 2*m);
  frame.addPath(path);
  ctx.fillStyle = '#000';

  const hl = 1 + 0.25 * lift;
  // 1) широкая «подушка» — тёмный низ/право
  ctx.shadowColor = rgba(col.rim, 0.34);
  ctx.shadowBlur = 78 * S; ctx.shadowOffsetX = -6 * S; ctx.shadowOffsetY = -36 * S;
  ctx.fill(frame, 'evenodd');
  // 2) тонкий светлый кант сверху-слева
  ctx.shadowColor = `rgba(255,255,255,${(0.50 * hl).toFixed(3)})`;
  ctx.shadowBlur = 11 * S; ctx.shadowOffsetX = 3 * S; ctx.shadowOffsetY = 9 * S * hl;
  ctx.fill(frame, 'evenodd');
  // 3) тонкий тёмный кант снизу-справа
  ctx.shadowColor = rgba(col.rim, 0.42);
  ctx.shadowBlur = 16 * S; ctx.shadowOffsetX = -3 * S; ctx.shadowOffsetY = -10 * S;
  ctx.fill(frame, 'evenodd');

  ctx.shadowColor = 'transparent'; ctx.shadowBlur = 0; ctx.shadowOffsetX = ctx.shadowOffsetY = 0;

  // мягкий блик, смещающийся при наклоне
  const sx = cx - w*0.22 - rot*w*5, sy = cy - h*0.42;
  const rg = ctx.createRadialGradient(sx, sy, 0, sx, sy, Math.max(w, h) * 0.8);
  rg.addColorStop(0, `rgba(255,255,255,${(0.20 + 0.14*lift).toFixed(3)})`);
  rg.addColorStop(1, 'rgba(255,255,255,0)');
  ctx.fillStyle = rg;
  ctx.fillRect(blk.minx, blk.miny, w, h);

  // матовая фактура, едет вместе с блоком
  if (grainPat) {
    if (grainPat.setTransform) grainPat.setTransform(new DOMMatrix([Math.max(1, S), 0, 0, Math.max(1, S), cx, cy]));
    ctx.fillStyle = grainPat;
    ctx.fillRect(blk.minx, blk.miny, w, h);
  }
  ctx.restore();
}

/* ───────────────────────── Кадр ───────────────────────── */
const tmp = new Float64Array(11);

function render(time) {
  const u  = ((time % LOOP) + LOOP) % LOOP;
  const j  = Math.floor(u / CFG.stepPeriod) % STEPS;
  const ts = u - j * CFG.stepPeriod;
  const head = (2 * j) % STEPS;            // кто ходит первым — меняется каждый шаг

  // фон
  const bg = ctx.createRadialGradient(CW*0.5, CH*0.42, 0, CW*0.5, CH*0.42, Math.hypot(CW, CH) * 0.6);
  bg.addColorStop(0, ALWAYS ? '#fff' : '#fbfcfe'); bg.addColorStop(1, ALWAYS ? '#fff' : '#e8ecf3');
  ctx.fillStyle = bg;
  ctx.fillRect(0, 0, CW, CH);

  const blks = [];
  for (let b = 0; b < STEPS; b++) {
    const k  = (b + j) % STEPS, k2 = (k + 1) % STEPS;
    const rank = (head - k + STEPS) % STEPS;
    const tl = clamp((ts - rank * CFG.stagger) / CFG.moveDur, 0, 1);
    const e  = ease(tl);
    const A = SLOTS[k], B = SLOTS[k2];
    for (let i = 0; i < 11; i++) tmp[i] = A[i] + (B[i] - A[i]) * e;

    const vel  = (ease(Math.min(tl + 0.01, 1)) - ease(Math.max(tl - 0.01, 0))) / 0.02;
    const dirX = (B[0] + B[2]) - (A[0] + A[2]) + 0.001 * ((B[1] + B[3]) - (A[1] + A[3]));
    const rot  = (dirX >= 0 ? 1 : -1) * vel * 0.03;               // микро-ротация
    const lift = Math.pow(Math.sin(Math.PI * clamp((tl - 0.05) / 0.85, 0, 1)), 1.15);
    const sc   = 1 + 0.045 * lift;

    const pts = shapePts(tmp);
    const cx = (tmp[0] + tmp[2]) / 2, cy = (tmp[1] + tmp[3]) / 2 - lift * 8;
    const c = Math.cos(rot), s = Math.sin(rot);
    const dev = pts.map(p => {
      const px = (p[0] - (tmp[0] + tmp[2]) / 2) * sc, py = (p[1] - (tmp[1] + tmp[3]) / 2) * sc;
      return [OX + (cx + px*c - py*s) * S, OY + (cy + px*s + py*c) * S];
    });
    const geo = buildPath(clean(dev));
    if (!geo) continue;
    geo.col = BLOCKS[b]; geo.lift = lift; geo.rot = rot; geo.idx = b;
    blks.push(geo);
  }

  // слой A: лежащие блоки (тени → тела), слой B: приподнятые, по возрастанию высоты
  const EPS = 0.015;
  const wgt = l => smooth(clamp(l / 0.2, 0, 1));
  for (const k of blks) shadowPass(k, 1 - wgt(k.lift));
  for (const k of blks) if (k.lift <= EPS) bodyPass(k);
  const up = blks.filter(k => k.lift > EPS).sort((p, q) => p.lift - q.lift || p.idx - q.idx);
  for (const k of up) { shadowPass(k, wgt(k.lift)); bodyPass(k); }
}

/* ───────────────────────── Оверлей и жизненный цикл ───────────────────────── */
const reduced = window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
let overlay = null, raf = 0, last = 0, ema = 16.7, slow = 0, t0 = 0, shownAt = 0, done = false;

function adapt(now) {            // авто-снижение разрешения, если не держим ~60 FPS
  if (last) {
    const dt = Math.min(now - last, 50);
    ema += (dt - ema) * 0.1;
    if (ema > 22) { if (++slow > 40 && dpr > 1) { dpr = Math.max(1, dpr - 0.25); slow = 0; ema = 16.7; resize(); } }
    else slow = 0;
  }
  last = now;
}
function tick(now) {
  render((now - t0) / 1000);
  adapt(now);
  raf = requestAnimationFrame(tick);
}

function show(instant, phaseStart) {
  if (done || overlay) return;
  const host = root;
  overlay = document.createElement('div');
  overlay.setAttribute('role', 'status');
  overlay.setAttribute('aria-label', 'Loading');
  overlay.style.cssText = 'position:fixed;inset:0;z-index:2147483647;background:'+(ALWAYS ? '#fff' : '#eef1f6')+';opacity:'+(instant ? 1 : 0)+';transition:opacity .25s ease';
  canvas.style.cssText = 'position:absolute;inset:0;width:100%;height:100%;display:block';
  overlay.appendChild(canvas);
  host.appendChild(overlay);
  resize();
  window.addEventListener('resize', resize);
  shownAt = performance.now();
  t0 = shownAt - (phaseStart ? Date.now() - phaseStart : 0);
  if (reduced) render(0); else raf = requestAnimationFrame(tick);
  if (!instant) requestAnimationFrame(() => { overlay.style.opacity = '1'; });
}

function hide() {
  if (done) return;
  done = true;
  clearTimeout(showTimer); clearTimeout(maxTimer);
  if (!overlay) return;
  const o = overlay;
  overlay = null;
  o.style.opacity = '0';
  setTimeout(() => {
    cancelAnimationFrame(raf);
    window.removeEventListener('resize', resize);
    o.remove();
  }, 300);
}

function onLoad() {
  if (!overlay) { hide(); return; }
  if (overlay.dataset.nav) { hide(); return; }
  const left = MIN_SHOWN - (performance.now() - shownAt);
  if (left > 0) setTimeout(hide, left); else hide();
}

let showTimer = 0, maxTimer = 0;
function arm() {
  clearTimeout(showTimer); clearTimeout(maxTimer);
  if (navStart) { show(true, navStart); navStart = 0; }
  else showTimer = setTimeout(function wait() {
    if (done) return;
    if (document.body) show(); else setTimeout(wait, 30);
  }, ALWAYS ? 0 : SHOW_AFTER);
  maxTimer = ALWAYS ? 0 : setTimeout(hide, MAX_SHOWN);
}
if (FRESH || ALWAYS) { arm(); if (!ALWAYS) window.addEventListener('load', onLoad); }
window.addEventListener('pageshow', e => { if (e.persisted && !ALWAYS) hide(); });

// клик по внутренней ссылке: сразу закрываем старую страницу лоадером, не дожидаясь сети
window.TeamhubLoader = {
  start() {
    if (ALWAYS || overlay) return;
    done = false;
    show(false);
    try { sessionStorage.setItem(NAV_KEY, String(Date.now())); } catch (e) {}
    if (overlay) overlay.dataset.nav = '1';
    clearTimeout(maxTimer);
    maxTimer = setTimeout(hide, MAX_SHOWN);   // переход не состоялся — не зависаем
  }
};
})();
