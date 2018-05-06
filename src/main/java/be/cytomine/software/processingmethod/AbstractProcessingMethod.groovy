package be.cytomine.software.processingmethod

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

import be.cytomine.software.communication.Communication

abstract class AbstractProcessingMethod {

    Communication communication

    static def newInstance(String classname) {
        if (classname == null) {
            throw new Exception("The class name [${classname}] doesn't refer to a class !")
        }

        def instance = Class
                .forName("be.cytomine.software.processingmethod." + classname)
                .newInstance()

        return instance
    }

    def abstract executeJob(def command, def serverParameters)

    def abstract isAlive(def jobId)

    def abstract retrieveLogs(def jobId, def outputFile)

    def abstract killJob(def jobId)

}
