# Análisis de calidad con SonarQube (Docker): backend Java (con pruebas y cobertura), web Angular, microservicios
# Node y app Kotlin. Deja el resultado en sonar/reporte-sonar.json y sonar/resumen.txt.
$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)
$root = (Get-Location).Path
$url = 'http://localhost:9000'
$claveArchivo = Join-Path $root 'sonar\.clave'

Write-Host '[1/6] Levantando SonarQube (la primera vez descarga ~800 MB y tarda unos minutos)...'
docker compose -f sonar/docker-compose.sonar.yml up -d | Out-Null
$ok = $false
for ($i = 0; $i -lt 90; $i++) {
  try { $s = Invoke-RestMethod "$url/api/system/status" -TimeoutSec 5; if ($s.status -eq 'UP') { $ok = $true; break } } catch {}
  Start-Sleep 5
}
if (-not $ok) { throw 'SonarQube no arrancó. Revisa Docker Desktop (necesita ~3 GB de memoria).' }

Write-Host '[2/6] Preparando usuario y token...'
if (Test-Path $claveArchivo) { $clave = (Get-Content $claveArchivo -Raw).Trim() } else {
  $clave = 'Sonar-' + [guid]::NewGuid().ToString('N').Substring(0, 16) + '!'
  try {
    $b = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes('admin:admin'))
    Invoke-RestMethod -Method Post "$url/api/users/change_password" -Headers @{ Authorization = "Basic $b" } -Body @{ login = 'admin'; previousPassword = 'admin'; password = $clave } | Out-Null
  } catch { Write-Host '  (la contraseña ya estaba cambiada; usando sonar/.clave)' }
  Set-Content $claveArchivo $clave
}
$auth = @{ Authorization = 'Basic ' + [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes("admin:$clave")) }
$nombre = 'analisis-' + (Get-Date -Format 'yyyyMMddHHmmss')
$token = (Invoke-RestMethod -Method Post "$url/api/user_tokens/generate" -Headers $auth -Body @{ name = $nombre }).token

Write-Host '[3/6] Backend Java: compilando, pruebas, cobertura y análisis (Maven en Docker)...'
docker run --rm --add-host=host.docker.internal:host-gateway -v "${root}\backend:/src" -v clinica-m2:/root/.m2 -w /src maven:3.9.9-eclipse-temurin-21 `
  mvn -B -q org.jacoco:jacoco-maven-plugin:0.8.12:prepare-agent verify org.jacoco:jacoco-maven-plugin:0.8.12:report `
  org.sonarsource.scanner.maven:sonar-maven-plugin:4.0.0.4121:sonar `
  "-Dsonar.host.url=http://host.docker.internal:9000" "-Dsonar.token=$token" "-Dsonar.projectKey=clinica-backend" "-Dsonar.projectName=Clínica Cárdenas · Backend" `
  "-Dsonar.coverage.jacoco.xmlReportPaths=target/site/jacoco/jacoco.xml"
if ($LASTEXITCODE -ne 0) { throw 'Falló el análisis del backend (revisa la salida de Maven arriba).' }

Write-Host '[4/6] Web Angular, microservicios Node y app Kotlin...'
docker run --rm --add-host=host.docker.internal:host-gateway -v "${root}:/usr/src" sonarsource/sonar-scanner-cli:11 `
  "-Dsonar.host.url=http://host.docker.internal:9000" "-Dsonar.token=$token" "-Dsonar.projectKey=clinica-web-servicios" `
  "-Dsonar.projectName=Clínica Cárdenas · Web, servicios y app" `
  "-Dsonar.sources=frontend/src,notificaciones,whatsapp-sim,sunat-sim,movil/composeApp/src/commonMain,movil/composeApp/src/androidMain" `
  "-Dsonar.exclusions=**/node_modules/**,**/*.spec.ts,**/build/**,**/*.png,**/res/**" "-Dsonar.sourceEncoding=UTF-8"
if ($LASTEXITCODE -ne 0) { throw 'Falló el análisis de la web y los servicios.' }

Write-Host '[5/6] Esperando que SonarQube procese los resultados...'
Start-Sleep 20
for ($i = 0; $i -lt 60; $i++) {
  $q = Invoke-RestMethod "$url/api/ce/activity_status" -Headers $auth
  if ($q.pending -eq 0 -and $q.inProgress -eq 0) { break }
  Start-Sleep 5
}

Write-Host '[6/6] Descargando el reporte...'
$reporte = @{}
$resumen = @()
foreach ($p in 'clinica-backend', 'clinica-web-servicios') {
  $m = Invoke-RestMethod "$url/api/measures/component?component=$p&metricKeys=bugs,vulnerabilities,code_smells,security_hotspots,coverage,duplicated_lines_density,ncloc,reliability_rating,security_rating,sqale_rating" -Headers $auth
  $gate = Invoke-RestMethod "$url/api/qualitygates/project_status?projectKey=$p" -Headers $auth
  $issues = @(); $page = 1
  do {
    $r = Invoke-RestMethod "$url/api/issues/search?componentKeys=$p&resolved=false&ps=500&p=$page" -Headers $auth
    $issues += $r.issues; $page++
  } while ($issues.Count -lt $r.total -and $page -le 20)
  $hot = Invoke-RestMethod "$url/api/hotspots/search?projectKey=$p&ps=500" -Headers $auth
  $reporte[$p] = @{ medidas = $m.component.measures; puerta = $gate.projectStatus.status; issues = $issues; hotspots = $hot.hotspots }
  $resumen += "$p · puerta de calidad: $($gate.projectStatus.status)"
  foreach ($x in $m.component.measures) { $resumen += "  $($x.metric): $($x.value)" }
  $resumen += "  issues abiertos: $($issues.Count) · hotspots: $($hot.hotspots.Count)"
}
$reporte | ConvertTo-Json -Depth 12 | Set-Content (Join-Path $root 'sonar\reporte-sonar.json') -Encoding UTF8
$resumen | Set-Content (Join-Path $root 'sonar\resumen.txt') -Encoding UTF8
$resumen | ForEach-Object { Write-Host $_ }
Write-Host ''
Write-Host "LISTO. Panel: $url (usuario admin, contraseña en sonar\.clave)"
