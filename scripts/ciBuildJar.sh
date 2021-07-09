#!/bin/bash

set -o xtrace
set -o errexit

echo "************************************** BUILD JAR ******************************************"

file='./ci/version'
VERSION_NUMBER=$(<"$file")

echo "Build jar for $VERSION_NUMBER"

echo "GPG_KEYNAME = $GPG_KEYNAME"

docker build --rm -f scripts/docker/Dockerfile-build-jar.build -t cytomine/cytomine-software-router-jar . --build-arg VERSION_NUMBER=$VERSION_NUMBER

containerId=$(docker create cytomine/cytomine-software-router-jar)
docker cp $containerId:/app/target/cytomine-software-router-$VERSION_NUMBER-jar-with-dependencies.jar ./ci
mv ./ci/cytomine-software-router-$VERSION_NUMBER-jar-with-dependencies.jar ./ci/Cytomine-software-router.jar
docker rm $containerId
docker rmi cytomine/cytomine-software-router-jar