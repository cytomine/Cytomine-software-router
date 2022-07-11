package be.cytomine.software.util

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

import groovy.util.logging.Log4j

@Log4j
class Utils {
    static String getImageNameFromCommand(String command) {
        def imageName
        if(command.contains("--name")) {
            def temp = command.substring(command.indexOf("--name ") + "--name ".size(), command.size())
            imageName = temp.substring(0, temp.indexOf(" "))
        } else if(command.contains("singularity build")) {
            def temp = command.split(" ")
            for(int i = 2;i<temp.size();i++){
                if(temp[i].startsWith("-")) continue
                imageName = temp[i].trim()
                break
            }
        }
        return imageName
    }

    static Process[] executeProcesses(String[] commands, File executiveDirectory) {
        def results = []
        for (String command : commands) {
            def process = executeProcess(command.trim(), executiveDirectory)
            results << process
            if(process.exitValue() != 0) return results
        }
        return results
    }
    static Process executeProcess(String command, String executiveDirectory) {
        return executeProcess(command, new File(executiveDirectory))
    }
    static Process executeProcess(String command, String executiveDirectory, Map<String, Object> envs) {
        return executeProcess(command, new File(executiveDirectory), envs)
    }
    static Process executeProcess(String command, File executiveDirectory) {
        executeProcess(command, executiveDirectory, null)
    }
    static Process executeProcess(String command, File executiveDirectory, Map<String, Object> envs) {
        if(command.contains("&&")) return executeProcesses(command.split("&&"), executiveDirectory)[-1]
        log.info "run process "+command
        def processBuilder = new ProcessBuilder((command).split(" ") as List)
                .directory(executiveDirectory)
                .redirectErrorStream(true)
        if (envs) {
            processBuilder.environment().putAll(envs)
        }
        def process = processBuilder.start()
        process.waitFor()
        return process
    }
}
