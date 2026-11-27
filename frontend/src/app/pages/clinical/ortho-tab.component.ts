import { Component, Input, OnChanges, OnDestroy, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Api } from '../../core/api.service';
import { apiError, isoDate, shortDate } from '../../core/format';
import { ToastService } from '../../core/toast.service';
import { ModalComponent } from '../../shared/modal.component';

import { OnlyDirective } from '../../shared/only.directive';
/** v2.8: ortodoncia del paciente: caso, controles y álbumes de fotos por etapa (las fotos son archivos de su ficha). */
@Component({
  selector: 'app-ortho-tab',
  standalone: true,
  imports: [OnlyDirective, FormsModule, ModalComponent],
  template: `
    @if (!opts || !cases) { <div class="skeleton" style="height: 200px"></div> }
    @else if (!c) {
      <section class="panel empty">
        <h3>Sin tratamiento de ortodoncia</h3>
        <p>Registra el caso para llevar sus controles y fotos por etapa.</p>
        <button class="btn primary" type="button" (click)="openCase(null)">Iniciar caso de ortodoncia</button>
      </section>
    } @else {
      @if (cases.length > 1) {
        <div class="seg cases">@for (x of cases; track x.id) { <button type="button" [class.on]="x.id === c.id" (click)="pick(x)">{{ x.startDate.slice(0, 4) }} · {{ x.statusLabel }}</button> }</div>
      }
      <section class="panel head">
        <div class="hl">
          <div><div class="muted small">Aparato</div><strong>{{ c.applianceLabel }}</strong></div>
          <div><div class="muted small">Inicio</div><strong>{{ sd(c.startDate) }}</strong></div>
          <div><div class="muted small">Tiempo</div><strong>{{ c.monthsElapsed }}{{ c.estimatedMonths ? ' de ' + c.estimatedMonths : '' }} meses</strong>
            @if (c.progress != null) { <div class="bar"><span [style.width.%]="c.progress"></span></div> }</div>
          <div><div class="muted small">Etapa actual</div><strong>{{ c.currentStageLabel }}</strong></div>
          <div><div class="muted small">Estado</div><span class="tag" [class.green]="c.status === 'ACTIVO'" [class.blue]="c.status === 'RETENCION'">{{ c.statusLabel }}</span></div>
          <div class="acts"><button class="btn sm" type="button" (click)="openCase(c)">Editar caso</button>
            @if (c.status === 'ACTIVO' || c.status === 'RETENCION') { <button class="btn sm primary" type="button" (click)="openControl()">+ Control</button> }</div>
        </div>
        @if (c.diagnosis || c.objectives || c.angleClass) {
          <div class="dx small">@if (c.angleClass) { <span class="tag plain">{{ opts.classes[c.angleClass] }}</span> }
            @if (c.diagnosis) { <div><strong>Diagnóstico:</strong> {{ c.diagnosis }}</div> }
            @if (c.objectives) { <div><strong>Objetivos:</strong> {{ c.objectives }}</div> }</div>
        }
      </section>

      <section class="panel">
        <div class="panel-head">
          <h3>Fotos por etapa</h3>
          <div class="seg"><button type="button" [class.on]="mode === 'album'" (click)="mode = 'album'">Álbum</button><button type="button" [class.on]="mode === 'compare'" (click)="mode = 'compare'">Comparar etapas</button></div>
        </div>
        @if (mode === 'album') {
          <nav class="stages">
            @for (s of stageList; track s[0]) {
              <button type="button" [class.on]="stage === s[0]" (click)="stage = s[0]">{{ s[1] }} <span class="n">{{ count(s[0]) }}</span></button>
            }
          </nav>
          <div class="grid">
            @for (v of viewList; track v[0]) {
              @let ph = photo(stage, v[0]);
              <div class="slot" [class.has]="ph">
                @if (ph) {
                  <button type="button" class="img" (click)="openViewer(ph)"><img [src]="thumbs[ph.fileId] || ''" [alt]="v[1]" /></button>
                  <div class="cap"><span>{{ v[1] }}</span><span class="muted">{{ sd(ph.takenOn) }}</span></div>
                } @else {
                  <label class="add" [attr.aria-label]="'Subir ' + v[1]">
                    <input type="file" accept="image/*" capture="environment" (change)="upload($event, v[0])" hidden />
                    <span class="plus">+</span><span>{{ v[1] }}</span>
                  </label>
                }
              </div>
            }
          </div>
          @if (extra(stage).length) {
            <div class="more">@for (ph of extra(stage); track ph.id) { <button type="button" class="mini" (click)="openViewer(ph)"><img [src]="thumbs[ph.fileId] || ''" [alt]="ph.title" /></button> }</div>
          }
          <div class="panel-foot small row wrap">
            <span class="muted">También puedes usar fotos que ya están en "Fotos y documentos":</span>
            <button class="btn sm ghost" type="button" (click)="openLink()">Agregar desde sus archivos</button>
          </div>
        } @else {
          <div class="cmp-head"><label class="small strong" for="cv">Vista</label>
            <select id="cv" class="input auto" [(ngModel)]="compareView">@for (v of viewList; track v[0]) { <option [value]="v[0]">{{ v[1] }}</option> }</select></div>
          <div class="cmp">
            @for (s of stageList; track s[0]) {
              @let ph = photo(s[0], compareView);
              @if (ph) { <figure><img [src]="thumbs[ph.fileId] || ''" [alt]="s[1]" (click)="openViewer(ph)" /><figcaption><strong>{{ s[1] }}</strong><span class="muted">{{ sd(ph.takenOn) }}</span></figcaption></figure> }
            }
            @if (!compareCount()) { <p class="muted pad">Aún no hay fotos de esta vista. Súbelas desde el álbum de cada etapa.</p> }
          </div>
        }
      </section>

      <section class="panel">
        <div class="panel-head"><h3>Controles · {{ c.controls.length }}</h3></div>
        <table class="table">
          <thead><tr><th>Fecha</th><th>Etapa</th><th>Arco sup.</th><th>Arco inf.</th><th>Elásticos</th><th>Higiene</th><th>Notas</th><th>Próximo</th></tr></thead>
          <tbody>
            @for (x of c.controls; track x.id) {
              <tr><td class="nowrap">{{ sd(x.controlDate) }}</td><td class="small">{{ opts.stages[x.stage] }}</td><td>{{ x.upperWire || '—' }}</td><td>{{ x.lowerWire || '—' }}</td>
                <td class="small">{{ x.elastics || '—' }}</td><td>@if (x.hygiene) { <span class="tag plain" [class.green]="x.hygiene === 'BUENA'" [class.amber]="x.hygiene === 'REGULAR'" [class.gum]="x.hygiene === 'MALA'">{{ hy[x.hygiene] }}</span> }</td>
                <td class="small">{{ x.notes }}<div class="muted">{{ x.createdBy }}</div></td><td class="small nowrap">{{ x.nextWeeks ? 'en ' + x.nextWeeks + ' sem.' : '—' }}</td></tr>
            } @empty { <tr><td colspan="8" class="muted">Sin controles registrados.</td></tr> }
          </tbody>
        </table>
      </section>
    }

    <!-- caso -->
    <app-modal [open]="caseOpen" [title]="cf.id ? 'Editar caso de ortodoncia' : 'Iniciar caso de ortodoncia'" [width]="640" (close)="caseOpen = false">
      <form class="grid" id="ocf" (ngSubmit)="saveCase()">
        <div class="cols-2">
          <div class="field"><label class="req" for="oa">Aparato</label><select id="oa" name="oa" class="input" [(ngModel)]="cf.appliance" [class.invalid]="e['appliance']"><option value="">Elige…</option>@for (a of entries(opts?.appliances); track a[0]) { <option [value]="a[0]">{{ a[1] }}</option> }</select>@if (e['appliance']) { <span class="err">{{ e['appliance'] }}</span> }</div>
          <div class="field"><label for="ocl">Clase de Angle</label><select id="ocl" name="ocl" class="input" [(ngModel)]="cf.angleClass"><option value="">Sin registrar</option>@for (a of entries(opts?.classes); track a[0]) { <option [value]="a[0]">{{ a[1] }}</option> }</select></div>
          <div class="field"><label class="req" for="osd">Inicio</label><input id="osd" name="osd" class="input" type="date" [(ngModel)]="cf.startDate" [class.invalid]="e['startDate']" />@if (e['startDate']) { <span class="err">{{ e['startDate'] }}</span> }</div>
          <div class="field"><label for="oem">Duración estimada (meses)</label><input id="oem" name="oem" class="input" type="number" min="1" max="60" [(ngModel)]="cf.estimatedMonths" [class.invalid]="e['estimatedMonths']" appOnly="int" /></div>
          @if (cf.id) {
            <div class="field"><label for="ost">Estado</label><select id="ost" name="ost" class="input" [(ngModel)]="cf.status">@for (a of entries(opts?.statuses); track a[0]) { <option [value]="a[0]">{{ a[1] }}</option> }</select></div>
            <div class="field"><label for="ocs">Etapa actual</label><select id="ocs" name="ocs" class="input" [(ngModel)]="cf.currentStage">@for (a of stageList; track a[0]) { <option [value]="a[0]">{{ a[1] }}</option> }</select></div>
          }
        </div>
        <div class="field"><label for="odx">Diagnóstico</label><textarea id="odx" name="odx" class="input" rows="2" [(ngModel)]="cf.diagnosis" maxlength="1000" placeholder="Ej.: apiñamiento moderado, mordida profunda" appOnly="text"></textarea></div>
        <div class="field"><label for="oob">Objetivos del tratamiento</label><textarea id="oob" name="oob" class="input" rows="2" [(ngModel)]="cf.objectives" maxlength="1000" appOnly="text"></textarea></div>
        @if (error) { <div class="err">{{ error }}</div> }
      </form>
      <div modal-actions><button class="btn" type="button" (click)="caseOpen = false">Cancelar</button><button class="btn primary" type="submit" form="ocf" [disabled]="busy">Guardar</button></div>
    </app-modal>

    <!-- control -->
    <app-modal [open]="ctlOpen" title="Registrar control" [subtitle]="c?.applianceLabel" [width]="600" (close)="ctlOpen = false">
      <form class="grid" id="octl" (ngSubmit)="saveControl()">
        <div class="cols-2">
          <div class="field"><label class="req" for="cd">Fecha</label><input id="cd" name="cd" class="input" type="date" [(ngModel)]="ctl.controlDate" [max]="today" [class.invalid]="e['controlDate']" />@if (e['controlDate']) { <span class="err">{{ e['controlDate'] }}</span> }</div>
          <div class="field"><label for="cs">Etapa</label><select id="cs" name="cs" class="input" [(ngModel)]="ctl.stage">@for (a of stageList; track a[0]) { <option [value]="a[0]">{{ a[1] }}</option> }</select></div>
          <div class="field"><label for="cu">Arco superior</label><input id="cu" name="cu" class="input" [(ngModel)]="ctl.upperWire" maxlength="60" placeholder="NiTi 0.016" list="wires" appOnly="text" /></div>
          <div class="field"><label for="cl">Arco inferior</label><input id="cl" name="cl" class="input" [(ngModel)]="ctl.lowerWire" maxlength="60" placeholder="NiTi 0.016" list="wires" appOnly="text" /></div>
          <div class="field"><label for="ce">Elásticos</label><input id="ce" name="ce" class="input" [(ngModel)]="ctl.elastics" maxlength="100" placeholder="Clase II 3/16 bilateral" appOnly="text" /></div>
          <div class="field"><label for="ch">Higiene</label><select id="ch" name="ch" class="input" [(ngModel)]="ctl.hygiene"><option value="">Sin evaluar</option><option value="BUENA">Buena</option><option value="REGULAR">Regular</option><option value="MALA">Mala</option></select></div>
          <div class="field"><label for="cn">Próximo control en (semanas)</label><input id="cn" name="cn" class="input" type="number" min="1" max="26" [(ngModel)]="ctl.nextWeeks" appOnly="int" /></div>
        </div>
        <datalist id="wires"><option value="NiTi 0.012"></option><option value="NiTi 0.014"></option><option value="NiTi 0.016"></option><option value="NiTi 0.016x0.022"></option><option value="NiTi 0.018x0.025"></option><option value="Acero 0.016x0.022"></option><option value="Acero 0.019x0.025"></option><option value="TMA 0.017x0.025"></option></datalist>
        <div class="field"><label for="cno">Notas</label><textarea id="cno" name="cno" class="input" rows="2" [(ngModel)]="ctl.notes" maxlength="1000" placeholder="Se recementó bracket 23, se activó cadeneta…" appOnly="text"></textarea></div>
        @if (error) { <div class="err">{{ error }}</div> }
      </form>
      <div modal-actions><button class="btn" type="button" (click)="ctlOpen = false">Cancelar</button><button class="btn primary" type="submit" form="octl" [disabled]="busy">Guardar control</button></div>
    </app-modal>

    <!-- agregar desde archivos -->
    <app-modal [open]="linkOpen" title="Agregar fotos desde sus archivos" [subtitle]="'Etapa: ' + (opts?.stages[stage] || '')" [width]="720" (close)="linkOpen = false">
      <div class="field"><label for="lv">Vista</label><select id="lv" class="input" [(ngModel)]="linkView">@for (v of viewList; track v[0]) { <option [value]="v[0]">{{ v[1] }}</option> }</select></div>
      <div class="pick">
        @for (f of images; track f.id) {
          <button type="button" class="pf" [class.used]="used(f.id)" [disabled]="used(f.id)" (click)="link(f)"><img [src]="thumbs[f.id] || ''" [alt]="f.title" /><span class="small">{{ f.title }}</span></button>
        } @empty { <p class="muted">No tiene fotos en sus archivos.</p> }
      </div>
    </app-modal>

    <!-- visor -->
    <app-modal [open]="!!viewer" [title]="viewer ? (opts.views[viewer.view] + ' · ' + opts.stages[viewer.stage]) : ''" [subtitle]="viewer ? sd(viewer.takenOn) : ''" [width]="820" (close)="viewer = null">
      @if (viewer) {
        <img class="big" [src]="thumbs[viewer.fileId] || ''" [alt]="viewer.title" />
        <div class="row wrap mv">
          <select class="input auto" [(ngModel)]="viewer.stage" aria-label="Etapa">@for (a of stageList; track a[0]) { <option [value]="a[0]">{{ a[1] }}</option> }</select>
          <select class="input auto" [(ngModel)]="viewer.view" aria-label="Vista">@for (v of viewList; track v[0]) { <option [value]="v[0]">{{ v[1] }}</option> }</select>
          <button class="btn sm" type="button" (click)="movePhoto()">Guardar ubicación</button>
          <button class="btn sm ghost danger" type="button" (click)="unlink()">Quitar del álbum</button>
        </div>
      }
    </app-modal>
  `,
  styles: [`
    .cases { margin-bottom: 10px; }
    .head { margin-bottom: 14px; }
    .hl { display: flex; flex-wrap: wrap; gap: 22px; align-items: center; padding: 14px 18px; }
    .hl .acts { margin-left: auto; display: flex; gap: 6px; }
    .bar { height: 6px; width: 120px; background: var(--line); border-radius: 99px; overflow: hidden; margin-top: 4px; }
    .bar span { display: block; height: 100%; background: var(--teal); }
    .dx { padding: 0 18px 14px; display: grid; gap: 4px; }
    .panel { margin-bottom: 14px; }
    .stages { display: flex; gap: 4px; overflow-x: auto; padding: 10px 14px; border-bottom: 1px solid var(--line); }
    .stages button { border: 1px solid var(--line-strong); background: var(--surface); border-radius: 999px; padding: 6px 12px; font: inherit; font-size: 13px; cursor: pointer; white-space: nowrap; }
    .stages button.on { background: var(--teal-soft); border-color: var(--teal); color: var(--teal-strong); font-weight: 700; }
    .stages .n { font-size: 11px; color: var(--muted); margin-left: 4px; }
    .grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(150px, 1fr)); gap: 10px; padding: 14px; }
    .slot { border: 1px dashed var(--line-strong); border-radius: 12px; overflow: hidden; aspect-ratio: 4 / 3.4; display: grid; background: var(--surface-2); }
    .slot.has { border-style: solid; background: #000; grid-template-rows: 1fr auto; }
    .img { border: 0; padding: 0; background: #000; cursor: zoom-in; overflow: hidden; }
    .img img, .cmp img, .mini img, .pf img { width: 100%; height: 100%; object-fit: cover; display: block; }
    .cap { display: flex; justify-content: space-between; gap: 6px; padding: 4px 8px; background: var(--surface); font-size: 11.5px; }
    .add { display: grid; place-items: center; align-content: center; gap: 4px; cursor: pointer; color: var(--muted); font-size: 12px; text-align: center; padding: 8px; }
    .add:hover { background: var(--teal-soft); color: var(--teal-strong); }
    .plus { font-size: 26px; line-height: 1; }
    .more { display: flex; gap: 8px; flex-wrap: wrap; padding: 0 14px 14px; }
    .mini { width: 70px; height: 56px; border: 1px solid var(--line); border-radius: 8px; overflow: hidden; padding: 0; cursor: pointer; }
    .cmp-head { display: flex; gap: 10px; align-items: center; padding: 12px 14px 0; }
    .auto { width: auto; }
    .cmp { display: grid; grid-template-columns: repeat(auto-fill, minmax(200px, 1fr)); gap: 12px; padding: 14px; }
    .cmp figure { margin: 0; border: 1px solid var(--line); border-radius: 12px; overflow: hidden; }
    .cmp img { aspect-ratio: 4 / 3; cursor: zoom-in; }
    .cmp figcaption { display: flex; justify-content: space-between; padding: 6px 10px; font-size: 12.5px; }
    .pick { display: grid; grid-template-columns: repeat(auto-fill, minmax(130px, 1fr)); gap: 10px; margin-top: 12px; max-height: 420px; overflow: auto; }
    .pf { border: 1px solid var(--line); border-radius: 10px; padding: 0 0 6px; background: var(--surface); cursor: pointer; display: grid; gap: 4px; overflow: hidden; text-align: left; }
    .pf img { aspect-ratio: 4 / 3; } .pf span { padding: 0 6px; }
    .pf.used { opacity: .4; cursor: default; }
    .big { width: 100%; max-height: 62vh; object-fit: contain; background: #000; border-radius: 10px; }
    .mv { margin-top: 10px; gap: 8px; }
    .danger { color: var(--gum); } .pad { padding: 10px 4px; } .nowrap { white-space: nowrap; }
    .err { color: var(--gum); font-size: 12.5px; }
  `],
})
export class OrthoTabComponent implements OnChanges, OnDestroy {
  @Input() patientId!: number;
  private api = inject(Api);
  private toast = inject(ToastService);
  opts: any = null;
  cases: any[] | null = null;
  c: any = null;
  stage = 'INICIAL';
  mode: 'album' | 'compare' = 'album';
  compareView = 'SONRISA';
  thumbs: Record<number, string> = {};
  stageList: [string, string][] = [];
  viewList: [string, string][] = [];
  caseOpen = false; cf: any = {};
  ctlOpen = false; ctl: any = {};
  linkOpen = false; linkView = 'FRONTAL'; images: any[] = [];
  viewer: any = null;
  e: Record<string, string> = {};
  error = ''; busy = false;
  today = isoDate();
  hy: Record<string, string> = { BUENA: 'Buena', REGULAR: 'Regular', MALA: 'Mala' };

  ngOnChanges() {
    this.api.orthoOptions().subscribe((o) => {
      this.opts = o; this.stageList = Object.entries(o.stages) as [string, string][]; this.viewList = (Object.entries(o.views) as [string, string][]).filter((v) => v[0] !== 'OTRA');
    });
    this.load();
  }
  ngOnDestroy() { Object.values(this.thumbs).forEach((u) => URL.revokeObjectURL(u)); }
  load(keep?: number) {
    this.api.patientOrtho(this.patientId).subscribe((l) => {
      this.cases = l;
      const pick = (keep && l.find((x) => x.id === keep)) || l[0] || null;
      if (pick) this.pick(pick); else this.c = null;
    });
  }
  pick(x: any) {
    const first = !this.c || this.c.id !== x.id;
    this.c = x;
    if (first) this.stage = x.currentStage;
    for (const p of x.photos) this.thumb(p.fileId);
  }
  private thumb(fileId: number) {
    if (this.thumbs[fileId]) return;
    this.thumbs[fileId] = '';
    this.api.fileContent(fileId).subscribe((b) => (this.thumbs[fileId] = URL.createObjectURL(b)));
  }
  photos(stage: string) { return (this.c?.photos || []).filter((p: any) => p.stage === stage); }
  photo(stage: string, view: string) { const l = this.photos(stage).filter((p: any) => p.view === view); return l[l.length - 1] || null; }
  count(stage: string) { return this.photos(stage).length || ''; }
  extra(stage: string) {
    const shown = new Set(this.viewList.map((v) => this.photo(stage, v[0])?.id).filter(Boolean));
    return this.photos(stage).filter((p: any) => !shown.has(p.id));
  }
  compareCount() { return this.stageList.filter((s) => this.photo(s[0], this.compareView)).length; }
  entries(o: any): [string, string][] { return o ? Object.entries(o) as [string, string][] : []; }

  openCase(c: any) {
    this.e = {}; this.error = '';
    this.cf = c ? { id: c.id, appliance: c.appliance, angleClass: c.angleClass || '', startDate: c.startDate, estimatedMonths: c.estimatedMonths, status: c.status,
      currentStage: c.currentStage, diagnosis: c.diagnosis || '', objectives: c.objectives || '' }
      : { id: null, appliance: '', angleClass: '', startDate: this.today, estimatedMonths: 18, diagnosis: '', objectives: '' };
    this.caseOpen = true;
  }
  saveCase() {
    this.busy = true; this.e = {}; this.error = '';
    const body = { ...this.cf, angleClass: this.cf.angleClass || null, estimatedMonths: this.cf.estimatedMonths || null };
    const req = this.cf.id ? this.api.updateOrtho(this.cf.id, body) : this.api.createOrtho(this.patientId, body);
    req.subscribe({
      next: (r) => { this.busy = false; this.caseOpen = false; this.toast.ok(this.cf.id ? 'Caso actualizado.' : 'Caso de ortodoncia iniciado.'); this.load(r.id); },
      error: (err) => { this.busy = false; const a = apiError(err); this.e = a.fields || {}; this.error = a.fields ? '' : a.message; },
    });
  }
  openControl() {
    this.e = {}; this.error = '';
    const last = this.c.controls[0];
    this.ctl = { controlDate: this.today, stage: this.c.currentStage, upperWire: last?.upperWire || '', lowerWire: last?.lowerWire || '', elastics: last?.elastics || '', hygiene: '', notes: '', nextWeeks: last?.nextWeeks || 4 };
    this.ctlOpen = true;
  }
  saveControl() {
    this.busy = true; this.e = {}; this.error = '';
    this.api.orthoControl(this.c.id, this.ctl).subscribe({
      next: (r) => { this.busy = false; this.ctlOpen = false; this.toast.ok('Control registrado.'); this.pick(r); this.reloadList(r); },
      error: (err) => { this.busy = false; const a = apiError(err); this.e = a.fields || {}; this.error = a.fields ? '' : a.message; },
    });
  }
  private reloadList(r: any) { this.cases = (this.cases || []).map((x) => (x.id === r.id ? r : x)); this.c = r; }
  upload(ev: Event, view: string) {
    const input = ev.target as HTMLInputElement; const f = input.files?.[0]; input.value = '';
    if (!f) return;
    this.api.orthoUpload(this.c.id, f, f.name, this.stage, view, this.today).subscribe({
      next: (r) => { this.toast.ok('Foto agregada al álbum.'); this.reloadList(r); this.pick(r); },
      error: (err) => this.toast.error(apiError(err).message),
    });
  }
  openLink() {
    this.linkOpen = true;
    this.api.patientFiles(this.patientId).subscribe((l) => {
      this.images = l.filter((f: any) => (f.contentType || '').startsWith('image/'));
      for (const f of this.images) this.thumb(f.id);
    });
  }
  used(fileId: number) { return (this.c?.photos || []).some((p: any) => p.fileId === fileId); }
  link(f: any) {
    this.api.orthoLink(this.c.id, { fileId: f.id, stage: this.stage, view: this.linkView }).subscribe({
      next: (r) => { this.toast.ok('Foto agregada al álbum.'); this.reloadList(r); this.pick(r); },
      error: (err) => this.toast.error(apiError(err).message),
    });
  }
  movePhoto() {
    const v = this.viewer;
    this.api.orthoMovePhoto(this.c.id, v.id, { stage: v.stage, view: v.view }).subscribe({
      next: (r) => { this.viewer = null; this.toast.ok('Foto reubicada.'); this.reloadList(r); },
      error: (err) => this.toast.error(apiError(err).message),
    });
  }
  unlink() {
    if (!confirm('¿Quitar la foto del álbum? Seguirá en "Fotos y documentos".')) return;
    this.api.orthoUnlink(this.c.id, this.viewer.id).subscribe((r) => { this.viewer = null; this.reloadList(r); });
  }
  openViewer(ph: any) { this.viewer = { ...ph }; }
  sd = shortDate;
}
