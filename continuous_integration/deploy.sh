#!/bin/bash

git fetch --tags
export VERSION=$(mvn -q -Dexec.executable=echo -Dexec.args='${project.version}' --non-recursive exec:exec)
export DEPLOY=false
if [[ ! $(git tag -l v$VERSION) ]]; then
    git config --local user.name "$(git log -1 --pretty=format:'%an')"
    git config --local user.email "$(git log -1 --pretty=format:'%ae')"
    git tag "v$VERSION"
    export DEPLOY=true
    echo "Deploy with version $VERSION"

    mvn package
    cp log4j.properties docker/
    cp target/cytomine-software-router-$VERSION-jar-with-dependencies.jar docker/
    docker build --build-arg RELEASE_PATH="." --build-arg VERSION=$VERSION -t cytomineuliege/software_router:latest -t cytomineuliege/software_router:v$VERSION docker/
fi;