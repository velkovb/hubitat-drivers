/*
 * Zemismart ZMS-206EU-2 for Hubitat. SPDX-License-Identifier: MIT
 * Independently implemented from the Zigbee2MQTT converter and wire format.
 * https://github.com/Koenkk/zigbee-herdsman-converters/blob/master/src/devices/zemismart.ts
 */
import groovy.transform.Field

@Field static final String VERSION = '0.2.0'
@Field static final int TUYA = 0xEF00
@Field static final List COLORS = ['red','blue','green','white','yellow','magenta','cyan','warm_white','warm_yellow']
@Field static final List RESTORE = ['power_off','power_on','restart_memory']
@Field final Map DATAPOINTS = [
    1: [name:'relay1', type:1, values:['off','on']],
    2: [name:'relay2', type:1, values:['off','on']],
    7: [name:'countdown1', type:2, max:43200, title:'Relay 1 countdown (seconds)'],
    8: [name:'countdown2', type:2, max:43200, title:'Relay 2 countdown (seconds)'],
    13:[name:'switch', type:1, values:['off','on']],
    15:[name:'indicatorStatus', type:4, values:['off','on_off_status','switch_position'], title:'Indicator mode'],
    16:[name:'backlightMode', type:1, values:['off','on'], title:'Backlight mode'],
    19:[name:'delayOffColor', type:4, values:COLORS, title:'Color during countdown'],
    29:[name:'powerOnBehavior1', type:4, values:RESTORE, title:'Relay 1 behavior after power loss'],
    30:[name:'powerOnBehavior2', type:4, values:RESTORE, title:'Relay 2 behavior after power loss'],
    101:[name:'childLock', type:1, values:['unlock','lock'], title:'Child lock'],
    102:[name:'backlightBrightness', type:2, max:100, title:'Backlight brightness (%)'],
    103:[name:'switchColorOn', type:4, values:COLORS, title:'Color when on'],
    104:[name:'switchColorOff', type:4, values:COLORS, title:'Color when off'],
    105:[name:'screenName1', type:0, title:'Screen name for Relay 1 (up to 12 characters)'],
    106:[name:'screenName2', type:0, title:'Screen name for Relay 2 (up to 12 characters)'],
    111:[name:'radarTimeout', type:4, values:['none','10s','20s','30s','45s','60s'], title:'Radar wake timeout']
]

metadata {
    definition(name:'Zemismart ZMS-206EU-2 Screen Switch', namespace:'velkovb',
               author:'Borislav Velkov', singleThreaded:true,
               importUrl:'https://raw.githubusercontent.com/velkovb/hubitat-drivers/main/drivers/zemismart/zms-206eu-2/zemismart-zms-206eu-2.groovy') {
        capability 'Configuration'
        capability 'Refresh'
        capability 'Switch'
        List<Map> options = DATAPOINTS.values().findAll { it.title }
        command 'setOption', [[name:'Option*', type:'ENUM', constraints:options.collect { it.name }],
                              [name:'Value', type:'STRING']]
        attribute 'driverVersion', 'string'
        attribute 'mcuVersion', 'string'
        attribute 'relay1', 'enum', ['off','on']
        attribute 'relay2', 'enum', ['off','on']
        options.each { spec ->
            attribute spec.name, spec.type == 2 ? 'number' : 'string'
            Map argument = [name:spec.title, type:'STRING']
            if (spec.values) {
                argument += [type:'ENUM', constraints:spec.values]
            } else if (spec.type == 2) {
                argument = [name:"${spec.title}, 0 to ${spec.max}".toString(), type:'NUMBER']
            }
            command "set${spec.name.capitalize()}".toString(), [argument]
        }
        fingerprint profileId:'0104', endpointId:'01', model:'TS0601', manufacturer:'_TZE204_3ctwoaip', deviceJoinName:'Zemismart ZMS-206EU-2'
        fingerprint profileId:'0104', endpointId:'01', model:'TS0601', manufacturer:'_TZE284_3ctwoaip', deviceJoinName:'Zemismart ZMS-206EU-2'
        fingerprint profileId:'0104', endpointId:'01', model:'TS0601', manufacturer:'_TZE284_dmckrsxg', deviceJoinName:'Zemismart ZMS-206EU-2'
        fingerprint profileId:'0104', endpointId:'01', model:'TS0601', manufacturer:'_TZE28C1000000_dmckrsxg', deviceJoinName:'Zemismart ZMS-206EU-2'
    }
    preferences {
        input name:'debugLogging', type:'bool', title:'Debug logging (30 minutes)', defaultValue:false
        input name:'timeEpoch', type:'enum', title:'Tuya clock sync', options:['1970':'Unix (1970)','2000':'2000','off':'Off'], defaultValue:'1970'
    }
}

void installed() { initialize() }
void updated() { initialize() }
void initialize() {
    sendEvent(name:'driverVersion', value:VERSION)
    [1, 2].each { gang ->
        String dni = childDni(gang)
        if (!getChildDevice(dni)) {
            addChildDevice('hubitat', 'Generic Component Switch', dni,
                [name:"Relay ${gang}".toString(), label:"${device.displayName} - Relay ${gang}".toString(), isComponent:true])
        }
    }
    unschedule('logsOff')
    if (settings.debugLogging) runIn(1800, 'logsOff')
}

void logsOff() { device.updateSetting('debugLogging', [value:'false', type:'bool']) }

void configure() {
    initialize()
    // The converter's Tuya initialization reads these Basic attributes.
    List<String> commands = zigbee.readAttribute(0x0000, [0x0004, 0x0000, 0x0001, 0x0005, 0x0007, 0xFFFE], [destEndpoint:1], 300)
    transmit(commands + zigbee.command(TUYA, 0x03, [destEndpoint:1], 200))
}

void refresh() { sendTuya(0x03) }
void on() { sendDatapoint(13, 1, '01') }
void off() { sendDatapoint(13, 1, '00') }

String childDni(int gang) { "${device.deviceNetworkId}-relay${gang}" }
void componentOn(child) { setRelay(child, '01') }
void componentOff(child) { setRelay(child, '00') }
void componentRefresh(child) { refresh() }

void setRelay(child, String value) {
    Integer gang = [1, 2].find { child.deviceNetworkId == childDni(it) }
    if (gang == null) {
        log.warn 'Unknown relay child'
        return
    }
    sendDatapoint(gang, 1, value)
}

void setIndicatorStatus(String value) { setOption('indicatorStatus', value) }
void setBacklightMode(String value) { setOption('backlightMode', value) }
void setDelayOffColor(String value) { setOption('delayOffColor', value) }
void setPowerOnBehavior1(String value) { setOption('powerOnBehavior1', value) }
void setPowerOnBehavior2(String value) { setOption('powerOnBehavior2', value) }
void setChildLock(String value) { setOption('childLock', value) }
void setSwitchColorOn(String value) { setOption('switchColorOn', value) }
void setSwitchColorOff(String value) { setOption('switchColorOff', value) }
void setRadarTimeout(String value) { setOption('radarTimeout', value) }
void setScreenName1(String value = '') { setOption('screenName1', value) }
void setScreenName2(String value = '') { setOption('screenName2', value) }
void setCountdown1(BigDecimal value) { setOption('countdown1', value?.stripTrailingZeros()?.toPlainString()) }
void setCountdown2(BigDecimal value) { setOption('countdown2', value?.stripTrailingZeros()?.toPlainString()) }
void setBacklightBrightness(BigDecimal value) { setOption('backlightBrightness', value?.stripTrailingZeros()?.toPlainString()) }

void setOption(String option, String value = '') {
    def entry = DATAPOINTS.find { id, spec -> spec.title && spec.name == option }
    if (entry == null) {
        log.warn "Unknown option ${option}"
        return
    }
    Map spec = entry.value
    String input = value?.trim()?.toLowerCase(Locale.ROOT) ?: ''
    String payload
    if (spec.type == 0) {
        String name = value ?: ''
        int end = Math.min(name.length(), 12)
        if (end < name.length() && Character.isHighSurrogate(name.charAt(end - 1))) end--
        payload = name.substring(0, end).getBytes('UTF-8').encodeHex().toString()
    } else if (spec.type == 2) {
        if (!(input ==~ /[0-9]{1,5}/) || input.toInteger() > spec.max) {
            log.warn "${option} requires an integer from 0 to ${spec.max}"
            return
        }
        payload = hex(input.toInteger(), 8)
    } else {
        int index = spec.values.indexOf(input)
        if (index < 0) {
            log.warn "${option} accepts ${spec.values.join(', ')}"
            return
        }
        payload = hex(index, 2)
    }
    sendDatapoint(entry.key as int, spec.type as int, payload)
}

void sendDatapoint(int id, int type, String payload) {
    // Tuya transaction 1 is little endian. DP length and numeric payload are big endian.
    sendTuya(0x00, '0100' + hex(id, 2) + hex(type, 2) + hex(payload.length().intdiv(2), 4) + payload)
}

void sendTuya(int command, String payload = '') {
    Map endpoint = [destEndpoint:1]
    transmit(payload ? zigbee.command(TUYA, command, endpoint, 100, payload) : zigbee.command(TUYA, command, endpoint, 100))
}

void transmit(List<String> commands) {
    sendHubCommand(new hubitat.device.HubMultiAction(commands, hubitat.device.Protocol.ZIGBEE))
}

String hex(long value, int width) { String.format("%0${width}X".toString(), value) }

void parse(String description) {
    if (settings.debugLogging) log.debug description
    if (description.startsWith('catchall:')) parseMap(zigbee.parseDescriptionAsMap(description))
}

void parseMap(Map frame) {
    if (frame.clusterInt != TUYA && !'EF00'.equalsIgnoreCase(frame.clusterId?.toString())) return
    List data = frame.data ?: []
    switch (frame.command?.toUpperCase(Locale.ROOT)) {
        case ['01','02','05','06','0A']:
            parseDatapoints(data)
            break
        case '11':
            if (data.size() >= 3) {
                int version = Integer.parseInt(data[2], 16)
                sendEvent(name:'mcuVersion', value:"${version >> 6}.${(version >> 4) & 3}.${version & 15}")
            }
            break
        case '24':
            String epoch = settings.timeEpoch ?: '1970'
            if (epoch != 'off') {
                long millis = now()
                long utc = Math.round(millis / 1000.0d) - (epoch == '2000' ? 946684800L : 0L)
                long local = utc + (location.timeZone?.getOffset(millis) ?: 0).intdiv(1000)
                sendTuya(0x24, '0800' + hex(utc, 8) + hex(local, 8))
            }
            break
        case '25':
            sendTuya(0x25, '010001')
            break
    }
}

void parseDatapoints(List data) {
    int offset = 2 // The first two bytes are the Tuya transaction sequence.
    while (offset + 4 <= data.size()) {
        int id = Integer.parseInt(data[offset], 16)
        int type = Integer.parseInt(data[offset + 1], 16)
        int length = Integer.parseInt(data[offset + 2] + data[offset + 3], 16)
        int start = offset + 4
        int end = start + length
        if (end > data.size()) return
        decodeDatapoint(id, type, data.subList(start, end).join(''))
        offset = end
    }
}

void decodeDatapoint(int id, int type, String payload) {
    Map spec = DATAPOINTS[id]
    if (spec == null || type != spec.type) return
    def value
    switch (type) {
        case 0:
            value = new String(payload.decodeHex(), 'UTF-8')
            break
        case 2:
            if (payload.length() != 8) return
            value = Long.parseLong(payload, 16)
            break
        default:
            if (payload.length() != 2) return
            int index = Integer.parseInt(payload, 16)
            if (index >= spec.values.size()) return
            value = spec.values[index]
    }
    sendEvent(name:spec.name, value:value)
    if (id in [1, 2]) getChildDevice(childDni(id))?.parse([[name:'switch', value:value]])
}
