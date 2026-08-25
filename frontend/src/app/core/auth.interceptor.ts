import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { Router } from '@angular/router';
import { AuthService } from './auth.service';
import { ClinicConfigService } from './clinic-config.service';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const clinic = inject(ClinicConfigService);
  const token = auth.token();
  const headers: Record<string,string> = { 'X-Clinic-Id': clinic.config().id };
  if (token) headers['Authorization'] = `Bearer ${token}`;
  const request = req.clone({ setHeaders: headers });

  return next(request).pipe(
    catchError((error: unknown) => {
      const status = (error as { status?: number }).status;
      if (status === 401) {
        auth.logout();
      } else if (status === 403) {
        void router.navigateByUrl('/app/no-autorizado');
      }
      return throwError(() => error);
    })
  );
};
