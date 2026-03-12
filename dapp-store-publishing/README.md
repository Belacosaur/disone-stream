# Solana Seeker dApp Store Submission

This folder contains everything needed to submit Disone to the Solana dApp Store (Seeker mobile store).

## Requirements Summary

**Official Solana dApp Store media purposes** (from [docs.solanamobile.com](https://docs.solanamobile.com/dapp-store/publishing-cli/prepare)):

| config purpose | File | Size | Required |
|----------------|------|------|----------|
| icon | `icon-512.png` | 512×512 px | Yes |
| banner | `Banner.png` | 1200×600 px | Yes |
| featureGraphic | `logosymbol.png` | 1200×1200 px | Optional (Editor's Choice carousel) |
| screenshot | `screenshot1.png` … `screenshot4.png` | Min 1080px, 1920×1080 recommended | Yes (min 4) |
| video | — | MP4, min 720×720 px | Optional |

APK: Release build signed with **dApp Store key only** (not Google Play key).

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

| File | Size | config purpose |
|------|------|----------------|
| `icon-512.png` | 512×512 | icon |
| `Banner.png` | 1200×600 | banner |
| `logosymbol.png` | 1200×1200 | featureGraphic (optional) |
| `screenshot1.png` … `screenshot4.png` | 1080× min | screenshot |

**icon-512**: Export from `res/drawable/logosymbol.png` at 512×512.  
**logosymbol**: Same asset at 1200×1200 for Editor's Choice carousel.

All images must have consistent orientation (all landscape or all portrait).

## Step 3: Update config.yaml

Edit `config.yaml` and replace all `<< >>` placeholders:

- Publisher name, email, website
- App description text
- Ensure file paths match your assets

## Step 4: Publisher Portal Setup (one-time)

1. Go to [publish.solanamobile.com](https://publish.solanamobile.com)
2. Sign up and complete KYC/KYB
3. Connect a Solana wallet (Phantom, Solflare) with ~0.2 SOL
4. Set storage provider (ArDrive recommended)

## Step 5: CLI Deployment

Same flow as the working Archive/android-twa deployment. Run from this folder.

**Prerequisites:** Solana CLI wallet at `~/.config/solana/id.json`, funded with ~0.2 SOL. Android build-tools installed.

### First-time only: Create App NFT

```bash
cd dapp-store-publishing
pnpm install
npx @solana-mobile/dapp-store-cli create app --keypair "$env:USERPROFILE\.config\solana\id.json" --url https://mainnet.helius-rpc.com/?api-key=YOUR_KEY
```

CLI updates `config.yaml` with the App NFT address.

### Each release: Create Release NFT

```bash
npx @solana-mobile/dapp-store-cli create release --keypair "$env:USERPROFILE\.config\solana\id.json" --url https://mainnet.helius-rpc.com/?api-key=YOUR_KEY --build-tools-path "C:\Users\YOU\AppData\Local\Android\Sdk\build-tools\34.0.0"
```

Uploads assets to Arweave, mints Release NFT, updates `config.yaml`. Needs stable network (min 0.25 MB/s upload).

### Submit for review

```bash
npx @solana-mobile/dapp-store-cli publish submit --keypair "$env:USERPROFILE\.config\solana\id.json" --url https://mainnet.helius-rpc.com/?api-key=YOUR_KEY --requestor-is-authorized --complies-with-solana-dapp-store-policies
```

### After submission

1. Join [Solana Mobile Discord](http://discord.gg/solanamobile), get Developer role
2. Post in `#dapp-store` that submission is complete
3. Review usually takes 2–5 business days

## Keystore Notes

- **Play Store builds**: `keystore.properties` + `android.keystore` (or existing Play key)
- **dApp Store builds**: `keystore.dappstore.properties` + `dappstore.keystore`

Keep `dappstore.keystore` secure. You need it for all future dApp Store updates.

## References

- [Submit a New App](https://docs.solanamobile.com/dapp-store/submit-new-app)
- [Publishing from Google Play](https://docs.solanamobile.com/dapp-store/publishing-from-google-play)
- [Prepare your dApp](https://docs.solanamobile.com/dapp-store/publishing-cli/prepare)
- [Discord #dev-answers](http://discord.gg/solanamobile) for support
