/**
 *  Camera Motion Capture
 *
 *  2020 mbarone
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 *  To use the video camera motion service, you must install and configure this on a local webserver
 *  https://github.com/michaelbarone/CameraMotionCapture
 *
 *  Change History:
 *
 *    Date        Who            What
 *    ----        ---            ----
 * 	 11-4-20	mbarone			initial release 
 */
 
 
def setVersion(){
    state.name = "Camera Motion Capture"
	state.version = "0.0.1"
} 
 
metadata {
	definition (name: "Camera Motion Capture", namespace: "mbarone", author: "mbarone", importUrl: "https://raw.githubusercontent.com/michaelbarone/hubitat/master/drivers/cameraMotionCapture.groovy") {
		capability "Actuator"
	}

    preferences {
		input name: "webServerURL", type: "text", title: "Web Server URL", description: "Full path to the CameraMotionCapture webapp", required: true
        input name: "captureCount", type: "number", title: "Capture Frame Count", defaultValue: 5, description: "How many images do you want to save for each motion event"
        input name: "captureDelay", type: "number", title: "Delay Between Frames", defaultValue: 3, description: "How many seconds between image captures for each motion event"
		input name: "username", type: "text", title: "Username", description: "Username if cameras require authentication"
		input name: "password", type: "text", title: "Password", description: "Password if cameras require authentication"
		input name: "daysToKeepEvents", type: "number", title: "Days to Keep Events", defaultValue: 10, description: "How many days do you want events to be saved for.  To disable, set to 0"
        input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
	}

	attribute "Details","string"
	attribute "iFrame", "text"
	
	command "addCamera", [[name:"Camera Name*",type:"STRING",description:"This Cannot Be changed without removing and re-adding the camera"]]
	command "clearOldEvents", [[name:"daysToKeep*",type:"NUMBER"]]
}

def logsOff(){
    log.warn "debug logging disabled..."
    device.updateSetting("logEnable",[value:"false",type:"bool"])
}

def installed() {
}

def updated() {
	// cancel schedule
	unschedule()
	// set schedule to clearOldEvents
	if(daysToKeepEvents>0){
		schedule("0 5 0 1/1 * ? *", clearOldEvents)
	}
	
	sendEvent(name: "iFrame", value: "<div style='height: 100%; width: 100%'><iframe src='${webServerURL}' style='height: 100%; width:100%; border: none;'></iframe><div>")
	clearDetails()
	if (logEnable) {
		log.warn "debug logging enabled..."
		runIn(1800,logsOff)
	}
}

def clearDetails(){
	sendEvent(name:"Details", value:"Running Normally.")
}

def getWebServerURL(){
	return "${webServerURL}"
}

def addCamera(camera){
    if (logEnable) log.debug "Creating Child Device "+camera
	
	foundChildDevice = null
	foundChildDevice = getChildDevice("${device.deviceNetworkId}-${camera}")

	if(foundChildDevice=="" || foundChildDevice==null){

		if (logEnable) log.debug "createChildDevice:  Creating Child Device 'CMC - ${camera}'"
		try {
			def deviceHandlerName = "Camera Motion Capture Child"
			addChildDevice(deviceHandlerName,
							"${device.deviceNetworkId}-${camera}",
							[
								completedSetup: true, 
								label: "CMC - ${camera}", 
								isComponent: false, 
								name: "CMC - ${camera}",
							]
						)
			sendEvent(name:"Details", value:"Child device created!  Refresh this page.")
			unschedule(clearDetails)
			runIn(300,clearDetails)
			getChildDevice("${device.deviceNetworkId}-${camera}").updateDataValue("cameraName",camera);
		}
		catch (e) {
			log.error "Child device creation failed with error = ${e}"
			sendEvent(name:"Details", value:"Child device creation failed. Please make sure that the '${deviceHandlerName}' is installed and published.", displayed: true)
		}
	} else {
		if (logEnable) log.debug "createChildDevice: Child Device 'CMC - ${camera}' found! Skipping"
	}
}

def captureEvent(cameraName,cameraURL,cCount=captureCount,cDelay=captureDelay,uname=username,pword=password){
	def params = [uri: "${webServerURL}/motionCapture.php",contentType: "application/x-www-form-urlencoded"]
	params['body'] = ["cameraName":cameraName,
						"cameraUrl":cameraURL,
						"captureCount":cCount,
						"captureDelay":cDelay
					]
					
	if(uname && uname != ""){
    	params['body'].put("username", uname)
    }
	if(pword && pword != ""){
    	params['body'].put("password", pword)
    }
	if (logEnable) log.debug "attempting post:"
	if (logEnable) log.debug params
	try {
		httpPost(params) {}
	} catch (e) {
		log.error "something went wrong on captureEvent: $e"
	}
}

def clearOldEvents(daysToKeep=daysToKeepEvents, camera=null){
	def params = [uri: "${webServerURL}/clearOldEvents.php",contentType: "application/x-www-form-urlencoded"]
	params['body'] = ["daysToKeep":daysToKeep]
	
	if(camera!=null && camera!=""){
		params['body'].put("cameraName", camera)
	}
	
	if (logEnable) log.debug "attempting post:"
	if (logEnable) log.debug params
	try {
		httpPost(params) {}
	} catch (e) {
		log.error "something went wrong on clearOldEvents: $e"
	}
}