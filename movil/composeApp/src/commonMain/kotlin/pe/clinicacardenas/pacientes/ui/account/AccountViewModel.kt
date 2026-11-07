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
import pe.clinicacardenas.pacientes.data.repository.ClinicRepository

data class AccountState(val loading: Boolean = true, val data: AccountDto? = null, val error: String? = null)

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
}
