import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../core/api.service';

interface ColumnMeta { name:string; typeName:string; nullable:boolean; autoIncrement:boolean; generated:boolean; defaultValue?:string|null; }
interface TableMeta { tableName:string; rowCount:number; columns:ColumnMeta[]; primaryKeys:string[]; }
interface ServiceCatalog { key:string; label:string; tables:TableMeta[]; available:boolean; error?:string|null; }
interface ReportInfo { id:string; label:string; service:string; path:string; }
interface RowError { row:number; message:string; }
interface ImportResponse { totalRows:number; inserted:number; updated:number; skipped:number; errorCount:number; errors:RowError[]; }

@Component({
  selector:'app-backup-center', standalone:true, imports:[FormsModule],
  template:`
  <section class="page-head">
    <div><span>Continuidad y migración</span><h1>Copias de seguridad e importación</h1><p>Descarga tablas e informes de forma individual o genera un respaldo global. Importa datos desde Excel usando plantillas controladas.</p></div>
    <button class="primary-btn" [disabled]="globalBusy()" (click)="downloadGlobal()">{{globalBusy()?'Generando respaldo...':'⬇ Descargar respaldo global'}}</button>
  </section>

  @if(message()){<div class="notice" [class.error]="messageType()==='error'">{{message()}}</div>}

  <section class="ux-flow">
    <article><span>1</span><div><b>Descarga una plantilla</b><small>Obtén exactamente los campos que acepta cada tabla.</small></div></article>
    <i>→</i>
    <article><span>2</span><div><b>Completa tu Excel</b><small>No cambies los encabezados. El ID puede quedar vacío si es automático.</small></div></article>
    <i>→</i>
    <article><span>3</span><div><b>Importa y valida</b><small>El sistema informa filas insertadas, actualizadas y observadas.</small></div></article>
  </section>

  <section class="summary-grid">
    <article class="summary global"><span>🗃</span><div><small>Respaldo integral</small><b>{{totalTables()}} tablas</b><em>{{totalRows()}} registros inventariados</em></div></article>
    <article class="summary"><span>📄</span><div><small>Informes descargables</small><b>{{reports().length}}</b><em>Snapshots operativos y financieros</em></div></article>
    <article class="summary"><span>📥</span><div><small>Importación Excel</small><b>APPEND / UPSERT</b><em>Sin reemplazos masivos silenciosos</em></div></article>
  </section>

  <section class="tabs"><button [class.active]="tab()==='tables'" (click)="tab.set('tables')">Tablas y Excel</button><button [class.active]="tab()==='reports'" (click)="tab.set('reports')">Informes</button><button [class.active]="tab()==='help'" (click)="tab.set('help')">Cómo importar</button></section>

  @if(tab()==='tables'){
    <section class="toolbar"><input [(ngModel)]="search" placeholder="Buscar tabla o módulo..."><button (click)="load()">↻ Actualizar catálogo</button></section>
    <section class="service-list">
      @for(service of filteredServices();track service.key){
        <article class="service-card">
          <div class="service-head"><div><span>{{service.label}}</span><h2>{{service.available ? (service.tables.length+' tablas') : 'Servicio no disponible'}}</h2></div><small>{{service.available ? (serviceRows(service)+' registros') : (service.error||'Sin conexión')}}</small></div>
          <div class="table-list">
            @for(t of service.tables;track t.tableName){
              <div class="table-row">
                <div class="table-info"><b>{{friendly(t.tableName)}}</b><small>{{t.tableName}} · {{t.rowCount}} filas · PK: {{t.primaryKeys.join(', ')||'sin clave detectada'}}</small></div>
                <div class="actions"><button (click)="downloadTable(service,t)">⬇ Datos</button><button (click)="downloadTemplate(service,t)">▤ Plantilla</button>@if(service.key!=='auth'){<button class="import" (click)="openImport(service,t)">⬆ Importar Excel</button>}@else{<span class="protected">🔒 Solo exportación</span>}</div>
              </div>
            } @empty { <p class="empty">Sin tablas disponibles.</p> }
          </div>
        </article>
      } @empty { <article class="panel-card empty">No se encontraron tablas con ese filtro.</article> }
    </section>
  }

  @if(tab()==='reports'){
    <section class="report-grid">
      @for(r of reports();track r.id){<article><span>📊</span><div><b>{{r.label}}</b><small>{{reportHint(r.id)}}</small></div><button (click)="downloadReport(r)">Descargar Excel</button></article>}
    </section>
  }

  @if(tab()==='help'){
    <section class="help-grid">
      <article><span>①</span><h3>Descarga la plantilla</h3><p>En la tabla que deseas cargar, presiona <b>Plantilla</b>. El Excel incluye una hoja DATOS y otra hoja INSTRUCCIONES con tipos y claves.</p></article>
      <article><span>②</span><h3>Completa los campos</h3><p>Agrega tus registros en DATOS. Mantén los encabezados originales. Si el identificador es autogenerado, déjalo vacío para registros nuevos.</p></article>
      <article><span>③</span><h3>Elige el modo correcto</h3><p><b>Agregar nuevos</b> intenta insertar filas. <b>Actualizar o agregar</b> usa la clave primaria: actualiza si ya existe o crea si no existe.</p></article>
      <article><span>④</span><h3>Revisa el resultado</h3><p>Después de importar verás insertados, actualizados y errores por número de fila. Corrige solo las filas observadas y vuelve a cargarlas.</p></article>
      <article class="warning"><span>!</span><h3>No cargues datos a ciegas</h3><p>Tablas como pagos, citas y proformas tienen relaciones entre sí. Para migraciones grandes, importa primero datos maestros y luego movimientos relacionados.</p></article>
      <article class="warning"><span>🔒</span><h3>Acceso administrativo</h3><p>Las copias globales y las importaciones están restringidas al Administrador porque contienen información clínica, personal y financiera.</p></article>
    </section>
  }

  @if(importOpen() && selectedService() && selectedTable()){
    <div class="modal-bg" (click)="closeImport()"></div>
    <section class="modal">
      <div class="modal-head"><div><span>Importación controlada</span><h2>{{friendly(selectedTable()!.tableName)}}</h2><small>{{selectedService()!.label}} · {{selectedTable()!.tableName}}</small></div><button (click)="closeImport()">×</button></div>
      <div class="mode-box"><label><input type="radio" name="mode" [(ngModel)]="importMode" value="APPEND"><span><b>Agregar nuevos</b><small>Recomendado para carga inicial. No modifica filas existentes.</small></span></label><label><input type="radio" name="mode" [(ngModel)]="importMode" value="UPSERT"><span><b>Actualizar o agregar</b><small>Si el ID/clave ya existe, actualiza; si no, inserta.</small></span></label></div>
      <label class="file-zone"><input type="file" accept=".xlsx,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" (change)="selectFile($event)"><span>📎</span><b>{{selectedFile()?.name||'Seleccionar archivo .xlsx'}}</b><small>Máximo 25 MB · hasta 50,000 filas</small></label>
      <div class="column-preview"><b>Campos esperados</b><div>@for(c of selectedTable()!.columns;track c.name){<span [class.pk]="selectedTable()!.primaryKeys.includes(c.name)">{{c.name}}{{c.autoIncrement?' · ID auto':''}}</span>}</div></div>
      @if(importResult();as result){<section class="result"><div><b>{{result.inserted}}</b><small>Insertados</small></div><div><b>{{result.updated}}</b><small>Actualizados</small></div><div><b>{{result.skipped}}</b><small>Omitidos</small></div><div [class.bad]="result.errorCount>0"><b>{{result.errorCount}}</b><small>Errores</small></div></section>@if(result.errors.length){<div class="errors"><b>Filas observadas</b>@for(e of result.errors.slice(0,20);track $index){<p>Fila {{e.row}}: {{e.message}}</p>}@if(result.errors.length>20){<small>Se muestran los primeros 20 errores de {{result.errors.length}}.</small>}</div>}}
      <div class="modal-actions"><button (click)="downloadTemplate(selectedService()!,selectedTable()!)">Descargar plantilla</button><button class="primary" [disabled]="!selectedFile()||importBusy()" (click)="runImport()">{{importBusy()?'Validando e importando...':'Importar y validar'}}</button></div>
    </section>
  }
  `,
  styles:[`
  .notice{padding:12px 14px;border:1px solid #cfe7db;background:#effaf4;color:#356e54;border-radius:11px;margin-bottom:15px;font-size:11px}.notice.error{background:#fff1f1;border-color:#f0cccc;color:#9e4040}.ux-flow{display:grid;grid-template-columns:1fr auto 1fr auto 1fr;gap:13px;align-items:center;margin-bottom:18px}.ux-flow article{display:flex;gap:12px;align-items:center;background:#fff;border:1px solid #dfe9ea;border-radius:14px;padding:15px}.ux-flow article>span{width:34px;height:34px;border-radius:10px;background:#e8f2f8;color:#3B78A6;display:grid;place-items:center;font-weight:800}.ux-flow b,.ux-flow small{display:block}.ux-flow b{font-size:11px}.ux-flow small{font-size:8px;color:#738b8e;margin-top:3px;line-height:1.45}.ux-flow i{font-style:normal;color:#9bb3b5}.summary-grid{display:grid;grid-template-columns:repeat(3,1fr);gap:14px;margin-bottom:18px}.summary{display:flex;gap:13px;align-items:center;background:#fff;border:1px solid #dfe9ea;border-radius:14px;padding:17px}.summary>span{font-size:24px}.summary small,.summary b,.summary em{display:block}.summary small{font-size:8px;color:#70888a;text-transform:uppercase}.summary b{font-size:17px;margin:4px 0}.summary em{font-style:normal;font-size:8px;color:#638083}.summary.global{border-color:#b8d7e6;background:#f8fcff}.tabs{display:flex;gap:8px;border-bottom:1px solid #dfe9ea;margin-bottom:15px}.tabs button{border:0;background:transparent;padding:11px 14px;color:#668083;cursor:pointer}.tabs button.active{color:#245f87;font-weight:800;border-bottom:3px solid #D06B4F}.toolbar{display:flex;justify-content:space-between;gap:10px;margin-bottom:13px}.toolbar input{flex:1;max-width:420px;padding:10px 12px;border:1px solid #dce7e8;border-radius:10px}.toolbar button,.actions button,.report-grid button,.modal-actions button{border:1px solid #d7e4e5;background:#fff;border-radius:9px;padding:8px 10px;cursor:pointer;color:#3e6266}.service-list{display:grid;gap:14px}.service-card{background:#fff;border:1px solid #dfe9ea;border-radius:15px;overflow:hidden}.service-head{padding:15px 17px;background:#f8fbfb;display:flex;justify-content:space-between;align-items:center;border-bottom:1px solid #e7eeee}.service-head span{font-size:9px;text-transform:uppercase;color:#3B78A6;font-weight:800}.service-head h2{font-size:14px;margin:4px 0 0}.service-head>small{font-size:9px;color:#72898c}.table-row{display:grid;grid-template-columns:1fr auto;gap:12px;align-items:center;padding:13px 16px;border-bottom:1px solid #edf2f2}.table-row:last-child{border-bottom:0}.table-info b,.table-info small{display:block}.table-info b{font-size:11px}.table-info small{font-size:8px;color:#7a9092;margin-top:4px}.protected{font-size:8px;font-weight:800;color:#7b5550;background:#fff1ed;border:1px solid #f0d0c6;border-radius:8px;padding:7px 9px;white-space:nowrap}.actions{display:flex;gap:6px}.actions .import{background:#edf7f6;border-color:#b9dcda;color:#276f70}.empty{padding:18px;color:#789092;font-size:10px}.report-grid{display:grid;grid-template-columns:1fr 1fr;gap:13px}.report-grid article{display:grid;grid-template-columns:42px 1fr auto;gap:12px;align-items:center;background:#fff;border:1px solid #dfe9ea;border-radius:14px;padding:16px}.report-grid article>span{font-size:24px}.report-grid b,.report-grid small{display:block}.report-grid b{font-size:11px}.report-grid small{font-size:8px;color:#758c8e;margin-top:3px}.help-grid{display:grid;grid-template-columns:1fr 1fr;gap:14px}.help-grid article{background:#fff;border:1px solid #dfe9ea;border-radius:14px;padding:19px}.help-grid article>span{width:34px;height:34px;border-radius:10px;background:#e8f4f3;display:grid;place-items:center;color:#267475;font-weight:800}.help-grid h3{font-size:12px}.help-grid p{font-size:9px;color:#657f82;line-height:1.7}.help-grid .warning{background:#fffaf1;border-color:#f0dfba}.modal-bg{position:fixed;inset:0;background:rgba(8,35,39,.5);z-index:80}.modal{position:fixed;z-index:81;left:50%;top:50%;transform:translate(-50%,-50%);width:min(720px,94vw);max-height:90vh;overflow:auto;background:#fff;border-radius:18px;padding:22px;box-shadow:0 35px 90px rgba(5,30,35,.3)}.modal-head{display:flex;justify-content:space-between}.modal-head>button{border:0;background:#eef3f3;width:32px;height:32px;border-radius:9px}.modal-head span{font-size:8px;color:#3B78A6;text-transform:uppercase;font-weight:800}.modal-head h2{margin:5px 0;font-size:21px}.modal-head small{font-size:8px;color:#728a8c}.mode-box{display:grid;grid-template-columns:1fr 1fr;gap:10px;margin:18px 0}.mode-box label{display:flex;gap:9px;border:1px solid #dfe9ea;border-radius:12px;padding:12px}.mode-box b,.mode-box small{display:block}.mode-box b{font-size:10px}.mode-box small{font-size:8px;color:#72898c;margin-top:3px}.file-zone{display:grid;place-items:center;border:2px dashed #c9dadd;border-radius:13px;padding:22px;background:#fafcfc;cursor:pointer}.file-zone input{display:none}.file-zone>span{font-size:25px}.file-zone b{font-size:11px;margin-top:6px}.file-zone small{font-size:8px;color:#7b9193;margin-top:3px}.column-preview{margin-top:15px}.column-preview>b{font-size:9px}.column-preview>div{display:flex;flex-wrap:wrap;gap:5px;margin-top:8px}.column-preview span{font-size:7px;padding:5px 7px;background:#eef3f4;border-radius:7px;color:#567174}.column-preview span.pk{background:#fff1e9;color:#a15b41}.result{display:grid;grid-template-columns:repeat(4,1fr);gap:8px;margin-top:15px}.result div{padding:10px;background:#f3faf6;border-radius:9px;text-align:center}.result b,.result small{display:block}.result b{font-size:16px}.result small{font-size:7px;color:#6c8587}.result .bad{background:#fff0f0;color:#9f3e3e}.errors{margin-top:10px;padding:10px;background:#fff5f5;border-radius:9px;max-height:160px;overflow:auto}.errors>b{font-size:9px}.errors p{font-size:7px;margin:5px 0;color:#8d4a4a}.modal-actions{display:flex;justify-content:flex-end;gap:8px;margin-top:17px}.modal-actions .primary{background:#3B78A6;color:#fff;border-color:#3B78A6}.modal-actions button:disabled,.primary-btn:disabled{opacity:.55;cursor:not-allowed}@media(max-width:900px){.ux-flow{grid-template-columns:1fr}.ux-flow i{display:none}.summary-grid,.report-grid,.help-grid{grid-template-columns:1fr}.table-row{grid-template-columns:1fr}.actions{flex-wrap:wrap}.mode-box{grid-template-columns:1fr}}
  `]
})
export class BackupCenterComponent implements OnInit{
  private api=inject(ApiService);
  readonly services=signal<ServiceCatalog[]>([]);readonly reports=signal<ReportInfo[]>([]);readonly tab=signal<'tables'|'reports'|'help'>('tables');readonly message=signal('');readonly messageType=signal<'ok'|'error'>('ok');readonly globalBusy=signal(false);readonly importBusy=signal(false);readonly importOpen=signal(false);readonly selectedService=signal<ServiceCatalog|null>(null);readonly selectedTable=signal<TableMeta|null>(null);readonly selectedFile=signal<File|null>(null);readonly importResult=signal<ImportResponse|null>(null);search='';importMode='APPEND';
  ngOnInit(){this.load();this.api.get<ReportInfo[]>('/backups/reports').subscribe({next:v=>this.reports.set(v),error:()=>this.reports.set([])});}
  load(){this.api.get<ServiceCatalog[]>('/backups/catalog').subscribe({next:v=>this.services.set(v),error:e=>this.notify(e?.error?.message||'No se pudo cargar el catálogo de respaldo.','error')});}
  filteredServices(){const q=this.search.trim().toLowerCase();if(!q)return this.services();return this.services().map(s=>({...s,tables:s.tables.filter(t=>t.tableName.toLowerCase().includes(q)||this.friendly(t.tableName).toLowerCase().includes(q)||s.label.toLowerCase().includes(q))})).filter(s=>s.tables.length);}
  totalTables(){return this.services().reduce((n,s)=>n+s.tables.length,0)}totalRows(){return this.services().reduce((n,s)=>n+this.serviceRows(s),0)}serviceRows(s:ServiceCatalog){return s.tables.reduce((n,t)=>n+Number(t.rowCount||0),0)}
  friendly(v:string){const labels:Record<string,string>={PATIENTS:'Pacientes',APPOINTMENTS:'Citas',DENTAL_CHAIRS:'Sillones dentales',DOCTOR_PROFILES:'Doctores',APP_USERS:'Usuarios',BUDGETS:'Presupuestos',ODONTOGRAMS:'Odontogramas',CLINICAL_DOCUMENTS:'Documentos clínicos',PUBLIC_INQUIRIES:'Solicitudes web',TREATMENT_CATALOG:'Catálogo de tratamientos',TREATMENT_PROFORMAS:'Proformas de tratamiento',TREATMENT_PROFORMA_HISTORY:'Historial de proformas',CLINICAL_ENCOUNTERS:'Atenciones clínicas',AUDIT_ENTRIES:'Auditoría',BACKUP_VERIFICATIONS:'Verificaciones de respaldo',PAYMENTS:'Pagos',EXPENSES:'Gastos',CASH_SESSIONS:'Sesiones de caja',DOCTOR_COMMISSIONS:'Comisiones',DOCTOR_COMMISSION_RULES:'Reglas de comisión',MEMBERSHIPS:'Membresías',INVENTORY_ITEMS:'Productos de inventario',STOCK_MOVEMENTS:'Movimientos de inventario',LAB_ORDERS:'Órdenes de laboratorio',EMPLOYEES:'Empleados',EQUIPMENT:'Equipos',COMPLAINTS:'Reclamos',DENTAL_EMERGENCIES:'Emergencias',FOLLOW_UPS:'Seguimientos',SURVEY_RESPONSES:'Respuestas de satisfacción',SURVEY_TOKENS:'Tokens de encuesta'};return labels[v.toUpperCase()]||v.toLowerCase().split('_').map(x=>x.charAt(0).toUpperCase()+x.slice(1)).join(' ')}
  downloadGlobal(){this.globalBusy.set(true);this.download('/backups/global','respaldo-clinica-cardenas.zip',()=>this.globalBusy.set(false));}
  downloadTable(s:ServiceCatalog,t:TableMeta){this.download(`/backups/tables/${encodeURIComponent(s.key)}/${encodeURIComponent(t.tableName)}/excel`,`tabla-${s.key}-${t.tableName}.xlsx`)}
  downloadTemplate(s:ServiceCatalog,t:TableMeta){this.download(`/backups/tables/${encodeURIComponent(s.key)}/${encodeURIComponent(t.tableName)}/template`,`plantilla-${s.key}-${t.tableName}.xlsx`)}
  downloadReport(r:ReportInfo){this.download(`/backups/reports/${encodeURIComponent(r.id)}/excel`,`informe-${r.id}.xlsx`)}
  private download(path:string,name:string,done?:()=>void){this.api.download(path).subscribe({next:blob=>{const url=URL.createObjectURL(blob);const a=document.createElement('a');a.href=url;a.download=name;a.click();URL.revokeObjectURL(url);done?.();},error:e=>{this.notify(e?.error?.message||'No se pudo generar la descarga.','error');done?.();}})}
  openImport(s:ServiceCatalog,t:TableMeta){this.selectedService.set(s);this.selectedTable.set(t);this.selectedFile.set(null);this.importResult.set(null);this.importMode='APPEND';this.importOpen.set(true)}closeImport(){if(this.importBusy())return;this.importOpen.set(false);this.selectedFile.set(null);this.importResult.set(null)}selectFile(e:Event){const input=e.target as HTMLInputElement;this.selectedFile.set(input.files?.[0]||null);this.importResult.set(null)}
  runImport(){const s=this.selectedService(),t=this.selectedTable(),file=this.selectedFile();if(!s||!t||!file)return;const form=new FormData();form.append('file',file);this.importBusy.set(true);this.api.postForm<ImportResponse>(`/backups/tables/${encodeURIComponent(s.key)}/${encodeURIComponent(t.tableName)}/import?mode=${this.importMode}`,form).subscribe({next:v=>{this.importBusy.set(false);this.importResult.set(v);this.notify(v.errorCount?`Importación terminada con ${v.errorCount} filas observadas.`:`Importación correcta: ${v.inserted} insertados y ${v.updated} actualizados.`,v.errorCount?'error':'ok');this.load();},error:e=>{this.importBusy.set(false);this.notify(e?.error?.message||'No se pudo importar el Excel.','error')}})}
  reportHint(id:string){if(id.includes('diarios'))return'Fotografía del día actual';if(id.includes('mensuales')||id.includes('sillones'))return'Periodo del mes actual';if(id==='cartera-pendiente')return'Tratamientos con saldo pendiente';if(id==='satisfaccion')return'Indicadores de encuestas';return'Snapshot disponible al momento de descargar'}
  notify(t:string,k:'ok'|'error'='ok'){this.message.set(t);this.messageType.set(k);setTimeout(()=>{if(this.message()===t)this.message.set('')},7000)}
}
