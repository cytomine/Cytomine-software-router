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

import groovy.json.JsonSlurper

class DockerHubManager {

    def username
    final int pageSize = 100

    private static def getRequest(def request) {
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

    private static def fetchAll(url) {
        def data = []

        while (url) {
            if (!url) break

            def response = getRequest(url)

            if (response?.count != null && (response?.count as Integer) > 0) {
                def results = response?.results
                results.each { elem ->
                    data.add(elem."name" as String)
                }
            }

            url = response?.next
        }

        return data
    }

    def getRepositories() throws FileNotFoundException {
        def url = "https://registry.hub.docker.com/v2/repositories/${username}/?page_size=${pageSize}"
        return fetchAll(url)
    }

    def getTags(def repository) throws FileNotFoundException {
        def url = "https://registry.hub.docker.com/v2/repositories/${username}/${repository}/tags/?page_size=${pageSize}"
        return fetchAll(url)
    }
}
