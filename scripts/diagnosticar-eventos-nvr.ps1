# Consulta de solo lectura. Ejecutar con un intervalo donde se haya confirmado movimiento:
# .\scripts\diagnosticar-eventos-nvr.ps1 -Channel 28 -Start '2026-09-23T08:00:00' -End '2026-09-23T09:00:00'
param(
    [Parameter(Mandatory = $true)]
    [ValidateRange(1, 32)]
    [int]$Channel,

    [Parameter(Mandatory = $true)]
    [datetime]$Start,

    [Parameter(Mandatory = $true)]
    [datetime]$End,

    [string]$NvrUrl = 'http://192.168.5.18'
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
            $response = $client.PostAsync("$($NvrUrl.TrimEnd('/'))/ISAPI/ContentMgmt/search", $content).GetAwaiter().GetResult()
            try {
                $body = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
                $document = [xml]$body
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
} finally {
    $client.Dispose()
    $handler.Dispose()
}
