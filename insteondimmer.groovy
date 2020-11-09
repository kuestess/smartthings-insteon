/*
 *  Insteon Dimmer Switch
 *  Original Author     : ethomasii@gmail.com
 *  Creation Date       : 2013-12-08
 *
 *  Rewritten by        : idealerror
 *  Last Modified Date  : 2016-12-13
 *
 *  Rewritten by        : kuestess
 *  Last Modified Date  : 2020-10-31
 *
 *  Disclaimer about 3rd party server: No longer uses third-party server :)
 *
 *  Changelog:
 *
 *  2020-10-31: Update to use hubAction
 *  2017-12-30: Corrected getStatus command2 to be 00 [jens@ratsey.com]
 *  2016-12-13: Added polling for Hub2
 *  2016-12-13: Added background refreshing every 3 minutes
 *  2016-11-21: Added refresh/polling functionality
 *  2016-10-15: Added full dimming functions
 *  2016-10-01: Redesigned interface tiles
 */

import groovy.json.JsonSlurper

preferences {
    input("deviceid", "text", title: "Device ID", description: "Your Insteon device.  Do not include periods example: FF1122.")
    input("host", "text", title: "URL", description: "The URL of your Hub (without http:// example: my.hub.com ")
    input("port", "text", title: "Port", description: "The hub port.")
    input("username", "text", title: "Username", description: "The hub username (found in app)")
    input("password", "text", title: "Password", description: "The hub password (found in app)")
    input("poll_frequency", "number", title: "Poll frequency", description: "Device status polling in minutes (0 to disable)", defaultValue: 3)
}

metadata {
    definition (name: "Insteon Dimmer Switch or Plug", author: "kuestess", oauth: true) {
        capability "Switch Level"
        capability "Polling"
        capability "Switch"
        capability "Refresh"
    }

    // simulator metadata
    simulator {
    }

    // UI tile definitions
    tiles(scale:2) {
       multiAttributeTile(name:"switch", type: "lighting", width: 6, height: 4, canChangeIcon: true){
            tileAttribute ("device.switch", key: "PRIMARY_CONTROL") {
                attributeState "on", label:'${name}', action:"switch.off", icon:"st.switches.switch.on", backgroundColor:"#79b821", nextState:"turningOff"
                attributeState "off", label:'${name}', action:"switch.on", icon:"st.switches.switch.off", backgroundColor:"#ffffff", nextState:"turningOn"
                attributeState "turningOn", label:'${name}', action:"switch.off", icon:"st.switches.switch.on", backgroundColor:"#79b821", nextState:"turningOff"
                attributeState "turningOff", label:'${name}', action:"switch.on", icon:"st.switches.switch.off", backgroundColor:"#ffffff", nextState:"turningOn"
            }

           tileAttribute ("device.level", key: "SLIDER_CONTROL") {
               attributeState "level", action:"switch level.setLevel"
           }

        }

        standardTile("refresh", "device.switch", width: 2, height: 2, inactiveLabel: false, decoration: "flat") {
            state "default", label:'', action:"refresh.refresh", icon:"st.secondary.refresh"
        }

        valueTile("level", "device.level", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
            state "level", label:'${currentValue} %', unit:"%", backgroundColor:"#ffffff"
        }

        main(["switch"])
        details(["switch", "level", "refresh"])
    }
}

def on() {
    log.debug "Turning device ON"
    sendCmd("11", "FF")
    sendEvent(name: "switch", value: "on");
    sendEvent(name: "level", value: 100, unit: "%")
}

def off() {
    log.debug "Turning device OFF"
    sendCmd("13", "00")
    sendEvent(name: "switch", value: "off");
    sendEvent(name: "level", value: 0, unit: "%")
}

def setLevel(value) {

    // log.debug "setLevel >> value: $value"

    // Max is 255
    def percent = value / 100
    def realval = percent * 255
    def valueaux = realval as Integer
    def level = Math.max(Math.min(valueaux, 255), 0)
    if (level > 0) {
        sendEvent(name: "switch", value: "on")
    } else {
        sendEvent(name: "switch", value: "off")
    }
    // log.debug "dimming value is $valueaux"
    log.debug "dimming to $level"
    dim(level,value)
}

def dim(level, real) {
    String hexlevel = level.toString().format( '%02x', level.toInteger() )
    // log.debug "Dimming to hex $hexlevel"
    sendCmd("11",hexlevel)
    sendEvent(name: "level", value: real, unit: "%")
}

def sendCmd(num, level)
{
    log.debug "Sending Command"

    def userpasstext = "$settings.username:$settings.password"
    def userpass = userpasstext.encodeAsBase64().toString()

    sendHubCommand(new physicalgraph.device.HubAction("""GET 3?0262${settings.deviceid}0F${num}${level}=I=3 HTTP/1.1\r\nHOST: ${settings.host}:${settings.port}\r\nAuthorization: Basic ${userpass}\r\n\r\n""", physicalgraph.device.Protocol.LAN, "${deviceNetworkId}"))

    log.debug "Command Completed"
}

def refresh()
{
    log.debug "Refreshing.."
    poll()
}

def poll()
{
    getStatus()

    def polltimer = getPollTimer().toInteger()

    if(polltimer != 0){
    	log.debug "Polling.."

        def randomness = Math.abs(new Random().nextInt()) % 60 + 10
        log.debug "Random: $randomness"

        runIn((60*polltimer)+randomness, refresh)
    } else {
    	//do nothing
        //log.debug "Polling disabled"
    }
}

def initialize(){
    poll()
}

def getStatus() {

    def userpasstext = "$settings.username:$settings.password"
    def userpass = userpasstext.encodeAsBase64().toString()

    sendHubCommand(new physicalgraph.device.HubAction("""GET 3?0262${settings.deviceid}0F1900=I=3 HTTP/1.1\r\nHOST: ${settings.host}:${settings.port}\r\nAuthorization: Basic ${userpass}\r\n\r\n""", physicalgraph.device.Protocol.LAN, "${deviceNetworkId}"))

    def buffer_status = runIn(2, getBufferStatus)
}

def getBufferStatus() {
	def buffer = ""

    def userpasstext = "$settings.username:$settings.password"
    def userpass = userpasstext.encodeAsBase64().toString()

    sendHubCommand(new physicalgraph.device.HubAction("""GET /buffstatus.xml HTTP/1.1\r\nHOST: ${settings.host}:${settings.port}\r\nAuthorization: Basic ${userpass}\r\n\r\n""", physicalgraph.device.Protocol.LAN, "${deviceNetworkId}", [callback:parseBuffer]))
}

def parseBuffer(response){
	def buffer = ""

    def body = new XmlSlurper().parseText("$response.body")
    buffer = body.BS.toString()

    log.debug "Buffer: ${buffer}"

    def buffer_end = buffer.substring(buffer.length()-2,buffer.length())
	def buffer_end_int = Integer.parseInt(buffer_end, 16)

    def parsed_buffer = buffer.substring(0,buffer_end_int)
    log.debug "ParsedBuffer: ${parsed_buffer}"

    def responseID = parsed_buffer.substring(22,28)

    if (responseID == settings.deviceid) {
        log.debug "Response is for correct device: ${responseID}"
        def status = parsed_buffer.substring(38,40)
        log.debug "Status: ${status}"

        def level = Math.round(Integer.parseInt(status, 16)*(100/255))
        log.debug "Level: ${level}"

        if (level == 0) {
            log.debug "Device is off..."
            sendEvent(name: "switch", value: "off")
            sendEvent(name: "level", value: level, unit: "%")
            }

        else if (level > 0) {
            log.debug "Device is on..."
            sendEvent(name: "switch", value: "on")
            sendEvent(name: "level", value: level, unit: "%")
        }
    } else {
    	log.debug "Response is for wrong device - trying again"
        getStatus()
    }
}

def getPollTimer(){
    if (settings.poll_frequency == null) {
    	settings.poll_frequency = 3
    }

    def polltimer = settings.poll_frequency as Integer
	return polltimer
}