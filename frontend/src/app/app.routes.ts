import { Routes } from '@angular/router';
import { authGuard, guestGuard, roleGuard } from './core/guards';
import { ShellComponent } from './layout/shell.component';

export const routes: Routes = [
  { path: 'login', canActivate: [guestGuard], loadComponent: () => import('./pages/login.component').then((m) => m.LoginComponent), title: 'Iniciar sesión · Clínica Cárdenas' },
  { path: 'recuperar', canActivate: [guestGuard], loadComponent: () => import('./pages/recover.component').then((m) => m.RecoverComponent), title: 'Recuperar contraseña · Clínica Cárdenas' },
  {
    path: '',
    component: ShellComponent,
    canActivate: [authGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'inicio' },
      { path: 'inicio', loadComponent: () => import('./pages/dashboard.component').then((m) => m.DashboardComponent), title: 'Inicio · Clínica Cárdenas' },
      {
        path: 'agenda', canActivate: [roleGuard('ADMIN', 'RECEPCION', 'ODONTOLOGO', 'DIRECTOR')],
        loadComponent: () => import('./pages/agenda.component').then((m) => m.AgendaComponent), title: 'Agenda · Clínica Cárdenas',
      },
      {
        path: 'citas/nueva', canActivate: [roleGuard('ADMIN', 'RECEPCION')],
        loadComponent: () => import('./pages/appointment-new.component').then((m) => m.AppointmentNewComponent), title: 'Nueva cita · Clínica Cárdenas',
      },
      { path: 'pacientes', loadComponent: () => import('./pages/patients.component').then((m) => m.PatientsComponent), title: 'Pacientes · Clínica Cárdenas' },
      {
        path: 'pacientes/nuevo', canActivate: [roleGuard('ADMIN', 'RECEPCION')],
        loadComponent: () => import('./pages/patient-form.component').then((m) => m.PatientFormComponent), title: 'Registrar paciente · Clínica Cárdenas',
      },
      {
        path: 'pacientes/:id/editar', canActivate: [roleGuard('ADMIN', 'RECEPCION')],
        loadComponent: () => import('./pages/patient-form.component').then((m) => m.PatientFormComponent), title: 'Editar paciente · Clínica Cárdenas',
      },
      { path: 'pacientes/:id', loadComponent: () => import('./pages/patient-detail.component').then((m) => m.PatientDetailComponent), title: 'Ficha del paciente · Clínica Cárdenas' },
      {
        path: 'comprobantes', canActivate: [roleGuard('ADMIN', 'CAJA')],
        loadComponent: () => import('./pages/invoices.component').then((m) => m.InvoicesComponent), title: 'Comprobantes · Clínica Cárdenas',
      },
      {
        path: 'comprobantes/nuevo', canActivate: [roleGuard('ADMIN', 'CAJA')],
        loadComponent: () => import('./pages/invoice-new.component').then((m) => m.InvoiceNewComponent), title: 'Nuevo comprobante · Clínica Cárdenas',
      },
      {
        path: 'comprobantes/:id', canActivate: [roleGuard('ADMIN', 'CAJA')],
        loadComponent: () => import('./pages/invoice-detail.component').then((m) => m.InvoiceDetailComponent), title: 'Comprobante · Clínica Cárdenas',
      },
      {
        path: 'usuarios', canActivate: [roleGuard('ADMIN')],
        loadComponent: () => import('./pages/users.component').then((m) => m.UsersComponent), title: 'Usuarios · Clínica Cárdenas',
      },
      { path: 'sin-permiso', loadComponent: () => import('./pages/forbidden.component').then((m) => m.ForbiddenComponent), title: 'Sin permiso · Clínica Cárdenas' },
    ],
  },
  { path: '**', redirectTo: '' },
];
