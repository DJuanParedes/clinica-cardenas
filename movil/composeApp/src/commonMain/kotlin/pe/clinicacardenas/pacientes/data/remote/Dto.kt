package pe.clinicacardenas.pacientes.data.remote

import kotlinx.serialization.Serializable

/* Modelos del API REST de la clínica (/api/portal). Los nombres siguen el JSON del backend Spring Boot. */

@Serializable
data class ClinicDto(val name: String, val address: String, val phone: String)

@Serializable
data class PatientDto(
    val id: Long,
    val firstName: String,
    val fullName: String,
    val docType: String,
    val docNumber: String,
    val phone: String? = null,
    val email: String? = null,
    val notifyChannel: String = "WHATSAPP",
    val clinic: ClinicDto,
)

@Serializable
data class SessionDto(val token: String, val expiresInMinutes: Long = 0, val patient: PatientDto)

@Serializable
data class LoginRequest(val docType: String, val docNumber: String, val password: String)

@Serializable
data class CodeRequest(val docType: String, val docNumber: String, val birthDate: String)

@Serializable
data class CodeResponse(val message: String, val sentTo: String? = null, val demoCode: String? = null)

@Serializable
data class ActivateRequest(val docType: String, val docNumber: String, val code: String, val password: String, val dataConsent: Boolean = false)

@Serializable
data class ChannelRequest(val channel: String)

@Serializable
data class DentistDto(val fullName: String, val specialty: String, val color: String = "#00A3D4")

@Serializable
data class AppointmentDto(
    val id: Long,
    val startAt: String,
    val endAt: String,
    val durationMin: Int,
    val reason: String,
    val status: String,
    val dentist: DentistDto,
    val canConfirm: Boolean = false,
    val canReschedule: Boolean = false,
    val canCancel: Boolean = false,
    val dentistId: Long? = null,
)

@Serializable
data class AppointmentsDto(
    val next: AppointmentDto? = null,
    val upcoming: List<AppointmentDto> = emptyList(),
    val past: List<AppointmentDto> = emptyList(),
)

@Serializable
data class MovementDto(
    val date: String? = null,
    val type: String,
    val concept: String,
    val detail: String? = null,
    val charge: Double = 0.0,
    val credit: Double = 0.0,
    val balance: Double = 0.0,
)

@Serializable
data class AccountDto(
    val balance: Double = 0.0,
    val inFavor: Double = 0.0,
    val charges: Double = 0.0,
    val credits: Double = 0.0,
    val movements: List<MovementDto> = emptyList(),
)

@Serializable
data class PingDto(val ok: Boolean, val clinic: ClinicDto)

@Serializable
data class ApiErrorDto(
    val status: Int = 0,
    val code: String? = null,
    val message: String? = null,
    val fields: Map<String, String>? = null,
)

// ---------------------------------------------------------------- reservas desde la app

@Serializable
data class BookingDentistDto(val id: Long, val fullName: String, val specialty: String = "", val color: String = "#00A3D4")

@Serializable
data class ReasonDto(val code: String, val label: String, val durationMin: Int)

@Serializable
data class BookingOptionsDto(val dentists: List<BookingDentistDto> = emptyList(), val reasons: List<ReasonDto> = emptyList())

@Serializable
data class PortalSlotsDto(val date: String, val durationMin: Int = 30, val works: Boolean = true, val slots: List<String> = emptyList())

@Serializable
data class BookRequest(val dentistId: Long, val startAt: String, val reasonCode: String, val notes: String? = null)

@Serializable
data class RescheduleRequest(val dentistId: Long?, val startAt: String)

@Serializable
data class CancelRequest(val reason: String)

@Serializable
data class MessageDto(val message: String = "", val id: Long? = null)

// ---------------------------------------------------------------- avisos, pagos, documentos y encuesta

@Serializable
data class NoticeDto(val id: Long, val kind: String, val title: String, val body: String, val createdAt: String, val readAt: String? = null)

@Serializable
data class NoticesDto(val unread: Long = 0, val items: List<NoticeDto> = emptyList())

@Serializable
data class PayRequest(
    val amount: Double,
    val method: String,
    val approvalCode: String? = null,
    val cardNumber: String? = null,
    val cardExpiry: String? = null,
    val cardCvv: String? = null,
)

@Serializable
data class PayResultDto(val operationId: String = "", val amount: Double = 0.0, val invoiceId: Long? = null, val invoice: String? = null, val message: String = "")

@Serializable
data class OnlineConfigDto(val enabled: Boolean = false, val testMode: Boolean = false, val message: String = "")

@Serializable
data class OnlineAmount(val amount: Double)

@Serializable
data class OnlineChargeDto(
    val id: Long,
    val amount: Double = 0.0,
    val status: String = "PENDIENTE",
    val statusLabel: String = "",
    val checkoutUrl: String? = null,
    val mpPaymentId: String? = null,
    val mpStatus: String? = null,
)

@Serializable
data class InvoiceItemDto(val id: Long, val number: String, val type: String, val date: String, val total: Double, val status: String)

@Serializable
data class DocFileDto(val id: Long, val kind: String, val title: String, val date: String, val contentType: String)

@Serializable
data class DocumentsDto(val invoices: List<InvoiceItemDto> = emptyList(), val files: List<DocFileDto> = emptyList())

@Serializable
data class LinkDto(val url: String)

@Serializable
data class PendingSurveyDto(val appointmentId: Long, val startAt: String, val reason: String, val dentist: String)

@Serializable
data class SurveyPendingDto(val pending: PendingSurveyDto? = null)

@Serializable
data class SurveyRequest(val appointmentId: Long, val score: Int, val comment: String?)

/** Promoción vigente publicada por la clínica (/api/public/promotions). */
@Serializable
data class PromoDto(
    val id: Long,
    val name: String,
    val procedure: String? = null,
    val listPrice: Double? = null,
    val finalPrice: Double? = null,
    val discountType: String? = null,
    val value: Double? = null,
    val validTo: String? = null,
    val conditions: String? = null,
)

/** Documento legal vigente (/api/public/legal/{code}). */
@Serializable
data class LegalDto(val code: String, val version: String, val title: String, val body: String)

// ---------- Mi historia: tratamientos, pagos y ficha (/api/portal/treatments)
@Serializable
data class PortalItemDto(val id: Long, val procedure: String, val tooth: Int? = null, val price: Double = 0.0, val status: String = "",
                         val doneAt: String? = null, val doneBy: String? = null, val paid: Double = 0.0, val balance: Double = 0.0)

@Serializable
data class PortalSessionDto(val date: String? = null, val notes: String? = null, val dentist: String? = null, val procedure: String? = null)

@Serializable
data class PortalPlanDto(val id: Long, val title: String, val status: String = "", val createdAt: String? = null, val dentist: String? = null,
                         val items: List<PortalItemDto> = emptyList(), val sessions: List<PortalSessionDto> = emptyList(),
                         val total: Double = 0.0, val paid: Double = 0.0, val balance: Double = 0.0, val doneCount: Int = 0, val itemCount: Int = 0)

@Serializable
data class PortalPaymentDto(val id: Long, val paidAt: String? = null, val amount: Double = 0.0, val method: String = "", val status: String = "", val procedure: String? = null)

@Serializable
data class PortalVisitDto(val date: String? = null, val reason: String? = null, val status: String = "", val dentist: String? = null)

@Serializable
data class ClinicalSummaryDto(val allergies: String? = null, val medication: String? = null, val background: String? = null, val bloodType: String? = null, val updatedAt: String? = null)

@Serializable
data class TreatmentsDto(val plans: List<PortalPlanDto> = emptyList(), val total: Double = 0.0, val paid: Double = 0.0, val balance: Double = 0.0,
                         val payments: List<PortalPaymentDto> = emptyList(), val visits: List<PortalVisitDto> = emptyList(), val clinical: ClinicalSummaryDto? = null)

// ---------- consentimientos y archivos solicitados
@Serializable
data class ConsentRecordDto(val id: Long, val code: String, val version: String = "", val channel: String? = null, val acceptedBy: String? = null,
                            val acceptedAt: String? = null, val fileId: Long? = null, val title: String? = null)

@Serializable
data class ConsentsDto(val records: List<ConsentRecordDto> = emptyList(), val signedFileId: Long? = null)

@Serializable
data class FileRequestDto(val id: Long, val title: String, val description: String? = null, val kind: String = "", val status: String = "",
                          val dueDate: String? = null, val fileId: Long? = null, val planTitle: String? = null, val requestedAt: String? = null)

@Serializable
data class ContactRequest(val phone: String, val email: String)

@Serializable
data class ContactResult(val phone: String = "", val email: String = "", val message: String = "")
