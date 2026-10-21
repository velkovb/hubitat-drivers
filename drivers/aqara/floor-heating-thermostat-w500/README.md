# Aqara Floor Heating Thermostat W500 for Hubitat

Preview v0.1.1. UT-A01E, Zigbee model `lumi.airrtc.aeu001`, endpoint 1.
Independent implementation under the repository MIT license.

## Features

- Hubitat Thermostat: heat/off, temperature, heating setpoint and operating state.
- Celsius/Fahrenheit hub units; half-degree Celsius device setpoint increments.
- Humidity, power in W and total energy in kWh.
- Home, away, sleep, vacation, evening and manual presets.
- Temperature-source selection (internal, external, NTC), calibration, hysteresis,
  setpoint hold and hold duration. Calibration and hysteresis commands use Celsius.
- Optional window detection, child lock and power-outage memory preferences.
- Readback of minimum/maximum heating limits and NTC R25/beta parameters.
- Firmware build string, last seen timestamp, identify and manual OTA delegation.

Current attributes are device readbacks, never optimistic confirmations of writes.
Configure binds clusters and reads state. All preferences initially keep the device
setting. Changing preferences writes only explicitly selected options. Configure
does not change the target temperature, heating mode, preset, sensor source, NTC
parameters or protection settings.

The device controls its heating locally. Hubitat is not a replacement for the
thermostat's hardware protections or the heating system's electrical safeguards.

## Pairing and installation

1. Switch to Zigbee in Aqara Home if necessary. Two signal bars indicate Zigbee;
   four indicate Thread. Start Zigbee pairing in Hubitat and put the W500 in its
   network setup mode using the reset instructions in its manual.
2. Install `aqara-w500.groovy` under Drivers Code and assign **Aqara Floor Heating
   Thermostat W500** to the existing paired device. No re-pairing is needed.
3. Save and click Configure. Check temperature, humidity, setpoint and mode.
4. Test physical button changes and ordinary heating control on the actual hardware
   before adding it to automations. Keep debug logging enabled during initial tests.

Configure creates bindings, but does not force periodic reporting configurations
that the device may reject. Optional measurement reads run every 15 minutes; the
preference can disable them. Refresh also reads settings and firmware identity.

The broad Thermostat capability exposes cooling/fan/auto commands in some Hubitat
interfaces. These issue a warning and no Zigbee command. Supported modes are
advertised as `off` and `heat`, with no fan modes. This driver does not force the
manual preset when setting a temperature: an existing device schedule can resume
after its hold period. Set the preset/hold explicitly if needed.

## Presets and tested settings

All six preset selections were verified with device readbacks on the pilot:

| Preset | Pilot target temperature |
| --- | --- |
| Home | 26 C |
| Away | 18 C |
| Sleep | 20 C |
| Vacation | 10 C |
| Evening / Wind down | 24 C |
| Manual | Retains the current manually selected temperature |

These comfort temperatures are values stored on the pilot, not constants imposed
by the driver. The M icon denotes manual operation; a temporary manual adjustment
can revert to an active onboard schedule after the hold duration.

Version 0.1.1 corrects Manual to manufacturer attribute 0x0311 value 255. The
initial Zigbee2MQTT reference used 8; the pilot returned 7 and changed its target
to 5 C for that write. Value 255 was independently cross-checked and passed the
live test. `presetRaw` exposes the readback, and an unknown code now reports
`preset = unknown` instead of leaving an obsolete preset displayed.

Hysteresis (0 -> 0.5 -> 0 C), calibration (0 -> 0.5 -> 0 C), hold duration
(120 -> 30 -> 120 minutes), hold off/on, window detection on/off, child lock
locked/unlocked and power-outage memory off/on passed write/readback tests.
Original settings and unset preference selections were restored. Power recovery
was not tested by interrupting mains power; window detection and child lock tests
confirm configuration readbacks, not every physical protection scenario.

## OTA firmware

**Update Firmware** manually calls Hubitat's built-in `zigbee.updateFirmware()`.
It is never called by installation, Configure, Refresh, preferences or polling.
Watch Hubitat live logs for the actual result. Availability of a compatible W500
image on Hubitat's servers is **unverified**. Zigbee2MQTT's OTA support does not
establish that Hubitat hosts the same firmware. This driver does not download,
validate or serve arbitrary firmware files, or claim successful flashing.

`firmwareVersion` is the Basic-cluster software build string if the thermostat
supplies it. Driver version and device firmware version are separate.

## Known limitations

- Local tests, Hubitat compilation, readings, heat/off/setpoint, all six presets and reversible settings passed on one device. The owner confirmed relay clicks and the heating icon. Loaded metering and successful OTA remain unverified. See [live validation](LIVE-VALIDATION.md).
- Power uses the W500-specific divisor 1; energy uses divisor 1000 from the ZHC
  definition. Nonzero metering still requires validation on a connected load.
- Onboard weekly schedule upload and preset-temperature-table editing are absent.
  Use Hubitat scheduling for setpoint changes, or retain the device's schedule.
- External sensor selection does not bind an Aqara sensor or inject measurements.
  External sensor association/data delivery requires further protocol work.
- NTC calibration writes are absent because of firmware-specific encoding quirks.
  Existing sensor calibration and floor protections are left intact.
- No energy reset, OTA firmware image hosting or automatic firmware updates.
- Native fault/alarm reports and proprietary heartbeats are not decoded yet.

## Validation and rollback

Local Groovy 2.4.21 tests execute the real parser and command builders against
Hubitat boundary stubs. They cover signed temperatures/calibration, zero and
invalid/sentinel values, additional attributes, wrong endpoints, Fahrenheit
conversion, configured limits, metering, NTC readbacks, command encodings,
preservation of device settings and manual-only OTA delegation.

```powershell
java -cp /path/to/groovy-all-2.4.21.jar groovy.ui.GroovyMain drivers/aqara/floor-heating-thermostat-w500/test-driver.groovy drivers/aqara/floor-heating-thermostat-w500/aqara-w500.groovy
```

The test runtime is a development dependency, not installed on Hubitat.
Rollback: reassign the prior driver. The initial pilot was device 827 with built-in
**Device** (type 210). Restoring a driver does not undo deliberately changed
hardware settings; restore them first if necessary. No existing automation is
modified by this package.

## Hubitat Package Manager

The package manifest is prepared for publication under
`drivers/aqara/floor-heating-thermostat-w500/packageManifest.json`. Its main-branch
URLs become usable only after the package is published there. Do not infer
publication or HPM availability from the local manifest alone.

## References

- [Zigbee2MQTT UT-A01E](https://www.zigbee2mqtt.io/devices/UT-A01E.html).
- [ZHC device definition](https://github.com/Koenkk/zigbee-herdsman-converters/blob/master/src/devices/lumi.ts):
  model, endpoint, thermostat fields, manufacturer attributes and metering scaling.
- [ZHC Lumi library](https://github.com/Koenkk/zigbee-herdsman-converters/blob/master/src/lib/lumi.ts):
  power-outage memory and firmware-specific NTC decoding facts.
- [Aqara W500 manual](https://cdn.shopify.com/s/files/1/0658/9068/1031/files/Floor_Heating_Thermostat_W500_User_Manual_EN.pdf?v=1750753649).
- Hubitat's on-hub Groovy API: Thermostat capability, Zigbee read/write, bindings
  and updateFirmware; [official OTA driver example](https://github.com/hubitat/HubitatPublic/blob/master/examples/drivers/GenericZigbeeRGBWBulb.groovy).

- [W500 ZHA preset implementation](https://gist.github.com/ThomasLohner/39f14592e7ae123d07aef97f9b5fb184): independent cross-check that Manual is 255. Only the protocol value informed this correction; no code was copied.

Protocol references informed this implementation; no upstream driver is copied.
