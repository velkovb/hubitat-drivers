# Zemismart ZMS-206EU-2 Screen Switch for Hubitat

Preview v0.2.0 for the two-gang Zemismart ZMS-206EU-2, including the ZMS-206EU-2-B.
The driver uses Hubitat's Zigbee radio and creates a child switch for each wired
relay. Use the child devices for independent control in automations.

## Install

1. In Hubitat Package Manager, install **Zemismart ZMS-206EU-2 Screen Switch**
   [from the package manifest](https://raw.githubusercontent.com/velkovb/hubitat-drivers/main/drivers/zemismart/zms-206eu-2/packageManifest.json).
   For manual installation, import the [raw Groovy driver](https://raw.githubusercontent.com/velkovb/hubitat-drivers/main/drivers/zemismart/zms-206eu-2/zemismart-zms-206eu-2.groovy)
   under **Drivers Code**.
2. Start Zigbee pairing in Hubitat, then hold a panel button until the indicator
   cycles through colors. If already paired, select **Zemismart ZMS-206EU-2 Screen Switch**
   as the device's **Type** and save.
3. Run **Configure** on the parent. Verify that both child switches appear,
   then test on/off commands and state updates from physical taps.

## Panel settings

Use the parent device's **Commands** for screen names, backlight mode and
brightness, on/off and countdown indicator colors, indicator mode, radar wake
timeout, child lock, per-relay countdowns, and per-relay power-on behavior.
Screen names use UTF-8 and accept up to 12 UTF-16 code units. Hubitat child
labels and the text displayed on the wall are set separately.

Radar timeout offers `none`, `10s`, `20s`, `30s`, `45s` and `60s`.
The meaning of `none` on the display is unverified. The countdown commands
address the wired relays. The driver exposes no power or energy meter.

## Validation and scope

Tested on one ZMS-206EU-2-B with Zigbee fingerprint
`TS0601 / _TZE28C1000000_dmckrsxg`. Both relays and state updates worked.
The panel reported ASCII and Cyrillic screen names and acknowledged a 60-second
radar timeout. Other settings and the remaining fingerprints have not been
fully tested on hardware. The driver recognizes
`_TZE204_3ctwoaip`, `_TZE284_3ctwoaip`, and `_TZE284_dmckrsxg` from the
same Zigbee2MQTT model definition.

Local Groovy tests exercise command encoding, dropdown definitions, report
handling, and time synchronization. They use stubs at the Hubitat API boundary.

```sh
java -Dfile.encoding=UTF-8 -cp /path/to/groovy-all-2.4.21.jar groovy.ui.GroovyMain drivers/zemismart/zms-206eu-2/test-driver.groovy drivers/zemismart/zms-206eu-2/zemismart-zms-206eu-2.groovy
```

Independently implemented under the repository MIT license. The Tuya datapoint
mapping and wire format are based on
[Zigbee2MQTT's device converter](https://github.com/Koenkk/zigbee-herdsman-converters/blob/master/src/devices/zemismart.ts)
and [zigbee-herdsman](https://github.com/Koenkk/zigbee-herdsman).
