import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  Appointment, AuditEntry, ClinicalHistory, Company, Dentist, Diagnosis, Invoice, InvoiceItem, OdontogramEntry,
  Page, Patient, PlanView, Procedure, Slot, Block, Submission,
} from './models';

function params(obj: Record<string, any>): HttpParams {
  let p = new HttpParams();
  for (const [k, v] of Object.entries(obj)) {
    if (v !== null && v !== undefined && v !== '') p = p.set(k, String(v));
  }
  return p;
}

@Injectable({ providedIn: 'root' })
export class Api {
  private http = inject(HttpClient);

  // ---------- inicio
  dashboard(): Observable<any> { return this.http.get('/api/dashboard'); }

  // ---------- pacientes (HU-01, HU-02, HU-03)
  // v2.5: ficha ampliada (captación, etiquetas, N° HC, datos fiscales, familiares)
  patientProfileOptions(): Observable<{ sources: { code: string; label: string }[]; relationships: string[]; tags: { tag: string; patients: number }[] }> {
    return this.http.get<any>('/api/patients/profile-options');
  }
  patientProfile(id: number): Observable<PatientProfile> { return this.http.get<PatientProfile>(`/api/patients/${id}/profile`); }
  updatePatientProfile(id: number, body: any): Observable<PatientProfile> { return this.http.put<PatientProfile>(`/api/patients/${id}/profile`, body); }
  patientRuc(ruc: string): Observable<RucInfo> { return this.http.get<RucInfo>(`/api/patients/ruc/${ruc}`); }
  addRelation(id: number, body: any): Observable<PatientProfile> { return this.http.post<PatientProfile>(`/api/patients/${id}/relations`, body); }
  updateRelation(id: number, relId: number, body: any): Observable<PatientProfile> { return this.http.put<PatientProfile>(`/api/patients/${id}/relations/${relId}`, body); }
  deleteRelation(id: number, relId: number): Observable<PatientProfile> { return this.http.delete<PatientProfile>(`/api/patients/${id}/relations/${relId}`); }

  // v2.5: anamnesis y signos vitales
  anamnesisTemplates(all = false): Observable<any[]> { return this.http.get<any[]>('/api/anamnesis/templates', { params: params({ all: all || undefined }) }); }
  createAnamnesisTemplate(body: any): Observable<any> { return this.http.post('/api/anamnesis/templates', body); }
  updateAnamnesisTemplate(id: number, body: any): Observable<any> { return this.http.put(`/api/anamnesis/templates/${id}`, body); }
  patientAnamnesis(id: number): Observable<{ records: any[]; alerts: string[] }> { return this.http.get<any>(`/api/patients/${id}/anamnesis`); }
  addAnamnesis(id: number, body: any): Observable<{ records: any[]; alerts: string[] }> { return this.http.post<any>(`/api/patients/${id}/anamnesis`, body); }
  updateAnamnesis(id: number, recordId: number, body: any): Observable<{ records: any[]; alerts: string[] }> { return this.http.put<any>(`/api/patients/${id}/anamnesis/${recordId}`, body); }
  vitals(id: number): Observable<any[]> { return this.http.get<any[]>(`/api/patients/${id}/vitals`); }
  addVitals(id: number, body: any): Observable<any[]> { return this.http.post<any[]>(`/api/patients/${id}/vitals`, body); }
  deleteVitals(id: number, vitalId: number): Observable<any[]> { return this.http.delete<any[]>(`/api/patients/${id}/vitals/${vitalId}`); }

  // v2.5: periodontograma
  periodontograms(id: number): Observable<any[]> { return this.http.get<any[]>(`/api/patients/${id}/periodontograms`); }
  periodontogram(id: number, examId: number): Observable<any> { return this.http.get(`/api/patients/${id}/periodontograms/${examId}`); }
  createPeriodontogram(id: number, body: any): Observable<any> { return this.http.post(`/api/patients/${id}/periodontograms`, body); }
  updatePeriodontogram(id: number, examId: number, body: any): Observable<any> { return this.http.put(`/api/patients/${id}/periodontograms/${examId}`, body); }

  // v2.5: laboratorio dental
  labOptions(): Observable<any> { return this.http.get('/api/lab-options'); }
  laboratories(all = false): Observable<any[]> { return this.http.get<any[]>('/api/laboratories', { params: params({ all: all || undefined }) }); }
  createLaboratory(body: any): Observable<any> { return this.http.post('/api/laboratories', body); }
  updateLaboratory(id: number, body: any): Observable<any> { return this.http.put(`/api/laboratories/${id}`, body); }
  labOrders(f: { status?: string; laboratoryId?: number | null; patientId?: number; overdue?: boolean; unpaid?: boolean; q?: string }): Observable<{ items: any[]; summary: any }> {
    return this.http.get<any>('/api/lab-orders', { params: params({ status: f.status, laboratoryId: f.laboratoryId, patientId: f.patientId, overdue: f.overdue || undefined, unpaid: f.unpaid || undefined, q: f.q }) });
  }
  labOrder(id: number): Observable<any> { return this.http.get(`/api/lab-orders/${id}`); }
  createLabOrder(body: any): Observable<any> { return this.http.post('/api/lab-orders', body); }
  updateLabOrder(id: number, body: any): Observable<any> { return this.http.put(`/api/lab-orders/${id}`, body); }
  labOrderStatus(id: number, body: any): Observable<any> { return this.http.post(`/api/lab-orders/${id}/status`, body); }
  payLabOrder(id: number, body: any): Observable<any> { return this.http.post(`/api/lab-orders/${id}/payments`, body); }

  // v2.6: comunicación con el paciente
  messageTemplates(): Observable<{ templates: any[]; variables: string[] }> { return this.http.get<any>('/api/messaging/templates'); }
  updateTemplate(code: string, body: any): Observable<any> { return this.http.put(`/api/messaging/templates/${code}`, body); }
  restoreTemplate(code: string): Observable<any> { return this.http.post(`/api/messaging/templates/${code}/restore`, {}); }
  previewTemplate(code: string, body: any): Observable<any> { return this.http.post(`/api/messaging/templates/${code}/preview`, body); }
  automations(): Observable<any[]> { return this.http.get<any[]>('/api/messaging/automations'); }
  updateAutomation(code: string, body: any): Observable<any> { return this.http.put(`/api/messaging/automations/${code}`, body); }
  messagingHistory(f: { template?: string; status?: string; q?: string }): Observable<any[]> {
    return this.http.get<any[]>('/api/messaging/history', { params: params({ template: f.template, status: f.status, q: f.q }) });
  }
  publicLinks(): Observable<any> { return this.http.get('/api/messaging/public-links'); }
  updatePublicLinks(body: any): Observable<any> { return this.http.put('/api/messaging/public-links', body); }
  publicBookingOptions(): Observable<any> { return this.http.get('/api/public/booking/options'); }
  publicSlots(dentistId: number, date: string, reasonCode: string): Observable<{ slots: string[] }> {
    return this.http.get<any>('/api/public/booking/slots', { params: params({ dentistId, date, reasonCode }) });
  }
  publicBook(body: any): Observable<any> { return this.http.post('/api/public/booking', body); }
  publicPreregisterForm(): Observable<any> { return this.http.get('/api/public/preregistro'); }
  publicPreregister(body: any): Observable<any> { return this.http.post('/api/public/preregistro', body); }
  publicSurvey(token: string): Observable<any> { return this.http.get(`/api/public/survey/${token}`); }
  publicRate(token: string, body: any): Observable<any> { return this.http.post(`/api/public/survey/${token}`, body); }

  searchPatientsFiltered(q: string, page: number, size: number, filters: { tag?: string; source?: string; mine?: boolean; task?: string; next?: string; budget?: string; debt?: boolean; visit?: string }): Observable<Page<Patient> & { insights?: Record<string, any> }> {
    return this.http.get<any>('/api/patients', { params: params({ q, page, size, mine: filters.mine || undefined, tag: filters.tag, source: filters.source,
      task: filters.task, next: filters.next, budget: filters.budget, debt: filters.debt || undefined, visit: filters.visit }) });
  }

  // v2.8: kardex y consumo de materiales
  kardex(itemId: number, from: string, to: string): Observable<any> { return this.http.get(`/api/inventory/${itemId}/kardex`, { params: params({ from, to }) }); }
  inventoryValuation(): Observable<any> { return this.http.get('/api/inventory/valuation'); }
  inventoryConsumption(from: string, to: string, patientId?: number): Observable<any> { return this.http.get('/api/inventory/consumption', { params: params({ from, to, patientId }) }); }
  // v2.8: ortodoncia
  orthoOptions(): Observable<any> { return this.http.get('/api/ortho/options'); }
  orthoCases(dentistId?: number): Observable<any[]> { return this.http.get<any[]>('/api/ortho/cases', { params: params({ dentistId }) }); }
  patientOrtho(pid: number): Observable<any[]> { return this.http.get<any[]>(`/api/patients/${pid}/ortho`); }
  createOrtho(pid: number, body: any): Observable<any> { return this.http.post(`/api/patients/${pid}/ortho`, body); }
  updateOrtho(id: number, body: any): Observable<any> { return this.http.put(`/api/ortho/cases/${id}`, body); }
  orthoControl(id: number, body: any): Observable<any> { return this.http.post(`/api/ortho/cases/${id}/controls`, body); }
  orthoUpload(id: number, file: Blob, name: string, stage: string, view: string, takenOn: string): Observable<any> {
    const fd = new FormData(); fd.append('file', file, name); fd.append('stage', stage); fd.append('view', view); if (takenOn) fd.append('takenOn', takenOn);
    return this.http.post(`/api/ortho/cases/${id}/photos`, fd);
  }
  orthoLink(id: number, body: any): Observable<any> { return this.http.post(`/api/ortho/cases/${id}/photos/link`, body); }
  orthoMovePhoto(id: number, photoId: number, body: any): Observable<any> { return this.http.put(`/api/ortho/cases/${id}/photos/${photoId}`, body); }
  orthoUnlink(id: number, photoId: number): Observable<any> { return this.http.delete(`/api/ortho/cases/${id}/photos/${photoId}`); }
  // v2.8: consentimientos informados con firma a distancia
  consentTemplates(all = false): Observable<any[]> { return this.http.get<any[]>('/api/informed-consents/templates', { params: params({ all: all || undefined }) }); }
  saveConsentTemplate(id: number | null, body: any): Observable<any> { return id ? this.http.put(`/api/informed-consents/templates/${id}`, body) : this.http.post('/api/informed-consents/templates', body); }
  informedConsents(status?: string): Observable<any[]> { return this.http.get<any[]>('/api/informed-consents', { params: params({ status }) }); }
  patientInformedConsents(pid: number): Observable<any[]> { return this.http.get<any[]>(`/api/patients/${pid}/informed-consents`); }
  sendInformedConsent(pid: number, body: any): Observable<any> { return this.http.post(`/api/patients/${pid}/informed-consents`, body); }
  cancelInformedConsent(id: number, reason: string): Observable<any> { return this.http.post(`/api/informed-consents/${id}/cancel`, { reason }); }
  publicConsent(token: string): Observable<any> { return this.http.get(`/api/public/consent/${token}`); }
  signPublicConsent(token: string, body: any): Observable<any> { return this.http.post(`/api/public/consent/${token}`, body); }
  // v2.8: notas fijas y alertas de la ficha; reporte anual
  patientFlags(pid: number): Observable<any> { return this.http.get(`/api/patients/${pid}/flags`); }
  addPatientNote(pid: number, body: any): Observable<any> { return this.http.post(`/api/patients/${pid}/notes`, body); }
  updatePatientNote(pid: number, id: number, body: any): Observable<any> { return this.http.put(`/api/patients/${pid}/notes/${id}`, body); }
  deletePatientNote(pid: number, id: number): Observable<any> { return this.http.delete(`/api/patients/${pid}/notes/${id}`); }
  annualReport(year: number): Observable<any> { return this.http.get('/api/reports/annual', { params: params({ year }) }); }
  annualReportFile(year: number, kind: 'pdf' | 'csv'): Observable<Blob> { return this.http.get(`/api/reports/annual/${kind}`, { params: params({ year }), responseType: 'blob' }); }
  // v2.10: cuadre y cierre de caja
  cashClosing(date: string): Observable<any> { return this.http.get('/api/cash/closing', { params: params({ date }) }); }
  closeCash(body: any): Observable<any> { return this.http.post('/api/cash/closing', body); }
  reopenCash(date: string, reason: string): Observable<any> { return this.http.post(`/api/cash/closing/${date}/reopen`, { reason }); }
  cashClosings(): Observable<any[]> { return this.http.get<any[]>('/api/cash/closings'); }
  cashClosingPdf(date: string): Observable<Blob> { return this.http.get(`/api/cash/closing/${date}/pdf`, { responseType: 'blob' }); }
  // v2.9: libro de reclamaciones
  complaintInfo(): Observable<any> { return this.http.get('/api/public/complaints/info'); }
  fileComplaint(body: any): Observable<any> { return this.http.post('/api/public/complaints', body); }
  lookupComplaint(code: string, doc: string): Observable<any> { return this.http.get(`/api/public/complaints/${encodeURIComponent(code)}`, { params: params({ doc }) }); }
  publicComplaintPdf(code: string, doc: string): Observable<Blob> { return this.http.get(`/api/public/complaints/${encodeURIComponent(code)}/pdf`, { params: params({ doc }), responseType: 'blob' }); }
  complaints(status?: string, year?: number, q?: string): Observable<any[]> { return this.http.get<any[]>('/api/complaints', { params: params({ status, year, q }) }); }
  complaintSummary(): Observable<any> { return this.http.get('/api/complaints/summary'); }
  complaintSettings(body: any): Observable<any> { return this.http.put('/api/complaints/settings', body); }
  updateComplaint(id: number, body: any): Observable<any> { return this.http.put(`/api/complaints/${id}`, body); }
  respondComplaint(id: number, body: any): Observable<any> { return this.http.post(`/api/complaints/${id}/respond`, body); }
  complaintPdf(id: number): Observable<Blob> { return this.http.get(`/api/complaints/${id}/pdf`, { responseType: 'blob' }); }
  complaintsCsv(year: number): Observable<Blob> { return this.http.get('/api/complaints/export', { params: params({ year }), responseType: 'blob' }); }

  // v2.7: tareas
  tasks(f: { scope?: string; status?: string; kind?: string; assigneeId?: number; patientId?: number; leadId?: number; q?: string }): Observable<{ items: any[]; counts: any }> {
    return this.http.get<any>('/api/tasks', { params: params(f) });
  }
  taskSummary(): Observable<any> { return this.http.get('/api/tasks/summary'); }
  taskOptions(): Observable<{ kinds: Record<string, string>; assignees: any[]; me: number }> { return this.http.get<any>('/api/tasks/options'); }
  createTask(body: any): Observable<any> { return this.http.post('/api/tasks', body); }
  updateTask(id: number, body: any): Observable<any> { return this.http.put(`/api/tasks/${id}`, body); }
  completeTask(id: number, result?: string): Observable<any> { return this.http.post(`/api/tasks/${id}/complete`, { result }); }
  cancelTask(id: number, result: string): Observable<any> { return this.http.post(`/api/tasks/${id}/cancel`, { result }); }
  reopenTask(id: number): Observable<any> { return this.http.post(`/api/tasks/${id}/reopen`, {}); }

  // v2.7: segmentos, campañas y cumpleaños
  segmentPreview(f: any): Observable<any> { return this.http.post('/api/marketing/segments/preview', f); }
  segments(): Observable<any[]> { return this.http.get<any[]>('/api/marketing/segments'); }
  saveSegment(id: number | null, body: any): Observable<any> { return id ? this.http.put(`/api/marketing/segments/${id}`, body) : this.http.post('/api/marketing/segments', body); }
  deleteSegment(id: number): Observable<any> { return this.http.delete(`/api/marketing/segments/${id}`); }
  segmentTasks(body: any): Observable<{ created: number; skipped: number }> { return this.http.post<any>('/api/marketing/segments/tasks', body); }
  birthdays(month?: number): Observable<any> { return this.http.get('/api/marketing/birthdays', { params: params({ month }) }); }
  updateBirthdaySettings(body: any): Observable<any> { return this.http.put('/api/marketing/birthdays/settings', body); }
  runBirthdays(): Observable<any> { return this.http.post('/api/marketing/birthdays/run', {}); }
  campaigns(): Observable<any[]> { return this.http.get<any[]>('/api/marketing/campaigns'); }
  campaign(id: number): Observable<any> { return this.http.get(`/api/marketing/campaigns/${id}`); }
  saveCampaign(id: number | null, body: any): Observable<any> { return id ? this.http.put(`/api/marketing/campaigns/${id}`, body) : this.http.post('/api/marketing/campaigns', body); }
  scheduleCampaign(id: number, scheduledAt: string | null): Observable<any> { return this.http.post(`/api/marketing/campaigns/${id}/schedule`, { scheduledAt }); }
  cancelCampaign(id: number): Observable<any> { return this.http.post(`/api/marketing/campaigns/${id}/cancel`, {}); }
  previewCampaign(body: any): Observable<any> { return this.http.post('/api/marketing/campaigns/preview', body); }
  setMarketingConsent(patientId: number, accepted: boolean, channel: string): Observable<any> {
    return this.http.put(`/api/patients/${patientId}/marketing-consent`, { accepted, channel });
  }

  // v2.7: embudo de ventas
  leads(f: { q?: string; source?: string; assigneeId?: number; closedDays?: number }): Observable<any> { return this.http.get('/api/leads', { params: params(f) }); }
  leadMetrics(days = 90): Observable<any> { return this.http.get('/api/leads/metrics', { params: params({ days }) }); }
  lead(id: number): Observable<any> { return this.http.get(`/api/leads/${id}`); }
  saveLead(id: number | null, body: any): Observable<any> { return id ? this.http.put(`/api/leads/${id}`, body) : this.http.post('/api/leads', body); }
  moveLead(id: number, stage: string, lostReason?: string): Observable<any> { return this.http.post(`/api/leads/${id}/stage`, { stage, lostReason }); }
  leadNote(id: number, text: string): Observable<any> { return this.http.post(`/api/leads/${id}/notes`, { text }); }
  linkLeadPatient(id: number, patientId: number): Observable<any> { return this.http.post(`/api/leads/${id}/patient`, { patientId }); }
  setLeadWhatsappAuto(enabled: boolean): Observable<any> { return this.http.put('/api/leads/settings/whatsapp-auto', { enabled }); }

  searchPatients(q: string, page = 0, size = 20, mine = false): Observable<Page<Patient>> {
    return this.http.get<Page<Patient>>('/api/patients', { params: params({ q, page, size, mine: mine ? 'true' : undefined }) });
  }
  patient(id: number): Observable<{ patient: Patient; nextAppointment?: Appointment; lastVisit?: Appointment; appointmentsCount: number }> {
    return this.http.get<any>(`/api/patients/${id}`);
  }
  checkDocument(type: string, number: string, excludeId?: number): Observable<{ exists: boolean; patient?: { id: number; fullName: string } }> {
    return this.http.get<any>('/api/patients/check-document', { params: params({ type, number, excludeId }) });
  }
  emailInvoice(id: number, email: string): Observable<any> { return this.http.post<any>(`/api/billing/invoices/${id}/email`, { email: email || null }); }
  cashDay(date: string): Observable<any> { return this.http.get<any>('/api/cash/day', { params: params({ date }) }); }
  // inventario: lotes, compras, solicitudes
  inventoryLots(id: number): Observable<any[]> { return this.http.get<any[]>(`/api/inventory/${id}/lots`); }
  purchases(from?: string, to?: string): Observable<any[]> { return this.http.get<any[]>('/api/inventory/purchases', { params: params({ from, to }) }); }
  purchase(id: number): Observable<any> { return this.http.get<any>(`/api/inventory/purchases/${id}`); }
  createPurchase(body: any): Observable<any> { return this.http.post<any>('/api/inventory/purchases', body); }
  payPurchase(id: number, body: any): Observable<any> { return this.http.post<any>(`/api/inventory/purchases/${id}/pay`, body); }
  supplyCatalog(): Observable<any[]> { return this.http.get<any[]>('/api/supply-requests/catalog'); }
  supplyRequests(status?: string, mine = false): Observable<any[]> { return this.http.get<any[]>('/api/supply-requests', { params: params({ status, mine: mine ? 'true' : undefined }) }); }
  createSupplyRequest(body: any): Observable<any> { return this.http.post<any>('/api/supply-requests', body); }
  answerSupplyRequest(id: number, body: any): Observable<any> { return this.http.post<any>(`/api/supply-requests/${id}/answer`, body); }
  cancelSupplyRequest(id: number): Observable<any> { return this.http.post<any>(`/api/supply-requests/${id}/cancel`, {}); }
  // catálogo y promociones
  catalogProcedures(all = false): Observable<any[]> { return this.http.get<any[]>('/api/catalog/procedures', { params: params({ all: all ? 'true' : undefined }) }); }
  saveProcedure(id: number | null, body: any): Observable<any> { return id ? this.http.put<any>(`/api/catalog/procedures/${id}`, body) : this.http.post<any>('/api/catalog/procedures', body); }
  promotions(): Observable<any[]> { return this.http.get<any[]>('/api/catalog/promotions'); }
  savePromotion(id: number | null, body: any): Observable<any[]> { return id ? this.http.put<any[]>(`/api/catalog/promotions/${id}`, body) : this.http.post<any[]>('/api/catalog/promotions', body); }
  // mi perfil y roles personalizados
  myProfile(): Observable<any> { return this.http.get<any>('/api/me/profile'); }
  updateMyProfile(body: any): Observable<any> { return this.http.put<any>('/api/me/profile', body); }
  changeMyPassword(body: any): Observable<any> { return this.http.post<any>('/api/me/password', body); }
  createRole(body: any): Observable<any> { return this.http.post<any>('/api/permissions/roles', body); }
  deleteRole(code: string, reassignTo?: string): Observable<any> {
    return this.http.delete<any>(`/api/permissions/roles/${code}`, { params: reassignTo ? { reassignTo } : {} });
  }
  editRole(code: string, body: { name?: string; description?: string; hidden?: boolean }): Observable<PermissionMatrix> {
    return this.http.patch<PermissionMatrix>(`/api/permissions/roles/${code}`, body);
  }
  editPermission(code: string, body: { label?: string; group?: string; description?: string }): Observable<PermissionMatrix> {
    return this.http.patch<PermissionMatrix>(`/api/permissions/catalog/${code}`, body);
  }
  // v2.11: eliminar registros (solo administrador) y papelera
  deletePreview(entity: string, id: string | number): Observable<DeletePreview> {
    return this.http.get<DeletePreview>(`/api/admin/records/${entity}/${id}/preview`);
  }
  deleteRecord(entity: string, id: string | number, confirm: string, reason: string): Observable<any> {
    return this.http.post<any>(`/api/admin/records/${entity}/${id}/delete`, { confirm, reason });
  }
  trash(): Observable<any[]> { return this.http.get<any[]>('/api/admin/records/trash'); }
  trashData(id: number): Observable<Blob> { return this.http.get(`/api/admin/records/trash/${id}`, { responseType: 'blob' }); }
  start2faEmail(): Observable<any> { return this.http.post<any>('/api/auth/2fa/email/start', {}); }
  enable2faEmail(code: string): Observable<any> { return this.http.post<any>('/api/auth/2fa/email/enable', { code }); }
  publicPromotions(): Observable<any[]> { return this.http.get<any[]>('/api/public/promotions'); }
  myEarnings(from: string, to: string): Observable<any> { return this.http.get<any>('/api/me/earnings', { params: params({ from, to }) }); }
  legal(code: string): Observable<{ code: string; version: string; title: string; body: string; publishedAt: string }> { return this.http.get<any>(`/api/public/legal/${code}`); }
  patientConsents(id: number): Observable<any> { return this.http.get<any>(`/api/patients/${id}/consents`); }
  uploadSignedConsent(id: number, file: File): Observable<any> { const fd = new FormData(); fd.append('file', file); return this.http.post<any>(`/api/patients/${id}/consents/signed`, fd); }
  dniConfig(): Observable<{ enabled: boolean }> { return this.http.get<{ enabled: boolean }>('/api/lookup/dni-config'); }
  lookupDni(dni: string): Observable<{ firstName: string; lastName: string; source: string; birthDate?: string; sex?: string; address?: string; ubigeo?: string; district?: string }> { return this.http.get<any>(`/api/lookup/dni/${dni}`); }
  ubigeo(): Observable<UbigeoDep[]> { return this.http.get<UbigeoDep[]>('/ubigeo.json'); }
  createPatient(body: any): Observable<Patient> { return this.http.post<Patient>('/api/patients', body); }
  updatePatient(id: number, body: any): Observable<Patient> { return this.http.put<Patient>(`/api/patients/${id}`, body); }
  patientAppointments(id: number): Observable<Appointment[]> { return this.http.get<Appointment[]>(`/api/patients/${id}/appointments`); }
  patientAudit(id: number): Observable<AuditEntry[]> { return this.http.get<AuditEntry[]>(`/api/patients/${id}/audit`); }

  // ---------- agenda (HU-07, HU-08, HU-09)
  dentists(): Observable<Dentist[]> { return this.http.get<Dentist[]>('/api/dentists'); }
  slots(dentistId: number, date: string, duration: number, excludeId?: number, patientId?: number):
      Observable<{ works: boolean; blocks: Block[]; slots: Slot[]; freeCount: number; dentist: Dentist }> {
    return this.http.get<any>('/api/agenda/slots', { params: params({ dentistId, date, duration, excludeId, patientId }) });
  }
  day(date: string, dentistId?: number): Observable<{ columns: { dentist: Dentist; blocks: Block[] }[]; appointments: Appointment[]; counts: Record<string, number> }> {
    return this.http.get<any>('/api/agenda/day', { params: params({ date, dentistId }) });
  }
  appointmentsRange(from: string, to: string, dentistId?: number): Observable<Appointment[]> {
    return this.http.get<Appointment[]>('/api/appointments', { params: params({ from, to, dentistId }) });
  }
  appointment(id: number): Observable<Appointment> { return this.http.get<Appointment>(`/api/appointments/${id}`); }
  createAppointment(body: any): Observable<Appointment> { return this.http.post<Appointment>('/api/appointments', body); }
  reschedule(id: number, body: any): Observable<Appointment> { return this.http.put<Appointment>(`/api/appointments/${id}`, body); }
  setStatus(id: number, status: string, reason?: string): Observable<Appointment> {
    return this.http.patch<Appointment>(`/api/appointments/${id}/status`, { status, reason });
  }
  appointmentAudit(id: number): Observable<AuditEntry[]> { return this.http.get<AuditEntry[]>(`/api/appointments/${id}/audit`); }

  // ---------- historia clínica (HU-04, HU-05, HU-06)
  history(pid: number): Observable<{ history: ClinicalHistory | null; diagnoses: Diagnosis[]; alerts: string[] }> {
    return this.http.get<any>(`/api/patients/${pid}/history`);
  }
  saveHistory(pid: number, body: ClinicalHistory): Observable<ClinicalHistory> { return this.http.put<ClinicalHistory>(`/api/patients/${pid}/history`, body); }
  addDiagnosis(pid: number, body: any): Observable<Diagnosis> { return this.http.post<Diagnosis>(`/api/patients/${pid}/diagnoses`, body); }
  odontogram(pid: number): Observable<{ entries: OdontogramEntry[]; updatedAt?: string; updatedBy?: string }> {
    return this.http.get<any>(`/api/patients/${pid}/odontogram`);
  }
  saveOdontogram(pid: number, entries: OdontogramEntry[]): Observable<{ entries: OdontogramEntry[]; updatedAt?: string; updatedBy?: string }> {
    return this.http.put<any>(`/api/patients/${pid}/odontogram`, { entries });
  }
  procedures(): Observable<Procedure[]> { return this.http.get<Procedure[]>('/api/procedures'); }
  plans(pid: number): Observable<PlanView[]> { return this.http.get<PlanView[]>(`/api/patients/${pid}/plans`); }
  createPlan(pid: number, body: any): Observable<PlanView> { return this.http.post<PlanView>(`/api/patients/${pid}/plans`, body); }
  addPlanItem(planId: number, body: any): Observable<PlanView> { return this.http.post<PlanView>(`/api/plans/${planId}/items`, body); }
  setItemStatus(itemId: number, status: string, supplies?: { itemId: number; quantity: number }[] | null): Observable<PlanView> { return this.http.patch<PlanView>(`/api/plan-items/${itemId}`, { status, supplies: supplies ?? null }); }
  /** v2.10: kit del procedimiento y catálogo, para indicar lo usado en la atención. */
  itemSupplies(itemId: number): Observable<{ kit: any[]; catalog: any[] }> { return this.http.get<{ kit: any[]; catalog: any[] }>(`/api/plan-items/${itemId}/supplies`); }
  addEvolution(planId: number, body: any): Observable<PlanView> { return this.http.post<PlanView>(`/api/plans/${planId}/evolutions`, body); }
  cancelPlan(planId: number): Observable<PlanView> { return this.http.post<PlanView>(`/api/plans/${planId}/cancel`, {}); }

  // ---------- facturación (HU-10, HU-11, HU-12)
  company(): Observable<Company> { return this.http.get<Company>('/api/billing/company'); }
  billableItems(pid: number): Observable<any[]> { return this.http.get<any[]>(`/api/billing/patients/${pid}/billable-items`); }
  invoices(f: { q?: string; status?: string; docType?: string; from?: string; to?: string; page?: number }): Observable<Page<Invoice>> {
    return this.http.get<Page<Invoice>>('/api/billing/invoices', { params: params(f) });
  }
  createInvoice(body: any): Observable<InvoiceDetail> { return this.http.post<InvoiceDetail>('/api/billing/invoices', body); }
  allSeries(): Observable<any[]> { return this.http.get<any[]>('/api/billing/series/all'); }
  createSeries(body: any): Observable<any> { return this.http.post('/api/billing/series', body); }
  updateSeries(id: number, body: any): Observable<any> { return this.http.put(`/api/billing/series/${id}`, body); }
  invoice(id: number): Observable<InvoiceDetail> { return this.http.get<InvoiceDetail>(`/api/billing/invoices/${id}`); }
  sendInvoice(id: number): Observable<InvoiceDetail> { return this.http.post<InvoiceDetail>(`/api/billing/invoices/${id}/send`, {}); }
  invoiceStatus(id: number): Observable<InvoiceDetail> { return this.http.post<InvoiceDetail>(`/api/billing/invoices/${id}/status`, {}); }
  lookupRuc(ruc: string): Observable<RucInfo> { return this.http.get<RucInfo>(`/api/billing/ruc/${ruc}`); }
  download(path: string): Observable<Blob> { return this.http.get(path, { responseType: 'blob' }); }

  // ---------- usuarios (HU-25, HU-27)
  users(): Observable<any[]> { return this.http.get<any[]>('/api/users'); }
  roles(): Observable<{ code: string; label: string }[]> { return this.http.get<any[]>('/api/roles'); }
  /** v2.10: roles asignables (sistema + creados por la clínica) con su resumen de permisos. */
  assignableRoles(): Observable<any[]> { return this.http.get<any[]>('/api/roles/assignable'); }
  createUser(body: any): Observable<any> { return this.http.post('/api/users', body); }
  updateUser(id: number, body: any): Observable<any> { return this.http.put(`/api/users/${id}`, body); }
  setUserActive(id: number, active: boolean): Observable<any> { return this.http.patch(`/api/users/${id}/active`, { active }); }
  setUserPassword(id: number, newPassword: string): Observable<any> { return this.http.post(`/api/users/${id}/password`, { newPassword }); }
  unlockUser(id: number): Observable<any> { return this.http.post(`/api/users/${id}/unlock`, {}); }
  userAudit(id: number): Observable<AuditEntry[]> { return this.http.get<AuditEntry[]>(`/api/users/${id}/audit`); }
  schedules(dentistId: number): Observable<any[]> { return this.http.get<any[]>(`/api/dentists/${dentistId}/schedules`); }
  saveSchedules(dentistId: number, blocks: { weekday: number; start: string; end: string }[]): Observable<any[]> {
    return this.http.put<any[]>(`/api/dentists/${dentistId}/schedules`, blocks);
  }

  // ---------- roles y permisos (HU-26)
  permissions(): Observable<PermissionMatrix> { return this.http.get<PermissionMatrix>('/api/permissions'); }
  savePermissions(role: string, permissions: string[]): Observable<PermissionMatrix> {
    return this.http.put<PermissionMatrix>(`/api/permissions/${role}`, { permissions });
  }

  // ---------- caja (HU-13, HU-14, HU-15)
  cashMeta(): Observable<{ methods: string[]; methodLabels: Record<string, string>; expenseCategories: string[] }> { return this.http.get<any>('/api/cash/meta'); }
  account(pid: number): Observable<Account> { return this.http.get<Account>(`/api/cash/patients/${pid}/account`); }
  pay(body: any, comprobante = false): Observable<{ payment: Payment; concept: string; account: Account; comprobante?: PaymentInvoice; comprobanteError?: string }> {
    return this.http.post<any>('/api/cash/payments', body, { params: { comprobante: String(comprobante) } });
  }
  paymentInvoice(paymentId: number): Observable<PaymentInvoice> { return this.http.post<PaymentInvoice>(`/api/cash/payments/${paymentId}/invoice`, {}); }
  // Cobros en línea con Mercado Pago (tarjeta, Yape)
  portalOnlineConfig(): Observable<OnlineConfig> { return this.http.get<OnlineConfig>('/api/portal/payments/online/config'); }
  portalOnlineCreate(amount: number): Observable<OnlineCharge> { return this.http.post<OnlineCharge>('/api/portal/payments/online', { amount }); }
  portalOnlineStatus(id: number): Observable<OnlineCharge> { return this.http.get<OnlineCharge>(`/api/portal/payments/online/${id}`); }
  onlineConfig(): Observable<OnlineConfig> { return this.http.get<OnlineConfig>('/api/cash/online/config'); }
  onlineCreate(body: { patientId: number; treatmentItemId: number | null; amount: number; description?: string }): Observable<OnlineCharge> { return this.http.post<OnlineCharge>('/api/cash/online', body); }
  onlineStatus(id: number): Observable<OnlineCharge> { return this.http.get<OnlineCharge>(`/api/cash/online/${id}`); }
  onlineCancel(id: number): Observable<OnlineCharge> { return this.http.post<OnlineCharge>(`/api/cash/online/${id}/cancel`, {}); }
  onlineWhatsapp(id: number): Observable<{ sent: boolean; phone: string }> { return this.http.post<any>(`/api/cash/online/${id}/whatsapp`, {}); }
  voidPayment(id: number, reason: string): Observable<Payment> { return this.http.post<Payment>(`/api/cash/payments/${id}/void`, { reason }); }
  addExpense(body: any): Observable<any> { return this.http.post('/api/cash/expenses', body); }
  updateExpense(id: number, body: any): Observable<any> { return this.http.put(`/api/cash/expenses/${id}`, body); }
  voidExpense(id: number, reason: string): Observable<any> { return this.http.post(`/api/cash/expenses/${id}/void`, { reason }); }
  cashToday(): Observable<CashReport> { return this.http.get<CashReport>('/api/cash/today'); }
  cashReport(from: string, to: string): Observable<CashReport> { return this.http.get<CashReport>('/api/cash/report', { params: params({ from, to }) }); }

  // ---------- comisiones (HU-16, HU-17, HU-18)
  commissionSummary(): Observable<any[]> { return this.http.get<any[]>('/api/commissions/summary'); }
  commissionRules(dentistId?: number): Observable<CommissionRule[]> { return this.http.get<CommissionRule[]>('/api/commissions/rules', { params: params({ dentistId }) }); }
  saveCommissionRule(id: number | null, body: any): Observable<CommissionRule> {
    return id ? this.http.put<CommissionRule>(`/api/commissions/rules/${id}`, body) : this.http.post<CommissionRule>('/api/commissions/rules', body);
  }
  calculateCommissions(dentistId: number, from: string, to: string): Observable<any> {
    return this.http.get('/api/commissions/calculate', { params: params({ dentistId, from, to }) });
  }
  settlements(dentistId?: number, status?: string): Observable<any[]> { return this.http.get<any[]>('/api/commissions/settlements', { params: params({ dentistId, status }) }); }
  settle(body: any): Observable<any> { return this.http.post('/api/commissions/settlements', body); }
  settlement(id: number): Observable<any> { return this.http.get(`/api/commissions/settlements/${id}`); }
  paySettlement(id: number, body: any): Observable<any> { return this.http.post(`/api/commissions/settlements/${id}/payouts`, body); }
  annulSettlement(id: number, reason: string): Observable<any> { return this.http.post(`/api/commissions/settlements/${id}/annul`, { reason }); }

  // ---------- indicadores (HU-19, HU-20, HU-21)
  incomeReport(from: string, to: string, group?: string): Observable<any> { return this.http.get('/api/reports/income', { params: params({ from, to, group }) }); }
  appointmentsReport(from: string, to: string, dentistId?: number | null, group?: string): Observable<any> {
    return this.http.get('/api/reports/appointments', { params: params({ from, to, dentistId, group }) });
  }
  treatmentsReport(from: string, to: string, dentistId?: number | null): Observable<any> {
    return this.http.get('/api/reports/treatments', { params: params({ from, to, dentistId }) });
  }

  // ---------- notificaciones (HU-23, HU-24)
  notifications(status?: string, type?: string): Observable<{ counters: Record<string, number>; items: NotificationItem[]; panelUrl: string }> {
    return this.http.get<any>('/api/notifications', { params: params({ status, type }) });
  }
  retryNotification(id: number): Observable<NotificationItem> { return this.http.post<NotificationItem>(`/api/notifications/${id}/retry`, {}); }
  patientNotifications(pid: number): Observable<NotificationItem[]> { return this.http.get<NotificationItem[]>(`/api/patients/${pid}/notifications`); }
  setNotificationChannel(pid: number, channel: string): Observable<Patient> {
    return this.http.patch<Patient>(`/api/patients/${pid}/notification-channel`, { channel });
  }
  remind(appointmentId: number): Observable<{ queued: number; duplicated: number; message: string }> {
    return this.http.post<any>(`/api/appointments/${appointmentId}/remind`, {});
  }

  // ---------- archivos clínicos y documentos firmados
  patientFiles(pid: number): Observable<PatientFile[]> { return this.http.get<PatientFile[]>(`/api/patients/${pid}/files`); }
  uploadFile(pid: number, file: Blob, name: string, meta: { kind: string; title?: string; note?: string; tooth?: number | null; share?: boolean }): Observable<PatientFile> {
    const fd = new FormData();
    fd.append('file', file, name);
    for (const [k, v] of Object.entries(meta)) if (v !== null && v !== undefined && v !== '') fd.append(k, String(v));
    return this.http.post<PatientFile>(`/api/patients/${pid}/files`, fd);
  }
  createDocument(pid: number, body: { kind: string; title: string; body: string; signerName: string; signatureJpeg: string; shareWithPatient: boolean }): Observable<PatientFile> {
    return this.http.post<PatientFile>(`/api/patients/${pid}/documents`, body);
  }
  fileContent(id: number): Observable<Blob> { return this.http.get(`/api/files/${id}/content`, { responseType: 'blob' }); }
  shareFile(id: number, sharedWithPatient: boolean): Observable<PatientFile> { return this.http.patch<PatientFile>(`/api/files/${id}`, { sharedWithPatient }); }
  updateFile(id: number, body: { title?: string; kind?: string; note?: string; tooth?: number | null }): Observable<PatientFile> { return this.http.patch<PatientFile>(`/api/files/${id}`, body); }
  appointmentAttention(id: number): Observable<any> { return this.http.get(`/api/appointments/${id}/attention`); }
  prescriptions(patientId: number): Observable<any[]> { return this.http.get<any[]>(`/api/patients/${patientId}/prescriptions`); }
  createPrescription(patientId: number, body: any): Observable<any> { return this.http.post(`/api/patients/${patientId}/prescriptions`, body); }
  voidPrescription(id: number, reason: string): Observable<any> { return this.http.post(`/api/prescriptions/${id}/void`, { reason }); }
  voidDiagnosis(id: number, reason: string): Observable<any> { return this.http.post(`/api/diagnoses/${id}/void`, { reason }); }
  voidEvolution(id: number, reason: string): Observable<any> { return this.http.post(`/api/evolutions/${id}/void`, { reason }); }
  deleteFile(id: number): Observable<any> { return this.http.delete(`/api/files/${id}`); }

  // ---------- inventario
  inventory(): Observable<{ items: InventoryItem[]; lowCount: number; recent: any[] }> { return this.http.get<any>('/api/inventory'); }
  createInventoryItem(body: any): Observable<InventoryItem> { return this.http.post<InventoryItem>('/api/inventory', body); }
  updateInventoryItem(id: number, body: any): Observable<InventoryItem> { return this.http.put<InventoryItem>(`/api/inventory/${id}`, body); }
  inventoryImage(id: number, file: File): Observable<InventoryItem> { const fd = new FormData(); fd.append('file', file); return this.http.post<InventoryItem>(`/api/inventory/${id}/image`, fd); }
  removeInventoryImage(id: number): Observable<InventoryItem> { return this.http.delete<InventoryItem>(`/api/inventory/${id}/image`); }
  dentistPhoto(id: number, file: File): Observable<Dentist> { const fd = new FormData(); fd.append('file', file); return this.http.post<Dentist>(`/api/dentists/${id}/photo`, fd); }
  removeDentistPhoto(id: number): Observable<Dentist> { return this.http.delete<Dentist>(`/api/dentists/${id}/photo`); }
  inventoryMove(id: number, body: any): Observable<any> { return this.http.post(`/api/inventory/${id}/moves`, body); }
  inventoryMoves(id: number): Observable<any[]> { return this.http.get<any[]>(`/api/inventory/${id}/moves`); }
  supplies(): Observable<any[]> { return this.http.get<any[]>('/api/inventory/supplies'); }
  saveSupplies(procedureId: number, list: { itemId: number; quantity: number }[]): Observable<any> { return this.http.put(`/api/inventory/supplies/${procedureId}`, list); }

  // ---------- seguimiento: lista de espera, cuotas, controles, satisfacción
  waitlist(): Observable<any[]> { return this.http.get<any[]>('/api/waitlist'); }
  addWaitlist(body: { patientId: number; dentistId?: number | null; reason: string; preference?: string }): Observable<any> { return this.http.post('/api/waitlist', body); }
  updateWaitlist(id: number, body: { dentistId?: number | null; reason: string; preference?: string }): Observable<any> { return this.http.put(`/api/waitlist/${id}`, body); }
  closeWaitlist(id: number): Observable<any> { return this.http.post(`/api/waitlist/${id}/close`, {}); }
  installments(planId: number): Observable<Installments> { return this.http.get<Installments>(`/api/plans/${planId}/installments`); }
  createInstallments(planId: number, count: number, firstDueDate: string): Observable<Installments> {
    return this.http.post<Installments>(`/api/plans/${planId}/installments`, { count, firstDueDate });
  }
  recalls(): Observable<any[]> { return this.http.get<any[]>('/api/recalls'); }
  sendRecall(patientId: number, kind: string): Observable<{ queued: number; message: string }> { return this.http.post<any>(`/api/recalls/${patientId}/send`, { kind }); }
  rescheduleRequests(): Observable<any[]> { return this.http.get<any[]>('/api/agenda/reschedule-requests'); }
  satisfaction(days = 90): Observable<any> { return this.http.get('/api/reports/satisfaction', { params: params({ days }) }); }

  // ---------- verificación en 2 pasos
  totpSetup(): Observable<{ secret: string; otpauthUrl: string }> { return this.http.post<any>('/api/auth/2fa/setup', {}); }
  totpEnable(code: string): Observable<any> { return this.http.post('/api/auth/2fa/enable', { code }); }
  totpDisable(code: string): Observable<any> { return this.http.post('/api/auth/2fa/disable', { code }); }

  // ---------- app del paciente (HU-22)
  portalMe(): Observable<any> { return this.http.get('/api/portal/me'); }
  portalAppointments(): Observable<{ next: PortalAppointment | null; upcoming: PortalAppointment[]; past: PortalAppointment[] }> {
    return this.http.get<any>('/api/portal/appointments');
  }
  portalConfirm(id: number): Observable<any> { return this.http.post(`/api/portal/appointments/${id}/confirm`, {}); }
  portalNotices(): Observable<{ unread: number; items: any[] }> { return this.http.get<any>('/api/portal/notices'); }
  portalReadNotices(): Observable<any> { return this.http.post('/api/portal/notices/read', {}); }
  portalBookingOptions(): Observable<any> { return this.http.get('/api/portal/booking/options'); }
  portalBookingSlots(dentistId: number, date: string, reasonCode: string, excludeId?: number | null): Observable<{ slots: string[]; durationMin: number; works: boolean }> {
    return this.http.get<any>('/api/portal/booking/slots', { params: params({ dentistId, date, reasonCode, excludeId: excludeId ?? undefined }) });
  }
  portalBook(body: { dentistId: number; startAt: string; reasonCode: string; notes?: string }): Observable<any> {
    return this.http.post('/api/portal/appointments', body);
  }
  portalReschedule(id: number, body: { dentistId?: number; startAt: string }): Observable<any> {
    return this.http.post(`/api/portal/appointments/${id}/reschedule`, body);
  }
  portalCancel(id: number, reason?: string): Observable<any> { return this.http.post(`/api/portal/appointments/${id}/cancel`, { reason }); }
  portalChannel(channel: string): Observable<any> { return this.http.patch('/api/portal/me/notifications', { channel }); }
  portalAccount(): Observable<any> { return this.http.get('/api/portal/account'); }
  portalContact(phone: string, email: string): Observable<any> { return this.http.put('/api/portal/me/contact', { phone, email }); }
  portalTreatments(): Observable<any> { return this.http.get('/api/portal/treatments'); }
  portalConsents(): Observable<any> { return this.http.get('/api/portal/consents'); }
  portalConsentPdf(id: number): Observable<Blob> { return this.http.get(`/api/portal/consents/${id}/pdf`, { responseType: 'blob' }); }
  portalFileLink(id: number): Observable<{ url: string }> { return this.http.get<{ url: string }>(`/api/portal/files/${id}/link`); }
  portalDocuments(): Observable<any> { return this.http.get('/api/portal/documents'); }
  portalInvoiceLink(id: number): Observable<{ url: string }> { return this.http.get<{ url: string }>(`/api/portal/documents/invoices/${id}/link`); }
  portalFileRequests(): Observable<any[]> { return this.http.get<any[]>('/api/portal/file-requests'); }
  portalUploadRequest(id: number, file: File): Observable<any[]> { const f = new FormData(); f.append('file', file); return this.http.post<any[]>(`/api/portal/file-requests/${id}/upload`, f); }
  // archivos solicitados y seguimiento de confirmaciones (personal)
  fileRequests(patientId: number): Observable<any[]> { return this.http.get<any[]>(`/api/patients/${patientId}/file-requests`); }
  createFileRequest(patientId: number, body: any): Observable<any[]> { return this.http.post<any[]>(`/api/patients/${patientId}/file-requests`, body); }
  cancelFileRequest(patientId: number, id: number): Observable<any[]> { return this.http.post<any[]>(`/api/patients/${patientId}/file-requests/${id}/cancel`, {}); }
  uploadFileRequest(patientId: number, id: number, file: File): Observable<any[]> { const f = new FormData(); f.append('file', file); return this.http.post<any[]>(`/api/patients/${patientId}/file-requests/${id}/upload`, f); }
  unconfirmed(): Observable<any> { return this.http.get('/api/agenda/unconfirmed'); }
  callResult(id: number, result: string, note: string): Observable<any> { return this.http.post(`/api/appointments/${id}/call`, { result, note }); }

  // ---------- mensajes de WhatsApp y asistente virtual
  conversations(q?: string): Observable<{ items: ConversationThread[]; unread: number; attention: number; mode: string }> {
    return this.http.get<any>('/api/conversations', { params: params({ q }) });
  }
  conversationSummary(): Observable<{ unread: number; attention: number; mode: string }> { return this.http.get<any>('/api/conversations/summary'); }
  conversation(phone: string): Observable<ConversationDetail> { return this.http.get<ConversationDetail>(`/api/conversations/${phone}`); }
  sendWhatsapp(phone: string, text: string): Observable<any> { return this.http.post(`/api/conversations/${phone}/messages`, { text }); }
  updateConversation(phone: string, body: { aiEnabled?: boolean; attended?: boolean }): Observable<ConversationDetail> {
    return this.http.patch<ConversationDetail>(`/api/conversations/${phone}`, body);
  }
  assistantSettings(): Observable<AssistantSettings> { return this.http.get<AssistantSettings>('/api/assistant/settings'); }
  updateAssistantSettings(body: { mode?: string; pauseMinutes?: number }): Observable<AssistantSettings> {
    return this.http.put<AssistantSettings>('/api/assistant/settings', body);
  }
  faq(): Observable<FaqItem[]> { return this.http.get<FaqItem[]>('/api/assistant/faq'); }
  addFaq(body: Partial<FaqItem>): Observable<FaqItem[]> { return this.http.post<FaqItem[]>('/api/assistant/faq', body); }
  updateFaq(id: number, body: Partial<FaqItem>): Observable<FaqItem[]> { return this.http.put<FaqItem[]>(`/api/assistant/faq/${id}`, body); }
  deleteFaq(id: number): Observable<FaqItem[]> { return this.http.delete<FaqItem[]>(`/api/assistant/faq/${id}`); }
  testAssistant(text: string, patientId?: number): Observable<AssistantTest> {
    return this.http.post<AssistantTest>('/api/assistant/test', { text, patientId: patientId ?? null });
  }
}

export interface PatientFile {
  id: number; patientId: number; kind: string; title: string; note?: string; tooth?: number; contentType: string; sizeBytes: number;
  sharedWithPatient: boolean; signedAt?: string; createdAt: string; createdBy: string; image?: boolean;
}

export interface InventoryItem { id: number; name: string; unit: string; stock: number; minStock: number; active: boolean; low: boolean; updatedAt?: string; imageUrl?: string | null;
  category?: string; brand?: string | null; supplier?: string | null; unitCost?: number | null; tracksExpiry?: boolean; }

export interface Installments {
  planId: number; total: number; paid: number;
  items: { id: number; number: number; dueDate: string; amount: number; status: 'PAGADA' | 'PENDIENTE' | 'VENCIDA' }[];
}

export interface PermissionMatrix {
  permissions: { code: string; group: string; label: string; description?: string }[];
  roles: { code: string; label: string; locked: boolean; permissions: string[]; users: number; custom?: boolean; baseRole?: string; baseRoleLabel?: string; description?: string; hidden?: boolean }[];
  adminOnly?: string[];
}

export interface DeletePreview {
  entity: string; title: string; id: string; label: string;
  alsoDeleted: Record<string, number>; unlinked: Record<string, number>;
  blockers: string[]; warnings: string[]; canDelete: boolean;
}

export interface Payment {
  id: number; patientId: number; treatmentItemId?: number; amount: number; method: string; reference?: string; notes?: string;
  paidAt: string; cashier: string; status: string; voidReason?: string; voidedAt?: string; voidedBy?: string;
}

export interface Obligation {
  treatmentItemId: number; procedure: string; category: string; tooth?: number; planId: number; planTitle: string;
  status: string; doneAt?: string; price: number; paid: number; pending: number;
}

export interface Account {
  patient: { id: number; fullName: string; docType: string; docNumber: string };
  charges: number; credits: number; balance: number; inFavor: number; unapplied: number;
  obligations: Obligation[];
  movements: { date: string; type: 'CARGO' | 'ABONO'; concept: string; detail: string; charge: number; credit: number; balance: number; paymentId?: number }[];
  payments: Payment[];
}

export interface CashReport {
  from: string; to: string; income: number; outgo: number; net: number; incomeCount: number; outgoCount: number; voided: number;
  byMethod: { method: string; label: string; income: number; outgo: number; net: number }[];
  byDay: { date: string; income: number; outgo: number }[];
  byCategory: { category: string; amount: number }[];
  detail: { type: 'INGRESO' | 'EGRESO'; id: number; date: string; concept: string; party?: string; patientId?: number; category?: string;
    method: string; reference?: string; amount: number; user: string }[];
}

export interface CommissionRule {
  id: number; dentist: Dentist; procedure?: Procedure; category?: string | null; scope?: string; calcType: string; value: number; base: string;
  validFrom: string; validTo?: string; active: boolean; notes?: string; label: string; createdBy: string;
}

export interface NotificationItem {
  id: number; patientId?: number; appointmentId?: number; type: string; operation: string; channel: string; destination: string;
  subject?: string; body: string; idempotencyKey: string; scheduledFor?: string; status: string; attempts: number;
  lastError?: string; providerId?: string; sentAt?: string; createdAt: string;
}

export interface PatientRelation {
  id: number; relatedPatientId?: number | null; fullName: string; docType?: string; docNumber?: string; relationship: string;
  phone?: string; email?: string; isGuardian: boolean | number; pays: boolean | number; notes?: string; relatedHc?: string; inverse: boolean | number;
}
export interface PatientProfile {
  source?: string; sourceLabel?: string; sourceDetail?: string; hcNumber?: string; fiscalRuc?: string; fiscalName?: string;
  fiscalAddress?: string; tags: string[]; relations: PatientRelation[];
}

export interface PortalAppointment {
  id: number; startAt: string; endAt: string; durationMin: number; reason: string; status: string;
  dentist: { fullName: string; specialty: string; color: string }; canConfirm: boolean;
  canReschedule?: boolean; canCancel?: boolean; dentistId?: number; confirmDeadline?: string;
}

export interface InvoiceDetail {
  invoice: Invoice; items: InvoiceItem[]; submissions: Submission[]; amountInWords: string; company: Company;
  patient?: { id: number; fullName: string };
  statusMessage?: string;
}

export interface RucInfo {
  ruc: string; razonSocial: string; estado: string; condicion: string;
  direccion: string; distrito: string; provincia: string; departamento: string; tipo: string;
}

export interface ConversationThread {
  phone: string; patientId: number | null; name: string; aiEnabled: boolean; manualUntil: string | null;
  needsAttention: boolean; unread: number; lastMessageAt: string | null; lastPreview: string | null;
}

export interface ConversationMessage {
  id: string; direction: 'IN' | 'OUT'; source: 'PACIENTE' | 'IA' | 'PERSONAL' | 'SISTEMA'; body: string;
  intent: string | null; engine: string | null; status: string | null; author: string | null; at: string;
}

export interface ConversationDetail {
  thread: { phone: string; patientId: number | null; name: string | null; aiEnabled: boolean; manualUntil: string | null; needsAttention: boolean; silence: string | null };
  messages: ConversationMessage[];
  upcoming?: { id: number; startAt: string; dentist: string; reason: string; status: string }[];
}

export interface AssistantSettings {
  mode: 'AUTOMATICO' | 'FUERA_HORARIO' | 'APAGADO'; pauseMinutes: number; openNow: boolean; schedule: string; knowledgeDocs: number;
  model: { proveedor: string; modelo: string; externo: boolean; ultimoError: string | null };
}

export interface FaqItem { id: number; question: string; answer: string; active: boolean; updatedAt?: string; updatedBy?: string; }

export interface AssistantTest { text: string; intent: string; needsHuman: boolean; engine: string; sources: string[]; ms: number; }

export interface OnlineConfig { provider: string; enabled: boolean; testMode: boolean; methods: string[]; message: string; }
export interface OnlineCharge {
  id: number; reference: string; patientId: number; treatmentItemId: number | null; amount: number; description: string;
  status: 'PENDIENTE' | 'APROBADO' | 'CANCELADO' | 'VENCIDO'; statusLabel: string; checkoutUrl: string | null;
  mpPaymentId: string | null; mpStatus: string | null; mpStatusDetail: string | null; method: string | null;
  paymentId: number | null; expiresAt: string; createdAt: string; source: string; testMode: boolean;
  invoiceId?: number; invoice?: string; invoiceStatus?: string;
}

/** Comprobante emitido automáticamente para un cobro. */
export interface PaymentInvoice { invoiceId: number; invoice: string; status: string; message: string; }

/** [código, nombre, hijos] — departamentos → provincias → distritos (INEI). */
export type UbigeoDist = [string, string];
export type UbigeoProv = [string, string, UbigeoDist[]];
export type UbigeoDep = [string, string, UbigeoProv[]];
