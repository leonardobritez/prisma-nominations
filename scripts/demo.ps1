# Demo end-to-end de la plataforma de nominaciones (PowerShell).
# Requiere: docker compose up -d  y los 3 servicios levantados:
#   mvn -pl nomination-api spring-boot:run
#   mvn -pl nomination-worker spring-boot:run           (perfil mock-abm por defecto)
#   mvn -pl event-publisher spring-boot:run -Dspring-boot.run.profiles=kafka
#
# Uso:  powershell -ExecutionPolicy Bypass -File scripts/demo.ps1

$ErrorActionPreference = "Stop"
$api = "http://localhost:8081"
$token = "dev-token"

function Wait-Health($url, $name) {
    for ($i = 0; $i -lt 30; $i++) {
        try {
            Invoke-RestMethod -Uri "$url/actuator/health" -TimeoutSec 2 | Out-Null
            Write-Host "[OK] $name disponible"
            return
        } catch {
            Start-Sleep -Seconds 1
        }
    }
    throw "$name no respondio en /actuator/health"
}

Wait-Health $api "nomination-api"

$requestId = [guid]::NewGuid().ToString()
$body = @{
    customer_id = "123456"
    account_id  = "987654"          # sufijos: _REJ _TO _DUP _FAIL
    card_id     = "TOKEN_OR_MASKED_REFERENCE"
    alias       = "CUENTA_PRINCIPAL"
    request_id  = $requestId
} | ConvertTo-Json

$headers = @{
    "Authorization"   = "Bearer $token"
    "Idempotency-Key" = "demo-$requestId"
    "X-Correlation-Id" = "corr-$requestId"
}

Write-Host "`n== POST /v1/nominations =="
$accepted = Invoke-RestMethod -Method Post -Uri "$api/v1/nominations" -Headers $headers -ContentType "application/json" -Body $body
$accepted | ConvertTo-Json -Depth 5

$nominationId = $accepted.nomination_id
Write-Host "`n== Consultando estado (hasta terminal) =="
for ($i = 0; $i -lt 40; $i++) {
    $status = Invoke-RestMethod -Method Get -Uri "$api/v1/nominations/$nominationId" -Headers @{ "Authorization" = "Bearer $token" }
    Write-Host ("  status = {0}" -f $status.status)
    if ($status.status -in @("APPROVED","REJECTED","FAILED","TIMEOUT","VALIDATION_FAILED")) { break }
    Start-Sleep -Milliseconds 500
}

Write-Host "`n== Historial =="
$history = Invoke-RestMethod -Method Get -Uri "$api/v1/nominations/$nominationId/history" -Headers @{ "Authorization" = "Bearer $token" }
$history.entries | Format-Table from_status, to_status, actor, reason_code -AutoSize

Write-Host "`nResultado final: $($status.status)"
