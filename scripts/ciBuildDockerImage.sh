#!/bin/bash

set -o xtrace
set -o errexit

echo "************************************** Publish docker ******************************************"

file='./ci/version'
VERSION_NUMBER=$(<"$file")

docker build --rm -f scripts/docker/software_router/Dockerfile --build-arg VERSION_NUMBER=$VERSION_NUMBER -t  cytomine/software_router:v$VERSION_NUMBER .

docker push cytomine/software_router:v$VERSION_NUMBER

docker rmi cytomine/software_router:v$VERSION_NUMBER
