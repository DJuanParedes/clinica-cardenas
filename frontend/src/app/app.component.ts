import { Component, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { ClinicConfigService } from './core/clinic-config.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet],
  template: '<router-outlet />'
})
export class AppComponent {
  private readonly clinicConfig = inject(ClinicConfigService);
}
