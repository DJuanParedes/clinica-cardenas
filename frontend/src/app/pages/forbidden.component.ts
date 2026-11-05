import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-forbidden',
  standalone: true,
  imports: [RouterLink],
  template: `
    <div class="page">
      <div class="panel empty">
        <h3>Esta sección no está disponible para tu rol</h3>
        <p>Si necesitas acceso, pídelo a la administración de la clínica.</p>
        <a class="btn" routerLink="/inicio">Ir al inicio</a>
      </div>
    </div>
  `,
})
export class ForbiddenComponent {}
