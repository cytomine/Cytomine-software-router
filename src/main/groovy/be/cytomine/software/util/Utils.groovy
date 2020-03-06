package be.cytomine.software.util

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
    static Process executeProcess(String command, File executiveDirectory) {
        if(command.contains("&&")) return executeProcesses(command.split("&&"), executiveDirectory)[-1]
        log.info "run process "+command
        def process = new ProcessBuilder((command).split(" ") as List)
                .directory(executiveDirectory)
                .redirectErrorStream(true)
                .start()
        process.waitFor()
        return process
    }
}
