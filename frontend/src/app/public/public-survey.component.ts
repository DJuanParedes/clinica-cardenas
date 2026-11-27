import { Component, Input, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Api } from '../core/api.service';
import { apiError, longDay } from '../core/format';
import { LogoComponent } from '../shared/logo.component';
import { PUBLIC_STYLES } from './public-styles';

import { OnlyDirective } from '../shared/only.directive';
/** v2.6: encuesta de satisfacción desde el enlace del mensaje (sin iniciar sesión). */
@Component({
  selector: 'app-public-survey',
  standalone: true,
  imports: [OnlyDirective, FormsModule, LogoComponent],
  template: `
    <header class="top"><app-logo [size]="44" /><div><div class="name">{{ s?.clinic || 'Clínica Dental' }}</div></div></header>
    <main>
      @if (error && !s) { <div class="card done"><div class="big">🔗</div><h1>Enlace no válido</h1><p class="lead">{{ error }}</p></div> }
      @else if (!s) { <div class="card"><div class="skeleton" style="height: 160px"></div></div> }
      @else if (msg || s.rated) { <div class="card done"><div class="big">💙</div><h1>¡Gracias, {{ s.firstName }}!</h1><p class="lead">{{ msg || 'Ya recibimos tu calificación.' }}</p></div> }
      @else {
        <div class="card">
          <h1>¿Cómo te atendimos, {{ s.firstName }}?</h1>
          <p class="lead">{{ s.reason }} con {{ s.dentist }} · {{ longDay(s.date) }}</p>
          <div class="stars" role="radiogroup" aria-label="Calificación">
            @for (n of [1,2,3,4,5]; track n) { <button type="button" [class.on]="score >= n" (click)="score = n" [attr.aria-label]="n + ' estrellas'">★</button> }
          </div>
          <p class="center muted">{{ ['', 'Muy mala', 'Mala', 'Regular', 'Buena', '¡Excelente!'][score] }}</p>
          <textarea class="input" rows="3" [(ngModel)]="comment" maxlength="500" placeholder="¿Algo que quieras contarnos? (opcional)" aria-label="Comentario" appOnly="text"></textarea>
          @if (error) { <div class="err">{{ error }}</div> }
          <button class="btn primary block" type="button" [disabled]="!score || busy" (click)="send()">Enviar calificación</button>
        </div>
      }
    </main>
  `,
  styles: [PUBLIC_STYLES + `
    .stars { display: flex; justify-content: center; gap: 6px; }
    .stars button { font-size: 42px; line-height: 1; background: none; border: 0; color: #d3dde2; cursor: pointer; padding: 0 2px; }
    .stars button.on { color: #f8a51c; }
    .center { text-align: center; margin: 0; }
  `],
})
export class PublicSurveyComponent implements OnInit {
  @Input() token = '';
  private api = inject(Api);
  s: any = null;
  score = 0;
  comment = '';
  busy = false;
  error = '';
  msg = '';
  ngOnInit() { this.api.publicSurvey(this.token).subscribe({ next: (s) => (this.s = s), error: (err) => (this.error = apiError(err).message) }); }
  send() {
    this.busy = true; this.error = '';
    this.api.publicRate(this.token, { score: this.score, comment: this.comment }).subscribe({
      next: (r) => { this.busy = false; this.msg = r.message; }, error: (err) => { this.busy = false; this.error = apiError(err).message; },
    });
  }
  longDay = longDay;
}
