import { Component, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { backendPending, pendingMessage } from './core/deployment';
import { ClinicConfigService } from './core/clinic-config.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet],
  template: '@if (backendPending) { <aside role="status" style="background:#fff3cd;color:#553f00;padding:14px 24px;text-align:center">{{pendingMessage}}</aside> }<router-outlet />'
})
export class AppComponent {
  readonly backendPending = backendPending;
  readonly pendingMessage = pendingMessage;
  private readonly clinicConfig = inject(ClinicConfigService);
}

