import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';
import { PortalAuth } from './portal-auth.service';
import { Role } from './models';

export const authGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  return auth.isLoggedIn() ? true : inject(Router).parseUrl('/login');
};

export const guestGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  return auth.isLoggedIn() ? inject(Router).parseUrl(auth.home()) : true;
};

/** Protección de pantallas por permiso (HU-26). */
export function permGuard(...perms: string[]): CanActivateFn {
  return () => {
    const auth = inject(AuthService);
    if (!auth.isLoggedIn()) return inject(Router).parseUrl('/login');
    return auth.can(...perms) ? true : inject(Router).parseUrl('/sin-permiso');
  };
}

/** Protección de pantallas por rol (HU-25). */
export function roleGuard(...roles: Role[]): CanActivateFn {
  return () => {
    const auth = inject(AuthService);
    if (!auth.isLoggedIn()) return inject(Router).parseUrl('/login');
    return auth.hasRole(...roles) ? true : inject(Router).parseUrl('/sin-permiso');
  };
}

/** HU-22: pantallas de la app del paciente. */
export const portalGuard: CanActivateFn = () => {
  const portal = inject(PortalAuth);
  return portal.isLoggedIn() ? true : inject(Router).parseUrl('/app/ingresar');
};

export const portalGuestGuard: CanActivateFn = () => {
  const portal = inject(PortalAuth);
  return portal.isLoggedIn() ? inject(Router).parseUrl('/app') : true;
};
