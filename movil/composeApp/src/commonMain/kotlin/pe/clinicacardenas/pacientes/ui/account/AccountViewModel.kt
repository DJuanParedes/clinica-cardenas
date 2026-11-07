package pe.clinicacardenas.pacientes.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pe.clinicacardenas.pacientes.data.remote.AccountDto
import pe.clinicacardenas.pacientes.data.remote.ApiException
import pe.clinicacardenas.pacientes.data.remote.PayRequest
import pe.clinicacardenas.pacientes.data.remote.PayResultDto
import pe.clinicacardenas.pacientes.data.repository.ClinicRepository

data class AccountState(
    val loading: Boolean = true,
    val data: AccountDto? = null,
    val error: String? = null,
    val paying: Boolean = false,
    val payError: String? = null,
    val paid: PayResultDto? = null,
)

/** Estado de cuenta del paciente: cargos, pagos y saldo pendiente. */
class AccountViewModel(private val repo: ClinicRepository) : ViewModel() {
    private val _state = MutableStateFlow(AccountState())
    val state: StateFlow<AccountState> = _state.asStateFlow()

    init { load() }

    fun load() {
        _state.update { it.copy(loading = it.data == null, error = null) }
        viewModelScope.launch {
            try {
                val data = repo.account()
                _state.update { it.copy(data = data) }
            } catch (e: ApiException) {
                _state.update { it.copy(error = e.message) }
            } finally {
                _state.update { it.copy(loading = false) }
            }
        }
    }

    /** Pago en línea simulado (Yape, Plin o tarjeta). Si se aprueba, la clínica emite la boleta SUNAT al instante. */
    fun pay(body: PayRequest) {
        _state.update { it.copy(paying = true, payError = null) }
        viewModelScope.launch {
            try {
                val r = repo.pay(body)
                _state.update { it.copy(paid = r) }
                load()
            } catch (e: ApiException) {
                _state.update { it.copy(payError = e.fields.values.firstOrNull() ?: e.message) }
            } finally {
                _state.update { it.copy(paying = false) }
            }
        }
    }

    fun clearPay() = _state.update { it.copy(paid = null, payError = null) }

    suspend fun invoiceUrl(id: Long): String? = runCatching { repo.invoiceUrl(id) }.getOrNull()
}
