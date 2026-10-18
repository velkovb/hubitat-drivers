# Aqara H2 EU Wall Outlet — Hubitat driver

**Preview v0.6.0 · Local Zigbee · AI-assisted / human-tested**

Community driver for the **Aqara H2 EU Wall Outlet** (`WP-P01D`, Zigbee model `lumi.plug.aeu001`) when joined **directly to Hubitat Elevation** in Zigbee mode. No Aqara hub, vendor cloud, or MQTT bridge is required for the tested functions.

> [!WARNING]
> This driver is experimental. It was developed with significant ChatGPT/AI assistance and iterative hands-on testing on **one Hubitat C-8 and one WP-P01D**. The packaged v0.6.0 source itself has **not yet been re-tested on the hub**; test evidence below comes from earlier development versions. Device firmware revision was not confirmed. There has been no independent code/security audit, and compatibility with other firmware or Hubitat models is unknown.

## Compatibility

| Item | Details |
| --- | --- |
| Device | Aqara H2 EU Wall Outlet, `WP-P01D` |
| Zigbee model | `lumi.plug.aeu001` |
| Protocol | Zigbee, direct pairing; **not Matter/Thread mode** |
| Tested Hubitat | Elevation **C-8** |
| Firmware | Not captured in the test notes |
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

**Migrating an existing beta:** Back up the working driver code first. Update the existing Drivers Code entry and verify the relay and settings before using HPM **Match Up** to manage future updates. Avoid adding duplicate driver definitions unnecessarily.

## Driver commands

| Command | Purpose | Status |
| --- | --- | --- |
| `on`, `off` | Switch the physical outlet | Tested |
| `refresh`, `configure` | Read current state / basic configuration; `configure()` does **not** write proprietary settings | Implemented |
| `probeMetering` | Read measurement attributes for diagnosis | Used in tests |
| `refreshSettings` | Read Aqara settings | Implemented; individual readbacks may vary |
| `setLed(on\|off)` | Control LED | Tested |
| `setButtonLock(lock\|unlock)` | Lock/unlock physical button | Tested |
| `setPowerOnBehavior(on\|previous\|off\|inverted)` | Behavior after mains power returns | `previous` tested only |
| `setChargingProtection(on\|off)` | Low-load automatic cutoff | Experimental |
| `setChargingLimit(0.1..2)` | Low-load charging threshold, W | Experimental |
| `setOverloadProtection(100..3840)` | High-load cutoff threshold, W | Experimental |

**Advanced writes:** Manufacturer-specific configuration writes require explicit opt-in in driver preferences; they are disabled by default. Do not change thresholds on important equipment without validating the behavior.

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