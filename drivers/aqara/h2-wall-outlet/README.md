# Aqara H2 EU Wall Outlet — Hubitat driver

**Preview v0.7.0 · Local Zigbee · AI-assisted / human-tested**

Community driver for the **Aqara H2 EU Wall Outlet** (`WP-P01D`, Zigbee model `lumi.plug.aeu001`) when joined **directly to Hubitat Elevation** in Zigbee mode. No Aqara hub, vendor cloud, or MQTT bridge is required for the tested functions.

> [!WARNING]
> This driver is experimental. It was developed with significant ChatGPT/AI assistance and iterative hands-on testing on **one Hubitat C-8 and one WP-P01D**. Version 0.7.0 compiled on Hubitat and its firmware diagnostics were tested live on 22 October 2026. Earlier development versions provided the relay, settings and metering evidence below; those features were not all re-tested with v0.7.0. The complete OTA file version remains unconfirmed.
>
> There has been no independent code/security audit, and compatibility with other firmware or Hubitat models is unknown.

## Compatibility

| Item | Details |
| --- | --- |
| Device | Aqara H2 EU Wall Outlet, `WP-P01D` |
| Zigbee model | `lumi.plug.aeu001` |
| Protocol | Zigbee, direct pairing; **not Matter/Thread mode** |
| Tested Hubitat | Elevation **C-8** |
| Firmware | Basic application `0x20` (32), hardware `01`, date code `20250611`; full OTA file version unconfirmed |
| Physical relay endpoint | **`01`** on the tested device; endpoint `02` changed logical state but did not switch outlet mains power |

## Feature status

| Function | Observed status | Notes |
| --- | --- | --- |
| Outlet ON/OFF | **Tested** | Remote commands operated the physical outlet through Zigbee endpoint `01`. |
| Switch-state reports | **Tested** | Endpoint `01` reported relay state; physical button operation was observed. |
| LED on/off | **Tested** | Physical LED changed in response to driver commands. |
| Physical button lock/unlock | **Tested** | User confirmed the physical button could be locked. Wider recovery testing remains outstanding. |
| Power-on behavior: `previous` | **Tested** | Previous OFF state was restored after power interruption. LED briefly illuminated in one test; this does **not** prove there was no brief mains energization. |
| Other power-on modes | **Not verified** | `on`, `off`, and `inverted` are exposed but have not been tested. |
| Live power (W) | **Observed** | Roughly 1–16 W for small loads and 400–500 W printer peaks; readings updated with some delay. No independent calibration. |
| Accumulated energy (kWh) | **Observed** | Counter advanced to `0.005` kWh in one test; scaling not independently calibrated. |
| Voltage/current | **Partially implemented** | Measurements and scaling have not been verified against an external meter. |
| Charging protection / charging limit | **Experimental** | Attribute writes exposed, but protection behavior unverified on hardware. |
| Overload limit | **Experimental** | Threshold writes and trip behavior unverified. **Not a certified protective device.** |
| Other firmware / hub models | **Unknown** | Only one WP-P01D / C-8 combination tested. |

## Installation

### Hubitat Package Manager (HPM)

In HPM, select **Install → From a URL**, then use the **package manifest URL** (not the Groovy file URL):

```text
https://raw.githubusercontent.com/velkovb/hubitat-drivers/main/drivers/aqara/h2-wall-outlet/packageManifest.json
```

Alternatively, add the project's [repository manifest](../../../repository.json) as a custom HPM repository, then select this package:

```text
https://raw.githubusercontent.com/velkovb/hubitat-drivers/main/repository.json
```

**Manual installation:** Import or paste [`aqara-h2-wall-outlet.groovy`](aqara-h2-wall-outlet.groovy) under Hubitat **Drivers Code**, save it, and select the new driver for the paired WP-P01D device.

## Driver commands

| Command | Purpose | Status |
| --- | --- | --- |
| `on`, `off` | Switch the physical outlet | Tested |
| `refresh`, `configure` | Read current state / basic configuration; `configure()` does **not** write proprietary settings | Implemented |
| `readFirmwareInfo` | Read device identity, Basic version fields and Aqara firmware field | Tested live; see below |
| `updateFirmware` | Request native Hubitat Zigbee OTA; may start a transfer | Experimental; tested request failed with C3 |
| `probeMetering` | Read measurement attributes for diagnosis | Used in tests |
| `refreshSettings` | Read Aqara settings | Implemented; individual readbacks may vary |
| `setLed(on\|off)` | Control LED | Tested |
| `setButtonLock(lock\|unlock)` | Lock/unlock physical button | Tested |
| `setPowerOnBehavior(on\|previous\|off\|inverted)` | Behavior after mains power returns | `previous` tested only |
| `setChargingProtection(on\|off)` | Low-load automatic cutoff | Experimental |
| `setChargingLimit(0.1..2)` | Low-load charging threshold, W | Experimental |
| `setOverloadProtection(100..3840)` | High-load cutoff threshold, W | Experimental |

**Advanced writes:** Manufacturer-specific configuration writes require explicit opt-in in driver preferences; they are disabled by default. Do not change thresholds on important equipment without validating the behavior.

## Firmware diagnostics and native OTA

`readFirmwareInfo` reads the Basic cluster manufacturer, model, application,
hardware, date code and software build fields, plus Aqara `0xFCC0/0x00EE`.
Refresh includes these read-only probes. Unsupported values remain absent.
`driverVersion` identifies the driver, independently of the device firmware.
The Aqara field is retained as decimal `firmwareFileVersion` and hexadecimal
`firmwareFileVersionHex`, with `firmwareSource` and `firmwareVersionStatus`.
A zero response is explicitly marked **unconfirmed**, rather than treated as
proof of installed OTA image version zero. Basic fields may also be captured
directly by Hubitat into the device Data section. The application byte is a
separate field and is not automatically equated with a complete OTA file version.

Live results on 22 October 2026 with model `lumi.plug.aeu001`:

- Basic application: `20` hex (32 decimal); hardware: `01`; date code: `20250611`.
- Aqara firmware field: `00000000`, repeatedly; full OTA file version unconfirmed.
- Basic software build `0x4000`: unsupported (`0x86`).
- Native OTA request: Hubitat logged `status:C3`, `imageFileName:null`,
  `fileVersion:null`. No transfer was observed. The cause remains unresolved;
  this does not establish that no newer firmware exists.

`updateFirmware` delegates to Hubitat's `zigbee.updateFirmware(manufacturer:0x115F)`
after verifying the exact model. It is **not a check-only command**: Hubitat may
start a transfer if it can offer a matching image. Its `otaRequestStatus` records
the request, not success or progress; monitor Hubitat logs for the actual outcome.
Read diagnostics again afterward. The driver does not force an image type/version,
upload firmware, accept arbitrary URLs, or read OTA-client cluster attributes.

Zigbee2MQTT documents OTA support for this model. Its OTA catalog inspected on
22 October 2026 lists an exact-model image with file version 33 (`0x00000021`),
manufacturer `0x115F`, image type 39694, and size 609594 bytes. The filename is
`20260120122157_OTA_lumi.plug.aeu001_0.0.0_0033_20260109_2A1D5E.ota`.
Availability there does not establish that Hubitat offers the same image.
End-to-end firmware updates through this driver are **not validated**.

Local Groovy tests cover identity and firmware parsing, unsupported/sentinel
responses, explicit zero status, read-only probes, exact-model guard, native OTA
delegation, relay routing and existing floating-point power decoding. Run with
Groovy 2.4 using `groovy test-firmware.groovy aqara-h2-wall-outlet.groovy`.

References:

- [WP-P01D OTA support](https://www.zigbee2mqtt.io/devices/WP-P01D.html)
- [Zigbee2MQTT OTA catalog](https://github.com/Koenkk/zigbee-OTA/blob/master/index.json)
- [Hubitat native OTA example](https://github.com/hubitat/HubitatPublic/blob/master/examples/drivers/GenericZigbeeRGBWBulb.groovy)

## Safety and known limitations

- **Do not use the outlet or this driver as a safety interlock**, a substitute for circuit breakers/RCDs, or protection for critical appliances, medical devices or fire hazards.
- `previous` power-on mode restoring the displayed state is not proof that the socket never briefly supplies power during boot. If this matters, test with a harmless load and suitable measurement equipment.
- Power and energy appear responsive, but their precision, persistence, and scaling have not been independently measured.
- Hardware behavior can differ by firmware, device revision, Zigbee network and hub platform version.
- Test on **one noncritical outlet** before using the driver throughout your home. Keep a way to restore physical button control if you enable lock.

## Reporting problems

Please open an issue in the [parent repository](https://github.com/velkovb/hubitat-drivers/issues) and include: WP-P01D firmware (if known), Hubitat model/platform version, the command used, expected vs actual behavior, and **redacted** Zigbee logs. Do not publish IP addresses, tokens, house identifiers or other private data.

## Credits, licensing and development disclosure

This is an independently authored Groovy integration produced with significant **ChatGPT/AI assistance** and **human hardware tests**, not a verified official Aqara or Hubitat driver. The original code is made available under the repository's [MIT License](../../../LICENSE).

The device-specific research drew on published [Zigbee2MQTT WP-P01D documentation](https://www.zigbee2mqtt.io/devices/WP-P01D.html), [andreibsk's Aqara H2 ZHA Gist](https://gist.github.com/andreibsk/fa9d68a417430bb4d00ff592345866de), and SmartThings community discussion, alongside Hubitat documentation. These sources are credited as technical references, **not redistributed as copied source code**. In particular, no standalone license was identified for the Gist, so it must not be assumed freely reusable as source. See the repository's full [third-party notices](../../../THIRD_PARTY_NOTICES.md).

This unofficial project is not affiliated with or endorsed by Aqara, Hubitat, SmartThings, or Zigbee.
