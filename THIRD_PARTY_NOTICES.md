# Sources, acknowledgements and third-party notices

The Aqara H2 outlet driver is an independently written **Hubitat Groovy** driver created with AI assistance and human hardware testing. The sources below helped identify Zigbee clusters, Aqara attributes, expected device features and Hubitat APIs. **The H2 outlet driver does not bundle source files, libraries, or verbatim implementations from these sources.** The separately listed curtain driver below is a GPL-licensed derivative. Zigbee cluster numbers, attribute IDs, protocols and observations are technical facts; this is not a claim of ownership of the Zigbee or Aqara specifications.

## Device protocol / manufacturer-specific behavior

1. **andreibsk — Custom ZHA quirk for Aqara Wall Outlet H2 EU** — https://gist.github.com/andreibsk/fa9d68a417430bb4d00ff592345866de (`aqara_wall_outlet_h2_eu.py`). Crucial independent device research describing manufacturer-specific attributes such as button lock, LED, charging cut-off, charging limit, overload limit, and power-on memory. **License status of this individual Gist: no explicit license identified.** It is linked and credited as a *technical reference only*; its Python implementation is not included or ported verbatim. Related proposed ZHA contribution: https://github.com/zigpy/zha-device-handlers/pull/3653 . The ZHA device-handlers repository is **Apache-2.0**, but that does not automatically establish the license for the separately published Gist.

2. **Zigbee2MQTT contributors / Koen Kanters and contributors** — https://www.zigbee2mqtt.io/devices/WP-P01D.html and https://github.com/Koenkk/zigbee-herdsman-converters . Useful device feature, attribute and reporting information. The zigbee-herdsman-converters source is licensed **MIT** (copyright in its upstream LICENSE); **no converter code is copied or distributed here**.

3. **SmartThings community contributors** — https://community.smartthings.com/t/st-edge-aqara-h2-wall-outlet-eu-wp-p01d-lumi-plug-aeu001-zigbee-driver/309304 . Community discussion of the device, endpoint behavior and additional functionality. Forum material is referenced, not copied or treated as generally open-source licensed.

4. **zigpy / ZHA device-handlers maintainers and contributors** — https://github.com/zigpy/zha-device-handlers and https://github.com/zigpy/zha-device-handlers/issues/3187 . Independent device-support work and issues, useful for comparing technical behavior; the project is **Apache-2.0**. None of its Python source is bundled.

## Hubitat integration and packaging references

5. **Hubitat developer documentation** — https://docs2.hubitat.com/en/developer/driver/building-a-zigbee-driver . Hubitat driver/API conventions; documentation is linked, not copied.

6. **Hubitat Package Manager maintainers (including Dominick Meglio and HubitatCommunity contributors)** — https://github.com/HubitatCommunity/hubitatpackagemanager and https://hubitatpackagemanager.hubitatcommunity.com/devs1.html . HPM manifest format and distribution workflow. The HPM application itself is **BSD-3-Clause**; this repository does not include HPM code.

## Licensing and scope

- **This repository's own original Groovy code and documentation:** MIT; see [`LICENSE`](LICENSE). File headers use SPDX-License-Identifier: MIT.
- **Third-party code bundled in this repository:** the Aqara smart curtain motor driver below, licensed GPL-3.0-or-later. The upstream software licenses above are acknowledged for transparency, not relicensed as part of this project. If code from any source is incorporated in the future, revisit compatibility and preserve all applicable licenses, copyright headers and any required NOTICE files.
- **Third-party trademarks:** Aqara, Hubitat, Zigbee, SmartThings, etc. belong to their owners. This is an unofficial community project, not endorsed by those companies.
- **Limitations:** This attribution review cannot certify that AI-generated code has no unintentional similarities to unrelated works. Please report any attribution or copyright concerns via GitHub Issues before reusing disputed material.


## Aqara smart curtain motor fork

The driver in drivers/aqara/smart-curtain-motor is derived from Markus Liljergren's Zigbee - Aqara Smart Curtain Motor v1.0.2.1123, copyright 2020 Markus Liljergren, GPL-3.0-or-later. Original upstream: https://github.com/markus-li/Hubitat/blob/release/drivers/expanded/zigbee-aqara-smart-curtain-motor-expanded.groovy . Original notices are retained; the local check-in modification is documented in that directory. See its COPYING for the GPL text. The repository's MIT license does not relicense this derived driver.

## Aqara H2 EU WS-K08E wall switch

The driver in `drivers/aqara/h2-eu-wall-switch` is independently implemented under
the repository MIT license. Protocol references: [Zigbee2MQTT WS-K08E](https://www.zigbee2mqtt.io/devices/WS-K08E.html),
[zigbee-herdsman-converters](https://github.com/Koenkk/zigbee-herdsman-converters) (MIT),
[ldeora's SmartThings H2 implementation](https://github.com/ldeora/SmartThingsEdgeDrivers/tree/main/aqara-h2-wall-switches),
and [Rylan Hazelton's Hubitat H2 US driver](https://github.com/krazik/hubitat-drivers/blob/main/AqaraH2Switch.groovy)
(source header: Apache-2.0, based on Preston Brown's work). These informed protocol
facts and platform conventions; their implementations and the Groovy test runtime
are not bundled. The EU driver uses its own endpoint mapping, parser, configuration
handling and tests; the US driver's hold/release mapping is not applicable here.


## Aqara W500 floor heating thermostat

The driver in `drivers/aqara/floor-heating-thermostat-w500` is independently
implemented under the repository MIT license. Protocol facts were checked against
[Zigbee2MQTT](https://www.zigbee2mqtt.io/devices/UT-A01E.html) and
[zigbee-herdsman-converters](https://github.com/Koenkk/zigbee-herdsman-converters)
(MIT), including thermostat/manufacturer attribute encodings, W500 metering scaling
and NTC readback quirks. Hubitat API conventions were checked against its on-hub
documentation and [official examples](https://github.com/hubitat/HubitatPublic).
No upstream driver implementation or Groovy runtime is bundled.


The Manual preset encoding was additionally cross-checked against
[Thomas Lohner's W500 ZHA quirk](https://gist.github.com/ThomasLohner/39f14592e7ae123d07aef97f9b5fb184).
Only a protocol constant informed this correction; no implementation was copied.
