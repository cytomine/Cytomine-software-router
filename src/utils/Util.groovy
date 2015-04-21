package utils

import groovy.json.JsonSlurper

/**
 * Created by julien 
 * Date : 10/04/15
 * Time : 15:14
 */
class Util {

    static String parseString(String message) {
        JsonSlurper slurper = new JsonSlurper()
        return slurper.parseText(message)
    }

    static createDataSoftwareDirectory() {

    }
}
