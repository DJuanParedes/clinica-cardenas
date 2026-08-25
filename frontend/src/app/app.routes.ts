import { Routes } from '@angular/router';
import { HomeComponent } from './pages/home.component';
import { PlatformHomeComponent } from './pages/platform-home.component';
import { LoginComponent } from './pages/login.component';
import { PasswordResetComponent } from './pages/password-reset.component';
import { ShellComponent } from './layout/shell.component';
import { DashboardComponent } from './pages/dashboard.component';
import { ReceptionDeskComponent } from './pages/reception-desk.component';
import { PatientsComponent } from './pages/patients.component';
import { AppointmentsComponent } from './pages/appointments.component';
import { DailyChairAgendaComponent } from './pages/daily-chair-agenda.component';
import { TreatmentProformasComponent } from './pages/treatment-proformas.component';
import { EmergenciesComponent } from './pages/emergencies.component';
import { OdontogramComponent } from './pages/odontogram.component';
import { BudgetsComponent } from './pages/budgets.component';
import { ReportsComponent } from './pages/reports.component';
import { GenericModuleComponent } from './pages/generic-module.component';
import { UnauthorizedComponent } from './pages/unauthorized.component';
import { UsersComponent } from './pages/users.component';
import { SchedulingResourcesComponent } from './pages/scheduling-resources.component';
import { InventoryComponent } from './pages/inventory.component';
import { CashPaymentsComponent } from './pages/cash-payments.component';
import { FinanceDashboardComponent } from './pages/finance-dashboard.component';
import { DocumentsComponent } from './pages/documents.component';
import { Patient360Component } from './pages/patient-360.component';
import { EncountersComponent } from './pages/encounters.component';
import { ChairMapComponent } from './pages/chair-map.component';
import { ChairAnalyticsComponent } from './pages/chair-analytics.component';
import { SurveyPublicComponent } from './pages/survey-public.component';
import { SurveyResultsComponent } from './pages/survey-results.component';
import { GovernanceComponent } from './pages/governance.component';
import { AdminFinancialSettingsComponent } from './pages/admin-financial-settings.component';
import { BackupCenterComponent } from './pages/backup-center.component';
import { ClinicsComponent } from './pages/clinics.component';
import { authGuard, roleGuard } from './core/guards';
import { MODULES } from './core/module-definitions';

const genericRoutes: Routes = MODULES.map(config => ({
  path: config.route.replace('/app/', ''),
  component: GenericModuleComponent,
  canActivate: [roleGuard],
  data: { roles: config.roles, config }
}));

export const routes: Routes = [
  { path: '', component: PlatformHomeComponent },
  { path: 'marca/:clinicSlug', component: HomeComponent },
  { path: 'login', component: LoginComponent },
  { path: 'recuperar-contrasena', component: PasswordResetComponent },
  { path: 'encuesta', component: SurveyPublicComponent },
  { path: 'marca/:clinicSlug/encuesta', component: SurveyPublicComponent },
  {
    path: 'app', component: ShellComponent, canActivate: [authGuard], children: [
      { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
      { path: 'dashboard', component: DashboardComponent },
      { path: 'recepcion-hoy', component: ReceptionDeskComponent, canActivate: [roleGuard], data: { roles: ['ADMIN','RECEPCION'] } },
      { path: 'pacientes', component: PatientsComponent, canActivate: [roleGuard], data: { roles: ['ADMIN','DOCTOR','RECEPCION'] } },
      { path: 'citas', component: AppointmentsComponent, canActivate: [roleGuard], data: { roles: ['ADMIN','DOCTOR','RECEPCION'] } },
      { path: 'agenda-diaria', component: DailyChairAgendaComponent, canActivate: [roleGuard], data: { roles: ['ADMIN','DOCTOR','RECEPCION'] } },
      { path: 'tratamientos-proformas', component: TreatmentProformasComponent, canActivate: [roleGuard], data: { roles: ['ADMIN','DOCTOR','RECEPCION','FINANZAS'] } },
      { path: 'emergencias', component: EmergenciesComponent, canActivate: [roleGuard], data: { roles: ['ADMIN','DOCTOR'] } },
      { path: 'odontograma', component: OdontogramComponent, canActivate: [roleGuard], data: { roles: ['ADMIN','DOCTOR'] } },
      { path: 'presupuestos', component: BudgetsComponent, canActivate: [roleGuard], data: { roles: ['ADMIN','DOCTOR','FINANZAS'] } },
      { path: 'reportes', component: ReportsComponent, canActivate: [roleGuard], data: { roles: ['ADMIN','DOCTOR','RECEPCION','FINANZAS'] } },
      { path: 'configuracion-tratamientos-comisiones', component: AdminFinancialSettingsComponent, canActivate: [roleGuard], data: { roles: ['ADMIN'] } },
      { path: 'usuarios', component: UsersComponent, canActivate: [roleGuard], data: { roles: ['ADMIN'] } },
      { path: 'clinicas', component: ClinicsComponent, canActivate: [roleGuard], data: { roles: ['ADMIN'] } },
      { path: 'recursos-agenda', component: SchedulingResourcesComponent, canActivate: [roleGuard], data: { roles: ['ADMIN','DOCTOR'] } },
      { path: 'inventario', component: InventoryComponent, canActivate: [roleGuard], data: { roles: ['ADMIN','DOCTOR','RECEPCION','FINANZAS'] } },
      { path: 'caja-pagos', component: CashPaymentsComponent, canActivate: [roleGuard], data: { roles: ['ADMIN','RECEPCION','FINANZAS'] } },
      { path: 'finanzas', component: FinanceDashboardComponent, canActivate: [roleGuard], data: { roles: ['ADMIN','FINANZAS'] } },
      { path: 'documentos', component: DocumentsComponent, canActivate: [roleGuard], data: { roles: ['ADMIN','DOCTOR'] } },
      { path: 'paciente-360', component: Patient360Component, canActivate: [roleGuard], data: { roles: ['ADMIN','DOCTOR','RECEPCION','FINANZAS'] } },
      { path: 'atenciones', component: EncountersComponent, canActivate: [roleGuard], data: { roles: ['ADMIN','DOCTOR','FINANZAS'] } },
      { path: 'mapa-sillones', component: ChairMapComponent, canActivate: [roleGuard], data: { roles: ['ADMIN','RECEPCION','FINANZAS'] } },
      { path: 'analisis-sillones', component: ChairAnalyticsComponent, canActivate: [roleGuard], data: { roles: ['ADMIN','FINANZAS'] } },
      { path: 'satisfaccion', component: SurveyResultsComponent, canActivate: [roleGuard], data: { roles: ['ADMIN','DOCTOR','RECEPCION','FINANZAS'] } },
      { path: 'gobierno', component: GovernanceComponent, canActivate: [roleGuard], data: { roles: ['ADMIN','FINANZAS'] } },
      { path: 'copias-importacion', component: BackupCenterComponent, canActivate: [roleGuard], data: { roles: ['ADMIN'] } },
      ...genericRoutes,
      { path: 'no-autorizado', component: UnauthorizedComponent }
    ]
  },
  { path: '**', redirectTo: '' }
];
