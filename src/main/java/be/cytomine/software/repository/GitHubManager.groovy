package be.cytomine.software.repository

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

import be.cytomine.software.consumer.Main
import groovy.util.logging.Log4j
import org.kohsuke.github.*

import java.nio.channels.Channels

@Log4j
class GitHubManager extends AbstractRepositoryManager {

    private GitHub gitHub
    private GHUser ghUser

    GitHubManager(String username) {
        super(username)
    }

    @Override
    def connectToRepository(String username) {
        gitHub = GitHub.connectAnonymously()
        ghUser = gitHub.getUser(username)
    }

    def retrieveDescriptor(def repository, def release) throws GHFileNotFoundException {
        def currentRepository = ghUser.getRepository((repository as String).trim().toLowerCase())
        if (currentRepository == null) {
            throw new GHFileNotFoundException("The repository doesn't exist !")
        }

        def content = currentRepository.getDirectoryContent(".", release as String)

        for (def element : content) {
            if (element.getName().trim().toLowerCase() == Main.configFile.softwareDescriptorName) {
                def url = new URL(element.getDownloadUrl())
                def readableByteChannel = Channels.newChannel(url.openStream())
                def filename = (Main.configFile.dataDirectory as String) + element.getName()
                def fileOutputStream = new FileOutputStream(filename)
                fileOutputStream.getChannel().transferFrom(readableByteChannel, 0, Long.MAX_VALUE)

                return filename
            }
        }

        throw new GHFileNotFoundException("The software descriptor doesn't exist !")
    }

}
