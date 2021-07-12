package be.cytomine.software.repository

/*
 * Copyright (c) 2009-2020. Authors: see NOTICE file.
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
    private GHRateLimit ghRateLimit

    GitHubManager(String username) {
        super(username)
    }

    GitHubManager(String username, def opts) {
        super(username, opts)
    }

    @Override
    def connectToRepository(String username, def opts) {
        try {
            if (opts.containsKey("token")) {
                gitHub = new GitHubBuilder().withOAuthToken(opts.token, username).withRateLimitHandler(RateLimitHandler.FAIL).build()
                log.info("Github $username connected with token")
            } else if (opts.containsKey("softwareRouterGithubUsername") && opts.containsKey("softwareRouterGithubToken")) {
                gitHub = new GitHubBuilder().withOAuthToken(opts.softwareRouterGithubToken, opts.softwareRouterGithubUsername).withRateLimitHandler(RateLimitHandler.FAIL).build()
                log.info("Github $username connected with software router Github credentials")
            } else {
                gitHub = new GitHubBuilder().withRateLimitHandler(RateLimitHandler.FAIL).build()
                log.info("Github $username connected anonymously")
            }
            ghRateLimit = gitHub.getRateLimit()
            ghUser = gitHub.getUser(username)
        } catch(IOException ex) {
            checkRateLimit()
            log.info(ex.printStackTrace())
        }

    }

    def retrieveDescriptor(def repository, def release) throws GHFileNotFoundException {
        try {
            def currentRepository = ghUser.getRepository((repository as String).trim().toLowerCase())
            if (currentRepository == null) {
                throw new GHFileNotFoundException("The repository doesn't exist !")
            }

            def content = currentRepository.getDirectoryContent(".", release as String)

            for (def element : content) {
                if (element.getName().trim().toLowerCase() == Main.configFile.cytomine.software.descriptorFile) {
                    def url = new URL(element.getDownloadUrl())
                    def readableByteChannel = Channels.newChannel(url.openStream())
                    def filename = (Main.configFile.cytomine.software.path.softwareSources as String) + "/" + new Date().getTime().toString() + ".json"
                    def fileOutputStream = new FileOutputStream(filename)
                    fileOutputStream.getChannel().transferFrom(readableByteChannel, 0, Long.MAX_VALUE)

                    return filename
                }
            }

            throw new GHFileNotFoundException("The software descriptor doesn't exist !")
        } catch (IOException e){
            if (e instanceof GHFileNotFoundException){
                log.error(e.getMessage())
                throw e
            }
            checkRateLimit()
            log.info(e.printStackTrace())
        }
    }
    private void checkRateLimit() {
        if(ghRateLimit.remaining == 0){
            log.error("API rate limit exceeded for this IP, limit will be reset at "+ghRateLimit.getResetDate())
        }
    }
}
