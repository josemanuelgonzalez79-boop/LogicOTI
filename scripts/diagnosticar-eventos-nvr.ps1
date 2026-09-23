# Consulta de solo lectura. Ejecutar con un intervalo donde se haya confirmado movimiento:
# .\scripts\diagnosticar-eventos-nvr.ps1 -Channel 20 -Start '2026-09-23T07:50:00' -End '2026-09-23T09:36:00' -SmartSearch
param(
    [Parameter(Mandatory = $true)]
    [ValidateRange(1, 32)]
    [int]$Channel,

    [Parameter(Mandatory = $true)]
    [datetime]$Start,

    [Parameter(Mandatory = $true)]
    [datetime]$End,

    [string]$NvrUrl = 'http://192.168.5.18',

    # Prueba opcional de búsqueda VCA. No modifica la configuración del NVR.
    [switch]$SmartSearch
)

if ($End -le $Start) {
    throw 'La hora final debe ser posterior a la inicial.'
}

Add-Type -AssemblyName System.Net.Http
$credential = Get-Credential -Message 'Credenciales del NVR (no se imprimen)'
$handler = [System.Net.Http.HttpClientHandler]::new()
$handler.Credentials = $credential.GetNetworkCredential()
$client = [System.Net.Http.HttpClient]::new($handler)
$client.Timeout = [timespan]::FromSeconds(25)
$trackId = $Channel * 100 + 1

try {
    foreach ($filter in @(
        @{ Name = 'Todas las grabaciones'; Descriptor = '/recordType.meta.std-cgi.com' },
        @{ Name = 'Movimiento (MOTION)'; Descriptor = '/recordType.meta.hikvision.com/MOTION' },
        @{ Name = 'Movimiento (motion)'; Descriptor = '/recordType.meta.hikvision.com/motion' }
    )) {
        $xmlRequest = @"
<?xml version="1.0" encoding="UTF-8"?>
<CMSearchDescription version="1.0" xmlns="http://www.hikvision.com/ver20/XMLSchema">
  <searchID>{$([guid]::NewGuid())}</searchID>
  <trackList><trackID>$trackId</trackID></trackList>
  <timeSpanList><timeSpan>
    <startTime>$($Start.ToString("yyyy-MM-ddTHH:mm:ss'Z'"))</startTime>
    <endTime>$($End.ToString("yyyy-MM-ddTHH:mm:ss'Z'"))</endTime>
  </timeSpan></timeSpanList>
  <maxResults>100</maxResults>
  <searchResultPostion>0</searchResultPostion>
  <metadataList><metadataDescriptor>$($filter.Descriptor)</metadataDescriptor></metadataList>
</CMSearchDescription>
"@
        $content = [System.Net.Http.StringContent]::new($xmlRequest, [System.Text.Encoding]::UTF8, 'application/xml')
        try {
            Write-Host "Consultando $($filter.Name) para CAM-$('{0:D3}' -f $Channel)..."
            $response = $client.PostAsync("$($NvrUrl.TrimEnd('/'))/ISAPI/ContentMgmt/search", $content).GetAwaiter().GetResult()
            try {
                Write-Host "  HTTP $([int]$response.StatusCode)"
                # El NVR puede enviar un charset inválido en Content-Type. XmlReader lee
                # los bytes y usa la codificación declarada en el propio documento XML.
                $stream = $response.Content.ReadAsStreamAsync().GetAwaiter().GetResult()
                $settings = [System.Xml.XmlReaderSettings]::new()
                $settings.DtdProcessing = [System.Xml.DtdProcessing]::Prohibit
                $settings.XmlResolver = $null
                $reader = [System.Xml.XmlReader]::Create($stream, $settings)
                try {
                    $document = [System.Xml.XmlDocument]::new()
                    $document.XmlResolver = $null
                    $document.Load($reader)
                } finally {
                    $reader.Dispose()
                }
                $matches = @($document.SelectNodes("//*[local-name()='searchMatchItem']"))
                $statusNode = $document.SelectSingleNode("//*[local-name()='responseStatusStrg']")
                $statusText = if ($null -eq $statusNode) { 'Sin estado' } else { $statusNode.InnerText }
                Write-Host "$($filter.Name): HTTP $([int]$response.StatusCode), estado $statusText, resultados primera pagina $($matches.Count)"
                $matches | Select-Object -First 10 | ForEach-Object {
                    $from = $_.SelectSingleNode("./*[local-name()='timeSpan']/*[local-name()='startTime']").InnerText
                    $until = $_.SelectSingleNode("./*[local-name()='timeSpan']/*[local-name()='endTime']").InnerText
                    $type = (@($_.SelectNodes("./*[local-name()='metadataMatches']/*[local-name()='metadataDescriptor']")) | ForEach-Object { $_.InnerText }) -join ', '
                    Write-Host "  $from - $until | tipo: $type"
                }
            } finally {
                $response.Dispose()
            }
        } catch {
            Write-Host "$($filter.Name): error $($_.Exception.Message)"
        } finally {
            $content.Dispose()
        }
    }

    if ($SmartSearch) {
        # 22 columnas x 18 filas; los dos bits de relleno al final de cada fila
        # quedan desactivados. La consulta examina el área completa de la imagen.
        $gridMap = 'FFFFFC' * 18
        $xmlRequest = @"
<?xml version="1.0" encoding="UTF-8"?>
<SmartSearchDescription version="1.0" xmlns="http://www.hikvision.com/ver20/XMLSchema">
  <searchID>{$([guid]::NewGuid())}</searchID>
  <searchResultPosition>0</searchResultPosition>
  <maxResults>100</maxResults>
  <trackID>$trackId</trackID>
  <startTime>$($Start.ToString("yyyy-MM-ddTHH:mm:ss'Z'"))</startTime>
  <endTime>$($End.ToString("yyyy-MM-ddTHH:mm:ss'Z'"))</endTime>
  <type>motionDetection</type>
  <MotionDetection>
    <Grid><rowGranularity>18</rowGranularity><columnGranularity>22</columnGranularity></Grid>
    <MotionDetectionLayout><layout><gridMap>$gridMap</gridMap></layout></MotionDetectionLayout>
  </MotionDetection>
</SmartSearchDescription>
"@
        $content = [System.Net.Http.StringContent]::new($xmlRequest, [System.Text.Encoding]::UTF8, 'application/xml')
        try {
            Write-Host "Consultando SmartSearch (motionDetection) para CAM-$('{0:D3}' -f $Channel)..."
            $response = $client.PostAsync("$($NvrUrl.TrimEnd('/'))/ISAPI/ContentMgmt/SmartSearch", $content).GetAwaiter().GetResult()
            try {
                Write-Host "  HTTP $([int]$response.StatusCode)"
                # XmlReader respeta el encoding del XML incluso si Content-Type tiene un charset inválido.
                $stream = $response.Content.ReadAsStreamAsync().GetAwaiter().GetResult()
                $settings = [System.Xml.XmlReaderSettings]::new()
                $settings.DtdProcessing = [System.Xml.DtdProcessing]::Prohibit
                $settings.XmlResolver = $null
                $reader = [System.Xml.XmlReader]::Create($stream, $settings)
                try {
                    $document = [System.Xml.XmlDocument]::new()
                    $document.XmlResolver = $null
                    $document.Load($reader)
                } finally {
                    $reader.Dispose()
                }
                $status = $document.SelectSingleNode("//*[local-name()='responseStatusStrg' or local-name()='statusString' or local-name()='subStatusCode']")
                $statusText = if ($null -eq $status) { $document.DocumentElement.LocalName } else { $status.InnerText }
                $matches = @($document.SelectNodes("//*[local-name()='searchMatchItem']"))
                Write-Host "SmartSearch: HTTP $([int]$response.StatusCode), estado $statusText, resultados primera pagina $($matches.Count)"
                $matches | Select-Object -First 10 | ForEach-Object {
                    $from = $_.SelectSingleNode("./*[local-name()='timeSpan']/*[local-name()='startTime']")
                    $until = $_.SelectSingleNode("./*[local-name()='timeSpan']/*[local-name()='endTime']")
                    if ($null -ne $from -and $null -ne $until) {
                        Write-Host "  $($from.InnerText) - $($until.InnerText)"
                    }
                }
            } finally {
                $response.Dispose()
            }
        } catch {
            Write-Host "SmartSearch: error $($_.Exception.Message)"
        } finally {
            $content.Dispose()
        }
    }
} finally {
    $client.Dispose()
    $handler.Dispose()
}
