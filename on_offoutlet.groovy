/**
 *  Insteon On/Off Outlet
 *  Original Author     : ethomasii@gmail.com
 *  Creation Date       : 2013-12-08
 *
 *  Rewritten by        : idealerror
 *  Last Modified Date  : 2016-12-13 
 *
 *  Rewritten by        : kuestess
 *  Last Modified Date  : 2017-04-24 
 *  
 *  Disclaimer about 3rd party server:
 * 
 *  The refresh function of this device type currently 
 *  calls out to my 3rd party server to contact your hub
 *  to determine the status of your device.  The reason
 *  for this is SmartThings cannot parse the XML that 
 *  the hub gives us.  It's malformed.  If you're
 *  uncomfortable with my server contacting your hub 
 *  directly, you should not use this device type.
 *  I do not store or log any information related to 
 *  this device type.
 * 
 *  Changelog:
 * 
 *  2016-12-13: Added background refreshing every 3 minutes
 *  2016-11-21: Added refresh/polling functionality
 *  2016-10-15: Added full dimming functions
 *  2016-10-01: Redesigned interface tiles
 */
 
import groovy.json.JsonSlurper

 
preferences {
    input("deviceid", "text", title: "Device ID", description: "Your Insteon device.  Do not include periods example: FF1122.")
    input("position", "enum", title: "Outlet position:", description: "Select top or bottom outlet", options: ["top","bottom"])
    input("host", "text", title: "URL", description: "The URL of your Hub (without http:// example: my.hub.com ")
    input("port", "text", title: "Port", description: "The hub port.")
    input("username", "text", title: "Username", description: "The hub username (found in app)")
    input("password", "text", title: "Password", description: "The hub password (found in app)")
} 
 
metadata {
    definition (name: "Insteon On/Off Outlet", namespace: "kuestess", author: "kuestess") {
		capability "Actuator"
		capability "Switch"
        capability "Polling"		
		capability "Refresh"
		capability "Sensor"
        
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
        }
        
        standardTile("refresh", "device.switch", width: 2, height: 2, inactiveLabel: false, decoration: "flat") {
            state "default", label:'', action:"refresh.refresh", icon:"st.secondary.refresh"
        }

        main(["switch"])
        details(["switch", "level", "refresh"])
    }
}

// Not in use
def parse(String description) {
}

def on() {
    log.debug "Turning device ON"
    log.debug "Position: $settings.position"
        
    if ("$settings.position" == "top") {
    	sendCmd("11", "FF")
    } else {
    	sendExtendedCmd("11", "FF")
        }
 
    sendEvent(name: "switch", value: "on");
}

def off() {
    log.debug "Turning device OFF"
    
    if ("$settings.position" == "top") {
    	sendCmd("13", "00")
    } else {
    	sendExtendedCmd("13", "00")
    	}
    
    sendEvent(name: "switch", value: "off");
}

def sendCmd(num, level)
{
    log.debug "Sending command"

    httpGet("http://${settings.username}:${settings.password}@${settings.host}:${settings.port}//3?0262${settings.deviceid}0F${num}${level}=I=3") {response -> 
        def content = response.data
        // log.debug content
    }
    log.debug "Command Completed"
}

def sendExtendedCmd(num, level)
{
    log.debug "Sending extended command"
    def extended
    
    if (num == "11") {
        extended = "02000000000000000000000000EC"
        } 
    else if (num == "13") {
        extended = "02000000000000000000000000EB"
    }
    
    httpGet("http://${settings.username}:${settings.password}@${settings.host}:${settings.port}//3?0262${settings.deviceid}1F${num}${level}${extended}=I=3") {response -> 
        def content = response.data
    }
    log.debug "Extended command completed"
}

def refresh()
{
    log.debug "Refreshing.."
    poll()
}

def poll()
{
    log.debug "Polling.."
    getStatus()
    runIn(180, refresh)
}

def initialize(){
    poll()
}

def getStatus() {

    def myURL = [
    	uri: "http://${settings.username}:${settings.password}@${settings.host}:${settings.port}/3?0262${settings.deviceid}0F1901=I=3"
    ]
    
    log.debug myURL
    httpPost(myURL)
	
    def buffer_status = runIn(2, getBufferStatus)
}

def getBufferStatus() {
	def buffer = ""
	def params = [
        uri: "http://${settings.username}:${settings.password}@${settings.host}:${settings.port}/buffstatus.xml"
    ]
    
    try {
        httpPost(params) {resp ->
            buffer = "${resp.responseData}"
            log.debug "Buffer: ${resp.responseData}"
        }
    } catch (e) {
        log.error "something went wrong: $e"
    }

	def buffer_end = buffer.substring(buffer.length()-2,buffer.length())
	def buffer_end_int = Integer.parseInt(buffer_end, 16)
    
    def parsed_buffer = buffer.substring(0,buffer_end_int)
    log.debug "ParsedBuffer: ${parsed_buffer}"
    
    def responseID = parsed_buffer.substring(22,28)
    
    if (responseID == settings.deviceid) {
        log.debug "Response is for correct device: ${responseID}"
        def status = parsed_buffer.substring(39,40)
        log.debug "Status: ${status}"

        if (status == "0") {
            log.debug "Both outlets off..."
            sendEvent(name: "switch", value: "off")
            }
        else if (status == "1") {
            log.debug "Top outlet on..."
            if ("$settings.position" == "top") {
            	sendEvent(name: "switch", value: "on")
                } else {sendEvent(name: "switch", value: "off")}
        }
        else if (status == "2") {
            log.debug "Bottom outlet on..."
            if ("$settings.position" == "top") {
            	sendEvent(name: "switch", value: "off")
                } else {sendEvent(name: "switch", value: "on")}
        }
        else if (status == "3") {
            log.debug "Both outlets on..."
            sendEvent(name: "switch", value: "on")
        }
    } else {
    	log.debug "Response is for wrong device - trying again"
        clearBuffer()
        runIn(2,getStatus)
    }
}

def clearBuffer() {
    log.debug "Clearing buffer..."
    def params = [
        uri: "http://${settings.username}:${settings.password}@${settings.host}:${settings.port}/1?XB=M=1"
    ]
    
    try {
        httpPost(params) {resp -> 
        	log.debug "Buffer: ${resp}"
        }
    } catch (e) {
        log.error "something went wrong: $e"
    }
}
