import { ChangeDetectionStrategy, Component, Input, OnChanges } from '@angular/core';
import { soles } from '../core/format';

export interface BarSeries { name: string; color: string; values: number[]; }

/** Gráfico de barras en SVG (sin librerías): agrupadas o apiladas, con ejes y leyenda. */
@Component({
  selector: 'app-bars',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (!labels.length) {
      <div class="none muted small">Sin datos en el período.</div>
    } @else {
      <svg [attr.viewBox]="'0 0 ' + W + ' ' + H" role="img" [attr.aria-label]="ariaLabel">
        @for (t of ticks; track t.v) {
          <line [attr.x1]="padL" [attr.x2]="W - 8" [attr.y1]="t.y" [attr.y2]="t.y" class="grid" />
          <text [attr.x]="padL - 8" [attr.y]="t.y + 4" class="ax" text-anchor="end">{{ t.label }}</text>
        }
        @for (b of bars; track $index) {
          <rect [attr.x]="b.x" [attr.y]="b.y" [attr.width]="b.w" [attr.height]="b.h" [attr.fill]="b.color" rx="2.5">
            <title>{{ b.title }}</title>
          </rect>
        }
        @for (l of xLabels; track l.i) {
          <text [attr.x]="l.x" [attr.y]="H - 8" class="ax" text-anchor="middle">{{ l.text }}</text>
        }
      </svg>
      @if (series.length > 1 || legend) {
        <div class="legend">
          @for (s of series; track s.name) { <span><i [style.background]="s.color"></i>{{ s.name }}</span> }
        </div>
      }
    }
  `,
  styles: [`
    :host { display: block; }
    svg { width: 100%; height: auto; display: block; }
    .grid { stroke: var(--line); stroke-width: 1; }
    .ax { font-size: 11px; fill: var(--muted); font-family: inherit; }
    rect:hover { opacity: .85; }
    .legend { display: flex; flex-wrap: wrap; gap: 6px 16px; justify-content: center; font-size: 12.5px; color: var(--ink-2); margin-top: 4px; }
    .legend i { display: inline-block; width: 10px; height: 10px; border-radius: 3px; margin-right: 6px; vertical-align: -1px; }
    .none { padding: 40px; text-align: center; }
  `],
})
export class BarsComponent implements OnChanges {
  @Input() labels: string[] = [];
  @Input() series: BarSeries[] = [];
  @Input() stacked = false;
  @Input() money = false;
  @Input() legend = false;
  @Input() ariaLabel = 'Gráfico de barras';
  @Input() H = 260;

  W = 720;
  padL = 64;
  padB = 28;
  padT = 12;
  bars: { x: number; y: number; w: number; h: number; color: string; title: string }[] = [];
  ticks: { v: number; y: number; label: string }[] = [];
  xLabels: { i: number; x: number; text: string }[] = [];

  private fmt(v: number): string {
    if (this.money) return v >= 1000 ? 'S/ ' + (v / 1000).toFixed(v >= 10000 ? 0 : 1) + ' k' : 'S/ ' + Math.round(v);
    return String(Math.round(v));
  }

  ngOnChanges() {
    const n = this.labels.length;
    this.W = Math.max(560, Math.min(1100, 90 + n * 34));
    const plotW = this.W - this.padL - 8;
    const plotH = this.H - this.padT - this.padB;
    let max = 0;
    for (let i = 0; i < n; i++) {
      const vals = this.series.map((s) => s.values[i] || 0);
      max = Math.max(max, this.stacked ? vals.reduce((a, b) => a + b, 0) : Math.max(0, ...vals));
    }
    const step = niceStep(max / 4 || 1);
    const top = Math.max(step, Math.ceil(max / step) * step);
    this.ticks = [];
    for (let v = 0; v <= top + 1e-9; v += step) {
      this.ticks.push({ v, y: this.padT + plotH - (v / top) * plotH, label: this.fmt(v) });
    }
    const slot = plotW / Math.max(n, 1);
    const groupW = Math.min(slot * 0.72, 46);
    const k = this.stacked ? 1 : Math.max(this.series.length, 1);
    const bw = groupW / k;
    this.bars = [];
    for (let i = 0; i < n; i++) {
      const x0 = this.padL + i * slot + (slot - groupW) / 2;
      let acc = 0;
      this.series.forEach((s, si) => {
        const v = s.values[i] || 0;
        const h = (v / top) * plotH;
        const x = this.stacked ? x0 : x0 + si * bw;
        const y = this.padT + plotH - h - (this.stacked ? (acc / top) * plotH : 0);
        if (v > 0) {
          this.bars.push({
            x, y, w: Math.max(this.stacked ? groupW : bw - 2, 2), h: Math.max(h, 1), color: s.color,
            title: `${this.labels[i]} · ${s.name}: ${this.money ? soles(v) : v}`,
          });
        }
        acc += v;
      });
    }
    const every = Math.ceil(n / 14);
    this.xLabels = this.labels.map((t, i) => ({ i, x: this.padL + i * slot + slot / 2, text: t })).filter((l) => l.i % every === 0);
  }
}

function niceStep(raw: number): number {
  const p = Math.pow(10, Math.floor(Math.log10(raw)));
  const f = raw / p;
  return (f <= 1 ? 1 : f <= 2 ? 2 : f <= 2.5 ? 2.5 : f <= 5 ? 5 : 10) * p;
}
