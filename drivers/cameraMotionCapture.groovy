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
 * 	 20-11-4	mbarone			initial release 
 * 	 20-11-6	mbarone			added lastMotion attributes and versionCheck for webserver version 
 * 	 20-11-25	mbarone			added 1s timeout to httpPost so hubitat doesnt hang while processing all camera snapshots
 * 	 21-03-17	mbarone			updated schedule functions and updated server code for CMC which adds some new features
 */
 
 
def setVersion(){
    state.name = "Camera Motion Capture"
	state.version = "0.0.4"
} 
 
metadata {
	definition (name: "Camera Motion Capture", namespace: "mbarone", author: "mbarone", importUrl: "https://raw.githubusercontent.com/michaelbarone/hubitat/master/drivers/cameraMotionCapture.groovy") {
		capability "Actuator"
	}

    preferences {
		input name: "webServerURL", type: "text", title: "Web Server URL", description: "Full path to the CameraMotionCapture webapp. ie:  http://webserverIP/CameraMotionCapture/", required: true
        input name: "captureCount", type: "number", title: "Capture Frame Count", defaultValue: 5, description: "How many images do you want to save for each motion event"
        input name: "captureDelay", type: "number", title: "Delay Between Frames", defaultValue: 2, description: "How many seconds between image captures for each motion event"
		input name: "username", type: "text", title: "Username", description: "Username if cameras require authentication"
		input name: "password", type: "text", title: "Password", description: "Password if cameras require authentication"
		input name: "daysToKeepEvents", type: "number", title: "Days to Keep Events", defaultValue: 10, description: "How many days do you want events to be saved for.  To disable, set to 0"
        input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
	}

	attribute "Details","string"
	attribute "lastMotionDevice","string"
	attribute "lastMotionTime","string"
	attribute "iFrame", "text"
	
	command "addCamera", [[name:"Camera Name*",type:"STRING",description:"This Cannot Be changed without removing and re-adding the camera"]]
	command "clearOldEvents", [[name:"daysToKeep*",type:"NUMBER"]]
	command "checkForWebserverUpdates"
}

def logsOff(){
    log.warn "debug logging disabled..."
    device.updateSetting("logEnable",[value:"false",type:"bool"])
}

def installed() {
}

def updated() {
	setSchedules()
	// run check now for webserver updates
	checkForWebserverUpdates()	
	sendEvent(name: "iFrame", value: "<div style='height: 100%; width: 100%'><iframe src='${webServerURL}' style='height: 100%; width:100%; border: none;'></iframe><div>")
	clearDetails()
	if (logEnable) {
		log.warn "debug logging enabled..."
		runIn(1800,logsOff)
	}
}

def initialize(){
	setSchedules()
}

def setSchedules() {
	// cancel schedules
	unschedule()
	// set schedule to clearOldEvents
	if(daysToKeepEvents>0){
		schedule("0 5 0 1/1 * ? *", clearOldEvents)
	}
	// set schedule to check webserver for updates
	schedule("0 22 1/12 ? * * *", checkForWebserverUpdates)
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

def captureEvent(cameraName,cameraURL,cCount=null,cDelay=null,uname=null,pword=null){

	sendEvent(name:"lastMotionDevice", value:cameraName)
	sendEvent(name:"lastMotionTime", value:new Date())

	def str = webServerURL
    if (str != null && str.length() > 0 && str.charAt(str.length() - 1) == '/') {
        str = str.substring(0, str.length() - 1);
    }
	def params = [uri: "${str}/data/motionCapture.php",
					contentType: "application/x-www-form-urlencoded",
					timeout: 1
				]
	params['body'] = ["cameraName":cameraName,
						"cameraUrl":cameraURL
					]

 	if(cCount && cCount != "" && cCount != null){
    	params['body'].put("captureCount", cCount)
    }
    if(cCount == null && (captureCount != null && captureCount != "")){
        params['body'].put("captureCount", captureCount)
    }
	if(cDelay && cDelay != "" && cDelay != null){
    	params['body'].put("captureDelay", cDelay)
    }
    if(cDelay == null && (captureDelay != null && captureDelay != "")){
        params['body'].put("captureDelay", captureDelay)
    }    
	if(uname && uname != "" && uname != null){
    	params['body'].put("username", uname)
    }
	if(uname == null && (username != null && username != "")){
    	params['body'].put("username", username)
    }    
	if(pword && pword != "" && pword != null){
    	params['body'].put("password", pword)
    }
	if(pword == null && (password != null && password != "")){
    	params['body'].put("password", password)
    }     
	if (logEnable) log.debug "attempting post:"
	if (logEnable) log.debug params
	try {
		httpPost(params) {}
		//asynchttpPost(null, params)
	} catch (e) {
		// due to long execution time of motionCapture, this post will timeout and always give an error.  to troubleshoot, uncomment the below log.error
		//log.error "something went wrong on captureEvent: $e"
	}
}

def clearOldEvents(daysToKeep=daysToKeepEvents, camera=null){
	def str = webServerURL
    if (str != null && str.length() > 0 && str.charAt(str.length() - 1) == '/') {
        str = str.substring(0, str.length() - 1);
    }
	def params = [uri: "${str}/data/clearOldEvents.php",
					contentType: "application/x-www-form-urlencoded",
					timeout: 1
				]
	params['body'] = ["daysToKeep":daysToKeep]
	
	if(camera!=null && camera!=""){
		params['body'].put("cameraName", camera)
	}
	
	if (logEnable) log.debug "attempting post:"
	if (logEnable) log.debug params
	try {
		httpPost(params) {}
		//asynchttpPost('postCallback', params)
	} catch (e) {
		//log.error "something went wrong on clearOldEvents: $e"
	}
}

def postCallback(response, data) {
    if (logEnable) log.debug "status of post call is: ${response.status}"
}

def checkForWebserverUpdates(){
	def str = webServerURL
    if (str != null && str.length() > 0 && str.charAt(str.length() - 1) == '/') {
        str = str.substring(0, str.length() - 1);
    }
	if (logEnable) log.debug "attempting Version Get request:"
	def url = "${str}/data/versionCheck.php";
	try {
		httpGet(url) { resp ->
            if (resp.data){
                if (logEnable) log.debug "Version Check = ${resp.data} - last check: "+new Date()
                state.webserver = "${resp.data} - last check: "+new Date()
            } else {
				state.webserver = "Could not check webserver version"
			}
		}
	} catch (e) {
		log.error "something went wrong on checkForWebserverUpdates: $e"
	}
}