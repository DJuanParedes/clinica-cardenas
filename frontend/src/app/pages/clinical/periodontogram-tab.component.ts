import { Component, Input, OnChanges, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Api } from '../../core/api.service';
import { DeleteButtonComponent } from '../../shared/delete-button.component';
import { ToastService } from '../../core/toast.service';
import { apiError, dateTime, shortDate } from '../../core/format';
import { IconComponent } from '../../shared/icon.component';

import { OnlyDirective } from '../../shared/only.directive';
interface Face { mg: number[]; ps: number[]; bleed: boolean[]; plaque: boolean[]; }
interface Tooth { missing: boolean; implant: boolean; mobility: number; furcation: number; v: Face; l: Face; }
interface ExamSummary { id: number; examDate: string; dentistName?: string; plaqueIndex: number; bleedingIndex: number; meanDepth: number; deepSites: number; teethPresent: number; notes?: string; createdAt: string; createdBy: string; }

const UPPER = [18, 17, 16, 15, 14, 13, 12, 11, 21, 22, 23, 24, 25, 26, 27, 28];
const LOWER = [48, 47, 46, 45, 44, 43, 42, 41, 31, 32, 33, 34, 35, 36, 37, 38];
const FURC = new Set([18, 17, 16, 28, 27, 26, 38, 37, 36, 48, 47, 46, 14, 24]);
const blankFace = (): Face => ({ mg: [0, 0, 0], ps: [0, 0, 0], bleed: [false, false, false], plaque: [false, false, false] });
const blankTooth = (): Tooth => ({ missing: false, implant: false, mobility: 0, furcation: 0, v: blankFace(), l: blankFace() });

/** v2.5: periodontograma con índice de placa (%IP), sangrado al sondaje (%SAS) y gráfico de bolsas. */
@Component({
  selector: 'app-periodontogram-tab',
  standalone: true,
  imports: [OnlyDirective, FormsModule, IconComponent, DeleteButtonComponent],
  template: `
    <section class="panel">
      <div class="panel-head">
        <div>
          <h3>Periodontograma</h3>
          <p class="muted small">
            @if (editing) { {{ current?.id ? 'Corrigiendo el examen del ' + date(current.examDate) : 'Nuevo examen' }} · MG positivo = recesión (mm) · PS = profundidad de sondaje (mm) }
            @else if (current) { Examen del {{ date(current.examDate) }} · {{ current.dentistName || current.createdBy }} }
            @else { Aún no hay exámenes periodontales. }
          </p>
        </div>
        <div class="row wrap">
          @if (!editing) {
            @if (exams.length) {
              <select class="input" [ngModel]="current?.id" (ngModelChange)="open($event)" aria-label="Examen">
                @for (x of exams; track x.id) { <option [ngValue]="x.id">{{ date(x.examDate) }} · IP {{ x.plaqueIndex }}% · SAS {{ x.bleedingIndex }}%</option> }
              </select>
              @if (current && isToday(current.createdAt)) { <button class="btn" type="button" (click)="edit()"><app-icon name="edit" [size]="15" /> Corregir</button> }
              @if (current?.id && !editing) { <app-delete-button entity="periodontograms" [recordId]="current.id" what="Periodontograma" [ghost]="true" (deleted)="load()" /> }
            }
            <button class="btn primary" type="button" (click)="newExam()"><app-icon name="plus" [size]="15" /> Nuevo examen</button>
          } @else {
            <input class="input" type="date" [(ngModel)]="examDate" [max]="today" aria-label="Fecha del examen" />
            <button class="btn" type="button" (click)="cancel()">Cancelar</button>
            <button class="btn primary" type="button" (click)="save()" [disabled]="saving">{{ saving ? 'Guardando…' : 'Guardar examen' }}</button>
          }
        </div>
      </div>

      @if (data) {
        <div class="panel-body">
          <div class="kpis">
            <div class="kpi" [class.bad]="ix.plaque > 20"><span>Índice de placa</span><strong>{{ ix.plaque }}%</strong>@if (prev) { <small [class]="delta(ix.plaque, prev.plaqueIndex, true)">{{ diff(ix.plaque, prev.plaqueIndex) }} vs. {{ date(prev.examDate) }}</small> }</div>
            <div class="kpi" [class.bad]="ix.bleeding > 10"><span>Sangrado al sondaje</span><strong>{{ ix.bleeding }}%</strong>@if (prev) { <small [class]="delta(ix.bleeding, prev.bleedingIndex, true)">{{ diff(ix.bleeding, prev.bleedingIndex) }}</small> }</div>
            <div class="kpi"><span>Sondaje medio</span><strong>{{ ix.mean }} mm</strong>@if (prev) { <small [class]="delta(ix.mean, prev.meanDepth, true)">{{ diff(ix.mean, prev.meanDepth, ' mm') }}</small> }</div>
            <div class="kpi" [class.bad]="ix.deep > 0"><span>Sitios ≥ 4 mm</span><strong>{{ ix.deep }}</strong></div>
            <div class="kpi"><span>Piezas presentes</span><strong>{{ ix.teeth }}</strong></div>
          </div>
          <p class="muted small legend">
            <span class="dot b on"></span> Sangrado · <span class="dot p on"></span> Placa · Clic en el número de la pieza: presente → ausente → implante.
            Meta clínica: placa &lt; 20 % y sangrado &lt; 10 %.
          </p>

          @for (arch of arches; track arch.name) {
            <div class="arch">
              <table class="perio">
                <tbody>
                  @for (face of arch.faces; track face.key; let fi = $index) {
                    @if (fi === 1) {
                      <tr class="nums"><th></th>@for (n of arch.teeth; track n) {
                        <td><button type="button" class="tn" [class.miss]="data[n].missing" [class.imp]="data[n].implant" [disabled]="!editing" (click)="cycle(n)" [title]="stateLabel(n)">{{ n }}</button></td>
                      }</tr>
                      <tr><th>Movilidad</th>@for (n of arch.teeth; track n) { <td>@if (!data[n].missing) {
                        @if (editing) { <select class="mini" [(ngModel)]="data[n].mobility" (ngModelChange)="recalc()" [attr.aria-label]="'Movilidad ' + n">@for (m of [0,1,2,3]; track m) { <option [ngValue]="m">{{ m }}</option> }</select> }
                        @else { <span [class.warnv]="data[n].mobility > 0">{{ data[n].mobility || '' }}</span> } } </td> }</tr>
                      <tr><th>Furca</th>@for (n of arch.teeth; track n) { <td>@if (!data[n].missing && furc(n)) {
                        @if (editing) { <select class="mini" [(ngModel)]="data[n].furcation" [attr.aria-label]="'Furca ' + n">@for (m of [0,1,2,3]; track m) { <option [ngValue]="m">{{ m }}</option> }</select> }
                        @else { <span [class.warnv]="data[n].furcation > 0">{{ data[n].furcation ? 'F' + data[n].furcation : '' }}</span> } } </td> }</tr>
                    }
                    <tr class="facehead"><th colspan="17">{{ face.label }}</th></tr>
                    @for (row of rowsFor(fi); track row) {
                      <tr [class]="'r-' + row">
                        <th>{{ rowLabel(row) }}</th>
                        @for (n of arch.teeth; track n) {
                          <td [class.off]="data[n].missing">
                            @if (!data[n].missing) {
                              <div class="trio">
                                @for (i of [0,1,2]; track i) {
                                  @if (row === 'bleed' || row === 'plaque') {
                                    <button type="button" class="dot" [class.b]="row === 'bleed'" [class.p]="row === 'plaque'" [class.on]="side(n, face.key)[row][i]"
                                            [disabled]="!editing" (click)="toggle(n, face.key, row, i)" [attr.aria-label]="rowLabel(row) + ' ' + n + ' sitio ' + (i + 1)"></button>
                                  } @else if (editing) {
                                    <input class="cell" type="number" [min]="row === 'mg' ? -5 : 0" max="15" [ngModel]="side(n, face.key)[row][i]"
                                           (ngModelChange)="setNum(n, face.key, row, i, $event)" [attr.aria-label]="rowLabel(row) + ' ' + n + ' sitio ' + (i + 1)" />
                                  } @else {
                                    <span class="v" [class.d4]="row === 'ps' && side(n, face.key).ps[i] >= 4" [class.d6]="row === 'ps' && side(n, face.key).ps[i] >= 6">{{ side(n, face.key)[row][i] || '' }}</span>
                                  }
                                }
                              </div>
                            }
                          </td>
                        }
                      </tr>
                    }
                    <tr class="chart"><th></th><td colspan="16">
                      <svg [attr.viewBox]="'0 0 ' + (arch.teeth.length * 60) + ' 90'" preserveAspectRatio="none" role="img" [attr.aria-label]="'Gráfico ' + face.label">
                        <line x1="0" [attr.x2]="arch.teeth.length * 60" y1="14" y2="14" class="cej" />
                        @for (n of arch.teeth; track n; let ti = $index) {
                          @if (!data[n].missing) {
                            @for (i of [0,1,2]; track i) {
                              <rect [attr.x]="ti * 60 + 8 + i * 16" [attr.y]="14 + side(n, face.key).mg[i] * 5" width="12"
                                    [attr.height]="max0(side(n, face.key).ps[i]) * 5" [attr.class]="side(n, face.key).ps[i] >= 6 ? 'pk d6' : side(n, face.key).ps[i] >= 4 ? 'pk d4' : 'pk'" />
                            }
                            <polyline [attr.points]="pts(n, face.key, ti)" class="gm" />
                          } @else { <line [attr.x1]="ti * 60 + 10" [attr.x2]="ti * 60 + 50" y1="8" y2="82" class="x" /> }
                        }
                      </svg>
                    </td></tr>
                  }
                </tbody>
              </table>
            </div>
          }
          <div class="field">
            <label for="pn">Notas del examen</label>
            @if (editing) { <textarea id="pn" class="input" rows="2" [(ngModel)]="notes" maxlength="500" placeholder="Diagnóstico periodontal, plan (raspado, control en 3 meses)…" appOnly="text"></textarea> }
            @else { <p>{{ current?.notes || '—' }}</p> }
          </div>
        </div>
      } @else if (!loading) {
        <div class="empty"><p>Registra el primer examen periodontal: profundidad de sondaje, margen gingival, sangrado y placa por pieza.</p></div>
      }
    </section>
  `,
  styles: [`
    .kpis { display: grid; grid-template-columns: repeat(auto-fit, minmax(140px, 1fr)); gap: 10px; margin-bottom: 8px; }
    .kpi { border: 1px solid var(--line); border-radius: 12px; padding: 10px 12px; display: grid; gap: 2px; }
    .kpi span { font-size: 12px; color: var(--muted); } .kpi strong { font-size: 22px; }
    .kpi.bad { border-color: #efc3cf; background: var(--gum-soft); }
    .kpi small.up { color: var(--gum); } .kpi small.down { color: var(--green); } .kpi small.same { color: var(--muted); }
    .legend { margin: 6px 0 12px; }
    .arch { overflow-x: auto; margin-bottom: 18px; border: 1px solid var(--line); border-radius: 12px; }
    .perio { border-collapse: collapse; min-width: 1100px; width: 100%; font-size: 12px; }
    .perio th { text-align: left; font-weight: 600; color: var(--muted); padding: 2px 8px; white-space: nowrap; width: 90px; }
    .perio td { text-align: center; padding: 1px 2px; border-left: 1px solid #eef2f4; width: 64px; min-width: 64px; }
    .perio .chart td { width: auto; }
    .perio td.off { background: repeating-linear-gradient(135deg, #f6f8f9, #f6f8f9 4px, #fff 4px, #fff 8px); }
    .facehead th { background: var(--teal-soft); color: var(--teal-strong); font-size: 11px; text-transform: uppercase; letter-spacing: .05em; padding: 4px 8px; }
    .nums td { padding: 4px 2px; }
    .tn { width: 100%; border: 1px solid var(--line); border-radius: 6px; background: #fff; font: inherit; font-weight: 800; padding: 3px 0; cursor: pointer; }
    .tn:disabled { cursor: default; color: inherit; }
    .tn.miss { background: #eceff1; color: #9aa5ab; text-decoration: line-through; }
    .tn.imp { background: var(--restore-soft); color: var(--restore); }
    .trio { display: flex; justify-content: center; gap: 2px; }
    .cell { width: 20px; padding: 1px 0; text-align: center; border: 1px solid var(--line); border-radius: 4px; font: inherit; font-size: 12px; -moz-appearance: textfield; }
    .cell::-webkit-outer-spin-button, .cell::-webkit-inner-spin-button { -webkit-appearance: none; margin: 0; }
    .v { width: 20px; display: inline-block; }
    .v.d4 { color: #b26a00; font-weight: 800; } .v.d6 { color: var(--gum); font-weight: 800; }
    .mini { font: inherit; font-size: 12px; padding: 0 2px; border: 1px solid var(--line); border-radius: 4px; }
    .warnv { color: var(--gum); font-weight: 800; }
    .dot { width: 14px; height: 14px; border-radius: 50%; border: 1px solid #c9d3d8; background: #fff; padding: 0; cursor: pointer; }
    .dot:disabled { cursor: default; }
    .dot.b.on { background: #e11d48; border-color: #e11d48; }
    .dot.p.on { background: #2563eb; border-color: #2563eb; }
    .legend .dot { display: inline-block; vertical-align: -2px; }
    .chart td { padding: 0; }
    svg { width: 100%; height: 90px; display: block; }
    .cej { stroke: #9aa5ab; stroke-dasharray: 3 3; stroke-width: 1; }
    .pk { fill: #bfe0ff; } .pk.d4 { fill: #ffd68a; } .pk.d6 { fill: #f8a5b6; }
    .gm { fill: none; stroke: #e11d48; stroke-width: 1.5; }
    .x { stroke: #c9d3d8; stroke-width: 2; }
  `],
})
export class PeriodontogramTabComponent implements OnChanges {
  @Input() patientId!: number;
  private api = inject(Api);
  private toast = inject(ToastService);

  exams: ExamSummary[] = [];
  current: any = null;
  data: Record<number, Tooth> | null = null;
  editing = false;
  saving = false;
  loading = true;
  notes = '';
  examDate = '';
  today = new Date().toLocaleDateString('en-CA');
  ix = { plaque: 0, bleeding: 0, mean: 0, deep: 0, teeth: 0 };
  arches = [
    { name: 'sup', teeth: UPPER, faces: [{ key: 'v' as const, label: 'Superior · vestibular' }, { key: 'l' as const, label: 'Superior · palatino' }] },
    { name: 'inf', teeth: LOWER, faces: [{ key: 'l' as const, label: 'Inferior · lingual' }, { key: 'v' as const, label: 'Inferior · vestibular' }] },
  ];

  get prev(): ExamSummary | null {
    const i = this.current?.id ? this.exams.findIndex((x) => x.id === this.current.id) : -1;
    return this.editing && !this.current?.id ? this.exams[0] || null : i >= 0 ? this.exams[i + 1] || null : null;
  }

  ngOnChanges() { this.load(); }

  load(openId?: number) {
    this.loading = true;
    this.api.periodontograms(this.patientId).subscribe((l) => {
      this.exams = l; this.loading = false;
      const id = openId ?? l[0]?.id;
      if (id) this.open(id); else { this.current = null; this.data = null; }
    });
  }

  open(id: number) {
    this.api.periodontogram(this.patientId, id).subscribe((x) => { this.current = x; this.data = this.toMap(x.data); this.notes = x.notes || ''; this.recalc(); });
  }

  newExam() {
    // Se copian las piezas ausentes, implantes y movilidad del último examen; las mediciones se toman de nuevo.
    const base: Record<number, Tooth> = {};
    for (const n of [...UPPER, ...LOWER]) {
      const t = blankTooth();
      const prev = this.data?.[n];
      if (prev && this.current) { t.missing = prev.missing; t.implant = prev.implant; t.mobility = prev.mobility; }
      base[n] = t;
    }
    this.current = { id: null };
    this.data = base; this.notes = ''; this.examDate = this.today; this.editing = true; this.recalc();
  }

  edit() { this.examDate = this.current.examDate; this.editing = true; }
  cancel() { this.editing = false; if (this.exams.length) this.open(this.current?.id || this.exams[0].id); else { this.data = null; this.current = null; } }

  save() {
    this.saving = true;
    const body = { examDate: this.examDate, notes: this.notes, data: this.data };
    const req = this.current?.id ? this.api.updatePeriodontogram(this.patientId, this.current.id, body) : this.api.createPeriodontogram(this.patientId, body);
    req.subscribe({
      next: (x) => { this.saving = false; this.editing = false; this.toast.ok('Periodontograma guardado.'); this.load(x.id); },
      error: (err) => { this.saving = false; this.toast.error(apiError(err).message); },
    });
  }

  private toMap(d: Record<string, Tooth>): Record<number, Tooth> {
    const m: Record<number, Tooth> = {};
    for (const n of [...UPPER, ...LOWER]) m[n] = d?.[String(n)] || blankTooth();
    return m;
  }

  side(n: number, k: 'v' | 'l') { return this.data![n][k] as any; }
  rowsFor(fi: number) { return fi === 0 ? ['bleed', 'plaque', 'mg', 'ps'] : ['ps', 'mg', 'plaque', 'bleed']; }
  rowLabel(r: string) { return r === 'bleed' ? 'Sangrado' : r === 'plaque' ? 'Placa' : r === 'mg' ? 'Margen gingival' : 'Sondaje'; }
  furc(n: number) { return FURC.has(n); }
  stateLabel(n: number) { const t = this.data![n]; return t.missing ? 'Ausente' : t.implant ? 'Implante' : 'Presente'; }

  cycle(n: number) {
    const t = this.data![n];
    if (!t.missing && !t.implant) t.missing = true;
    else if (t.missing) { t.missing = false; t.implant = true; }
    else t.implant = false;
    this.recalc();
  }
  toggle(n: number, k: 'v' | 'l', row: string, i: number) { const f = this.side(n, k); f[row][i] = !f[row][i]; this.recalc(); }
  setNum(n: number, k: 'v' | 'l', row: string, i: number, v: any) {
    const num = v === '' || v === null ? 0 : Math.round(Number(v));
    this.side(n, k)[row][i] = isNaN(num) ? 0 : Math.max(row === 'mg' ? -5 : 0, Math.min(15, num));
    this.recalc();
  }

  recalc() {
    if (!this.data) return;
    let teeth = 0, sites = 0, pl = 0, bl = 0, deep = 0, sum = 0;
    for (const t of Object.values(this.data)) {
      if (t.missing) continue;
      teeth++;
      for (const f of [t.v, t.l]) for (let i = 0; i < 3; i++) { sites++; if (f.plaque[i]) pl++; if (f.bleed[i]) bl++; sum += f.ps[i] || 0; if ((f.ps[i] || 0) >= 4) deep++; }
    }
    const r = (x: number) => Math.round(x * 100) / 100;
    this.ix = { plaque: sites ? r(pl * 100 / sites) : 0, bleeding: sites ? r(bl * 100 / sites) : 0, mean: sites ? r(sum / sites) : 0, deep, teeth };
  }

  pts(n: number, k: 'v' | 'l', ti: number) { const f = this.side(n, k); return [0, 1, 2].map((i) => `${ti * 60 + 14 + i * 16},${14 + f.mg[i] * 5}`).join(' '); }
  max0(v: number) { return Math.max(0, v || 0); }
  diff(a: number, b: number, unit = ' pts') { const d = Math.round((a - Number(b)) * 100) / 100; return (d > 0 ? '+' : '') + d + unit; }
  delta(a: number, b: number, lowerIsBetter: boolean) { const d = a - Number(b); return d === 0 ? 'same' : (d < 0) === lowerIsBetter ? 'down' : 'up'; }
  isToday(iso: string) { return iso?.slice(0, 10) === this.today; }
  date = shortDate;
  dt = dateTime;
}
