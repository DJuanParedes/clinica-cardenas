import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { ApiService } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { ModuleConfig, ModuleField } from '../core/models';

@Component({
  selector:'app-generic-module',
  standalone:true,
  imports:[FormsModule],
  template:`
    <section class="page-head">
      <div><span>{{config.eyebrow}}</span><h1>{{config.title}}</h1><p>{{config.subtitle}}</p></div>
      @if (canCreate()) {<button class="primary-btn" (click)="newRecord()">＋ Nuevo registro</button>}
    </section>

    @if (message()) {<div class="inline-message" [class.error]="messageType()==='error'">{{message()}}</div>}

    @if (formOpen()) {
      <section class="panel-card form-panel">
        <div class="card-title"><div><span>{{editingId() ? 'Edición' : 'Nuevo registro'}}</span><h2>{{editingId() ? 'Actualizar información' : 'Registrar información'}}</h2></div><button class="close-btn" (click)="closeForm()">×</button></div>
        <form class="dynamic-form" (ngSubmit)="save()">
          @for (field of config.fields; track field.key) {
            <label [class.wide]="field.type==='textarea'">
              {{field.label}}
              @switch (field.type) {
                @case ('textarea') {<textarea [name]="field.key" [ngModel]="draft[field.key]" (ngModelChange)="draft[field.key]=$event" [required]="field.required || false" [placeholder]="field.placeholder || ''"></textarea>}
                @case ('select') {<select [name]="field.key" [ngModel]="draft[field.key]" (ngModelChange)="draft[field.key]=$event" [required]="field.required || false"><option value="">Seleccionar</option>@for(option of field.options || []; track option){<option [value]="option">{{option}}</option>}</select>}
                @default {<input [type]="field.type || 'text'" [name]="field.key" [ngModel]="draft[field.key]" (ngModelChange)="draft[field.key]=$event" [required]="field.required || false" [placeholder]="field.placeholder || ''" [attr.step]="field.type==='number' ? '0.01' : null">}
              }
            </label>
          }
          <div class="form-actions"><button type="button" class="secondary-btn" (click)="closeForm()">Cancelar</button><button class="primary-btn" [disabled]="saving()">{{saving() ? 'Guardando...' : (editingId() ? 'Guardar cambios' : 'Crear registro')}}</button></div>
        </form>
      </section>
    }

    <section class="module-kpis">
      <article><span>{{config.icon}}</span><div><small>Registros visibles</small><b>{{filtered().length}}</b></div></article>
      <article><span>✓</span><div><small>Estado del módulo</small><b>Operativo</b></div></article>
      <article><span>🔒</span><div><small>Acceso actual</small><b>{{accessLabel()}}</b></div></article>
    </section>

    @if (isComplaintsModule()) {
      <section class="complaint-decision">
        <article><small>Casos abiertos</small><b>{{openComplaintCount()}}</b><span>Reclamos y sugerencias sin cierre.</span></article>
        <article [class.alert-card]="overdueComplaintCount()>0"><small>Vencidos</small><b>{{overdueComplaintCount()}}</b><span>Superaron su fecha límite.</span></article>
        <article><small>Vencen en 7 días</small><b>{{dueSoonComplaintCount()}}</b><span>Prioridad de seguimiento.</span></article>
        <article><small>Área con más abiertos</small><b class="area-name">{{topComplaintArea()}}</b><span>Concentración actual de casos.</span></article>
      </section>
      <section class="panel-card decision-panel"><div class="card-title"><div><span>Toma de decisiones</span><h2>Prioridades de reclamos y satisfacción</h2></div></div><div class="decision-list">@for(insight of complaintInsights();track insight){<div><span>→</span><p>{{insight}}</p></div>}</div></section>
    }

    <section class="panel-card">
      <div class="toolbar">
        <div class="search"><span>⌕</span><input [(ngModel)]="query" placeholder="Buscar en este módulo"></div>
        <div class="toolbar-actions"><button class="secondary-btn" (click)="load()">Actualizar</button><button class="secondary-btn" (click)="exportCsv()">Exportar CSV</button></div>
      </div>
      @if (loading()) {<div class="loading-card">Cargando información...</div>}
      @else if (filtered().length) {
        <div class="data-table-wrap">
          <table class="data-table module-table">
            <thead><tr>@for(field of listFields(); track field.key){<th>{{field.label}}</th>}<th>Estado</th><th>Acciones</th></tr></thead>
            <tbody>
              @for(item of filtered(); track item['id']) {
                <tr>
                  @for(field of listFields(); track field.key){<td>{{display(item[field.key], field)}}</td>}
                  <td><em class="status-chip">{{item[config.statusField] || '—'}}</em></td>
                  <td>
                    <div class="row-actions">
                      <button (click)="view(item)">Ver</button>
                      @if(canEdit()){<button (click)="edit(item)">Editar</button>}
                      @if(canStatus()){<select [ngModel]="item[config.statusField]" (ngModelChange)="changeStatus(item,$event)">@for(option of config.statusOptions; track option){<option [value]="option">{{option}}</option>}</select>}
                    </div>
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      } @else {
        <div class="empty-state"><span>{{config.icon}}</span><b>No hay registros que coincidan</b><p>{{canCreate() ? 'Utiliza “Nuevo registro” para comenzar.' : 'Tu rol tiene acceso de consulta; todavía no existen datos disponibles.'}}</p></div>
      }
    </section>

    @if (selected(); as item) {
      <section class="drawer-backdrop" (click)="selected.set(null)"></section>
      <aside class="detail-drawer">
        <div class="drawer-head"><div><span>Detalle del registro</span><h2>{{primaryTitle(item)}}</h2></div><button (click)="selected.set(null)">×</button></div>
        <div class="detail-grid">
          @for(field of config.fields; track field.key){<div><small>{{field.label}}</small><b>{{display(item[field.key],field)}}</b></div>}
          <div><small>Estado</small><b>{{item[config.statusField] || '—'}}</b></div>
        </div>
        <div class="drawer-actions">@if(canEdit()){<button class="primary-btn" (click)="edit(item); selected.set(null)">Editar registro</button>}<button class="secondary-btn" (click)="selected.set(null)">Cerrar</button></div>
      </aside>
    }
  `,
  styles:[`
    .inline-message{padding:11px 14px;border-radius:10px;background:#e7f7ee;color:#36795a;border:1px solid #cbead8;margin-bottom:14px;font-size:11px}.inline-message.error{background:#fff0f0;color:#a94444;border-color:#f1cccc}.form-panel{margin-bottom:18px}.dynamic-form{display:grid;grid-template-columns:repeat(3,1fr);gap:13px}.dynamic-form label{font-size:10px;font-weight:700;color:#48696b}.dynamic-form input,.dynamic-form select,.dynamic-form textarea{display:block;width:100%;box-sizing:border-box;border:1px solid #dce7e8;border-radius:9px;padding:11px;margin-top:6px;background:#fbfdfd}.dynamic-form textarea{min-height:90px;resize:vertical}.dynamic-form .wide{grid-column:span 2}.form-actions{grid-column:1/-1;display:flex;justify-content:flex-end;gap:9px}.close-btn{border:1px solid #dce7e8;background:#fff;border-radius:8px;width:32px;height:32px}.module-kpis{display:grid;grid-template-columns:repeat(3,1fr);gap:15px;margin-bottom:18px}.complaint-decision{display:grid;grid-template-columns:repeat(4,1fr);gap:12px;margin-bottom:16px}.complaint-decision article{background:#fff;border:1px solid #e1ebec;border-radius:14px;padding:16px}.complaint-decision small,.complaint-decision b,.complaint-decision span{display:block}.complaint-decision small{font-size:8px;text-transform:uppercase;color:#789092}.complaint-decision b{font-size:22px;margin:5px 0;color:#173f43}.complaint-decision .area-name{font-size:14px}.complaint-decision span{font-size:8px;color:#789092}.complaint-decision .alert-card{background:#fff8ef;border-color:#efd9b4}.decision-panel{margin-bottom:16px}.decision-list{display:grid;grid-template-columns:1fr 1fr;gap:8px}.decision-list>div{display:grid;grid-template-columns:26px 1fr;gap:8px;align-items:start;padding:10px;border-radius:10px;background:#f7fbfb}.decision-list>div>span{width:24px;height:24px;border-radius:8px;background:#e4f4f3;color:#3B78A6;display:grid;place-items:center}.decision-list p{font-size:9px;line-height:1.5;margin:3px 0;color:#557376}.module-kpis article{background:#fff;border:1px solid #e1ebec;border-radius:14px;padding:17px;display:flex;align-items:center;gap:13px}.module-kpis article>span{width:42px;height:42px;border-radius:12px;background:#e5f5f4;display:grid;place-items:center}.module-kpis small,.module-kpis b{display:block}.module-kpis small{font-size:9px;color:#789092}.module-kpis b{font-size:15px;margin-top:4px}.toolbar{display:flex;justify-content:space-between;align-items:center;gap:12px;margin-bottom:16px}.search{width:min(450px,60%);position:relative}.search span{position:absolute;left:12px;top:9px}.search input{width:100%;border:1px solid #dce7e8;border-radius:9px;padding:10px 12px 10px 35px}.toolbar-actions{display:flex;gap:8px}.module-table{min-width:1050px}.status-chip{font-style:normal;font-size:8px;padding:6px 8px;border-radius:99px;background:#e6f6ee;color:#367c5a;white-space:nowrap}.row-actions{display:flex;gap:6px;align-items:center}.row-actions button,.row-actions select{border:1px solid #dce7e8;background:#fff;border-radius:7px;padding:6px 7px;font-size:8px;color:#4d6e71}.empty-state{text-align:center;padding:50px 10px;color:#70888a}.empty-state span{font-size:42px}.empty-state b{display:block;color:#31585b;margin-top:12px}.empty-state p{font-size:10px}.drawer-backdrop{position:fixed;inset:0;background:rgba(7,38,42,.35);z-index:60}.detail-drawer{position:fixed;right:0;top:0;bottom:0;width:min(460px,92vw);background:#fff;z-index:61;padding:25px;box-shadow:-20px 0 60px rgba(6,39,43,.22);overflow:auto}.drawer-head{display:flex;justify-content:space-between;gap:12px;border-bottom:1px solid #e3eded;padding-bottom:17px}.drawer-head span{font-size:9px;text-transform:uppercase;color:#3B78A6;font-weight:800}.drawer-head h2{font-size:20px;margin:5px 0}.drawer-head button{border:0;background:#eef4f4;border-radius:8px;width:34px;height:34px}.detail-grid{display:grid;gap:0;margin-top:10px}.detail-grid div{padding:13px 0;border-bottom:1px solid #edf2f2}.detail-grid small,.detail-grid b{display:block}.detail-grid small{font-size:8px;text-transform:uppercase;color:#789092}.detail-grid b{font-size:11px;margin-top:5px;line-height:1.55}.drawer-actions{display:flex;gap:9px;margin-top:22px}@media(max-width:850px){.dynamic-form{grid-template-columns:1fr 1fr}.module-kpis,.complaint-decision{grid-template-columns:1fr 1fr}.decision-list{grid-template-columns:1fr}.toolbar{align-items:stretch;flex-direction:column}.search{width:100%}}@media(max-width:560px){.dynamic-form{grid-template-columns:1fr}.complaint-decision{grid-template-columns:1fr}.dynamic-form .wide{grid-column:auto}.toolbar-actions{display:grid;grid-template-columns:1fr 1fr}}
  `]
})
export class GenericModuleComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly api = inject(ApiService);
  private readonly auth = inject(AuthService);
  readonly config = this.route.snapshot.data['config'] as ModuleConfig;
  readonly items = signal<Record<string, any>[]>([]);
  readonly selected = signal<Record<string, any> | null>(null);
  readonly formOpen = signal(false);
  readonly editingId = signal<number | null>(null);
  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly message = signal('');
  readonly messageType = signal<'success'|'error'>('success');
  query = '';
  draft: Record<string, any> = {};

  readonly listFields = computed(() => this.config.fields.filter(f => f.list).slice(0, 5));

  ngOnInit(): void { this.resetDraft(); this.load(); }
  canCreate(): boolean { return this.auth.hasRole(this.config.createRoles); }
  canEdit(): boolean { return this.auth.hasRole(this.config.editRoles); }
  canStatus(): boolean { return this.auth.hasRole(this.config.statusRoles); }
  accessLabel(): string { return this.canEdit() ? 'Edición habilitada' : 'Solo consulta'; }
  isComplaintsModule(): boolean { return this.config.route === '/app/reclamos'; }
  openComplaintCount(): number { return this.items().filter(x => x['complaintStatus'] !== 'CERRADO').length; }
  overdueComplaintCount(): number { const today=this.todayKey(); return this.items().filter(x => x['complaintStatus'] !== 'CERRADO' && x['dueDate'] && String(x['dueDate']) < today).length; }
  dueSoonComplaintCount(): number { const today=new Date(); const limit=new Date(today); limit.setDate(limit.getDate()+7); return this.items().filter(x => { if(x['complaintStatus']==='CERRADO'||!x['dueDate']) return false; const d=new Date(`${x['dueDate']}T12:00:00`); return d>=new Date(`${this.todayKey()}T00:00:00`) && d<=limit; }).length; }
  topComplaintArea(): string { const map=new Map<string,number>(); for(const x of this.items().filter(i=>i['complaintStatus']!=='CERRADO')){const area=String(x['area']||'Sin área');map.set(area,(map.get(area)||0)+1);} return [...map.entries()].sort((a,b)=>b[1]-a[1])[0]?.[0]||'Sin casos'; }
  complaintInsights(): string[] { const insights:string[]=[]; const overdue=this.overdueComplaintCount(), soon=this.dueSoonComplaintCount(); if(overdue>0) insights.push(`Atender primero ${overdue} caso(s) vencido(s) y documentar respuesta o acción correctiva.`); if(soon>0) insights.push(`${soon} caso(s) vencen en los próximos 7 días; asigna responsable y fecha de respuesta.`); const withoutResponse=this.items().filter(x=>x['complaintStatus']!=='CERRADO'&&!String(x['response']||'').trim()).length; if(withoutResponse>0) insights.push(`${withoutResponse} caso(s) abiertos todavía no tienen respuesta registrada.`); const area=this.topComplaintArea(); if(area!=='Sin casos') insights.push(`El área con mayor concentración de casos abiertos es “${area}”; revisa causas repetitivas.`); if(!insights.length) insights.push('No hay reclamos abiertos prioritarios. Mantén seguimiento de sugerencias y felicitaciones para detectar oportunidades.'); return insights; }

  load(): void {
    this.loading.set(true);
    this.api.get<Record<string, any>[]>(this.config.endpoint).subscribe({
      next: value => { this.items.set(value); this.loading.set(false); },
      error: error => { this.loading.set(false); this.notify(error?.error?.message || 'No se pudo cargar el módulo.', 'error'); }
    });
  }

  filtered(): Record<string, any>[] {
    const q = this.query.trim().toLowerCase();
    return this.items().filter(item => !q || Object.values(item).some(value => String(value ?? '').toLowerCase().includes(q)));
  }

  newRecord(): void { this.editingId.set(null); this.resetDraft(); this.formOpen.set(true); this.message.set(''); }
  edit(item: Record<string, any>): void { this.editingId.set(Number(item['id'])); this.draft = {...item}; this.formOpen.set(true); this.message.set(''); window.scrollTo({top:0,behavior:'smooth'}); }
  view(item: Record<string, any>): void { this.selected.set(item); }
  closeForm(): void { this.formOpen.set(false); this.editingId.set(null); this.resetDraft(); }

  save(): void {
    this.saving.set(true);
    const request = this.editingId()
      ? this.api.put<Record<string, any>>(`${this.config.endpoint}/${this.editingId()}`, this.draft)
      : this.api.post<Record<string, any>>(this.config.endpoint, this.draft);
    request.subscribe({
      next: () => { this.saving.set(false); this.closeForm(); this.notify('Registro guardado correctamente.'); this.load(); },
      error: error => { this.saving.set(false); this.notify(error?.error?.message || 'No se pudo guardar el registro.', 'error'); }
    });
  }

  changeStatus(item: Record<string, any>, value: string): void {
    const previous = item[this.config.statusField];
    item[this.config.statusField] = value;
    this.api.patch<Record<string, any>>(`${this.config.endpoint}/${item['id']}/status?value=${encodeURIComponent(value)}`).subscribe({
      next: updated => { Object.assign(item, updated); this.notify('Estado actualizado.'); },
      error: error => { item[this.config.statusField] = previous; this.notify(error?.error?.message || 'No se pudo actualizar el estado.', 'error'); }
    });
  }

  exportCsv(): void {
    const fields = [...this.config.fields, {key:this.config.statusField,label:'Estado'} as ModuleField];
    const rows = [fields.map(f => f.label), ...this.filtered().map(item => fields.map(f => item[f.key] ?? ''))];
    const csv = '\uFEFF' + rows.map(row => row.map(value => `"${String(value).replace(/"/g,'""')}"`).join(';')).join('\n');
    const blob = new Blob([csv], {type:'text/csv;charset=utf-8'});
    const link = document.createElement('a');
    link.href = URL.createObjectURL(blob);
    link.download = `${this.config.route.split('/').pop()}-${new Date().toISOString().slice(0,10)}.csv`;
    link.click();
    URL.revokeObjectURL(link.href);
    this.notify('Archivo CSV generado.');
  }

  display(value: any, field: ModuleField): string {
    if (value === null || value === undefined || value === '') return '—';
    if (field.type === 'number') return Number(value).toLocaleString('es-PE', {maximumFractionDigits:2});
    return String(value).replace('T', ' ');
  }

  primaryTitle(item: Record<string, any>): string {
    const first = this.config.fields.find(f => f.required) ?? this.config.fields[0];
    return String(item[first.key] ?? this.config.title);
  }

  private todayKey(): string { const d=new Date(); return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`; }

  private resetDraft(): void {
    this.draft = {};
    for (const field of this.config.fields) {
      this.draft[field.key] = field.type === 'number' ? 0 : '';
    }
    if (this.config.statusField) {
      this.draft[this.config.statusField] = this.config.statusOptions?.[0] ?? '';
    }
  }

  private notify(text: string, type: 'success'|'error' = 'success'): void {
    this.message.set(text); this.messageType.set(type);
  }
}
