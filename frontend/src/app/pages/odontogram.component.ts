import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../core/api.service';
import { AuthService } from '../core/auth.service';

type ToothCondition='SANO'|'CARIES'|'RESTAURACIÓN'|'AUSENTE'|'ENDODONCIA'|'CORONA'|'IMPLANTE'|'SELLANTE'|'FRACTURA';
type Dentition='PERMANENTE'|'TEMPORAL'|'MIXTA';
type CariesRisk='NO_EVALUADO'|'BAJO'|'MODERADO'|'ALTO';
type CariesStage='NO_EVALUADA'|'INICIAL'|'MODERADA'|'SEVERA';
type CariesActivity='NO_EVALUADA'|'ACTIVA'|'INACTIVA';
type ToothSurface='M'|'D'|'V'|'L'|'O'|'I';

interface Patient{id:number;fullName:string;dni:string;}
interface ToothFinding{condition:ToothCondition;surfaces:ToothSurface[];cariesStage?:CariesStage;cariesActivity?:CariesActivity;diagnosis?:string;treatment?:string;}
interface FdiState{standard:string;dentition:Dentition;cariesRisk:CariesRisk;riskFactors:string[];protectiveFactors:string[];assessedAt?:string;teeth:Record<string,ToothFinding>;}
interface OdontogramRecord{id?:number;patientId?:number|null;patientName:string;stateJson:string;diagnosis:string;treatment:string;updatedBy?:string;updatedAt?:string;standard?:string;}

@Component({
  selector:'app-odontogram',
  standalone:true,
  imports:[FormsModule],
  template:`
<section class="page-head">
  <div><span>Registro clínico · FDI / ISO 3950</span><h1>Odontograma digital</h1><p>Dentición permanente, temporal o mixta con numeración internacional de dos dígitos, superficies y evaluación de caries.</p></div>
  <button class="primary-btn" (click)="save()" [disabled]="saving()||!selectedPatient()">{{saving()?'Guardando...':'Guardar odontograma'}}</button>
</section>
@if(message()){<div class="inline-message" [class.error]="messageType()==='error'">{{message()}}</div>}

<section class="clinical-guide">
  <article class="done"><span>1</span><div><b>Paciente</b><small>Selecciona la ficha correcta</small></div></article><i>→</i>
  <article><span>2</span><div><b>Evaluación general</b><small>Dentición y riesgo de caries</small></div></article><i>→</i>
  <article><span>3</span><div><b>Pieza y superficies</b><small>Condición, severidad y actividad</small></div></article><i>→</i>
  <article><span>4</span><div><b>Conducta clínica</b><small>Diagnóstico, plan y seguimiento</small></div></article><i>→</i>
  <article><span>5</span><div><b>Guardar</b><small>Validación FDI en el servidor</small></div></article>
</section>

<section class="standard-card panel-card">
  <div><small>Estándar aplicado</small><b>Notación FDI de dos dígitos / ISO 3950:2016</b><p>Cuadrantes 1–4 para dientes permanentes y 5–8 para temporales.</p></div>
  <label>Dentición<select [ngModel]="dentition()" (ngModelChange)="changeDentition($event)"><option>PERMANENTE</option><option>TEMPORAL</option><option>MIXTA</option></select></label>
  <label>Riesgo de caries<select [ngModel]="cariesRisk()" (ngModelChange)="cariesRisk.set($event)"><option>NO_EVALUADO</option><option>BAJO</option><option>MODERADO</option><option>ALTO</option></select></label>
</section>

<section class="risk-grid panel-card">
  <label>Factores de riesgo <small>Uno por línea: caries activa, azúcar frecuente, hiposalivación, placa, barreras de acceso…</small><textarea [(ngModel)]="riskFactorsText"></textarea></label>
  <label>Factores protectores <small>Uno por línea: cepillado con flúor, controles preventivos, higiene interdental…</small><textarea [(ngModel)]="protectiveFactorsText"></textarea></label>
</section>

<section class="odontogram-layout">
  <article class="panel-card">
    <div class="patient-banner"><div><span>{{initials(selectedPatient())}}</span><div><b>{{selectedPatient()||'Selecciona un paciente'}}</b><small>DNI {{patientDni()}}</small></div></div><select [ngModel]="selectedPatientId()" (ngModelChange)="changePatient($event)">@for(p of patients();track p.id){<option [ngValue]="p.id">{{p.fullName}} · DNI {{p.dni}}</option>}</select></div>
    <div class="clinical-summary"><div><small>Piezas registradas</small><b>{{findingCount()}}</b></div><div class="caries"><small>Con caries</small><b>{{conditionCount('CARIES')}}</b></div><div class="treated"><small>Restauradas / tratadas</small><b>{{treatedCount()}}</b></div><div class="missing"><small>Ausentes</small><b>{{conditionCount('AUSENTE')}}</b></div><div><small>Superficies señaladas</small><b>{{surfaceCount()}}</b></div></div>
    <div class="legend">@for(s of conditions;track s){<span><i [attr.data-state]="s"></i>{{s}}</span>}</div>

    @if(showPermanent()){
      <div class="dentition-title"><b>Dentición permanente</b><small>FDI 11–48</small></div>
      <div class="arch"><h3>Arcada superior permanente</h3><div class="quadrant-axis"><span>Derecha del paciente</span><i>Línea media</i><span>Izquierda del paciente</span></div><div class="teeth">@for(n of permanentUpper;track n){<button type="button" (click)="select(n)" [class.selected]="selected()===n" [class.has-finding]="hasFinding(n)" [title]="toothDescription(n)"><span class="tooth" [attr.data-state]="stateOf(n)">🦷</span><b>{{n}}</b><em>{{shortCondition(stateOf(n))}}</em>@if(findingOf(n).surfaces.length){<small>{{findingOf(n).surfaces.join(' · ')}}</small>}</button>}</div></div>
      <div class="arch lower"><h3>Arcada inferior permanente</h3><div class="quadrant-axis"><span>Derecha del paciente</span><i>Línea media</i><span>Izquierda del paciente</span></div><div class="teeth">@for(n of permanentLower;track n){<button type="button" (click)="select(n)" [class.selected]="selected()===n" [class.has-finding]="hasFinding(n)" [title]="toothDescription(n)"><span class="tooth" [attr.data-state]="stateOf(n)">🦷</span><b>{{n}}</b><em>{{shortCondition(stateOf(n))}}</em>@if(findingOf(n).surfaces.length){<small>{{findingOf(n).surfaces.join(' · ')}}</small>}</button>}</div></div>
    }
    @if(showPrimary()){
      <div class="dentition-title primary"><b>Dentición temporal</b><small>FDI 51–85</small></div>
      <div class="arch compact"><h3>Arcada superior temporal</h3><div class="quadrant-axis"><span>Derecha del paciente</span><i>Línea media</i><span>Izquierda del paciente</span></div><div class="teeth">@for(n of primaryUpper;track n){<button type="button" (click)="select(n)" [class.selected]="selected()===n" [class.has-finding]="hasFinding(n)" [title]="toothDescription(n)"><span class="tooth" [attr.data-state]="stateOf(n)">🦷</span><b>{{n}}</b><em>{{shortCondition(stateOf(n))}}</em>@if(findingOf(n).surfaces.length){<small>{{findingOf(n).surfaces.join(' · ')}}</small>}</button>}</div></div>
      <div class="arch compact lower"><h3>Arcada inferior temporal</h3><div class="quadrant-axis"><span>Derecha del paciente</span><i>Línea media</i><span>Izquierda del paciente</span></div><div class="teeth">@for(n of primaryLower;track n){<button type="button" (click)="select(n)" [class.selected]="selected()===n" [class.has-finding]="hasFinding(n)" [title]="toothDescription(n)"><span class="tooth" [attr.data-state]="stateOf(n)">🦷</span><b>{{n}}</b><em>{{shortCondition(stateOf(n))}}</em>@if(findingOf(n).surfaces.length){<small>{{findingOf(n).surfaces.join(' · ')}}</small>}</button>}</div></div>
    }

    <div class="general-notes"><label>Diagnóstico general<textarea [(ngModel)]="diagnosis" placeholder="Resumen diagnóstico integral"></textarea></label><label>Plan general<textarea [(ngModel)]="treatment" placeholder="Prevención, tratamiento y seguimiento"></textarea></label></div>
  </article>

  <aside class="panel-card">
    <div class="card-title tooth-title"><div><span>Pieza FDI seleccionada</span><h2>{{selected()||'—'}}</h2><p>{{selected()?toothDescription(selected()!):''}}</p></div>@if(selected()&&hasFinding(selected()!)){<button class="clear-tooth" (click)="clearSelected()">Limpiar pieza</button>}</div>
    @if(selected()){
      <label class="section-label">1. Condición principal</label><div class="condition-grid">@for(s of conditions;track s){<button type="button" [class.active]="stateOf(selected()!)===s" [attr.data-state]="s" (click)="setCondition(s)"><i></i><span>{{conditionLabel(s)}}</span></button>}</div>
      <label class="section-label">2. Superficies afectadas <small>Selecciona una o varias. Oclusal corresponde a posteriores e incisal a anteriores.</small></label><div class="surface-grid">@for(s of surfaces;track s.code){<button type="button" [disabled]="!surfaceAllowed(selected()!,s.code)" [class.active]="hasSurface(selected()!,s.code)" (click)="toggleSurface(s.code)"><b>{{s.code}}</b><small>{{s.label}}</small></button>}</div>
      @if(stateOf(selected()!)==='CARIES'){
        <div class="caries-box"><b>Evaluación de la lesión</b><label>Severidad<select [ngModel]="findingOf(selected()!).cariesStage||'NO_EVALUADA'" (ngModelChange)="setFinding('cariesStage',$event)"><option>NO_EVALUADA</option><option>INICIAL</option><option>MODERADA</option><option>SEVERA</option></select></label><label>Actividad<select [ngModel]="findingOf(selected()!).cariesActivity||'NO_EVALUADA'" (ngModelChange)="setFinding('cariesActivity',$event)"><option>NO_EVALUADA</option><option>ACTIVA</option><option>INACTIVA</option></select></label></div>
      }
      <label>Diagnóstico de la pieza<textarea [ngModel]="findingOf(selected()!).diagnosis||''" (ngModelChange)="setFinding('diagnosis',$event)" placeholder="Hallazgo y evaluación clínica"></textarea></label>
      <label>Intervención / seguimiento<textarea [ngModel]="findingOf(selected()!).treatment||''" (ngModelChange)="setFinding('treatment',$event)" placeholder="Prevención, control o tratamiento indicado"></textarea></label>
    }@else{<div class="empty-state"><span>🦷</span><b>Selecciona una pieza</b><p>Registra condición, superficies, caries y conducta clínica.</p></div>}
    <div class="history"><h3>Trazabilidad</h3><div><b>Paciente</b><small>{{selectedPatient()||'—'}}</small></div><div><b>Última actualización</b><small>{{updatedAt()||'Sin guardar'}}</small></div><div><b>Actualizado por</b><small>{{updatedBy()||auth.user()?.name}}</small></div></div>
  </aside>
</section>
  `,
  styles:[`
.inline-message{padding:11px 14px;border-radius:10px;background:#e7f7ee;color:#36795a;border:1px solid #cbead8;margin-bottom:14px;font-size:11px}.inline-message.error{background:#fff0f0;color:#a94444;border-color:#f1cccc}.clinical-guide{display:grid;grid-template-columns:1fr auto 1fr auto 1fr auto 1fr auto 1fr;align-items:center;gap:7px;background:#fff;border:1px solid #e0eaeb;border-radius:14px;padding:13px 15px;margin-bottom:14px}.clinical-guide article{display:flex;align-items:center;gap:8px}.clinical-guide article>span{width:27px;height:27px;border-radius:9px;background:#eef4f5;color:#547175;display:grid;place-items:center;font-size:9px;font-weight:900}.clinical-guide article.done>span{background:#dff2fb;color:#356f98}.clinical-guide b,.clinical-guide small{display:block}.clinical-guide b{font-size:9px}.clinical-guide small{font-size:7px;color:#789092;margin-top:2px}.clinical-guide i{font-style:normal;color:#a4b7b9}.standard-card{display:grid;grid-template-columns:1fr 190px 190px;align-items:end;gap:16px;margin-bottom:14px;border-left:4px solid #3B78A6}.standard-card small,.standard-card b,.standard-card p{display:block}.standard-card small{font-size:8px;color:#3B78A6;text-transform:uppercase;font-weight:800}.standard-card b{font-size:13px;margin:4px 0}.standard-card p{font-size:9px;color:#6f888a;margin:0}.standard-card label,.risk-grid label,aside label,.general-notes label{font-size:9px;font-weight:700;color:#496a6c}.standard-card select,.patient-banner select,aside select,aside textarea,.risk-grid textarea,.general-notes textarea{display:block;width:100%;box-sizing:border-box;border:1px solid #dce7e8;border-radius:9px;padding:10px;background:#fff;margin-top:6px}.risk-grid{display:grid;grid-template-columns:1fr 1fr;gap:14px;margin-bottom:18px}.risk-grid label small{display:block;color:#789092;font-weight:400;line-height:1.5;margin-top:3px}.risk-grid textarea{min-height:65px;resize:vertical}.odontogram-layout{display:grid;grid-template-columns:minmax(0,1fr) 370px;gap:18px}.patient-banner{display:flex;justify-content:space-between;align-items:center;padding-bottom:14px;border-bottom:1px solid #e7eeee}.patient-banner>div{display:flex;align-items:center;gap:12px}.patient-banner>div>span{width:42px;height:42px;border-radius:12px;background:#e2f4f3;color:#3B78A6;display:grid;place-items:center;font-weight:800}.patient-banner b,.patient-banner small{display:block}.patient-banner small{font-size:9px;color:#789092;margin-top:3px}.clinical-summary{display:grid;grid-template-columns:repeat(5,1fr);border:1px solid #e4ecec;border-radius:11px;overflow:hidden;margin-top:13px}.clinical-summary>div{padding:10px;text-align:center;border-right:1px solid #e7eeee}.clinical-summary>div:last-child{border-right:0}.clinical-summary small,.clinical-summary b{display:block}.clinical-summary small{font-size:7px;color:#789092}.clinical-summary b{font-size:16px;margin-top:3px}.clinical-summary .caries b{color:#c54d4d}.clinical-summary .treated b{color:#3979b4}.clinical-summary .missing b{color:#6e7778}.legend{display:flex;flex-wrap:wrap;gap:12px;padding:14px 0}.legend span{font-size:7px;color:#607a7d}.legend i,.condition-grid i{display:inline-block;width:9px;height:9px;border-radius:3px;margin-right:5px;background:#cde5e4}.legend i[data-state='CARIES'],.condition-grid button[data-state='CARIES'] i{background:#ef5757}.legend i[data-state='RESTAURACIÓN'],.condition-grid button[data-state='RESTAURACIÓN'] i{background:#4e8de8}.legend i[data-state='AUSENTE'],.condition-grid button[data-state='AUSENTE'] i{background:#a7b1b2}.legend i[data-state='ENDODONCIA'],.condition-grid button[data-state='ENDODONCIA'] i{background:#e39139}.legend i[data-state='CORONA'],.condition-grid button[data-state='CORONA'] i{background:#945ec9}.legend i[data-state='IMPLANTE'],.condition-grid button[data-state='IMPLANTE'] i{background:#444}.legend i[data-state='SELLANTE'],.condition-grid button[data-state='SELLANTE'] i{background:#25a98b}.legend i[data-state='FRACTURA'],.condition-grid button[data-state='FRACTURA'] i{background:#b77345}.dentition-title{display:flex;justify-content:space-between;align-items:center;background:#eef7fb;border-radius:9px;padding:9px 12px;color:#315e78}.dentition-title.primary{background:#f5f0fb;color:#725590;margin-top:15px}.dentition-title b{font-size:10px}.dentition-title small{font-size:8px}.arch{padding:13px 0}.arch.lower{border-top:1px dashed #dbe6e7}.arch h3{text-align:center;font-size:9px;color:#648083;margin-bottom:5px}.quadrant-axis{display:grid;grid-template-columns:1fr 90px 1fr;align-items:center;text-align:center;margin-bottom:7px}.quadrant-axis span{font-size:6px;color:#87999a}.quadrant-axis i{font-size:6px;font-style:normal;color:#3B78A6;border-left:1px dashed #87aeb7;border-right:1px dashed #87aeb7}.teeth{display:flex;justify-content:center;gap:3px;flex-wrap:wrap}.teeth button{min-width:42px;border:1px solid transparent;background:transparent;border-radius:9px;padding:5px 3px;cursor:pointer;position:relative}.teeth button.has-finding{background:#f8fbfb;border-color:#dce8e9}.teeth button.selected{border-color:#3B78A6;background:#e8f5fb;box-shadow:0 0 0 2px #d9edf6}.tooth{display:block;font-size:27px;filter:grayscale(.4)}.compact .tooth{font-size:25px}.tooth[data-state='CARIES']{filter:sepia(1) saturate(5) hue-rotate(315deg)}.tooth[data-state='RESTAURACIÓN']{filter:sepia(1) saturate(4) hue-rotate(170deg)}.tooth[data-state='AUSENTE']{opacity:.2}.tooth[data-state='ENDODONCIA']{filter:sepia(1) saturate(5) hue-rotate(345deg)}.tooth[data-state='CORONA']{filter:sepia(1) saturate(4) hue-rotate(235deg)}.tooth[data-state='IMPLANTE']{filter:grayscale(1)}.tooth[data-state='SELLANTE']{filter:sepia(1) saturate(4) hue-rotate(105deg)}.tooth[data-state='FRACTURA']{filter:sepia(1) saturate(4) hue-rotate(330deg)}.teeth button>b,.teeth button>em,.teeth button>small{display:block}.teeth button>b{font-size:8px;color:#3f656a;margin-top:2px}.teeth button>em{font-size:5px;color:#798e90;font-style:normal;text-transform:uppercase;margin-top:2px;min-height:6px}.teeth button>small{font-size:5px;color:#3B78A6;margin-top:2px}.general-notes{display:grid;grid-template-columns:1fr 1fr;gap:13px;border-top:1px solid #e7eeee;padding-top:16px;margin-top:8px}.general-notes textarea{min-height:75px;resize:vertical}.tooth-title{align-items:flex-start}.tooth-title p{font-size:8px;color:#789092;margin:3px 0 0}.clear-tooth{border:1px solid #ead1d1;background:#fff5f5;color:#9d5050;border-radius:8px;padding:7px 9px;font-size:7px}.section-label{display:block;margin:12px 0 7px}.section-label small{display:block;font-size:7px;font-weight:400;color:#789092;line-height:1.4;margin-top:3px}.condition-grid{display:grid;grid-template-columns:repeat(3,1fr);gap:5px;margin-bottom:15px}.condition-grid button{display:flex;align-items:center;gap:2px;border:1px solid #dfe9e9;background:#fff;border-radius:8px;padding:7px 6px;text-align:left;color:#526e71}.condition-grid button.active{border-color:#3B78A6;background:#edf7fb;color:#285d7b;box-shadow:inset 0 0 0 1px #3B78A6}.condition-grid button i{flex:0 0 8px;width:8px;height:8px;margin-right:3px}.condition-grid button span{font-size:6px;font-weight:800}.surface-grid{display:grid;grid-template-columns:repeat(3,1fr);gap:6px;margin:7px 0 16px}.surface-grid button{border:1px solid #dce7e8;background:#fff;border-radius:8px;padding:7px 4px;color:#496a6c}.surface-grid button.active{background:#3B78A6;color:#fff;border-color:#3B78A6}.surface-grid button:disabled{opacity:.25}.surface-grid b,.surface-grid small{display:block}.surface-grid b{font-size:10px}.surface-grid small{font-size:6px;margin-top:2px}.caries-box{padding:12px;border:1px solid #f0d5d5;background:#fff7f7;border-radius:10px;margin-bottom:14px}.caries-box>b{font-size:9px;color:#a44}.caries-box label{display:block;margin-top:9px}.empty-state{text-align:center;padding:35px 10px;color:#6c8587}.empty-state span{font-size:55px}.empty-state b{display:block;color:#31585b;margin-top:12px}.empty-state p{font-size:10px;line-height:1.6}.history{border-top:1px solid #e7eeee;margin-top:24px;padding-top:18px}.history h3{font-size:11px}.history div{padding:9px 0;border-bottom:1px solid #edf2f2}.history b,.history small{display:block;font-size:9px}.history small{color:#7b9294;margin-top:2px}@media(max-width:1150px){.clinical-guide{grid-template-columns:repeat(5,1fr)}.clinical-guide>i{display:none}.odontogram-layout{grid-template-columns:1fr}.standard-card{grid-template-columns:1fr 1fr}.standard-card>div{grid-column:1/-1}}@media(max-width:700px){.clinical-guide,.clinical-summary,.risk-grid,.general-notes,.standard-card{grid-template-columns:1fr}.clinical-summary>div{border-right:0;border-bottom:1px solid #e7eeee}.standard-card>div{grid-column:auto}.patient-banner{align-items:stretch;gap:10px;flex-direction:column}.tooth{font-size:23px}.condition-grid{grid-template-columns:1fr 1fr}}
  `]
})
export class OdontogramComponent implements OnInit{
  private api=inject(ApiService);
  readonly auth=inject(AuthService);
  readonly conditions:ToothCondition[]=['SANO','CARIES','RESTAURACIÓN','AUSENTE','ENDODONCIA','CORONA','IMPLANTE','SELLANTE','FRACTURA'];
  readonly surfaces:{code:ToothSurface;label:string}[]=[{code:'M',label:'Mesial'},{code:'D',label:'Distal'},{code:'V',label:'Vestibular'},{code:'L',label:'Lingual'},{code:'O',label:'Oclusal'},{code:'I',label:'Incisal'}];
  readonly permanentUpper=[18,17,16,15,14,13,12,11,21,22,23,24,25,26,27,28];
  readonly permanentLower=[48,47,46,45,44,43,42,41,31,32,33,34,35,36,37,38];
  readonly primaryUpper=[55,54,53,52,51,61,62,63,64,65];
  readonly primaryLower=[85,84,83,82,81,71,72,73,74,75];
  readonly selected=signal<number|null>(16);
  readonly values=signal<Record<string,ToothFinding>>({});
  readonly dentition=signal<Dentition>('PERMANENTE');
  readonly cariesRisk=signal<CariesRisk>('NO_EVALUADO');
  readonly patients=signal<Patient[]>([]);
  readonly selectedPatientId=signal<number|null>(null);
  readonly selectedPatient=signal('');
  readonly updatedAt=signal('');
  readonly updatedBy=signal('');
  readonly saving=signal(false);
  readonly message=signal('');
  readonly messageType=signal<'success'|'error'>('success');
  diagnosis='';
  treatment='';
  riskFactorsText='';
  protectiveFactorsText='';

  ngOnInit(){
    this.api.get<Patient[]>('/patients').subscribe({next:v=>{this.patients.set(v);if(v.length){this.selectedPatientId.set(v[0].id);this.selectedPatient.set(v[0].fullName);this.load();}},error:e=>this.notify(e?.error?.message||'No se pudieron cargar los pacientes.','error')});
  }

  changePatient(id:number){const patient=this.patients().find(p=>p.id===Number(id));if(!patient)return;this.selectedPatientId.set(patient.id);this.selectedPatient.set(patient.fullName);this.load();}
  changeDentition(value:Dentition){
    const teeth=Object.keys(this.values()).map(Number);
    if(value==='PERMANENTE'&&teeth.some(x=>x>=50)){this.notify('Hay hallazgos en piezas temporales. Usa dentición MIXTA o elimínalos antes de cambiar.','error');return;}
    if(value==='TEMPORAL'&&teeth.some(x=>x<50)){this.notify('Hay hallazgos en piezas permanentes. Usa dentición MIXTA o elimínalos antes de cambiar.','error');return;}
    this.dentition.set(value);this.selected.set(value==='TEMPORAL'?55:16);
  }
  showPermanent(){return this.dentition()!=='TEMPORAL';}
  showPrimary(){return this.dentition()!=='PERMANENTE';}

  load(){
    if(!this.selectedPatient()||!this.selectedPatientId())return;
    this.api.get<OdontogramRecord>(`/odontograms?patientId=${this.selectedPatientId()}&patientName=${encodeURIComponent(this.selectedPatient())}`).subscribe({next:r=>{try{this.loadState(JSON.parse(r.stateJson||'{}'));}catch{this.resetState();this.notify('El registro anterior no pudo interpretarse; no se sobrescribirá hasta que guardes.','error');}this.diagnosis=r.diagnosis||'';this.treatment=r.treatment||'';this.updatedAt.set(r.updatedAt?.replace('T',' ')||'');this.updatedBy.set(r.updatedBy||'');},error:e=>this.notify(e?.error?.message||'No se pudo cargar el odontograma.','error')});
  }

  private loadState(raw:any){
    const source=raw?.teeth||raw||{};const findings:Record<string,ToothFinding>={};
    Object.entries(source).forEach(([code,value])=>{if(!/^([1-4][1-8]|[5-8][1-5])$/.test(code))return;if(typeof value==='string')findings[code]={condition:this.asCondition(value),surfaces:[]};else if(value&&typeof value==='object'){const v:any=value;findings[code]={condition:this.asCondition(v.condition),surfaces:Array.isArray(v.surfaces)?v.surfaces:[],cariesStage:v.cariesStage,cariesActivity:v.cariesActivity,diagnosis:v.diagnosis||'',treatment:v.treatment||''};}});
    this.values.set(findings);
    this.dentition.set(['PERMANENTE','TEMPORAL','MIXTA'].includes(raw?.dentition)?raw.dentition:this.inferDentition(Object.keys(findings)));
    this.cariesRisk.set(['NO_EVALUADO','BAJO','MODERADO','ALTO'].includes(raw?.cariesRisk)?raw.cariesRisk:'NO_EVALUADO');
    this.riskFactorsText=Array.isArray(raw?.riskFactors)?raw.riskFactors.join('\n'):'';this.protectiveFactorsText=Array.isArray(raw?.protectiveFactors)?raw.protectiveFactors.join('\n'):'';this.selected.set(this.dentition()==='TEMPORAL'?55:16);
  }
  private resetState(){this.values.set({});this.dentition.set('PERMANENTE');this.cariesRisk.set('NO_EVALUADO');this.riskFactorsText='';this.protectiveFactorsText='';}

  save(){
    if(!this.selectedPatient())return;this.saving.set(true);
    const state:FdiState={standard:'FDI_ISO_3950_2016',dentition:this.dentition(),cariesRisk:this.cariesRisk(),riskFactors:this.lines(this.riskFactorsText),protectiveFactors:this.lines(this.protectiveFactorsText),assessedAt:new Date().toISOString().slice(0,10),teeth:this.values()};
    const body:OdontogramRecord={patientId:this.selectedPatientId(),patientName:this.selectedPatient(),stateJson:JSON.stringify(state),diagnosis:this.diagnosis,treatment:this.treatment};
    this.api.put<OdontogramRecord>('/odontograms',body).subscribe({next:r=>{this.saving.set(false);this.loadState(JSON.parse(r.stateJson));this.updatedAt.set(r.updatedAt?.replace('T',' ')||'');this.updatedBy.set(r.updatedBy||'');this.notify('Odontograma FDI guardado y validado correctamente.');},error:e=>{this.saving.set(false);this.notify(e?.error?.message||'No se pudo guardar el odontograma.','error');}});
  }

  select(n:number){this.selected.set(n);}
  stateOf(n:number){return this.findingOf(n).condition;}
  findingOf(n:number):ToothFinding{return this.values()[String(n)]??{condition:'SANO',surfaces:[]};}
  hasFinding(n:number){const f=this.findingOf(n);return f.condition!=='SANO'||f.surfaces.length>0||!!f.diagnosis?.trim()||!!f.treatment?.trim();}
  findingCount(){return Object.keys(this.values()).map(Number).filter(n=>this.hasFinding(n)).length;}
  conditionCount(condition:ToothCondition){return Object.values(this.values()).filter(f=>f.condition===condition).length;}
  treatedCount(){const treated=new Set<ToothCondition>(['RESTAURACIÓN','ENDODONCIA','CORONA','IMPLANTE','SELLANTE']);return Object.values(this.values()).filter(f=>treated.has(f.condition)).length;}
  surfaceCount(){return Object.values(this.values()).reduce((total,f)=>total+(f.surfaces?.length||0),0);}
  setCondition(condition:ToothCondition){const n=this.selected();if(!n)return;if(condition==='SANO'){this.clearSelected();return;}const current=this.findingOf(n);const next:ToothFinding={...current,condition};if(condition==='CARIES'){next.cariesStage=current.cariesStage||'INICIAL';next.cariesActivity=current.cariesActivity||'ACTIVA';}else{delete next.cariesStage;delete next.cariesActivity;}if(['AUSENTE','IMPLANTE'].includes(condition))next.surfaces=[];this.updateFinding(n,next);}
  setFinding(field:'cariesStage'|'cariesActivity'|'diagnosis'|'treatment',value:string){const n=this.selected();if(!n)return;this.updateFinding(n,{...this.findingOf(n),[field]:value});}
  hasSurface(n:number,surface:ToothSurface){return this.findingOf(n).surfaces.includes(surface);}
  toggleSurface(surface:ToothSurface){const n=this.selected();if(!n||!this.surfaceAllowed(n,surface))return;const current=this.findingOf(n);const surfaces=current.surfaces.includes(surface)?current.surfaces.filter(x=>x!==surface):[...current.surfaces,surface];this.updateFinding(n,{...current,surfaces});}
  surfaceAllowed(n:number,surface:ToothSurface){const anterior=n%10<=3;return surface!=='O'&&surface!=='I'||surface==='I'&&anterior||surface==='O'&&!anterior;}
  private updateFinding(n:number,finding:ToothFinding){this.values.update(v=>({...v,[String(n)]:finding}));}
  clearSelected(){const n=this.selected();if(!n)return;this.values.update(v=>{const next={...v};delete next[String(n)];return next;});}

  toothDescription(n:number){const quadrant=Math.floor(n/10),position=n%10;const primary=quadrant>=5;const side=[1,4,5,8].includes(quadrant)?'derecha':'izquierda';const arch=[1,2,5,6].includes(quadrant)?'superior':'inferior';const names=primary?['','incisivo central','incisivo lateral','canino','primer molar','segundo molar']:['','incisivo central','incisivo lateral','canino','primer premolar','segundo premolar','primer molar','segundo molar','tercer molar'];return `${names[position]||'pieza dental'} ${arch} ${side} · ${primary?'temporal':'permanente'}`;}
  shortCondition(value:ToothCondition){return({SANO:'',CARIES:'Caries','RESTAURACIÓN':'Rest.',AUSENTE:'Ausente',ENDODONCIA:'Endo.',CORONA:'Corona',IMPLANTE:'Implante',SELLANTE:'Sellante',FRACTURA:'Fractura'} as Record<ToothCondition,string>)[value];}
  conditionLabel(value:ToothCondition){return({SANO:'Sano / limpiar',CARIES:'Caries','RESTAURACIÓN':'Restauración',AUSENTE:'Ausente',ENDODONCIA:'Endodoncia',CORONA:'Corona',IMPLANTE:'Implante',SELLANTE:'Sellante',FRACTURA:'Fractura'} as Record<ToothCondition,string>)[value];}

  initials(name:string){return name?name.split(' ').slice(0,2).map(x=>x[0]).join('').toUpperCase():'CC';}
  patientDni(){return this.patients().find(p=>p.fullName===this.selectedPatient())?.dni||'—';}
  private lines(value:string){return value.split(/\r?\n/).map(x=>x.trim()).filter(Boolean);}
  private inferDentition(codes:string[]):Dentition{const permanent=codes.some(x=>Number(x)<50),primary=codes.some(x=>Number(x)>=50);return permanent&&primary?'MIXTA':primary?'TEMPORAL':'PERMANENTE';}
  private asCondition(value:any):ToothCondition{const normalized=String(value||'SANO').toUpperCase().normalize('NFD').replace(/[\u0300-\u036f]/g,'');const found=this.conditions.find(x=>x.normalize('NFD').replace(/[\u0300-\u036f]/g,'')===normalized);return found||'SANO';}
  private notify(text:string,type:'success'|'error'='success'){this.message.set(text);this.messageType.set(type);}
}
