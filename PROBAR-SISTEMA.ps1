[Console]::OutputEncoding = New-Object System.Text.UTF8Encoding($false)
$OutputEncoding = [Console]::OutputEncoding
$ErrorActionPreference = "Stop"
$baseUrl = "http://localhost:8090"
$localEnv = Join-Path (Split-Path -Parent $MyInvocation.MyCommand.Path) ".env"
function Get-PrivateEnvValue([string]$key) {
  $fromProcess=[Environment]::GetEnvironmentVariable($key)
  if(-not [string]::IsNullOrWhiteSpace($fromProcess)){ return $fromProcess.Trim() }
  if(Test-Path $localEnv){
    $line=Get-Content $localEnv | Where-Object { $_ -match ('^'+[regex]::Escape($key)+'=') } | Select-Object -First 1
    if($line){ return (($line -split '=',2)[1]).Trim() }
  }
  return ''
}
$localOtpCode=Get-PrivateEnvValue 'LOCAL_OTP_CODE'
$platformAdminEmail=Get-PrivateEnvValue 'BOOTSTRAP_ADMIN_EMAIL'
if($localOtpCode -notmatch '^\d{6}$'){ throw "LOCAL_OTP_CODE local no disponible. Ejecuta PREPARAR-ENTORNO-LOCAL.bat." }
if($platformAdminEmail -notmatch '^[^@\s]+@[^@\s]+\.[^@\s]+$'){ throw "BOOTSTRAP_ADMIN_EMAIL privado no disponible. Ejecuta PREPARAR-ENTORNO-LOCAL.bat." }
[System.Net.ServicePointManager]::Expect100Continue = $false
[System.Net.ServicePointManager]::DefaultConnectionLimit = 64
function Json($value) { return ($value | ConvertTo-Json -Depth 12) }
function Merge-Headers($headers) {
  $merged = @{ "Accept" = "application/json"; "Cache-Control" = "no-cache" }
  if ($null -ne $headers) {
    foreach ($key in $headers.Keys) { $merged[$key] = $headers[$key] }
  }
  return $merged
}
function Status-Code($errorRecord) {
  try {
    if ($null -ne $errorRecord.Exception.Response -and $null -ne $errorRecord.Exception.Response.StatusCode) {
      return [int]$errorRecord.Exception.Response.StatusCode
    }
  } catch {}
  return $null
}
function Get-WithRetry([string]$uri, $headers, [int]$attempts = 8) {
  $lastError = $null
  for ($attempt = 1; $attempt -le $attempts; $attempt++) {
    try {
      $response = Invoke-RestMethod -Uri $uri -Method Get -Headers (Merge-Headers $headers) -DisableKeepAlive -TimeoutSec 30 -ErrorAction Stop
      # Windows PowerShell 5.1 puede entregar un array JSON como un único objeto de pipeline.
      # Lo enumeramos explícitamente para que @(...), Where-Object y Count trabajen con los elementos reales.
      if ($response -is [System.Array]) {
        foreach ($item in $response) { Write-Output $item }
        return
      }
      return $response
    }
    catch {
      $lastError = $_
      $status = Status-Code $_
      $transient = ($null -eq $status -or $status -eq 408 -or $status -eq 429 -or $status -ge 500)
      if (-not $transient -or $attempt -ge $attempts) {
        $statusText = if ($null -eq $status) { "SIN_HTTP" } else { [string]$status }
        Write-Host "ERROR GET $uri (HTTP $statusText)" -ForegroundColor Red
        if ($_.ErrorDetails.Message) { Write-Host $_.ErrorDetails.Message -ForegroundColor Red }
        throw
      }
      $delay = [Math]::Min(2 * $attempt, 8)
      Write-Host "Reintentando GET $uri ($attempt/$attempts) después de un corte transitorio..." -ForegroundColor Yellow
      Start-Sleep -Seconds $delay
    }
  }
  throw $lastError
}
function Invoke-Json([string]$method, [string]$path, $body, $headers) {
  $params = @{
    Uri = "$baseUrl$path"
    Method = $method
    Headers = (Merge-Headers $headers)
    TimeoutSec = 30
    DisableKeepAlive = $true
    ErrorAction = "Stop"
  }
  if ($null -ne $body) {
    $params["ContentType"] = "application/json; charset=utf-8"
    $params["Body"] = Json $body
  }
  try {
    return Invoke-RestMethod @params
  }
  catch {
    $status = Status-Code $_
    $statusText = if ($null -eq $status) { "SIN_HTTP" } else { [string]$status }
    Write-Host "ERROR $method $path (HTTP $statusText)" -ForegroundColor Red
    if ($_.ErrorDetails.Message) { Write-Host $_.ErrorDetails.Message -ForegroundColor Red }
    throw
  }
}
function Invoke-ExpectedStatus([string]$method, [string]$path, $body, $headers, [int]$expectedStatus) {
  $params = @{
    Uri = "$baseUrl$path"
    Method = $method
    Headers = (Merge-Headers $headers)
    TimeoutSec = 30
    DisableKeepAlive = $true
    UseBasicParsing = $true
    ErrorAction = "Stop"
  }
  if ($null -ne $body) {
    $params["ContentType"] = "application/json; charset=utf-8"
    $params["Body"] = Json $body
  }
  $actualStatus = $null
  try {
    $response = Invoke-WebRequest @params
    $actualStatus = [int]$response.StatusCode
  }
  catch {
    $actualStatus = Status-Code $_
    if ($null -eq $actualStatus) { throw }
  }
  if ($actualStatus -ne $expectedStatus) {
    throw "Se esperaba HTTP $expectedStatus en $method $path, pero se obtuvo HTTP $actualStatus"
  }
}
function Expect-HttpSuccess([string]$method, [string]$path, $body, $headers) {
  $params = @{
    Uri = "$baseUrl$path"
    Method = $method
    Headers = (Merge-Headers $headers)
    TimeoutSec = 30
    DisableKeepAlive = $true
    UseBasicParsing = $true
    ErrorAction = "Stop"
  }
  if ($null -ne $body) {
    $params["ContentType"] = "application/json; charset=utf-8"
    $params["Body"] = Json $body
  }
  try {
    $response = Invoke-WebRequest @params
    $status = [int]$response.StatusCode
    if ($status -lt 200 -or $status -ge 300) { throw "HTTP $status" }
    return $status
  }
  catch {
    $status = Status-Code $_
    $statusText = if ($null -eq $status) { "SIN_HTTP" } else { [string]$status }
    Write-Host "ERROR $method $path (HTTP $statusText)" -ForegroundColor Red
    if ($_.ErrorDetails.Message) { Write-Host $_.ErrorDetails.Message -ForegroundColor Red }
    throw
  }
}
function Login([string]$email, [string]$password) {
  return Invoke-Json "Post" "/api/auth/login" @{email=$email;password=$password} @{}
}
function Headers($login) { return @{Authorization="Bearer $($login.token)"} }
function Expect-InitialAccessBlocked([string]$email, [string]$password) {
  Invoke-ExpectedStatus "Post" "/api/auth/login" @{email=$email;password=$password} @{} 401
  Write-Host "OK  Primer acceso no valida DNI por login y obliga recuperación OTP: $email" -ForegroundColor Yellow
}
function Get-Api([string]$path, $headers) { return Get-WithRetry "$baseUrl$path" $headers }
function Post-Api([string]$path, $body, $headers) { return Invoke-Json "Post" $path $body $headers }
function Put-Api([string]$path, $body, $headers) { return Invoke-Json "Put" $path $body $headers }
function Patch-Api([string]$path, $headers) { return Invoke-Json "Patch" $path @{} $headers }
function PatchBody-Api([string]$path, $body, $headers) { return Invoke-Json "Patch" $path $body $headers }
function Ok([string]$text) { Write-Host "OK  $text" -ForegroundColor Green }
function Expect-Unauthorized([string]$path, $headers) {
  Invoke-ExpectedStatus "Get" $path $null $headers 401
  Write-Host "OK  Sesión revocada inmediatamente: $path" -ForegroundColor Yellow
}
function Expect-Forbidden([string]$path, $headers) {
  Invoke-ExpectedStatus "Get" $path $null $headers 403
  Write-Host "OK  Acceso bloqueado: $path" -ForegroundColor Yellow
}
function Expect-PostForbidden([string]$path, $body, $headers) {
  Invoke-ExpectedStatus "Post" $path $body $headers 403
  Write-Host "OK  Escritura bloqueada: $path" -ForegroundColor Yellow
}
function Expect-PatchForbidden([string]$path, $headers) {
  Invoke-ExpectedStatus "Patch" $path $null $headers 403
  Write-Host "OK  Cambio de estado bloqueado: $path" -ForegroundColor Yellow
}
function Expect-PutForbidden([string]$path, $body, $headers) {
  Invoke-ExpectedStatus "Put" $path $body $headers 403
  Write-Host "OK  Edición bloqueada: $path" -ForegroundColor Yellow
}
function Expect-PatchConflict([string]$path, $headers) {
  Invoke-ExpectedStatus "Patch" $path $null $headers 409
  Write-Host "OK  Transición inválida bloqueada: $path" -ForegroundColor Yellow
}
function Expect-PostConflict([string]$path, $body, $headers) {
  Invoke-ExpectedStatus "Post" $path $body $headers 409
  Write-Host "OK  Regla de agenda aplicada: $path" -ForegroundColor Yellow
}
function Expect-PutConflict([string]$path, $body, $headers) {
  Invoke-ExpectedStatus "Put" $path $body $headers 409
  Write-Host "OK  Edición protegida por trazabilidad: $path" -ForegroundColor Yellow
}
function Expect-PostBadRequest([string]$path, $body, $headers) {
  Invoke-ExpectedStatus "Post" $path $body $headers 400
  Write-Host "OK  Validación de monto aplicada: $path" -ForegroundColor Yellow
}
function Expect-PatchBadRequest([string]$path, $headers) {
  Invoke-ExpectedStatus "Patch" $path $null $headers 400
  Write-Host "OK  Estado inválido rechazado por backend: $path" -ForegroundColor Yellow
}
function Close-TestAppointment($appointmentId, $headers) {
  $current = Get-Api "/api/appointments/$appointmentId" $headers
  if ($current.status -eq "CANCELADO") { return }
  if ($current.status -notin @("NO_ASISTIO","PENDIENTE","CONFIRMADO","OBSERVADO")) {
    throw "No se puede cerrar la cita de prueba $appointmentId desde estado $($current.status)"
  }
  if ($current.status -ne "NO_ASISTIO") {
    Patch-Api "/api/appointments/$appointmentId/status?value=NO_ASISTIO" $headers | Out-Null
  }
  Patch-Api "/api/appointments/$appointmentId/status?value=CANCELADO" $headers | Out-Null
}
Write-Host "`n1. Gateway y solicitud web pública" -ForegroundColor Cyan
$health = Get-Api "/actuator/health" @{}
if ($health.status -ne "UP") { throw "Gateway no saludable" }
Ok "Gateway saludable"
$stamp = Get-Date -Format "HHmmssfff"
$publicInquiry = Post-Api "/api/public/inquiries" @{
  fullName="Paciente Web $stamp"; phone="987654321"; specialty="Evaluación general"; message="Prueba automática de solicitud web"
} @{}
Ok "Solicitud pública guardada con ID $($publicInquiry.id)"
$anonymousSurvey = Post-Api "/api/public/surveys" @{
  receptionScore=5;kindnessScore=5;waitTimeScore=4;explanationScore=5;doctorTreatmentScore=5;cleanlinessScore=5;comfortScore=4;priceClarityScore=5;overallScore=5;recommend=$true;anonymous=$true;contactRequested=$false;improvement="Prueba pública"
} @{}
if ($anonymousSurvey.status -ne "REGISTRADA" -or $null -eq $anonymousSurvey.id) { throw "La encuesta pública no devolvió una confirmación válida" }
Ok "Encuesta pública anónima guardada sin iniciar sesión"
Write-Host "`n2. Autenticación y roles sin usuarios demo" -ForegroundColor Cyan
# El ADMIN local no tiene una contraseña fija en código. Se activa por OTP, igual que producción.
$adminTestPassword = "Local#" + $stamp + "Aa9!"
Post-Api "/api/auth/password-reset/request" @{email=$platformAdminEmail} @{} | Out-Null
Post-Api "/api/auth/password-reset/confirm" @{email=$platformAdminEmail;code=$localOtpCode;newPassword=$adminTestPassword} @{} | Out-Null
$admin = Login $platformAdminEmail $adminTestPassword
$ha=Headers $admin
$sessionValidationStatus = Expect-HttpSuccess "Get" "/api/auth/session/validate" $null $ha
if ($sessionValidationStatus -ne 204 -and $sessionValidationStatus -ne 200) { throw "La validación de sesión ADMIN vía Gateway no respondió correctamente" }
Ok "Validación de sesión ADMIN vía Gateway estable y separada del tiempo de respuesta del microservicio"
$doctorName = "Dr. Prueba $stamp"
$doctorEmail = "doctor.$stamp@validation.local"
$receptionEmail = "recepcion.$stamp@validation.local"
$financeEmail = "finanzas.$stamp@validation.local"
$doctor = Post-Api "/api/users/local-validation/token" @{role="DOCTOR";name=$doctorName;email=$doctorEmail} $ha
$reception = Post-Api "/api/users/local-validation/token" @{role="RECEPCION";name="Recepción Validación";email=$receptionEmail} $ha
$finance = Post-Api "/api/users/local-validation/token" @{role="FINANZAS";name="Finanzas Validación";email=$financeEmail} $ha
$hd=Headers $doctor; $hr=Headers $reception; $hf=Headers $finance
$testDoctorLogin=$doctor; $hdTest=$hd
Ok "ADMIN es el único usuario persistente base; roles de validación emitidos solo en perfil local"
Expect-PatchBadRequest "/api/surveys/responses/$($anonymousSurvey.id)/status?value=ESTADO_INVENTADO" $ha
Write-Host "`n2.1 Alta real de usuario, DNI temporal y recuperación OTP" -ForegroundColor Cyan
# Limpia únicamente cuentas de validación creadas por ejecuciones anteriores de esta suite.
# No toca usuarios reales: exige dominio validation.local + nombres/patrones propios de la prueba.
$staleValidationUsers = @(Get-Api "/api/users" $ha | Where-Object {
  $_.role -ne "ADMIN" -and (
    ($_.email -match '^usuario\.\d{9}@validation\.local$' -and $_.fullName -like 'Usuario Temporal *') -or
    ($_.email -match '^doctor\.provision\.\d{9}@validation\.local$' -and $_.fullName -like 'Doctor Provision *')
  )
})
foreach ($stale in $staleValidationUsers) {
  Invoke-Json "Delete" "/api/users/local-validation/purge?email=$([uri]::EscapeDataString([string]$stale.email))" $null $ha | Out-Null
}
if ($staleValidationUsers.Count -gt 0) { Ok "Se limpiaron $($staleValidationUsers.Count) cuenta(s) temporales de una validación anterior" }
$validationDni = [string](Get-Random -Minimum 10000000 -Maximum 99999999)
$validationPhone = "+519" + ([string](Get-Random -Minimum 10000000 -Maximum 99999999))
$validationEmail = "usuario.$stamp@validation.local"
$testUser = Post-Api "/api/users" @{
  fullName="Usuario Temporal $stamp"; email=$validationEmail; dni=$validationDni; phone=$validationPhone; recoveryChannel="SMS"; role="RECEPCION"
} $ha
if (-not $testUser.mustResetPassword) { throw "El usuario nuevo no quedó con cambio de contraseña obligatorio" }
Expect-InitialAccessBlocked $validationEmail $validationDni
$validationPassword = "Valid#" + $stamp + "Aa9!"
$resetRequest = Post-Api "/api/auth/password-reset/request" @{email=$validationEmail} @{}
if ([string]::IsNullOrWhiteSpace([string]$resetRequest.message)) { throw "La solicitud OTP no devolvió confirmación genérica" }
if ($null -ne $resetRequest.destination -or $null -ne $resetRequest.channel) { throw "La recuperación expone si el correo o canal están registrados" }
Post-Api "/api/auth/password-reset/confirm" @{email=$validationEmail;code=$localOtpCode;newPassword=$validationPassword} @{} | Out-Null
$afterReset = @(Get-Api "/api/users" $ha | Where-Object { $_.id -eq $testUser.id }) | Select-Object -First 1
if ($null -eq $afterReset -or $afterReset.mustResetPassword) { throw "La recuperación OTP no liberó el primer acceso" }
$realUserLogin = Login $validationEmail $validationPassword
$hu = Headers $realUserLogin
$patientAccessStatus = Expect-HttpSuccess "Get" "/api/patients" $null $hu
if ($patientAccessStatus -ne 200) { throw "El usuario real no pudo usar su sesión después del cambio de contraseña" }
Ok "Sesión real válida después del cambio de contraseña, incluso con 0 pacientes"
Patch-Api "/api/users/$($testUser.id)/enabled?value=false" $ha | Out-Null
Expect-Unauthorized "/api/patients" $hu
Patch-Api "/api/users/$($testUser.id)/enabled?value=true" $ha | Out-Null
Expect-Unauthorized "/api/patients" $hu
Invoke-Json "Delete" "/api/users/local-validation/purge?email=$([uri]::EscapeDataString($validationEmail))" $null $ha | Out-Null
if (@(Get-Api "/api/users" $ha | Where-Object { $_.id -eq $testUser.id }).Count -ne 0) { throw "La suite local no pudo limpiar la cuenta temporal" }
Ok "DNI temporal + OTP + cambio de clave + revocación inmediata al bloquear/reactivar verificados"
# Un alta real con rol DOCTOR debe crear automáticamente su perfil de Agenda.
$provisionDni = [string](Get-Random -Minimum 10000000 -Maximum 99999999)
$provisionPhone = "+519" + ([string](Get-Random -Minimum 10000000 -Maximum 99999999))
$provisionEmail = "doctor.provision.$stamp@validation.local"
$provisionedUser = Post-Api "/api/users" @{
  fullName="Doctor Provision $stamp"; email=$provisionEmail; dni=$provisionDni; phone=$provisionPhone; recoveryChannel="WHATSAPP"; role="DOCTOR";
  doctorSpecialty="Endodoncia"; doctorBranch="Sede principal"; shiftStart="08:00"; shiftEnd="17:00"
} $ha
$provisionedDoctor = @(Get-Api "/api/scheduling/doctors" $ha | Where-Object { $_.accountEmail -eq $provisionEmail }) | Select-Object -First 1
if ($null -eq $provisionedDoctor -or -not $provisionedDoctor.active -or $provisionedDoctor.specialty -ne "Endodoncia") { throw "Crear un usuario DOCTOR no sincronizó su perfil de Agenda" }
Invoke-Json "Delete" "/api/users/$($provisionedUser.id)" $null $ha | Out-Null
$provisionedDoctorAfter = @(Get-Api "/api/scheduling/doctors" $ha | Where-Object { $_.accountEmail -eq $provisionEmail }) | Select-Object -First 1
if ($null -eq $provisionedDoctorAfter -or $provisionedDoctorAfter.active) { throw "Eliminar una cuenta DOCTOR antes del primer acceso no desactivó su perfil de Agenda" }
Ok "Alta de usuario DOCTOR sincroniza Agenda y la limpieza previa al primer acceso desactiva el perfil"
$doctors = @(Get-Api "/api/scheduling/doctors" $ha)
$chairs = @(Get-Api "/api/scheduling/chairs" $ha)
$chairName = "Sillon Prueba $stamp"
$testDoctor = Post-Api "/api/scheduling/doctors" @{
  fullName=$doctorName; accountEmail=$doctorEmail; specialty="Rehabilitacion oral"; branch="Sede principal";
  shiftStart="08:00:00"; shiftEnd="17:00:00"; active=$true
} $ha
if ($null -eq $testDoctor.id -or $testDoctor.accountEmail -ne $doctorEmail) { throw "No se pudo crear el perfil temporal de Agenda para el token DOCTOR" }
$testChair = Post-Api "/api/scheduling/chairs" @{name=$chairName;branch="Sede principal";description="Recurso exclusivo de prueba";active=$true;operationalStatus="DISPONIBLE"} $ha
$secondChairName = "Sillon Alterno $stamp"
$secondTestChair = Post-Api "/api/scheduling/chairs" @{name=$secondChairName;branch="Sede principal";description="Segundo recurso para validar buffer de limpieza";active=$true;operationalStatus="DISPONIBLE"} $ha
if ($null -eq $testChair.id -or $null -eq $secondTestChair.id) { throw "No se pudieron crear los sillones aislados de prueba" }
Ok "Doctor clínico aislado y sillones de validación disponibles"
Write-Host "`n2.2 Copias de seguridad, Excel e importación protegida" -ForegroundColor Cyan
$backupCatalog = @(Get-Api "/api/backups/catalog" $ha)
if ($backupCatalog.Count -lt 11) { throw "El catálogo de copias no devolvió todos los microservicios de datos" }
$unavailableBackupServices = @($backupCatalog | Where-Object { -not $_.available })
if ($unavailableBackupServices.Count -gt 0) { throw "Hay servicios no disponibles para respaldo: $($unavailableBackupServices.label -join ', ')" }
$backupTables = @($backupCatalog | ForEach-Object { @($_.tables) })
if ($backupTables.Count -lt 20) { throw "El catálogo de copias devolvió pocas tablas: $($backupTables.Count)" }
$backupReports = @(Get-Api "/api/backups/reports" $ha)
if ($backupReports.Count -lt 11) { throw "El catálogo de informes descargables está incompleto" }
Ok "Catálogo de copias: $($backupTables.Count) tablas y $($backupReports.Count) informes"
Expect-Forbidden "/api/backups/catalog" $hr
Expect-Forbidden "/api/backups/catalog" $hd
Expect-Forbidden "/api/backups/catalog" $hf
$patientBackupService = @($backupCatalog | Where-Object { $_.key -eq "pacientes" }) | Select-Object -First 1
$patientBackupTable = @($patientBackupService.tables) | Select-Object -First 1
if ($null -eq $patientBackupTable) { throw "No se encontró tabla de pacientes para probar la plantilla Excel" }
$templateFile = Join-Path $env:TEMP "clinica-template-$stamp.xlsx"
$globalFile = Join-Path $env:TEMP "clinica-backup-$stamp.zip"
$backupHistoryBefore = @(Get-Api "/api/governance/backups" $ha).Count
try {
  Invoke-WebRequest -Uri "$baseUrl/api/backups/tables/pacientes/$($patientBackupTable.tableName)/template" -Method Get -Headers (Merge-Headers $ha) -DisableKeepAlive -TimeoutSec 45 -OutFile $templateFile -ErrorAction Stop | Out-Null
  if (-not (Test-Path $templateFile) -or (Get-Item $templateFile).Length -lt 1000) { throw "La plantilla Excel de importación no se generó correctamente" }
  Ok "Plantilla Excel individual generada"
  Invoke-WebRequest -Uri "$baseUrl/api/backups/global" -Method Get -Headers (Merge-Headers $ha) -DisableKeepAlive -TimeoutSec 120 -OutFile $globalFile -ErrorAction Stop | Out-Null
  if (-not (Test-Path $globalFile) -or (Get-Item $globalFile).Length -lt 2000) { throw "El respaldo global ZIP no se generó correctamente" }
  $backupHistoryAfter = @(Get-Api "/api/governance/backups" $ha).Count
  if ($backupHistoryAfter -le $backupHistoryBefore) { throw "El respaldo global no quedó registrado en el historial de gobierno" }
  Ok "Respaldo global de tablas e informes generado y registrado con trazabilidad"
}
finally {
  Remove-Item $templateFile -Force -ErrorAction SilentlyContinue
  Remove-Item $globalFile -Force -ErrorAction SilentlyContinue
}
Write-Host "`n3. Recepción: paciente, agenda, llegada y solicitud web" -ForegroundColor Cyan
$random = Get-Random -Minimum 10000000 -Maximum 99999999
$patient = Post-Api "/api/patients" @{
  fullName="Paciente Prueba $stamp"; dni="$random"; birthDate="1995-05-15"; district="Cercado"; address="Dirección de prueba";
  phone="986543210"; email="paciente$stamp@example.com"; occupation="Prueba"; emergencyContact="Contacto";
  emergencyPhone="912345678"; currentCondition="Sin condición actual"; familyHistory="Sin antecedentes"; allergies="Ninguna"
} $hr
Ok "Paciente creado con ID $($patient.id)"
$outsideStart=(Get-Date).Date.AddDays(200).AddHours(7).AddMinutes(30)
$outsideEnd=$outsideStart.AddMinutes(30)
Expect-PostConflict "/api/appointments" @{
  patientId=$patient.id; patientName=$patient.fullName; doctorName=$doctorName; specialty="Rehabilitación oral";
  chair=$chairName; startAt=$outsideStart.ToString("yyyy-MM-ddTHH:mm:ss"); endAt=$outsideEnd.ToString("yyyy-MM-ddTHH:mm:ss"); reason="Prueba fuera de horario"
} $hr
Ok "Agenda rechaza citas fuera del rango operativo 08:00-22:00"
$start=(Get-Date).Date.AddDays(200).AddHours(14)
$end=$start.AddMinutes(45)
$appointment = Post-Api "/api/appointments" @{
  patientId=$patient.id; patientName=$patient.fullName; doctorName=$doctorName; specialty="Rehabilitación oral";
  chair=$chairName; startAt=$start.ToString("yyyy-MM-ddTHH:mm:ss"); endAt=$end.ToString("yyyy-MM-ddTHH:mm:ss"); reason="Evaluación automática"
} $hr
if ($appointment.status -ne "PENDIENTE") { throw "Una cita nueva no nació en estado PENDIENTE" }
Expect-PatchConflict "/api/appointments/$($appointment.id)/status?value=CONFIRMADO" $hr
Ok "Cita creada PENDIENTE; el estado intermedio CONFIRMADO está bloqueado en el flujo nuevo"
$bufferConflictStart=$end.AddMinutes(5)
$bufferConflictEnd=$bufferConflictStart.AddMinutes(30)
Expect-PostConflict "/api/appointments" @{
  patientId=$patient.id; patientName=$patient.fullName; doctorName=$doctorName; specialty="Rehabilitación oral";
  chair=$chairName; startAt=$bufferConflictStart.ToString("yyyy-MM-ddTHH:mm:ss"); endAt=$bufferConflictEnd.ToString("yyyy-MM-ddTHH:mm:ss"); reason="Debe respetar limpieza"
} $hr
$crossChair = Post-Api "/api/appointments" @{
  patientId=$patient.id; patientName=$patient.fullName; doctorName=$doctorName; specialty="Rehabilitación oral";
  chair=$secondChairName; startAt=$bufferConflictStart.ToString("yyyy-MM-ddTHH:mm:ss"); endAt=$bufferConflictEnd.ToString("yyyy-MM-ddTHH:mm:ss"); reason="Buffer de limpieza no debe bloquear otro sillón"
} $hr
Close-TestAppointment $crossChair.id $hr
Expect-PatchConflict "/api/appointments/$($crossChair.id)/status?value=CONFIRMADO" $hr # terminal-reopen-check
$bufferBoundaryStart=$end.AddMinutes(15)
$bufferBoundaryEnd=$bufferBoundaryStart.AddMinutes(30)
$bufferBoundary = Post-Api "/api/appointments" @{
  patientId=$patient.id; patientName=$patient.fullName; doctorName=$doctorName; specialty="Rehabilitación oral";
  chair=$chairName; startAt=$bufferBoundaryStart.ToString("yyyy-MM-ddTHH:mm:ss"); endAt=$bufferBoundaryEnd.ToString("yyyy-MM-ddTHH:mm:ss"); reason="Inicio exacto al terminar limpieza"
} $hr
Close-TestAppointment $bufferBoundary.id $hr
Ok "Buffer de limpieza validado: bloquea solo el sillón, permite otro sillón y libera exactamente a los 15 min"
if ($appointment.doctorAccountEmail -ne $doctorEmail) { throw "La cita no quedó vinculada al correo de la cuenta del doctor" }
$doctorAgenda = @(Get-Api "/api/appointments" $hdTest)
if (-not ($doctorAgenda | Where-Object { [string]$_.id -eq [string]$appointment.id })) {
  Write-Host "DEBUG doctor login: $($testDoctorLogin.name) <$($testDoctorLogin.email)>" -ForegroundColor Yellow
  Write-Host "DEBUG cita: $($appointment.doctorName) <$($appointment.doctorAccountEmail)> ID=$($appointment.id)" -ForegroundColor Yellow
  Write-Host "DEBUG agenda devuelta: $($doctorAgenda.Count) cita(s); IDs=$(@($doctorAgenda | ForEach-Object { $_.id }) -join ' ,')" -ForegroundColor Yellow
  throw "El doctor temporal no puede ver su propia cita"
}
Ok "El doctor autenticado ve únicamente su cita clínica de prueba"
$arrivedAppointment = Patch-Api "/api/appointments/$($appointment.id)/status?value=LLEGO" $hr
if ($arrivedAppointment.status -ne "LLEGO") { throw "Recepción no pudo confirmar la llegada desde la agenda" }
Expect-PatchConflict "/api/appointments/$($appointment.id)/status?value=NO_ASISTIO" $hr # arrived-no-show-check
Expect-PatchConflict "/api/appointments/$($appointment.id)/status?value=REPROGRAMADA" $hr # manual-reprogram-check
$encountersAdmin = @(Get-Api "/api/encounters" $ha)
$encounter = $encountersAdmin | Where-Object { [string]$_.appointmentId -eq [string]$appointment.id } | Select-Object -First 1
if ($null -eq $encounter) { throw "No se creó la Atención Clínica vinculada a la cita" }
if ($encounter.doctorAccountEmail -ne $doctorEmail) { throw "La atención central no heredó la identidad de la cuenta del doctor" }
if ($encounter.status -ne "LLEGO" -or $null -eq $encounter.arrivedAt) { throw "Confirmar llegada desde Agenda no sincronizó estado y hora real en Atención Clínica" }
Ok "Recepción confirmó la llegada desde Agenda; estado y hora real quedaron sincronizados sin abrir Atenciones clínicas"
$assignedTreatmentName = "Evaluacion y restauracion"
Expect-PutForbidden "/api/encounters/$($encounter.id)/treatment" @{treatmentName=$assignedTreatmentName;totalCost=450} $hr
Expect-PutConflict "/api/encounters/$($encounter.id)/treatment" @{treatmentName=$assignedTreatmentName;totalCost=450} $hdTest
Expect-Forbidden "/api/encounters/by-appointment/$($appointment.id)/reception-summary" $hr
Ok "Recepción no asigna costos y Doctor tampoco puede ejecutar tratamientos fuera de una proforma aceptada"
$chairDate = $start.ToString("yyyy-MM-dd")
$receptionAgenda = @(Get-Api "/api/appointments" $hr)
$chairDay = @($receptionAgenda | Where-Object { $_.chair -eq $chairName -and ([datetime]$_.startAt).ToString('yyyy-MM-dd') -eq $chairDate })
if (-not ($chairDay | Where-Object { [string]$_.id -eq [string]$appointment.id })) { throw "El calendario por sillón no encontró la cita creada" }
Ok "Calendario por sillón y detalle de cita verificados"
$safeChairSchedule = @(Get-Api "/api/encounters/chairs/schedule?date=$chairDate" $hr)
if (-not ($safeChairSchedule | Where-Object { [string]$_.appointmentId -eq [string]$appointment.id })) { throw "El mapa de sillones no obtuvo la agenda segura de Recepción" }
if ($safeChairSchedule | Where-Object { $null -ne $_.paidAmount -or $null -ne $_.productionAmount }) { throw "La agenda segura del mapa expuso datos financieros" }
Ok "Mapa de sillones usa agenda segura por fecha sin exponer datos financieros a Recepción"
# Reprogramación real: conserva la cita original y libera su horario para una nueva reserva.
$reprogramStart = $start.AddDays(1).Date.AddHours(9)
$reprogramEnd = $reprogramStart.AddMinutes(30)
$reprogramSource = Post-Api "/api/appointments" @{
  patientId=$patient.id; patientName=$patient.fullName; doctorName=$doctorName; specialty="Rehabilitación oral";
  chair=$chairName; startAt=$reprogramStart.ToString("yyyy-MM-ddTHH:mm:ss"); endAt=$reprogramEnd.ToString("yyyy-MM-ddTHH:mm:ss"); reason="Prueba de reprogramación"
} $hr
$noShowBeforeReprogram = Patch-Api "/api/appointments/$($reprogramSource.id)/status?value=NO_ASISTIO" $hr
if ($noShowBeforeReprogram.status -ne "NO_ASISTIO" -or $noShowBeforeReprogram.attendanceOutcome -ne "NO_ASISTIO" -or $noShowBeforeReprogram.resolution -ne "PENDIENTE_DECISION") { throw "La inasistencia no abrió correctamente la decisión reprogramar/cerrar" }
$reprogramTargetStart = $reprogramStart.AddHours(2)
$reprogramTargetEnd = $reprogramTargetStart.AddMinutes(30)
$reprogramTarget = Post-Api "/api/appointments/$($reprogramSource.id)/reprogram" @{
  patientId=$patient.id; patientName=$patient.fullName; doctorName=$doctorName; specialty="Rehabilitación oral";
  chair=$chairName; startAt=$reprogramTargetStart.ToString("yyyy-MM-ddTHH:mm:ss"); endAt=$reprogramTargetEnd.ToString("yyyy-MM-ddTHH:mm:ss"); reason="Reprogramada por prueba funcional"
} $hr
$reprogramHistorical = Get-Api "/api/appointments/$($reprogramSource.id)" $hr
if ($reprogramHistorical.status -ne "REPROGRAMADA" -or $reprogramHistorical.attendanceOutcome -ne "NO_ASISTIO" -or $reprogramHistorical.resolution -ne "REPROGRAMADA" -or $reprogramTarget.status -ne "PENDIENTE" -or $null -ne $reprogramTarget.attendanceOutcome) { throw "La reprogramación no conservó la inasistencia ni creó una nueva cita limpia como PENDIENTE" }
if ([string]$reprogramHistorical.reprogrammedToAppointmentId -ne [string]$reprogramTarget.id -or [string]$reprogramTarget.reprogrammedFromAppointmentId -ne [string]$reprogramHistorical.id) { throw "La reprogramación no enlazó cita anterior y nueva cita" }
$reprogramEncounters = @(Get-Api "/api/encounters" $ha)
$reprogramEncounter = $reprogramEncounters | Where-Object { [string]$_.appointmentId -eq [string]$reprogramTarget.id } | Select-Object -First 1
if ($null -eq $reprogramEncounter -or ($reprogramEncounters | Where-Object { [string]$_.appointmentId -eq [string]$reprogramSource.id })) { throw "La Atención central no se trasladó a la nueva cita reprogramada" }
$freedSlotAppointment = Post-Api "/api/appointments" @{
  patientId=$patient.id; patientName=$patient.fullName; doctorName=$doctorName; specialty="Rehabilitación oral";
  chair=$chairName; startAt=$reprogramStart.ToString("yyyy-MM-ddTHH:mm:ss"); endAt=$reprogramEnd.ToString("yyyy-MM-ddTHH:mm:ss"); reason="Validación de horario liberado"
} $hr
if ($null -eq $freedSlotAppointment.id) { throw "El horario original no quedó disponible después de reprogramar" }
Close-TestAppointment $freedSlotAppointment.id $hr
$closedNoShow = Get-Api "/api/appointments/$($freedSlotAppointment.id)" $hr
if ($closedNoShow.status -ne "CANCELADO" -or $closedNoShow.attendanceOutcome -ne "NO_ASISTIO" -or $closedNoShow.resolution -ne "CERRADA_SIN_REPROGRAMAR") { throw "Cerrar una inasistencia perdió su resultado histórico" }
Ok "NO ASISTIÓ permite dos salidas claras: reprogramar conservando historial o cerrar sin reprogramar"
$inquiries = Get-Api "/api/inquiries" $hr
if (-not ($inquiries | Where-Object { [string]$_.id -eq [string]$publicInquiry.id })) { throw "Recepción no ve la solicitud web" }
Patch-Api "/api/inquiries/$($publicInquiry.id)/status?value=CONTACTADA" $hr | Out-Null
Expect-PatchBadRequest "/api/inquiries/$($publicInquiry.id)/status?value=ESTADO_INVENTADO" $hr
Ok "Recepción consultó y actualizó la solicitud web"
Write-Host "`n4. Doctor: agenda clínica, odontograma, presupuesto y laboratorio" -ForegroundColor Cyan
$emergency = Post-Api "/api/emergencies" @{
  patientId=$patient.id; patientName=$patient.fullName; priority="MEDIA"; emergencyType="Dolor agudo"; painLevel=6; symptoms="Prueba automática fuera de Recepción"
} $ha
Ok "Emergencia de prueba creada por Administración para validar el flujo del Doctor"
Patch-Api "/api/encounters/$($encounter.id)/start" $hdTest | Out-Null
$appointmentDuringCare = Get-Api "/api/appointments/$($appointment.id)" $hdTest
if ($appointmentDuringCare.status -ne "EN_PROCESO") { throw "Iniciar Atención Clínica no sincronizó la cita a EN_PROCESO" }
Patch-Api "/api/emergencies/$($emergency.id)/attend?doctor=$([uri]::EscapeDataString($doctorName))" $hdTest | Out-Null
$fdiCatalog = Get-Api "/api/odontograms/standard" $hdTest
if ($fdiCatalog.standard -ne "FDI_ISO_3950_2016" -or
    -not (@($fdiCatalog.permanentTeeth) -contains 18) -or
    -not (@($fdiCatalog.primaryTeeth) -contains 55)) {
  throw "El catálogo del odontograma no expone la numeración permanente y temporal FDI / ISO 3950"
}
$fdiState = @{
  standard="FDI_ISO_3950_2016"
  dentition="MIXTA"
  cariesRisk="ALTO"
  riskFactors=@("Caries activa", "Consumo frecuente de azúcares")
  protectiveFactors=@("Cepillado con pasta fluorada")
  assessedAt=(Get-Date).ToString("yyyy-MM-dd")
  teeth=@{
    "16"=@{condition="CARIES";surfaces=@("O","M");cariesStage="MODERADA";cariesActivity="ACTIVA";diagnosis="Caries oclusomesial activa";treatment="Control de riesgo y tratamiento conservador"}
    "11"=@{condition="RESTAURACIÓN";surfaces=@("I")}
    "55"=@{condition="SELLANTE";surfaces=@("O")}
  }
}
$odontogram = Put-Api "/api/odontograms" @{
  patientId=$patient.id; patientName=$patient.fullName; stateJson=(Json $fdiState); diagnosis="Caries activa en pieza 16"; treatment="Control de riesgo y restauración conservadora"
} $hdTest
$storedFdiState = $odontogram.stateJson | ConvertFrom-Json
if ([string]$odontogram.patientId -ne [string]$patient.id -or
    $odontogram.standard -ne "FDI_ISO_3950_2016" -or
    $storedFdiState.standard -ne "FDI_ISO_3950_2016" -or
    $storedFdiState.dentition -ne "MIXTA" -or
    $storedFdiState.cariesRisk -ne "ALTO" -or
    $storedFdiState.teeth.'16'.cariesStage -ne "MODERADA" -or
    $storedFdiState.teeth.'16'.cariesActivity -ne "ACTIVA") {
  throw "El odontograma no conservó la evaluación clínica estructurada FDI"
}
Invoke-ExpectedStatus "Put" "/api/odontograms" @{
  patientId=$patient.id
  patientName=$patient.fullName
  stateJson='{"standard":"FDI_ISO_3950_2016","dentition":"PERMANENTE","cariesRisk":"NO_EVALUADO","riskFactors":[],"protectiveFactors":[],"teeth":{"99":{"condition":"CARIES"}}}'
  diagnosis="Prueba inválida"
  treatment="No debe guardarse"
} $hdTest 400
Ok "Odontograma FDI guardado; denticiones, superficies y caries validadas por backend"
$budget = Post-Api "/api/budgets" @{
  patientName=$patient.fullName; doctorName=$doctorName; treatment="Restauración pieza 16";
  subtotal=450; discount=25; expiresAt=(Get-Date).AddDays(30).ToString("yyyy-MM-dd"); changeReason="Presupuesto inicial de prueba"
} $hdTest
$budgetV2 = Post-Api "/api/budgets/$($budget.id)/versions" @{
  treatment="Restauración estética pieza 16"; subtotal=480; discount=30; expiresAt=(Get-Date).AddDays(35).ToString("yyyy-MM-dd"); changeReason="Cambio de material"
} $hdTest
Ok "Presupuesto creado y versionado a V$($budgetV2.versionNumber)"
$lab = Post-Api "/api/lab-orders" @{
  patientName=$patient.fullName; doctorName=$doctorName; laboratory="Laboratorio Local"; workType="Corona";
  toothPieces="16"; material="Zirconio"; shade="A2"; sentDate=(Get-Date).ToString("yyyy-MM-dd");
  expectedDate=(Get-Date).AddDays(7).ToString("yyyy-MM-dd"); cost=320; notes="Orden automática"
} $hdTest
Ok "Orden de laboratorio creada con ID $($lab.id)"
Expect-PatchBadRequest "/api/lab-orders/$($lab.id)/status?value=ESTADO_INVENTADO" $hdTest
$encounter = Put-Api "/api/encounters/$($encounter.id)/finish" @{treatmentNames="IGNORAR-LEGACY";productionAmount=80;notes="Evaluación automatizada previa al plan";labRepetition=$false;correctiveTreatment=$false;postTreatmentEmergency=$false;followUpCompleted=$true;reason="Prueba funcional"} $hdTest
$consultationLabel = [string]$encounter.treatmentNames
if (-not $consultationLabel.StartsWith("Consulta / evalua") -or [decimal]$encounter.productionAmount -ne 0) { throw "Una consulta/evaluación sin línea de proforma generó tratamiento o producción fuera del plan aceptado" }
$appointmentCompleted = Get-Api "/api/appointments/$($appointment.id)" $hdTest
if ($appointmentCompleted.status -ne "COMPLETADO") { throw "Finalizar Atención Clínica no sincronizó la cita a COMPLETADO" }
$surveyToken = Post-Api "/api/surveys/tokens" @{encounterId=$encounter.id;patientId=$patient.id;doctorName=$doctorName;specialty="Rehabilitación oral";branch="Sede principal";chairName=$chairName} $hdTest
$linkedSurvey = Post-Api "/api/public/surveys" @{surveyCode=$surveyToken.code;receptionScore=5;kindnessScore=5;waitTimeScore=4;explanationScore=5;doctorTreatmentScore=5;cleanlinessScore=5;comfortScore=5;priceClarityScore=4;overallScore=5;recommend=$true;anonymous=$true;contactRequested=$false;likedMost="Trato profesional"} @{}
if ($linkedSurvey.status -ne "REGISTRADA") { throw "La encuesta vinculada no fue registrada" }
Ok "Atención finalizada y encuesta vinculada registrada"
Write-Host "`n4.1 Tratamientos, proformas aceptadas y sesiones clínicas trazables" -ForegroundColor Cyan
$proforma = Post-Api "/api/proformas" @{
  patientId=$patient.id;patientName=$patient.fullName;appointmentId=$appointment.id;doctorName=$doctorName;
  discountAmount=0;adjustmentAmount=0;notes="Plan aceptado después de la evaluación";
  lines=@(
    @{treatmentName="Endodoncia";toothZone="Pieza 16";quantity=1;plannedSessions=3;unitPrice=320},
    @{treatmentName="Restauracion";toothZone="Pieza 16";quantity=1;plannedSessions=1;unitPrice=160}
  )
} $hr
if ($null -eq $proforma.id -or [decimal]$proforma.totalAmount -ne 480 -or $proforma.status -ne "ACEPTADA" -or $null -eq $proforma.acceptedAt -or [string]$proforma.encounterId -ne [string]$encounter.id) { throw "La proforma no nació ACEPTADA o no quedó vinculada a la evaluación de origen" }
$proformaLines=@($proforma.lines)
if ($proformaLines.Count -ne 2 -or [string]::IsNullOrWhiteSpace([string]$proformaLines[0].lineReference) -or [int]$proformaLines[0].plannedSessions -ne 3) { throw "Las líneas de proforma no tienen referencia estable y sesiones planificadas" }
$endoLine=$proformaLines[0]
$restLine=$proformaLines[1]
$proformaList = @(Get-Api "/api/proformas?patientId=$($patient.id)" $hr)
if (-not ($proformaList | Where-Object { [string]$_.id -eq [string]$proforma.id })) { throw "Recepción no pudo volver a leer la proforma persistida" }
$proformaHistory = @(Get-Api "/api/proformas/$($proforma.id)/history" $hr)
if (-not ($proformaHistory | Where-Object { $_.action -eq "ACEPTADA" })) { throw "La proforma aceptada no generó historial auditable" }
Ok "La proforma nace ACEPTADA, con fecha de aceptación, líneas estables y sesiones planificadas"
$discountPlan = Post-Api "/api/proformas" @{
  patientId=$patient.id;patientName=$patient.fullName;doctorName=$doctorName;discountAmount=30;adjustmentAmount=0;
  adjustmentReason="Descuento de validación autorizado";authorizedBy="Administrador de validación";notes="Validación de distribución de descuento";
  lines=@(
    @{treatmentName="Profilaxis";quantity=1;plannedSessions=1;unitPrice=100},
    @{treatmentName="Restauracion";quantity=1;plannedSessions=1;unitPrice=200}
  )
} $hr
if ([decimal]$discountPlan.totalAmount -ne 270) { throw "La proforma con descuento no calculó el total aceptado correctamente" }
$discountBase = Get-Api "/api/proformas/$($discountPlan.id)/billing-base" $hr
$allocatedTotal = [decimal]0
foreach($line in @($discountBase.lines)){ $allocatedTotal += [decimal]$line.acceptedAmount }
if ($allocatedTotal -ne 270) { throw "Los importes aceptados por tratamiento no suman exactamente el total de la proforma después del descuento" }
Patch-Api "/api/proformas/$($discountPlan.id)/status?value=CANCELADA" $hr | Out-Null
Ok "Descuentos/ajustes se distribuyen por tratamiento sin inflar producción ni cobranza"
# Primera sesión del tratamiento: una cita de tratamiento NO puede existir suelta.
$treatmentStart1=$start.AddDays(6).Date.AddHours(10)
$treatmentEnd1=$treatmentStart1.AddMinutes(45)
$treatmentAppointment1=Post-Api "/api/appointments" @{
  patientId=$patient.id;patientName=$patient.fullName;doctorName=$doctorName;specialty="Rehabilitación oral";
  chair=$chairName;startAt=$treatmentStart1.ToString("yyyy-MM-ddTHH:mm:ss");endAt=$treatmentEnd1.ToString("yyyy-MM-ddTHH:mm:ss");
  reason="Sesión 1 de Endodoncia";treatmentName=$endoLine.treatmentName;proformaId=$proforma.id;proformaCode=$proforma.code;proformaLineReference=$endoLine.lineReference
} $hr
Expect-PatchForbidden "/api/appointments/$($treatmentAppointment1.id)/status?value=LLEGO" $hdTest
$treatmentAppointment1=Patch-Api "/api/appointments/$($treatmentAppointment1.id)/status?value=LLEGO" $hr
Expect-PatchConflict "/api/appointments/$($treatmentAppointment1.id)/status?value=CANCELADO" $hr
$badMoveStart=$treatmentStart1.AddHours(2);$badMoveEnd=$badMoveStart.AddMinutes(45)
Expect-PostConflict "/api/appointments/$($treatmentAppointment1.id)/reprogram" @{
  patientId=$patient.id;patientName=$patient.fullName;doctorName=$doctorName;specialty="Rehabilitación oral";chair=$chairName;
  startAt=$badMoveStart.ToString("yyyy-MM-ddTHH:mm:ss");endAt=$badMoveEnd.ToString("yyyy-MM-ddTHH:mm:ss");reason="No debe moverse porque ya llegó";
  treatmentName=$endoLine.treatmentName;proformaId=$proforma.id;proformaCode=$proforma.code;proformaLineReference=$endoLine.lineReference
} $hr
$treatmentEncounter1=@(Get-Api "/api/encounters" $ha) | Where-Object { [string]$_.appointmentId -eq [string]$treatmentAppointment1.id } | Select-Object -First 1
if ($null -eq $treatmentEncounter1 -or [string]$treatmentEncounter1.proformaLineReference -ne [string]$endoLine.lineReference) { throw "La primera sesión no creó Atención Clínica vinculada a la línea de proforma" }
Patch-Api "/api/encounters/$($treatmentEncounter1.id)/start" $hdTest | Out-Null
if ((Get-Api "/api/appointments/$($treatmentAppointment1.id)" $hdTest).status -ne "EN_PROCESO") { throw "La cita de tratamiento no pasó a EN_PROCESO al iniciar la atención" }
$treatmentEncounter1=Put-Api "/api/encounters/$($treatmentEncounter1.id)/finish" @{notes="Primera sesión completada";followUpCompleted=$true;reason="Sesión clínica"} $hdTest
if ((Get-Api "/api/appointments/$($treatmentAppointment1.id)" $hdTest).status -ne "COMPLETADO") { throw "La cita de tratamiento no pasó a COMPLETADO al finalizar la atención" }
# Segunda sesión: NO ASISTIÓ abre exactamente reprogramar/cerrar; al reprogramar vuelve a PENDIENTE.
$treatmentStart2=$start.AddDays(7).Date.AddHours(10)
$treatmentEnd2=$treatmentStart2.AddMinutes(45)
$treatmentAppointment2=Post-Api "/api/appointments" @{
  patientId=$patient.id;patientName=$patient.fullName;doctorName=$doctorName;specialty="Rehabilitación oral";
  chair=$chairName;startAt=$treatmentStart2.ToString("yyyy-MM-ddTHH:mm:ss");endAt=$treatmentEnd2.ToString("yyyy-MM-ddTHH:mm:ss");
  reason="Sesión 2 de Endodoncia";treatmentName=$endoLine.treatmentName;proformaId=$proforma.id;proformaCode=$proforma.code;proformaLineReference=$endoLine.lineReference
} $hr
$treatmentNoShow=Patch-Api "/api/appointments/$($treatmentAppointment2.id)/status?value=NO_ASISTIO" $hr
if ($treatmentNoShow.attendanceOutcome -ne "NO_ASISTIO" -or $treatmentNoShow.resolution -ne "PENDIENTE_DECISION") { throw "NO ASISTIÓ no conservó el resultado de asistencia ni abrió la decisión" }
$treatmentStart2b=$treatmentStart2.AddDays(1)
$treatmentEnd2b=$treatmentStart2b.AddMinutes(45)
# Mientras la inasistencia esté pendiente, no se puede saltar el flujo creando otra sesión paralela.
Expect-PostConflict "/api/appointments" @{
  patientId=$patient.id;patientName=$patient.fullName;doctorName=$doctorName;specialty="Rehabilitación oral";
  chair=$chairName;startAt=$treatmentStart2b.ToString("yyyy-MM-ddTHH:mm:ss");endAt=$treatmentEnd2b.ToString("yyyy-MM-ddTHH:mm:ss");
  reason="No debe crear sesión paralela con inasistencia pendiente";treatmentName=$endoLine.treatmentName;proformaId=$proforma.id;proformaCode=$proforma.code;proformaLineReference=$endoLine.lineReference
} $hr
$treatmentAppointment2b=Post-Api "/api/appointments/$($treatmentAppointment2.id)/reprogram" @{
  patientId=$patient.id;patientName=$patient.fullName;doctorName=$doctorName;specialty="Rehabilitación oral";
  chair=$chairName;startAt=$treatmentStart2b.ToString("yyyy-MM-ddTHH:mm:ss");endAt=$treatmentEnd2b.ToString("yyyy-MM-ddTHH:mm:ss");
  reason="Sesión 2 reprogramada";treatmentName=$endoLine.treatmentName;proformaId=$proforma.id;proformaCode=$proforma.code;proformaLineReference=$endoLine.lineReference
} $hr
$treatmentHistorical=Get-Api "/api/appointments/$($treatmentAppointment2.id)" $hr
if ($treatmentHistorical.status -ne "REPROGRAMADA" -or $treatmentHistorical.attendanceOutcome -ne "NO_ASISTIO" -or $treatmentHistorical.resolution -ne "REPROGRAMADA" -or $treatmentAppointment2b.status -ne "PENDIENTE") { throw "La segunda sesión no volvió correctamente al bucle después de reprogramar una inasistencia" }
$treatmentAppointment2b=Patch-Api "/api/appointments/$($treatmentAppointment2b.id)/status?value=LLEGO" $hr
$treatmentEncounter2=@(Get-Api "/api/encounters" $ha) | Where-Object { [string]$_.appointmentId -eq [string]$treatmentAppointment2b.id } | Select-Object -First 1
Patch-Api "/api/encounters/$($treatmentEncounter2.id)/start" $hdTest | Out-Null
$treatmentEncounter2=Put-Api "/api/encounters/$($treatmentEncounter2.id)/finish" @{notes="Segunda sesión completada después de reprogramar";followUpCompleted=$true;reason="Sesión clínica"} $hdTest
# Endodoncia planificó 3 sesiones y solo se ejecutaron 2: debe quedar EN_PROCESO, no fingir que se completó.
$proformaProgress=Get-Api "/api/proformas/$($proforma.id)" $hr
$endoProgress=@($proformaProgress.lines) | Where-Object { [string]$_.lineReference -eq [string]$endoLine.lineReference } | Select-Object -First 1
if ($endoProgress.treatmentStatus -ne "EN_PROCESO" -or $proformaProgress.status -ne "EN_TRATAMIENTO") { throw "El tratamiento parcial no conservó estado EN_PROCESO / EN_TRATAMIENTO" }
$billingBase=Get-Api "/api/proformas/$($proforma.id)/billing-base" $hr
$endoBilling=@($billingBase.lines) | Where-Object { [string]$_.lineReference -eq [string]$endoLine.lineReference } | Select-Object -First 1
if ([int]$endoBilling.completedSessions -ne 2 -or [int]$endoBilling.plannedSessions -ne 3) { throw "La proforma no conserva el avance real 2/3 sesiones" }
if (-not $proformaProgress.traceabilityLocked -or $proformaProgress.cancellationAllowed) { throw "La proforma iniciada no quedó protegida contra edición/cancelación que rompería trazabilidad" }
Expect-PutConflict "/api/proformas/$($proforma.id)" $proformaProgress $hr
Expect-PatchConflict "/api/proformas/$($proforma.id)/status?value=CANCELADA" $hr
Ok "Tratamiento parcial verificado: 2/3 sesiones realizadas, trazabilidad protegida y plan todavía EN TRATAMIENTO"
# Trazabilidad real de reprogramacion con proforma vinculada.
# Se usa una proforma ACEPTADA independiente para validar que la cita de origen se mueve sin perder el plan.
$traceStart = $start.AddDays(3).Date.AddHours(10)
$traceEnd = $traceStart.AddMinutes(30)
$traceAppointment = Post-Api "/api/appointments" @{
  patientId=$patient.id; patientName=$patient.fullName; doctorName=$doctorName; specialty="Rehabilitación oral";
  chair=$chairName; startAt=$traceStart.ToString("yyyy-MM-ddTHH:mm:ss"); endAt=$traceEnd.ToString("yyyy-MM-ddTHH:mm:ss"); reason="Trazabilidad proforma y reprogramación"
} $hr
$traceEncounters = @(Get-Api "/api/encounters" $ha)
$traceEncounter = $traceEncounters | Where-Object { [string]$_.appointmentId -eq [string]$traceAppointment.id } | Select-Object -First 1
if ($null -eq $traceEncounter) { throw "No se creó la Atención central para la prueba de trazabilidad de proforma" }
$traceProforma = Post-Api "/api/proformas" @{
  patientId=$patient.id;patientName=$patient.fullName;appointmentId=$traceAppointment.id;doctorName=$doctorName;
  discountAmount=0;adjustmentAmount=0;notes="Proforma temporal para validar reprogramación";
  lines=@(@{treatmentName="Control preventivo";toothZone="General";quantity=1;plannedSessions=1;unitPrice=100})
} $hr
if ($traceProforma.status -ne "ACEPTADA" -or [string]$traceProforma.encounterId -ne [string]$traceEncounter.id) { throw "La proforma aceptada temporal no quedó vinculada correctamente antes de reprogramar" }
$traceTargetStart = $traceStart.AddHours(2)
$traceTargetEnd = $traceTargetStart.AddMinutes(30)
$traceTarget = Post-Api "/api/appointments/$($traceAppointment.id)/reprogram" @{
  patientId=$patient.id; patientName=$patient.fullName; doctorName=$doctorName; specialty="Rehabilitación oral";
  chair=$chairName; startAt=$traceTargetStart.ToString("yyyy-MM-ddTHH:mm:ss"); endAt=$traceTargetEnd.ToString("yyyy-MM-ddTHH:mm:ss"); reason="Mover cita con proforma vinculada"
} $hr
$traceHistorical = Get-Api "/api/appointments/$($traceAppointment.id)" $hr
$traceMovedProforma = Get-Api "/api/proformas/$($traceProforma.id)" $hr
$traceProformaHistory = @(Get-Api "/api/proformas/$($traceProforma.id)/history" $hr)
if ($traceHistorical.status -ne "REPROGRAMADA" -or $traceTarget.status -ne "PENDIENTE") { throw "La cita con proforma no conservó correctamente el historial de reprogramación" }
if ([string]$traceMovedProforma.appointmentId -ne [string]$traceTarget.id -or [string]$traceMovedProforma.encounterId -ne [string]$traceEncounter.id) { throw "La proforma no se trasladó a la nueva cita conservando la Atención central" }
if (-not ($traceProformaHistory | Where-Object { $_.action -eq "REPROGRAMADA" })) { throw "La reprogramación de la proforma no dejó historial auditable" }
Patch-Api "/api/proformas/$($traceProforma.id)/status?value=CANCELADA" $hr | Out-Null
Close-TestAppointment $traceTarget.id $hr
Ok "Reprogramación de cita + Atención + proforma + historial verificada de extremo a extremo"
Write-Host "`n5. Finanzas: comisión y membresía" -ForegroundColor Cyan
$commissionRule = Post-Api "/api/finance/commission-rules" @{doctorName=$doctorName;commissionRate=30;active=$true} $ha
if ([decimal]$commissionRule.commissionRate -ne 30) { throw "El Administrador no pudo establecer la comisión predeterminada del doctor" }
$commissionOverride = Post-Api "/api/finance/commission-treatment-rules" @{doctorName=$doctorName;treatmentName=$endoLine.treatmentName;commissionRate=40;active=$true} $ha
if ([decimal]$commissionOverride.commissionRate -ne 40 -or $commissionOverride.treatmentName -ne $endoLine.treatmentName) { throw "El Administrador no pudo establecer una excepción de comisión por tratamiento" }
Expect-PostForbidden "/api/finance/commission-treatment-rules" @{doctorName=$doctorName;treatmentName=$restLine.treatmentName;commissionRate=99;active=$true} $hf
$commissionMonthForTest=(Get-Date -Format "yyyy-MM")
$commission = Post-Api "/api/finance/commissions/generate?month=$commissionMonthForTest&doctorName=$([uri]::EscapeDataString($doctorName))" @{} $hf
if ([decimal]$commission.grossProduction -ne 480 -or [decimal]$commission.laboratoryCosts -ne 320 -or [decimal]$commission.finalAmount -ne 58.67 -or [decimal]$commission.commissionRate -ne 36.67) { throw "La liquidación no combinó correctamente el porcentaje predeterminado y la excepción por tratamiento" }
Ok "Comisiones automáticas: 30% predeterminado + 40% para Endodoncia, con laboratorio prorrateado = S/ $($commission.finalAmount)"
$commissionReview = Patch-Api "/api/finance/commissions/$($commission.id)/status?value=$([uri]::EscapeDataString('EN REVISIÓN'))" $hf
$commissionApproved = Patch-Api "/api/finance/commissions/$($commission.id)/status?value=APROBADO" $hf
$commissionPaid = Patch-Api "/api/finance/commissions/$($commission.id)/status?value=PAGADO" $hf
if ($commissionPaid.paymentStatus -ne 'PAGADO') { throw "La liquidación no llegó a PAGADO" }
Expect-PatchConflict "/api/finance/commissions/$($commission.id)/status?value=CALCULADO" $hf
Ok "Liquidación de comisión: flujo CALCULADO -> EN REVISIÓN -> APROBADO -> PAGADO; PAGADO no se reabre"
$membership = Post-Api "/api/finance/memberships" @{
  patientName=$patient.fullName; planName="Plan preventivo"; totalSessions=4; usedSessions=0; price=400; paidAmount=200;
  startDate=(Get-Date).ToString("yyyy-MM-dd"); endDate=(Get-Date).AddMonths(6).ToString("yyyy-MM-dd"); membershipStatus="ACTIVA"
} $hf
Ok "Membresía creada con ID $($membership.id)"
Expect-PatchBadRequest "/api/finance/memberships/$($membership.id)/status?value=ESTADO_INVENTADO" $hf
Write-Host "`n5.1 Caja, pagos, gastos e inventario" -ForegroundColor Cyan
$existingCashSessions = Get-Api "/api/finance/cash-sessions" $hf
foreach ($openSession in @($existingCashSessions | Where-Object { $_.cashier -eq $financeEmail -and $_.cashStatus -eq "ABIERTA" })) {
  $amount = if ($null -eq $openSession.openingAmount) { 0 } else { $openSession.openingAmount }
  Post-Api "/api/finance/cash-sessions/$($openSession.id)/close" @{expectedAmount=$amount;actualAmount=$amount;notes="Cierre preventivo de prueba"} $hf | Out-Null
}
$cash = Post-Api "/api/finance/cash-sessions/open" @{branch="Sede principal";openingAmount=200;notes="Caja de prueba $stamp"} $hf
$pendingBeforePayment = @(Get-Api "/api/finance/patients/$($patient.id)/pending-treatments" $hr)
$pendingTarget = $pendingBeforePayment | Where-Object { [string]$_.proformaId -eq [string]$proforma.id -and $_.source -eq "PROFORMA" } | Select-Object -First 1
if ($null -eq $pendingTarget -or [decimal]$pendingTarget.balance -ne 480) { throw "Caja no cargó la proforma aceptada con su saldo pendiente" }
if (@($pendingTarget.visits) | Where-Object { [string]$_.encounterId -eq [string]$encounter.id }) { throw "Caja incluyó incorrectamente la cita de evaluación como sesión cobrable del tratamiento" }
# La evaluación/origen no puede recibir un pago específico de tratamiento.
Expect-PostConflict "/api/finance/payments" @{proformaId=$proforma.id;encounterId=$encounter.id;patientId=$patient.id;patientName=$patient.fullName;concept="No debe vincularse a evaluación";amount=10;paymentMethod="YAPE";reference="INVALID-$stamp"} $hr
# El pago se asigna a la primera sesión real de Endodoncia.
$payment = Post-Api "/api/finance/payments" @{
  proformaId=$proforma.id;proformaCode=$proforma.code;proformaLineReference=$endoLine.lineReference;
  encounterId=$treatmentEncounter1.id;appointmentId=$treatmentAppointment1.id;
  patientId=$patient.id;patientName=$patient.fullName;treatmentName=$endoLine.treatmentName;
  concept="Pago sesión Endodoncia";amount=150;paymentMethod="YAPE";reference="TEST-$stamp"
} $hr
if ($payment.registeredBy -ne $receptionEmail -or [string]$payment.proformaId -ne [string]$proforma.id -or [string]$payment.appointmentId -ne [string]$treatmentAppointment1.id -or [string]$payment.proformaLineReference -ne [string]$endoLine.lineReference) { throw "Caja no conservó la relación pago → proforma → tratamiento → cita" }
# Endodoncia cuesta S/ 320; después de pagar 150, la línea solo admite 170 aunque el plan global deba más.
Expect-PostBadRequest "/api/finance/payments" @{proformaId=$proforma.id;proformaCode=$proforma.code;proformaLineReference=$endoLine.lineReference;encounterId=$null;patientId=$patient.id;patientName=$patient.fullName;treatmentName=$endoLine.treatmentName;concept="Sobreasignación inválida";amount=200;paymentMethod="EFECTIVO";reference="OVER-$stamp"} $hr
$pendingAfterPayment = @(Get-Api "/api/finance/patients/$($patient.id)/pending-treatments" $hr)
$pendingTargetAfter = $pendingAfterPayment | Where-Object { [string]$_.proformaId -eq [string]$proforma.id -and $_.source -eq "PROFORMA" } | Select-Object -First 1
if ($null -eq $pendingTargetAfter -or [decimal]$pendingTargetAfter.balance -ne 330) { throw "El saldo de la proforma no se actualizó después del pago por cita" }
$proformaAfterPayment = Get-Api "/api/proformas/$($proforma.id)" $hr
if ([decimal]$proformaAfterPayment.paidAmount -ne 150 -or [decimal]$proformaAfterPayment.balanceAmount -ne 330) { throw "La proforma no reflejó el pago aplicado a una cita de tratamiento" }
$visitPayments=Get-Api "/api/finance/patients/$($patient.id)/visit-payments" $hf
if (-not (@($visitPayments.payments) | Where-Object { [string]$_.id -eq [string]$payment.id -and [string]$_.appointmentId -eq [string]$treatmentAppointment1.id })) { throw "El historial financiero no conserva el pago con su cita específica" }
Ok "Caja registra pago desglosado por proforma, tratamiento y cita; la evaluación de origen no se confunde con una sesión"
$cancelledPayment = Patch-Api "/api/finance/payments/$($payment.id)/status?value=ANULADO" $hf
if ($cancelledPayment.paymentStatus -ne "ANULADO") { throw "Finanzas no pudo anular el pago" }
$pendingAfterCancel = @(Get-Api "/api/finance/patients/$($patient.id)/pending-treatments" $hf)
$pendingTargetCancelled = $pendingAfterCancel | Where-Object { [string]$_.proformaId -eq [string]$proforma.id -and $_.source -eq "PROFORMA" } | Select-Object -First 1
if ($null -eq $pendingTargetCancelled -or [decimal]$pendingTargetCancelled.balance -ne 480) { throw "La anulación del pago no restituyó el saldo de la proforma" }
$proformaAfterCancel = Get-Api "/api/proformas/$($proforma.id)" $hf
if ([decimal]$proformaAfterCancel.paidAmount -ne 0 -or [decimal]$proformaAfterCancel.balanceAmount -ne 480) { throw "La proforma no revirtió el abono después de anular el pago" }
$reactivatedPayment = Patch-Api "/api/finance/payments/$($payment.id)/status?value=REGISTRADO" $hf
if ($reactivatedPayment.paymentStatus -ne "REGISTRADO") { throw "Finanzas no pudo reactivar el pago" }
$pendingAfterReactivate = @(Get-Api "/api/finance/patients/$($patient.id)/pending-treatments" $hf)
$pendingTargetReactivated = $pendingAfterReactivate | Where-Object { [string]$_.proformaId -eq [string]$proforma.id -and $_.source -eq "PROFORMA" } | Select-Object -First 1
if ($null -eq $pendingTargetReactivated -or [decimal]$pendingTargetReactivated.balance -ne 330) { throw "La reactivación del pago no volvió a aplicar el saldo" }
$proformaAfterReactivate = Get-Api "/api/proformas/$($proforma.id)" $hf
if ([decimal]$proformaAfterReactivate.paidAmount -ne 150 -or [decimal]$proformaAfterReactivate.balanceAmount -ne 330) { throw "La proforma no reaplicó el abono al reactivar el pago" }
Ok "Anulación y reactivación mantienen sincronizados pago, cita, tratamiento y proforma"
$expense = Post-Api "/api/finance/expenses" @{category="INSUMOS";description="Compra solicitada por recepción";amount=25;supplier="Proveedor prueba";documentNumber="T-$stamp"} $hr
if ($expense.expenseStatus -ne "PENDIENTE_APROBACION" -or $expense.registeredBy -ne $receptionEmail) { throw "El gasto de Recepción no quedó pendiente de aprobación" }
$receptionExpenses = @(Get-Api "/api/finance/expenses" $hr)
if (-not ($receptionExpenses | Where-Object { [string]$_.id -eq [string]$expense.id })) { throw "Recepción no puede consultar su propio gasto" }
Expect-PatchForbidden "/api/finance/expenses/$($expense.id)/status?value=APROBADO" $hr
$approvedExpense = Patch-Api "/api/finance/expenses/$($expense.id)/status?value=APROBADO" $hf
if ($approvedExpense.expenseStatus -ne "APROBADO") { throw "Finanzas no pudo aprobar el gasto registrado por Recepción" }
Post-Api "/api/finance/cash-sessions/$($cash.id)/close" @{expectedAmount=350;actualAmount=350;notes="Cierre automático"} $hf | Out-Null
Ok "Caja, pago y gasto de Recepción con aprobación de Finanzas verificados"
$financeSummary = Get-Api "/api/finance/summary" $hf
$financeAnalysis = Get-Api "/api/finance/analysis" $hf
$financePending = @(Get-Api "/api/encounters/pending-treatments" $hf)
if ($null -eq $financeSummary.paymentTotal -or $null -eq $financeSummary.expenseTotal -or $null -eq $financeAnalysis.paymentMethods) { throw "El dashboard financiero no devolvió sus indicadores principales" }
Expect-Forbidden "/api/finance/summary" $hr
Expect-Forbidden "/api/finance/analysis" $hr
Expect-Forbidden "/api/encounters/pending-treatments" $hr
Ok "Dashboard financiero operativo y oculto a Recepción"
$reportDay=(Get-Date).ToString("yyyy-MM-dd");$reportMonth=(Get-Date).ToString("yyyy-MM")
$dailyFinance=Get-Api "/api/finance/reports/daily?date=$reportDay" $hf
$monthlyFinance=Get-Api "/api/finance/reports/monthly?month=$reportMonth" $hf
if ($null -eq $dailyFinance.totalIncome -or $null -eq $monthlyFinance.paymentMethods -or -not (@($dailyFinance.rows) | Where-Object { $_.paymentMethod -eq "YAPE" })) { throw "Los informes diario/mensual no detallan ingresos y medios de pago" }
$patientIncomeRow=@($dailyFinance.rows) | Where-Object { [string]$_.patientId -eq [string]$patient.id -and $_.paymentMethod -eq "YAPE" } | Select-Object -First 1
if ($null -eq $patientIncomeRow -or $patientIncomeRow.patientType -ne "NUEVO") { throw "El informe financiero no clasificó al paciente nuevo según su primera atención" }
$commissionPreview=Get-Api "/api/finance/commission-preview?month=$reportMonth" $hf
$doctorPreview=@($commissionPreview.rows) | Where-Object { $_.doctorName -eq $doctorName } | Select-Object -First 1
if ($null -eq $doctorPreview -or [decimal]$doctorPreview.defaultRate -ne 30 -or [decimal]$doctorPreview.commissionRate -ne 36.67) { throw "El informe de comisiones no distinguió porcentaje predeterminado y tasa efectiva" }
$endoPreview=@($doctorPreview.treatments) | Where-Object { $_.treatmentName -eq $endoLine.treatmentName } | Select-Object -First 1
if ($null -eq $endoPreview -or -not $endoPreview.overrideApplied -or [decimal]$endoPreview.commissionRate -ne 40) { throw "La vista previa no mostró la excepción de comisión de Endodoncia" }
if ([decimal]$doctorPreview.agreedAmount -ne 480 -or [decimal]$doctorPreview.laboratoryCosts -ne 320 -or [decimal]$doctorPreview.commissionBase -ne 160 -or [decimal]$doctorPreview.commissionAmount -ne 58.67) { throw "La comisión mensual no aplicó la matriz doctor + tratamiento con laboratorio prorrateado" }
Expect-Forbidden "/api/finance/reports/daily?date=$reportDay" $hr
Expect-Forbidden "/api/finance/commission-preview?month=$reportMonth" $hr
Ok "Informes diario/mensual y vista de comisiones disponibles solo para Finanzas/Admin"
# Una proforma clínicamente COMPLETADA debe seguir en Caja mientras tenga saldo.
$completedPlan=Post-Api "/api/proformas" @{patientId=$patient.id;patientName=$patient.fullName;doctorName=$doctorName;discountAmount=0;adjustmentAmount=0;notes="Plan corto para validar cobranza posterior";lines=@(@{treatmentName="Profilaxis final";toothZone="General";quantity=1;plannedSessions=1;unitPrice=90})} $hr
$completedLine=@($completedPlan.lines)[0]
$completedStart=$start.AddDays(11).Date.AddHours(10);$completedEnd=$completedStart.AddMinutes(30)
$completedAppointment=Post-Api "/api/appointments" @{patientId=$patient.id;patientName=$patient.fullName;doctorName=$doctorName;specialty="Rehabilitación oral";chair=$chairName;startAt=$completedStart.ToString("yyyy-MM-ddTHH:mm:ss");endAt=$completedEnd.ToString("yyyy-MM-ddTHH:mm:ss");reason="Profilaxis final";treatmentName=$completedLine.treatmentName;proformaId=$completedPlan.id;proformaCode=$completedPlan.code;proformaLineReference=$completedLine.lineReference} $hr
Patch-Api "/api/appointments/$($completedAppointment.id)/status?value=LLEGO" $hr | Out-Null
$completedEncounter=@(Get-Api "/api/encounters" $ha) | Where-Object { [string]$_.appointmentId -eq [string]$completedAppointment.id } | Select-Object -First 1
Patch-Api "/api/encounters/$($completedEncounter.id)/start" $hdTest | Out-Null
Put-Api "/api/encounters/$($completedEncounter.id)/finish" @{notes="Tratamiento corto finalizado";followUpCompleted=$true;reason="Prueba cobranza post-tratamiento"} $hdTest | Out-Null
$completedPlanAfter=Get-Api "/api/proformas/$($completedPlan.id)" $hf
if ($completedPlanAfter.status -ne "COMPLETADA" -or [decimal]$completedPlanAfter.balanceAmount -ne 90) { throw "La proforma corta no quedó COMPLETADA con saldo pendiente" }
$completedPending=@(Get-Api "/api/finance/patients/$($patient.id)/pending-treatments" $hr) | Where-Object { [string]$_.proformaId -eq [string]$completedPlan.id } | Select-Object -First 1
if ($null -eq $completedPending -or [decimal]$completedPending.balance -ne 90) { throw "Caja ocultó una proforma COMPLETADA que todavía tenía saldo" }
$completedPayment=Post-Api "/api/finance/payments" @{proformaId=$completedPlan.id;proformaCode=$completedPlan.code;proformaLineReference=$completedLine.lineReference;encounterId=$completedEncounter.id;appointmentId=$completedAppointment.id;patientId=$patient.id;patientName=$patient.fullName;treatmentName=$completedLine.treatmentName;concept="Pago posterior a tratamiento";amount=90;paymentMethod="EFECTIVO";reference="POST-TX-$stamp"} $hr
$completedPendingAfter=@(Get-Api "/api/finance/patients/$($patient.id)/pending-treatments" $hr) | Where-Object { [string]$_.proformaId -eq [string]$completedPlan.id } | Select-Object -First 1
if ($null -ne $completedPendingAfter) { throw "Caja mantuvo un plan COMPLETADO después de pagar totalmente su saldo" }
Ok "Estado clínico y cobranza separados: una proforma COMPLETADA sigue cobrable hasta saldo cero"
$patient360Finance = Get-Api "/api/encounters/patients/$($patient.id)/360" $hf
if ($null -eq $patient360Finance.account.debtTotal) { throw "Finanzas no recibió la deuda total de Paciente 360" }
$patient360Reception = Get-Api "/api/encounters/patients/$($patient.id)/360" $hr
if ($null -ne $patient360Reception.account) { throw "Recepción recibió información de deuda no autorizada" }
if ($null -eq $patient360Reception.proformas -or -not (@($patient360Reception.proformas) | Where-Object { [string]$_.id -eq [string]$proforma.id })) { throw "Paciente 360 no devolvió la proforma del paciente" }
$plan360=@($patient360Reception.proformaDetails) | Where-Object { [string]$_.id -eq [string]$proforma.id } | Select-Object -First 1
if ($null -eq $plan360 -or $plan360.status -ne "EN_TRATAMIENTO" -or [decimal]$plan360.performedAmount -le 0 -or [decimal]$plan360.performedAmount -ge [decimal]$plan360.totalAmount) { throw "Paciente 360 no distingue plan aceptado, realizado parcial y saldo" }
$line360=@($plan360.lines) | Where-Object { [string]$_.lineReference -eq [string]$endoLine.lineReference } | Select-Object -First 1
if ($null -eq $line360 -or [int]$line360.plannedSessions -ne 3 -or [int]$line360.completedSessions -ne 2 -or $line360.status -ne "EN_PROCESO") { throw "Paciente 360 no muestra avance desglosado 2/3 de la línea de tratamiento" }
$lineAppointments360=@($line360.appointments)
if (-not ($lineAppointments360 | Where-Object { $_.attendanceOutcome -eq "NO_ASISTIO" -and $_.resolution -eq "REPROGRAMADA" -and [string]$_.reprogrammedToAppointmentId -eq [string]$treatmentAppointment2b.id })) { throw "Paciente 360 no conserva la inasistencia y su reprogramación dentro del tratamiento" }
$visit360=@($line360.visits) | Where-Object { [string]$_.appointmentId -eq [string]$treatmentAppointment1.id } | Select-Object -First 1
if ($null -eq $visit360 -or -not (@($visit360.payments) | Where-Object { [string]$_.id -eq [string]$payment.id })) { throw "Paciente 360 no desglosa el pago dentro de la cita del tratamiento correspondiente" }
if (-not (@($patient360Reception.appointmentHistory) | Where-Object { [string]$_.id -eq [string]$treatmentAppointment2.id -and $_.attendanceOutcome -eq "NO_ASISTIO" })) { throw "Paciente 360 no devolvió el historial completo de citas del paciente" }
Ok "Paciente 360: Proforma → tratamiento → citas/asistencia → sesiones clínicas → pagos, con tratamiento parcial preservado"
$noShowAnalyticsDate=$treatmentStart2.ToString("yyyy-MM-dd")
$noShowAnalytics=Get-Api "/api/encounters/chairs/analytics?from=$noShowAnalyticsDate&to=$noShowAnalyticsDate" $hf
$noShowChair=@($noShowAnalytics) | Where-Object { $_.chairName -eq $chairName } | Select-Object -First 1
if ($null -eq $noShowChair -or [int]$noShowChair.noShows -lt 1) { throw "La analítica perdió la inasistencia después de reprogramarla" }
Ok "Analítica conserva NO ASISTIÓ aunque la cita luego haya sido reprogramada"
$fromDate=$start.ToString("yyyy-MM-dd"); $toDate=$start.ToString("yyyy-MM-dd")
$chairAnalytics=Get-Api "/api/encounters/chairs/analytics?from=$fromDate&to=$toDate" $hf
if (-not ($chairAnalytics | Where-Object { $_.chairName -eq $chairName })) { throw "No se generó analítica por sillón" }
$goal = Post-Api "/api/chair-goals" @{chairName=$chairName;periodType="MENSUAL";periodStart=$fromDate;productionGoal=10000;collectionGoal=8000;utilizationGoal=75;patientGoal=50} $hf
if ($null -eq $goal.id -or -not $goal.active) { throw "La meta de sillón no se guardó activa" }
$savedFilter = Post-Api "/api/saved-filters" @{name="Filtro prueba $stamp";reportType="ANALISIS_SILLON";parametersJson="{`"from`":`"$fromDate`",`"to`":`"$toDate`"}";shared=$false} $hf
if ($null -eq $savedFilter.id -or $savedFilter.shared) { throw "El filtro guardado no respetó shared=false" }
$testTreatment = Post-Api "/api/treatments" @{name="Tratamiento prueba $stamp";category="GENERALES";specialty="Odontología general";defaultDurationMinutes=30;referencePrice=100} $ha
if ($null -eq $testTreatment.id -or -not $testTreatment.active -or $testTreatment.category -ne "GENERALES") { throw "El catálogo de tratamientos no aplicó categoría/estado correctamente" }
Ok "Análisis, meta, filtro y tratamiento de prueba guardados correctamente"
$item = Post-Api "/api/inventory/items" @{code="TEST-$stamp";name="Insumo de prueba";category="PRUEBA";unit="UNIDAD";currentStock=10;minimumStock=2;lot="L-$stamp";unitCost=5;inventoryStatus="DISPONIBLE";active=$true} $hf
Post-Api "/api/inventory/movements" @{itemId=$item.id;movementType="SALIDA";quantity=2;reason="Consumo de prueba"} $hdTest | Out-Null
$items = Get-Api "/api/inventory/items" $hf
$updatedItem = $items | Where-Object { [string]$_.id -eq [string]$item.id }
if ($updatedItem.currentStock -ne 8) { throw "El stock no se actualizó correctamente" }
Ok "Inventario actualizado y kardex registrado"
$document = Post-Api "/api/operations/documents" @{patientName=$patient.fullName;category="INFORME";fileName="informe-prueba.txt";version="1.0";notes="Metadato documental de prueba"} $hdTest
Ok "Registro documental creado con ID $($document.id)"
Expect-PatchBadRequest "/api/operations/documents/$($document.id)/status?value=ESTADO_INVENTADO" $hdTest
Write-Host "`n6. Administración: módulos y control total permitido" -ForegroundColor Cyan
$backup = Post-Api "/api/modules/backups" @{
  title="Respaldo de prueba $stamp"; subject="Todas las bases locales"; detail="Registro generado por la prueba automática";
  responsible="Administrador"; eventDate=(Get-Date).ToString("yyyy-MM-dd"); amount=0; status="COMPLETADO"
} $ha
Ok "Registro administrativo creado con ID $($backup.id)"
Expect-PatchBadRequest "/api/modules/backups/$($backup.id)/status?value=ESTADO_INVENTADO" $ha
$verifiedBackup = Post-Api "/api/governance/backups" @{backupName="Respaldo verificado $stamp";scope="Bases y documentos";location="Copia externa de prueba";sizeMb=25;checksum="TEST-$stamp";status="EXITOSO";encrypted=$true;offsiteCopy=$true;completedAt=(Get-Date).ToString("yyyy-MM-ddTHH:mm:ss");restorationTestedAt=(Get-Date).ToString("yyyy-MM-ddTHH:mm:ss");notes="Restauración validada"} $ha
Get-Api "/api/audit" $ha | Out-Null
Get-Api "/api/surveys/summary" $ha | Out-Null
Get-Api "/api/encounters/quality/summary" $ha | Out-Null
Ok "Auditoría, satisfacción, calidad y respaldo verificado consultados"
Get-Api "/api/operations/employees" $ha | Out-Null
Get-Api "/api/modules/security" $ha | Out-Null
Get-Api "/api/modules/monitoring" $ha | Out-Null
Get-Api "/api/modules/integrations" $ha | Out-Null
Get-Api "/api/modules/quality" $ha | Out-Null
Ok "Módulos administrativos consultados"
Write-Host "`n7. Restricciones por rol" -ForegroundColor Cyan
Expect-Forbidden "/api/operations/employees" $hd
Expect-Forbidden "/api/modules/security" $hr
Expect-Forbidden "/api/patients" $hf
Expect-Forbidden "/api/inquiries" $hf
Expect-PostForbidden "/api/budgets" @{patientName="No permitido";doctorName="Recepción";treatment="Prueba";subtotal=100;discount=0} $hr
Expect-PostForbidden "/api/patients" @{fullName="No permitido";dni="12345678";phone=""} $hd
Expect-Forbidden "/api/users" $hr
Expect-PostForbidden "/api/inventory/items" @{code="NO";name="No permitido";unit="UNIDAD";currentStock=1;minimumStock=0} $hd
Expect-Forbidden "/api/encounters" $hr
Expect-Forbidden "/api/emergencies" $hr
Expect-Forbidden "/api/budgets" $hr
Expect-Forbidden "/api/operations/documents" $hr
Expect-Forbidden "/api/finance/memberships" $hr
Expect-Forbidden "/api/operations/follow-ups" $hr
Expect-Forbidden "/api/modules/treatments" $hr
Expect-Forbidden "/api/modules/sterilization" $hr
Ok "Lista blanca de Recepción verificada también en backend"
Write-Host "`n8. Dashboard y reportes para cada rol" -ForegroundColor Cyan
foreach ($entry in @(@{n='ADMIN';h=$ha},@{n='DOCTOR';h=$hd},@{n='RECEPCION';h=$hr},@{n='FINANZAS';h=$hf})) {
  Get-Api "/api/dashboard" $entry.h | Out-Null
  Get-Api "/api/reports/summary" $entry.h | Out-Null
  Ok "Dashboard y reporte: $($entry.n)"
}
Patch-Api "/api/scheduling/doctors/$($testDoctor.id)/active?value=false" $ha | Out-Null
Ok "Perfil temporal del doctor desactivado al finalizar la prueba"
Write-Host "`nTODAS LAS PRUEBAS FUNCIONALES Y DE ROLES FINALIZARON CORRECTAMENTE" -ForegroundColor Green
