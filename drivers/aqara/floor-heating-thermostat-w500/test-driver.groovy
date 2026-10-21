import org.codehaus.groovy.control.CompilerConfiguration

// Runs the real driver against boundary stubs; no hub, relay or firmware writes.
class DriverHarness extends Script {
    def events = [], sent = [], data = [:], jobs = [:]
    def state = [:], settings = [:]
    String scale = 'C'
    def device = [deviceNetworkId:'1234', zigbeeId:'0011223344556677', displayName:'W500',
        updateDataValue:{ k,v -> data[k] = v }, updateSetting:{ k,v -> settings[k] = v.value }]
    def location = [timeZone:TimeZone.getTimeZone('Europe/Sofia')]
    def log = [warn:{ x -> }, debug:{ x -> }]
    def zigbee = new ZigbeeStub()
    def run() {}
    def metadata(Closure c) {}
    def getTemperatureScale() { scale }
    def sendEvent(Map m) { events << m }
    def unschedule(String s) { jobs.remove(s) }
    def runIn(int n, String s) { jobs[s] = n }
    def runEvery15Minutes(String s) { jobs[s] = 900 }
    def sendHubCommand(action) { sent << action }
}
class ZigbeeStub {
    def calls = []
    def parseDescriptionAsMap(String s) {
        if (s == 'bad') throw new IllegalArgumentException('malformed')
        [endpoint:'01',cluster:'0201',attrId:'0000',value:'0898']
    }
    def methodMissing(String name, args) { calls << [name:name,args:args.toList()]; [name] }
}
def loader = new GroovyClassLoader(this.class.classLoader,new CompilerConfiguration(scriptBaseClass:DriverHarness.name))
loader.parseClass('package hubitat.device; enum Protocol { ZIGBEE }')
loader.parseClass('package hubitat.device; class HubMultiAction { HubMultiAction(Object a, Object b) {} }')
def driver = loader.parseClass(new File(args ? args[0] : 'drivers/aqara/floor-heating-thermostat-w500/aqara-w500.groovy')).newInstance()
driver.run()
def report = { cluster, attr, value, ep='01' -> driver.parseMap([endpoint:ep,cluster:cluster,attrId:attr,value:value]) }
def last = { name -> driver.events.findAll { it.name == name }.last().value }
def count = { name -> driver.events.count { it.name == name } }
def rejected = { Closure action ->
    boolean failed = false
    try { action() } catch (IllegalArgumentException expected) { failed = true }
    assert failed
}
driver.initialize()
assert driver.jobs.poll == 900
assert !driver.zigbee.calls
driver.configure()
assert !driver.zigbee.calls.find { it.name in ['writeAttribute','updateFirmware'] }
assert driver.events.find { it.name == 'supportedThermostatModes' }.value == '["off","heat"]'
driver.parse('valid')
assert last('temperature') == 22G
driver.parse('bad') // no exception escapes
report('0201','0000','FF9C')
assert last('temperature') == -1G
int before = count('temperature')
report('0201','0000','8000')
report('0201','0000','FFFF','02')
report('0201','0000','invalid')
assert count('temperature') == before
driver.parseMap([endpoint:'01',clusterInt:0x0201,attrId:'0012',value:'0834',
    additionalAttrs:[[attrId:'001C',value:'04'],[attrId:'0000',status:'86']]])
assert last('heatingSetpoint') == 21G
assert last('thermostatSetpoint') == 21G
assert last('thermostatMode') == 'heat'
assert count('temperature') == before
report('0201','0029','0001'); assert last('thermostatOperatingState') == 'heating'
report('0201','0029','0008'); assert last('thermostatOperatingState') == 'heating'
report('0201','0029','0000'); assert last('thermostatOperatingState') == 'idle'
report('0201','0010','E7'); assert last('temperatureCalibration') == -2.5G
report('0405','0000','1388'); assert last('humidity') == 50G
report('0405','0000','FFFF'); assert last('humidity') == 50G
report('0B04','050B','04D2'); assert last('power') == 1234L
report('0B04','050B','8000'); assert last('power') == 1234L
report('0B04','050B','0000'); assert last('power') == 0L
report('0702','0000','000000003039'); assert last('energy') == 12.345G
report('0702','0000','FFFFFFFFFFFF'); assert last('energy') == 12.345G
report('FCC0','0311','FF'); assert last('preset') == 'manual'
assert last('presetRaw') == 255
report('FCC0','0311','07'); assert last('preset') == 'unknown'
assert last('presetRaw') == 7
report('FCC0','0311','03'); assert last('preset') == 'sleep'
report('FCC0','0280','02'); assert last('sensorSource') == 'ntc'
report('FCC0','0315','00002710'); assert last('ntcR25') == 10G
report('FCC0','0316','47600000'); assert !driver.events.find { it.name == 'ntcBeta' } // integer 57344, outside supported range
report('FCC0','0316','45760F6E'); assert last('ntcBeta') == 3950
report('FCC0','0316','4576E000'); assert last('ntcBeta') == 3950 // integer 3950 after a power cycle
report('0000','4000','0.0.0_1030'); assert last('firmwareVersion') == '0.0.0_1030'
report('0000','0005','lumi.airrtc.aeu001'); assert driver.data.model == 'lumi.airrtc.aeu001'
driver.zigbee.calls.clear()
before = count('heatingSetpoint')
driver.setHeatingSetpoint(21.5)
def write = driver.zigbee.calls.find { it.name == 'writeAttribute' }
assert write.args[0..4] == [0x0201,0x0012,0x29,2150,[destEndpoint:1]]
assert count('heatingSetpoint') == before // readbacks only, never optimistic
driver.setScale('F')
driver.zigbee.calls.clear()
driver.setHeatingSetpoint(68)
assert driver.zigbee.calls[0].args[3] == 2000
report('0201','0000','07D0'); assert last('temperature') == 68G
driver.setScale('C')
report('0201','0015','01F4')
report('0201','0016','0BB8')
rejected { driver.setHeatingSetpoint(31) }
rejected { driver.setHeatingSetpoint('NaN') }
rejected { driver.setHeatingSetpoint(4) }
driver.zigbee.calls.clear()
driver.setTemperatureCalibration(-2.5)
assert driver.zigbee.calls[0].args[0..3] == [0x0201,0x0010,0x28,231]
rejected { driver.setTemperatureCalibration(2.6) }
rejected { driver.setHysteresis(0.1) }
rejected { driver.setSetpointHoldDuration(-1) }
rejected { driver.setSetpointHoldDuration(1.5) }
rejected { driver.setPreset('invalid') }
driver.zigbee.calls.clear()
driver.setPreset('manual')
assert driver.zigbee.calls[0].args[0..3] == [0xFCC0,0x0311,0x20,255]
driver.zigbee.calls.clear()
driver.settings.childLock = 'locked'
driver.preferenceCommands()
assert driver.zigbee.calls[0].args == [0xFCC0,0x0277,0x20,1,[destEndpoint:1,mfgCode:0x115F],300]
driver.zigbee.calls.clear()
driver.settings.clear()
driver.updated()
assert !driver.zigbee.calls
driver.settings.pollEnabled = false
driver.updated()
assert !driver.jobs.containsKey('poll')
driver.cool(); driver.setThermostatMode('auto'); driver.setThermostatFanMode('on')
assert !driver.zigbee.calls
driver.updateFirmware()
assert driver.zigbee.calls.last().name == 'updateFirmware'
println 'PASS: parser, Celsius/Fahrenheit, sentinels, limits, signed calibration, settings, metering, NTC reads, lifecycle preservation and manual-only OTA delegation.'
