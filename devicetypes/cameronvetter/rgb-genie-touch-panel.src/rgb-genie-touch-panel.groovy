metadata {
	definition (name: "Rgb Genie Touch Panel", namespace: "cameronvetter", author: "Cameron Vetter") {
		capability "Sensor"
		capability "Actuator"

		fingerprint mfr:"0330", prod:"0301", model: "A106"
	}

	simulator {
		status "scene1":  "command: 9881, payload: 00 5B 03 04 83 01"
		status "scene2": "command: 9881, payload: 00 5B 03 06 83 02"
		status "scene3": "command: 9881, payload: 00 5B 03 07 83 03"
	}

	preferences {
	}

	tiles(scale: 2) {
		standardTile("scene1", "device.switch", width: 2, height: 2, inactiveLabel: false, decoration: "flat") {
			state "scene1", label:'Scene 1', action:"scene.activate(1)", icon:"st.Lighting.light11"
		}
		standardTile("scene2", "device.switch", width: 2, height: 2, inactiveLabel: false, decoration: "flat") {
			state "scene2", label:'Scene 2', action:"scene.activate(2)", icon:"st.Lighting.light11"
		}
		standardTile("scene3", "device.switch", width: 2, height: 2, inactiveLabel: false, decoration: "flat") {
			state "scene3", label:'Scene 3', action:"scene.activate(3)", icon:"st.Lighting.light11"
		}

		main(["scene1"])
		details(["scene1", "scene2", "scene3"])

	}
}

def parse(description) {

	def result = []
    
	if (description != "updated") {
		logger("parse() >> zwave.parse($description)", "debug")
		def cmd = zwave.parse(description, getCommandClassVersions())
		if (cmd) {
			result += zwaveEvent(cmd)
		}
	}
    
	if (result?.name == 'hail' && hubFirmwareLessThan("000.011.00602")) {
		result = [result, response(zwave.basicV1.basicGet())]
		logger("Was hailed: requesting state update", "debug")
	} else {
		logger("Parse returned ${result?.descriptionText}", "debug")
	}
    
	return result
}

def zwaveEvent(physicalgraph.zwave.commands.centralscenev1.CentralSceneNotification cmd) {
	logger("Scene {$cmd.sceneNumber} Activated", "trace")
	createEvent(name: "scene.activate", value: cmd.sceneNumber)
}

/**
 *  zwaveEvent( COMMAND_CLASS_SECURITY (0x98) : SECURITY_MESSAGE_ENCAPSULATION (0x81) )
 *
 *  The Security Message Encapsulation command is used to encapsulate Z-Wave commands using AES-128.
 *
 *  Action: Extract the encapsulated command and pass to the appropriate zwaveEvent() handler.
 *    Set state.useSecurity flag to true.
 *
 *  cmd attributes:
 *    List<Short> commandByte         Parameters of the encapsulated command.
 *    Short   commandClassIdentifier  Command Class ID of the encapsulated command.
 *    Short   commandIdentifier       Command ID of the encapsulated command.
 *    Boolean secondFrame             Indicates if first or second frame.
 *    Short   sequenceCounter
 *    Boolean sequenced               True if the command is transmitted using multiple frames.
 **/
def zwaveEvent(physicalgraph.zwave.commands.securityv1.SecurityMessageEncapsulation cmd) {
    logger("zwaveEvent(): Security Encapsulated Command received: ${cmd}","trace")

    state.useSecurity = true

    def encapsulatedCommand = cmd.encapsulatedCommand(getCommandClassVersions())
    if (encapsulatedCommand) {
        cacheCommandMd(encapsulatedCommand, "SECURITY_MESSAGE_ENCAPSULATION")
        return zwaveEvent(encapsulatedCommand)
    } else {
        logger("zwaveEvent(): Unable to extract security encapsulated command from: ${cmd}","error")
    }
}

/**
 *  zwaveEvent( COMMAND_CLASS_SECURITY (0x98) : SECURITY_COMMANDS_SUPPORTED_REPORT (0x03) )
 *
 *  The Security Commands Supported Report command advertises which command classes are supported using security
 *  encapsulation.
 *
 *  Action: Log an info message. Set state.useSecurity flag to true.
 *
 *  cmd attributes:
 *    List<Short>  commandClassControl
 *    List<Short>  commandClassSupport
 *    Short        reportsToFollow
 *
 *  Exmaple: SecurityCommandsSupportedReport(commandClassControl: [43],
 *   commandClassSupport: [32, 90, 133, 38, 142, 96, 112, 117, 39], reportsToFollow: 0)
 **/
def zwaveEvent(physicalgraph.zwave.commands.securityv1.SecurityCommandsSupportedReport cmd) {
    logger("zwaveEvent(): Security Commands Supported Reportreceived: ${cmd}","trace")

    state.useSecurity = true
    state.zwtGeneralMd.securityCommandClassSupport = cmd.commandClassSupport.sort()
    state.zwtGeneralMd.securityCommandClassControl = cmd.commandClassControl.sort()

    logger("Command classes supported with security encapsulation: ${toCcNames(state.zwtGeneralMd.securityCommandClassSupport, true)}","info")
    logger("Command classes supported for CONTROL with security encapsulation: ${toCcNames(state.zwtGeneralMd.securityCommandClassControl, true)}","info")
}

/**
 *  zwaveEvent( DEFAULT CATCHALL )
 *
 *  Called for all commands that aren't handled above.
 **/
def zwaveEvent(physicalgraph.zwave.Command cmd) {
    logger("zwaveEvent(): No handler for command: ${cmd}", "warn")
}

/**
 *  cacheCommandMd()
 *
 *  Caches command meta-data.
 *  Translates commandClassId to a name, however commandId is not translated (the lookup would be too much code).
 **/
private cacheCommandMd(cmd, description = "", sourceEndpoint = "", destinationEndpoint = "") {

    // Update commands meta-data cache:
    if (state.zwtCommandsMd?.find( { it.commandClassId == cmd.commandClassId & it.commandId == cmd.commandId } )) { // Known command type.
        state.zwtCommandsMd?.collect {
            if (it.commandClassId == cmd.commandClassId & it.commandId == cmd.commandId) {
                it.description = description
                it.parsedCmd = cmd.toString()
                if (sourceEndpoint) {it.sourceEndpoint = sourceEndpoint}
                if (destinationEndpoint) {it.destinationEndpoint = destinationEndpoint}
            }
        }
    }
    else { // New command type:
        logger("zwaveEvent(): New command type discovered.","debug")
        def commandMd = [
            commandClassId: cmd.commandClassId,
            commandClassName: toCcNames(cmd.commandClassId.toInteger()),
            commandId: cmd.commandId,
            description: description,
            parsedCmd: cmd.toString()
        ]
        if (sourceEndpoint) {commandMd.sourceEndpoint = sourceEndpoint}
        if (destinationEndpoint) {commandMd.destinationEndpoint = destinationEndpoint}

        state.zwtCommandsMd << commandMd
    }

}


/**
 *  logger()
 *
 *  Wrapper function for all logging. Simplified for this device handler.
 **/
private logger(msg, level = "debug") {

	state.loggingLevelIDE = 5
    switch(level) {
        case "error":
            if (state.loggingLevelIDE >= 1) log.error msg
            break

        case "warn":
            if (state.loggingLevelIDE >= 2) log.warn msg
            break

        case "info":
            if (state.loggingLevelIDE >= 3) log.info msg
            break

        case "debug":
            if (state.loggingLevelIDE >= 4) log.debug msg
            break

        case "trace":
            if (state.loggingLevelIDE >= 5) log.trace msg
            break

        default:
            log.debug msg
            break
    }
}

/**
 *  getCommandClassVersions()
 *
 *  Returns a map of the command class versions supported by the device. Used by parse() and zwaveEvent() to
 *  extract encapsulated commands from MultiChannelCmdEncap, MultiInstanceCmdEncap, SecurityMessageEncapsulation,
 *  and Crc16Encap messages.
 **/
private getCommandClassVersions() {
    return [
        0x20: 1, // Basic V1
        0x25: 1, // Switch Binary V1
        0x26: 2, // Switch Multilvel V2
        0x27: 1, // Switch All V1
        0x2B: 1, // Scene Activation V1
        0x30: 2, // Sensor Binary V2
        0x31: 5, // Sensor Multilevel V5
        0x32: 3, // Meter V3
        0x33: 3, // Switch Color V3
        0x56: 1, // CRC16 Encapsulation V1
        0x59: 1, // Association Grp Info
        0x60: 3, // Multi Channel V3
        0x62: 1, // Door Lock V1
        0x70: 2, // Configuration V2
        0x71: 1, // Alarm (Notification) V1
        0x72: 2, // Manufacturer Specific V2
        0x73: 1, // Powerlevel V1
        0x75: 2, // Protection V2
        0x76: 1, // Lock V1
        0x84: 1, // Wake Up V1
        0x85: 2, // Association V2
        0x86: 1, // Version V1
        0x8E: 2, // Multi Channel Association V2
        0x87: 1, // Indicator V1
        0x98: 1  // Security V1
   ]
}