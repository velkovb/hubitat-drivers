# W500 pilot validation - 21 October 2026

## Deployment

- User approved installation and read-only live checks.
- Hubitat compiled and verified driver 2253, v0.1.0, assigned to paired device 827.
- Prior driver: built-in Device, type 210. No re-pairing or automation edits.
- Installed source was read back and compared exactly with reviewed repository
  source, ignoring only surrounding whitespace (13240 characters).
- Configure bound clusters and requested reads; no thermostat setting writes,
  heating commands or OTA commands were issued.
- Debug logging enabled for the trial, with logsOff scheduled after 30 minutes.
- Optional 15-minute measurement polling job is present.

## Live readbacks

| Reading | Value |
| --- | --- |
| Temperature | 22.2 C |
| Humidity | 46% |
| Heating setpoint | 25.5 C |
| Thermostat mode | heat |
| Standard running state | heating (0x0029 = 0x0001) |
| Aqara working state | idle (0x0310 = 2) |
| Power / energy | 0 W / 0 kWh |
| Sensor source | internal |
| Setpoint limits | 5 to 35 C |
| Temperature calibration | 0 C |
| Hysteresis | 0 C |
| Hold / duration | off / 120 minutes |
| Child lock | unlocked |
| Window detection | off |
| Power-outage memory | on |
| NTC R25 / beta | 10 kOhm / 3950 K |

These are reported values, not proof of loaded heating operation. In particular,
the standard running state and proprietary working-state attribute disagree on
this setup. They are kept separate rather than guessing which represents the
physical relay. Validate against the display and a suitable load before using
operating-state events as automation triggers.

The software-build attribute (Basic 0x4000) returned unsupported (0x86). Basic
application version is 0x1E; it is not a verified complete firmware version.
Preset 0x0311 returned 0xFF, outside the documented mapping, so the driver did
not invent a preset value. Other documented preset writes remain untested.

Live debug logs showed responses to temperature, humidity, thermostat settings,
metering and manufacturer reads, with no parser error in the inspected interval.
Unsupported software-build readback was ignored correctly. Regular unsolicited
temperature and manufacturer heartbeat reports were also observed; proprietary
heartbeat content is not decoded in this version.

## Initial outstanding validation (superseded by later results below)

- Physical button changes and commanded heat/off/setpoint/preset/settings.
- Physical relay versus the two reported operating-state fields.
- Nonzero power/energy accuracy on a connected load.
- Availability of a W500 firmware image via Hubitat and actual OTA operation.
- Long-term reliability, external sensors and additional firmware variants.

Rollback: reassign built-in Device (type 210) to device 827. This stops using this
driver; it does not undo any later intentional settings changes. Existing state
attributes can remain displayed until cleared; they are not fresh readbacks after
rollback.


## Command tests - 21 October 2026, 09:23-09:25 Europe/Sofia

The user authorized live testing after installation. A fresh baseline was heat
mode, 18.5 C setpoint, hold on, 120-minute duration, internal sensor. The setpoint
had changed since the initial installation snapshot; the fresh baseline was used
for restoration.

| Test | Device readback |
| --- | --- |
| Off command | Mode off, converged in 656 ms |
| Heat command | Mode heat, converged in 618 ms |
| Setpoint 24 C | Setpoint 24 C, converged in 619 ms |
| Demand above measured 22.4-22.5 C | Standard 0x0029 state = heating |
| Restore 18.5 C | Setpoint restored in 623 ms; standard 0x0029 state = idle |
| Final restored state | Heat mode, 18.5 C, hold on, 120-minute duration |

Readbacks and unsolicited reports confirm delivered thermostat commands rather
than only tool dispatch. The driver sends no optimistic state events. No preset,
sensor association, NTC calibration, child lock or protection setting was changed.

The proprietary 0x0310 state was 0 (`working`) during both the high-setpoint and
low-setpoint phases. It does not track demand the same way as standard 0x0029 in
this trial. Keep it separate; standard `thermostatOperatingState` did track the
setpoint changes correctly. Physical relay/load behavior still requires a local
observation. Power and energy remained zero during the short test, which does not
validate loaded metering.

A single explicit Update Firmware command was issued at 09:24:49. The driver
called Hubitat's OTA helper and an OTA-related discovery packet appeared in logs.
No completed firmware transfer, success status or new firmware version was
observed in the inspected interval. This establishes command dispatch only, not
working OTA or the existence of an image on Hubitat's servers. No automatic
update or retry is scheduled.


## Final v0.1.1 validation - 21 October 2026, 10:01-10:03 Europe/Sofia

The owner confirmed that no heater is connected, heard the relay clicks during
the control tests, and subsequently checked the heating icon manually. Physical
relay actuation and the display indication are therefore confirmed; loaded power
and energy accuracy cannot be established on this installation yet.

The fresh settings-test baseline was Sleep, 20 C, heat mode, hold on, duration
120 minutes, internal sensor, calibration 0, hysteresis 0, window detection off,
child lock unlocked and power recovery memory on. Optional setting preferences
were unset and debug logging was false.

The first Manual preset test exposed a real protocol error in v0.1.0: writing 8
returned raw 7 and a 5 C target. Sleep/20 C was restored. The Manual value 255 was
cross-checked against a separate W500 implementation, corrected in v0.1.1, and
tested successfully. Unknown raw preset codes now explicitly report unknown
rather than preserving an obsolete previous preset. Regression tests cover both
the corrected command encoding and readback behavior.

| Preset | Raw readback | Target readback | Convergence |
| --- | --- | --- | --- |
| Manual | 255 | retained 20 C | 645 ms |
| Home | 1 | 26 C | 642 ms |
| Away | 2 | 18 C | 569 ms |
| Vacation | 5 | 10 C | 673 ms |
| Evening | 6 | 24 C | 625 ms |
| Sleep | 3 | 20 C | 618 ms |

| Reversible setting test | Confirmed readbacks |
| --- | --- |
| Hysteresis | 0.5 then restored 0 C |
| Calibration | +0.5 then restored 0 C |
| Hold duration | 30 then restored 120 minutes |
| Hold | off then restored on |
| Window detection | on then restored off |
| Child lock | locked then restored unlocked |
| Power-outage memory | off then restored on |

Each test used device-reported attributes, not optimistic events. Hold, hysteresis
and calibration writes converged in approximately 0.6 seconds. Preference tests
converged before the follow-up read. After testing, preference selections were
cleared back to their original unset state and debug logging was restored false.
Final state: Sleep, 20 C, heat, hold on/120 minutes, sensor internal, original
calibration/hysteresis/lock/window/memory settings restored. No sensor-source or
NTC calibration change, physical power interruption, factory reset or repeat OTA
attempt was performed.

The corrected source compiled on Hubitat as code version 2 of driver 2253, and the
device reports driverVersion 0.1.1. The local Groovy harness passed again. Remaining
limits are nonzero metering accuracy, a confirmed OTA transfer/update, external
sensor integration, schedule upload and long-term validation on additional units.
