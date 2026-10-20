# Aqara H2 EU WS-K08E / WS-K08D for Hubitat

Version 0.1.0, experimental. Zigbee model `lumi.switch.agl010` only.
Independent Groovy implementation; protocol references are linked below. MIT; see the repository LICENSE.

## Device layout

The parent provides four buttons, configuration and whole-device metering. Two
built-in **Generic Component Switch** children control the left and right relays.
The parent intentionally has no all-relays Switch command.

| Physical control | Hubitat button | Zigbee endpoint |
| --- | --- | --- |
| Upper left / left relay | 1 | 1 |
| Upper right / right relay | 2 | 2 |
| Lower left | 3 | 4 |
| Lower right | 4 | 5 |

EU gesture values: 0 = held, 1 = pushed, 2 = doubleTapped, 255 = released.
Upper buttons are expected to emit single presses on current firmware; declaring
all four Hubitat capabilities does not guarantee every gesture for every button.
Lower double/hold/release gestures require enabling their multiple-click setting.
The public SmartThings implementation notes holds can require about four seconds.

## Hubitat Package Manager

Preview package manifest: https://raw.githubusercontent.com/velkovb/hubitat-drivers/main/drivers/aqara/h2-eu-wall-switch/packageManifest.json

Add this repository in HPM using https://raw.githubusercontent.com/velkovb/hubitat-drivers/main/repository.json .
For an existing manual installation, Match Up can adopt the same driver name and namespace.
Core controls and advanced settings were tested on one WS-K08E with neutral and no load.
Metering accuracy and startup output transients remain unverified.

## Installation and operation

1. Install `aqara-h2-eu-ws-k08e.groovy` in Hubitat Drivers Code.
2. Select **Aqara H2 EU 4 Button 2 Relay (WS-K08E)** on the paired Zigbee device.
3. Save and click **Configure** once. It creates the two children, binds clusters,
   configures relay reporting and reads identity, settings and metering.
4. Use the child devices for relay control and the parent for Button Controller
   or Rule Machine button triggers.

Preferences default to **Keep device setting**. Save Preferences applies only
explicit selections. Configure reapplies selections, too. Current-state attributes
are readbacks, not optimistic acknowledgments of writes. A failed or unsupported
write therefore does not falsely confirm a setting.

Leave upper button modes at **Control wired relay** for manual lighting control
without Hubitat. Decoupled mode changes that behavior. Relay lock is separate and
blocks relay operation; leave it off for ordinary wired lights.

Refresh sends only reads. It does not poll button attributes or create gestures
from read responses. There is no periodic gesture reporting configured. Repeat
presses generate separate events, even if the button and action match.

Metering requires appropriate hardware/wiring (normally neutral). Power and energy
are whole-device totals, not per-relay values. Electrical power and metering use
the device's reported multipliers/divisors; values are withheld until scaling is
known. Analog power at endpoint 21 is in watts and takes precedence for 120 seconds.
Current uses the WS-K08E-specific unscaled value documented by Zigbee2MQTT. Current
reporting is not forcibly configured because the model is known to reject it.
No OTA updates, proprietary heartbeat decoding, or energy reset are implemented.

## Initial validation on 19 October 2026

- Local Groovy 2.4.21 harness passed: real parser, captured relay frames, all EU
  gesture values/endpoints, repeated presses, unknown/invalid messages, unsupported
  additional attributes, zero values, child command routing, setting wire values,
  power/energy scaling and preservation of wall-control settings during Configure.
- Hubitat compiled and installed the driver on one paired WS-K08E.
- Configure created the two relay children successfully.
- Live reads confirmed `Aqara` / `lumi.switch.agl010`, left on / right off,
  both upper buttons coupled, both relay locks off, lower multi-click off,
  LED on, inverted LED off, power recovery previous.
- Live power scaling: multiplier 1 / divisor 10. Energy: multiplier 1 / divisor
  1000, unit kWh. Power and energy read 0. No current value received at initial check.
- No relay on/off command or behavior-setting write was sent during deployment.
- Follow-up hardware tests are recorded below. Nonzero metering and long-term reliability remain unvalidated.

Run local tests from the repository root with Groovy 2.4.21:

```powershell
java -cp /path/to/groovy-all-2.4.21.jar groovy.ui.GroovyMain drivers/aqara/h2-eu-wall-switch/test-driver.groovy
```

Research files and the Groovy runtime are not bundled in this repository. Development used
downloaded protocol references and Groovy 2.4.21; neither is a runtime dependency on Hubitat.

## Hardware validation on 20 October 2026

Tested on one Hubitat C-8 and one WS-K08E (`lumi.switch.agl010`) with neutral,
powered from a plug-in test cable, with no loads connected. WS-K08D is protocol
compatible according to upstream references but was not physically tested.

| Feature | Result |
| --- | --- |
| Left and right relay commands | On/off confirmed by incoming Zigbee reports, approximately 0.4-0.5 seconds; channel independence checked |
| Physical single presses | All four positions confirmed: upper left 1, upper right 2, lower left 3, lower right 4 |
| Lower multiple-click settings | Enabled independently by endpoint; both read back on |
| Lower gestures | Physical doubleTapped, held and released confirmed for buttons 3 and 4 |
| Decoupled mode | Both sides: physical press generated the corresponding event without switching its relay; remote on/off still worked |
| Relay locks | Both sides: remote on blocked while locked; fresh reads confirmed off; unlocking restored on/off control |
| LED indicator | User visually confirmed enabled/disabled with relay held on |
| Inverted indication | User confirmed relay on / indicator dark and relay off / indicator lit; restored normal indication |
| Restore previous state | After a roughly 10-second unplug, left on / right off restored; communication resumed |
| Recovery off | Both on before power cycle, both off after reconnection |
| Recovery on | Both off before power cycle, both on after reconnection |
| Inverted recovery | Left on / right off before power cycle became left off / right on |

Recovery results establish final reported states, not the absence of brief output
transients during boot. The user heard relay clicks during startup; two clicks can
be one per relay and do not establish an extra on/off cycle. No connected load or
measurement was available to confirm whether any brief output pulse occurred.

An initial remote-on attempt during the left decoupled trial timed out; subsequent
coupled and decoupled on/off tests passed. The cause of that isolated timeout was
not established. Inspected functional-test logs showed no parser errors.

Final restored configuration: both relays off, both upper buttons coupled,
both locks off, LED enabled with inversion off, power recovery previous.
Lower multi-click remains enabled on both buttons.

Still untested: loaded power/current/energy accuracy, physical button behavior
while relay-locked, long-term reliability, no-neutral operation and other firmware
versions. Software-generated button events are covered by implementation but were
not tested through a dashboard or automation in this hardware trial.

## Rollback

The pilot previously used built-in **Device**. Reassign it to that driver
to stop using this implementation. No re-pairing is needed. Children remain; avoid
deleting any child after adding it to automations without checking dependencies.
Restoring the old driver does not undo any subsequently selected hardware settings;
restore those settings first while this driver is active.

## References

- [Zigbee2MQTT WS-K08E](https://www.zigbee2mqtt.io/devices/WS-K08E.html)
- [ZHC device definition](https://github.com/Koenkk/zigbee-herdsman-converters/blob/master/src/devices/lumi.ts)
  and [Lumi attribute definitions](https://github.com/Koenkk/zigbee-herdsman-converters/blob/master/src/lib/lumi.ts):
  endpoint map, manufacturer code, gesture values, setting encodings, current scaling.
- [SmartThings H2 implementation](https://github.com/ldeora/SmartThingsEdgeDrivers/tree/main/aqara-h2-wall-switches):
  cross-check of child architecture, gestures and endpoint 21 analog power.
- [Hubitat H2 US driver](https://github.com/krazik/hubitat-drivers/blob/main/AqaraH2Switch.groovy):
  Hubitat conventions only. Its gesture mapping and single-relay layout are not
  used for the EU WS-K08E.
- Hubitat's on-hub Groovy API reference: explicit `destEndpoint` parameters,
  manufacturer framing, read/write/report command builders.

References inspected 19 October 2026. No reference implementation is copied wholesale.
