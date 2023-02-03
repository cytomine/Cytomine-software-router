package be.cytomine.software.repository.threads

/*
 * Copyright (c) 2009-2022. Authors: see NOTICE file.
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
import groovy.util.logging.Log4j2

@Log4j2
class ImagePullerThread implements Runnable {

    def pullingCommand
    Closure callback

    def pullUsername = Main.configFile.cytomine.software.dockerhub.username ?: null
    def pullPassword = Main.configFile.cytomine.software.dockerhub.password ?: null

    Map<String, Object> getEnv() {
        def env = [:]
        if (pullUsername && !pullUsername.isEmpty() && pullPassword && !pullPassword.isEmpty()) {
            env.put("SINGULARITY_DOCKER_USERNAME", "$pullUsername")
            env.put("SINGULARITY_DOCKER_PASSWORD", "$pullPassword")
        }
        return (env.size() > 0) ? env : null
    }

    @Override
    void run() {
        def imageName = Utils.getImageNameFromCommand(pullingCommand)


        log.info("Pulling thread is running for image ${imageName}")

        if (new File("${Main.configFile.cytomine.software.path.softwareImages}/${imageName}").exists()) {
            log.warn("The image [${imageName}] already exists !")
            return
        }

        synchronized (Main.pendingPullingTable) {
            Main.pendingPullingTable.add(imageName)
        }

        def process = Utils.executeProcess((pullingCommand as String), Main.configFile.cytomine.software.path.softwareImages, getEnv())
        if (process.exitValue() == 0) {
            log.info("The image [${imageName}] has successfully been pulled !")
            def movingProcess = Utils.executeProcess("mv ${imageName} ${Main.configFile.cytomine.software.path.softwareImages}", ".", getEnv())
            if (movingProcess.exitValue() == 0) {
                log.info("The image [${imageName}] has successfully been moved !")
            } else {
                log.error("The image [${imageName}] has not been moved !")
            }
        } else {
            log.info("The image [${imageName}] has not been pulled !")
            log.error(process.text)
        }

        synchronized (Main.pendingPullingTable) {
            Main.pendingPullingTable.remove(imageName)
        }
        if(callback) callback()
    }
}
