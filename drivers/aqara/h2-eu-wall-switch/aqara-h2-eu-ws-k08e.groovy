/*
 * Aqara H2 EU WS-K08E / WS-K08D - Hubitat driver, v0.1.0
 * Copyright 2026 Borislav Velkov. SPDX-License-Identifier: MIT
 * Independently implemented from the protocol references listed in README.md.
 * Preview: core controls tested on one WS-K08E; metering accuracy unverified.
 */
import groovy.transform.Field

@Field static final String VERSION = '0.1.0'
@Field static final Map BUTTONS = [1:1, 2:2, 4:3, 5:4]
@Field static final Map ACTIONS = [0:'held', 1:'pushed', 2:'doubleTapped', 255:'released']
@Field static final Map OPTIONS = [
    leftMode: [ep:1, attr:0x0200, type:0x20, values:['decoupled':0, 'control_relay':1]],
    rightMode: [ep:2, attr:0x0200, type:0x20, values:['decoupled':0, 'control_relay':1]],
    leftLock: [ep:1, attr:0x0285, type:0x20, values:['off':0, 'on':1]],
    rightLock: [ep:2, attr:0x0285, type:0x20, values:['off':0, 'on':1]],
    lowerLeftMultiClick: [ep:4, attr:0x0286, type:0x20, values:['off':1, 'on':2]],
    lowerRightMultiClick: [ep:5, attr:0x0286, type:0x20, values:['off':1, 'on':2]],
    ledIndicator: [ep:1, attr:0x0203, type:0x10, values:['off':0, 'on':1]],
    flipIndicator: [ep:1, attr:0x00F0, type:0x20, values:['off':0, 'on':1]],
    powerOnBehavior: [ep:1, attr:0x0517, type:0x20, values:['on':0, 'previous':1, 'off':2, 'inverted':3]]
]

metadata {
    definition(name:'Aqara H2 EU 4 Button 2 Relay (WS-K08E)', namespace:'velkovb',
               author:'Borislav Velkov', singleThreaded:true, importUrl:'https://raw.githubusercontent.com/velkovb/hubitat-drivers/main/drivers/aqara/h2-eu-wall-switch/aqara-h2-eu-ws-k08e.groovy') {
        capability 'Configuration'
        capability 'Refresh'
        capability 'PushableButton'
        capability 'DoubleTapableButton'
        capability 'HoldableButton'
        capability 'ReleasableButton'
        capability 'PowerMeter'
        capability 'EnergyMeter'
        capability 'CurrentMeter'
        attribute 'driverVersion', 'string'
        attribute 'lastSeen', 'string'
        attribute 'leftSwitch', 'enum', ['on','off']
        attribute 'rightSwitch', 'enum', ['on','off']
        OPTIONS.each { key, spec -> attribute key, 'string' }
        fingerprint profileId:'0104', manufacturer:'LUMI', model:'lumi.switch.agl010',
                    deviceJoinName:'Aqara Light Switch H2 EU 4 buttons 2 Channels'
        fingerprint profileId:'0104', manufacturer:'Aqara', model:'lumi.switch.agl010',
                    deviceJoinName:'Aqara Light Switch H2 EU 4 buttons 2 Channels'
    }
    preferences {
        input name:'debugLogging', type:'bool', title:'Debug logging (30 minutes)', defaultValue:false
        input name:'infoLogging', type:'bool', title:'Log button and relay events', defaultValue:true
        Map titles = [leftMode:'Upper-left button mode', rightMode:'Upper-right button mode',
            leftLock:'Lock left relay', rightLock:'Lock right relay',
            lowerLeftMultiClick:'Lower-left button multiple clicks', lowerRightMultiClick:'Lower-right button multiple clicks',
            ledIndicator:'LED indicator', flipIndicator:'Invert LED indication', powerOnBehavior:'After a power outage']
        Map labels = [on:'On', off:'Off', decoupled:'Decoupled (automations only)', control_relay:'Control wired relay',
            previous:'Restore previous state', inverted:'Invert previous state']
        OPTIONS.each { key, spec ->
            input name:key, type:'enum', title:titles[key], options:([keep:'Keep device setting'] + spec.values.collectEntries { k,v -> [(k):labels[k]] }), defaultValue:'keep'
        }
    }
}

void installed() { initialize() }
void updated() {
    initialize()
    transmit(preferenceCommands())
}
void initialize() {
    sendEvent(name:'numberOfButtons', value:4)
    sendEvent(name:'driverVersion', value:VERSION)
    ensureChildren()
    unschedule('logsOff')
    if (settings.debugLogging == true) runIn(1800, 'logsOff')
}
void logsOff() { device.updateSetting('debugLogging', [value:'false', type:'bool']) }

void ensureChildren() {
    [1:'Left relay', 2:'Right relay'].each { ep, label ->
        String dni = childDni(ep)
        if (!getChildDevice(dni)) {
            addChildDevice('hubitat', 'Generic Component Switch', dni,
                [name:label, label:"${device.displayName} - ${label}", isComponent:true])
        }
    }
}
String childDni(Integer ep) { "${device.deviceNetworkId}-ep${ep}" }
Integer childEndpoint(child) {
    Integer ep = [1,2].find { child.deviceNetworkId == childDni(it) }
    if (ep == null) throw new IllegalArgumentException('Unknown relay child')
    ep
}
void componentOn(child) { transmit(relayCommands(childEndpoint(child), true)) }
void componentOff(child) { transmit(relayCommands(childEndpoint(child), false)) }
void componentRefresh(child) { transmit(readAttr(childEndpoint(child), 0x0006, 0x0000)) }
List<String> relayCommands(Integer ep, Boolean on) {
    if (!(ep in [1,2])) throw new IllegalArgumentException('Relay endpoint must be 1 or 2')
    zigbee.command(0x0006, on ? 1 : 0, [destEndpoint:ep], 200) + readAttr(ep, 0x0006, 0x0000)
}
void transmit(List<String> commands) {
    if (commands) sendHubCommand(new hubitat.device.HubMultiAction(commands, hubitat.device.Protocol.ZIGBEE))
}
List<String> readAttr(Integer ep, Integer cluster, Object attr) {
    Map params = [destEndpoint:ep]
    if (cluster == 0xFCC0) params.mfgCode = 0x115F
    zigbee.readAttribute(cluster, attr, params, 150)
}
List<String> preferenceCommands() {
    List<String> commands = []
    OPTIONS.each { key, spec ->
        String requested = settings[key]?.toString()
        if (requested && requested != 'keep') {
            if (!spec.values.containsKey(requested)) throw new IllegalArgumentException("Invalid ${key}")
            commands += zigbee.writeAttribute(0xFCC0, spec.attr, spec.type, spec.values[requested],
                [destEndpoint:spec.ep, mfgCode:0x115F], 200)
            commands += readAttr(spec.ep, 0xFCC0, spec.attr)
        }
    }
    commands
}
List<String> configure() {
    initialize()
    List<String> commands = []
    [1,2].each { ep -> commands += zigbee.configureReporting(0x0006, 0x0000, 0x10, 0, 3600, null, [destEndpoint:ep], 200) }
    // Bind buttons without periodic reporting of the last gesture (which can replay old presses).
    [1,2,4,5].each { ep ->
        [0x0012,0xFCC0].each { cluster ->
            commands << "zdo bind 0x${device.deviceNetworkId} 0x${String.format('%02X',ep)} 0x01 0x${String.format('%04X',cluster)} {${device.zigbeeId}} {}"
            commands << 'delay 200'
        }
    }
    // Do not configure electrical measurement reporting: this model rejects some of it.
    commands + preferenceCommands() + refresh()
}
List<String> refresh() {
    List<String> commands = readAttr(1, 0x0000, [0x0004,0x0005,0x0001])
    [1,2].each { ep -> commands += readAttr(ep, 0x0006, 0x0000) }
    OPTIONS.each { key,spec -> commands += readAttr(spec.ep, 0xFCC0, spec.attr) }
    // Read scaling before samples; also handle reports arriving in the reverse order.
    commands += readAttr(1, 0x0B04, [0x0604,0x0605,0x050B,0x0508])
    commands += readAttr(1, 0x0702, [0x0300,0x0301,0x0302,0x0000])
    commands += readAttr(21, 0x000C, 0x0055)
    commands
}

void push(Object button) { digitalButton('pushed', button) }
void doubleTap(Object button) { digitalButton('doubleTapped', button) }
void hold(Object button) { digitalButton('held', button) }
void release(Object button) { digitalButton('released', button) }
void digitalButton(String action, Object value) {
    Integer button = value.toString().toInteger()
    if (!(button in 1..4)) throw new IllegalArgumentException('Button must be 1 to 4')
    sendEvent(name:action, value:button, type:'digital', isStateChange:true)
}

void parse(String description) {
    try {
        Map msg = zigbee.parseDescriptionAsMap(description)
        if (msg) parseMap(msg)
    } catch (Exception e) {
        log.warn "${device.displayName}: cannot parse Zigbee message (${e.message})"
        if (settings.debugLogging == true) log.debug description
    }
}
void parseMap(Map msg) {
    if (settings.debugLogging == true) log.debug "${device.displayName}: ${msg}"
    sendEvent(name:'lastSeen', value:new Date().format("yyyy-MM-dd'T'HH:mm:ssXXX", location.timeZone), displayed:false)
    Integer cluster = msg.clusterInt != null ? msg.clusterInt as Integer : hex(msg.cluster ?: msg.clusterId)
    Integer ep = hex(msg.endpoint ?: msg.sourceEndpoint)
    if (cluster == null || ep == null) return
    // Responses with unsupported attributes must not accidentally reuse the parent's value.
    ([msg] + (msg.additionalAttrs ?: [])).each { Map attr ->
        if (attr.status != null && attr.status != '00') return
        if (attr.attrId == null || attr.value == null) return
        handleAttribute(cluster, ep, hex(attr.attrId), attr.value.toString(), msg.command?.toString())
    }
}
void handleAttribute(Integer cluster, Integer ep, Integer attr, String value, String command) {
    if (cluster == 0x0000 && ep == 1) {
        if (attr == 4) device.updateDataValue('manufacturer', value)
        if (attr == 5) device.updateDataValue('model', value)
        if (attr == 1) device.updateDataValue('application', value)
        return
    }
    Long n = hexLong(value)
    if (n == null) return
    if (cluster == 0x0006 && attr == 0 && ep in [1,2] && n in [0L,1L]) {
        String relayState = n == 1L ? 'on' : 'off'
        getChildDevice(childDni(ep))?.parse([[name:'switch', value:relayState]])
        sendEvent(name:ep == 1 ? 'leftSwitch' : 'rightSwitch', value:relayState)
        info("${ep == 1 ? 'Left' : 'Right'} relay is ${relayState}")
    } else if (cluster == 0x0012 && attr == 0x0055 && command == '0A') {
        Integer button = BUTTONS[ep]
        String action = ACTIONS[n.intValue()]
        // Read responses are deliberately ignored: refreshing must never create a button event.
        if (button != null && action != null) {
            sendEvent(name:action, value:button, type:'physical', isStateChange:true)
            info("Button ${button} ${action}")
        }
    } else if (cluster == 0xFCC0) {
        OPTIONS.each { key,spec ->
            if (spec.ep == ep && spec.attr == attr) {
                def entry = spec.values.find { k,v -> v == n }
                if (entry) sendEvent(name:key, value:entry.key)
            }
        }
    } else if (cluster == 0x0B04 && ep == 1) {
        if (attr == 0x0604 && n != 65535L) state.powerMultiplier = n
        if (attr == 0x0605 && n > 0L && n != 65535L) state.powerDivisor = n
        if (attr == 0x050B && n != 0x8000L) state.rawPower = n >= 32768L ? n - 65536L : n
        // WS-K08E current is explicitly unscaled in the Z2M model definition.
        if (attr == 0x0508 && n != 65535L) sendEvent(name:'amperage', value:n, unit:'A')
        publishPower()
    } else if (cluster == 0x0702 && ep == 1) {
        if (attr == 0x0300) state.energyUnit = n
        if (attr == 0x0301 && n != 0xFFFFFFL) state.energyMultiplier = n
        if (attr == 0x0302 && n > 0L && n != 0xFFFFFFL) state.energyDivisor = n
        if (attr == 0 && n != 0xFFFFFFFFFFFFL) state.rawEnergy = n
        if (state.energyUnit == 0 && state.rawEnergy != null && state.energyMultiplier != null && state.energyDivisor != null) {
            sendEvent(name:'energy', value:(state.rawEnergy as BigDecimal) * (state.energyMultiplier as BigDecimal) / (state.energyDivisor as BigDecimal), unit:'kWh')
        }
    } else if (cluster == 0x000C && ep == 21 && attr == 0x0055 && value.length() == 8) {
        Float watts = Float.intBitsToFloat(n.intValue())
        if (!watts.isNaN() && !watts.isInfinite() && watts >= 0) {
            state.analogPowerTime = now()
            sendEvent(name:'power', value:watts, unit:'W')
        }
    }
}
void publishPower() {
    if (state.analogPowerTime != null && now() - (state.analogPowerTime as Long) < 120000L) return
    if (state.rawPower != null && state.powerMultiplier != null && state.powerDivisor != null) {
        sendEvent(name:'power', value:(state.rawPower as BigDecimal) * (state.powerMultiplier as BigDecimal) / (state.powerDivisor as BigDecimal), unit:'W')
    }
}
Integer hex(Object s) { Long n = hexLong(s); n == null ? null : n.intValue() }
Long hexLong(Object s) {
    String value = s?.toString()
    if (!value || !(value ==~ /[0-9a-fA-F]{1,12}/)) return null
    Long.parseLong(value,16)
}
void info(String message) { if (settings.infoLogging != false) log.info "${device.displayName}: ${message}" }
