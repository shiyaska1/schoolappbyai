# Generates a School App activation key for one device — same HMAC-SHA256 scheme (and secret) as
# data/License.kt, so a customer's Device ID (shown on their expiry screen) turns into a key here.
#
# Usage:
#   .\generate-license-key.ps1 -DeviceId "ABCD1234EF567890"
#   .\generate-license-key.ps1 -DeviceId "ABCD1234EF567890" -Milestone 6   # a later renewal, not the first
#
# Keep this script and the secret below private — anyone with both can activate any device.

param(
    [Parameter(Mandatory = $true)][string]$DeviceId,
    [int]$Milestone = 1
)

# Must match License.kt's SECRET exactly (and the POS billing app's — they're deliberately shared).
$Secret = "POSB-change-this-secret-2024"

$message = $DeviceId.Trim().ToUpper()
if ($Milestone -gt 1) { $message = "$message$Milestone" }

$hmac = New-Object System.Security.Cryptography.HMACSHA256
$hmac.Key = [System.Text.Encoding]::UTF8.GetBytes($Secret)
$hashBytes = $hmac.ComputeHash([System.Text.Encoding]::UTF8.GetBytes($message))
$hex = -join ($hashBytes | ForEach-Object { $_.ToString("x2") })
$key = $hex.Substring(0, 16).ToUpper()
$formatted = ($key -split '(?<=\G.{4})(?!$)') -join '-'

Write-Output "Device ID : $($DeviceId.Trim().ToUpper())"
Write-Output "Milestone : $Milestone"
Write-Output "Key       : $formatted"
