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
