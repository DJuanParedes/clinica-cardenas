import { Component, inject, OnInit, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../core/api.service';

interface Treatment { id?:number; name:string; category?:string; specialty?:string; defaultDurationMinutes:number; referencePrice:number; active:boolean; }
interface Doctor { id:number; fullName:string; specialty:string; active:boolean; }
interface CommissionRule { id?:number; doctorName:string; commissionRate:number; active:boolean; updatedBy?:string; updatedAt?:string; }
interface TreatmentCommissionRule { id?:number; doctorName:string; treatmentName:string; commissionRate:number; active:boolean; updatedBy?:string; updatedAt?:string; }

@Component({
  selector:'app-admin-financial-settings', standalone:true, imports:[FormsModule,DecimalPipe],
  template:`
  <section class="page-head"><div><span>Configuración administrativa</span><h1>Tratamientos y comisiones</h1><p>Define el catálogo y las reglas automáticas de comisión: porcentaje predeterminado por doctor y excepciones únicamente cuando un tratamiento tenga un porcentaje diferente.</p></div></section>
  @if(message()){<div class="msg" [class.error]="messageType()==='error'">{{message()}}</div>}

  <section class="grid-layout">
    <article class="panel-card">
      <div class="card-title"><div><span>Catálogo maestro</span><h2>Tratamientos</h2><p>Clasificación obligatoria: Endodoncia, Ortodoncia o Generales.</p></div><button class="primary-btn" (click)="newTreatment()">＋ Nuevo tratamiento</button></div>
      @if(treatmentFormOpen()){
        <div class="edit-box">
          <div class="form-grid">
            <label>Nombre<input [(ngModel)]="treatmentDraft.name" placeholder="Ej. Corona de zirconio"></label>
            <label>Tipo<select [(ngModel)]="treatmentDraft.category"><option value="GENERALES">Generales</option><option value="ENDODONCIA">Endodoncia</option><option value="ORTODONCIA">Ortodoncia</option></select></label>
            <label>Duración referencial (min)<input type="number" min="10" max="480" [(ngModel)]="treatmentDraft.defaultDurationMinutes"></label>
            <label>Precio referencial S/<input type="number" min="0" step="0.01" [(ngModel)]="treatmentDraft.referencePrice"></label>
          </div>
          <div class="actions"><button class="secondary-btn" (click)="treatmentFormOpen.set(false)">Cancelar</button><button class="primary-btn" (click)="saveTreatment()">{{editingTreatmentId()?'Guardar cambios':'Crear tratamiento'}}</button></div>
        </div>
      }
      @for(cat of categories;track cat){<div class="category-block">
        <div class="category-title"><b>{{categoryLabel(cat)}}</b><span>{{treatmentsByCategory(cat).length}} tratamiento(s)</span></div>
        <div class="table"><table><thead><tr><th>Tratamiento</th><th>Duración</th><th>Precio ref.</th><th>Estado</th><th>Acciones</th></tr></thead><tbody>
          @for(t of treatmentsByCategory(cat);track t.id){<tr><td><b>{{t.name}}</b></td><td>{{t.defaultDurationMinutes}} min</td><td>S/ {{t.referencePrice|number:'1.2-2'}}</td><td><span class="pill" [class.off]="!t.active">{{t.active?'Activo':'Inactivo'}}</span></td><td><button class="link" (click)="editTreatment(t)">Editar</button><button class="link" (click)="toggleTreatment(t)">{{t.active?'Desactivar':'Activar'}}</button></td></tr>}
        </tbody></table></div>
      </div>}
    </article>

    <article class="panel-card">
      <div class="card-title"><div><span>Reglas por profesional</span><h2>Comisiones de doctores</h2><p>Primero coloca el porcentaje predeterminado. Solo registra excepciones en tratamientos con una comisión distinta.</p></div></div>
      <div class="formula"><b>Regla automática</b><span>1) Usa la excepción Doctor + Tratamiento si existe. 2) Si no existe, usa el porcentaje predeterminado del doctor. 3) El laboratorio se descuenta proporcionalmente antes de calcular cada comisión.</span></div>
      <div class="doctor-list">
        @for(d of activeDoctors();track d.id){
          <article class="doctor-card" [class.open]="selectedDoctor()===d.fullName">
            <div class="doctor-row"><div><b>{{d.fullName}}</b><small>{{d.specialty}}</small></div><label>% predeterminado<input type="number" min="0" max="100" step="0.01" [ngModel]="rateFor(d.fullName)" (ngModelChange)="setRate(d.fullName,$event)"></label><label class="check"><input type="checkbox" [ngModel]="activeFor(d.fullName)" (ngModelChange)="setRuleActive(d.fullName,$event)"> Activa</label><button class="primary-btn" (click)="saveRule(d.fullName)">Guardar</button><button class="secondary-btn wide" (click)="toggleDoctor(d.fullName)">{{selectedDoctor()===d.fullName?'Ocultar excepciones':'Excepciones por tratamiento'}}</button></div>
            @if(selectedDoctor()===d.fullName){
              <div class="exceptions">
                <div class="exception-head"><div><b>Excepciones de {{d.fullName}}</b><small>Deja un tratamiento sin excepción para que use {{rateFor(d.fullName)|number:'1.0-2'}}% predeterminado.</small></div></div>
                <div class="exception-list">
                  @for(t of activeTreatments();track t.id){
                    <div class="exception-row"><div><b>{{t.name}}</b><small>{{categoryLabel(normCategory(t.category||t.specialty))}}</small></div>
                      @if(ruleForTreatment(d.fullName,t.name);as rule){
                        <label>Excepción %<input type="number" min="0" max="100" step="0.01" [ngModel]="overrideDraft[key(d.fullName,t.name)]??rule.commissionRate" (ngModelChange)="setOverride(d.fullName,t.name,$event)"></label>
                        <span class="effective">Se usa excepción</span><button class="primary-btn" (click)="saveOverride(d.fullName,t.name)">Guardar</button><button class="link danger" (click)="removeOverride(rule)">Usar predeterminado</button>
                      } @else {
                        <label>Excepción %<input type="number" min="0" max="100" step="0.01" [ngModel]="overrideDraft[key(d.fullName,t.name)]??rateFor(d.fullName)" (ngModelChange)="setOverride(d.fullName,t.name,$event)"></label>
                        <span class="effective default">Actual: {{rateFor(d.fullName)|number:'1.0-2'}}%</span><button class="secondary-btn" (click)="saveOverride(d.fullName,t.name)">Crear excepción</button><span></span>
                      }
                    </div>
                  }
                </div>
              </div>
            }
          </article>
        }
      </div>
      <p class="note">Finanzas no puede editar porcentajes durante una liquidación. Las reglas quedan definidas previamente por ADMIN y el sistema calcula automáticamente la comisión efectiva del periodo.</p>
    </article>
  </section>
  `,
  styles:[`
  .grid-layout{display:grid;grid-template-columns:1.15fr .85fr;gap:16px}.card-title{display:flex;justify-content:space-between;gap:14px;align-items:flex-start}.card-title p{font-size:9px;color:#789092;margin:4px 0}.msg{padding:10px 13px;border-radius:9px;background:#e9f7ef;color:#307151;margin-bottom:13px}.msg.error{background:#fff0f0;color:#a33}.edit-box{background:#f7fbfb;border:1px solid #dfeaea;border-radius:12px;padding:13px;margin:12px 0}.form-grid{display:grid;grid-template-columns:1.4fr 1fr 1fr 1fr;gap:10px}.form-grid label,.doctor-row label,.exception-row label{font-size:8px;font-weight:700;color:#567276}.form-grid input,.form-grid select,.doctor-row input,.exception-row input{width:100%;box-sizing:border-box;border:1px solid #d8e5e5;border-radius:8px;padding:9px;margin-top:5px;background:#fff}.actions{display:flex;justify-content:flex-end;gap:8px;margin-top:10px}.category-block{margin-top:16px}.category-title{display:flex;justify-content:space-between;align-items:center;padding:9px 10px;background:#edf7f6;border-radius:9px}.category-title b{font-size:11px;color:#175d62}.category-title span{font-size:8px;color:#6f8588}.table{overflow:auto}table{width:100%;border-collapse:collapse;font-size:9px}th,td{text-align:left;padding:9px;border-bottom:1px solid #edf1f1}.pill{display:inline-block;padding:4px 7px;border-radius:20px;background:#e3f5eb;color:#267450;font-size:7px}.pill.off{background:#f0f2f2;color:#7b8586}.link{border:0;background:transparent;color:#087f85;font-weight:700;font-size:8px;cursor:pointer;margin-right:7px}.link.danger{color:#a34f4f}.formula{background:#fff7e9;border:1px solid #f0dfb7;border-radius:11px;padding:13px;margin:12px 0}.formula b,.formula span{display:block}.formula b{font-size:9px;color:#805d1f}.formula span{font-size:9px;margin-top:5px;color:#5f552f;line-height:1.55}.doctor-list{display:grid;gap:9px}.doctor-card{border:1px solid #e1ebeb;border-radius:12px;overflow:hidden}.doctor-card.open{border-color:#9fc9d8;box-shadow:0 8px 24px rgba(42,90,110,.08)}.doctor-row{display:grid;grid-template-columns:1fr 115px 65px 72px;gap:8px;align-items:end;padding:11px}.doctor-row>div b,.doctor-row>div small{display:block}.doctor-row>div b{font-size:10px}.doctor-row>div small{font-size:8px;color:#788e90;margin-top:3px}.doctor-row .check{display:flex;align-items:center;gap:5px;padding-bottom:8px}.doctor-row .check input{width:auto;margin:0}.wide{grid-column:1/-1}.exceptions{border-top:1px solid #e2eded;background:#f8fbfb;padding:12px}.exception-head b,.exception-head small{display:block}.exception-head b{font-size:10px;color:#315d65}.exception-head small{font-size:8px;color:#71898d;margin-top:3px}.exception-list{margin-top:10px;display:grid;gap:6px}.exception-row{display:grid;grid-template-columns:1fr 90px 88px 88px 105px;gap:7px;align-items:end;background:#fff;border:1px solid #e5eded;border-radius:9px;padding:8px}.exception-row>div b,.exception-row>div small{display:block}.exception-row>div b{font-size:9px}.exception-row>div small{font-size:7px;color:#839698;margin-top:2px}.effective{font-size:7px;color:#8a5f17;background:#fff2cf;border-radius:20px;padding:6px;text-align:center;margin-bottom:3px}.effective.default{color:#477078;background:#edf5f6}.note{font-size:8px;color:#72898b;line-height:1.55;background:#f7fafa;padding:10px;border-radius:8px;margin-top:13px}@media(max-width:1150px){.grid-layout{grid-template-columns:1fr}.form-grid{grid-template-columns:1fr 1fr}}@media(max-width:760px){.form-grid{grid-template-columns:1fr}.doctor-row,.exception-row{grid-template-columns:1fr 1fr}.wide{grid-column:1/-1}.exception-row>div{grid-column:1/-1}}
  `]
})
export class AdminFinancialSettingsComponent implements OnInit {
  private api=inject(ApiService);
  readonly treatments=signal<Treatment[]>([]);readonly doctors=signal<Doctor[]>([]);readonly rules=signal<CommissionRule[]>([]);readonly treatmentRules=signal<TreatmentCommissionRule[]>([]);readonly selectedDoctor=signal<string|null>(null);
  readonly treatmentFormOpen=signal(false);readonly editingTreatmentId=signal<number|null>(null);readonly message=signal('');readonly messageType=signal<'success'|'error'>('success');readonly categories=['ENDODONCIA','ORTODONCIA','GENERALES'];
  treatmentDraft:Treatment={name:'',category:'GENERALES',specialty:'',defaultDurationMinutes:30,referencePrice:0,active:true};rateDraft:Record<string,number>={};activeDraft:Record<string,boolean>={};overrideDraft:Partial<Record<string,number>>={};
  ngOnInit(){this.load();}
  load(){this.api.get<Treatment[]>('/treatments/all').subscribe({next:v=>this.treatments.set(v),error:e=>this.notify(e?.error?.detail||e?.error?.message||'No se cargó el catálogo.','error')});this.api.get<Doctor[]>('/scheduling/doctors').subscribe({next:v=>this.doctors.set(v),error:()=>this.doctors.set([])});this.api.get<CommissionRule[]>('/finance/commission-rules').subscribe({next:v=>{this.rules.set(v);for(const r of v){this.rateDraft[r.doctorName]=Number(r.commissionRate||0);this.activeDraft[r.doctorName]=!!r.active;}},error:()=>this.rules.set([])});this.api.get<TreatmentCommissionRule[]>('/finance/commission-treatment-rules').subscribe({next:v=>{this.treatmentRules.set(v);for(const r of v)this.overrideDraft[this.key(r.doctorName,r.treatmentName)]=Number(r.commissionRate||0);},error:()=>this.treatmentRules.set([])});}
  activeDoctors(){return this.doctors().filter(d=>d.active).sort((a,b)=>a.fullName.localeCompare(b.fullName));}activeTreatments(){return this.treatments().filter(t=>t.active).sort((a,b)=>a.name.localeCompare(b.name));}treatmentsByCategory(c:string){return this.treatments().filter(t=>this.normCategory(t.category||t.specialty)===c).sort((a,b)=>a.name.localeCompare(b.name));}
  normCategory(v?:string){const n=(v||'').toUpperCase();return n.includes('ENDO')?'ENDODONCIA':n.includes('ORTO')?'ORTODONCIA':'GENERALES';}categoryLabel(v:string){return v==='ENDODONCIA'?'Endodoncia':v==='ORTODONCIA'?'Ortodoncia':'Generales';}
  newTreatment(){this.treatmentDraft={name:'',category:'GENERALES',specialty:'Odontología general',defaultDurationMinutes:30,referencePrice:0,active:true};this.editingTreatmentId.set(null);this.treatmentFormOpen.set(true);}editTreatment(t:Treatment){this.treatmentDraft={...t,category:this.normCategory(t.category||t.specialty)};this.editingTreatmentId.set(t.id||null);this.treatmentFormOpen.set(true);}
  saveTreatment(){if(!this.treatmentDraft.name.trim()){this.notify('Escribe el nombre del tratamiento.','error');return;}const cat=this.normCategory(this.treatmentDraft.category);const specialty=cat==='ENDODONCIA'?'Endodoncia':cat==='ORTODONCIA'?'Ortodoncia':'Odontología general';const body={name:this.treatmentDraft.name.trim(),category:cat,specialty,defaultDurationMinutes:Number(this.treatmentDraft.defaultDurationMinutes||30),referencePrice:Number(this.treatmentDraft.referencePrice||0)};const id=this.editingTreatmentId();const req=id?this.api.put<Treatment>(`/treatments/${id}`,body):this.api.post<Treatment>('/treatments',body);req.subscribe({next:()=>{this.notify('Catálogo de tratamientos actualizado.');this.treatmentFormOpen.set(false);this.load();},error:e=>this.notify(e?.error?.detail||e?.error?.message||'No se pudo guardar el tratamiento.','error')});}
  toggleTreatment(t:Treatment){this.api.patch<Treatment>(`/treatments/${t.id}/active?value=${!t.active}`).subscribe({next:()=>{this.notify(t.active?'Tratamiento desactivado.':'Tratamiento activado.');this.load();},error:e=>this.notify(e?.error?.detail||e?.error?.message||'No se pudo cambiar el estado.','error')});}
  rateFor(name:string){return this.rateDraft[name]??Number(this.rules().find(r=>r.doctorName===name)?.commissionRate||0);}activeFor(name:string){return this.activeDraft[name]??(this.rules().find(r=>r.doctorName===name)?.active??true);}setRate(name:string,v:any){this.rateDraft[name]=Math.max(0,Math.min(100,Number(v||0)));}setRuleActive(name:string,v:any){this.activeDraft[name]=!!v;}
  saveRule(name:string){this.api.post<CommissionRule>('/finance/commission-rules',{doctorName:name,commissionRate:this.rateFor(name),active:this.activeFor(name)}).subscribe({next:()=>{this.notify(`Porcentaje predeterminado de ${name} guardado.`);this.load();},error:e=>this.notify(e?.error?.detail||e?.error?.message||'No se pudo guardar la comisión.','error')});}
  toggleDoctor(name:string){this.selectedDoctor.set(this.selectedDoctor()===name?null:name);}key(d:string,t:string){return `${d.toLowerCase()}|${t.toLowerCase()}`;}ruleForTreatment(d:string,t:string){return this.treatmentRules().find(r=>r.doctorName.toLowerCase()===d.toLowerCase()&&r.treatmentName.toLowerCase()===t.toLowerCase())||null;}setOverride(d:string,t:string,v:any){this.overrideDraft[this.key(d,t)]=Math.max(0,Math.min(100,Number(v||0)));}
  saveOverride(d:string,t:string){const rate=this.overrideDraft[this.key(d,t)]??this.rateFor(d);this.api.post<TreatmentCommissionRule>('/finance/commission-treatment-rules',{doctorName:d,treatmentName:t,commissionRate:rate,active:true}).subscribe({next:()=>{this.notify(`Excepción guardada: ${d} · ${t} = ${rate}%.`);this.load();},error:e=>this.notify(e?.error?.detail||e?.error?.message||'No se pudo guardar la excepción.','error')});}
  removeOverride(rule:TreatmentCommissionRule){if(!rule.id)return;this.api.delete<void>(`/finance/commission-treatment-rules/${rule.id}`).subscribe({next:()=>{delete this.overrideDraft[this.key(rule.doctorName,rule.treatmentName)];this.notify(`Se eliminó la excepción de ${rule.treatmentName}; volverá a usar el porcentaje predeterminado.`);this.load();},error:e=>this.notify(e?.error?.detail||e?.error?.message||'No se pudo eliminar la excepción.','error')});}
  private notify(t:string,k:'success'|'error'='success'){this.message.set(t);this.messageType.set(k);}
}
