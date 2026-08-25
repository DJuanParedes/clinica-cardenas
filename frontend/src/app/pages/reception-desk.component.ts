import { DatePipe } from '@angular/common';
import { Component, inject, OnInit, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { ApiService } from '../core/api.service';

interface ReceptionAppointment {
  id:number; patientId:number; patientName:string; doctorName:string; chair:string; specialty:string;
  treatmentName?:string; reason?:string; startAt:string; endAt:string; status:string;
  attendanceOutcome?:string|null; resolution?:string|null;
}

interface WebInquiry {
  id:number; fullName:string; phone:string; specialty:string; message?:string; createdAt:string;
  status:string; assignedTo?:string;
}

@Component({
  selector:'app-reception-desk',
  standalone:true,
  imports:[DatePipe,RouterLink],
  template:`
<section class="page-head reception-head">
  <div><span>Tu jornada</span><h1>Recepción de hoy</h1><p>Empieza por la cola del día. Aquí puedes confirmar llegadas, resolver inasistencias y contactar a nuevos pacientes.</p></div>
  <div class="head-actions"><a class="secondary-btn" routerLink="/app/pacientes">＋ Nuevo paciente</a><a class="primary-btn" routerLink="/app/citas">＋ Nueva cita</a></div>
</section>
@if(message()){<div class="notice" [class.error]="messageType()==='error'">{{message()}}</div>}

<section class="shift-guide">
  <div class="shift-title"><span>Guía rápida</span><b>Trabaja en este orden</b></div>
  <div class="guide-steps">
    <article><span>1</span><div><b>Busca al paciente</b><small>Confirma DNI y teléfono</small></div></article><i>→</i>
    <article><span>2</span><div><b>Agenda su cita</b><small>Elige doctor, sillón y hora</small></div></article><i>→</i>
    <article><span>3</span><div><b>Confirma la llegada</b><small>Marca “Llegó” o “No asistió”</small></div></article><i>→</i>
    <article><span>4</span><div><b>Finaliza en Caja</b><small>Cobra la proforma o el saldo</small></div></article>
  </div>
  <p>La evaluación y la información clínica corresponden al doctor.</p>
</section>

@if(loading()){<div class="loading-card">Preparando la jornada de recepción…</div>}@else{
<section class="reception-kpis">
  <article><span class="blue">📅</span><div><small>Citas de hoy</small><b>{{todayAppointments().length}}</b><em>{{waitingForArrival().length}} por recibir</em></div></article>
  <article><span class="green">✓</span><div><small>Pacientes presentes</small><b>{{arrivedPatients().length}}</b><em>Esperando o en atención</em></div></article>
  <article [class.alert-card]="pendingNoShows().length"><span class="orange">!</span><div><small>Inasistencias por resolver</small><b>{{pendingNoShows().length}}</b><em>Reprogramar o cerrar</em></div></article>
  <article [class.alert-card]="newInquiries().length"><span class="violet">🌐</span><div><small>Solicitudes nuevas</small><b>{{newInquiries().length}}</b><em>Contactar y coordinar</em></div></article>
</section>

@if(pendingNoShows().length){
  <section class="decision-alert"><span>⚠</span><div><b>Hay {{pendingNoShows().length}} inasistencia(s) sin resolver</b><p>No las dejes pendientes: desde Agenda puedes reprogramar conservando el historial o cerrar la cita sin reprogramar.</p></div><a routerLink="/app/citas">Resolver en Agenda</a></section>
}

<section class="desk-grid">
  <article class="panel-card queue-card">
    <div class="section-title"><div><span>Cola del día</span><h2>Pacientes que requieren una decisión</h2></div><a routerLink="/app/agenda-diaria">Agenda diaria</a></div>
    <div class="queue-list">
      @for(a of actionableAppointments();track a.id){
        <section class="queue-item" [class.late]="isLate(a)">
          <time><b>{{a.startAt|date:'HH:mm'}}</b><small>{{relativeTime(a)}}</small></time>
          <div class="patient"><b>{{a.patientName}}</b><small>{{a.doctorName}} · {{a.chair}}</small><em>{{a.treatmentName||a.reason||a.specialty||'Atención odontológica'}}</em></div>
          <span class="state" [class.arrived]="a.status==='LLEGO'">{{statusLabel(a.status)}}</span>
          <div class="item-actions">
            @if(isAwaiting(a)){<button class="arrive" (click)="updateAppointment(a,'LLEGO')">✓ Llegó</button><button class="no-show" (click)="updateAppointment(a,'NO_ASISTIO')">No asistió</button>}
            @else if(a.status==='LLEGO'){<span class="doctor-step">Listo para el doctor</span>}
            @else if(a.status==='NO_ASISTIO'){<button class="open-agenda" (click)="openAppointment(a)">Resolver</button>}
          </div>
        </section>
      }@empty{<div class="empty"><span>✓</span><b>No hay decisiones pendientes</b><p>La cola de recepción está al día.</p></div>}
    </div>
  </article>

  <aside class="panel-card inquiry-card">
    <div class="section-title"><div><span>Portal público</span><h2>Solicitudes por contactar</h2></div><a routerLink="/app/solicitudes-web">Ver todas</a></div>
    <div class="inquiry-list">
      @for(i of openInquiries().slice(0,6);track i.id){
        <section><div class="inquiry-top"><span>{{initials(i.fullName)}}</span><div><b>{{i.fullName}}</b><small>{{i.specialty}} · {{i.createdAt|date:'dd/MM HH:mm'}}</small></div></div><p>{{i.message||'Sin mensaje adicional.'}}</p><div class="inquiry-actions"><a [href]="callHref(i.phone)">☎ {{i.phone}}</a>@if(i.status==='NUEVA'){<button (click)="markContacted(i)">Marcar contactada</button>}<button (click)="scheduleInquiry(i)">Programar</button></div></section>
      }@empty{<div class="empty compact"><span>✓</span><b>Sin solicitudes pendientes</b><p>No hay contactos web por gestionar.</p></div>}
    </div>
  </aside>
</section>

<section class="quick-tools panel-card"><div><span>Otras tareas</span><h2>Accesos directos</h2></div><nav><a routerLink="/app/pacientes"><span>👤</span><b>Buscar paciente</b><small>Ficha y datos de contacto</small></a><a routerLink="/app/citas"><span>📅</span><b>Abrir calendario</b><small>Programar o reprogramar</small></a><a routerLink="/app/mapa-sillones"><span>🦷</span><b>Ver sillones</b><small>Disponibilidad actual</small></a><a routerLink="/app/caja-pagos"><span>💳</span><b>Ir a Caja</b><small>Cobrar un saldo</small></a></nav></section>
}
  `,
  styles:[`
.reception-head{align-items:center}.head-actions{display:flex;gap:10px;flex:0 0 auto}.head-actions a{text-decoration:none}.notice{padding:13px 16px;border-radius:11px;background:#e7f7ee;color:#2f6f51;border:1px solid #cbead8;margin-bottom:17px;font-size:13px;font-weight:700}.notice.error{background:#fff0f0;color:#a94444;border-color:#f1cccc}.shift-guide{background:linear-gradient(120deg,#244f69,#356f83);border-radius:20px;padding:20px 22px;margin-bottom:20px;color:#fff;box-shadow:0 12px 28px rgba(36,79,105,.16)}.shift-title{display:flex;gap:11px;align-items:center;margin-bottom:15px}.shift-title span{font-size:11px;text-transform:uppercase;letter-spacing:1.2px;background:rgba(255,255,255,.12);padding:6px 9px;border-radius:99px;color:#dcecf2;font-weight:800}.shift-title b{font-size:17px}.guide-steps{display:grid;grid-template-columns:1fr auto 1fr auto 1fr auto 1fr;gap:13px;align-items:center}.guide-steps article{display:flex;align-items:center;gap:11px}.guide-steps article>span{width:40px;height:40px;flex:0 0 40px;border-radius:12px;background:#fff;color:#27566b;display:grid;place-items:center;font-weight:900;font-size:15px}.guide-steps b,.guide-steps small{display:block}.guide-steps b{font-size:13px;line-height:1.3}.guide-steps small{font-size:11px;line-height:1.4;color:#d3e5ec;margin-top:3px}.guide-steps i{font-style:normal;color:#9dc9d8;font-size:18px}.shift-guide>p{margin:14px 0 0;padding-top:12px;border-top:1px solid rgba(255,255,255,.12);font-size:11px;color:#d4e8ee}.reception-kpis{display:grid;grid-template-columns:repeat(4,1fr);gap:15px;margin-bottom:20px}.reception-kpis article{background:#fff;border:1px solid #dde9ea;border-radius:17px;padding:18px;display:flex;gap:14px;align-items:center;box-shadow:0 6px 18px rgba(23,63,67,.04)}.reception-kpis article.alert-card{border-color:#e9d2a8;background:#fffdf8}.reception-kpis article>span{width:50px;height:50px;flex:0 0 50px;border-radius:14px;display:grid;place-items:center;font-size:18px;font-weight:900}.blue{background:#e7f2fb;color:#376f9c}.green{background:#e6f7ee;color:#347b59}.orange{background:#fff1dd;color:#a96b1d}.violet{background:#f0eafa;color:#7453a0}.reception-kpis small,.reception-kpis b,.reception-kpis em{display:block}.reception-kpis small{font-size:12px;line-height:1.3;color:#607b7e}.reception-kpis b{font-size:28px;line-height:1.1;margin:4px 0;color:#173f43}.reception-kpis em{font-style:normal;font-size:11px;color:#628083}.decision-alert{display:grid;grid-template-columns:auto 1fr auto;align-items:center;gap:15px;padding:16px 18px;border:1px solid #e9cf9d;background:#fff9eb;border-radius:15px;margin-bottom:20px}.decision-alert>span{width:44px;height:44px;border-radius:12px;background:#f4dca9;color:#8c6018;display:grid;place-items:center;font-size:18px;font-weight:900}.decision-alert b{font-size:14px}.decision-alert p{font-size:12px;line-height:1.5;color:#746548;margin:4px 0 0}.decision-alert a{background:#95691e;color:#fff;padding:11px 14px;border-radius:10px;font-size:12px;font-weight:800}.desk-grid{display:grid;grid-template-columns:minmax(0,1.22fr) minmax(360px,.78fr);gap:20px;align-items:start}.section-title{display:flex;justify-content:space-between;gap:12px;align-items:flex-start;margin-bottom:18px}.section-title span,.quick-tools>div>span{font-size:11px;color:#3B78A6;text-transform:uppercase;font-weight:900;letter-spacing:.9px}.section-title h2,.quick-tools h2{font-size:21px;line-height:1.3;margin:5px 0}.section-title>a{border:1px solid #d4e3e5;border-radius:9px;padding:9px 11px;font-size:11px;font-weight:800;color:#45696d;white-space:nowrap}.queue-list{display:grid;gap:10px}.queue-item{display:grid;grid-template-columns:72px minmax(190px,1fr) auto auto;gap:13px;align-items:center;border:1px solid #dfe9ea;border-radius:14px;padding:15px;background:#fff}.queue-item.late{border-left:5px solid #d7794e}.queue-item time b,.queue-item time small,.patient b,.patient small,.patient em{display:block}.queue-item time b{font-size:19px;color:#234e61}.queue-item time small{font-size:10px;color:#80634e;margin-top:3px}.patient b{font-size:14px}.patient small{font-size:11px;color:#607b7e;margin-top:3px}.patient em{font-size:11px;color:#3B78A6;font-style:normal;margin-top:4px}.state{padding:7px 9px;border-radius:99px;background:#fff3dc;color:#8b6624;font-size:10px;font-weight:900}.state.arrived{background:#e5f3ff;color:#326b9d}.item-actions{display:flex;gap:7px;align-items:center}.item-actions button,.inquiry-actions button{border:0;border-radius:9px;padding:10px 11px;font-size:11px;font-weight:800;cursor:pointer;white-space:nowrap}.arrive{background:#37846a;color:#fff}.no-show{background:#fff0f0;color:#a34848}.open-agenda{background:#3B78A6;color:#fff}.doctor-step{font-size:11px;color:#2f6e51;background:#e7f7ee;border-radius:9px;padding:10px}.inquiry-list{display:grid;gap:11px}.inquiry-list>section{border:1px solid #dfe9ea;border-radius:14px;padding:15px}.inquiry-top{display:flex;align-items:center;gap:11px}.inquiry-top>span{width:42px;height:42px;flex:0 0 42px;border-radius:12px;background:#e6f2fa;color:#346e9d;display:grid;place-items:center;font-size:11px;font-weight:900}.inquiry-top b,.inquiry-top small{display:block}.inquiry-top b{font-size:13px}.inquiry-top small{font-size:10px;color:#71898c;margin-top:2px}.inquiry-list p{font-size:11px;color:#557377;line-height:1.55;margin:11px 0}.inquiry-actions{display:flex;flex-wrap:wrap;gap:7px}.inquiry-actions a,.inquiry-actions button{display:inline-flex;align-items:center;min-height:40px;background:#eef6f7;color:#35646a;padding:9px 10px;border-radius:8px;font-size:10px;font-weight:800}.inquiry-actions button:last-child{background:#3B78A6;color:#fff}.empty{text-align:center;padding:30px;color:#607b7e}.empty>span{width:48px;height:48px;border-radius:50%;background:#e7f7ee;color:#347858;display:grid;place-items:center;margin:auto;font-size:19px;font-weight:900}.empty b{display:block;font-size:14px;margin-top:10px}.empty p{font-size:11px}.empty.compact{padding:20px}.quick-tools{margin-top:20px;display:grid;grid-template-columns:190px 1fr;gap:20px;align-items:center}.quick-tools nav{display:grid;grid-template-columns:repeat(4,1fr);gap:11px;padding:0}.quick-tools nav a{display:grid;grid-template-columns:42px 1fr;column-gap:10px;align-items:center;border:1px solid #dce8e9;border-radius:13px;padding:13px;color:#31585b}.quick-tools nav a>span{grid-row:1/3;width:42px;height:42px;border-radius:11px;background:#eff7f7;display:grid;place-items:center;font-size:19px}.quick-tools nav b,.quick-tools nav small{display:block}.quick-tools nav b{font-size:12px}.quick-tools nav small{font-size:10px;color:#71898c;margin-top:2px}@media(max-width:1250px){.guide-steps{grid-template-columns:1fr 1fr}.guide-steps>i{display:none}.reception-kpis{grid-template-columns:1fr 1fr}.desk-grid{grid-template-columns:1fr}.quick-tools{grid-template-columns:1fr}}@media(max-width:760px){.reception-head{align-items:flex-start}.head-actions{width:100%;flex-direction:column}.head-actions a{text-align:center}.guide-steps,.reception-kpis,.quick-tools nav{grid-template-columns:1fr}.queue-item{grid-template-columns:62px 1fr}.queue-item>.state{justify-self:start}.item-actions{grid-column:1/-1;flex-wrap:wrap}.item-actions button{flex:1}.decision-alert{grid-template-columns:auto 1fr}.decision-alert a{grid-column:1/-1;text-align:center}.section-title{align-items:flex-start}.section-title h2{font-size:18px}}
  `]
})
export class ReceptionDeskComponent implements OnInit{
  private readonly api=inject(ApiService);
  private readonly router=inject(Router);
  readonly appointments=signal<ReceptionAppointment[]>([]);
  readonly inquiries=signal<WebInquiry[]>([]);
  readonly loading=signal(true);
  readonly message=signal('');
  readonly messageType=signal<'success'|'error'>('success');

  ngOnInit(){this.load();}
  load(){
    this.loading.set(true);let pending=2;
    const done=()=>{pending--;if(pending===0)this.loading.set(false);};
    this.api.get<ReceptionAppointment[]>('/appointments').subscribe({next:v=>{this.appointments.set(v);done();},error:e=>{this.notify(e?.error?.message||'No se pudo cargar la agenda.','error');done();}});
    this.api.get<WebInquiry[]>('/inquiries').subscribe({next:v=>{this.inquiries.set(v);done();},error:e=>{this.notify(e?.error?.message||'No se pudieron cargar las solicitudes web.','error');done();}});
  }
  todayAppointments(){const today=this.dateKey(new Date());return this.appointments().filter(a=>this.dateKey(new Date(a.startAt))===today).sort((a,b)=>new Date(a.startAt).getTime()-new Date(b.startAt).getTime());}
  waitingForArrival(){return this.todayAppointments().filter(a=>this.isAwaiting(a));}
  arrivedPatients(){return this.todayAppointments().filter(a=>['LLEGO','EN_PROCESO'].includes(a.status));}
  pendingNoShows(){return this.appointments().filter(a=>a.status==='NO_ASISTIO'&&a.resolution==='PENDIENTE_DECISION').sort((a,b)=>new Date(b.startAt).getTime()-new Date(a.startAt).getTime());}
  newInquiries(){return this.inquiries().filter(i=>i.status==='NUEVA');}
  openInquiries(){return this.inquiries().filter(i=>['NUEVA','CONTACTADA'].includes(i.status)).sort((a,b)=>new Date(b.createdAt).getTime()-new Date(a.createdAt).getTime());}
  actionableAppointments(){const ids=new Set<number>();const rows=[...this.waitingForArrival(),...this.todayAppointments().filter(a=>a.status==='LLEGO'),...this.pendingNoShows()];return rows.filter(a=>!ids.has(a.id)&&!!ids.add(a.id)).sort((a,b)=>{const priority=(x:ReceptionAppointment)=>x.status==='NO_ASISTIO'?0:this.isAwaiting(x)?1:2;return priority(a)-priority(b)||new Date(a.startAt).getTime()-new Date(b.startAt).getTime();});}
  isAwaiting(a:ReceptionAppointment){return ['PENDIENTE','CONFIRMADO','OBSERVADO'].includes(a.status||'PENDIENTE');}
  isLate(a:ReceptionAppointment){return this.isAwaiting(a)&&new Date(a.startAt).getTime()<Date.now()-15*60000;}
  updateAppointment(a:ReceptionAppointment,status:'LLEGO'|'NO_ASISTIO'){this.api.patch<ReceptionAppointment>(`/appointments/${a.id}/status?value=${status}`).subscribe({next:v=>{Object.assign(a,v);this.notify(status==='LLEGO'?`${a.patientName} quedó registrado como presente.`:`Inasistencia registrada para ${a.patientName}. Falta decidir si se reprograma o se cierra.`);this.load();},error:e=>this.notify(e?.error?.message||'No se pudo actualizar la cita.','error')});}
  markContacted(i:WebInquiry){this.api.patch<WebInquiry>(`/inquiries/${i.id}/status?value=CONTACTADA`).subscribe({next:v=>{Object.assign(i,v);this.notify(`Contacto registrado para ${i.fullName}.`);},error:e=>this.notify(e?.error?.message||'No se pudo actualizar la solicitud.','error')});}
  scheduleInquiry(i:WebInquiry){if(i.status==='NUEVA')this.markContacted(i);void this.router.navigate(['/app/citas'],{queryParams:{create:1,patientName:i.fullName,phone:i.phone,specialty:i.specialty,inquiryId:i.id}});}
  openAppointment(a:ReceptionAppointment){void this.router.navigate(['/app/citas'],{queryParams:{appointment:a.id}});}
  relativeTime(a:ReceptionAppointment){const minutes=Math.round((new Date(a.startAt).getTime()-Date.now())/60000);if(a.status==='NO_ASISTIO')return 'Pendiente de decisión';if(minutes>60)return `En ${Math.floor(minutes/60)} h ${minutes%60} min`;if(minutes>0)return `En ${minutes} min`;if(minutes>=-15)return 'Hora de recibir';return `Hace ${Math.abs(minutes)} min`;}
  statusLabel(v:string){return({PENDIENTE:'Por recibir',CONFIRMADO:'Por recibir',OBSERVADO:'Por revisar',LLEGO:'Presente',NO_ASISTIO:'No asistió'} as Record<string,string>)[v]||v;}
  initials(name:string){return name.split(' ').filter(Boolean).slice(0,2).map(x=>x[0]).join('').toUpperCase();}
  callHref(phone:string){return `tel:+51${String(phone||'').replace(/\D/g,'')}`;}
  private dateKey(d:Date){return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`;}
  private notify(text:string,type:'success'|'error'='success'){this.message.set(text);this.messageType.set(type);}
}
