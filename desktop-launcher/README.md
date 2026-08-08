# CGC-PMS Windows desktop launcher

Win32 x64 launcher for the existing local CGC-PMS web stack. It opens the fixed URL `http://127.0.0.1:5173/` in a side-loaded Chromium application window after `GET /api/actuator/health` reports top-level `status=UP`.

## Build and test

Requirements: Visual Studio Build Tools 2022 with MSVC x64 and Windows SDK, PowerShell 7, Node.js for the contract health fixture.

```powershell
pwsh -NoProfile -File desktop-launcher/scripts/build.ps1 -Configuration Release -Architecture x64
pwsh -NoProfile -File desktop-launcher/tests/launcher-contract.ps1
pwsh -NoProfile -File desktop-launcher/scripts/package.ps1
```

Chromium archives, build output, packages, profiles, state, and logs are not committed. `chromium.lock.json` is the authority for exact upstream revision, version, source, hashes, and license.

## Security boundary

- No command-line or environment override for URL or Chromium flags.
- No Docker, backend, credential, Cookie, token, or business-data access.
- Writable profile/state/log data stays under `%LOCALAPPDATA%\CGC-PMS\Desktop`.
- `--app` removes normal browser chrome; it is not a navigation security sandbox.
- Chromium upgrades require a new lock, hash verification, real-package acceptance, and preserved rollback directory.
