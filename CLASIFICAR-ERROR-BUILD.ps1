[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][string]$LogPath,
    [string]$Module = 'desconocido',
    [switch]$TransientOnly
)
$ErrorActionPreference = 'SilentlyContinue'
if (-not (Test-Path -LiteralPath $LogPath)) {
    if ($TransientOnly) { exit 1 }
    Write-Host "No existe el log: $LogPath" -ForegroundColor Yellow
    exit 0
}
$text = [IO.File]::ReadAllText($LogPath,[Text.Encoding]::UTF8)
$transientPattern = 'Could not transfer artifact|Connection reset|Read timed out|ETIMEDOUT|EAI_AGAIN|TLS handshake timeout|i/o timeout|Temporary failure|unexpected EOF|429 Too Many Requests|502 Bad Gateway|503 Service Unavailable|504 Gateway Timeout|network is unreachable|connection refused.*registry|grpc server closed unexpectedly|rpc error: code = Unavailable|failed to solve:.*grpc|BuildKit.*(?:closed|unavailable)|buildx.*(?:closed|unavailable)|error reading from server|failed to receive status'
$isTransient = $text -match $transientPattern
if ($TransientOnly) {
    if ($isTransient) { exit 0 } else { exit 1 }
}
$type = 'Error de compilacion/construccion no clasificado'
$action = 'Revisa las ultimas lineas del log del modulo y DIAGNOSTICO-CLINICA.txt.'
if ($text -match 'unable to prepare context|path .* not found|failed to resolve source metadata.*context') {
    $type = 'Contexto/ruta Docker invalido'
    $action = 'Usa un contexto relativo seguro tras entrar a la carpeta del proyecto. En Windows evita pasar %~dp0 con backslash final como argumento de contexto entre comillas.'
} elseif ($text -match 'failed to read dockerfile|open Dockerfile: no such file or directory|Dockerfile.*(?:not found|no such file)') {
    $type = 'Dockerfile no encontrado / ruta de build incorrecta'
    $action = 'El build debe usar docker\Dockerfile.runtime.local mediante una ruta explicita. La prevalidacion bloquea referencias relativas inseguras.'
} elseif ($text -match 'app\.jar.*(?:no such file|not found)|falta build-artifacts.*app\.jar') {
    $type = 'JAR exportado ausente'
    $action = 'Revisa java-artifacts.log. El instalador exige los 15 app.jar antes de construir imagenes.'
} elseif ($text -match 'grpc server closed unexpectedly|rpc error: code = Unavailable|failed to solve:.*grpc|BuildKit.*(?:closed|unavailable)|buildx.*(?:closed|unavailable)') {
    $type = 'Docker BuildKit/gRPC interrumpido'
    $action = 'El instalador construye secuencialmente y reintenta solo el componente afectado. Si persiste, reinicia Docker Desktop y conserva build-artifacts para no recompilar Maven.'
} elseif ($text -match 'no space left on device|ENOSPC') {
    $type = 'Almacenamiento insuficiente en Docker/disco'
    $action = 'Libera espacio en Docker Desktop y en el disco; luego vuelve a validar.'
} elseif ($text -match 'exit code[: ]+137|Killed|OutOfMemoryError|Java heap space|Allocation failed') {
    $type = 'Memoria insuficiente / proceso terminado por OOM'
    $action = 'Aumenta la memoria asignada a Docker Desktop o cierra aplicaciones pesadas.'
} elseif ($text -match 'NG5002|Unexpected closing block|Unexpected closing tag') {
    $type = 'Plantilla Angular invalida'
    $action = 'Revisa bloques @if/@for y etiquetas HTML en el archivo indicado por Angular.'
} elseif ($text -match 'NG[0-9]{4}|TS[0-9]{4}') {
    $type = 'Compilacion Angular/TypeScript'
    $action = 'Corrige el archivo y linea informados por ng build/TypeScript.'
} elseif ($text -match 'local variables referenced from a lambda expression must be final or effectively final') {
    $type = 'Java: variable mutable capturada por lambda'
    $action = 'Copia el valor a una variable final/efectivamente final o extrae la logica a un helper.'
} elseif ($text -match 'COMPILATION ERROR|maven-compiler-plugin.*Compilation failure|\[ERROR\].*\.java:\[') {
    $type = 'Compilacion Java/Maven'
    $action = 'Corrige el primer error Java real del log; los errores posteriores pueden ser en cascada.'
} elseif ($text -match 'Could not resolve dependencies|Non-resolvable parent POM|Could not transfer artifact') {
    $type = 'Resolucion de dependencias Maven/red'
    $action = 'Verifica Internet/DNS. El validador ya reintenta una vez los errores transitorios.'
} elseif ($text -match 'npm ERR!|EAI_AGAIN|ETIMEDOUT|ECONNRESET') {
    $type = 'NPM/red o dependencia frontend'
    $action = 'Verifica Internet/DNS; npm tiene reintentos configurados y el build transitorio se reintenta una vez.'
} elseif ($text -match 'port is already allocated|failed to bind host port|bind:.*address already in use') {
    $type = 'Conflicto de puerto'
    $action = 'Cierra el proceso/contenedor ajeno que usa el puerto reportado y vuelve a iniciar.'
} elseif ($text -match 'Cannot find module|Module not found|package .* does not exist|cannot find symbol') {
    $type = 'Import/dependencia/simbolo faltante'
    $action = 'Revisa imports, nombres de clases/archivos y dependencias del modulo.'
} elseif ($isTransient) {
    $type = 'Fallo transitorio de red/registro'
    $action = 'El build fue reintentado automaticamente; si persiste revisa conectividad, DNS o proxy.'
}
$line = "[$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')] $Module :: $type :: $action"
Write-Host "CLASIFICACION: $type" -ForegroundColor Yellow
Write-Host "ACCION: $action" -ForegroundColor Yellow
Add-Content -LiteralPath (Join-Path $PSScriptRoot 'CLASIFICACION-ERRORES.txt') -Value $line -Encoding UTF8
exit 0
