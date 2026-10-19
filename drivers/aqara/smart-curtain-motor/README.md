# Aqara Smart Curtain Motor — check-in fix

Experimental local fork of Markus Liljergren's `v1.0.2.1123` driver, release `v1.0.3` (pilot `v1.0.2.1123-local.1`). AI-assisted modification, tested initially on one `lumi.curtain` motor with Hubitat C-8, platform 2.5.2.139, on 19 October 2026.

## Change

The original driver updates `lastCheckin` only for profile `0104`, cluster `000A`. Other incoming messages can update curtain position while presence stays stale and recovery polling continues.

The patch counts decoded incoming messages with a four-digit hexadecimal cluster or clusterId as contact. It retains the existing 60-minute check-in throttle, presence timeout, controls, and preferences. Refresh itself does not mark the device online; an incoming response must reach the parser. A radio response proves contact, not mechanical operation.

The separate driver name preserves the original for rollback. The import URL points to this fork. The package is available in this repository's HPM index.

## Hubitat Package Manager

Add the repository URL https://raw.githubusercontent.com/velkovb/hubitat-drivers/main/repository.json in HPM settings. For an existing local pilot, use **Match Up** to adopt the driver by its exact name and namespace, then **Repair** this package to install the published source without changing device assignments. For a new install, choose **Install > From a URL** and use https://raw.githubusercontent.com/velkovb/hubitat-drivers/main/drivers/aqara/smart-curtain-motor/packageManifest.json .

## Manual installation and rollback

1. Keep a hub backup and the original driver code.
2. Save `aqara-smart-curtain-motor.groovy` as a separate Drivers Code entry.
3. Select `Zigbee - Aqara Smart Curtain Motor (local check-in fix)` on one existing curtain device, save, then Refresh.
4. Verify a fresh lastCheckin, presence `present`, and eventual removal of the recoveryEvent job. Check logs for parser exceptions.
5. Test ordinary open/close/stop operation with someone present before broader rollout.

To roll back, select the original `Zigbee - Aqara Smart Curtain Motor` driver on the same device. Device identity and app references are retained. Do not re-pair or delete the device.

## Validation

- Hubitat compiled and installed the patched source successfully.
- On the living room pilot, a refresh response updated lastCheckin to 2026-10-19 09:05:17 Europe/Sofia and presence to `present`; the recoveryEvent job was absent on subsequent inspection.
- User confirmed normal curtain operation. After roughly 13 hours, retained event history showed regular check-ins through 21:22 Sofia, presence remained present, recovery polling remained stopped, and available logs contained no matching new errors or offline warnings. Offline detection, hub restarts and other motor models remain untested. Release 1.0.3 changes only version and import metadata relative to the tested pilot.
- The upstream fingerprints also include `lumi.curtain.hagl04`; this model has not been tested with the patch.

## Attribution and license

This file is derived from Markus Liljergren's driver, copyright 2020, licensed **GPL-3.0-or-later**. The repository's MIT license does not replace that license for this derived driver. Original notices remain in the source; see [COPYING](COPYING).

Original upstream: https://github.com/markus-li/Hubitat/blob/release/drivers/expanded/zigbee-aqara-smart-curtain-motor-expanded.groovy

The kkossev maintained fork was inspected for comparison; its healthStatus variant retained the same narrow check-in condition. This patch is based on the previously installed original driver, not the healthStatus fork.
