import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { ApiService } from '../core/api.service';
import { AuthService } from '../core/auth.service';

interface DashboardData {
  role: string; patients: number; appointments: number; emergencies: number; laboratoryOrders: number;
  budgets: number; followUps: number; complaints: number; equipment: number; memberships: number;
  commissionTotal: number; webInquiries: number; inventoryItems:number; inventoryAlerts:number; encounters:number;
  averageWaitMinutes:number; followUpRate:number; satisfactionAverage:number; satisfactionAlerts:number;
  paymentTotal?:number; expenseTotal?:number; openCash?:number; message: string;
}
interface AppointmentLite { id?:number; patientName:string; doctorName:string; specialty:string; chair:string; startAt:string; endAt:string; status?:string; treatmentName?:string; reason?:string; cleaningBufferMinutes?:number; }
interface ChairLite { id:number; name:string; branch:string; active:boolean; operationalStatus?:string; unavailableUntil?:string|null; }
interface QuickAction { icon:string; title:string; text:string; route:string; }
interface DashboardMetric { icon:string; label:string; value:number; note:string; money?:boolean; }
interface DashboardIndicator { label:string; value:number; money?:boolean; }

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [DatePipe, DecimalPipe, RouterLink],
  template: `
    <section class="page-head">
      <div><span>{{ greeting() }}</span><h1>Panel de {{ roleName() }}</h1><p>{{ data()?.message || 'Cargando información operativa...' }}</p></div>
      <button class="primary-btn" (click)="openPrimaryAction()">＋ {{primaryAction().title}}</button>
    </section>
    @if (loading()) { <div class="loading-card">Cargando panel...</div> }
    @if (data(); as d) {
      <section class="metric-grid">
        @for(metric of metrics(d); track metric.label){
          <article><span class="metric-icon">{{metric.icon}}</span><div><small>{{metric.label}}</small><b>{{metric.money ? ('S/ '+(metric.value|number:'1.2-2')) : metric.value}}</b><em>{{metric.note}}</em></div></article>
        }
      </section>

      @if(auth.hasRole(['RECEPCION'])){
        <section class="reception-summary">
          <article class="panel doctor-summary">
            <div class="panel-title"><div><span>Atenciones de hoy</span><h2>Distribución por doctor</h2></div><b class="today-total">{{todayAppointments().length}} total</b></div>
            <div class="doctor-grid">
              @for(item of doctorBreakdown(); track item.name){
                <div><span class="doctor-avatar">{{initials(item.name)}}</span><div><b>{{item.name}}</b><small>{{item.count}} {{item.count===1?'atención':'atenciones'}} programadas</small></div></div>
              } @empty { <p class="empty-note">No hay atenciones programadas para hoy.</p> }
            </div>
          </article>
          <article class="panel chair-summary">
            <div class="panel-title"><div><span>Capacidad operativa</span><h2>Sillones disponibles hoy</h2></div><a routerLink="/app/mapa-sillones">Ver mapa</a></div>
            <div class="chair-list">
              @for(chair of availableChairsToday(); track chair.id){
                <div><span>🦷</span><div><b>{{chair.name}}</b><small>{{freeSlotCount(chair.name)}} bloques de 30 min libres · {{chair.branch}}</small></div><em>Disponible</em></div>
              } @empty { <p class="empty-note">No se detectan sillones con bloques disponibles hoy.</p> }
            </div>
          </article>
        </section>
      }

      <section class="dashboard-grid">
        @if(auth.hasRole(['ADMIN','DOCTOR','RECEPCION'])){
          <article class="panel schedule-panel">
            <div class="panel-title"><div><span>Actividad clínica</span><h2>Agenda operativa</h2></div><a routerLink="/app/citas">Ver agenda</a></div>
            <div class="timeline">
              @for(a of todayAppointments().slice(0,8); track a.id){
                <div><time>{{a.startAt|date:'HH:mm'}}</time><span class="line"></span><div><b>{{a.patientName}}</b><small>{{a.doctorName}} · {{a.chair}} · {{a.treatmentName||a.reason||a.specialty}}</small></div><em [class]="statusClass(a.status)">{{statusLabel(a.status)}}</em></div>
              } @empty { <div class="agenda-empty">No hay citas para hoy.</div> }
            </div>
          </article>
        } @else {
          <article class="panel schedule-panel"><div class="panel-title"><div><span>Actividad financiera</span><h2>Resumen de cobranza</h2></div><a routerLink="/app/comisiones">Ver comisiones</a></div><div class="indicator-list"><div><span>Presupuestos registrados</span><b>{{d.budgets}}</b></div><div><span>Membresías activas</span><b>{{d.memberships}}</b></div><div><span>Órdenes con costo de laboratorio</span><b>{{d.laboratoryOrders}}</b></div><div><span>Comisiones calculadas</span><b>S/ {{d.commissionTotal|number:'1.2-2'}}</b></div></div></article>
        }
        <article class="panel quick-panel"><div class="panel-title"><div><span>Accesos directos</span><h2>Tareas frecuentes</h2></div></div><div class="quick-grid">@for(action of secondaryActions(); track action.route){<button (click)="router.navigateByUrl(action.route)"><span>{{action.icon}}</span><b>{{action.title}}</b><small>{{action.text}}</small></button>}</div></article>
        <article class="panel indicator-panel"><div class="panel-title"><div><span>Indicadores</span><h2>Resumen del rol</h2></div></div><div class="indicator-list">@for(item of indicators(d);track item.label){<div><span>{{item.label}}</span><b>{{item.money ? ('S/ '+(item.value|number:'1.2-2')) : item.value}}</b></div>}</div></article>
        <article class="panel security-panel"><div class="shield">🔐</div><div><span>Seguridad y continuidad</span><h2>Acceso controlado</h2><p>El menú y las APIs aplican permisos del rol autenticado. Las operaciones no autorizadas se bloquean en frontend y backend.</p><div class="health"><i></i> Sesión y servicios activos</div></div></article>
      </section>
    }
  `,
  styles: [`
    .metric-grid{display:grid;grid-template-columns:repeat(4,1fr);gap:16px;margin-bottom:20px}.metric-grid article{background:#fff;border:1px solid #dde9ea;border-radius:17px;padding:20px;display:flex;gap:15px;align-items:center;box-shadow:0 6px 18px rgba(23,63,67,.04)}.metric-icon{width:52px;height:52px;flex:0 0 52px;border-radius:15px;background:#e1f6f4;display:grid;place-items:center;font-size:23px}.metric-grid small,.metric-grid b,.metric-grid em{display:block}.metric-grid small{font-size:12px;line-height:1.35;color:#607b7e}.metric-grid b{font-size:27px;line-height:1.15;color:#173f43;margin:4px 0}.metric-grid em{font-size:11px;color:#438a6d;font-style:normal}.reception-summary{display:grid;grid-template-columns:1fr 1fr;gap:20px;margin-bottom:20px}.today-total{font-size:14px;color:#3B78A6}.doctor-grid{display:grid;grid-template-columns:1fr 1fr;gap:11px}.doctor-grid>div{display:flex;align-items:center;gap:11px;padding:13px;border:1px solid #dfe9ea;border-radius:13px}.doctor-avatar{width:42px;height:42px;flex:0 0 42px;border-radius:11px;background:#e5f5f4;color:#3B78A6;font-weight:800;display:grid;place-items:center;font-size:11px}.doctor-grid b,.doctor-grid small{display:block}.doctor-grid b{font-size:13px}.doctor-grid small{font-size:11px;line-height:1.4;color:#71898c;margin-top:3px}.chair-list{display:grid;gap:8px}.chair-list>div{display:grid;grid-template-columns:36px 1fr auto;gap:10px;align-items:center;padding:12px;border-bottom:1px solid #edf2f2}.chair-list b,.chair-list small{display:block}.chair-list b{font-size:13px}.chair-list small{font-size:11px;line-height:1.4;color:#71898c;margin-top:3px}.chair-list em{font-style:normal;font-size:10px;font-weight:800;padding:6px 8px;background:#e8f7ee;color:#347556;border-radius:99px}.empty-note,.agenda-empty{font-size:13px;color:#71898c;padding:14px 0}.dashboard-grid{display:grid;grid-template-columns:1.25fr .75fr;gap:20px}.panel{background:#fff;border:1px solid #dde9ea;border-radius:18px;padding:24px;box-shadow:0 7px 20px rgba(23,63,67,.04)}.panel-title{display:flex;justify-content:space-between;align-items:flex-start;gap:12px;margin-bottom:20px}.panel-title span,.security-panel span{font-size:11px;text-transform:uppercase;letter-spacing:1.2px;color:#3B78A6;font-weight:800}.panel-title h2,.security-panel h2{font-size:21px;line-height:1.3;margin:5px 0}.panel-title a{border:1px solid #d4e3e5;background:#fff;border-radius:9px;padding:9px 11px;font-size:11px;font-weight:800;color:#48686b;white-space:nowrap}.timeline{display:grid;gap:5px}.timeline>div{display:grid;grid-template-columns:52px 18px 1fr auto;gap:11px;align-items:center;padding:13px 0;border-bottom:1px solid #edf2f2}.timeline time{font-size:14px;font-weight:800}.timeline .line{width:10px;height:10px;background:#19a4a7;border-radius:50%;box-shadow:0 0 0 4px #e5f6f5}.timeline b,.timeline small{display:block}.timeline b{font-size:14px}.timeline small{font-size:11px;line-height:1.45;color:#71898c;margin-top:3px}.timeline em{font-style:normal;font-size:10px;font-weight:800;padding:7px 9px;border-radius:99px}.confirmed{background:#e6f7ed;color:#36815b}.pending{background:#fff5db;color:#9a762b}.arrived{background:#e3f1ff;color:#346a9a}.progress{background:#e7f1fb;color:#2f628f}.done{background:#e8f7ee;color:#347556}.cancelled{background:#f5eeee;color:#9a4d4d}.neutral{background:#edf2f3;color:#60787b}.quick-grid{display:grid;grid-template-columns:1fr 1fr;gap:11px}.quick-grid button{min-height:114px;border:1px solid #dce8e9;background:#fbfdfd;border-radius:14px;padding:16px;text-align:left;cursor:pointer;transition:.18s}.quick-grid button:hover{border-color:#78b8bc;background:#f1f9f9;transform:translateY(-1px)}.quick-grid span{font-size:23px}.quick-grid b,.quick-grid small{display:block}.quick-grid b{font-size:14px;margin-top:9px}.quick-grid small{font-size:11px;line-height:1.45;color:#71898c;margin-top:3px}.indicator-list{display:grid;gap:3px}.indicator-list div{display:flex;justify-content:space-between;gap:16px;padding:14px 12px;border-bottom:1px solid #edf2f2;font-size:13px}.indicator-list span{color:#597579}.indicator-list b{color:#173f43}.security-panel{background:linear-gradient(135deg,#083f45,#075b62);color:#fff;display:flex;gap:20px;align-items:center}.security-panel p{font-size:13px;line-height:1.65;color:#c5dfe1}.shield{width:76px;height:76px;flex:0 0 76px;border-radius:22px;background:rgba(255,255,255,.09);display:grid;place-items:center;font-size:35px}.health{font-size:12px;color:#bfe7d2;margin-top:11px}.health i{display:inline-block;width:8px;height:8px;background:#38d58a;border-radius:50%;margin-right:6px}@media(max-width:1150px){.metric-grid{grid-template-columns:1fr 1fr}.dashboard-grid,.reception-summary{grid-template-columns:1fr}}@media(max-width:600px){.metric-grid{grid-template-columns:1fr}.quick-grid,.doctor-grid{grid-template-columns:1fr}.timeline>div{grid-template-columns:48px 14px 1fr}.timeline em{display:none}.panel{padding:19px}}
  `]
})
export class DashboardComponent implements OnInit {
  private readonly api = inject(ApiService);
  readonly auth = inject(AuthService);
  readonly router = inject(Router);
  readonly data = signal<DashboardData | null>(null);
  readonly appointments = signal<AppointmentLite[]>([]);
  readonly chairs = signal<ChairLite[]>([]);
  readonly loading = signal(true);
  readonly greeting = computed(() => new Date().getHours() < 12 ? 'Buenos días' : new Date().getHours() < 19 ? 'Buenas tardes' : 'Buenas noches');
  readonly roleName = computed(() => { switch(this.auth.user()?.role){case 'DOCTOR':return 'doctor';case 'RECEPCION':return 'recepción';case 'FINANZAS':return 'finanzas';default:return 'administración';} });

  readonly quickActions = computed<QuickAction[]>(() => {
    switch(this.auth.user()?.role){
      case 'DOCTOR': return [
        {icon:'🩺',title:'Historia clínica',text:'Revisar y registrar evolución.',route:'/app/historia-clinica'},
        {icon:'🦷',title:'Odontograma',text:'Actualizar piezas y diagnóstico.',route:'/app/odontograma'},
        {icon:'🧾',title:'Nuevo presupuesto',text:'Crear o versionar propuesta.',route:'/app/presupuestos'},
        {icon:'⚗',title:'Laboratorio',text:'Crear y seguir órdenes.',route:'/app/laboratorio'}
      ];
      case 'RECEPCION': return [
        {icon:'☑',title:'Recepción de hoy',text:'Pacientes, inasistencias y contactos.',route:'/app/recepcion-hoy'},
        {icon:'👤',title:'Registrar paciente',text:'Nueva ficha administrativa.',route:'/app/pacientes'},
        {icon:'📅',title:'Programar cita',text:'Calendario, sillón y horario.',route:'/app/citas'},
        {icon:'💳',title:'Caja y pagos',text:'Cobros, caja y gastos pendientes.',route:'/app/caja-pagos'},
        {icon:'🌐',title:'Solicitudes web',text:'Contactar y programar pacientes.',route:'/app/solicitudes-web'}
      ];
      case 'FINANZAS': return [
        {icon:'💵',title:'Caja y pagos',text:'Cobranza, aperturas y cierres.',route:'/app/caja-pagos'},
        {icon:'％',title:'Comisiones',text:'Calcular y aprobar liquidaciones.',route:'/app/comisiones'},
        {icon:'🎫',title:'Membresías',text:'Cobranza y saldos.',route:'/app/membresias'},
        {icon:'🧾',title:'Presupuestos',text:'Consultar propuestas aceptadas.',route:'/app/presupuestos'},
        {icon:'📊',title:'Reportes',text:'Exportar indicadores financieros.',route:'/app/reportes'}
      ];
      default: return [
        {icon:'👤',title:'Pacientes',text:'Supervisar registros.',route:'/app/pacientes'},
        {icon:'📅',title:'Agenda',text:'Ver operación diaria.',route:'/app/citas'},
        {icon:'🪪',title:'Empleados',text:'Contratos y turnos.',route:'/app/empleados'},
        {icon:'🖥',title:'Monitoreo',text:'Estado de la plataforma.',route:'/app/monitoreo'}
      ];
    }
  });

  readonly primaryAction = computed(() => this.quickActions()[0]!);
  readonly secondaryActions = computed(() => this.quickActions().slice(1));
  ngOnInit(): void {
    this.api.get<DashboardData>('/dashboard').subscribe({next:v=>{this.data.set(v);this.loading.set(false);},error:()=>this.loading.set(false)});
    if(this.auth.hasRole(['ADMIN','DOCTOR','RECEPCION'])){
      this.api.get<AppointmentLite[]>('/appointments').subscribe({next:v=>this.appointments.set(v),error:()=>this.appointments.set([])});
      this.api.get<ChairLite[]>('/scheduling/chairs').subscribe({next:v=>this.chairs.set(v),error:()=>this.chairs.set([])});
    }
  }
  openPrimaryAction(): void { void this.router.navigateByUrl(this.primaryAction().route); }

  todayAppointments(): AppointmentLite[]{
    const today=this.dateKey(new Date());
    const inactive=new Set(['CANCELADO','NO_ASISTIO','REPROGRAMADA']);
    return this.appointments().filter(a=>this.dateKey(new Date(a.startAt))===today && !inactive.has((a.status||'').toUpperCase())).sort((a,b)=>new Date(a.startAt).getTime()-new Date(b.startAt).getTime());
  }
  doctorBreakdown(): {name:string;count:number}[]{
    const map=new Map<string,number>();
    for(const a of this.todayAppointments()) map.set(a.doctorName,(map.get(a.doctorName)||0)+1);
    return [...map.entries()].map(([name,count])=>({name,count})).sort((a,b)=>b.count-a.count||a.name.localeCompare(b.name));
  }
  availableChairsToday(): ChairLite[]{
    const blocked=new Set(['MANTENIMIENTO','FUERA_DE_SERVICIO','BLOQUEADO']);
    return this.chairs().filter(c=>c.active&&!blocked.has((c.operationalStatus||'DISPONIBLE').toUpperCase())&&this.freeSlotCount(c.name)>0);
  }
  freeSlotCount(chairName:string): number{
    let free=0;
    for(let hour=8;hour<22;hour++) for(const minute of [0,30]) if(this.slotChairAvailable(chairName,hour,minute)&&!this.slotBusy(chairName,hour,minute)) free++;
    return free;
  }
  private slotChairAvailable(chairName:string,hour:number,minute:number):boolean{const c=this.chairs().find(x=>x.name===chairName);if(!c||!c.active)return false;const blocked=new Set(['MANTENIMIENTO','FUERA_DE_SERVICIO','BLOQUEADO']);if(blocked.has((c.operationalStatus||'DISPONIBLE').toUpperCase()))return false;if(!c.unavailableUntil)return true;const date=this.dateKey(new Date());return new Date(`${date}T${String(hour).padStart(2,'0')}:${String(minute).padStart(2,'0')}:00`)>=new Date(c.unavailableUntil);}
  private slotBusy(chairName:string,hour:number,minute:number):boolean{
    const date=this.dateKey(new Date());
    const start=new Date(`${date}T${String(hour).padStart(2,'0')}:${String(minute).padStart(2,'0')}:00`);
    const end=new Date(start.getTime()+30*60000);
    return this.todayAppointments().some(a=>{const aStart=new Date(a.startAt);const aEnd=new Date(new Date(a.endAt).getTime()+(Number(a.cleaningBufferMinutes)||0)*60000);return a.chair===chairName && start<aEnd && end>aStart;});
  }
  initials(name:string){return name.split(' ').filter(x=>!['Dr.','Dra.'].includes(x)).slice(0,2).map(x=>x[0]).join('').toUpperCase();}
  statusLabel(v?:string){return ({PENDIENTE:'Pendiente',CONFIRMADO:'Confirmada',LLEGO:'Llegó',EN_PROCESO:'En atención',COMPLETADO:'Atendida',CANCELADO:'Cancelada',NO_ASISTIO:'No asistió',OBSERVADO:'Observada'} as Record<string,string>)[v||'']||v||'Pendiente';}
  statusClass(v?:string){return ({CONFIRMADO:'confirmed',PENDIENTE:'pending',LLEGO:'arrived',EN_PROCESO:'progress',COMPLETADO:'done',CANCELADO:'cancelled',NO_ASISTIO:'cancelled'} as Record<string,string>)[v||'']||'neutral';}

  metrics(d: DashboardData): DashboardMetric[]{
    switch(this.auth.user()?.role){
      case 'DOCTOR': return [
        {icon:'✚',label:'Atenciones clínicas',value:d.encounters,note:'Registros centrales'},
        {icon:'🚨',label:'Emergencias',value:d.emergencies,note:'Atención prioritaria'},
        {icon:'🧾',label:'Presupuestos',value:d.budgets,note:'Propuestas clínicas'},
        {icon:'⚗',label:'Laboratorio',value:d.laboratoryOrders,note:'Órdenes en control'}
      ];
      case 'RECEPCION': return [
        {icon:'👥',label:'Pacientes totales',value:d.patients,note:'Registrados en la clínica'},
        {icon:'📅',label:'Atenciones programadas hoy',value:this.todayAppointments().length,note:'Agenda del día'},
        {icon:'🩺',label:'Doctores con agenda hoy',value:this.doctorBreakdown().length,note:'Distribución de carga'},
        {icon:'🦷',label:'Sillones disponibles hoy',value:this.availableChairsToday().length,note:'Con bloques libres'}
      ];
      case 'FINANZAS': return [
        {icon:'🧾',label:'Presupuestos',value:d.budgets,note:'Consulta económica'},
        {icon:'🎫',label:'Membresías',value:d.memberships,note:'Planes registrados'},
        {icon:'★',label:'Satisfacción',value:d.satisfactionAverage||0,note:'Promedio sobre 5'},
        {icon:'💵',label:'Pagos registrados',value:d.paymentTotal||0,note:'Cobranza acumulada',money:true}
      ];
      default: return [
        {icon:'👥',label:'Pacientes registrados',value:d.patients,note:'Base activa'},
        {icon:'✚',label:'Atenciones registradas',value:d.encounters,note:'Fuente central'},
        {icon:'🚨',label:'Emergencias',value:d.emergencies,note:'Atención prioritaria'},
        {icon:'⚗',label:'Órdenes de laboratorio',value:d.laboratoryOrders,note:'En seguimiento'}
      ];
    }
  }

  indicators(d:DashboardData): DashboardIndicator[]{
    switch(this.auth.user()?.role){
      case 'FINANZAS': return [
        {label:'Presupuestos registrados',value:d.budgets},{label:'Membresías activas',value:d.memberships},
        {label:'Cajas abiertas',value:d.openCash||0},{label:'Comisiones calculadas',value:d.commissionTotal,money:true},{label:'Alertas de satisfacción',value:d.satisfactionAlerts||0}
      ];
      case 'DOCTOR': return [
        {label:'Pacientes visibles',value:d.patients},{label:'Seguimientos pendientes',value:d.followUps},
        {label:'Presupuestos',value:d.budgets},{label:'Seguimientos completados %',value:d.followUpRate||0},{label:'Satisfacción promedio',value:d.satisfactionAverage||0}
      ];
      case 'RECEPCION': return [
        {label:'Pacientes registrados',value:d.patients},{label:'Atenciones de hoy',value:this.todayAppointments().length},
        {label:'Solicitudes web',value:d.webInquiries},{label:'Alertas de inventario',value:d.inventoryAlerts||0},{label:'Satisfacción promedio',value:d.satisfactionAverage||0}
      ];
      default: return [
        {label:'Presupuestos activos',value:d.budgets},{label:'Seguimientos pendientes',value:d.followUps},
        {label:'Solicitudes web',value:d.webInquiries},{label:'Espera promedio (min)',value:d.averageWaitMinutes||0},{label:'Alertas de inventario',value:d.inventoryAlerts||0},{label:'Alertas de satisfacción',value:d.satisfactionAlerts||0}
      ];
    }
  }
  private dateKey(d:Date){return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`;}
}
