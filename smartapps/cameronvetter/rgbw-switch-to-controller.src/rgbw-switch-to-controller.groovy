/**
 *  RGBW Switch to Controller
 *
 *  Copyright 2019 Cameron Vetter
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
 */
definition(
    name: "RGBW Switch to Controller",
    namespace: "cameronvetter",
    author: "Cameron Vetter",
    description: "Connect a switch with RGBW capabilities to a Controller with RGBW capabilities",
    category: "Convenience",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
    iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png")

preferences {

    page(name: "pageOne", nextPage: "pageTwo", install: false) {
        section {
            input "thepanel", "device.rgbGenieTouchPanel", required: true, title: "Touch Panel to Configure:"
        }
  	}
    
        page(name: "pageTwo")
    

}


def pageTwo() {
    dynamicPage(name: "pageTwo", title: "Select Routine to Execute for Scenes", install: true, uninstall: true) {

        // get the available actions
            def actions = location.helloHome?.getPhrases()*.label
        	log.trace actions
            if (actions) {
            	// sort them alphabetically
            	actions.sort()
                section("Scene 1") {
                	// use the actions as the options for an enum input
                	input "scene1Action", "enum", title: "Select routine to execute", options: actions
                }
                section("Scene 2") {
                	// use the actions as the options for an enum input
                	input "scene2Action", "enum", title: "Select routine to execute", options: actions
                }
                section("Scene 3") {
                	// use the actions as the options for an enum input
                	input "scene3Action", "enum", title: "Select routine to execute", options: actions
                }
            }
    }
}

def installed() {
	log.debug "Installed with settings: ${settings}"

	initialize()
}

def updated() {
	log.debug "Updated with settings: ${settings}"

	unsubscribe()
	initialize()
}

def initialize() {
	subscribe(thepanel, "scene.activate", sceneActivatedHandler)
}

def sceneActivatedHandler(evt) {
    log.debug "sceneActivatedHandler called: $evt value: $evt.value"
    switch(evt.value)
    {
        case 1:
            location.helloHome?.execute(settings.scene1Action)
            break;
        case 2:
            location.helloHome?.execute(settings.scene2Action)
            break;
        case 3:
            location.helloHome?.execute(settings.scene3Action)
            break;
    }
}