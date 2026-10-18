/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 velkovb and contributors
 *
 * Aqara Wall Outlet H2 EU WP-P01D (lumi.plug.aeu001)
 * Hubitat Elevation (tested on C-8) — community preview v0.6.1
 *
 * AI-assisted ("vibe-coded") independent Groovy implementation.
 * Human-tested on one device; NOT independently code-audited or certified.
 *
 * TESTED: EP01 physical relay, LED on/off, physical button lock/unlock,
 * power-on behavior=previous, changing power and accumulating energy.
 * NOT YET VERIFIED: other startup modes, current/voltage accuracy,
 * charging cut-off and overload-configuration writes.
 *
 * Manufacturer attributes were researched using sources listed in
 * THIRD_PARTY_NOTICES.md. No upstream implementation is bundled.
 * Use at your own risk and test on one device before wider deployment.
 */
metadata {
    definition(name: 'Aqara H2 Wall Outlet EU (Local Zigbee)', namespace: 'velkovb', author: 'velkovb') {
        capability 'Actuator'
        capability 'Switch'
        capability 'Refresh'
        capability 'Configuration'
        capability 'PowerMeter'
        capability 'EnergyMeter'
        capability 'VoltageMeasurement'
        capability 'CurrentMeter'

        command 'refreshSettings'
        command 'probeMetering'
        command 'setLed', [[name: 'LED', type: 'ENUM', constraints: ['on', 'off']]]
        command 'setButtonLock', [[name: 'Physical button', type: 'ENUM', constraints: ['lock', 'unlock']]]
        command 'setPowerOnBehavior', [[name: 'After power returns', type: 'ENUM', constraints: ['on', 'previous', 'off', 'inverted']]]
        command 'setChargingProtection', [[name: 'Protection', type: 'ENUM', constraints: ['on', 'off']]]
        command 'setChargingLimit', [[name: 'Watts, 0.1 to 2.0', type: 'NUMBER']]
        command 'setOverloadProtection', [[name: 'Watts, 100 to 3840', type: 'NUMBER']]

        attribute 'ledIndicator', 'string'
        attribute 'buttonLock', 'string'
        attribute 'powerOnBehavior', 'string'
        attribute 'chargingProtection', 'string'
        attribute 'chargingLimit', 'number'
        attribute 'overloadProtection', 'number'
        attribute 'deviceTemperature', 'number'
        attribute 'powerOutageCount', 'number'
        attribute 'meteringSource', 'string'
        attribute 'driverStatus', 'string'

        fingerprint profileId: '0104', model: 'lumi.plug.aeu001', deviceJoinName: 'Aqara H2 Wall Outlet EU'
    }
    preferences {
        input name: 'logEnable', type: 'bool', title: 'Debug messages (avoid leaving on)', defaultValue: false
        input name: 'txtEnable', type: 'bool', title: 'Log physical switch changes', defaultValue: true
        input name: 'allowAdvancedWrites', type: 'bool', title: 'Enable ADVANCED changes (button lock, restart, charger, overload)', defaultValue: false
        input name: 'meteringLog', type: 'bool', title: 'Log metering diagnostics', defaultValue: false
        input name: 'relayEndpoint', type: 'enum', title: 'Relay Zigbee endpoint (01 verified for this model)', options: ['01': '01 (tested)', '02': '02 (experimental)'], defaultValue: '01'
    }
}

private int relayEp() {
    (settings.relayEndpoint?.toString() == '02') ? 2 : 1
}
private String cmdOnOff(int commandId) {
    String.format('he cmd 0x%s 0x%02X 0x0006 0x%02X {}', device.deviceNetworkId, relayEp(), commandId)
}
private String readOnOff(int ep) {
    String.format('he rattr 0x%s 0x%02X 0x0006 0x0000 {}', device.deviceNetworkId, ep)
}
private String readEp(int ep, int cluster, int attr) {
    String.format('he rattr 0x%s 0x%02X 0x%04X 0x%04X {}', device.deviceNetworkId, ep, cluster, attr)
}
private List<String> manufacturerRead(int attr) {
    zigbee.readAttribute(0xFCC0, attr, [mfgCode: '0x115F', destEndpoint: 0x01], 250)
}
private List<String> manufacturerWriteByte(int attr, int typeId, int number) {
    zigbee.writeAttribute(0xFCC0, attr, typeId, number,
                          [mfgCode: '0x115F', destEndpoint: 0x01], 250)
}
private List<String> manufacturerWriteFloat(int attr, float number) {
    // Hubitat Zigbee.writeAttribute takes an Integer payload; IEEE754 floats
    // must be explicitly marshalled as little-endian 4-byte ZCL Single (0x39).
    // This advanced write command is unverified on a physical C-8.
    int ieee754 = Float.floatToIntBits(number)
    String littleEndian = String.format('%08X', Integer.reverseBytes(ieee754))
    [String.format('he wattr 0x%s 0x01 0xFCC0 0x%04X 0x39 {%s} {115F}',
                   device.deviceNetworkId, attr, littleEndian)]
}
private void blocked(String what) {
    log.warn "${device.displayName}: ${what} NOT sent. First enable 'ADVANCED changes' in Preferences."
}

// Relay defaults to EP01, confirmed on one physical WP-P01D.
def on()  { [cmdOnOff(1), 'delay 300', readOnOff(relayEp())] }
def off() { [cmdOnOff(0), 'delay 300', readOnOff(relayEp())] }

def configure() {
    log.info 'Aqara H2 v0.6.1: read-only configure (no proprietary settings overwritten)'
    return refresh()
}

def refresh() {
    List<String> commands = [readOnOff(relayEp()), 'delay 160']
    commands.addAll(probeMeteringCommands())
    return commands
}

def probeMetering() {
    log.info 'Reading EP21 analog power and EP01 meter/voltage/current; aggregate 0x00F7 telemetry is received asynchronously.'
    return probeMeteringCommands()
}
private List<String> probeMeteringCommands() {
    List<String> cmds = []
    [[21, 0x000C, 0x0055],
     [1, 0x0702, 0x0000], [1, 0x0702, 0x0301], [1, 0x0702, 0x0302],
     [1, 0x0B04, 0x050B], [1, 0x0B04, 0x0505], [1, 0x0B04, 0x0508],
     [1, 0x0B04, 0x0600], [1, 0x0B04, 0x0601],
     [1, 0x0B04, 0x0602], [1, 0x0B04, 0x0603],
     [1, 0x0B04, 0x0604], [1, 0x0B04, 0x0605]
    ].each { row ->
        cmds << readEp(row[0] as int, row[1] as int, row[2] as int)
        cmds << 'delay 160'
    }
    return cmds
}

def refreshSettings() {
    List<String> cmds = []
    [0x0200, 0x0202, 0x0203, 0x0206, 0x020B, 0x0517].each { a ->
        cmds.addAll(manufacturerRead(a))
        cmds << 'delay 180'
    }
    log.info 'Reading Aqara settings; no writes sent.'
    return cmds
}

// LED is a reversible cosmetic setting; it's enabled without ADVANCED opt-in.
def setLed(String desired) {
    String v = desired?.toLowerCase()
    if (!(v in ['on', 'off'])) { log.warn 'LED accepts only on/off'; return [] }
    log.info "Requesting Aqara LED: ${v}"
    List<String> cmds = []
    cmds.addAll(manufacturerWriteByte(0x0203, 0x10, v == 'on' ? 1 : 0))
    cmds << 'delay 350'
    cmds.addAll(manufacturerRead(0x0203))
    return cmds
}

def setButtonLock(String desired) {
    if (!allowAdvancedWrites) { blocked('Button lock'); return [] }
    String v = desired?.toLowerCase()
    if (!(v in ['lock', 'unlock'])) return []
    // Confirmed on one physical WP-P01D: 0=locked, 1=unlocked.
    int value = (v == 'lock') ? 0 : 1
    log.info "Changing physical button ${v}; raw value ${value}."
    List<String> cmds = []
    cmds.addAll(manufacturerWriteByte(0x0200, 0x20, value))
    cmds << 'delay 300'
    cmds.addAll(manufacturerRead(0x0200))
    return cmds
}

def setPowerOnBehavior(String desired) {
    if (!allowAdvancedWrites) { blocked('Power-on behavior'); return [] }
    Map values = [on: 0, previous: 1, off: 2, inverted: 3]
    Integer number = values[desired?.toLowerCase()]
    if (number == null) return []
    List<String> cmds = []
    cmds.addAll(manufacturerWriteByte(0x0517, 0x20, number))
    cmds << 'delay 300'
    cmds.addAll(manufacturerRead(0x0517))
    return cmds
}

def setChargingProtection(String desired) {
    if (!allowAdvancedWrites) { blocked('Charging protection'); return [] }
    String v = desired?.toLowerCase()
    if (!(v in ['on', 'off'])) return []
    List<String> cmds = []
    cmds.addAll(manufacturerWriteByte(0x0202, 0x10, v == 'on' ? 1 : 0))
    cmds << 'delay 300'
    cmds.addAll(manufacturerRead(0x0202))
    return cmds
}

def setChargingLimit(BigDecimal value) {
    if (!allowAdvancedWrites) { blocked('Charging limit'); return [] }
    if (value == null || value < 0.1 || value > 2.0) {
        log.warn 'Charging limit must be between 0.1 and 2 W.'; return []
    }
    List<String> cmds = manufacturerWriteFloat(0x0206, value.floatValue())
    cmds << 'delay 300'
    cmds.addAll(manufacturerRead(0x0206))
    return cmds
}

def setOverloadProtection(BigDecimal value) {
    if (!allowAdvancedWrites) { blocked('Overload threshold'); return [] }
    if (value == null || value < 100 || value > 3840) {
        log.warn 'Overload threshold must be 100–3840 W; NOT a substitute for circuit protection.'; return []
    }
    log.warn "Writing device overload threshold ${value} W. This does NOT replace the circuit breaker or RCD."
    List<String> cmds = manufacturerWriteFloat(0x020B, value.floatValue())
    cmds << 'delay 300'
    cmds.addAll(manufacturerRead(0x020B))
    return cmds
}

void installed() {
    sendEvent(name:'driverStatus', value:'v0.6.1 preview: core features tested on one WP-P01D; protection settings experimental')
}
void updated() {
    sendEvent(name:'driverStatus', value:'v0.6.1 preview: relay endpoint defaults to 01; advanced writes opt-in')
}

private String endpoint(Map m) {
    String ep = m.endpoint?.toString()?.replaceFirst('(?i)^0x','')?.toUpperCase()
    ep ? ep.padLeft(2, '0') : ''
}
private Long parseLEUnsigned(String hex) {
    if (!hex || hex.length() % 2) return null
    try {
        String rev = hex.toUpperCase().toList().collate(2).collect{ it.join('') }.reverse().join('')
        return new BigInteger(rev, 16).longValue()
    } catch (Exception ignored) { return null }
}
private Double parseFloatLE(String hex) {
    if (hex == null || hex.size() != 8) return null
    try {
        long bits = Long.parseLong(hex.toUpperCase().toList().collate(2).collect{it.join('')}.reverse().join(''),16)
        float f = Float.intBitsToFloat((int)bits)
        return Float.isNaN(f) || Float.isInfinite(f) ? null : (double)f
    } catch (Exception ignored) { return null }
}
private Double numberFromAttr(Map m) {
    String value = m.value?.toString()?.replaceFirst('(?i)^0x', '')
    if (!value) return null
    // For ordinary read-attribute messages, Hubitat presents the VALUE
    // as big-endian hexadecimal (already normalized from the wire).
    // Confirmed on this device: 0x40BF0A3D = Float32 5.97 W on EP21,
    // 0x0003E8 = divisor 1000 on EP01. Do not byte-swap these values.
    try {
        if ((m.encoding?.toString()?.toUpperCase() == '39') && value.size() == 8) {
            float f = Float.intBitsToFloat((int)Long.parseLong(value, 16))
            return (Float.isNaN(f) || Float.isInfinite(f)) ? null : (double)f
        }
        return new BigInteger(value, 16).doubleValue()
    } catch (Exception ignored) { return null }
}
private void emit(String name, double value, String unit = null) {
    if (Double.isNaN(value) || Double.isInfinite(value)) return
    double n = Math.round(value * 1000d) / 1000d
    def existing = device.currentValue(name)
    if (existing != null) {
        try { if (Math.abs(existing.toString().toDouble() - n) < 0.0005d) return }
        catch (Exception ignored) {}
    }
    Map evt = [name:name, value:n]
    if (unit) evt.unit = unit
    sendEvent(evt)
}

private void measure(String which, Double n, String source) {
    if (n == null || n < 0) return
    switch (which) {
        case 'power':
            if (n > 5000d) return
            emit('power', n, 'W'); break
        case 'energy':
            if (n > 10000000d) return
            emit('energy', n, 'kWh'); break
        case 'voltage':
            if (n < 100d || n > 300d) return
            emit('voltage', n, 'V'); break
        case 'amperage':
            if (n > 50d) return
            emit('amperage', n, 'A'); break
    }
    if (which in ['power','energy']) {
        if (device.currentValue('meteringSource') != source) sendEvent(name:'meteringSource', value:source)
    }
    if (meteringLog) log.info "H2 ${which}=${n} (${source})"
}

// Aqara/F7 typed-value parser: tag 149=energy, 150=voltage (in 0.1 V),
// 151=current (in mA), 152=power, 3=device temp, 5=outage count.
// Unrecognized types stop parsing rather than guessing lengths.
private void parseAqaraAggregate(String payload) {
    if (!payload || payload.size() % 2) return
    // Some Hubitat firmware descriptions include the Zigbee octet-string length
    // byte (e.g. 0x3F for 63 bytes); remove it only if the length agrees.
    if (payload.size() >= 4) {
        int declared = Integer.parseInt(payload.substring(0, 2), 16)
        if (declared == (payload.size() - 2) / 2) payload = payload.substring(2)
    }
    int i = 0
    while (i + 4 <= payload.length()) {
        try {
            int tag = Integer.parseInt(payload.substring(i,i+2),16)
            int type = Integer.parseInt(payload.substring(i+2,i+4),16)
            i += 4
            int len = [(0x10):1,(0x20):1,(0x21):2,(0x22):3,(0x23):4,
                       (0x28):1,(0x29):2,(0x2A):3,(0x2B):4,
                       (0x30):1,(0x31):2,(0x39):4][type] ?: 0
            if (!len || i + len*2 > payload.length()) break
            String raw = payload.substring(i, i + len*2)
            i += len*2
            Double v = (type == 0x39) ? parseFloatLE(raw) : parseLEUnsigned(raw)?.doubleValue()
            if (v == null) continue
            switch (tag) {
                case 3: // signed internal chip temperature
                    if (type == 0x28) {
                        int c = Integer.parseInt(raw,16)
                        if (c >= 128) c -= 256
                        if (c > -30 && c < 120) emit('deviceTemperature', c, '°C')
                    }
                    break
                case 5:
                    if (v >= 0 && v < 1000000) emit('powerOutageCount', v)
                    break
                case 149: measure('energy', v, 'Aqara 0xF7 tag149'); break
                case 150: measure('voltage', v / 10d, 'Aqara 0xF7 tag150'); break
                case 151: measure('amperage', v / 1000d, 'Aqara 0xF7 tag151'); break
                case 152: measure('power', v, 'Aqara 0xF7 tag152'); break
            }
        } catch (Exception e) {
            if (logEnable) log.debug "Aggregate parse stopped at position ${i}: ${e.message}"
            break
        }
    }
}

private void aqaraSetting(int attr, Double value) {
    if (value == null) return
    switch (attr) {
        case 0x0203: sendEvent(name:'ledIndicator', value:value >= 1 ? 'on' : 'off'); break
        case 0x0200: sendEvent(name:'buttonLock', value:value == 0 ? 'locked' : 'unlocked'); break
        case 0x0202: sendEvent(name:'chargingProtection', value:value >= 1 ? 'on' : 'off'); break
        case 0x0206: emit('chargingLimit', value, 'W'); break
        case 0x020B: emit('overloadProtection', value, 'W'); break
        case 0x0517:
            String mode = [0:'on',1:'previous',2:'off',3:'inverted'][(int)value]
            if (mode) sendEvent(name:'powerOnBehavior', value:mode)
            break
    }
}

def parse(String description) {
    Map m
    try { m = zigbee.parseDescriptionAsMap(description) }
    catch (Exception ignored) { return }
    if (m == null) return
    String ep = endpoint(m)
    int cl = m.clusterInt != null ? (m.clusterInt as int) : -1
    int at = m.attrInt != null ? (m.attrInt as int) : -1
    if (logEnable && !(cl == 0xFCC0 && at == 0xFFF2)) log.debug "H2 RX ${m}"
    if (cl == 0x0006 && at == 0 && ep == String.format('%02X', relayEp()) && m.value != null) {
        String val = (Integer.parseInt(m.value.toString(),16) == 1) ? 'on' : 'off'
        if (device.currentValue('switch') != val) {
            sendEvent(name:'switch', value:val)
            if (txtEnable) log.info "${device.displayName} physical outlet: ${val}"
        }
        return
    }
    if (cl == 0x0006 && ep != String.format('%02X', relayEp())) return // auxiliary/logical endpoint
    if (cl == 0xFCC0) {
        if (at == 0xFFF2) return // noisy heartbeat packet; not needed
        if (at in [0x00F7, 0xFF01] && m.value) {
            parseAqaraAggregate(m.value.toString()); return
        }
        if (at in [0x0200, 0x0202, 0x0203, 0x0206, 0x020B, 0x0517]) {
            aqaraSetting(at, numberFromAttr(m)); return
        }
        return
    }
    if (cl == 0x000C && at == 0x0055 && ep == '15') {
        measure('power', numberFromAttr(m), 'EP21 analog input'); return
    }
    if (cl == 0x0702 && ep == '01') {
        if (at == 0x0301) { state.energyMultiplier = numberFromAttr(m); return }
        if (at == 0x0302) { state.energyDivisor = numberFromAttr(m); return }
        if (at == 0x0000) {
            Double raw = numberFromAttr(m)
            // Some WP-P01D firmware reports zero on the standard energy path.
            // Don't overwrite a nonzero Aqara aggregate reading with that zero.
            if (raw == 0d && (device.currentValue('energy') ?: 0d).toString().toDouble() > 0d) return
            if (raw != null) {
                double factor = (state.energyMultiplier ?: 1d) / (state.energyDivisor ?: 1d)
                if (factor > 0) measure('energy', raw * factor, 'EP01 SimpleMetering')
            }
            return
        }
    }
    if (cl == 0x0B04 && ep == '01') {
        if (at == 0x0600) { state.voltageMultiplier = numberFromAttr(m); return }
        if (at == 0x0601) { state.voltageDivisor = numberFromAttr(m); return }
        if (at == 0x0602) { state.currentMultiplier = numberFromAttr(m); return }
        if (at == 0x0603) { state.currentDivisor = numberFromAttr(m); return }
        if (at == 0x0604) { state.powerMultiplier = numberFromAttr(m); return }
        if (at == 0x0605) { state.powerDivisor = numberFromAttr(m); return }
        Double raw = numberFromAttr(m)
        if (raw == null) return
        if (at == 0x050B) {
            // Aqara exposes 0.1 W electrical-measurement units; the
            // same test showed 0x003B = 59 for a 5.97 W EP21 reading.
            // Prefer EP21's direct Float32 as the authoritative meter.
            if (device.currentValue('power') == null) {
                measure('power', raw * (state.powerMultiplier ?: 1d) / (state.powerDivisor ?: 10d), 'EP01 ElectricalMeasurement (fallback)')
            } else if (meteringLog) {
                log.info "EP01 activePower raw=${raw}, EP21 direct meter remains authoritative"
            }
        }
        if (at == 0x0505) measure('voltage', raw * (state.voltageMultiplier ?: 1d) / (state.voltageDivisor ?: 1d), 'EP01 ElectricalMeasurement')
        if (at == 0x0508) measure('amperage', raw * (state.currentMultiplier ?: 1d) / (state.currentDivisor ?: 1d), 'EP01 ElectricalMeasurement')
        return
    }
}
