package be.cytomine.software.util

class Utils {
    public static String getImageNameFromCommand(String command) {
        def imageName
        if(command.contains("--name")) {
            def temp = command.substring(command.indexOf("--name ") + "--name ".size(), command.size())
            imageName = temp.substring(0, temp.indexOf(" "))
        }
        return imageName
    }
}
