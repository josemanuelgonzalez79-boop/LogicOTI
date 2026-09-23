# Diagnostico de solo lectura mediante el SDK nativo de Hikvision (Windows x64).
# Busca eventos de movimiento de D17; no cambia ajustes ni descarga video.
# .\scripts\diagnosticar-eventos-sdk-nvr.ps1 -SdkDll 'C:\ruta\al\SDK\lib\HCNetSDK.dll' -Channel 17 -Start '2026-09-23T09:30:00' -End '2026-09-23T10:00:00'
param(
    [Parameter(Mandatory = $true)]
    [ValidateScript({ Test-Path -LiteralPath $_ -PathType Leaf })]
    [string]$SdkDll,

    [ValidateRange(1, 128)]
    [int]$Channel = 17,

    [Parameter(Mandatory = $true)]
    [datetime]$Start,

    [Parameter(Mandatory = $true)]
    [datetime]$End,

    [string]$NvrHost = '192.168.5.18',

    [ValidateRange(1, 65535)]
    [int]$Port = 8000,

    [switch]$Json
)

if ($End -le $Start) { throw 'End debe ser posterior a Start.' }
if (-not [Environment]::Is64BitProcess) { throw 'Use PowerShell de 64 bits para el SDK Win64.' }

# Las estructuras y constantes corresponden a NET_DVR_SEARCH_EVENT_PARAM_V40,
# NET_DVR_SEARCH_EVENT_RET_V40 y NET_DVR_DEVICEINFO_V30 del SDK de Hikvision.
# El resultado reserva mas espacio del necesario para no exponer memoria nativa.
$nativeCode = @'
using System;
using System.Runtime.InteropServices;

public static class HikvisionSdkProbe
{
    [DllImport("kernel32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
    public static extern bool SetDllDirectory(string path);

    [DllImport("kernel32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
    public static extern IntPtr LoadLibrary(string path);

    [DllImport("kernel32.dll", SetLastError = true)]
    public static extern bool FreeLibrary(IntPtr module);

    [DllImport("HCNetSDK.dll", CallingConvention = CallingConvention.Cdecl)]
    [return: MarshalAs(UnmanagedType.Bool)]
    public static extern bool NET_DVR_Init();

    [DllImport("HCNetSDK.dll", CallingConvention = CallingConvention.Cdecl)]
    [return: MarshalAs(UnmanagedType.Bool)]
    public static extern bool NET_DVR_Cleanup();

    [DllImport("HCNetSDK.dll", CallingConvention = CallingConvention.Cdecl)]
    public static extern int NET_DVR_Login_V30(
        [MarshalAs(UnmanagedType.LPStr)] string ip, ushort port,
        IntPtr username, IntPtr password, IntPtr deviceInfo);

    [DllImport("HCNetSDK.dll", CallingConvention = CallingConvention.Cdecl)]
    [return: MarshalAs(UnmanagedType.Bool)]
    public static extern bool NET_DVR_Logout(int userId);

    [DllImport("HCNetSDK.dll", CallingConvention = CallingConvention.Cdecl)]
    public static extern int NET_DVR_FindFileByEvent_V40(int userId, IntPtr search);

    [DllImport("HCNetSDK.dll", CallingConvention = CallingConvention.Cdecl)]
    public static extern int NET_DVR_FindNextEvent_V40(int handle, IntPtr result);

    [DllImport("HCNetSDK.dll", CallingConvention = CallingConvention.Cdecl)]
    [return: MarshalAs(UnmanagedType.Bool)]
    public static extern bool NET_DVR_FindClose_V30(int handle);

    [DllImport("HCNetSDK.dll", CallingConvention = CallingConvention.Cdecl)]
    public static extern uint NET_DVR_GetLastError();
}
'@

if (-not ('HikvisionSdkProbe' -as [type])) { Add-Type -TypeDefinition $nativeCode -ErrorAction Stop }

function Write-SdkTime([IntPtr]$Address, [int]$Offset, [datetime]$Value) {
    $parts = @($Value.Year, $Value.Month, $Value.Day, $Value.Hour, $Value.Minute, $Value.Second)
    for ($index = 0; $index -lt $parts.Count; $index++) {
        [Runtime.InteropServices.Marshal]::WriteInt32($Address, $Offset + $index * 4, $parts[$index])
    }
}

function Read-SdkTime([IntPtr]$Address, [int]$Offset) {
    $parts = @(0..5 | ForEach-Object { [Runtime.InteropServices.Marshal]::ReadInt32($Address, $Offset + $_ * 4) })
    try { return [datetime]::new($parts[0], $parts[1], $parts[2], $parts[3], $parts[4], $parts[5]) }
    catch { return "Valor de hora no valido: $($parts -join ',')" }
}

$sdkPath = (Resolve-Path -LiteralPath $SdkDll).Path
$sdkFolder = Split-Path -Parent $sdkPath
$module = [IntPtr]::Zero
$deviceInfo = [IntPtr]::Zero
$username = [IntPtr]::Zero
$password = [IntPtr]::Zero
$search = [IntPtr]::Zero
$result = [IntPtr]::Zero
$userId = -1
$findHandle = -1
$initialized = $false

try {
    if (-not [HikvisionSdkProbe]::SetDllDirectory($sdkFolder)) {
        throw "No fue posible configurar la carpeta del SDK. Error Win32 $([Runtime.InteropServices.Marshal]::GetLastWin32Error())."
    }
    $module = [HikvisionSdkProbe]::LoadLibrary($sdkPath)
    if ($module -eq [IntPtr]::Zero) {
        throw "No fue posible cargar HCNetSDK.dll y sus dependencias desde lib. Error Win32 $([Runtime.InteropServices.Marshal]::GetLastWin32Error())."
    }
    if (-not [HikvisionSdkProbe]::NET_DVR_Init()) {
        throw "NET_DVR_Init fallo: error SDK $([HikvisionSdkProbe]::NET_DVR_GetLastError())."
    }
    $initialized = $true

    if ($Json) {
        if ([string]::IsNullOrWhiteSpace($env:LOGICOTI_SDK_NVR_USERNAME) -or
                [string]::IsNullOrWhiteSpace($env:LOGICOTI_SDK_NVR_PASSWORD)) {
            throw 'Faltan credenciales de SDK en el entorno del proceso.'
        }
        $username = [Runtime.InteropServices.Marshal]::StringToHGlobalAnsi($env:LOGICOTI_SDK_NVR_USERNAME)
        $password = [Runtime.InteropServices.Marshal]::StringToHGlobalAnsi($env:LOGICOTI_SDK_NVR_PASSWORD)
    } else {
        $credential = Get-Credential -Message 'Credenciales del NVR para el SDK (no se imprimen)'
        if ($null -eq $credential) { throw 'Se cancelo la solicitud de credenciales.' }
        $username = [Runtime.InteropServices.Marshal]::StringToHGlobalAnsi($credential.UserName)
        $password = [Runtime.InteropServices.Marshal]::SecureStringToGlobalAllocAnsi($credential.Password)
    }
    $deviceInfo = [Runtime.InteropServices.Marshal]::AllocHGlobal(1024)
    [Runtime.InteropServices.Marshal]::Copy((New-Object byte[] 1024), 0, $deviceInfo, 1024)

    $userId = [HikvisionSdkProbe]::NET_DVR_Login_V30($NvrHost, [uint16]$Port, $username, $password, $deviceInfo)
    if ($userId -lt 0) {
        throw "Login SDK fallo: error $([HikvisionSdkProbe]::NET_DVR_GetLastError()). Verifique usuario, puerto y permiso de reproduccion."
    }
    # La numeracion SDK para D1 puede comenzar en 33, aunque ISAPI use trackID 101.
    $firstDigitalChannel = [int][Runtime.InteropServices.Marshal]::ReadByte($deviceInfo, 66)
    if ($firstDigitalChannel -eq 0) {
        throw 'El SDK no informo un canal digital inicial. Se detuvo para evitar consultar otra camara.'
    }
    $sdkChannel = $firstDigitalChannel + $Channel - 1
    if (-not $Json) { Write-Host "D$Channel => canal SDK $sdkChannel (primer canal digital: $firstDigitalChannel)." }

    # WORD majorType=0 (movimiento), WORD minorType=0xffff (todos).
    # NET_DVR_TIME en offsets 4 y 28, lockType=0xff en 52.
    # Union de 800 bytes en offset 184; primer canal WORD y 0xffff como terminador.
    $search = [Runtime.InteropServices.Marshal]::AllocHGlobal(984)
    [Runtime.InteropServices.Marshal]::Copy((New-Object byte[] 984), 0, $search, 984)
    [Runtime.InteropServices.Marshal]::WriteByte($search, 0, [byte]0)
    [Runtime.InteropServices.Marshal]::WriteByte($search, 1, [byte]0)
    [Runtime.InteropServices.Marshal]::WriteByte($search, 2, [byte]255)
    [Runtime.InteropServices.Marshal]::WriteByte($search, 3, [byte]255)
    Write-SdkTime $search 4 $Start
    Write-SdkTime $search 28 $End
    [Runtime.InteropServices.Marshal]::WriteByte($search, 52, 255)
    [Runtime.InteropServices.Marshal]::WriteByte($search, 184, [byte]($sdkChannel -band 255))
    [Runtime.InteropServices.Marshal]::WriteByte($search, 185, [byte]($sdkChannel -shr 8))
    [Runtime.InteropServices.Marshal]::WriteByte($search, 186, [byte]255)
    [Runtime.InteropServices.Marshal]::WriteByte($search, 187, [byte]255)

    if (-not $Json) { Write-Host "Buscando eventos de movimiento de D$Channel entre $($Start.ToString('yyyy-MM-dd HH:mm:ss')) y $($End.ToString('HH:mm:ss'))..." }
    $findHandle = [HikvisionSdkProbe]::NET_DVR_FindFileByEvent_V40($userId, $search)
    if ($findHandle -lt 0) { throw "Busqueda SDK fallo: error $([HikvisionSdkProbe]::NET_DVR_GetLastError())." }

    $result = [Runtime.InteropServices.Marshal]::AllocHGlobal(8192)
    $deadline = [Diagnostics.Stopwatch]::StartNew()
    $count = 0
    $events = [System.Collections.Generic.List[object]]::new()
    $limit = if ($Json) { 2000 } else { 100 }
    $seconds = if ($Json) { 50 } else { 30 }
    $complete = $false
    while ($count -lt $limit -and $deadline.Elapsed.TotalSeconds -lt $seconds) {
        [Runtime.InteropServices.Marshal]::Copy((New-Object byte[] 8192), 0, $result, 8192)
        $status = [HikvisionSdkProbe]::NET_DVR_FindNextEvent_V40($findHandle, $result)
        if ($status -eq 1000) {
            $count++
            $from = Read-SdkTime $result 4
            $until = Read-SdkTime $result 28
            $returnedChannel = [int][uint16][Runtime.InteropServices.Marshal]::ReadInt16($result, 52)
            if ($returnedChannel -ne $sdkChannel) { throw "El SDK devolvio un canal distinto: $returnedChannel." }
            if ($from -isnot [datetime] -or $until -isnot [datetime]) { throw 'El SDK devolvio una fecha de evento invalida.' }
            if ($Json) {
                $events.Add([pscustomobject]@{
                    start = $from.ToString('yyyy-MM-ddTHH:mm:ss')
                    end = $until.ToString('yyyy-MM-ddTHH:mm:ss')
                    channel = $Channel
                })
            } else { Write-Host "  $from - $until | canal SDK: $returnedChannel" }
        } elseif ($status -eq 1002) {
            Start-Sleep -Milliseconds 200
        } elseif ($status -eq 1001 -or $status -eq 1003) {
            $complete = $true
            break
        } else {
            throw "Lectura de eventos fallo: estado $status, error SDK $([HikvisionSdkProbe]::NET_DVR_GetLastError())."
        }
    }
    if ($Json) {
        if (-not $complete) { throw 'Busqueda de eventos incompleta: reduzca el periodo de consulta.' }
        [pscustomobject]@{ channel = $Channel; complete = $true; events = @($events.ToArray()) } |
            ConvertTo-Json -Depth 4 -Compress
    } else {
        Write-Host "Eventos devueltos: $count."
        if ($deadline.Elapsed.TotalSeconds -ge 30) { Write-Host 'Busqueda detenida tras 30 segundos.' }
        if ($count -eq 100) { Write-Host 'Busqueda detenida despues de los primeros 100 eventos.' }
    }
} finally {
    if ($findHandle -ge 0) { [void][HikvisionSdkProbe]::NET_DVR_FindClose_V30($findHandle) }
    if ($result -ne [IntPtr]::Zero) { [Runtime.InteropServices.Marshal]::FreeHGlobal($result) }
    if ($search -ne [IntPtr]::Zero) { [Runtime.InteropServices.Marshal]::FreeHGlobal($search) }
    if ($deviceInfo -ne [IntPtr]::Zero) { [Runtime.InteropServices.Marshal]::FreeHGlobal($deviceInfo) }
    if ($username -ne [IntPtr]::Zero) { [Runtime.InteropServices.Marshal]::FreeHGlobal($username) }
    if ($password -ne [IntPtr]::Zero) {
        if ($Json) { [Runtime.InteropServices.Marshal]::FreeHGlobal($password) }
        else { [Runtime.InteropServices.Marshal]::ZeroFreeGlobalAllocAnsi($password) }
    }
    if ($userId -ge 0) { [void][HikvisionSdkProbe]::NET_DVR_Logout($userId) }
    if ($initialized) { [void][HikvisionSdkProbe]::NET_DVR_Cleanup() }
    if ($module -ne [IntPtr]::Zero) { [void][HikvisionSdkProbe]::FreeLibrary($module) }
    [void][HikvisionSdkProbe]::SetDllDirectory($null)
}
