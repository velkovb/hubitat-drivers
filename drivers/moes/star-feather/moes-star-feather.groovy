/*
 * SPDX-License-Identifier: MIT
 * MOES Star Feather SFL02-Z local Zigbee driver
 * Tuya datapoint reference: https://github.com/Koenkk/zigbee-herdsman-converters/blob/master/src/devices/moes.ts
 */
import groovy.transform.Field
import hubitat.device.HubMultiAction
import hubitat.device.Protocol

@Field static final int TUYA = 0xEF00
@Field static final Map MODELS = [
    '_TZE200_stvgmdjz': 1, '_TZE284_stvgmdjz': 1, '_TZE200_z3u99qxt': 1,
    '_TZE200_uenof8jd': 2, '_TZE284_uenof8jd': 2, '_TZE200_tzyy0rtq': 2,
    '_TZE200_wv9ukqca': 3, '_TZE200_zo0cfekv': 3,
    '_TZE200_dq8bu0pt': 4, '_TZE200_9dhenr94': 4
]
@Field static final Map PANEL = [
    backlightMode:   [dp: 36,  type: 1, title: 'Panel backlight', options: ['off', 'on']],
    indicatorMode:   [dp: 37,  type: 4, title: 'Relay indicator', options: ['off', 'lit when on', 'lit when off']],
    powerOnBehavior: [dp: 38,  type: 4, title: 'Power restoration', options: ['off', 'on', 'previous']],
    inductionMode:   [dp: 103, type: 1, title: 'Near-touch sensing', options: ['off', 'on']],
    vibrationMode:   [dp: 104, type: 4, title: 'Vibration level', options: ['Gear 0', 'Gear 1', 'Gear 2', 'Gear 3']]
]

metadata {
    definition(name: 'MOES Star Feather Zigbee Switch', namespace: 'velkovb', author: 'velkovb') {
        capability 'Switch'
        capability 'PushableButton'
        capability 'Configuration'
        capability 'Initialize'
        capability 'Refresh'
        command 'setCountdown', [[name: 'Gang*', type: 'NUMBER'], [name: 'Seconds (0–43200)*', type: 'NUMBER']]
        command 'setMomentary', [[name: 'Gang*', type: 'NUMBER'], [name: 'Seconds (0–3600)*', type: 'NUMBER']]
        PANEL.each { name, spec -> attribute name, 'enum', spec.options }
        (1..4).each { gang ->
            attribute "modeL${gang}", 'enum', ['switch', 'scene']
            attribute "countdownL${gang}", 'number'
            attribute "momentaryL${gang}", 'number'
        }
        MODELS.each { manufacturer, gangs ->
            fingerprint profileId: '0104', endpointId: '01', model: 'TS0601',
                manufacturer: manufacturer, inClusters: '0004,0005,EF00,0000',
                outClusters: '0019,000A', deviceJoinName: "MOES Star Feather ${gangs} Gang"
        }
    }
    preferences {
        PANEL.each { name, spec ->
            input name: name, type: 'enum', title: spec.title, options: spec.options
        }
        (1..gangCount()).each { gang ->
            input name: "modeL${gang}", type: 'enum', title: "Gang ${gang} touch mode", options: ['switch', 'scene']
            input name: "momentaryL${gang}", type: 'number', title: "Gang ${gang} momentary timer (seconds, 0 disables)", range: '0..3600'
        }
        input name: 'timeStart', type: 'enum', title: 'Tuya time sync epoch', options: ['1970', '2000', 'off'], defaultValue: '1970'
        input name: 'debugLogging', type: 'bool', title: 'Debug logging', defaultValue: false
    }
}

void installed() { initialize() }
void configure() { initialize() }

void initialize() {
    if (!gangCount()) {
        log.warn "Unsupported Star Feather fingerprint: ${device.getDataValue('model')} / ${device.getDataValue('manufacturer')}"
        return
    }
    (1..gangCount()).each { gang ->
        String relayName = "Gang ${gang} relay"
        String label = "${device.displayName} - ${relayName}"
        def child = getChildDevice(childDni(gang))
        if (!child) {
            addChildDevice('hubitat', 'Generic Component Switch', childDni(gang),
                [name: relayName, label: label, isComponent: true])
        } else if (child.label in ["${device.displayName} EP" + String.format('%02d', gang),
                                   "${device.displayName} - Gang ${gang}"]) {
            child.setLabel(label)
        }
    }
    sendEvent(name: 'numberOfButtons', value: gangCount())
    refresh()
}

void updated() {
    if (!gangCount()) return
    Map applied = state.appliedPreferences ?: [:]
    Map changes = [:]
    List<String> commands = []
    PANEL.each { name, spec ->
        int value = spec.options.indexOf(settings[name])
        if (value >= 0 && settings[name] != applied[name]) {
            commands.addAll(writeDp(spec.dp, spec.type, value))
            changes[name] = settings[name]
        }
    }
    (1..gangCount()).each { gang ->
        String mode = "modeL${gang}"
        int modeValue = ['switch', 'scene'].indexOf(settings[mode])
        if (modeValue >= 0 && settings[mode] != applied[mode]) {
            commands.addAll(writeDp(17 + gang, 4, modeValue))
            changes[mode] = settings[mode]
        }
        String timer = "momentaryL${gang}"
        Integer seconds = boundedInteger(settings[timer], 0, 3600)
        if (seconds != null && seconds != applied[timer]) {
            commands.addAll(writeDp(104 + gang, 2, seconds))
            changes[timer] = seconds
        }
    }
    dispatch(commands)
    state.appliedPreferences = applied + changes
}

void refresh() {
    if (gangCount()) dispatch(zigbee.command(TUYA, 0x03, [destEndpoint: 1]))
}

void on() { setAllRelays(1) }
void off() { setAllRelays(0) }
void componentOn(cd) { setRelay(cd, 1) }
void componentOff(cd) { setRelay(cd, 0) }
void componentRefresh(cd) { refresh() }

void setCountdown(Number gang, Number seconds) { setTimer(gang, seconds, 29, 43200) }
void setMomentary(Number gang, Number seconds) { setTimer(gang, seconds, 104, 3600) }

private void setTimer(Number gang, Number seconds, int base, int maxSeconds) {
    Integer channel = boundedInteger(gang, 1, gangCount())
    Integer duration = boundedInteger(seconds, 0, maxSeconds)
    if (channel == null || duration == null) {
        log.warn "Expected a gang from 1 to ${gangCount()} and whole seconds from 0 to ${maxSeconds}"
        return
    }
    dispatch(writeDp(base + channel, 2, duration))
}

private static Integer boundedInteger(Object value, int low, int high) {
    if (value == null || !(value.toString() ==~ /[0-9]+/) || value.toString().size() > 9) return null
    int number = value.toString().toInteger()
    number >= low && number <= high ? number : null
}

private void setAllRelays(int value) {
    if (gangCount()) dispatch((1..gangCount()).collectMany { gang -> writeDp(23 + gang, 1, value) })
}

private void setRelay(cd, int value) {
    Integer gang = (1..gangCount()).find { childDni(it) == cd.deviceNetworkId }
    if (gangCount() && gang != null) dispatch(writeDp(23 + gang, 1, value))
}

private int gangCount() {
    (device?.getDataValue('model') == 'TS0601' ? MODELS[device.getDataValue('manufacturer')] : null) ?: 0
}

private String childDni(int gang) { String.format('%s-%02d', device.id, gang) }

private List<String> writeDp(int dp, int type, int value) {
    int length = type == 2 ? 4 : 1
    // The first byte is the Tuya transaction status, followed by its sequence byte.
    String payload = String.format('00%02X%02X%02X%04X', nextSequence(), dp, type, length)
    payload += length == 4 ? String.format('%08X', value) : String.format('%02X', value)
    zigbee.command(TUYA, 0x00, [destEndpoint: 1], 200, payload)
}

private int nextSequence() {
    int sequence = ((state.sequence ?: 0) + 1) & 0xFF
    state.sequence = sequence
    sequence
}

private void dispatch(List<String> commands) {
    if (commands) sendHubCommand(new HubMultiAction(commands, Protocol.ZIGBEE))
}

void parse(String description) {
    if (!gangCount() || !description?.startsWith('catchall:')) return
    Map frame = zigbee.parseDescriptionAsMap(description)
    if (frame.clusterInt != TUYA || frame.isClusterSpecific == false) return
    if (frame.command == '24') {
        syncTime()
        return
    }
    if (!(frame.command in ['01', '02', '06'])) return

    List data = frame.data
    if (!data || data.size() < 2 || !data.every { it ==~ /(?i)[0-9a-f]{2}/ }) return
    List<Map> records = []
    int offset = 2
    while (offset < data.size()) {
        if (offset + 4 > data.size()) return
        int dp = Integer.parseInt(data[offset], 16)
        int type = Integer.parseInt(data[offset + 1], 16)
        int length = Integer.parseInt(data[offset + 2] + data[offset + 3], 16)
        offset += 4
        if (offset + length > data.size()) return
        List bytes = data.subList(offset, offset + length)
        records.add([dp: dp, type: type, bytes: bytes])
        offset += length
    }

    Map relays = new LinkedHashMap(state.relays ?: [:])
    records.each { record -> applyReport(record, relays) }
    if (relays) {
        state.relays = relays
        if (relays.values().contains('on')) sendEvent(name: 'switch', value: 'on')
        else if (relays.size() == gangCount()) sendEvent(name: 'switch', value: 'off')
    }
}

private void applyReport(Map record, Map relays) {
    int dp = record.dp
    List bytes = record.bytes
    int count = gangCount()
    if (settings.debugLogging) log.debug "Star Feather DP ${dp}, type ${record.type}, data ${bytes.join(' ')}"
    if (dp >= 1 && dp <= count) {
        push(dp)
        return
    }
    if (!(bytes.size() in [1, 4])) return
    long value = Long.parseLong(bytes.join(''), 16)
    if (dp >= 24 && dp < 24 + count && record.type == 1 && bytes.size() == 1 && value <= 1) {
        int gang = dp - 23
        String status = value == 1 ? 'on' : 'off'
        relays[gang.toString()] = status
        getChildDevice(childDni(gang))?.parse([[name: 'switch', value: status]])
        return
    }
    if (dp >= 18 && dp < 18 + count && record.type == 4 && bytes.size() == 1 && value <= 1) {
        sendEvent(name: "modeL${dp - 17}", value: value == 1 ? 'scene' : 'switch')
        return
    }
    if (record.type == 2 && bytes.size() == 4) {
        if (dp >= 30 && dp < 30 + count) sendEvent(name: "countdownL${dp - 29}", value: value, unit: 's')
        if (dp >= 105 && dp < 105 + count) sendEvent(name: "momentaryL${dp - 104}", value: value, unit: 's')
        return
    }
    def setting = PANEL.find { name, spec -> spec.dp == dp }
    if (setting && record.type == setting.value.type && bytes.size() == 1 && value < setting.value.options.size()) {
        sendEvent(name: setting.key, value: setting.value.options[value as int])
    }
}

void push(Number button) {
    Integer gang = boundedInteger(button, 1, gangCount())
    if (gang != null) sendEvent(name: 'pushed', value: gang, isStateChange: true)
}

private void syncTime() {
    String epoch = settings.timeStart ?: '1970'
    if (epoch == 'off') return
    long millis = now()
    long utc = millis.intdiv(1000) - (epoch == '2000' ? 946684800L : 0L)
    long local = utc + (location.timeZone?.getOffset(millis) ?: 0).intdiv(1000)
    String payload = String.format('0008%08X%08X', utc, local)
    dispatch(zigbee.command(TUYA, 0x24, [destEndpoint: 1], 200, payload))
}
