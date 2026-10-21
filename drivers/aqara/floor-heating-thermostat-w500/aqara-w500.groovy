/*
 * Aqara Floor Heating Thermostat W500 (UT-A01E), v0.1.1
 * Copyright 2026 Borislav Velkov. SPDX-License-Identifier: MIT
 * Independent implementation; protocol references and limitations in README.md.
 */
import groovy.transform.Field

@Field static final String VERSION = '0.1.1'
@Field static final Map OPTIONS = [
    preset:[attr:0x0311, type:0x20, values:[home:1, away:2, sleep:3, vacation:5, evening:6, manual:255]],
    sensorSource:[attr:0x0280, type:0x20, values:[internal:0, external:1, ntc:2]],
    windowDetection:[attr:0x0273, type:0x20, values:[off:0, on:1]],
    childLock:[attr:0x0277, type:0x20, values:[unlocked:0, locked:1]],
    powerOutageMemory:[attr:0x0201, type:0x10, values:[off:0, on:1]]
]

metadata {
    definition(name:'Aqara Floor Heating Thermostat W500', namespace:'velkovb', author:'Borislav Velkov', singleThreaded:true,
        importUrl:'https://raw.githubusercontent.com/velkovb/hubitat-drivers/main/drivers/aqara/floor-heating-thermostat-w500/aqara-w500.groovy') {
        capability 'Actuator'
        capability 'Configuration'
        capability 'Refresh'
        capability 'TemperatureMeasurement'
        capability 'RelativeHumidityMeasurement'
        capability 'Thermostat'
        capability 'PowerMeter'
        capability 'EnergyMeter'
        attribute 'driverVersion', 'string'
        attribute 'firmwareVersion', 'string'
        attribute 'lastSeen', 'string'
        attribute 'deviceWorkingState', 'string'
        attribute 'presetRaw', 'number'
        attribute 'temperatureCalibration', 'number'
        attribute 'hysteresis', 'number'
        attribute 'minHeatSetpointLimit', 'number'
        attribute 'maxHeatSetpointLimit', 'number'
        attribute 'temperatureSetpointHold', 'string'
        attribute 'temperatureSetpointHoldDuration', 'number'
        attribute 'ntcR25', 'number'
        attribute 'ntcBeta', 'number'
        OPTIONS.each { key, spec -> attribute key, 'string' }
        command 'setPreset', [[name:'Preset',type:'ENUM',constraints:['home','away','sleep','vacation','evening','manual']]]
        command 'setSensorSource', [[name:'Sensor source',type:'ENUM',constraints:['internal','external','ntc']]]
        command 'setHysteresis', [[name:'Temperature swing (C, 0 to 3, step 0.5)',type:'NUMBER']]
        command 'setTemperatureCalibration', [[name:'Offset (C, -2.5 to 2.5, step 0.1)',type:'NUMBER']]
        command 'setSetpointHold', [[name:'Hold',type:'ENUM',constraints:['on','off']]]
        command 'setSetpointHoldDuration', [[name:'Minutes (0 to 65535; 65535 = forever)',type:'NUMBER']]
        command 'identify'
        command 'updateFirmware'
        fingerprint profileId:'0104', manufacturer:'Aqara', model:'lumi.airrtc.aeu001', deviceJoinName:'Aqara Floor Heating Thermostat W500'
        fingerprint profileId:'0104', manufacturer:'LUMI', model:'lumi.airrtc.aeu001', deviceJoinName:'Aqara Floor Heating Thermostat W500'
    }
    preferences {
        input name:'debugLogging', type:'bool', title:'Debug logging (30 minutes)', defaultValue:false
        input name:'pollEnabled', type:'bool', title:'Refresh measurements every 15 minutes', defaultValue:true
        [windowDetection:'Window detection', childLock:'Child lock', powerOutageMemory:'Restore state after a power outage'].each { key,title ->
            input name:key, type:'enum', title:title, options:([keep:'Keep device setting'] + OPTIONS[key].values.collectEntries { k,v -> [(k):k.capitalize()] }), defaultValue:'keep'
        }
    }
}

void installed() { initialize() }
void updated() {
    initialize()
    transmit(preferenceCommands())
}
void initialize() {
    sendEvent(name:'driverVersion', value:VERSION)
    sendEvent(name:'supportedThermostatModes', value:'["off","heat"]')
    sendEvent(name:'supportedThermostatFanModes', value:'[]')
    unschedule('poll')
    if (settings.pollEnabled != false) runEvery15Minutes('poll')
    unschedule('logsOff')
    if (settings.debugLogging == true) runIn(1800,'logsOff')
}
void logsOff() { device.updateSetting('debugLogging',[value:'false',type:'bool']) }
void transmit(List<String> commands) {
    if (commands) sendHubCommand(new hubitat.device.HubMultiAction(commands, hubitat.device.Protocol.ZIGBEE))
}
Map params(Integer cluster) { cluster == 0xFCC0 ? [destEndpoint:1,mfgCode:0x115F] : [destEndpoint:1] }
List<String> readAttr(Integer cluster, Object attrs) { zigbee.readAttribute(cluster,attrs,params(cluster),200) }
List<String> writeAttr(Integer cluster, Integer attr, Integer type, Integer value) {
    zigbee.writeAttribute(cluster,attr,type,value,params(cluster),300) + readAttr(cluster,attr)
}
List<String> preferenceCommands() {
    List<String> commands = []
    ['windowDetection','childLock','powerOutageMemory'].each { key ->
        String value = settings[key]?.toString()
        if (value && value != 'keep') commands += optionCommands(key,value)
    }
    commands
}
List<String> optionCommands(String key, String value) {
    Map spec = OPTIONS[key]
    if (!spec || !spec.values.containsKey(value)) throw new IllegalArgumentException("Invalid ${key}: ${value}")
    writeAttr(0xFCC0,spec.attr,spec.type,spec.values[value])
}
List<String> configure() {
    initialize()
    List<String> commands = []
    // Bind for device-initiated reports. Do not overwrite thermostat/sensor/NTC settings.
    [0x0201,0x0405,0x0B04,0x0702,0xFCC0].each { cluster ->
        commands << "zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0x${String.format('%04X',cluster)} {${device.zigbeeId}} {}"
        commands << 'delay 200'
    }
    commands + preferenceCommands() + refresh()
}
List<String> measurementReads() {
    readAttr(0x0201,[0x0000,0x0012,0x001C,0x0029]) + readAttr(0x0405,0x0000) +
        readAttr(0x0B04,0x050B) + readAttr(0x0702,0x0000)
}
void poll() { transmit(measurementReads()) }
List<String> refresh() {
    measurementReads() + readAttr(0x0000,[0x0001,0x0004,0x0005,0x4000]) +
        readAttr(0x0201,[0x0010,0x0015,0x0016,0x0023,0x0024]) +
        readAttr(0xFCC0,OPTIONS.values().collect { it.attr } + [0x0310,0x030C,0x0315,0x0316])
}
List<String> heat() { setThermostatMode('heat') }
List<String> off() { setThermostatMode('off') }
List<String> setThermostatMode(String mode) {
    if (!(mode in ['off','heat'])) { log.warn 'W500 supports only off and heat'; return [] }
    writeAttr(0x0201,0x001C,0x30,mode == 'heat' ? 4 : 0)
}
List<String> setHeatingSetpoint(Object value) {
    BigDecimal c = new BigDecimal(value.toString())
    if (getTemperatureScale() == 'F') c = (c - 32G) * 5G / 9G
    c = (c * 2G).setScale(0,BigDecimal.ROUND_HALF_UP) / 2G
    BigDecimal lo = state.minHeatC != null ? state.minHeatC as BigDecimal : 5G
    BigDecimal hi = state.maxHeatC != null ? state.maxHeatC as BigDecimal : 40G
    if (c < lo || c > hi || c < 5G || c > 40G) throw new IllegalArgumentException("Setpoint must be within device limits (${lo} to ${hi} C)")
    writeAttr(0x0201,0x0012,0x29,(c * 100G).intValue())
}
List<String> setPreset(String value) { optionCommands('preset',value) }
List<String> setSensorSource(String value) { optionCommands('sensorSource',value) }
Integer bounded(Object value, BigDecimal min, BigDecimal max, BigDecimal step, Integer scale) {
    BigDecimal n = new BigDecimal(value.toString())
    if (n < min || n > max || n.remainder(step) != 0G) throw new IllegalArgumentException("Value must be ${min} to ${max}, step ${step}")
    (n * scale).intValue()
}
List<String> setHysteresis(Object value) { writeAttr(0xFCC0,0x030C,0x20,bounded(value,0G,3G,0.5G,10)) }
List<String> setTemperatureCalibration(Object value) {
    Integer n = bounded(value,-2.5G,2.5G,0.1G,10)
    writeAttr(0x0201,0x0010,0x28,n & 0xFF)
}
List<String> setSetpointHold(String value) {
    if (!(value in ['on','off'])) throw new IllegalArgumentException('Hold must be on or off')
    writeAttr(0x0201,0x0023,0x30,value == 'on' ? 1 : 0)
}
List<String> setSetpointHoldDuration(Object value) { writeAttr(0x0201,0x0024,0x21,bounded(value,0G,65535G,1G,1)) }
List<String> identify() { zigbee.command(0x0003,0x00,[destEndpoint:1],200,'0300') }
List<String> updateFirmware() {
    log.warn 'Manual OTA requested. Hubitat must host a matching W500 firmware image; inspect live logs for results.'
    zigbee.updateFirmware()
}
// Required by the broad Thermostat capability; hardware has no fan/cooling/auto control.
List<String> unsupported() { log.warn 'This command is not supported by the heating-only W500'; [] }
List<String> auto() { unsupported() }
List<String> cool() { unsupported() }
List<String> emergencyHeat() { unsupported() }
List<String> fanAuto() { unsupported() }
List<String> fanCirculate() { unsupported() }
List<String> fanOn() { unsupported() }
List<String> setCoolingSetpoint(Object value) { unsupported() }
List<String> setThermostatFanMode(String value) { unsupported() }
List<String> setSchedule(Object value) { unsupported() }

void parse(String description) {
    if (settings.debugLogging == true) log.debug description
    try {
        Map msg = zigbee.parseDescriptionAsMap(description)
        if (msg) parseMap(msg)
    } catch (Exception e) { log.warn "W500 message could not be parsed: ${e.message}" }
}
void parseMap(Map msg) {
    Integer cluster = msg.clusterInt != null ? msg.clusterInt as Integer : hex(msg.cluster ?: msg.clusterId)?.intValue()
    Integer ep = hex(msg.endpoint ?: msg.sourceEndpoint)?.intValue()
    if (ep != 1 || cluster == null) return
    sendEvent(name:'lastSeen',value:new Date().format("yyyy-MM-dd'T'HH:mm:ssXXX",location.timeZone),displayed:false)
    ([msg] + (msg.additionalAttrs ?: [])).each { Map attr ->
        if (attr.status != null && attr.status != '00') return
        if (attr.attrId == null || attr.value == null) return
        handleAttribute(cluster,hex(attr.attrId)?.intValue(),attr.value.toString())
    }
}
void temperatureEvent(String name, BigDecimal c) {
    BigDecimal v = getTemperatureScale() == 'F' ? c * 9G / 5G + 32G : c
    sendEvent(name:name,value:v.setScale(1,BigDecimal.ROUND_HALF_UP),unit:getTemperatureScale())
}
void handleAttribute(Integer cluster, Integer attr, String value) {
    if (cluster == 0 && attr in [4,5,0x4000]) {
        if (attr == 4) device.updateDataValue('manufacturer',value)
        if (attr == 5) device.updateDataValue('model',value)
        if (attr == 0x4000) { device.updateDataValue('softwareBuild',value); sendEvent(name:'firmwareVersion',value:value) }
        return
    }
    Long n = hex(value)
    if (n == null) return
    if (cluster == 0 && attr == 1) device.updateDataValue('application',value)
    if (cluster == 0x0201) {
        if (attr in [0,0x0012,0x0015,0x0016] && n != 0x8000L && n <= 65535L) {
            BigDecimal c = (n >= 32768L ? n - 65536L : n) / 100G
            if (attr == 0) temperatureEvent('temperature',c)
            if (attr == 0x0012) { temperatureEvent('heatingSetpoint',c); temperatureEvent('thermostatSetpoint',c) }
            if (attr == 0x0015) { state.minHeatC = c; temperatureEvent('minHeatSetpointLimit',c) }
            if (attr == 0x0016) { state.maxHeatC = c; temperatureEvent('maxHeatSetpointLimit',c) }
        }
        if (attr == 0x001C && n in [0L,4L]) sendEvent(name:'thermostatMode',value:n == 4L ? 'heat' : 'off')
        if (attr == 0x0029 && n != 0xFFFFL) sendEvent(name:'thermostatOperatingState',value:(n & 0x09L) != 0L ? 'heating' : 'idle')
        if (attr == 0x0010 && n <= 255L && n != 0x80L) sendEvent(name:'temperatureCalibration',value:(n >= 128L ? n - 256L : n) / 10G,unit:'C')
        if (attr == 0x0023 && n in [0L,1L]) sendEvent(name:'temperatureSetpointHold',value:n == 1L ? 'on' : 'off')
        if (attr == 0x0024 && n <= 65535L) sendEvent(name:'temperatureSetpointHoldDuration',value:n,unit:'min')
    }
    if (cluster == 0x0405 && attr == 0 && n <= 10000L) sendEvent(name:'humidity',value:n / 100G,unit:'%')
    // W500-specific scaling from ZHC: active power /1 W; summation /1000 kWh.
    if (cluster == 0x0B04 && attr == 0x050B && n < 32768L) sendEvent(name:'power',value:n,unit:'W')
    if (cluster == 0x0702 && attr == 0 && n != 0xFFFFFFFFFFFFL) sendEvent(name:'energy',value:n / 1000G,unit:'kWh')
    if (cluster == 0xFCC0) {
        if (attr == 0x0311) {
            sendEvent(name:'presetRaw',value:n)
            if (!OPTIONS.preset.values.containsValue(n.intValue())) sendEvent(name:'preset',value:'unknown')
        }
        OPTIONS.each { key,spec ->
            if (attr == spec.attr) {
                def entry = spec.values.find { k,v -> v == n }
                if (entry) sendEvent(name:key,value:entry.key)
            }
        }
        if (attr == 0x0310 && n in [0L,2L]) sendEvent(name:'deviceWorkingState',value:n == 0L ? 'working' : 'idle')
        if (attr == 0x030C && n <= 30L) sendEvent(name:'hysteresis',value:n / 10G,unit:'C')
        if (attr == 0x0315 && n != 0xFFFFFFFFL) sendEvent(name:'ntcR25',value:n >= 1000L ? n / 1000G : n,unit:'kOhm')
        if (attr == 0x0316 && value.length() == 8) {
            Float beta = Float.intBitsToFloat(n.intValue())
            if (!beta.isNaN() && !beta.isInfinite()) {
                Integer decoded = beta == Math.rint(beta.doubleValue()) ? beta.intValue() : (n & 0xFFFFL).intValue()
                if (decoded >= 1000 && decoded <= 9999) sendEvent(name:'ntcBeta',value:decoded,unit:'K')
            }
        }
    }
}
Long hex(Object raw) {
    String s = raw?.toString()
    s && s ==~ /[0-9a-fA-F]{1,12}/ ? Long.parseLong(s,16) : null
}
