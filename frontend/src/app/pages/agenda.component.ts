import { DeleteButtonComponent } from '../shared/delete-button.component';
import { Component, Input, OnChanges, OnDestroy, OnInit, SimpleChanges, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { Api } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { ToastService } from '../core/toast.service';
import { Appointment, AuditEntry, Block, Dentist } from '../core/models';
import { STATUS_LABEL, STATUS_TONE, apiError, dateTime, hhmm, isoDate, longDay, parseLocal, soles } from '../core/format';
import { IconComponent } from '../shared/icon.component';
import { categoryColor } from '../core/categories';
import { ModalComponent } from '../shared/modal.component';

import { OnlyDirective } from '../shared/only.directive';
const SLOT_PX = 30;     // alto de 15 minutos
const MIN_PER_SLOT = 15;

interface Column { dentist: Dentist; blocks: Block[]; appts: Appointment[]; }

/** Agenda diaria por odontólogo (HU-07 / HU-09). */
@Component({
  selector: 'app-agenda',
  standalone: true,
  imports: [OnlyDirective, DeleteButtonComponent, FormsModule, RouterLink, IconComponent, ModalComponent],
  template: `
    <div class="page wide">
      <div class="page-head">
        <div>
          <h1>{{ view === 'dia' ? 'Agenda del día' : view === 'semana' ? 'Agenda de la semana' : 'Agenda del mes' }}</h1>
          <p class="sub">{{ view === 'dia' ? dayLabel : rangeLabel }}</p>
        </div>
        <div class="actions">
          <div class="seg vsel" role="tablist" aria-label="Vista">
            <button type="button" [class.on]="view === 'dia'" (click)="setView('dia')">Día</button>
            <button type="button" [class.on]="view === 'semana'" (click)="setView('semana')">Semana</button>
            <button type="button" [class.on]="view === 'mes'" (click)="setView('mes')">Mes</button>
          </div>
          <div class="row nav">
            <button class="btn sm" type="button" (click)="shift(-1)" aria-label="Anterior"><app-icon name="chevronLeft" [size]="16" /></button>
            <button class="btn sm" type="button" (click)="setDate(todayIso)" [disabled]="date === todayIso">Hoy</button>
            <button class="btn sm" type="button" (click)="shift(1)" aria-label="Siguiente"><app-icon name="chevronRight" [size]="16" /></button>
            <input class="input datein" type="date" [ngModel]="date" (ngModelChange)="setDate($event)" aria-label="Elegir fecha" />
          </div>
          @if (auth.can('MENSAJES') && botMode) {
            <div class="bot" title="Quién responde los mensajes de WhatsApp de los pacientes">
              <span class="bdot" [class.on]="botMode !== 'APAGADO'"></span>
              <label class="small" for="botmode">Asistente WhatsApp</label>
              <select id="botmode" class="bsel" [ngModel]="botMode" (ngModelChange)="setBotMode($event)">
                <option value="AUTOMATICO">Automático</option>
                <option value="FUERA_HORARIO">Fuera de horario</option>
                <option value="APAGADO">Apagado</option>
              </select>
              <a class="blink" routerLink="/mensajes">Mensajes @if (botUnread) { <b>{{ botUnread }}</b> }</a>
            </div>
          }
          @if (auth.can('CITAS_GESTIONAR')) {
            <a class="btn primary" routerLink="/citas/nueva" [queryParams]="{ fecha: date }"><app-icon name="plus" [size]="16" /> Nueva cita</a>
          }
        </div>
      </div>

      @if (view === 'dia') {
      <div class="counts">
        @for (s of statusOrder; track s) {
          <span class="tag" [class]="'tag ' + tone(s)">{{ label(s) }} <strong class="tabular">{{ counts[s] || 0 }}</strong></span>
        }
        <span class="grow"></span>
        @if (dentists.length > 1 && !auth.hasRole('ODONTOLOGO')) {
          <select class="input dsel" [ngModel]="filter" (ngModelChange)="filter = $event; reload()" aria-label="Filtrar por odontólogo">
            <option [ngValue]="null">Todos los odontólogos</option>
            @for (d of dentists; track d.id) { <option [ngValue]="d.id">{{ d.fullName }}</option> }
          </select>
        }
      </div>

      <section class="panel board">
        @if (loading) {
          <div class="skeleton" style="height: 420px; margin: 16px"></div>
        } @else if (columns.length === 0) {
          <div class="empty"><h3>Ningún odontólogo atiende este día</h3><p>Elige otra fecha o revisa los horarios en Usuarios y accesos.</p></div>
        } @else {
          <div class="grid-head" [style.grid-template-columns]="cols">
            <div></div>
            @for (c of columns; track c.dentist.id) {
              <div class="dh">
                @if (c.dentist.photoUrl) { <img class="dph" [src]="c.dentist.photoUrl" [alt]="c.dentist.fullName" [style.border-color]="c.dentist.color" /> }
                @else { <span class="dot" [style.background]="c.dentist.color"></span> }
                <div><div class="strong">{{ c.dentist.fullName }}</div><div class="muted small">{{ c.dentist.specialty }} · {{ c.appts.length }} citas</div></div>
              </div>
            }
          </div>
          <div class="grid-body" [style.grid-template-columns]="cols" [style.height.px]="bodyHeight">
            <div class="times">
              @for (t of hours; track t) { <span class="tl tabular" [style.top.px]="y(t)">{{ t }}</span> }
            </div>
            @for (c of columns; track c.dentist.id) {
              <div class="col">
                @for (t of hours; track t) { <span class="hline" [style.top.px]="y(t)"></span> }
                @for (b of c.blocks; track b.start) {
                  <div class="open" [style.top.px]="y(b.start)" [style.height.px]="y(b.end) - y(b.start)"></div>
                }
                @for (a of c.appts; track a.id) {
                  <button type="button" class="appt" [class]="'appt s-' + a.status"
                          [style.top.px]="y(time(a.startAt)) + 1" [style.height.px]="y(time(a.endAt)) - y(time(a.startAt)) - 2"
                          [style.border-left-color]="c.dentist.color" (click)="open(a)">
                    <span class="t tabular">{{ hhmm(a.startAt) }}–{{ hhmm(a.endAt) }}</span>
                    <span class="n">{{ a.patient.fullName }}@if (a.bookedVia === 'WEB') { <span class="webtag" title="Reservada por el enlace público">Web</span> }</span>
                    <span class="r"><i class="cdot" [style.background]="catColor(a.category)"></i>{{ motive(a) }}</span>
                    <span class="st">{{ label(a.status) }}@if ($any(a).rescheduleRequestedAt) { · pide reprogramar }</span>
                  </button>
                }
              </div>
            }
            @if (nowY !== null) { <div class="now" [style.top.px]="nowY"><span>{{ nowLabel }}</span></div> }
          </div>
        }
      </section>
      } @else {
        <div class="counts">
          <span class="muted small">{{ rangeAppts.length }} citas · {{ activeCount }} sin cancelar</span>
          @if (view === 'semana') { <label class="check small"><input type="checkbox" [(ngModel)]="showCancelled" (ngModelChange)="layoutWeek()" /> Ver canceladas</label> }
          <span class="grow"></span>
          @if (dentists.length > 1 && !auth.hasRole('ODONTOLOGO')) {
            <select class="input dsel" [ngModel]="filter" (ngModelChange)="filter = $event; reload()" aria-label="Filtrar por odontólogo">
              <option [ngValue]="null">Todos los odontólogos</option>
              @for (d of dentists; track d.id) { <option [ngValue]="d.id">{{ d.fullName }}</option> }
            </select>
          }
        </div>
        @if (loading) { <div class="skeleton" style="height: 420px"></div> }
        @else if (view === 'semana') {
          <section class="panel week">
            <div class="wgrid" [style.--wh.px]="weekHeight">
              <div class="wcorner"></div>
              @for (d of weekDays; track d.iso) {
                <button type="button" class="wh" [class.today]="d.iso === todayIso" (click)="goDay(d.iso)">
                  <span class="muted small">{{ d.dow }}</span><strong>{{ d.num }}</strong><span class="muted small">{{ (weekBoxes[d.iso] || []).length }} cita{{ (weekBoxes[d.iso] || []).length === 1 ? '' : 's' }}</span>
                </button>
              }
              <div class="wtimes">
                @for (t of weekHours; track t) { <span class="tl tabular" [style.top.px]="wy(t)">{{ t }}</span> }
              </div>
              @for (d of weekDays; track d.iso) {
                <div class="wcol" [class.today]="d.iso === todayIso">
                  @for (t of weekHours; track t) { <span class="hline" [style.top.px]="wy(t)"></span> }
                  @for (b of weekBoxes[d.iso] || []; track b.a.id) {
                    <button type="button" class="wappt" [class]="'wappt s-' + b.a.status" [style.top.px]="b.top" [style.height.px]="b.height"
                            [style.left]="'calc(' + (100 / b.lanes) * b.lane + '% + 2px)'" [style.width]="'calc(' + (100 / b.lanes) + '% - 4px)'"
                            [style.border-left-color]="b.a.dentist.color" (click)="open(b.a)"
                            [attr.title]="hhmm(b.a.startAt) + '–' + hhmm(b.a.endAt) + ' · ' + b.a.patient.fullName + ' · ' + (b.a.category || b.a.reason) + ' · ' + b.a.dentist.fullName">
                      <span class="n"><b class="t tabular">{{ hhmm(b.a.startAt) }}</b> {{ b.a.patient.fullName }}</span>
                      @if (b.height > 30) { <span class="r"><i class="cdot" [style.background]="catColor(b.a.category)"></i>{{ b.a.category || b.a.reason }}</span> }
                    </button>
                  }
                  @if (d.iso === todayIso && weekNowY !== null) { <div class="now" [style.top.px]="weekNowY"></div> }
                  @if (!(weekBoxes[d.iso] || []).length) { <span class="free muted small">Libre</span> }
                </div>
              }
            </div>
          </section>
        } @else {
          <section class="panel month">
            @for (h of ['Lun', 'Mar', 'Mié', 'Jue', 'Vie', 'Sáb', 'Dom']; track h) { <div class="mh muted small">{{ h }}</div> }
            @for (d of monthDays; track d.iso) {
              <button type="button" class="mday" [class.out]="!d.inMonth" [class.today]="d.iso === todayIso" (click)="goDay(d.iso)">
                <span class="mn">{{ d.num }}</span>
                @if (d.list.length) {
                  <span class="mc">{{ d.list.length }} cita{{ d.list.length === 1 ? '' : 's' }}</span>
                  @for (a of d.list.slice(0, 3); track a.id) { <span class="mi" [style.border-left-color]="a.dentist.color">{{ hhmm(a.startAt) }} {{ a.patient.firstName || a.patient.fullName }}</span> }
                  @if (d.list.length > 3) { <span class="muted small">+{{ d.list.length - 3 }} más</span> }
                }
              </button>
            }
          </section>
        }
      }
    </div>

    <app-modal [open]="!!sel" [title]="sel?.patient?.fullName" [subtitle]="sel ? hhmm(sel.startAt) + ' – ' + hhmm(sel.endAt) + ' · ' + sel.dentist.fullName : ''" (close)="sel = null">
      @if (sel) {
        <div class="stack">
          <div class="row wrap">
            <span class="tag" [class]="'tag ' + tone(sel.status)">{{ label(sel.status) }}</span>
            <span class="muted small"><i class="cdot" [style.background]="catColor(sel.category)"></i>{{ motive(sel) }} · {{ sel.durationMin }} min</span>
          </div>
          @if (sel.notes) { <p class="small">{{ sel.notes }}</p> }
          @if (sel.statusReason) { <p class="small muted">{{ sel.statusReason }}</p> }
          @if ($any(sel).rescheduleRequestedAt) {
            <div class="notice amber"><span>El paciente pidió otro horario (respondió "2" por WhatsApp). Usa <strong>Reprogramar</strong> después de coordinar con él.</span></div>
          }
          @if (selInfo) {
            @if (selInfo.clinicalAlerts?.allergies) { <div class="notice red small"><app-icon name="alert" [size]="16" /><span><strong>Alergias:</strong> {{ selInfo.clinicalAlerts.allergies }}</span></div> }
            @if (selInfo.clinicalAlerts?.medication) { <div class="notice amber small"><span><strong>Medicación:</strong> {{ selInfo.clinicalAlerts.medication }}</span></div> }
            @if (selInfo.clinicalAlerts && !selInfo.clinicalAlerts.allergies) { <p class="muted small">Alergias: no refiere.</p> }
            @if (selInfo.pendingProcedures?.length) {
              <div class="small"><strong>Por realizar:</strong>
                <ul class="pp">@for (x of selInfo.pendingProcedures.slice(0, 5); track x.id) { <li>{{ x.procedure }}{{ x.tooth ? ' · pieza ' + x.tooth : '' }}</li> }</ul></div>
            }
            @if (selInfo.patient?.minor) { <p class="small"><span class="tag amber">Menor de edad</span> Representante: {{ selInfo.patient.guardianName }} · {{ selInfo.patient.guardianPhone }}</p> }
          }
          @if (mine(sel)) { <span class="tag teal small">Esta cita es contigo</span> }
          <dl class="kv">
            <dt>Documento</dt><dd>{{ sel.patient.docType }} {{ sel.patient.docNumber }}</dd>
            <dt>Teléfono</dt><dd>{{ sel.patient.phone }}</dd>
            @if (sel.arrivedAt) { <dt>Llegada</dt><dd>{{ hhmm(sel.arrivedAt) }}</dd> }
            <dt>Programada por</dt><dd>{{ sel.createdBy }} · {{ dt(sel.createdAt) }}</dd>
          </dl>

          @if (canChange) {
            <div class="row wrap actions-row">
              @for (n of nextStates(sel.status); track n) {
                <button type="button" class="btn sm" [class.primary]="n === primaryNext(sel.status)" [class.danger]="n === 'CANCELADA' || n === 'NO_ASISTIO'"
                        (click)="act(n)" [disabled]="busy">{{ actionLabel(n) }}</button>
              }
              @if (canReschedule(sel)) {
                <a class="btn sm" routerLink="/citas/nueva" [queryParams]="{ reprogramar: sel.id }">Reprogramar</a>
              }
              <app-delete-button entity="appointments" [recordId]="sel.id" what="Cita" (deleted)="sel = null; reload()" />
            </div>
            @if (cancelling) {
              <div class="field">
                <label for="cr">Motivo de la cancelación</label>
                <input id="cr" class="input" [(ngModel)]="reason" placeholder="Ej. paciente avisó por teléfono" appOnly="text" maxlength="200" />
                <div class="row" style="justify-content: flex-end; margin-top: 6px">
                  <button type="button" class="btn sm" (click)="cancelling = false">Volver</button>
                  <button type="button" class="btn sm danger" (click)="change('CANCELADA')" [disabled]="!reason.trim() || busy">Cancelar cita</button>
                </div>
              </div>
            }
          }

          @if (auth.can('CITAS_GESTIONAR') && (sel.status === 'PROGRAMADA' || sel.status === 'CONFIRMADA')) {
            <div class="remind">
              <button type="button" class="btn sm" (click)="remind()" [disabled]="reminding"><app-icon name="bell" [size]="14" /> Enviar recordatorio ahora</button>
              <span class="muted small">El recordatorio automático sale 24 h antes por {{ channelLabel(sel.patient.notifyChannel) }}.</span>
            </div>
          }

          <div class="row"><a class="small" [routerLink]="['/pacientes', sel.patient.id]">Abrir ficha del paciente</a></div>

          @if (att && (att.procedures.length || att.evolutions.length || att.payments.length || att.prescriptions.length)) {
            <div class="attention">
              <div class="small strong">En esta cita</div>
              @for (x of att.procedures; track x.id) { <div class="small">✔ {{ x.procedure }}@if (x.tooth) { · pieza {{ x.tooth }} } <span class="muted">· {{ money(x.price) }}</span></div> }
              @for (x of att.evolutions; track x.id) { <div class="small" [class.muted]="x.voidedAt">📝 {{ x.notes }}@if (x.voidedAt) { (anulada) }</div> }
              @for (x of att.prescriptions; track x.id) { <div class="small" [class.muted]="x.voidedAt">💊 Receta: {{ x.drugs }}@if (x.voidedAt) { (anulada) }</div> }
              @for (x of att.payments; track x.id) { <div class="small" [class.muted]="x.status !== 'VALIDO'">💵 Cobro {{ money(x.amount) }} · {{ x.method }}@if (x.status !== 'VALIDO') { (anulado) }</div> }
            </div>
          } @else if (sel.status === 'ATENDIDA') { <p class="muted small">No se registraron procedimientos, evoluciones ni cobros en esta cita.</p> }

          @if (history.length) {
            <details>
              <summary class="small strong">Historial de la cita</summary>
              <ul class="hist">@for (h of history; track h.id) { <li><span class="muted">{{ dt(h.createdAt) }}</span> {{ h.username }} · {{ h.detail }}</li> }</ul>
            </details>
          }
        </div>
      }
    </app-modal>
  `,
  styles: [`
    .webtag { display: inline-block; margin-left: 6px; padding: 0 6px; border-radius: 6px; background: #efe7ff; color: #5b3fb0; font-size: 10.5px; font-weight: 800; vertical-align: middle; }
    .vsel { margin-right: 6px; }
    .week { overflow: auto; max-height: calc(100vh - 230px); min-height: 380px; }
    .wgrid { display: grid; grid-template-columns: 56px repeat(7, minmax(120px, 1fr)); grid-template-rows: auto var(--wh); min-width: 900px; }
    .wcorner, .wh { position: sticky; top: 0; z-index: 3; background: var(--surface, #fff); border-bottom: 1px solid var(--line); }
    .wcorner { left: 0; z-index: 4; }
    .wh { display: flex; flex-direction: column; align-items: center; gap: 1px; padding: 8px 4px; border-top: 0; border-left: 1px solid var(--line); border-right: 0; cursor: pointer; font: inherit; }
    .wh strong { font-size: 19px; }
    .wh.today { background: var(--teal-soft); }
    .wtimes { position: sticky; left: 0; z-index: 2; background: var(--surface, #fff); }
    .wtimes .tl { position: absolute; right: 8px; transform: translateY(-50%); font-size: 11.5px; color: var(--muted); }
    .wcol { position: relative; border-left: 1px solid var(--line); }
    .wcol.today { background: color-mix(in srgb, var(--teal-soft) 55%, transparent); }
    .wcol .hline { position: absolute; left: 0; right: 0; border-top: 1px dashed var(--line); }
    .wcol .free { position: absolute; top: 10px; left: 10px; }
    .wappt { position: absolute; display: flex; flex-direction: column; gap: 0; overflow: hidden; text-align: left; padding: 3px 6px;
      border: 1px solid var(--line); border-left: 4px solid var(--teal); border-radius: 8px; background: #fff; font: inherit; font-size: 11.5px;
      line-height: 1.35; justify-content: center; cursor: pointer; box-shadow: 0 1px 2px rgba(15, 23, 42, .06); z-index: 1; }
    .wappt:hover { z-index: 2; box-shadow: 0 4px 12px rgba(15, 23, 42, .15); }
    .wappt .t { font-weight: 800; margin-right: 2px; }
    .wappt .n { font-weight: 600; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
    .wappt .r { color: var(--muted); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
    .cdot { display: inline-block; width: 7px; height: 7px; border-radius: 50%; margin-right: 5px; vertical-align: middle; }
    .wappt .cdot { display: inline-block; width: 7px; height: 7px; border-radius: 50%; margin-right: 4px; vertical-align: middle; }
    .wappt.s-CANCELADA { opacity: .5; text-decoration: line-through; }
    .wappt.s-ATENDIDA { background: #eef8f1; }
    .wappt.s-NO_ASISTIO { background: #fdf0f2; }
    .wcol .now { position: absolute; left: 0; right: 0; height: 2px; border-top: 0; background: var(--gum, #c0264d); z-index: 2; }
    .chip { display: flex; flex-direction: column; text-align: left; gap: 2px; padding: 6px 8px; border: 1px solid var(--line); border-left: 4px solid var(--teal); border-radius: 8px; background: var(--surface); cursor: pointer; font: inherit; font-size: 12.5px; }
    .chip.s-CANCELADA { opacity: .5; text-decoration: line-through; }
    .chip.s-ATENDIDA { background: #eef8f1; }
    .pad { padding: 6px; }
    .month { display: grid; grid-template-columns: repeat(7, 1fr); }
    .mh { padding: 8px; text-align: center; border-bottom: 1px solid var(--line); font-weight: 700; }
    .mday { min-height: 104px; display: flex; flex-direction: column; align-items: flex-start; gap: 2px; padding: 6px; border: 0; border-right: 1px solid var(--line); border-bottom: 1px solid var(--line); background: var(--surface); cursor: pointer; font: inherit; text-align: left; overflow: hidden; }
    .mday:nth-of-type(7n) { border-right: 0; }
    .mday.out { opacity: .45; }
    .mday.today { background: var(--teal-soft); }
    .mday:hover { outline: 2px solid var(--teal); outline-offset: -2px; }
    .mn { font-weight: 800; }
    .mc { font-size: 11.5px; color: var(--teal-strong); font-weight: 700; }
    .mi { font-size: 11.5px; border-left: 3px solid var(--teal); padding-left: 4px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; max-width: 100%; }
    .notice.red { background: #fde8e6; color: #8a1c12; border: 1px solid #f3b4ac; display: flex; gap: 8px; align-items: flex-start; padding: 8px 10px; border-radius: 10px; }
    .pp { margin: 4px 0 0; padding-left: 18px; }
    @media (max-width: 760px) { .mday { min-height: 64px; } .mi { display: none; } .week { grid-template-columns: repeat(7, 150px); } }
    .dph { width: 40px; height: 40px; border-radius: 50%; object-fit: cover; border: 2px solid; flex: none; }
    .bot { display: flex; align-items: center; gap: 6px; border: 1px solid var(--line); border-radius: 10px; padding: 4px 8px; background: var(--surface); }
    .bdot { width: 8px; height: 8px; border-radius: 50%; background: var(--muted); }
    .bdot.on { background: #25d366; }
    .bsel { height: 28px; border: 0; background: transparent; font: inherit; font-size: 13px; font-weight: 700; color: var(--ink); cursor: pointer; }
    .blink { font-size: 13px; font-weight: 700; }
    .blink b { background: #25d366; color: #fff; border-radius: 9px; padding: 0 6px; font-size: 11.5px; margin-left: 2px; }
    .wide { max-width: none; }
    .remind { display: flex; flex-direction: column; gap: 4px; padding: 10px 12px; background: var(--surface-2); border-radius: 10px; }
    .remind .btn { align-self: flex-start; }
    .nav { gap: 6px; }
    .datein { width: 160px; height: 30px; font-size: 13px; }
    .counts { display: flex; flex-wrap: wrap; align-items: center; gap: 6px; margin-bottom: 14px; }
    .counts strong { margin-left: 2px; }
    .dsel { width: 240px; height: 34px; font-size: 13px; }
    .board { overflow-x: auto; }
    .grid-head, .grid-body { display: grid; min-width: 640px; }
    .grid-head { border-bottom: 1px solid var(--line); position: sticky; top: 0; background: var(--surface); z-index: 3; }
    .dh { display: flex; align-items: center; gap: 10px; padding: 12px 14px; border-left: 1px solid var(--line); }
    .dot { width: 10px; height: 10px; border-radius: 50%; flex: none; }
    .grid-body { position: relative; }
    .times { position: relative; }
    .tl { position: absolute; right: 10px; transform: translateY(-50%); font-size: 11.5px; color: var(--muted); }
    .col { position: relative; border-left: 1px solid var(--line); background: repeating-linear-gradient(135deg, #f3f6f6 0 6px, #eef2f2 6px 12px); }
    .hline { position: absolute; left: 0; right: 0; border-top: 1px solid #e3eaeb; z-index: 1; }
    .open { position: absolute; left: 0; right: 0; background: var(--surface); }
    .appt { position: absolute; left: 6px; right: 6px; z-index: 2; display: flex; flex-direction: column; align-items: flex-start; gap: 0; overflow: hidden;
      padding: 5px 9px; border: 1px solid var(--line); border-left: 4px solid; border-radius: 7px; background: #fff; text-align: left; font: inherit; cursor: pointer; color: var(--ink);
      box-shadow: 0 1px 2px rgba(15, 43, 49, .06); }
    .appt:hover { box-shadow: 0 4px 14px -4px rgba(15, 43, 49, .25); z-index: 4; }
    .appt .t { font-size: 11.5px; color: var(--muted); }
    .appt .n { font-weight: 600; font-size: 13px; line-height: 1.25; }
    .appt .r { font-size: 12px; color: var(--ink-2); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; max-width: 100%; }
    .appt .st { position: absolute; right: 8px; top: 5px; font-size: 11px; font-weight: 600; color: var(--muted); }
    .appt.s-CONFIRMADA .st { color: var(--teal); }
    .appt.s-EN_ESPERA { background: var(--amber-soft); } .appt.s-EN_ESPERA .st { color: var(--amber); }
    .appt.s-EN_ATENCION { background: var(--restore-soft); } .appt.s-EN_ATENCION .st { color: var(--restore); }
    .appt.s-ATENDIDA { background: var(--green-soft); } .appt.s-ATENDIDA .st { color: var(--green); }
    .appt.s-CANCELADA, .appt.s-NO_ASISTIO { background: #fafbfb; opacity: .6; text-decoration: line-through; }
    .now { position: absolute; left: 56px; right: 0; border-top: 2px solid var(--gum); z-index: 5; pointer-events: none; }
    .now span { position: absolute; left: -54px; top: -10px; font-size: 11px; font-weight: 700; color: var(--gum); background: var(--surface); padding: 0 3px; }
    .actions-row { padding-top: 6px; }
    .attention { background: var(--surface-2, #f4f8fa); border-radius: 8px; padding: 8px 10px; margin: 8px 0; display: flex; flex-direction: column; gap: 3px; }
    .hist { list-style: none; padding: 0; margin: 8px 0 0; font-size: 12.5px; display: flex; flex-direction: column; gap: 4px; }
  `],
})
export class AgendaComponent implements OnInit, OnChanges, OnDestroy {
  private api = inject(Api);
  private toast = inject(ToastService);
  private router = inject(Router);
  auth = inject(AuthService);

  @Input() fecha?: string;

  todayIso = isoDate();
  date = this.todayIso;
  filter: number | null = null;
  dentists: Dentist[] = [];
  columns: Column[] = [];
  counts: Record<string, number> = {};
  loading = true;
  hours: string[] = [];
  startMin = 8 * 60;
  endMin = 20 * 60;
  bodyHeight = 0;
  cols = '64px';
  nowY: number | null = null;
  nowLabel = '';

  sel: Appointment | null = null;
  history: AuditEntry[] = [];
  cancelling = false;
  reason = '';
  busy = false;

  statusOrder = ['PROGRAMADA', 'CONFIRMADA', 'EN_ESPERA', 'EN_ATENCION', 'ATENDIDA', 'CANCELADA', 'NO_ASISTIO'];
  private timer = setInterval(() => this.placeNow(), 60_000);

  constructor() {
    this.api.dentists().subscribe((d) => (this.dentists = d));
    const me = this.auth.user();
    if (me?.role === 'ODONTOLOGO' && me.dentistId) this.filter = me.dentistId;
  }

  /** Parámetro del asistente virtual de WhatsApp (quién responde a los pacientes) y mensajes sin leer. */
  botMode = '';
  botUnread = 0;
  private loadBot() {
    if (!this.auth.can('MENSAJES')) return;
    this.api.conversationSummary().subscribe({ next: (s) => { this.botMode = s.mode; this.botUnread = s.unread; }, error: () => {} });
  }
  setBotMode(m: string) {
    this.api.updateAssistantSettings({ mode: m }).subscribe({
      next: (s) => { this.botMode = s.mode; this.toast.ok('Asistente de WhatsApp: ' + ({ AUTOMATICO: 'automático', FUERA_HORARIO: 'solo fuera de horario', APAGADO: 'apagado' } as Record<string, string>)[s.mode] + '.'); },
      error: (e) => { this.toast.error(apiError(e).message); this.loadBot(); },
    });
  }

  ngOnInit() {
    this.loadBot();
    if (this.fecha && /^\d{4}-\d{2}-\d{2}$/.test(this.fecha)) this.date = this.fecha;
    try { const v = localStorage.getItem('cc.agendaView'); if (v === 'semana' || v === 'mes') this.view = v; } catch {}
    this.reload();
  }

  ngOnChanges(ch: SimpleChanges) {
    const c = ch['fecha'];
    if (c && !c.firstChange && this.fecha && this.fecha !== this.date && /^\d{4}-\d{2}-\d{2}$/.test(this.fecha)) {
      this.date = this.fecha;
      this.load();
    }
  }

  view: 'dia' | 'semana' | 'mes' = 'dia';
  rangeAppts: Appointment[] = [];
  weekDays: { iso: string; dow: string; num: number; list: Appointment[] }[] = [];
  /** v2.10: semana con eje de horas. */
  weekHours: string[] = [];
  weekStart = 8 * 60;
  weekEnd = 20 * 60;
  weekHeight = 0;
  weekNowY: number | null = null;
  weekBoxes: Record<string, { a: Appointment; top: number; height: number; lane: number; lanes: number }[]> = {};
  catColor = categoryColor;
  showCancelled = false;
  private scrolledFor = '';

  /** v2.10: al abrir el día se muestra la hora actual (hoy) o la primera cita, no siempre las 8:00. */
  private scrollToFocus() {
    const target = document.querySelector<HTMLElement>('.grid-body .now') || document.querySelector<HTMLElement>('.grid-body .appt');
    if (!target) return;
    if (target.getBoundingClientRect().top > window.innerHeight - 120) target.scrollIntoView({ block: 'center', behavior: 'smooth' });
  }
  /** "Ortodoncia · control mensual", sin repetir si el motivo es la misma categoría. */
  motive(a: Appointment): string {
    if (!a.category || a.reason === a.category) return a.reason;
    return a.category + ' · ' + a.reason;
  }
  monthDays: { iso: string; num: number; inMonth: boolean; list: Appointment[] }[] = [];
  get activeCount() { return this.rangeAppts.filter((a) => a.status !== 'CANCELADA').length; }
  get rangeLabel() {
    const d = parseLocal(this.date);
    if (this.view === 'mes') return new Intl.DateTimeFormat('es-PE', { month: 'long', year: 'numeric' }).format(d);
    const r = this.range();
    const f = new Intl.DateTimeFormat('es-PE', { day: 'numeric', month: 'short' });
    return f.format(parseLocal(r.from)) + ' – ' + f.format(parseLocal(r.to));
  }
  setView(v: 'dia' | 'semana' | 'mes') {
    this.view = v;
    try { localStorage.setItem('cc.agendaView', v); } catch {}
    this.reload();
  }
  reload() { this.view === 'dia' ? this.load() : this.loadRange(); }
  goDay(iso: string) { this.view = 'dia'; this.setDate(iso); }
  private range() {
    const d = parseLocal(this.date);
    if (this.view === 'semana') {
      const s = new Date(d); s.setDate(d.getDate() - ((d.getDay() + 6) % 7));
      const e = new Date(s); e.setDate(s.getDate() + 6);
      return { from: isoDate(s), to: isoDate(e) };
    }
    const first = new Date(d.getFullYear(), d.getMonth(), 1);
    const s = new Date(first); s.setDate(1 - ((first.getDay() + 6) % 7));
    const last = new Date(d.getFullYear(), d.getMonth() + 1, 0);
    const e = new Date(last); e.setDate(last.getDate() + (6 - ((last.getDay() + 6) % 7)));
    return { from: isoDate(s), to: isoDate(e) };
  }
  loadRange() {
    this.loading = true;
    const r = this.range();
    this.api.appointmentsRange(r.from, r.to, this.filter ?? undefined).subscribe({
      next: (list) => {
        this.rangeAppts = list.sort((a, b) => a.startAt.localeCompare(b.startAt));
        const by = (iso: string) => this.rangeAppts.filter((a) => a.startAt.startsWith(iso));
        const days: string[] = [];
        for (let x = parseLocal(r.from); isoDate(x) <= r.to; x.setDate(x.getDate() + 1)) days.push(isoDate(x));
        const dows = ['Dom', 'Lun', 'Mar', 'Mié', 'Jue', 'Vie', 'Sáb'];
        const month = parseLocal(this.date).getMonth();
        this.weekDays = days.map((iso) => { const d = parseLocal(iso); return { iso, dow: dows[d.getDay()], num: d.getDate(), list: by(iso) }; });
        this.layoutWeek();
        this.monthDays = days.map((iso) => { const d = parseLocal(iso); return { iso, num: d.getDate(), inMonth: d.getMonth() === month, list: by(iso).filter((a) => a.status !== 'CANCELADA') }; });
        this.loading = false;
      },
      error: () => (this.loading = false),
    });
  }

  private static readonly WPX = 1.5; // píxeles por minuto en la vista semanal (1 h = 90 px, 30 min = 45 px)

  wy(t: string): number { return (this.toMin(t) - this.weekStart) * AgendaComponent.WPX + 4; }

  layoutWeek() {
    const all = this.weekDays.flatMap((d) => d.list).filter((a) => this.showCancelled || a.status !== 'CANCELADA');
    let lo = 8 * 60, hi = 20 * 60;
    for (const a of all) { lo = Math.min(lo, this.toMin(this.time(a.startAt))); hi = Math.max(hi, this.toMin(this.time(a.endAt))); }
    this.weekStart = Math.floor(lo / 60) * 60;
    this.weekEnd = Math.ceil(hi / 60) * 60;
    this.weekHours = [];
    for (let m = this.weekStart; m <= this.weekEnd; m += 60) this.weekHours.push(String(Math.floor(m / 60)).padStart(2, '0') + ':00');
    this.weekHeight = (this.weekEnd - this.weekStart) * AgendaComponent.WPX + 10;
    this.weekBoxes = {};
    for (const d of this.weekDays) {
      // Citas que se cruzan (distintos odontólogos a la misma hora) se reparten en carriles lado a lado.
      const items = d.list.filter((a) => this.showCancelled || a.status !== 'CANCELADA').map((a) => ({ a, s: this.toMin(this.time(a.startAt)), e: this.toMin(this.time(a.endAt)) }));
      const out: { a: Appointment; top: number; height: number; lane: number; lanes: number }[] = [];
      let group: typeof items = [], groupEnd = -1;
      const flush = () => {
        const laneEnds: number[] = [];
        const placed = group.map((it) => {
          let lane = laneEnds.findIndex((end) => end <= it.s);
          if (lane < 0) { lane = laneEnds.length; laneEnds.push(it.e); } else laneEnds[lane] = it.e;
          return { it, lane };
        });
        for (const p of placed) out.push({ a: p.it.a, lane: p.lane, lanes: laneEnds.length,
          top: (p.it.s - this.weekStart) * AgendaComponent.WPX + 5, height: Math.max(20, (p.it.e - p.it.s) * AgendaComponent.WPX - 2) });
        group = []; groupEnd = -1;
      };
      for (const it of items.sort((x, y) => x.s - y.s)) {
        if (group.length && it.s >= groupEnd) flush();
        group.push(it); groupEnd = Math.max(groupEnd, it.e);
      }
      if (group.length) flush();
      this.weekBoxes[d.iso] = out;
    }
    const now = new Date(), m = now.getHours() * 60 + now.getMinutes();
    this.weekNowY = m >= this.weekStart && m <= this.weekEnd ? (m - this.weekStart) * AgendaComponent.WPX + 4 : null;
  }

  get dayLabel() { return longDay(this.date) + (this.date === this.todayIso ? ' · hoy' : ''); }

  setDate(d: string) {
    if (!d) return;
    this.date = d;
    this.router.navigate([], { queryParams: { fecha: d === this.todayIso ? null : d }, replaceUrl: true });
    this.reload();
  }

  shift(n: number) {
    const d = parseLocal(this.date);
    if (this.view === 'mes') d.setMonth(d.getMonth() + n, 1);
    else d.setDate(d.getDate() + n * (this.view === 'semana' ? 7 : 1));
    this.setDate(isoDate(d));
  }

  load() {
    this.loading = true;
    this.api.day(this.date, this.filter ?? undefined).subscribe({
      next: (r) => {
        this.counts = r.counts;
        this.columns = r.columns
          .map((c) => ({ ...c, appts: r.appointments.filter((a) => a.dentist.id === c.dentist.id) }))
          .filter((c) => c.blocks.length > 0 || c.appts.length > 0);
        this.computeRange();
        this.loading = false;
        this.placeNow();
        if (this.sel) this.sel = r.appointments.find((a) => a.id === this.sel!.id) || null;
        if (!this.scrolledFor || this.scrolledFor !== this.date) { this.scrolledFor = this.date; setTimeout(() => this.scrollToFocus(), 50); }
      },
      error: () => (this.loading = false),
    });
  }

  private toMin(t: string): number {
    const [h, m] = t.substring(0, 5).split(':').map(Number);
    return h * 60 + m;
  }

  private computeRange() {
    let lo = 9 * 60, hi = 19 * 60;
    for (const c of this.columns) {
      for (const b of c.blocks) { lo = Math.min(lo, this.toMin(b.start)); hi = Math.max(hi, this.toMin(b.end)); }
      for (const a of c.appts) { lo = Math.min(lo, this.toMin(this.time(a.startAt))); hi = Math.max(hi, this.toMin(this.time(a.endAt))); }
    }
    this.startMin = Math.floor(lo / 60) * 60;
    this.endMin = Math.ceil(hi / 60) * 60;
    this.hours = [];
    for (let m = this.startMin; m <= this.endMin; m += 60) this.hours.push(String(Math.floor(m / 60)).padStart(2, '0') + ':00');
    this.bodyHeight = ((this.endMin - this.startMin) / MIN_PER_SLOT) * SLOT_PX + 12;
    this.cols = `64px repeat(${this.columns.length}, minmax(220px, 1fr))`;
  }

  y(t: string): number {
    return ((this.toMin(t) - this.startMin) / MIN_PER_SLOT) * SLOT_PX + 6;
  }

  time(iso: string): string { return hhmm(iso); }

  placeNow() {
    if (this.date !== isoDate()) { this.nowY = null; return; }
    const d = new Date();
    const m = d.getHours() * 60 + d.getMinutes();
    if (m < this.startMin || m > this.endMin) { this.nowY = null; return; }
    this.nowLabel = String(d.getHours()).padStart(2, '0') + ':' + String(d.getMinutes()).padStart(2, '0');
    this.nowY = ((m - this.startMin) / MIN_PER_SLOT) * SLOT_PX + 6;
  }

  get canChange() { return this.auth.can('CITAS_ESTADO'); }

  canReschedule(a: Appointment) {
    return this.auth.can('CITAS_GESTIONAR') && ['PROGRAMADA', 'CONFIRMADA', 'EN_ESPERA'].includes(a.status);
  }

  nextStates(s: string): string[] {
    const map: Record<string, string[]> = {
      PROGRAMADA: ['CONFIRMADA', 'EN_ESPERA', 'NO_ASISTIO', 'CANCELADA'],
      CONFIRMADA: ['EN_ESPERA', 'NO_ASISTIO', 'CANCELADA'],
      EN_ESPERA: ['EN_ATENCION', 'CANCELADA'],
      EN_ATENCION: ['ATENDIDA'],
    };
    return map[s] || [];
  }

  primaryNext(s: string): string {
    return ({ PROGRAMADA: 'EN_ESPERA', CONFIRMADA: 'EN_ESPERA', EN_ESPERA: 'EN_ATENCION', EN_ATENCION: 'ATENDIDA' } as Record<string, string>)[s] || '';
  }

  actionLabel(n: string): string {
    return ({
      CONFIRMADA: 'Confirmar asistencia', EN_ESPERA: 'Registrar llegada', EN_ATENCION: 'Pasar a atención',
      ATENDIDA: 'Marcar atendida', NO_ASISTIO: 'No asistió', CANCELADA: 'Cancelar cita',
    } as Record<string, string>)[n] || n;
  }

  act(n: string) {
    if (n === 'CANCELADA') this.cancelling = true;
    else this.change(n);
  }

  selInfo: any = null;
  mine(a: Appointment) { const me = this.auth.user(); return me?.role === 'ODONTOLOGO' && me.dentistId === a.dentist.id; }

  att: any = null;
  money = soles;

  open(a: Appointment) {
    this.sel = a;
    this.selInfo = null;
    if (this.auth.can('HISTORIA_CLINICA') || this.auth.can('PACIENTES_EDITAR')) {
      this.api.patient(a.patient.id).subscribe({ next: (r) => (this.selInfo = r), error: () => {} });
    }
    this.cancelling = false;
    this.reason = '';
    this.history = [];
    this.att = null;
    this.api.appointmentAudit(a.id).subscribe((h) => (this.history = h));
    if (['EN_ATENCION', 'ATENDIDA'].includes(a.status)) this.api.appointmentAttention(a.id).subscribe((r) => (this.att = r));
  }

  change(status: string) {
    if (!this.sel) return;
    this.busy = true;
    this.api.setStatus(this.sel.id, status, status === 'CANCELADA' ? this.reason : undefined).subscribe({
      next: (a) => {
        this.busy = false;
        this.cancelling = false;
        this.toast.ok(`${a.patient.fullName}: ${this.label(a.status).toLowerCase()}.`);
        this.sel = a;
        this.api.appointmentAudit(a.id).subscribe((h) => (this.history = h));
        this.load();
      },
      error: (e) => { this.busy = false; this.toast.error(apiError(e).message); },
    });
  }

  reminding = false;

  /** HU-23: recordatorio manual. Si ya se envió para esta cita y hora, el sistema no lo duplica. */
  remind() {
    if (!this.sel) return;
    this.reminding = true;
    this.api.remind(this.sel.id).subscribe({
      next: (r) => { this.reminding = false; if (r.queued > 0) this.toast.ok(r.message); else this.toast.warn(r.message); },
      error: (e) => { this.reminding = false; this.toast.error(apiError(e).message); },
    });
  }

  channelLabel(c?: string) { return ({ WHATSAPP: 'WhatsApp', EMAIL: 'correo', AMBOS: 'WhatsApp y correo', NINGUNO: 'ningún canal (el paciente no recibe avisos)' } as Record<string, string>)[c || 'WHATSAPP']; }

  label(s: string) { return STATUS_LABEL[s] || s; }
  tone(s: string) { return STATUS_TONE[s] || 'grey'; }
  hhmm = hhmm;
  dt = dateTime;

  ngOnDestroy() { clearInterval(this.timer); }
}
