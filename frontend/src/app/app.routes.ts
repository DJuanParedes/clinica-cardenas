import { Routes } from '@angular/router';
import { authGuard, guestGuard, permGuard, portalGuard, portalGuestGuard } from './core/guards';
import { ShellComponent } from './layout/shell.component';

const T = (s: string) => `${s} · Clínica Cárdenas`;

export const routes: Routes = [
  // ---------- HU-22: aplicación móvil del paciente
  { path: 'app/ingresar', canActivate: [portalGuestGuard], loadComponent: () => import('./portal/portal-login.component').then((m) => m.PortalLoginComponent), title: T('Mi app') },
  {
    path: 'app',
    canActivate: [portalGuard],
    loadComponent: () => import('./portal/portal-shell.component').then((m) => m.PortalShellComponent),
    children: [
      { path: '', loadComponent: () => import('./portal/portal-home.component').then((m) => m.PortalHomeComponent), title: T('Mis citas') },
      { path: 'cuenta', loadComponent: () => import('./portal/portal-account.component').then((m) => m.PortalAccountComponent), title: T('Mi cuenta') },
      { path: 'perfil', loadComponent: () => import('./portal/portal-profile.component').then((m) => m.PortalProfileComponent), title: T('Mi perfil') },
    ],
  },

  // ---------- personal de la clínica
  { path: 'login', canActivate: [guestGuard], loadComponent: () => import('./pages/login.component').then((m) => m.LoginComponent), title: T('Iniciar sesión') },
  { path: 'recuperar', canActivate: [guestGuard], loadComponent: () => import('./pages/recover.component').then((m) => m.RecoverComponent), title: T('Recuperar contraseña') },
  {
    path: '',
    component: ShellComponent,
    canActivate: [authGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'inicio' },
      { path: 'inicio', loadComponent: () => import('./pages/dashboard.component').then((m) => m.DashboardComponent), title: T('Inicio') },
      { path: 'agenda', canActivate: [permGuard('AGENDA_VER')], loadComponent: () => import('./pages/agenda.component').then((m) => m.AgendaComponent), title: T('Agenda') },
      { path: 'citas/nueva', canActivate: [permGuard('CITAS_GESTIONAR')], loadComponent: () => import('./pages/appointment-new.component').then((m) => m.AppointmentNewComponent), title: T('Nueva cita') },
      { path: 'pacientes', canActivate: [permGuard('PACIENTES_VER')], loadComponent: () => import('./pages/patients.component').then((m) => m.PatientsComponent), title: T('Pacientes') },
      { path: 'pacientes/nuevo', canActivate: [permGuard('PACIENTES_EDITAR')], loadComponent: () => import('./pages/patient-form.component').then((m) => m.PatientFormComponent), title: T('Registrar paciente') },
      { path: 'pacientes/:id/editar', canActivate: [permGuard('PACIENTES_EDITAR')], loadComponent: () => import('./pages/patient-form.component').then((m) => m.PatientFormComponent), title: T('Editar paciente') },
      { path: 'pacientes/:id', canActivate: [permGuard('PACIENTES_VER')], loadComponent: () => import('./pages/patient-detail.component').then((m) => m.PatientDetailComponent), title: T('Ficha del paciente') },
      { path: 'caja', canActivate: [permGuard('CAJA_COBRAR', 'CAJA_EGRESOS', 'CAJA_REPORTE')], loadComponent: () => import('./pages/cash.component').then((m) => m.CashComponent), title: T('Caja del día') },
      { path: 'caja/cobro', canActivate: [permGuard('CAJA_COBRAR')], loadComponent: () => import('./pages/payment-new.component').then((m) => m.PaymentNewComponent), title: T('Registrar cobro') },
      { path: 'caja/reporte', canActivate: [permGuard('CAJA_REPORTE')], loadComponent: () => import('./pages/cash-report.component').then((m) => m.CashReportComponent), title: T('Flujo de caja') },
      { path: 'comprobantes', canActivate: [permGuard('COMPROBANTES')], loadComponent: () => import('./pages/invoices.component').then((m) => m.InvoicesComponent), title: T('Comprobantes') },
      { path: 'comprobantes/nuevo', canActivate: [permGuard('COMPROBANTES')], loadComponent: () => import('./pages/invoice-new.component').then((m) => m.InvoiceNewComponent), title: T('Nuevo comprobante') },
      { path: 'comprobantes/:id', canActivate: [permGuard('COMPROBANTES')], loadComponent: () => import('./pages/invoice-detail.component').then((m) => m.InvoiceDetailComponent), title: T('Comprobante') },
      { path: 'comisiones', canActivate: [permGuard('COMISIONES')], loadComponent: () => import('./pages/commissions.component').then((m) => m.CommissionsComponent), title: T('Comisiones') },
      { path: 'reportes', canActivate: [permGuard('REPORTES')], loadComponent: () => import('./pages/reports.component').then((m) => m.ReportsComponent), title: T('Indicadores') },
      { path: 'notificaciones', canActivate: [permGuard('NOTIFICACIONES')], loadComponent: () => import('./pages/notifications.component').then((m) => m.NotificationsComponent), title: T('Notificaciones') },
      { path: 'usuarios', canActivate: [permGuard('USUARIOS')], loadComponent: () => import('./pages/users.component').then((m) => m.UsersComponent), title: T('Usuarios') },
      { path: 'roles', canActivate: [permGuard('ROLES')], loadComponent: () => import('./pages/roles.component').then((m) => m.RolesComponent), title: T('Roles y permisos') },
      { path: 'inventario', canActivate: [permGuard('INVENTARIO')], loadComponent: () => import('./pages/inventory.component').then((m) => m.InventoryComponent), title: T('Inventario') },
      { path: 'seguimiento', canActivate: [permGuard('AGENDA_VER')], loadComponent: () => import('./pages/followup.component').then((m) => m.FollowupComponent), title: T('Seguimiento') },
      { path: 'seguridad', loadComponent: () => import('./pages/security.component').then((m) => m.SecurityComponent), title: T('Mi seguridad') },
      { path: 'sin-permiso', loadComponent: () => import('./pages/forbidden.component').then((m) => m.ForbiddenComponent), title: T('Sin permiso') },
    ],
  },
  { path: '**', redirectTo: '' },
];
