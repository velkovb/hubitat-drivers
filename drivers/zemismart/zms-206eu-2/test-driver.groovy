import org.codehaus.groovy.control.CompilerConfiguration

// Exercise the wire format and routing using Hubitat boundary stubs.
abstract class HubitatStub extends Script {
    def events = [], children = [:], commands = [], commandArguments = [:]
    def settings = [:]
    def location = [timeZone:TimeZone.getTimeZone('Europe/Sofia')]
    def device = [deviceNetworkId:'E686', displayName:'Zemismart']
    def log = [warn:{ message -> }]
    def zigbee = [command:{ Object... args -> commands << args.toList(); ['command'] },
                  readAttribute:{ Object... args -> commands << args.toList(); ['read'] }]
    void metadata(Closure body) { body() }
    void definition(Map details, Closure body) { body() }
    void preferences(Closure body) {}
    void capability(String name) {}
    void fingerprint(Map details) {}
    void attribute(Object... args) {}
    void command(String name, List<Map> arguments) { commandArguments[name] = arguments }
    void unschedule(String method) {}
    long now() { 1700000000000L }
    void sendEvent(Map event) { events << event }
    def getChildDevice(String dni) { children[dni] }
    void addChildDevice(String namespace, String type, String dni, Map details) {
        children[dni] = [deviceNetworkId:dni, parse:{ values -> events.addAll(values.collect { it + [child:dni] }) }]
    }
    void sendHubCommand(Object action) {}
}

def loader = new GroovyClassLoader(this.class.classLoader, new CompilerConfiguration(scriptBaseClass:HubitatStub.name))
loader.parseClass('package hubitat.device; enum Protocol { ZIGBEE }')
loader.parseClass('package hubitat.device; class HubMultiAction { HubMultiAction(Object commands, Object protocol) {} }')
def driver = loader.parseClass(new File(args ? args[0] : 'zms-206eu-2/zemismart-zms-206eu-2.groovy')).newInstance()
driver.run()
def colorChoices = ['red','blue','green','white','yellow','magenta','cyan','warm_white','warm_yellow']
['setSwitchColorOn','setSwitchColorOff','setDelayOffColor'].each { name ->
    assert driver.commandArguments[name][0].type == 'ENUM'
    assert driver.commandArguments[name][0].constraints == colorChoices
}
assert driver.commandArguments['setBacklightBrightness'][0].type == 'NUMBER'
assert driver.commandArguments['setScreenName2'][0].type == 'STRING'
driver.setSwitchColorOn('magenta')
assert driver.commands.last().last() == '01006704000105'
driver.setBacklightBrightness(30.0G)
assert driver.commands.last().last() == '0100660200040000001E'
driver.commands.clear()
driver.configure()
assert driver.commands[0] == [0, [4, 0, 1, 5, 7, 0xFFFE], [destEndpoint:1], 300]
assert driver.commands[1] == [0xEF00, 3, [destEndpoint:1], 200]
driver.updated()
assert driver.children.keySet() == ['E686-relay1', 'E686-relay2'] as Set
driver.events.clear()

driver.componentOn(driver.children['E686-relay2'])
assert driver.commands.last() == [0xEF00, 0, [destEndpoint:1], 100, '01000201000101']
assert driver.events.empty
driver.off()
assert driver.commands.last().last() == '01000D01000100'
driver.setOption('countdown2', '43200')
assert driver.commands.last().last() == '0100080200040000A8C0'
driver.setOption('switchColorOff', 'warm_yellow')
assert driver.commands.last().last() == '01006804000108'
driver.setOption('screenName1', 'Лампа')
assert driver.commands.last().last().equalsIgnoreCase('01006900000AD09BD0B0D0BCD0BFD0B0')
driver.setOption('screenName2', '12345678901😀')
assert driver.commands.last().last() == '01006A00000B3132333435363738393031'
int writes = driver.commands.size()
driver.setOption('countdown2', '43201')
driver.setOption('childLock', 'invalid')
assert driver.commands.size() == writes

def receive = { String command, String payload ->
    driver.parseMap([clusterInt:0xEF00, command:command, data:payload.tokenize(' ')])
}
receive('0A', '01 00 02 01 00 01 01 01 01 00 01 00 0D 01 00 01 01 66 02 00 04 00 00 00 64 69 00 00 02 D0 9B')
assert driver.events.findAll { it.child } == [[name:'switch', value:'on', child:'E686-relay2'], [name:'switch', value:'off', child:'E686-relay1']]
assert driver.events.find { it.name == 'switch' && !it.child }.value == 'on'
assert driver.events.find { it.name == 'backlightBrightness' }.value == 100
assert driver.events.find { it.name == 'screenName1' }.value == 'Л'
int eventCount = driver.events.size()
receive('02', '01 00 02 01 00 02 00') // Truncated.
receive('02', '01 00 02 04 00 01 00') // Wrong datatype.
assert driver.events.size() == eventCount

receive('24', '00 00')
assert driver.commands.last().last() == '08006553F10065540D20'
receive('25', '00 00')
assert driver.commands.last().last() == '010001'
println 'ZMS-206EU-2 command and report checks passed'
