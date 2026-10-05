# toolchain/

Vendored build tools for the **lightweight** APK path in `scripts/build_apk.sh`
(~53 MB, all tracked in git). These binaries make `bash scripts/build_apk.sh`
work with no Android SDK installed — it drives `aapt2`, `javac`, R8's D8, and an
APK signer directly instead of going through Gradle.

This path exists for constrained machines (it peaks well under 150 MB of RAM).
The full Gradle build (`bash scripts/build_apk.sh full`) does not use these
except `uber-apk-signer.jar`, which signs the Gradle output. CI
(`.github/workflows/ci.yml`) uses Gradle only and ignores this directory.

## Contents

| File | Version | Origin | SHA-256 |
| --- | --- | --- | --- |
| `aapt2` | `2.19-11315950` | Android SDK build-tools, Google | `da28a8bdafafb91cf917f7635cce6e146db9df74da92fd5420ea293a1ea1de5b` |
| `android.jar` | platform API 32 (`S_V2`, `TIRAMISU`) | `platforms/android-32`, Google | `4fade8d5e04130bd8ccd74d5fec6c86b01ad75c3ee877ccc5d660255c4c78646` |
| `r8.jar` | R8 / D8 (shipped with AGP) | R8 project | `4733945987ee0a840fafc34080b135259e01678412e07212b23f706334290294` |
| `uber-apk-signer.jar` | `1.3.0` | `at.favre.tools:uber-apk-signer` | `e1299fd6fcf4da527dd53735b56127e8ea922a321128123b9c32d619bba1d835` |

## Licensing

Verified from the shipped artifacts, not assumed:

- `aapt2`, `android.jar` — Android SDK, Google LLC. **Apache License 2.0.**
  (`android.jar` carries `Created-By: soong_zip`; it is the documented platform
  stub, not an app binary.)
- `r8.jar` — its embedded `LICENSE` states **3-Clause BSD** for R8's own code
  ("Copyright (c) 2016, the R8 project authors"), plus bundled third-party
  libraries that are mostly Apache-2.0 (Guava and others). Full text ships
  inside the jar: `unzip -p r8.jar LICENSE`.
- `uber-apk-signer.jar` — `at.favre.tools:uber-apk-signer:1.3.0`, confirmed via
  its embedded Maven `pom.properties`. Upstream is **Apache-2.0**; the jar
  carries no top-level `LICENSE` entry, so rely on upstream when redistributing.

All of these permit redistribution. If this repository is ever shipped as a
product, carry the upstream license texts along with the binaries.

## Verifying integrity

```sh
cd toolchain
sha256sum -c SHA256SUMS
```

`SHA256SUMS` holds the hashes from the table above. Run it after any
replacement — the lightweight path silently produces a broken APK if a jar is
truncated or a substituted binary disagrees about the platform level.

## Replacing a tool

1. Drop the new binary in alongside its upstream license text.
2. Recompute:
   `cd toolchain && sha256sum aapt2 android.jar r8.jar uber-apk-signer.jar > SHA256SUMS`
3. Rebuild and confirm the output still parses:
   `./aapt2 dump badging ../dist/lastlab.apk`.

## Known limits

- `android.jar` is pinned at API 32. `app/build.gradle.kts` targets SDK 36 and
  compiles against the real SDK under Gradle, so raising `targetSdk` does not
  affect the Gradle build — but the lightweight path cannot reference platform
  APIs newer than 32. Bump this file when that path needs a newer constant.
- D8 here is whatever AGP shipped. The script passes `--min-api` from `minSdk`,
  so the two stay in sync.