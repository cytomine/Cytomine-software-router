package consumer

/**
 * Created by julien 
 * Date : 20/04/15
 * Time : 10:14
 */
class JobExecutionThread implements Runnable{

    ArrayList commandToExecute
    String softwareName
    File jobDirectory = new File(RabbitWorker.configFile.jobDirectory as String)

    @Override
    void run() {
        String logFile = softwareName + "-" + new Date().format('d-M-yyyy_hh-mm-ss-SSS').toString() + ".log"
        File logFileJob = new File((String)RabbitWorker.configFile.logsDirectory + logFile)
        logFileJob.getParentFile().mkdirs();
        logFileJob.createNewFile();

        println "Job to execute : " + commandToExecute

        def process = new ProcessBuilder(commandToExecute)
        process.directory(jobDirectory)
        process.redirectErrorStream(true)

        process.redirectOutput(ProcessBuilder.Redirect.appendTo(logFileJob))
        process.start().waitFor()
    }

}
