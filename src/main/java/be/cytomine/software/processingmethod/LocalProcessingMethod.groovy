package be.cytomine.software.processingmethod

class LocalProcessingMethod extends AbstractProcessingMethod {

    def executeJob(def command, def serverParameters) {
        communication.executeCommand(command as String)
    }

    def isAlive(def jobId) {
        return null
    }

    def retrieveLogs(def jobId, def outputFile) {
        return null
    }

    def killJob(def jobId) {
        return null
    }

}
