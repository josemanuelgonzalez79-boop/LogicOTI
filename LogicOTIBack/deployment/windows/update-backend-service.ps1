[CmdletBinding()]
param(
    [string]$InstallDirectory = 'C:\LogicOTI\Backend'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Assert-Administrator {
    $identity = [Security.Principal.WindowsIdentity]::GetCurrent()
    $principal = [Security.Principal.WindowsPrincipal]::new($identity)

    if (-not $principal.IsInRole(
        [Security.Principal.WindowsBuiltInRole]::Administrator
    )) {
        throw 'Abre PowerShell como administrador y vuelve a ejecutar la actualización.'
    }
}

Assert-Administrator

$backendRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$mavenWrapper = Join-Path $backendRoot 'mvnw.cmd'
$serviceExecutable = Join-Path $InstallDirectory 'logicoti-backend-service.exe'
$destinationJar = Join-Path $InstallDirectory 'logicoti-backend.jar'
$serviceEnv = Join-Path $InstallDirectory '.env'
$serviceName = 'LogicOTIBackend'

foreach ($requiredFile in @(
    $mavenWrapper,
    $serviceExecutable,
    $serviceEnv
)) {
    if (-not (Test-Path -LiteralPath $requiredFile -PathType Leaf)) {
        throw "Falta el archivo requerido: $requiredFile"
    }
}

if (-not (Get-Service -Name $serviceName -ErrorAction SilentlyContinue)) {
    throw "El servicio $serviceName no está instalado. Ejecuta install-backend-service.ps1."
}

Write-Host 'Compilando y ejecutando pruebas del backend...'
Push-Location $backendRoot
try {
    & $mavenWrapper clean verify

    if ($LASTEXITCODE -ne 0) {
        throw "Maven terminó con código $LASTEXITCODE."
    }
}
finally {
    Pop-Location
}

$jarCandidates = @(
    Get-ChildItem -Path (Join-Path $backendRoot 'target') -Filter '*.jar' -File |
        Where-Object { $_.Name -notlike '*.original' }
)

if ($jarCandidates.Count -ne 1) {
    throw "Se esperaba un JAR ejecutable y se encontraron $($jarCandidates.Count)."
}

Write-Host 'Deteniendo LogicOTIBackend...'
& $serviceExecutable stop
if ($LASTEXITCODE -ne 0) {
    throw "WinSW no pudo detener el servicio. Código: $LASTEXITCODE"
}

Copy-Item -LiteralPath $jarCandidates[0].FullName -Destination $destinationJar -Force

Write-Host 'Iniciando LogicOTIBackend...'
& $serviceExecutable start
if ($LASTEXITCODE -ne 0) {
    throw "WinSW no pudo iniciar el servicio. Código: $LASTEXITCODE"
}

Get-Service -Name $serviceName | Format-Table Status, Name, DisplayName
Write-Host 'La actualización terminó. El archivo .env instalado no fue modificado.'
Write-Host 'Comprueba el health en http://127.0.0.1:3210/actuator/health'
