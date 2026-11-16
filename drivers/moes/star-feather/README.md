# MOES Star Feather Zigbee Switch

Preview driver for MOES Star Feather SFL02-Z switches with one to four gangs. It connects directly to Hubitat over Zigbee. Supported manufacturer IDs are listed in the driver.

Install the Groovy source under **Drivers Code**, then select **MOES Star Feather Zigbee Switch** for the paired device. Run **Initialize** on the parent to create or reuse the gang switches and query the panel. When migrating from the community MOES driver, this driver uses the same child network IDs so existing children can be reused. Check automations after changing drivers.

- Each gang has an independent child switch labeled `<parent> - Gang N relay`. Initialize updates recognized default labels and preserves custom labels and existing device IDs. Parent On and Off control all relays.
- Set relay or scene mode per gang. Scene touches are parent button events numbered by gang.
- **Panel backlight**, **Relay indicator**, **Near-touch sensing**, **Vibration level**, and **Power restoration** are parent preferences. The published Zigbee definition has panel-wide lighting settings.
- **Set Countdown** starts a one-shot timer from 0 to 43,200 seconds. **Set Momentary** or the per-gang momentary preference sets the recurring auto-off timer from 0 to 3,600 seconds. Zero disables the timer.
- **Tuya time sync epoch** defaults to 1970, with options for 2000 and off.

The driver uses the [Zigbee2MQTT Star Feather datapoint definitions](https://github.com/Koenkk/zigbee-herdsman-converters/blob/master/src/devices/moes.ts). Physical LED placement depends on the panel. Verify touch, timers and lighting on your hardware before relying on them in automations.
