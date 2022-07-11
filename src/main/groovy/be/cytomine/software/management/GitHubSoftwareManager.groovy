package be.cytomine.software.management

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

import be.cytomine.software.boutiques.Interpreter
import be.cytomine.software.repository.GitHubManager
import groovy.util.logging.Log4j

@Log4j
class GitHubSoftwareManager extends AbstractSoftwareManager {

    GitHubManager gitHubManager
    String ghRepositoryName
    File descriptor


    GitHubSoftwareManager(GitHubManager gitHubManager, String ghRepositoryName, String release, Long idSoftwareUserRepository) throws ClassNotFoundException {
        this.gitHubManager = gitHubManager
        this.ghRepositoryName = ghRepositoryName
        this.release = release
        this.idSoftwareUserRepository = idSoftwareUserRepository
    }

    @Override
    protected File retrieveDescriptor() {
        descriptor = new File(gitHubManager.retrieveDescriptor(ghRepositoryName, release))
        return descriptor
    }
    @Override
    protected String generateSingularityBuildingCommand(Interpreter interpreter){
        def pullingInformation = interpreter.getPullingInformation()
        def imageName = interpreter.getImageName() + "-" + release as String

        return 'singularity pull --name ' + imageName + '.simg docker://' +
                pullingInformation['image'] + ':' + release as String
    }

    protected void checkDescriptor(Interpreter interpreter) {
        //if(interpreter.parseSoftware().name != ghRepositoryName) throw new CytomineException("Software name must be equals to repository name")
    }

    void cleanFiles() {
        cleanFiles(descriptor)
    }

    protected String getSourcePath() {
        return "https://github.com/${gitHubManager.username}/${ghRepositoryName}/archive/${release}.zip"
    }
}
