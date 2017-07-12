/**
 *  Insteon IO LInc
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
    input("host", "text", title: "URL", description: "The URL of your Hub (without http:// example: my.hub.com ")
    input("port", "text", title: "Port", description: "The hub port.")
    input("username", "text", title: "Username", description: "The hub username (found in app)")
    input("password", "text", title: "Password", description: "The hub password (found in app)")
} 
 
metadata {
    definition (name: "Insteon IOLinc Garage Door", namespace: "kuestess", author: "kuestess") {
		capability "Actuator"
		capability "Door Control"
        capability "Garage Door Control"
		capability "Contact Sensor"
		capability "Refresh"
		capability "Sensor"
	}

    // UI tile definitions
	tiles {
		standardTile("toggle", "device.door", width: 2, height: 2) {
			state("closed", label:'${name}', action:"door control.open", icon:"st.doors.garage.garage-closed", backgroundColor:"#00A0DC", nextState:"opening")
			state("open", label:'${name}', action:"door control.close", icon:"st.doors.garage.garage-open", backgroundColor:"#e86d13", nextState:"closing")
			state("opening", label:'${name}', icon:"st.doors.garage.garage-closed", backgroundColor:"#e86d13")
			state("closing", label:'${name}', icon:"st.doors.garage.garage-open", backgroundColor:"#00A0DC")
			
		}
		standardTile("open", "device.door", inactiveLabel: false, decoration: "flat") {
			state "default", label:'open', action:"door control.open", icon:"st.doors.garage.garage-opening"
		}
		standardTile("close", "device.door", inactiveLabel: false, decoration: "flat") {
			state "default", label:'close', action:"door control.close", icon:"st.doors.garage.garage-closing"
		}
		standardTile("refresh", "device.door", width: 1, height: 1, inactiveLabel: false, decoration: "flat") {
            state "default", label:'', action:"refresh.refresh", icon:"st.secondary.refresh"
        }

		main "toggle"
		details(["toggle", "open", "close","refresh"])
	}
}

// Not in use
def parse(String description) {
}

def on() {
    log.debug "Turning device ON"
    sendCmd("11", "FF")
    sendEvent(name: "switch", value: "on");
}

def off() {
    log.debug "Turning device OFF"
    sendCmd("13", "00")
    sendEvent(name: "switch", value: "off");
}

def sendCmd(num, level)
{
    log.debug "Sending Command"

    httpGet("http://${settings.username}:${settings.password}@${settings.host}:${settings.port}//3?0262${settings.deviceid}0F${num}${level}=I=3") {response -> 
        def content = response.data
        // log.debug content
    }
    log.debug "Command Completed"
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
            log.debug "Sending open event..."
            finishOpening()
            }

        else if (status == "1") {
            log.debug "Sending close event..."
            finishClosing()
        }
    } else {
    	log.debug "Response is for wrong device - trying again"
        clearBuffer()
        runIn(2,getStatus)
    }
}

def open() {
	on()
    sendEvent(name: "door", value: "opening")
    runIn(20, getStatus)
}

def close() {
    on()
    sendEvent(name: "door", value: "closing")
	runIn(20, getStatus)
}

def finishOpening() {
    sendEvent(name: "door", value: "open")
    sendEvent(name: "contact", value: "open")
}

def finishClosing() {
    sendEvent(name: "door", value: "closed")
    sendEvent(name: "contact", value: "closed")
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
