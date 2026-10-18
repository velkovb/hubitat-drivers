# Hubitat Drivers

**Vibe-coded, human-tested, local-first** custom drivers for [Hubitat Elevation](https://hubitat.com/). Source, docs and install manifests are public so others can inspect, test and improve them.

> **Transparency:** Much of this repository was produced with **ChatGPT/AI assistance** (including the initial Groovy driver and this documentation). It was developed iteratively using real Zigbee logs and hands-on tests on a **single Hubitat C-8 + Aqara H2 EU outlet**. The maintainer reviewed test results, but the code has **not been independently audited, formally verified, or broadly hardware-tested**. There may be bugs, wrong assumptions, missed edge cases and unverified configuration writes. Please treat it as an experimental community driver, not vendor-supported software.

## Repository layout

```text
hubitat-drivers/
├── README.md
├── LICENSE
├── THIRD_PARTY_NOTICES.md
├── repository.json                        # HPM repository manifest
└── drivers/
    └── aqara/
        └── h2-wall-outlet/
            ├── aqara-h2-wall-outlet.groovy
            └── packageManifest.json       # HPM package manifest
```

Future drivers can live under `drivers/aqara/` or a similar directory for their manufacturer. There is no build process, helper script, or CI requirement.

## Aqara H2 EU wall outlet

**Preview v0.6.0** (the packaged v0.6.0 source has **not yet been compiled or re-tested on the hub**; the observed behavior below comes from earlier development versions) — Aqara **WP-P01D**, Zigbee model **`lumi.plug.aeu001`** in **Zigbee mode**, paired directly to Hubitat. **No Aqara hub, cloud service or MQTT bridge required.** Tested on **Hubitat C-8, one outlet**; the device firmware revision was not confirmed.

### Actual state of the implementation

| Feature | Status | Evidence / caveat |
| --- | --- | --- |
| Relay `on` / `off` | ✅ Tested | **Endpoint `01`** changes actual power on one WP-P01D; endpoint `02` changed logical state without operating the relay. |
| Switch state reporting | ✅ Tested | Endpoint `01` reports switch state; physical button operation observed. |
| `setLed(on/off)` | ✅ Tested | Physical LED changed on the test outlet. |
| `setButtonLock(lock/unlock)` | ✅ Tested | Physical button locking was confirmed after initial troubleshooting. Remote-control fallback and behavior across power failures need broader testing. |
| `setPowerOnBehavior(previous)` | ✅ Tested | Outlet restored its previous state on power restoration; the LED briefly illuminated during one test. This **does not prove** that mains power never briefly energized. |
| Live `power` (W) | ✅ Observed | Approximately 1–16 W with small loads and 400–500 W printer peaks; raw readings seem plausible, not calibrated against an external meter. Updates may be delayed. |
| Accumulated `energy` (kWh) | ✅ Observed | Value increased to `0.005` kWh during tests; scaling and accuracy not independently calibrated. |
| Voltage/current | ⚠️ Partial | Parsing implemented, physical correctness/units not fully verified. |
| Other power-on modes (`on`, `off`, `inverted`) | ⚠️ Unverified | Exposed in the driver but not tested. |
| Charging protection and charging limit | ⚠️ Experimental | Manufacturer attributes exposed; write behavior not hardware-verified. |
| Overload threshold | ⚠️ Experimental | Write behavior and safe trip characteristics not verified; **not a safety device**. |
| Firmware variants / other Hubitat models | ❔ Unknown | Only one Zigbee WP-P01D / C-8 combination tested. |

**Safety:** This driver must not be used as a safety interlock, medical protection, fire-prevention mechanism, or alternative to qualified electrical installation, breakers, RCDs or certified device protection. Do not depend on `previous` mode to guarantee no momentary power on restoration. Test each installation with harmless loads before use. **ADVANCED changes** are disabled by default, and `configure()` does not silently change proprietary settings.

### Install through Hubitat Package Manager (HPM)

After this repository has been published on GitHub, in HPM select **Install → From a URL** and paste the **package manifest** (not the Groovy source):

```text
https://raw.githubusercontent.com/velkovb/hubitat-drivers/main/drivers/aqara/h2-wall-outlet/packageManifest.json
```

Or in HPM **Settings → Add a Custom Repository**, use:

```text
https://raw.githubusercontent.com/velkovb/hubitat-drivers/main/repository.json
```

**Availability:** These URLs will return 404 until the repository is created and pushed to `main`. The driver is **not** yet registered in HPM's global community directory.

#### Already installed an earlier beta manually?

First make a backup of the working Groovy code. Edit the **existing** code in **Drivers Code** (do not create a duplicate driver or delete a working device), save, and verify switch control and settings. Then use HPM **Match Up** if appropriate to adopt managed updates. Keep the HPM driver ID stable between releases.

### Main commands

- `on`, `off`, `refresh`, `configure` — local relay and status (`configure()` is read-only).
- `probeMetering` and `refreshSettings` — explicit diagnostics and reads.
- `setLed(on|off)` — working LED control.
- `setButtonLock(lock|unlock)` — tested physical-button lock; requires **ADVANCED changes** opt-in.
- `setPowerOnBehavior(on|previous|off|inverted)` — only `previous` has been tested.
- `setChargingProtection(on|off)`, `setChargingLimit(0.1..2)`, `setOverloadProtection(100..3840)` — **experimental; do not use on important loads**.

### Issue reports / contributions

Issues and PRs are welcome. Please include **Hubitat model and platform version**, Zigbee device model/firmware if known, expected vs observed behavior, relevant **redacted** Hubitat logs, and steps to reproduce. Do not post IP addresses, tokens, secrets or identifiable home network details. Test on a spare outlet before applying any proposed fixes broadly.

## Releasing / licensing / credits

- One `repository.json` at root; each driver gets its own HPM `packageManifest.json` alongside its Groovy source.
- Keep the package driver **UUID**, name and namespace stable. On release, update the package `version`, `dateReleased`, and `releaseNotes`; verify that its raw URLs resolve after pushing changes. No Python setup script is needed.
- **MIT License** for the project's own original code and documentation: [`LICENSE`](LICENSE).
- **Sources and acknowledgements:** [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md). This project used technical references from andreibsk's ZHA quirk, Zigbee2MQTT, SmartThings community discussion, Hubitat documentation and HPM. **No third-party source file is distributed or intentionally copied verbatim.** The standalone Gist is not assumed to be MIT-licensed.
- **Unofficial project:** not affiliated with or endorsed by Aqara, Hubitat, Zigbee or SmartThings.
