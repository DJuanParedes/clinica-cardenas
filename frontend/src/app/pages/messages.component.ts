import { Component, ElementRef, OnDestroy, OnInit, ViewChild, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Api, AssistantSettings, AssistantTest, ConversationDetail, ConversationMessage, ConversationThread, FaqItem } from '../core/api.service';
import { ToastService } from '../core/toast.service';
import { AuthService } from '../core/auth.service';
import { apiError, dateTime } from '../core/format';
import { Patient } from '../core/models';
import { IconComponent } from '../shared/icon.component';
import { PatientPickerComponent } from '../shared/patient-picker.component';

/** Respuestas rápidas que el personal envía con un clic (se pueden editar antes de enviar). */
const QUICK = [
  'Hola, gracias por escribirnos 😊 ¿En qué te podemos ayudar?',
  'Tu cita está confirmada ✅ ¡Te esperamos!',
  'Te llamaremos en unos minutos para coordinar.',
  'Puedes reservar o reprogramar tu cita desde la app de la clínica (Mis citas).',
  'Para darte el precio exacto necesitamos una evaluación. ¿Te agendamos una?',
  'Recuerda llegar 10 minutos antes y traer tu DNI.',
];

const MODE_LABEL: Record<string, string> = { AUTOMATICO: 'Automático', FUERA_HORARIO: 'Fuera de horario', APAGADO: 'Apagado' };
const MODE_HELP: Record<string, string> = {
  AUTOMATICO: 'El asistente responde al instante, las 24 horas. El personal puede intervenir en cualquier conversación.',
  FUERA_HORARIO: 'En horario de atención responde el personal; fuera de horario responde el asistente.',
  APAGADO: 'Solo responde el personal. Los mensajes nuevos quedan marcados para atender.',
};
const INTENT_LABEL: Record<string, string> = {
  SALUDO: 'saludo', GRACIAS: 'agradecimiento', DESPEDIDA: 'despedida', MI_CITA: 'consulta su cita', CONFIRMAR: 'confirma',
  REPROGRAMAR: 'quiere reprogramar', CANCELAR: 'quiere cancelar', DISPONIBILIDAD: 'busca horario', PRECIO: 'precios', PAGO: 'pagos',
  HORARIO: 'horario', UBICACION: 'ubicación', URGENCIA: 'urgencia', HUMANO: 'pide hablar con alguien', OTRO: 'otra consulta',
  RECORDATORIO: 'recordatorio', CAMBIO_CITA: 'cambio de cita', CANCELACION: 'cancelación', CONFIRMADA: 'confirmó con 1',
};

/** Mensajes de WhatsApp: conversación en los dos sentidos con cualquier paciente, con asistente virtual (RAG + IA). */
@Component({
  selector: 'app-messages',
  standalone: true,
  imports: [FormsModule, RouterLink, IconComponent, PatientPickerComponent],
  template: `
    <div class="page">
      <div class="page-head">
        <div>
          <h1>Mensajes de WhatsApp</h1>
          <p class="sub">Conversa con cualquier paciente. El asistente virtual responde al instante con los datos de la clínica y te avisa cuando necesita ayuda.</p>
        </div>
        <div class="actions">
          <button class="btn" type="button" [class.primary]="panel === 'test'" (click)="toggle('test')"><app-icon name="send" [size]="16" /> Probar asistente</button>
          <button class="btn" type="button" [class.primary]="panel === 'faq'" (click)="toggle('faq')"><app-icon name="clipboard" [size]="16" /> Base de conocimiento</button>
        </div>
      </div>

      @if (settings) {
        <section class="panel bot">
          <div class="bot-l">
            <div class="bot-t"><span class="dot" [class.on]="settings.mode !== 'APAGADO'"></span> Asistente virtual</div>
            <div class="modes" role="radiogroup" aria-label="Modo del asistente">
              @for (m of modes; track m) {
                <button type="button" class="mode" role="radio" [attr.aria-checked]="settings.mode === m" [class.on]="settings.mode === m"
                        [disabled]="!canConfigure" (click)="setMode(m)">{{ modeLabel(m) }}</button>
              }
            </div>
            <div class="muted small">{{ modeHelp(settings.mode) }}</div>
          </div>
          <div class="bot-r small">
            <div><span class="muted">Modelo:</span> <b>{{ settings.model.externo ? settings.model.proveedor + ' · ' + settings.model.modelo : 'Local (intenciones + búsqueda)' }}</b></div>
            <div><span class="muted">Base de conocimiento:</span> {{ settings.knowledgeDocs }} documentos</div>
            <div><span class="muted">Ahora:</span> {{ settings.openNow ? 'en horario de atención' : 'fuera de horario' }}
              · <span class="muted">pausa al intervenir:</span>
              <select class="mini" [ngModel]="settings.pauseMinutes" (ngModelChange)="setPause($event)" [disabled]="!canConfigure" aria-label="Pausa del asistente">
                @for (p of pauses; track p) { <option [ngValue]="p">{{ p < 60 ? p + ' min' : (p / 60) + ' h' }}</option> }
              </select></div>
            @if (settings.model.ultimoError) { <div class="err">Último error del modelo: {{ settings.model.ultimoError }} (se usó la respuesta local)</div> }
          </div>
        </section>
      }

      @if (panel === 'test') {
        <section class="panel tool">
          <h3>Probar el asistente</h3>
          <p class="muted small">Escribe como si fueras un paciente. No se envía nada ni se ejecutan acciones.</p>
          <div class="row">
            <input class="input" [(ngModel)]="testText" placeholder="Ej.: ¿cuánto cuesta una limpieza? · ¿atienden los sábados? · quiero cambiar mi cita" (keydown.enter)="runTest()" />
            <div class="pp"><app-patient-picker [value]="testPatient" (valueChange)="testPatient = $event" placeholder="Paciente (opcional)" /></div>
            <button class="btn primary" type="button" (click)="runTest()" [disabled]="testing">{{ testing ? 'Pensando…' : 'Probar' }}</button>
          </div>
          @if (testResult) {
            <div class="answer">
              <div class="b out ia"><div class="who">🤖 Asistente</div><div [innerHTML]="wa(testResult.text)"></div></div>
              <div class="muted small">Intención: <b>{{ intent(testResult.intent) }}</b> · modelo {{ testResult.engine }} · {{ testResult.ms }} ms
                @if (testResult.needsHuman) { · <span class="warn">avisaría al personal</span> }
                @if (testResult.sources.length) { <br>Fuentes: {{ testResult.sources.join(' · ') }} }</div>
            </div>
          }
        </section>
      }

      @if (panel === 'faq') {
        <section class="panel tool">
          <div class="panel-head"><h3>Base de conocimiento</h3><span class="muted small">Además de estas preguntas, el asistente usa los horarios, precios del catálogo, dirección y medios de pago reales.</span></div>
          <div class="faq-new">
            <input class="input" [(ngModel)]="faqForm.question" placeholder="Pregunta (ej.: ¿Atienden con seguro?)" maxlength="300" />
            <textarea class="input" rows="2" [(ngModel)]="faqForm.answer" placeholder="Respuesta que dará el asistente" maxlength="1500"></textarea>
            <div class="row-end">
              @if (faqForm.id) { <button class="btn ghost sm" type="button" (click)="resetFaq()">Cancelar</button> }
              <button class="btn primary sm" type="button" (click)="saveFaq()" [disabled]="!canConfigure">{{ faqForm.id ? 'Guardar cambios' : 'Agregar pregunta' }}</button>
            </div>
          </div>
          <div class="faq-list">
            @for (f of faqs; track f.id) {
              <div class="faq" [class.off]="!f.active">
                <div class="q">{{ f.question }}</div>
                <div class="a">{{ f.answer }}</div>
                <div class="fa">
                  <label class="small"><input type="checkbox" [checked]="f.active" (change)="toggleFaq(f)" [disabled]="!canConfigure" /> Activa</label>
                  <button class="btn ghost sm" type="button" (click)="editFaq(f)" [disabled]="!canConfigure"><app-icon name="edit" [size]="14" /> Editar</button>
                  @if (auth.can('ELIMINAR_REGISTROS')) { <button class="btn ghost sm danger" type="button" (click)="removeFaq(f)"><app-icon name="trash" [size]="14" /> Eliminar</button> }
                </div>
              </div>
            } @empty { <p class="muted small">Sin preguntas frecuentes.</p> }
          </div>
        </section>
      }

      <section class="chat panel">
        <aside class="list">
          <div class="list-h">
            <input class="input" [(ngModel)]="q" (ngModelChange)="loadThreads()" placeholder="Buscar paciente o número" aria-label="Buscar conversación" />
            <div class="filters">
              <button type="button" class="chip" [class.on]="filter === 'all'" (click)="filter = 'all'">Todas</button>
              <button type="button" class="chip" [class.on]="filter === 'attention'" (click)="filter = 'attention'">Por atender @if (attention) { <b>{{ attention }}</b> }</button>
              <button type="button" class="chip" [class.on]="filter === 'unread'" (click)="filter = 'unread'">Sin leer @if (unreadTotal) { <b>{{ unreadTotal }}</b> }</button>
            </div>
            <div class="new"><app-patient-picker [value]="newPatient" (valueChange)="startWith($event)" placeholder="Nueva conversación: buscar paciente" /></div>
          </div>
          <div class="items">
            @for (t of visibleThreads; track t.phone) {
              <button type="button" class="item" [class.sel]="t.phone === phone" (click)="open(t.phone)">
                <span class="av">{{ initials(t.name) }}</span>
                <span class="c">
                  <span class="n"><b>{{ t.name || fmtPhone(t.phone) }}</b><small>{{ ago(t.lastMessageAt) }}</small></span>
                  <span class="p">
                    @if (t.needsAttention) { <span class="att" title="Necesita atención del personal">!</span> }
                    <span class="txt">{{ t.lastPreview }}</span>
                    @if (t.unread) { <span class="badge">{{ t.unread }}</span> }
                  </span>
                </span>
              </button>
            } @empty { <div class="empty small"><p>{{ q ? 'Sin resultados.' : 'Aún no hay conversaciones. Cuando un paciente escriba por WhatsApp aparecerá aquí.' }}</p></div> }
          </div>
        </aside>

        <div class="conv">
          @if (detail) {
            <header class="conv-h">
              <span class="av">{{ initials(detail.thread.name) }}</span>
              <div class="who-h">
                <b>{{ detail.thread.name || 'Número no registrado' }}</b>
                <div class="muted small">{{ fmtPhone(detail.thread.phone) }}
                  @if (detail.thread.patientId) { · <a [routerLink]="['/pacientes', detail.thread.patientId]">Ver ficha</a> }</div>
              </div>
              <label class="switch small" title="Que el asistente responda en esta conversación">
                <input type="checkbox" [checked]="detail.thread.aiEnabled" (change)="setAi($any($event.target).checked)" /> Asistente en este chat
              </label>
              @if (detail.thread.needsAttention) {
                <button class="btn sm" type="button" (click)="attended()"><app-icon name="check" [size]="14" /> Marcar atendido</button>
              }
            </header>
            @if (detail.thread.silence) { <div class="note">🔕 {{ detail.thread.silence }}</div> }
            @if (detail.upcoming?.length) {
              <div class="up small">
                @for (a of detail.upcoming ?? []; track a.id) {
                  <span class="tag" [class.green]="a.status === 'CONFIRMADA'">📅 {{ dt(a.startAt) }} · {{ a.dentist }} · {{ a.status === 'CONFIRMADA' ? 'confirmada' : 'por confirmar' }}</span>
                }
              </div>
            }
            <div class="msgs" #msgs>
              @for (m of detail.messages; track m.id) {
                <div class="row-m" [class.right]="m.direction === 'OUT'">
                  <div class="b" [class.out]="m.direction === 'OUT'" [class.ia]="m.source === 'IA'" [class.staff]="m.source === 'PERSONAL'" [class.sys]="m.source === 'SISTEMA'"
                       [class.fail]="m.status === 'FALLIDO'">
                    <div class="who">{{ label(m) }}</div>
                    <div [innerHTML]="wa(m.body)"></div>
                    <div class="meta">{{ dt(m.at) }}@if (m.intent && m.source !== 'PERSONAL') { · {{ intent(m.intent) }} }@if (m.status === 'FALLIDO') { · ⚠ no enviado }</div>
                  </div>
                </div>
              } @empty { <div class="empty small"><p>Sin mensajes. Escribe el primero.</p></div> }
            </div>
            <div class="compose">
              <div class="quick">
                @for (t of quick; track t) { <button type="button" class="chip" (click)="useQuick(t)">{{ t.length > 38 ? t.slice(0, 36) + '…' : t }}</button> }
                @if (faqs.length) {
                  <select class="mini" (change)="useFaq($any($event.target).value); $any($event.target).value = ''" aria-label="Insertar respuesta de la base de conocimiento">
                    <option value="">Respuesta guardada…</option>
                    @for (f of faqs; track f.id) { <option [value]="f.id">{{ f.question }}</option> }
                  </select>
                }
              </div>
              <div class="send-row">
                <textarea class="input" rows="2" [(ngModel)]="draft" placeholder="Escribe un mensaje para el paciente (Enter para enviar, Shift+Enter nueva línea)"
                          (keydown.enter)="onEnter($event)" maxlength="1500"></textarea>
                <button class="btn primary" type="button" (click)="send()" [disabled]="sending || !draft.trim()"><app-icon name="send" [size]="16" /> {{ sending ? 'Enviando…' : 'Enviar' }}</button>
              </div>
              <div class="muted small">Al escribir tú, el asistente se pausa en este chat por {{ settings?.pauseMinutes ?? 120 }} min para no interrumpir.</div>
            </div>
          } @else {
            <div class="empty pick"><app-icon name="message" [size]="40" /><p>Elige una conversación o busca un paciente para escribirle.</p></div>
          }
        </div>
      </section>
    </div>
  `,
  styles: [`
    .bot { display: flex; gap: 18px; justify-content: space-between; align-items: flex-start; flex-wrap: wrap; padding: 14px 16px; margin-bottom: 14px; }
    .bot-t { font-weight: 800; display: flex; align-items: center; gap: 8px; margin-bottom: 8px; }
    .dot { width: 10px; height: 10px; border-radius: 50%; background: var(--muted); }
    .dot.on { background: #25d366; box-shadow: 0 0 0 3px rgba(37, 211, 102, .2); }
    .modes { display: flex; gap: 6px; margin-bottom: 6px; flex-wrap: wrap; }
    .mode { border: 1px solid var(--line); background: var(--surface); border-radius: 18px; padding: 6px 14px; font: inherit; font-size: 13px; cursor: pointer; color: var(--ink-2); }
    .mode.on { background: var(--teal); border-color: var(--teal); color: #fff; font-weight: 700; }
    .bot-r { display: flex; flex-direction: column; gap: 3px; min-width: 260px; }
    .mini { height: 28px; font: inherit; font-size: 12.5px; border: 1px solid var(--line); border-radius: 8px; background: var(--surface); padding: 0 6px; }
    .err { color: var(--gum); }
    .warn { color: var(--orange); font-weight: 700; }
    .tool { padding: 14px 16px; margin-bottom: 14px; }
    .tool h3 { margin: 0 0 4px; }
    .row { display: flex; gap: 8px; align-items: flex-start; flex-wrap: wrap; margin-top: 8px; }
    .row > .input { flex: 2; min-width: 240px; }
    .pp { flex: 1; min-width: 220px; }
    .answer { margin-top: 12px; display: flex; flex-direction: column; gap: 6px; max-width: 640px; }
    .faq-new { display: flex; flex-direction: column; gap: 8px; margin: 10px 0 14px; }
    .row-end { display: flex; justify-content: flex-end; gap: 8px; }
    .faq-list { display: grid; grid-template-columns: repeat(auto-fill, minmax(300px, 1fr)); gap: 10px; }
    .faq { border: 1px solid var(--line); border-radius: 12px; padding: 10px 12px; display: flex; flex-direction: column; gap: 4px; }
    .faq.off { opacity: .55; }
    .faq .q { font-weight: 700; }
    .faq .a { font-size: 13.5px; color: var(--ink-2); white-space: pre-wrap; }
    .fa { display: flex; gap: 6px; align-items: center; margin-top: 4px; flex-wrap: wrap; }
    .danger { color: var(--gum); }
    .chat { display: grid; grid-template-columns: 340px minmax(0, 1fr); height: calc(100vh - 210px); min-height: 520px; padding: 0; overflow: hidden; }
    .list { border-right: 1px solid var(--line); display: flex; flex-direction: column; min-height: 0; }
    .list-h { padding: 10px; display: flex; flex-direction: column; gap: 8px; border-bottom: 1px solid var(--line); }
    .filters { display: flex; gap: 6px; flex-wrap: wrap; }
    .chip { border: 1px solid var(--line); background: var(--surface); border-radius: 14px; padding: 4px 10px; font: inherit; font-size: 12.5px; cursor: pointer; color: var(--ink-2); }
    .chip.on { background: var(--brand-soft); border-color: var(--teal); color: var(--teal-strong); font-weight: 700; }
    .chip b { background: var(--gum); color: #fff; border-radius: 8px; padding: 0 5px; margin-left: 3px; font-size: 11px; }
    .items { overflow: auto; flex: 1; }
    .item { width: 100%; display: flex; gap: 10px; align-items: center; padding: 10px 12px; border: 0; border-bottom: 1px solid var(--line); background: none; font: inherit; text-align: left; cursor: pointer; color: inherit; }
    .item:hover { background: var(--surface-2); }
    .item.sel { background: var(--brand-soft); }
    .av { width: 40px; height: 40px; border-radius: 50%; background: var(--surface-2); display: grid; place-items: center; font-weight: 800; color: var(--ink-2); flex: none; font-size: 14px; }
    .c { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 2px; }
    .n { display: flex; justify-content: space-between; gap: 6px; }
    .n b { white-space: nowrap; overflow: hidden; text-overflow: ellipsis; font-weight: 700; }
    .n small { color: var(--muted); flex: none; font-size: 11.5px; }
    .p { display: flex; gap: 6px; align-items: center; font-size: 13px; color: var(--muted); }
    .txt { flex: 1; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
    .att { background: var(--orange); color: #fff; border-radius: 50%; width: 18px; height: 18px; display: grid; place-items: center; font-weight: 900; font-size: 12px; flex: none; }
    .badge { background: #25d366; color: #fff; border-radius: 10px; min-width: 20px; height: 20px; padding: 0 6px; display: grid; place-items: center; font-size: 11.5px; font-weight: 800; flex: none; }
    .conv { display: flex; flex-direction: column; min-height: 0; background: #efeae2; }
    .conv-h { display: flex; gap: 10px; align-items: center; padding: 10px 14px; background: var(--surface); border-bottom: 1px solid var(--line); flex-wrap: wrap; }
    .who-h { flex: 1; min-width: 160px; }
    .switch { display: flex; gap: 6px; align-items: center; cursor: pointer; }
    .note { background: #fff3c4; padding: 6px 14px; font-size: 12.5px; }
    .up { display: flex; gap: 6px; flex-wrap: wrap; padding: 6px 14px; background: var(--surface); border-bottom: 1px solid var(--line); }
    .msgs { flex: 1; overflow: auto; padding: 14px 5%; display: flex; flex-direction: column; gap: 4px; }
    .row-m { display: flex; }
    .row-m.right { justify-content: flex-end; }
    .b { max-width: min(560px, 78%); background: #fff; border-radius: 10px; padding: 6px 10px 5px; box-shadow: 0 1px .5px rgba(11, 20, 26, .13); white-space: pre-wrap; font-size: 14px; line-height: 1.4; word-wrap: break-word; }
    .b.out { background: #d9fdd3; }
    .b.staff { background: #dcecff; }
    .b.sys { background: #f3f4f6; }
    .b.fail { outline: 2px solid var(--gum); }
    .who { font-size: 11.5px; font-weight: 800; color: var(--teal-strong); margin-bottom: 2px; }
    .b.staff .who { color: #1d5fbf; }
    .b.sys .who { color: var(--muted); }
    .meta { font-size: 11px; color: var(--muted); text-align: right; margin-top: 3px; }
    .compose { background: var(--surface); border-top: 1px solid var(--line); padding: 8px 12px; display: flex; flex-direction: column; gap: 6px; }
    .quick { display: flex; gap: 6px; align-items: center; overflow-x: auto; padding-bottom: 2px; scrollbar-width: thin; }
    .quick > * { flex: none; }
    .send-row { display: flex; gap: 8px; align-items: flex-end; }
    .send-row textarea { flex: 1; resize: vertical; min-height: 44px; }
    .pick { margin: auto; text-align: center; color: var(--muted); }
    @media (max-width: 900px) {
      .chat { grid-template-columns: minmax(0, 1fr); height: auto; }
      .list { max-height: 45vh; border-right: 0; border-bottom: 1px solid var(--line); }
      .conv { min-height: 70vh; }
    }
  `],
})
export class MessagesComponent implements OnInit, OnDestroy {
  private api = inject(Api);
  private toast = inject(ToastService);
  auth = inject(AuthService);
  @ViewChild('msgs') msgsRef?: ElementRef<HTMLDivElement>;

  threads: ConversationThread[] = [];
  unreadTotal = 0;
  attention = 0;
  q = '';
  filter: 'all' | 'attention' | 'unread' = 'all';
  phone: string | null = null;
  detail: ConversationDetail | null = null;
  draft = '';
  sending = false;
  newPatient: Patient | null = null;

  settings: AssistantSettings | null = null;
  modes = ['AUTOMATICO', 'FUERA_HORARIO', 'APAGADO'];
  pauses = [30, 60, 120, 240, 480];
  panel: '' | 'test' | 'faq' = '';
  faqs: FaqItem[] = [];
  faqForm: { id?: number; question: string; answer: string } = { question: '', answer: '' };
  testText = '';
  testPatient: Patient | null = null;
  testResult: AssistantTest | null = null;
  testing = false;
  quick = QUICK;
  canConfigure = false;

  private timer: ReturnType<typeof setInterval> | undefined;
  private lastCount = -1;

  ngOnInit() {
    this.canConfigure = this.auth.can('MENSAJES');
    this.loadThreads();
    this.loadSettings();
    this.api.faq().subscribe((f) => (this.faqs = f));
    // Tiempo real: la bandeja y la conversación abierta se actualizan cada 4 s.
    this.timer = setInterval(() => { this.loadThreads(); if (this.phone) this.refreshOpen(); }, 4000);
  }

  ngOnDestroy() { clearInterval(this.timer); }

  get visibleThreads(): ConversationThread[] {
    if (this.filter === 'attention') return this.threads.filter((t) => t.needsAttention);
    if (this.filter === 'unread') return this.threads.filter((t) => t.unread > 0);
    return this.threads;
  }

  loadThreads() {
    this.api.conversations(this.q || undefined).subscribe({
      next: (r) => { this.threads = r.items; this.unreadTotal = r.unread; this.attention = r.attention; },
      error: () => {},
    });
  }

  loadSettings() { this.api.assistantSettings().subscribe((s) => (this.settings = s)); }

  open(phone: string) {
    this.phone = phone;
    this.lastCount = -1;
    this.api.conversation(phone).subscribe({ next: (d) => this.show(d), error: (e) => this.toast.error(apiError(e).message) });
  }

  private refreshOpen() {
    const p = this.phone;
    if (!p) return;
    this.api.conversation(p).subscribe({ next: (d) => { if (this.phone === p) this.show(d); }, error: () => {} });
  }

  private show(d: ConversationDetail) {
    const grew = d.messages.length !== this.lastCount;
    this.detail = d;
    this.lastCount = d.messages.length;
    if (grew) setTimeout(() => { const el = this.msgsRef?.nativeElement; if (el) el.scrollTop = el.scrollHeight; });
  }

  startWith(p: Patient | null) {
    this.newPatient = null;
    if (!p) return;
    const digits = (p.phone || '').replace(/\D/g, '').replace(/^51(?=9\d{8}$)/, '');
    if (!/^9\d{8}$/.test(digits)) { this.toast.error(`${p.fullName} no tiene un celular válido registrado.`); return; }
    this.open(digits);
  }

  onEnter(e: Event) {
    const k = e as KeyboardEvent;
    if (k.shiftKey) return;
    k.preventDefault();
    this.send();
  }

  send() {
    const text = this.draft.trim();
    if (!text || !this.phone || this.sending) return;
    this.sending = true;
    this.api.sendWhatsapp(this.phone, text).subscribe({
      next: () => { this.draft = ''; this.sending = false; this.refreshOpen(); this.loadThreads(); },
      error: (e) => { this.sending = false; this.toast.error(apiError(e).message); this.refreshOpen(); },
    });
  }

  useQuick(t: string) { this.draft = this.draft ? this.draft + ' ' + t : t; }

  useFaq(id: string) {
    const f = this.faqs.find((x) => String(x.id) === id);
    if (f) this.draft = f.answer;
  }

  setAi(on: boolean) {
    if (!this.phone) return;
    this.api.updateConversation(this.phone, { aiEnabled: on }).subscribe({
      next: (d) => { this.show(d); this.toast.ok(on ? 'El asistente vuelve a responder en este chat.' : 'El asistente no responderá en este chat.'); },
      error: (e) => this.toast.error(apiError(e).message),
    });
  }

  attended() {
    if (!this.phone) return;
    this.api.updateConversation(this.phone, { attended: true }).subscribe({ next: (d) => { this.show(d); this.loadThreads(); } });
  }

  setMode(m: string) {
    if (!this.settings || this.settings.mode === m) return;
    this.api.updateAssistantSettings({ mode: m }).subscribe({
      next: (s) => { this.settings = s; this.toast.ok('Asistente: ' + this.modeLabel(m) + '.'); if (this.phone) this.refreshOpen(); },
      error: (e) => this.toast.error(apiError(e).message),
    });
  }

  setPause(p: number) {
    this.api.updateAssistantSettings({ pauseMinutes: p }).subscribe({ next: (s) => (this.settings = s), error: (e) => this.toast.error(apiError(e).message) });
  }

  toggle(p: 'test' | 'faq') { this.panel = this.panel === p ? '' : p; }

  runTest() {
    if (!this.testText.trim() || this.testing) return;
    this.testing = true;
    this.api.testAssistant(this.testText.trim(), this.testPatient?.id).subscribe({
      next: (r) => { this.testResult = r; this.testing = false; },
      error: (e) => { this.testing = false; this.toast.error(apiError(e).message); },
    });
  }

  saveFaq() {
    const body = { question: this.faqForm.question, answer: this.faqForm.answer, active: true };
    const req = this.faqForm.id ? this.api.updateFaq(this.faqForm.id, body) : this.api.addFaq(body);
    req.subscribe({
      next: (f) => { this.faqs = f; this.toast.ok('Base de conocimiento actualizada.'); this.resetFaq(); this.loadSettings(); },
      error: (e) => this.toast.error(apiError(e).message),
    });
  }

  editFaq(f: FaqItem) { this.faqForm = { id: f.id, question: f.question, answer: f.answer }; }
  resetFaq() { this.faqForm = { question: '', answer: '' }; }

  toggleFaq(f: FaqItem) {
    this.api.updateFaq(f.id, { question: f.question, answer: f.answer, active: !f.active }).subscribe({
      next: (x) => { this.faqs = x; this.loadSettings(); },
      error: (e) => this.toast.error(apiError(e).message),
    });
  }

  removeFaq(f: FaqItem) {
    if (!confirm('¿Eliminar la pregunta "' + f.question + '"?')) return;
    this.api.deleteFaq(f.id).subscribe({ next: (x) => { this.faqs = x; this.loadSettings(); } });
  }

  // ---------- presentación
  dt = dateTime;
  modeLabel(m: string) { return MODE_LABEL[m] || m; }
  modeHelp(m: string) { return MODE_HELP[m] || ''; }
  intent(i: string | null) { return i ? INTENT_LABEL[i] || i.toLowerCase() : ''; }

  label(m: ConversationMessage): string {
    if (m.source === 'PACIENTE') return this.detail?.thread.name || 'Paciente';
    if (m.source === 'IA') return '🤖 Asistente virtual';
    if (m.source === 'PERSONAL') return '👤 ' + (m.author || 'Personal');
    return '🔔 Aviso automático';
  }

  initials(n: string | null | undefined): string {
    return (n || '').split(/\s+/).filter(Boolean).slice(0, 2).map((x) => x[0]).join('').toUpperCase() || '#';
  }

  fmtPhone(p: string): string { return '+51 ' + p.slice(0, 3) + ' ' + p.slice(3, 6) + ' ' + p.slice(6); }

  ago(iso: string | null): string {
    if (!iso) return '';
    const d = new Date(iso.replace(' ', 'T'));
    const now = new Date();
    if (d.toDateString() === now.toDateString()) return d.toLocaleTimeString('es-PE', { hour: '2-digit', minute: '2-digit' });
    return d.toLocaleDateString('es-PE', { day: '2-digit', month: '2-digit' });
  }

  /** Formato de WhatsApp (*negrita*, _cursiva_, ~tachado~) sobre texto escapado. */
  wa(s: string): string {
    const esc = (s || '').replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c] as string));
    return esc.replace(/\*([^*\n]+)\*/g, '<b>$1</b>').replace(/(^|\s)_([^_\n]+)_/g, '$1<i>$2</i>').replace(/~([^~\n]+)~/g, '<s>$1</s>');
  }
}
