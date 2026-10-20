import org.codehaus.groovy.control.CompilerConfiguration

// Execute the real driver with Hubitat boundary stubs; no radio or hub writes.
class DriverHarness extends Script {
    def events = [], children = [:], sent = [], data = [:]
    def state = [:], settings = [:]
    def device = [deviceNetworkId:'5C96', zigbeeId:'0011223344556677', displayName:'H2',
                  updateDataValue:{ k,v -> data[k] = v }, updateSetting:{ k,v -> settings[k] = v.value }]
    def location = [timeZone:TimeZone.getTimeZone('Europe/Sofia')]
    def log = [info:{ x -> }, debug:{ x -> }, warn:{ x -> }]
    def zigbee = new ZigbeeStub()
    def run() {}
    def metadata(Closure c) {}
    def sendEvent(Map m) { events << m }
    def getChildDevice(String id) { children[id] }
    def addChildDevice(String ns, String type, String id, Map opts) {
        children[id] = [deviceNetworkId:id, events:[]]
        children[id].parse = { values -> children[id].events.addAll(values) }
    }
    def unschedule(String s) {}
    def runIn(int n, String s) {}
    def now() { 1000000L }
    def sendHubCommand(action) { sent << action }
}
class ZigbeeStub {
    def calls = []
    def methodMissing(String name, args) { calls << [name:name, args:args.toList()]; [name] }
}
def config = new CompilerConfiguration(scriptBaseClass:DriverHarness.name)
def loader = new GroovyClassLoader(this.class.classLoader, config)
loader.parseClass('package hubitat.device; enum Protocol { ZIGBEE }')
loader.parseClass('package hubitat.device; class HubMultiAction { HubMultiAction(Object a, Object b) {} }')
def driver = loader.parseClass(new File(args ? args[0] : 'drivers/aqara/h2-eu-wall-switch/aqara-h2-eu-ws-k08e.groovy')).newInstance()
driver.run()
driver.initialize()
assert driver.children.size() == 2
driver.initialize()
assert driver.children.size() == 2
assert driver.events.find { it.name == 'numberOfButtons' }.value == 4

def report = { ep, cluster, attr, value, cmd='0A' ->
    driver.parseMap([endpoint:ep, cluster:cluster, attrId:attr, value:value, command:cmd])
}
// Captured relay reports, zero values, model string, and multiple attributes.
report('01','0000','0005','lumi.switch.agl010')
assert driver.data.model == 'lumi.switch.agl010'
report('01','0006','0000','01')
report('02','0006','0000','00')
assert driver.children['5C96-ep1'].events.last().value == 'on'
assert driver.children['5C96-ep2'].events.last().value == 'off'
driver.parseMap([endpoint:'02', clusterInt:0xFCC0, attrId:'0200', value:'01', command:'0A',
                additionalAttrs:[[attrId:'0285',value:'00'],[attrId:'0203',status:'86']]])
assert driver.events.findAll { it.name == 'rightMode' }.last().value == 'control_relay'
assert driver.events.findAll { it.name == 'rightLock' }.last().value == 'off'
assert !driver.events.find { it.name == 'ledIndicator' }

// EU gesture values and endpoint map; unknown input and readbacks create no gestures.
['01':1,'02':2,'04':3,'05':4].each { ep, button ->
    ['0000':'held','0001':'pushed','0002':'doubleTapped','00FF':'released'].each { value, action ->
        report(ep,'0012','0055',value)
        assert driver.events.last().name == action
        assert driver.events.last().value == button
        assert driver.events.last().isStateChange
    }
}
def buttonEvents = { driver.events.findAll { it.name in ['pushed','doubleTapped','held','released'] } }
int before = buttonEvents().size()
report('03','0012','0055','0001')
report('04','0012','0055','0003')
report('04','0012','0055','0001','01')
report('04','0012','0055','invalid')
assert buttonEvents().size() == before
report('04','0012','0055','0001')
report('04','0012','0055','0001')
assert buttonEvents().size() == before + 2 // repeated intentional presses preserved

// No optimistic relay event, correct destination, no dependence on bad saved endpoint 15.
driver.zigbee.calls.clear()
driver.componentOn(driver.children['5C96-ep2'])
assert driver.zigbee.calls[0].args[2].destEndpoint == 2
assert driver.children['5C96-ep2'].events.last().value == 'off'
driver.zigbee.calls.clear()
assert driver.preferenceCommands() == []
driver.settings.lowerLeftMultiClick = 'on'
driver.settings.leftMode = 'decoupled'
driver.preferenceCommands()
def writes = driver.zigbee.calls.findAll { it.name == 'writeAttribute' }
assert writes.size() == 2
assert writes.find { it.args[1] == 0x0286 }.args[3] == 2
assert writes.find { it.args[1] == 0x0286 }.args[4] == [destEndpoint:4,mfgCode:0x115F]
assert writes.find { it.args[1] == 0x0200 }.args[3] == 0

// Meter scaling is required, invalid sentinel reports ignored, float power is in watts.
report('01','0B04','050B','007B')
assert !driver.events.find { it.name == 'power' }
report('01','0B04','0604','0001')
report('01','0B04','0605','000A')
assert driver.events.findAll { it.name == 'power' }.last().value == 12.3G
report('01','0B04','050B','8000')
assert driver.state.rawPower == 123L
report('01','0702','0000','0000000004D2')
report('01','0702','0300','00')
report('01','0702','0301','000001')
report('01','0702','0302','0003E8')
assert driver.events.findAll { it.name == 'energy' }.last().value == 1.234G
report('15','000C','0055','42480000') // IEEE754 50.0 W
assert driver.events.findAll { it.name == 'power' }.last().value == 50.0f
report('01','0B04','050B','0004')
assert driver.events.findAll { it.name == 'power' }.last().value == 50.0f
report('01','0B04','0508','0000')
assert driver.events.findAll { it.name == 'amperage' }.last().value == 0
driver.settings.clear()
driver.zigbee.calls.clear()
driver.configure()
assert !driver.zigbee.calls.find { it.name == 'writeAttribute' }
assert !driver.zigbee.calls.find { it.name == 'configureReporting' && it.args[0] == 0x0012 }
assert !driver.zigbee.calls.find { it.name == 'command' }
println 'PASS: real-driver parser, child routing, EU gestures, preference encoding, meter scaling, and non-actuating configuration.'
