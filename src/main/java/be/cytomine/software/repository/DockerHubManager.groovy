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

import groovy.json.JsonSlurper

class DockerHubManager {

    def username

    private def getRequest(def request) {
        def result = new StringBuilder()
        def url = new URL(request as String)
        def connection= url.openConnection() as HttpURLConnection
        connection.setRequestMethod("GET")
        def bufferedReader = new BufferedReader(new InputStreamReader(connection.getInputStream()))
        def line
        while ((line = bufferedReader.readLine()) != null) {
            result.append(line)
        }
        bufferedReader.close()

        return new JsonSlurper().parseText(result.toString())
    }

    def getRepositories() throws FileNotFoundException {
        def result = getRequest("https://registry.hub.docker.com/v2/repositories/${username}/")

        def repositoriesList = []
        if (result."count" != null && (result?."count" as Integer) > 0) {
            def repositories = result?."results"
            repositories.each { elem ->
                repositoriesList.add(elem."name" as String)
            }
        }

        return repositoriesList
    }

    def getTags(def repository) throws FileNotFoundException {
        def result = getRequest("https://registry.hub.docker.com/v2/repositories/${username as String}/${repository as String}/tags/")

        def tagsList = []
        if (result."count" != null && (result?."count" as Integer) > 0) {
            def tags = result?."results"
            tags.each { elem ->
                tagsList.add(elem."name" as String)
            }
        }

        return tagsList
    }

}
