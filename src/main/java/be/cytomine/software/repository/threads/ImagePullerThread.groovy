package be.cytomine.software.repository.threads

import be.cytomine.software.consumer.Main

/*
 * Copyright (c) 2009-2018. Authors: see NOTICE file.
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

import groovy.util.logging.Log4j

@Log4j
class ImagePullerThread implements Runnable {

    def pullingCommand

    @Override
    void run() {
        def temp = pullingCommand.substring(pullingCommand.indexOf("--name ") + "--name ".size(), pullingCommand.size())
        def imageName = temp.substring(0, temp.indexOf(" "))

        log.info("Pulling thread is running for image ${imageName}")

        if (new File("${Main.configFile.imagesDirectory}/${imageName}").exists()) {
            log.info("The image [${imageName}] already exists !")
            return
        }

        synchronized (Main.pendingPullingTable) {
            Main.pendingPullingTable.add(imageName)
        }

        def process = (pullingCommand as String).execute()
        process.waitFor()
        if (process.exitValue() == 0) {
            log.info("The image [${imageName}] has successfully been pulled !")
        } else {
            log.info("The image [${imageName}] has not been pulled !")
        }

        def movingProcess = ("mv ${imageName} ${Main.configFile.imagesDirectory}").execute()
        movingProcess.waitFor()

        synchronized (Main.pendingPullingTable) {
            Main.pendingPullingTable.remove(imageName)
        }

    }

}
