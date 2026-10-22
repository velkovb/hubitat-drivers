import org.codehaus.groovy.control.CompilerConfiguration

class OutletHarness extends Script {
    def events=[], state=[:], settings=[:], data=[:], values=[:]
    def device=[deviceNetworkId:'1234',displayName:'Test outlet',
        updateDataValue:{k,v -> data[k]=v}, getDataValue:{k -> data[k]}, currentValue:{k -> values[k]}]
    def log=[info:{s ->},warn:{s ->},debug:{s ->}]
    def zigbee=new OutletZigbeeStub()
    def propertyMissing(String key) { settings[key] }
    def run() {}
    def metadata(Closure c) {}
    def sendEvent(Map m) { events << m; values[m.name]=m.value }
}
class OutletZigbeeStub {
    def calls=[], incoming=[:]
    def parseDescriptionAsMap(String s) { incoming }
    def methodMissing(String name, args) { calls << [name:name,args:args.toList()]; [name] }
}
def config=new CompilerConfiguration(scriptBaseClass:OutletHarness.name)
def loader=new GroovyClassLoader(this.class.classLoader, config)
def driver=loader.parseClass(new File(args ? args[0] : 'outlet-firmware/aqara-h2-wall-outlet.groovy')).newInstance()
driver.run()
def report={Map m -> driver.zigbee.incoming=m; driver.parse('test')}
report([endpoint:'01',clusterInt:0,attrInt:4,value:'LUMI',additionalAttrs:[
    [attrInt:5,value:'lumi.plug.aeu001'],[attrInt:3,value:'01'],[attrInt:1,value:'1D'],
    [attrInt:6,value:'20260109'],[attrInt:0x4000,status:'86']]])
assert driver.data.model=='lumi.plug.aeu001'
assert driver.data.manufacturer=='LUMI'
assert driver.data.hardwareVersion=='01'
assert driver.data.dateCode=='20260109'
assert driver.data.application=='1D'
assert !driver.data.containsKey('softwareBuild')
report([endpoint:'01',clusterInt:0xFCC0,attrInt:0x00EE,value:'00000021'])
assert driver.values.firmwareFileVersion==33L
assert driver.data.firmwareFileVersionHex=='0x00000021'
assert driver.values.firmwareVersionStatus=='Reported by Aqara FCC0/00EE'
report([endpoint:'01',clusterInt:0xFCC0,attrInt:0x00EE,value:'FFFFFFFF'])
assert driver.values.firmwareFileVersion==33L
report([endpoint:'01',clusterInt:0xFCC0,attrInt:0x00EE,value:'00000000'])
assert driver.values.firmwareFileVersion==0L
assert driver.values.firmwareVersionStatus=='Unconfirmed: Aqara field returned zero'
report([endpoint:'02',clusterInt:0,attrInt:5,value:'other-model'])
assert driver.data.model=='lumi.plug.aeu001'
report([endpoint:'01',clusterInt:0x0006,attrInt:0,value:'01'])
assert driver.values.switch=='on'
report([endpoint:'15',clusterInt:0x000C,attrInt:0x0055,encoding:'39',value:'40BF0A3D'])
assert Math.abs(driver.values.power-5.97d)<0.001
driver.zigbee.calls.clear()
def cmds=driver.readFirmwareInfo()
assert cmds.findAll { it.startsWith('he rattr') }.size()==6
assert driver.zigbee.calls.every { it.name=='readAttribute' }
assert driver.zigbee.calls[0].args[2].destEndpoint==1
assert !cmds.find { it.contains('0x0019') || it.contains('wattr') || it.contains('he cmd') }
driver.data.model='wrong-model'
driver.zigbee.calls.clear()
assert driver.updateFirmware()==[]
assert driver.zigbee.calls.empty
driver.data.model='lumi.plug.aeu001'
driver.updateFirmware()
assert driver.zigbee.calls*.name==['updateFirmware']
assert driver.zigbee.calls[0].args==[[manufacturer:0x115F]]
assert driver.on()[0]=='he cmd 0x1234 0x01 0x0006 0x01 {}'
assert driver.off()[0]=='he cmd 0x1234 0x01 0x0006 0x00 {}'
println 'PASS: outlet firmware parser, unsupported/sentinel handling, read-only probes, model guard, native OTA delegation, relay routing and existing float metering.'
