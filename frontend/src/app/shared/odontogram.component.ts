import { Component, EventEmitter, Input, OnChanges, Output } from '@angular/core';
import { OdontogramEntry } from '../core/models';

type Pos = 'top' | 'right' | 'bottom' | 'left' | 'center';

interface ToothView {
  n: number;
  x: number;
  y: number;
  upper: boolean;
  shape: string;
  faces: { pos: Pos; face: string; d: string }[];
}

export interface Tool { code: string; label: string; scope: 'face' | 'tooth'; tone: 'lesion' | 'done' | 'neutral'; }

export const TOOLS: Tool[] = [
  { code: 'CARIES', label: 'Caries', scope: 'face', tone: 'lesion' },
  { code: 'FRACTURA', label: 'Fractura', scope: 'face', tone: 'lesion' },
  { code: 'RESTAURACION_DEFECTUOSA', label: 'Restauración defectuosa', scope: 'face', tone: 'lesion' },
  { code: 'RESTAURACION', label: 'Restauración', scope: 'face', tone: 'done' },
  { code: 'SELLANTE', label: 'Sellante', scope: 'face', tone: 'done' },
  { code: 'EXTRACCION_INDICADA', label: 'Extracción indicada', scope: 'tooth', tone: 'lesion' },
  { code: 'ENDODONCIA_INDICADA', label: 'Endodoncia indicada', scope: 'tooth', tone: 'lesion' },
  { code: 'CORONA_INDICADA', label: 'Corona indicada', scope: 'tooth', tone: 'lesion' },
  { code: 'MOVILIDAD', label: 'Movilidad', scope: 'tooth', tone: 'lesion' },
  { code: 'ENDODONCIA', label: 'Endodoncia', scope: 'tooth', tone: 'done' },
  { code: 'CORONA', label: 'Corona', scope: 'tooth', tone: 'done' },
  { code: 'IMPLANTE', label: 'Implante', scope: 'tooth', tone: 'done' },
  { code: 'AUSENTE', label: 'Pieza ausente', scope: 'tooth', tone: 'neutral' },
];

export const FACE_NAME: Record<string, string> = {
  V: 'Vestibular', L: 'Lingual / palatino', M: 'Mesial', D: 'Distal', O: 'Oclusal / incisal', X: 'Pieza completa',
};

const LABEL: Record<string, string> = Object.fromEntries(TOOLS.map((t) => [t.code, t.label]));

const SHAPES = {
  incisor: 'M13 1C12 8 11 14 10 18L9 24C9 28 12 30 18 30C24 30 27 28 27 24L26 18C25 14 24 8 23 1C20 0 16 0 13 1Z',
  premolar: 'M14 1C12 8 10 14 9 18L7 24C7 28 11 30 18 30C25 30 29 28 29 24L27 18C26 14 24 8 22 1C19 0 17 0 14 1Z',
  molar: 'M8 2C8 9 7 14 6 18L4 23C4 28 9 30 18 30C27 30 32 28 32 23L30 18C29 14 28 9 28 2C26 1 24 3 23 9C22 13 20 14 18 14C16 14 14 13 13 9C12 3 10 1 8 2Z',
};

const FACE_PATHS: Record<Pos, string> = {
  top: 'M0 0H36L25 11H11Z',
  right: 'M36 0V36L25 25V11Z',
  bottom: 'M0 36H36L25 25H11Z',
  left: 'M0 0L11 11V25L0 36Z',
  center: 'M11 11H25V25H11Z',
};

const COL = 44;
const MID_GAP = 22;

@Component({
  selector: 'app-odontogram',
  standalone: true,
  template: `
    <div class="odo">
      @if (editable) {
        <div class="palette" role="toolbar" aria-label="Herramientas del odontograma">
          <div class="group">
            <span class="glabel">Hallazgos</span>
            @for (t of lesionTools; track t.code) {
              <button type="button" class="tool lesion" [class.on]="tool === t.code" (click)="pick(t.code)">
                <span class="sw" [attr.data-code]="t.code"></span>{{ t.label }}
              </button>
            }
          </div>
          <div class="group">
            <span class="glabel">Tratamientos</span>
            @for (t of doneTools; track t.code) {
              <button type="button" class="tool done" [class.on]="tool === t.code" (click)="pick(t.code)">
                <span class="sw" [attr.data-code]="t.code"></span>{{ t.label }}
              </button>
            }
            <button type="button" class="tool" [class.on]="tool === 'AUSENTE'" (click)="pick('AUSENTE')">
              <span class="sw" data-code="AUSENTE"></span>Ausente
            </button>
            <button type="button" class="tool eraser" [class.on]="tool === 'BORRAR'" (click)="pick('BORRAR')">
              <span class="sw" data-code="BORRAR"></span>Borrar marca
            </button>
          </div>
          <p class="hint">
            @if (currentTool?.scope === 'tooth') { Toca cualquier parte de la pieza para marcarla completa. }
            @else if (tool === 'BORRAR') { Toca una cara o pieza marcada para quitar la marca. }
            @else { Toca la cara de la pieza: {{ currentTool?.label?.toLowerCase() }}. Tocarla de nuevo la quita. }
          </p>
        </div>
      }

      <div class="chart-wrap">
        <div class="dentition seg no-print">
          <button type="button" [class.on]="!deciduous" (click)="setDeciduous(false)">Permanente</button>
          <button type="button" [class.on]="deciduous" (click)="setDeciduous(true)">Decidua</button>
        </div>
        <svg class="chart" [attr.viewBox]="viewBox" role="img" aria-label="Odontograma del paciente">
          <defs>
            <pattern id="hatch-gum" width="5" height="5" patternUnits="userSpaceOnUse" patternTransform="rotate(45)">
              <rect width="5" height="5" fill="#fbe8ed"></rect><rect width="2.2" height="5" fill="#b8405e"></rect>
            </pattern>
            <pattern id="hatch-blue" width="5" height="5" patternUnits="userSpaceOnUse" patternTransform="rotate(45)">
              <rect width="5" height="5" fill="#e6edf9"></rect><rect width="2.2" height="5" fill="#2f5da8"></rect>
            </pattern>
            <pattern id="defect" width="6" height="6" patternUnits="userSpaceOnUse">
              <rect width="6" height="6" fill="#2f5da8"></rect><rect y="2" width="6" height="2" fill="#b8405e"></rect>
            </pattern>
          </defs>

          <line class="midline" [attr.x1]="midX" [attr.x2]="midX" y1="4" [attr.y2]="height - 4"></line>
          <line class="midline" x1="0" [attr.x2]="width" y1="104" y2="104"></line>
          <text class="qlabel" [attr.x]="midX - 8" y="100" text-anchor="end">Derecha del paciente</text>
          <text class="qlabel" [attr.x]="midX + 8" y="100">Izquierda</text>

          @for (t of teeth; track t.n) {
            <g class="tooth" [class.sel]="selected === t.n" [class.missing]="whole(t.n) === 'AUSENTE'"
               [attr.transform]="'translate(' + t.x + ',' + t.y + ')'">
              <title>Pieza {{ t.n }}{{ summaryFor(t.n) }}</title>
              <!-- número -->
              <text class="num" x="20" [attr.y]="t.upper ? 11 : 97" text-anchor="middle" (click)="clickTooth(t.n)">{{ t.n }}</text>
              <!-- silueta de la pieza -->
              <g [attr.transform]="t.upper ? 'translate(2,15)' : 'translate(2,81) scale(1,-1)'" (click)="clickTooth(t.n)" class="glyph">
                <path [attr.d]="t.shape" [class]="'shape ' + glyphClass(t.n)"></path>
                @if (whole(t.n) === 'ENDODONCIA' || whole(t.n) === 'ENDODONCIA_INDICADA') {
                  <line x1="18" y1="2" x2="18" y2="25" [class]="whole(t.n) === 'ENDODONCIA' ? 'mk blue' : 'mk red'"></line>
                }
              </g>
              @if (whole(t.n) === 'IMPLANTE') {
                <text class="imp" x="20" [attr.y]="t.upper ? 33 : 69" text-anchor="middle">IMP</text>
              }
              @if (whole(t.n) === 'MOVILIDAD') {
                <text class="mov" x="38" [attr.y]="t.upper ? 11 : 97" text-anchor="end">M</text>
              }
              <!-- caras -->
              <g [attr.transform]="t.upper ? 'translate(2,50)' : 'translate(2,8)'">
                @for (f of t.faces; track f.pos) {
                  <path class="face" [attr.d]="f.d" [attr.fill]="faceFill(t.n, f.face)" (click)="clickFace(t.n, f.face)">
                    <title>{{ t.n }} · {{ faceName(f.face) }}{{ faceSummary(t.n, f.face) }}</title>
                  </path>
                }
                @if (whole(t.n) === 'CORONA' || whole(t.n) === 'CORONA_INDICADA') {
                  <circle cx="18" cy="18" r="21" [class]="whole(t.n) === 'CORONA' ? 'ring blue' : 'ring red'"></circle>
                }
              </g>
              @if (whole(t.n) === 'EXTRACCION_INDICADA' || whole(t.n) === 'AUSENTE') {
                <g [class]="whole(t.n) === 'AUSENTE' ? 'cross grey' : 'cross red'">
                  <line x1="3" [attr.y1]="t.upper ? 16 : 6" x2="37" [attr.y2]="t.upper ? 86 : 80"></line>
                  <line x1="37" [attr.y1]="t.upper ? 16 : 6" x2="3" [attr.y2]="t.upper ? 86 : 80"></line>
                </g>
              }
            </g>
          }
        </svg>
      </div>

      @if (selected) {
        <div class="detail">
          <strong>Pieza {{ selected }}</strong>
          @if (marksOf(selected).length === 0) { <span class="muted">sin hallazgos registrados</span> }
          @for (m of marksOf(selected); track m.face) {
            <span class="chip" [class.lesion]="isLesion(m.condition)" [class.done]="!isLesion(m.condition)">
              {{ faceName(m.face) }}: {{ label(m.condition) }}
              @if (editable) {
                <button type="button" (click)="remove(m.tooth, m.face)" [attr.aria-label]="'Quitar ' + label(m.condition)">×</button>
              }
            </span>
          }
        </div>
      }

      <div class="legend">
        <span><i class="lg red"></i>Rojo: hallazgo o tratamiento por realizar</span>
        <span><i class="lg blue"></i>Azul: tratamiento existente o realizado</span>
        <span class="muted">{{ lesionCount }} hallazgos · {{ doneCount }} tratamientos</span>
      </div>
    </div>
  `,
  styles: [`
    .odo { display: flex; flex-direction: column; gap: 14px; }
    .palette { display: flex; flex-direction: column; gap: 10px; padding: 14px; background: var(--surface-2); border: 1px solid var(--line); border-radius: var(--radius); }
    .group { display: flex; flex-wrap: wrap; gap: 6px; align-items: center; }
    .glabel { font-size: 12px; font-weight: 600; color: var(--muted); width: 92px; }
    .tool { display: inline-flex; align-items: center; gap: 7px; height: 30px; padding: 0 10px; font: inherit; font-size: 12.5px; font-weight: 600;
      border: 1px solid var(--line-strong); border-radius: 999px; background: var(--surface); color: var(--ink-2); cursor: pointer; }
    .tool:hover { border-color: var(--ink-2); }
    .tool.on { background: var(--night); color: #fff; border-color: var(--night); }
    .sw { width: 12px; height: 12px; border-radius: 3px; border: 1px solid rgba(0,0,0,.15); background: #fff; }
    .sw[data-code=CARIES] { background: #b8405e; }
    .sw[data-code=FRACTURA] { background: repeating-linear-gradient(45deg, #b8405e 0 2px, #fbe8ed 2px 4px); }
    .sw[data-code=RESTAURACION_DEFECTUOSA] { background: repeating-linear-gradient(0deg, #2f5da8 0 2px, #b8405e 2px 4px); }
    .sw[data-code=RESTAURACION] { background: #2f5da8; }
    .sw[data-code=SELLANTE] { background: repeating-linear-gradient(45deg, #2f5da8 0 2px, #e6edf9 2px 4px); }
    .sw[data-code=EXTRACCION_INDICADA], .sw[data-code=ENDODONCIA_INDICADA], .sw[data-code=CORONA_INDICADA], .sw[data-code=MOVILIDAD] { border: 2px solid #b8405e; border-radius: 50%; }
    .sw[data-code=ENDODONCIA], .sw[data-code=CORONA], .sw[data-code=IMPLANTE] { border: 2px solid #2f5da8; border-radius: 50%; }
    .sw[data-code=AUSENTE] { background: linear-gradient(45deg, transparent 45%, #687f85 45% 55%, transparent 55%); }
    .sw[data-code=BORRAR] { background: #fff; border-style: dashed; }
    .hint { font-size: 12.5px; color: var(--muted); margin: 0; padding-left: 98px; }

    .chart-wrap { position: relative; background: var(--surface); border: 1px solid var(--line); border-radius: var(--radius); padding: 40px 12px 12px; overflow-x: auto; }
    .dentition { position: absolute; top: 8px; right: 8px; }
    .dentition button { height: 28px; font-size: 12px; }
    .chart { display: block; width: 100%; min-width: 620px; height: auto; user-select: none; }
    .midline { stroke: var(--line-strong); stroke-dasharray: 3 4; }
    .qlabel { font-size: 9px; fill: var(--muted); }
    .num { font-size: 11px; font-weight: 600; fill: var(--ink-2); cursor: pointer; font-variant-numeric: tabular-nums; }
    .tooth.sel .num { fill: var(--teal); }
    .glyph { cursor: pointer; }
    .shape { fill: #fbfcfc; stroke: #9fb1b5; stroke-width: 1.1; }
    .shape.lesion { stroke: #b8405e; }
    .shape.done { stroke: #2f5da8; }
    .shape.implant { stroke: #2f5da8; stroke-dasharray: 2.5 2; }
    .face { stroke: #8fa3a8; stroke-width: 1; cursor: pointer; transition: fill .1s; }
    .face:hover { stroke: var(--night); stroke-width: 1.6; }
    .tooth.sel .face { stroke: var(--teal); }
    .tooth.missing .face, .tooth.missing .shape { opacity: .35; }
    .mk { stroke-width: 2.4; stroke-linecap: round; }
    .mk.blue, .ring.blue { stroke: #2f5da8; }
    .mk.red, .ring.red { stroke: #b8405e; }
    .ring { fill: none; stroke-width: 2.2; }
    .cross line { stroke-width: 2.6; stroke-linecap: round; pointer-events: none; }
    .cross.red line { stroke: #b8405e; }
    .cross.grey line { stroke: #687f85; }
    .imp { font-size: 8.5px; font-weight: 700; fill: #2f5da8; pointer-events: none; }
    .mov { font-size: 10px; font-weight: 700; fill: #b8405e; }

    .detail { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; font-size: var(--step--1); }
    .chip { display: inline-flex; align-items: center; gap: 6px; height: 26px; padding: 0 10px; border-radius: 999px; font-weight: 600; font-size: 12.5px; }
    .chip.lesion { background: var(--gum-soft); color: var(--gum); }
    .chip.done { background: var(--restore-soft); color: var(--restore); }
    .chip button { border: 0; background: none; color: inherit; font-size: 15px; cursor: pointer; padding: 0; line-height: 1; }
    .legend { display: flex; flex-wrap: wrap; gap: 18px; font-size: 12.5px; color: var(--ink-2); }
    .lg { display: inline-block; width: 10px; height: 10px; border-radius: 2px; margin-right: 6px; vertical-align: -1px; }
    .lg.red { background: #b8405e; } .lg.blue { background: #2f5da8; }
    @media (max-width: 720px) { .glabel { width: 100%; } .hint { padding-left: 0; } }
  `],
})
export class OdontogramComponent implements OnChanges {
  @Input() entries: OdontogramEntry[] = [];
  @Input() editable = false;
  @Output() entriesChange = new EventEmitter<OdontogramEntry[]>();

  readonly lesionTools = TOOLS.filter((t) => t.tone === 'lesion');
  readonly doneTools = TOOLS.filter((t) => t.tone === 'done');

  tool = 'CARIES';
  selected: number | null = null;
  deciduous = false;
  teeth: ToothView[] = [];
  width = 0;
  height = 196;
  midX = 0;
  viewBox = '';

  private map = new Map<string, OdontogramEntry>();

  constructor() { this.layout(); }

  ngOnChanges() {
    this.map.clear();
    for (const e of this.entries || []) this.map.set(e.tooth + e.face, e);
    // Si solo hay piezas deciduas registradas, abrir en dentición decidua.
    const list = this.entries || [];
    if (list.length && list.every((e) => e.tooth >= 51)) this.setDeciduous(true);
  }

  get currentTool(): Tool | undefined { return TOOLS.find((t) => t.code === this.tool); }

  setDeciduous(v: boolean) {
    this.deciduous = v;
    this.layout();
  }

  private layout() {
    const upperL = this.deciduous ? [55, 54, 53, 52, 51] : [18, 17, 16, 15, 14, 13, 12, 11];
    const upperR = this.deciduous ? [61, 62, 63, 64, 65] : [21, 22, 23, 24, 25, 26, 27, 28];
    const lowerL = this.deciduous ? [85, 84, 83, 82, 81] : [48, 47, 46, 45, 44, 43, 42, 41];
    const lowerR = this.deciduous ? [71, 72, 73, 74, 75] : [31, 32, 33, 34, 35, 36, 37, 38];
    const half = upperL.length;
    this.width = half * 2 * COL + MID_GAP + 8;
    this.midX = 4 + half * COL + MID_GAP / 2 - 2;
    this.viewBox = `0 0 ${this.width} ${this.height + 18}`;
    const out: ToothView[] = [];
    const place = (row: number[], startIdx: number, upper: boolean) => {
      row.forEach((n, i) => {
        const idx = startIdx + i;
        const x = 4 + idx * COL + (idx >= half ? MID_GAP : 0);
        const y = upper ? 0 : 108;
        out.push({ n, x, y, upper, shape: this.shapeFor(n), faces: this.facesFor(n, upper) });
      });
    };
    place(upperL, 0, true);
    place(upperR, half, true);
    place(lowerL, 0, false);
    place(lowerR, half, false);
    this.teeth = out;
  }

  private shapeFor(n: number): string {
    const unit = n % 10;
    if (n >= 51) return unit <= 3 ? SHAPES.incisor : SHAPES.molar;
    if (unit <= 3) return SHAPES.incisor;
    if (unit <= 5) return SHAPES.premolar;
    return SHAPES.molar;
  }

  /** Mapea la posición dibujada a la cara anatómica según arcada y lado. */
  private facesFor(n: number, upper: boolean): { pos: Pos; face: string; d: string }[] {
    const q = Math.floor(n / 10);
    const patientRight = q === 1 || q === 4 || q === 5 || q === 8; // se dibuja a la izquierda
    const map: Record<Pos, string> = {
      top: upper ? 'V' : 'L',
      bottom: upper ? 'L' : 'V',
      left: patientRight ? 'D' : 'M',
      right: patientRight ? 'M' : 'D',
      center: 'O',
    };
    return (['top', 'right', 'bottom', 'left', 'center'] as Pos[]).map((pos) => ({ pos, face: map[pos], d: FACE_PATHS[pos] }));
  }

  whole(n: number): string | null {
    return this.map.get(n + 'X')?.condition ?? null;
  }

  glyphClass(n: number): string {
    const w = this.whole(n);
    if (!w) return '';
    if (w === 'IMPLANTE') return 'implant';
    if (w === 'AUSENTE') return '';
    return this.isLesion(w) ? 'lesion' : 'done';
  }

  faceFill(n: number, face: string): string {
    const c = this.map.get(n + face)?.condition;
    switch (c) {
      case 'CARIES': return '#b8405e';
      case 'FRACTURA': return 'url(#hatch-gum)';
      case 'RESTAURACION': return '#2f5da8';
      case 'SELLANTE': return 'url(#hatch-blue)';
      case 'RESTAURACION_DEFECTUOSA': return 'url(#defect)';
      default: return this.selected === n ? '#eef7f6' : '#ffffff';
    }
  }

  isLesion(code: string): boolean { return TOOLS.find((t) => t.code === code)?.tone === 'lesion'; }
  label(code: string): string { return LABEL[code] || code; }
  faceName(f: string): string { return FACE_NAME[f] || f; }

  faceSummary(n: number, face: string): string {
    const c = this.map.get(n + face)?.condition;
    return c ? ': ' + this.label(c) : '';
  }

  summaryFor(n: number): string {
    const marks = this.marksOf(n);
    return marks.length ? ' · ' + marks.map((m) => this.label(m.condition)).join(', ') : '';
  }

  marksOf(n: number): OdontogramEntry[] {
    return [...this.map.values()].filter((e) => e.tooth === n).sort((a, b) => a.face.localeCompare(b.face));
  }

  get lesionCount(): number { return [...this.map.values()].filter((e) => this.isLesion(e.condition)).length; }
  get doneCount(): number { return [...this.map.values()].filter((e) => !this.isLesion(e.condition) && e.condition !== 'AUSENTE').length; }

  pick(code: string) { this.tool = code; }

  clickTooth(n: number) {
    this.selected = n;
    if (!this.editable) return;
    const t = this.currentTool;
    if (this.tool === 'BORRAR') { this.remove(n, 'X'); return; }
    if (t?.scope === 'tooth') this.toggle(n, 'X', t.code);
  }

  clickFace(n: number, face: string) {
    this.selected = n;
    if (!this.editable) return;
    const t = this.currentTool;
    if (this.tool === 'BORRAR') {
      if (this.map.has(n + face)) this.remove(n, face); else this.remove(n, 'X');
      return;
    }
    if (!t) return;
    if (t.scope === 'tooth') this.toggle(n, 'X', t.code);
    else this.toggle(n, face, t.code);
  }

  private toggle(n: number, face: string, code: string) {
    const key = n + face;
    if (this.map.get(key)?.condition === code) this.map.delete(key);
    else this.map.set(key, { tooth: n, face, condition: code });
    this.emit();
  }

  remove(n: number, face: string) {
    if (this.map.delete(n + face)) this.emit();
  }

  private emit() {
    this.entriesChange.emit([...this.map.values()]);
  }
}
