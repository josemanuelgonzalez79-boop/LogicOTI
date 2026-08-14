[CmdletBinding()]
param(
    [string]$InstallDirectory = 'C:\LogicOTI\Backend',
    [string]$WinSWExecutable = 'C:\Caddy\caddy-service.exe',
    [string]$JavaExecutable = ''
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Assert-Administrator {
    $identity = [Security.Principal.WindowsIdentity]::GetCurrent()
    $principal = [Security.Principal.WindowsPrincipal]::new($identity)

    if (-not $principal.IsInRole(
        [Security.Principal.WindowsBuiltInRole]::Administrator
    )) {
        throw 'Abre PowerShell como administrador y vuelve a ejecutar el instalador.'
    }
}

function Resolve-JavaExecutable {
    param([string]$ConfiguredPath)

    if (-not [string]::IsNullOrWhiteSpace($ConfiguredPath)) {
        if (-not (Test-Path -LiteralPath $ConfiguredPath -PathType Leaf)) {
            throw "No existe Java en: $ConfiguredPath"
        }

        return (Resolve-Path -LiteralPath $ConfiguredPath).Path
    }

    $javaCommand = Get-Command java.exe -ErrorAction SilentlyContinue

    if ($null -eq $javaCommand) {
        throw 'No se encontró java.exe. Instala Java 21 o indica -JavaExecutable.'
    }

    return $javaCommand.Source
}

Assert-Administrator

$backendRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$envSource = Join-Path $backendRoot '.env'
$mavenWrapper = Join-Path $backendRoot 'mvnw.cmd'
$templatePath = Join-Path $PSScriptRoot 'logicoti-backend-service.xml.template'
$serviceExecutable = Join-Path $InstallDirectory 'logicoti-backend-service.exe'
$serviceConfiguration = Join-Path $InstallDirectory 'logicoti-backend-service.xml'
$serviceEnv = Join-Path $InstallDirectory '.env'
$serviceName = 'LogicOTIBackend'

if (Get-Service -Name $serviceName -ErrorAction SilentlyContinue) {
    throw "El servicio $serviceName ya existe. Usa update-backend-service.ps1 para actualizarlo."
}

foreach ($requiredFile in @(
    $WinSWExecutable,
    $envSource,
    $mavenWrapper,
    $templatePath
)) {
    if (-not (Test-Path -LiteralPath $requiredFile -PathType Leaf)) {
        throw "Falta el archivo requerido: $requiredFile"
    }
}

$resolvedJava = Resolve-JavaExecutable -ConfiguredPath $JavaExecutable

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

New-Item -ItemType Directory -Path $InstallDirectory -Force | Out-Null
New-Item -ItemType Directory -Path (Join-Path $InstallDirectory 'logs') -Force | Out-Null

Copy-Item -LiteralPath $WinSWExecutable -Destination $serviceExecutable
Copy-Item -LiteralPath $jarCandidates[0].FullName `
    -Destination (Join-Path $InstallDirectory 'logicoti-backend.jar')

if (-not (Test-Path -LiteralPath $serviceEnv)) {
    Copy-Item -LiteralPath $envSource -Destination $serviceEnv
}

$escapedJava = [Security.SecurityElement]::Escape($resolvedJava)
$serviceXml = (Get-Content -LiteralPath $templatePath -Raw).Replace(
    '__JAVA_EXECUTABLE__',
    $escapedJava
)
$serviceXml | Set-Content -LiteralPath $serviceConfiguration -Encoding UTF8

Write-Host "Java del servicio: $resolvedJava"
Write-Host "Directorio del servicio: $InstallDirectory"

& $serviceExecutable install
if ($LASTEXITCODE -ne 0) {
    throw "WinSW no pudo instalar el servicio. Código: $LASTEXITCODE"
}

& $serviceExecutable start
if ($LASTEXITCODE -ne 0) {
    throw "WinSW no pudo iniciar el servicio. Código: $LASTEXITCODE"
}

Get-Service -Name $serviceName | Format-Table Status, Name, DisplayName
Write-Host 'El backend quedó instalado con inicio automático y recuperación ante fallos.'
Write-Host 'Comprueba el health en http://127.0.0.1:3210/actuator/health'
