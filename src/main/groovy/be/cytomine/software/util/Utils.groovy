package be.cytomine.software.util

class Utils {
    public static String getImageNameFromCommand(String command) {
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
}
