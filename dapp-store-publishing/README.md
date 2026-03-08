# Solana Seeker dApp Store Submission

This folder contains everything needed to submit Disone to the Solana dApp Store (Seeker mobile store).

## Requirements Summary

| Asset | Spec |
|-------|------|
| APK | Release build signed with **dApp Store key only** (not Google Play key) |
| Icon | 512×512 px |
| Banner | 1200×600 px |
| Screenshots | Min 4 images, 1080×1080 or 1920×1080 recommended |
| Videos (optional) | MP4, min 720×720 px |

## Step 1: Build the dApp Store APK

The dApp Store requires a **separate signing key** from Google Play. This project uses the `dappStore` product flavor.

```bash
./gradlew assembleDappStoreRelease
```

APK output: `app/build/outputs/apk/dappStore/release/app-dappStore-release.apk`

To copy it into this publishing folder:

```bash
./gradlew copyDappStoreApkForPublishing
```

## Step 2: Prepare Media Assets

Place these files in `media/`:

| File | Size | Description |
|------|------|-------------|
| `app_icon.png` | 512×512 | App icon (export from `res/drawable` in Android Studio) |
| `banner_graphic.png` | 1200×600 | Store banner |
| `screenshot_1.png` … `screenshot_4.png` | 1080× min | App screenshots |

**Icon export**: In Android Studio, right-click `res/drawable/ic_launcher_foreground.xml` → Export as PNG → choose 512×512.

All images must have consistent orientation (all landscape or all portrait).

## Step 3: Update config.yaml

Edit `config.yaml` and replace all `<< >>` placeholders:

- Publisher name, email, website
- App description text
- Ensure file paths match your assets

## Step 4: Publisher Portal Setup

1. Go to [publish.solanamobile.com](https://publish.solanamobile.com)
2. Sign up and complete KYC/KYB
3. Connect a Solana wallet (Phantom, Solflare) with ~0.2 SOL
4. Set storage provider (ArDrive recommended)

## Step 5: Submit

1. In the portal: **Add a dApp** → **New dApp**
2. Fill in app details (name, description, icon, screenshots, etc.)
3. **New Version** → upload your `app-dappStore-release.apk`
4. Approve all signing requests (Arweave uploads, Release NFT)
5. Submit for review

## Keystore Notes

- **Play Store builds**: `keystore.properties` + `android.keystore` (or existing Play key)
- **dApp Store builds**: `keystore.dappstore.properties` + `dappstore.keystore`

Keep `dappstore.keystore` secure. You need it for all future dApp Store updates.

## References

- [Submit a New App](https://docs.solanamobile.com/dapp-store/submit-new-app)
- [Publishing from Google Play](https://docs.solanamobile.com/dapp-store/publishing-from-google-play)
- [Prepare your dApp](https://docs.solanamobile.com/dapp-store/publishing-cli/prepare)
- [Discord #dev-answers](http://discord.gg/solanamobile) for support
