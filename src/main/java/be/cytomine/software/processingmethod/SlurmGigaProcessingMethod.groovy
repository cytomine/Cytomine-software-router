package be.cytomine.software.processingmethod

class SlurmGigaProcessingMethod extends SlurmProcessingMethod {

    @Override
    def executeJob(def command, def serverParameters) {
        def slurmCommand = 'sbatch --output=%A.out --time=' + defaultTime

        if (serverParameters != null) {
            slurmCommand = 'sbatch --output=%A.out '

            def timeSet = false
            serverParameters.each { element ->
                if (element.key == "time") timeSet = true
                slurmCommand += '--' + element.key + '=' + element.value +  ' '
            }
        }


        def temp = (command as String).replace("singularity run ", "").trim()
        def imageName = temp.substring(0, temp.indexOf(" "))

        def importResult = communication.copyLocalToRemote("./", "./", imageName)

        def executionCommand = '''echo "#!/bin/bash
export PATH=$PATH:/home/mass/opt/gridbin/bin
''' + command + '''"|''' + slurmCommand

        log.info("SLURM COMMAND : ${slurmCommand}")
        log.info("EXECUTION COMMAND : ${executionCommand}")

        def response = communication.executeCommand(executionCommand)

        log.info("RESPONSE : " + response)

        def jobId = (response =~ /(\d+)/)
        return jobId.find() ? jobId.group() as Integer : -1
    }

}
