import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';
import { UserRole } from './models';

export const authGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return auth.authenticated() ? true : router.createUrlTree(['/login']);
};

const RECEPTION_ALLOWED_PATHS = new Set([
  '/app/dashboard','/app/recepcion-hoy','/app/pacientes','/app/paciente-360','/app/citas','/app/agenda-diaria','/app/tratamientos-proformas','/app/mapa-sillones',
  '/app/solicitudes-web','/app/caja-pagos','/app/satisfaccion','/app/laboratorio',
  '/app/reportes','/app/reclamos','/app/inventario','/app/no-autorizado'
]);

export const roleGuard: CanActivateFn = (route, state) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const role = auth.user()?.role;
  const path = state.url.split('?')[0].replace(/\/$/, '') || '/app/dashboard';
  if (role === 'RECEPCION' && !RECEPTION_ALLOWED_PATHS.has(path)) {
    return router.createUrlTree(['/app/no-autorizado']);
  }
  const roles = (route.data['roles'] ?? []) as UserRole[];
  return roles.length === 0 || auth.hasRole(roles)
    ? true
    : router.createUrlTree(['/app/no-autorizado']);
};
