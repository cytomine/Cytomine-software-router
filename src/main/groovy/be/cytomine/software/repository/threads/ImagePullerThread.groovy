package be.cytomine.software.repository.threads

/*
 * Copyright (c) 2009-2020. Authors: see NOTICE file.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import be.cytomine.software.consumer.Main
import be.cytomine.software.util.Utils
import groovy.util.logging.Log4j

@Log4j
class ImagePullerThread implements Runnable {

    def pullingCommand
    Closure callback

    @Override
    void run() {
        def imageName = Utils.getImageNameFromCommand(pullingCommand)

        log.info("Pulling thread is running for image ${imageName}")

        if (new File("${Main.configFile.cytomine.software.path.softwareImages}/${imageName}").exists()) {
            log.info("The image [${imageName}] already exists !")
            return
        }

        synchronized (Main.pendingPullingTable) {
            Main.pendingPullingTable.add(imageName)
        }

        def process = Utils.executeProcess((pullingCommand as String), Main.configFile.cytomine.software.path.softwareImages)
        if (process.exitValue() == 0) {
            log.info("The image [${imageName}] has successfully been pulled !")
        } else {
            log.info("The image [${imageName}] has not been pulled !")
            log.error(process.text)
        }

        //TODO handle error (can be a network error)

        Utils.executeProcess("mv ${imageName} ${Main.configFile.cytomine.software.path.softwareImages}", ".")

        synchronized (Main.pendingPullingTable) {
            Main.pendingPullingTable.remove(imageName)
        }
        if(callback) callback()
    }

}
