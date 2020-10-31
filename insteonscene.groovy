/**
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
    input("group", "text", title: "Group number", description: "Scene group number from the Insteon app")
    input("host", "text", title: "URL", description: "The URL of your Hub (without http:// example: my.hub.com ")
    input("port", "text", title: "Port", description: "The hub port.")
    input("username", "text", title: "Username", description: "The hub username (found in app)")
    input("password", "text", title: "Password", description: "The hub password (found in app)")
}

metadata {
    definition (name: "Insteon Local Scene", author: "kuestess", oauth: true) {
        capability "Switch"
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

// Not in use
def parse(String description) {
}

def on() {
    log.debug "Turning scene ON"
    sendCmd("11", "$settings.group")
    sendEvent(name: "switch", value: "on");
    sendEvent(name: "level", value: 100, unit: "%")
}

def off() {
    log.debug "Turning scene OFF"
    sendCmd("13", "$settings.group")
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

def sendCmd(num, group)
{
    log.debug "Sending Command"

    def userpasstext = "$settings.username:$settings.password"
    def userpass = userpasstext.encodeAsBase64().toString()

    sendHubCommand(new physicalgraph.device.HubAction("""GET /0?${num}${group}=I=0 HTTP/1.1\r\nHOST: ${settings.host}:${settings.port}\r\nAuthorization: Basic ${userpass}\r\n\r\n""", physicalgraph.device.Protocol.LAN, "${deviceNetworkId}"))

    log.debug "Command Completed"
}

def refresh()
{
    log.debug "Refreshing.."

}

def poll()
{
    log.debug "Polling.."
}

def ping()
{
    log.debug "Pinging.."
    //poll()
}

def initialize(){
    //poll()
}
