# Hubitat Drivers

**Vibe-coded, human-tested, local-first** community drivers for [Hubitat Elevation](https://hubitat.com/).

This repository contains custom Hubitat drivers for devices that benefit from local control, without requiring vendor cloud services wherever supported. Drivers are published as readable Groovy source with [Hubitat Package Manager (HPM)](https://github.com/HubitatCommunity/hubitatpackagemanager) manifests.

> [!IMPORTANT]
> **AI-assisted development:** Much of this project, including code and documentation, has been developed with ChatGPT/AI assistance and then iteratively tested by a human maintainer using real devices and logs. AI-generated code can contain mistakes, incorrect assumptions or untested edge cases. **No independent security or code audit is claimed.** Support and verification vary by driver; consult each driver's README before installing.

## Drivers

| Driver | Protocol | Status | Documentation |
| --- | --- | --- | --- |
| [Aqara H2 EU Wall Outlet (WP-P01D)](drivers/aqara/h2-wall-outlet/) | Zigbee | **Preview v0.6.1** — tested on one Hubitat C-8 and one outlet; some features remain experimental | [Features, installation and known limitations](drivers/aqara/h2-wall-outlet/README.md) |
| [Aqara Smart Curtain Motor check-in fix](drivers/aqara/smart-curtain-motor/) | Zigbee | **Preview v1.0.3** — movement and 13-hour check-in trial on one lumi.curtain; GPL-3.0-or-later, HPM available | [Trial and rollback notes](drivers/aqara/smart-curtain-motor/README.md) |
| [Aqara H2 EU Wall Switch (WS-K08E)](drivers/aqara/h2-eu-wall-switch/) | Zigbee | **Preview v0.1.0** — core controls and advanced settings tested on one WS-K08E; loaded metering unverified | [Installation and validation](drivers/aqara/h2-eu-wall-switch/README.md) |

More devices may be added under `drivers/<manufacturer>/<device>/`.

## Installation

The recommended installation method is **Hubitat Package Manager**.

1. Install HPM on your Hubitat if you haven't already.
2. In HPM, add the following custom repository URL, then select the driver you want to install:

   ```text
   https://raw.githubusercontent.com/velkovb/hubitat-drivers/main/repository.json
   ```

3. Follow the *specific driver's README* for pairing, configuration and safety instructions.

You can also use HPM's **Install → From a URL** with an individual driver's `packageManifest.json`, linked from that driver's README.

## Repository structure

```text
hubitat-drivers/
├── README.md                   # Repository overview
├── LICENSE                     # Project license
├── THIRD_PARTY_NOTICES.md      # Upstream sources and acknowledgements
├── repository.json             # HPM repository index
└── drivers/
    └── aqara/
        └── h2-wall-outlet/
            ├── README.md       # Driver-specific documentation
            ├── aqara-h2-wall-outlet.groovy
            └── packageManifest.json
```

## Issues and contributions

Bug reports and pull requests are welcome. For driver problems, please include the device model and firmware (if known), Hubitat hub model and platform version, expected versus observed behavior, reproduction steps, and relevant **redacted** logs. Do not publish IP addresses, tokens or other identifying information.

Changes should be tested with a noncritical device before being recommended more broadly. For new drivers, include a driver-specific README, a package manifest and appropriate attribution.

## License, credits and disclaimer

The project's original code and documentation are published under the [MIT License](LICENSE). See [Third-party notices](THIRD_PARTY_NOTICES.md) for sources and attribution; upstream works retain their own licenses.

This is an **unofficial community project**, not affiliated with or endorsed by Hubitat, Aqara or other manufacturers. Driver software is provided **as is**. It must not be used as a safety interlock or a substitute for certified electrical protection. Read the relevant driver documentation for device-specific risks and limitations.
