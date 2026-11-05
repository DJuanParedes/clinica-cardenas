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
data class ActivateRequest(val docType: String, val docNumber: String, val code: String, val password: String)

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
