# Subscription / wallet logcat - uses adb full path (no PATH required)
# Run: .\logcat-subscription.ps1

$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
if (-not (Test-Path $adb)) {
    Write-Error "adb not found at $adb"
    exit 1
}
& $adb logcat WalletManager:* SubscriptionUseCase:* WalletConnect:* okhttp.OkHttpClient:W *:S
