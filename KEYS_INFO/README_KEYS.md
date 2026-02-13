# PotataSMAPI Keys Documentation

This folder contains information about the two sets of keys required for this project.

## 1. App Developer Key (Private)
**File:** `android/app/potata.jks`
**Purpose:** This key signs the **PotataSMAPI App** itself. 
- You need this to install the app on your phone.
- **NEVER** share this key or its password publicly.
- **Alias:** `PotataSMAPI`
- **Password:** [Stored in your build.gradle]

## 2. Patcher Key (Internal)
**File:** `KEYS_INFO/potata_patcher.jks` (and eventually bundled in assets)
**Purpose:** This key is used by the PotataSMAPI app to sign the **Modded Stardew Valley APK** it creates on the user's phone.
- This key is bundled *inside* the app.
- It doesn't need to be secret, but it's good to keep track of.
- **Alias:** `potata_patcher`
- **Password:** `potata-patcher-key-2026`

## Security Note
The `.gitignore` has been updated to ignore all `*.jks` files. These files will **not** be uploaded to GitHub to keep your developer identity safe.
