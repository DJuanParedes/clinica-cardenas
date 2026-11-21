import { AfterViewInit, Component, ElementRef, Input, OnDestroy, OnInit, ViewChild, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Api, PatientFile } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { ToastService } from '../../core/toast.service';
import { FILE_KIND, apiError, dateTime, fileSize, saveBlob } from '../../core/format';
import { IconComponent } from '../../shared/icon.component';
import { ModalComponent } from '../../shared/modal.component';

const TEMPLATES: Record<string, { title: string; body: string }> = {
  CONSENTIMIENTO: {
    title: 'Consentimiento informado',
    body: 'Declaro que el profesional me explicó en términos claros el procedimiento a realizar, sus beneficios, riesgos y alternativas, '
      + 'y que pude hacer todas las preguntas que consideré necesarias.\n\nProcedimiento: \n\nAutorizo a la Clínica Dental Cárdenas a realizarlo. '
      + 'Sé que puedo retirar este consentimiento antes de iniciar el tratamiento.',
  },
  RECETA: { title: 'Receta médica', body: 'Rp.\n1. \n   Indicaciones: \n\nRecomendaciones: ' },
  PRESUPUESTO: { title: 'Presupuesto de tratamiento', body: 'Detalle del tratamiento propuesto:\n- \n\nTotal: S/ \nValidez: 30 días.' },
};

/** Fotos, radiografías y documentos del paciente; consentimientos y recetas firmados en pantalla. */
@Component({
  selector: 'app-files-tab',
  standalone: true,
  imports: [FormsModule, IconComponent, ModalComponent],
  template: `
    <div class="spread head">
      <p class="muted small">{{ files.length }} {{ files.length === 1 ? 'archivo' : 'archivos' }} · los marcados como compartidos aparecen en la app del paciente.</p>
      @if (canUpload) {
        <div class="row">
          <button class="btn" type="button" (click)="openDoc('CONSENTIMIENTO')"><app-icon name="edit" [size]="16" /> Documento con firma</button>
          <button class="btn primary" type="button" (click)="picker.click()"><app-icon name="plus" [size]="16" /> Subir archivo</button>
          <input #picker type="file" hidden accept="image/*,application/pdf" (change)="picked($event)" />
        </div>
      }
    </div>

    <div class="filters row">
      @for (k of kindKeys; track k) {
        <button type="button" class="chip" [class.on]="filter === k" (click)="filter = k">{{ k === '' ? 'Todos' : kindLabel(k) }}</button>
      }
    </div>

    @if (loaded && shown.length === 0) {
      <div class="panel empty">
        <h3>Sin archivos</h3>
        <p>Sube fotos intraorales, radiografías o PDF, o genera un consentimiento firmado por el paciente.</p>
      </div>
    }

    <div class="gallery">
      @for (f of shown; track f.id) {
        <article class="panel card">
          <button type="button" class="thumb" (click)="open(f)" [attr.aria-label]="'Abrir ' + f.title">
            @if (thumbs[f.id]) { <img [src]="thumbs[f.id]" alt="" /> }
            @else { <span class="ph"><app-icon [name]="f.contentType === 'application/pdf' ? 'file' : 'eye'" [size]="34" /><span class="small">{{ f.contentType === 'application/pdf' ? 'PDF' : 'Imagen' }}</span></span> }
          </button>
          <div class="meta">
            <div class="strong ellipsis" [title]="f.title">{{ f.title }}</div>
            <div class="muted small">{{ kindLabel(f.kind) }}@if (f.tooth) { · pieza {{ f.tooth }} }@if (f.signedAt) { · firmado }</div>
            <div class="muted small">{{ dt(f.createdAt) }} · {{ size(f.sizeBytes) }}</div>
            @if (f.note) { <div class="small note">{{ f.note }}</div> }
          </div>
          <div class="acts">
            @if (canUpload) {
              <label class="check small"><input type="checkbox" [checked]="f.sharedWithPatient" (change)="share(f, $any($event.target).checked)" /> En la app</label>
            } @else if (f.sharedWithPatient) { <span class="tag teal">En la app</span> }
            <span class="grow"></span>
            <button class="btn ghost sm" type="button" (click)="download(f)" aria-label="Descargar"><app-icon name="download" [size]="16" /></button>
            @if (canDelete) { <button class="btn ghost sm" type="button" (click)="remove(f)" aria-label="Eliminar"><app-icon name="x" [size]="16" /></button> }
          </div>
        </article>
      }
    </div>

    <!-- subir archivo -->
    <app-modal [open]="!!pending" title="Subir archivo" [subtitle]="pending?.name || ''" (close)="pending = null">
      <form class="stack" (ngSubmit)="upload()">
        @if (pendingUrl) { <img class="preview" [src]="pendingUrl" alt="Vista previa" /> }
        <div class="grid two">
          <div class="field"><label for="fk">Tipo</label>
            <select id="fk" class="input" name="kind" [(ngModel)]="meta.kind">
              @for (k of uploadKinds; track k) { <option [value]="k">{{ kindLabel(k) }}</option> }
            </select>
          </div>
          <div class="field"><label for="ft">Pieza (opcional)</label>
            <input id="ft" class="input" name="tooth" type="number" min="11" max="85" [(ngModel)]="meta.tooth" placeholder="Ej. 36" />
          </div>
        </div>
        <div class="field"><label for="fti">Título</label><input id="fti" class="input" name="title" [(ngModel)]="meta.title" maxlength="150" /></div>
        <div class="field"><label for="fn">Nota</label><textarea id="fn" class="input" rows="2" name="note" [(ngModel)]="meta.note" maxlength="500"></textarea></div>
        <label class="check"><input type="checkbox" name="share" [(ngModel)]="meta.share" /> Compartir con el paciente en su app</label>
      </form>
      <ng-container modal-actions>
        <button class="btn" type="button" (click)="pending = null">Cancelar</button>
        <button class="btn primary" type="button" (click)="upload()" [disabled]="busy">@if (busy) { <span class="spin"></span> } Subir</button>
      </ng-container>
    </app-modal>

    <!-- documento firmado -->
    <app-modal [open]="docOpen" title="Documento con firma" subtitle="Se genera un PDF con la firma del paciente y queda en su ficha." [width]="720" (close)="docOpen = false">
      <div class="stack">
        <div class="seg">
          @for (k of docKinds; track k) { <button type="button" [class.on]="doc.kind === k" (click)="setDocKind(k)">{{ kindLabel(k) }}</button> }
        </div>
        <div class="field"><label for="dt">Título</label><input id="dt" class="input" [(ngModel)]="doc.title" maxlength="150" /></div>
        <div class="field"><label for="db">Contenido</label><textarea id="db" class="input" rows="8" [(ngModel)]="doc.body"></textarea></div>
        <div class="field"><label for="ds">Nombre de quien firma</label><input id="ds" class="input" [(ngModel)]="doc.signerName" maxlength="120" /></div>
        <div class="field">
          <div class="spread"><label>Firma {{ doc.kind === 'CONSENTIMIENTO' ? '(obligatoria)' : '(opcional)' }}</label>
            <button type="button" class="btn ghost sm" (click)="clearPad()">Borrar firma</button></div>
          <canvas #pad class="pad" width="640" height="200" aria-label="Recuadro para firmar con el dedo o el mouse"
                  (pointerdown)="down($event)" (pointermove)="move($event)" (pointerup)="up()" (pointerleave)="up()"></canvas>
          <span class="muted small">Firma con el dedo (tablet o celular) o con el mouse.</span>
        </div>
        <label class="check"><input type="checkbox" [(ngModel)]="doc.share" /> Compartir con el paciente en su app</label>
      </div>
      <ng-container modal-actions>
        <button class="btn" type="button" (click)="docOpen = false">Cancelar</button>
        <button class="btn primary" type="button" (click)="saveDoc()" [disabled]="busy">@if (busy) { <span class="spin"></span> } Generar PDF</button>
      </ng-container>
    </app-modal>

    <!-- visor de imagen -->
    <app-modal [open]="!!viewing" [title]="viewing?.title || ''" [width]="980" (close)="closeViewer()">
      @if (viewUrl) { <img class="full" [src]="viewUrl" [alt]="viewing?.title || ''" /> }
    </app-modal>
  `,
  styles: [`
    .head { margin-bottom: 12px; }
    .filters { flex-wrap: wrap; gap: 6px; margin-bottom: 14px; }
    .chip { border: 1px solid var(--line); background: var(--surface); border-radius: 999px; padding: 5px 12px; font: inherit; font-size: 13px; cursor: pointer; }
    .chip.on { background: var(--brand-soft); border-color: var(--brand); color: var(--teal-strong); font-weight: 700; }
    .gallery { display: grid; grid-template-columns: repeat(auto-fill, minmax(220px, 1fr)); gap: 14px; }
    .card { display: flex; flex-direction: column; overflow: hidden; }
    .thumb { aspect-ratio: 4 / 3; border: 0; padding: 0; background: var(--surface-2); cursor: zoom-in; display: grid; place-items: center; overflow: hidden; }
    .thumb img { width: 100%; height: 100%; object-fit: cover; }
    .ph { display: flex; flex-direction: column; align-items: center; gap: 6px; color: var(--muted); }
    .meta { padding: 10px 12px 4px; display: flex; flex-direction: column; gap: 2px; min-width: 0; }
    .ellipsis { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .note { color: var(--ink-2); margin-top: 4px; }
    .acts { display: flex; align-items: center; gap: 4px; padding: 6px 8px 8px 12px; border-top: 1px solid var(--line); margin-top: auto; }
    .preview { max-height: 220px; max-width: 100%; border-radius: 10px; align-self: center; }
    .full { width: 100%; height: auto; border-radius: 8px; }
    .pad { width: 100%; height: 200px; border: 1.5px dashed var(--line-strong, #b9c6cc); border-radius: 12px; background: #fff; touch-action: none; cursor: crosshair; }
    .seg { display: inline-flex; border: 1px solid var(--line); border-radius: 10px; overflow: hidden; align-self: flex-start; }
    .seg button { border: 0; background: var(--surface); padding: 7px 14px; font: inherit; font-size: 13.5px; cursor: pointer; }
    .seg button.on { background: var(--brand); color: #fff; font-weight: 700; }
  `],
})
export class FilesTabComponent implements OnInit, OnDestroy {
  private api = inject(Api);
  private auth = inject(AuthService);
  private toast = inject(ToastService);

  @Input() patientId!: number;
  @Input() patientName = '';
  @ViewChild('pad') padRef?: ElementRef<HTMLCanvasElement>;

  files: PatientFile[] = [];
  thumbs: Record<number, string> = {};
  loaded = false;
  busy = false;
  filter = '';
  kindKeys = ['', 'FOTO', 'RADIOGRAFIA', 'DOCUMENTO', 'CONSENTIMIENTO', 'RECETA', 'PRESUPUESTO'];
  uploadKinds = ['FOTO', 'RADIOGRAFIA', 'DOCUMENTO'];
  docKinds = ['CONSENTIMIENTO', 'RECETA', 'PRESUPUESTO'];

  pending: File | null = null;
  pendingUrl = '';
  meta = { kind: 'FOTO', title: '', note: '', tooth: null as number | null, share: false };

  docOpen = false;
  doc = { kind: 'CONSENTIMIENTO', title: '', body: '', signerName: '', share: true };
  private drawing = false;
  private signed = false;

  viewing: PatientFile | null = null;
  viewUrl = '';

  get canUpload() { return this.auth.can('HISTORIA_CLINICA', 'PACIENTES_EDITAR'); }
  get canDelete() { return this.auth.can('HISTORIA_CLINICA'); }
  get shown() { return this.filter ? this.files.filter((f) => f.kind === this.filter) : this.files; }

  ngOnInit() { this.load(); }
  ngOnDestroy() { Object.values(this.thumbs).forEach((u) => URL.revokeObjectURL(u)); this.closeViewer(); }

  load() {
    this.api.patientFiles(this.patientId).subscribe((list) => {
      this.files = list;
      this.loaded = true;
      for (const f of list) {
        if (f.contentType.startsWith('image/') && !this.thumbs[f.id]) {
          this.api.fileContent(f.id).subscribe((b) => (this.thumbs[f.id] = URL.createObjectURL(b)));
        }
      }
    });
  }

  kindLabel(k: string) { return FILE_KIND[k] || k; }
  dt = dateTime;
  size = fileSize;

  picked(ev: Event) {
    const input = ev.target as HTMLInputElement;
    const f = input.files?.[0];
    input.value = '';
    if (!f) return;
    if (f.size > 10 * 1024 * 1024) { this.toast.error('El archivo supera 10 MB.'); return; }
    this.pending = f;
    this.pendingUrl = f.type.startsWith('image/') ? URL.createObjectURL(f) : '';
    this.meta = { kind: f.type === 'application/pdf' ? 'DOCUMENTO' : 'FOTO', title: f.name.replace(/\.[^.]+$/, ''), note: '', tooth: null, share: false };
  }

  upload() {
    if (!this.pending || this.busy) return;
    this.busy = true;
    this.api.uploadFile(this.patientId, this.pending, this.pending.name, this.meta).subscribe({
      next: () => { this.busy = false; this.pending = null; this.toast.ok('Archivo guardado en la ficha.'); this.load(); },
      error: (e) => { this.busy = false; this.toast.error(apiError(e).message); },
    });
  }

  share(f: PatientFile, on: boolean) {
    this.api.shareFile(f.id, on).subscribe({
      next: (r) => { f.sharedWithPatient = r.sharedWithPatient; this.toast.ok(on ? 'El paciente ya lo ve en su app.' : 'Se dejó de compartir.'); },
      error: (e) => this.toast.error(apiError(e).message),
    });
  }

  remove(f: PatientFile) {
    if (!confirm(`¿Eliminar "${f.title}" de la ficha?`)) return;
    this.api.deleteFile(f.id).subscribe({
      next: () => { this.files = this.files.filter((x) => x.id !== f.id); this.toast.ok('Archivo eliminado.'); },
      error: (e) => this.toast.error(apiError(e).message),
    });
  }

  download(f: PatientFile) {
    const ext = f.contentType === 'application/pdf' ? '.pdf' : f.contentType === 'image/png' ? '.png' : '.jpg';
    this.api.fileContent(f.id).subscribe((b) => saveBlob(b, f.title.replace(/[^\wáéíóúñ\- ]+/gi, '') + ext));
  }

  open(f: PatientFile) {
    if (f.contentType.startsWith('image/')) {
      this.viewing = f;
      this.viewUrl = this.thumbs[f.id] || '';
      if (!this.viewUrl) this.api.fileContent(f.id).subscribe((b) => (this.viewUrl = URL.createObjectURL(b)));
    } else {
      this.api.fileContent(f.id).subscribe((b) => window.open(URL.createObjectURL(b), '_blank'));
    }
  }
  closeViewer() { this.viewing = null; this.viewUrl = ''; }

  // ---------- documento con firma
  openDoc(kind: string) {
    this.doc = { kind, title: TEMPLATES[kind].title, body: TEMPLATES[kind].body, signerName: this.patientName, share: true };
    this.docOpen = true;
    setTimeout(() => this.clearPad());
  }
  setDocKind(k: string) {
    this.doc.kind = k;
    this.doc.title = TEMPLATES[k].title;
    this.doc.body = TEMPLATES[k].body;
  }
  private ctx(): CanvasRenderingContext2D | null { return this.padRef?.nativeElement.getContext('2d') || null; }
  private point(e: PointerEvent) {
    const c = this.padRef!.nativeElement;
    const r = c.getBoundingClientRect();
    return { x: (e.clientX - r.left) * (c.width / r.width), y: (e.clientY - r.top) * (c.height / r.height) };
  }
  clearPad() {
    const c = this.padRef?.nativeElement; const g = this.ctx();
    if (!c || !g) return;
    g.fillStyle = '#fff'; g.fillRect(0, 0, c.width, c.height);
    g.strokeStyle = '#0d2b4a'; g.lineWidth = 3; g.lineCap = 'round'; g.lineJoin = 'round';
    this.signed = false;
  }
  down(e: PointerEvent) {
    const g = this.ctx(); if (!g) return;
    (e.target as HTMLElement).setPointerCapture?.(e.pointerId);
    this.drawing = true;
    const p = this.point(e);
    g.beginPath(); g.moveTo(p.x, p.y);
  }
  move(e: PointerEvent) {
    if (!this.drawing) return;
    const g = this.ctx(); if (!g) return;
    const p = this.point(e);
    g.lineTo(p.x, p.y); g.stroke();
    this.signed = true;
  }
  up() { this.drawing = false; }

  saveDoc() {
    if (this.busy) return;
    if (this.doc.kind === 'CONSENTIMIENTO' && !this.signed) { this.toast.error('El consentimiento necesita la firma del paciente.'); return; }
    const signature = this.signed && this.padRef ? this.padRef.nativeElement.toDataURL('image/jpeg', 0.85) : '';
    this.busy = true;
    this.api.createDocument(this.patientId, {
      kind: this.doc.kind, title: this.doc.title, body: this.doc.body, signerName: this.doc.signerName,
      signatureJpeg: signature, shareWithPatient: this.doc.share,
    }).subscribe({
      next: () => { this.busy = false; this.docOpen = false; this.toast.ok('Documento generado y guardado en la ficha.'); this.load(); },
      error: (e) => { this.busy = false; this.toast.error(apiError(e).message); },
    });
  }
}
