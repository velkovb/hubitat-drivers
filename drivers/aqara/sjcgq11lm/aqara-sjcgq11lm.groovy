/*
 * Aqara Water Leak Sensor SJCGQ11LM
 * Protocol reference: zigbee-herdsman-converters, src/devices/lumi.ts and src/lib/lumi.ts.
 */

metadata {
    definition(name: 'Aqara SJCGQ11LM Water Leak Sensor', namespace: 'velkovb',
            author: 'Borislav Velkov', singleThreaded: true) {
        capability 'Sensor'
        capability 'WaterSensor'
        capability 'Battery'
        capability 'HealthCheck'
        capability 'Initialize'
        attribute 'healthStatus', 'enum', ['unknown', 'online', 'offline']
        attribute 'lastCheckin', 'string'
        attribute 'lastWet', 'string'
        attribute 'lastDry', 'string'
        attribute 'batteryVoltage', 'number'
        attribute 'batteryLow', 'enum', ['true', 'false']
        attribute 'deviceTemperature', 'number'
        attribute 'powerOutageCount', 'number'
        attribute 'triggerCount', 'number'

        fingerprint profileId: '0104', endpointId: '01', inClusters: '0000,0003,0001',
            outClusters: '0019', manufacturer: 'LUMI', model: 'lumi.sensor_wleak.aq1',
            deviceJoinName: 'Aqara SJCGQ11LM Water Leak Sensor'
    }
    preferences {
        input name: 'logEnable', type: 'bool', title: 'Enable debug logging', defaultValue: false
    }
}

void installed() { initialize() }
void updated() { initialize() }

void initialize() {
    unschedule('checkHealth')
    checkHealth()
    runEvery5Minutes('checkHealth')
}

void ping() { checkHealth() }

void checkHealth() {
    if (state.lastReportAt == null) {
        publish('healthStatus', 'unknown')
        return
    }
    publish('healthStatus', now() - (state.lastReportAt as long) >= 10800000L ? 'offline' : 'online')
}

void parse(String description) {
    if (logEnable) log.debug description

    if (description.startsWith('zone status')) {
        def zone = description =~ /(?i)^zone status\s+0x([0-9a-f]{1,4})\b/
        if (zone.find()) publishZoneStatus(Integer.parseInt(zone.group(1), 16))
        return
    }

    // Read Basic report bytes before Hubitat converts the vendor TLV to text.
    def basic = description =~ /(?i)\bcluster:\s*0000,.*?\battrId:\s*([0-9a-f]{4}),\s*encoding:\s*([0-9a-f]{2}),.*?\bvalue:\s*([0-9a-f]+)\s*$/
    if (basic.find()) {
        recordCheckin()
        String attribute = basic.group(1).toUpperCase()
        String encoding = basic.group(2)
        String value = basic.group(3)
        if (attribute == 'FF01' && encoding in ['41', '42']) {
            decodeBasicTelemetry(value)
        } else if (attribute == '0005' && encoding == '42' && value.size() >= 2) {
            int nextAttribute = 2 + 2 * Integer.parseInt(value.substring(0, 2), 16)
            if (value.size() >= nextAttribute + 8 &&
                    value.substring(nextAttribute, nextAttribute + 6).equalsIgnoreCase('01FF42')) {
                decodeBasicTelemetry(value.substring(nextAttribute + 6))
            }
        }
        return
    }

    Map frame = zigbee.parseDescriptionAsMap(description)
    if (!frame) return

    if (frame.cluster == '0500' && frame.command == '00' && frame.data instanceof List && frame.data.size() >= 2) {
        String statusHex = frame.data[1] + frame.data[0]
        if (statusHex ==~ /(?i)[0-9a-f]{4}/) {
            publishZoneStatus(Integer.parseInt(statusHex, 16))
        }
    }
}

private void publishZoneStatus(int status) {
    recordCheckin()
    String water = (status & 0x01) != 0 ? 'wet' : 'dry'
    if (device.currentValue('water') != water) {
        publish('water', water)
        publish(water == 'wet' ? 'lastWet' : 'lastDry', formatTimestamp(state.lastReportAt as long))
    }
    publish('batteryLow', ((status & 0x08) != 0).toString())
}

private void decodeBasicTelemetry(String hex) {
    if (!(hex ==~ /(?i)([0-9a-f]{2})+/)) return
    List<Integer> bytes = hex.findAll(/../).collect { Integer.parseInt(it, 16) }
    // Some Aqara reports overstate the length. Decode only complete fields actually received.
    int end = Math.min(1 + bytes[0], bytes.size())
    int offset = 1
    while (offset + 2 <= end) {
        int tag = bytes[offset++]
        int type = bytes[offset++]
        int width = fieldWidth(type, bytes, offset)
        if (width < 0 || offset + width > end) return

        if (tag == 1 && type == 0x21) {
            int millivolts = bytes[offset] | (bytes[offset + 1] << 8)
            if (millivolts > 0 && millivolts < 0xFFFF) {
                publish('batteryVoltage', millivolts, 'mV')
                int percent = Math.round((millivolts - 2850) * 100.0f / 150)
                publish('battery', Math.max(0, Math.min(100, percent)), '%')
            }
        } else if (tag == 3 && type == 0x28 && bytes[offset] != 0x80) {
            publish('deviceTemperature', bytes[offset] >= 128 ? bytes[offset] - 256 : bytes[offset], '°C')
        } else if (tag == 5 && type == 0x21) {
            publish('powerOutageCount', (bytes[offset] | (bytes[offset + 1] << 8)) - 1)
        } else if (tag == 6 && type in [0x21, 0x22, 0x23, 0x24]) {
            publish('triggerCount', (bytes[offset] | (bytes[offset + 1] << 8)) - 1)
        }
        // Basic telemetry tag 100 can be stale. Wet and dry come from IAS only.
        offset += width
    }
}

private static int fieldWidth(int type, List<Integer> bytes, int offset) {
    if (type == 0x00) return 0
    if (type == 0x10 || type == 0x30) return 1
    if (type == 0x31 || type == 0x38) return 2
    if (type == 0x39) return 4
    if (type == 0x3A) return 8
    if (type in 0x08..0x0F || type in 0x18..0x2F) return (type & 7) + 1
    if (type in [0x41, 0x42] && offset < bytes.size()) {
        return bytes[offset] == 0xFF ? 1 : bytes[offset] + 1
    }
    return -1
}

private void recordCheckin() {
    state.lastReportAt = now()
    publish('lastCheckin', formatTimestamp(state.lastReportAt as long))
    publish('healthStatus', 'online')
}

private String formatTimestamp(long timestamp) {
    return new Date(timestamp).format('yyyy-MM-dd HH:mm:ss Z', location.timeZone ?: TimeZone.getTimeZone('UTC'))
}

private void publish(String name, Object value, String unit = null) {
    if (device.currentValue(name)?.toString() == value.toString()) return
    Map event = [name: name, value: value]
    if (unit) event.unit = unit
    sendEvent(event)
}
