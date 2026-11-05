import { Component, EventEmitter, Input, OnDestroy, Output, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Subject, Subscription, debounceTime, distinctUntilChanged, switchMap, of, catchError } from 'rxjs';
import { Api } from '../core/api.service';
import { Patient } from '../core/models';
import { IconComponent } from './icon.component';
import { initials } from '../core/format';

/** Buscador de pacientes reutilizable (HU-02) para cita y comprobante. */
@Component({
  selector: 'app-patient-picker',
  standalone: true,
  imports: [FormsModule, IconComponent, RouterLink],
  template: `
    @if (value) {
      <div class="picked">
        <span class="avatar">{{ ini(value.fullName) }}</span>
        <div class="grow">
          <div class="strong">{{ value.fullName }}</div>
          <div class="muted small">{{ value.docType }} {{ value.docNumber }} · {{ value.age }} años · {{ value.phone }}</div>
        </div>
        @if (!locked) { <button type="button" class="btn sm" (click)="clear()">Cambiar</button> }
      </div>
    } @else {
      <div class="search-box">
        <app-icon class="ico" name="search" />
        <input class="input" [(ngModel)]="q" (ngModelChange)="input$.next($event)" [placeholder]="placeholder"
               autocomplete="off" aria-label="Buscar paciente por documento o nombre" />
      </div>
      @if (q.trim().length >= 2) {
        <div class="results">
          @if (loading) { <div class="row muted small pad">Buscando…</div> }
          @for (p of results; track p.id) {
            <button type="button" class="res" (click)="choose(p)">
              <span class="avatar">{{ ini(p.fullName) }}</span>
              <span class="grow">
                <span class="strong">{{ p.fullName }}</span>
                <span class="muted small block">{{ p.docType }} {{ p.docNumber }} · {{ p.age }} años</span>
              </span>
            </button>
          }
          @if (!loading && results.length === 0) {
            <div class="pad small">
              <span class="muted">No hay pacientes con "{{ q }}".</span>
              <a routerLink="/pacientes/nuevo" [queryParams]="{ doc: q }"> Registrar paciente nuevo</a>
            </div>
          }
        </div>
      }
    }
  `,
  styles: [`
    .picked { display: flex; align-items: center; gap: 12px; padding: 12px 14px; border: 1px solid #bfdcd8; background: var(--teal-soft); border-radius: var(--radius); }
    .results { margin-top: 6px; border: 1px solid var(--line); border-radius: var(--radius); background: var(--surface); max-height: 300px; overflow: auto; }
    .res { display: flex; align-items: center; gap: 12px; width: 100%; padding: 10px 14px; background: none; border: 0; border-bottom: 1px solid var(--line); text-align: left; font: inherit; cursor: pointer; color: inherit; }
    .res:last-child { border-bottom: 0; }
    .res:hover, .res:focus-visible { background: #f2f8f7; }
    .block { display: block; }
    .pad { padding: 12px 14px; }
  `],
})
export class PatientPickerComponent implements OnDestroy {
  private api = inject(Api);

  @Input() value: Patient | null = null;
  @Input() locked = false;
  @Input() placeholder = 'DNI, carné o nombre del paciente';
  @Output() valueChange = new EventEmitter<Patient | null>();

  q = '';
  results: Patient[] = [];
  loading = false;
  input$ = new Subject<string>();
  private sub: Subscription;

  constructor() {
    this.sub = this.input$.pipe(
      debounceTime(250),
      distinctUntilChanged(),
      switchMap((q) => {
        if (q.trim().length < 2) { this.results = []; return of(null); }
        this.loading = true;
        return this.api.searchPatients(q.trim(), 0, 8).pipe(catchError(() => of(null)));
      }),
    ).subscribe((page) => {
      this.loading = false;
      if (page) this.results = page.content;
    });
  }

  ini(n: string) { return initials(n); }

  choose(p: Patient) {
    this.value = p;
    this.valueChange.emit(p);
    this.q = '';
    this.results = [];
  }

  clear() {
    this.value = null;
    this.valueChange.emit(null);
  }

  ngOnDestroy() { this.sub.unsubscribe(); }
}
