#
# Copyright (c) 2009-2018. Authors: see NOTICE file.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

FROM cytomine/java8:v1.2.0

RUN apt-get -y update && \
    apt-get --no-install-recommends --no-install-suggests -y install \
        apt-transport-https \
        ca-certificates \
        iptables \
        make \
        squashfs-tools \
        openssh-client \
        libarchive-dev \
        python && \
    rm -rf /var/lib/apt/lists/*

RUN dpkg-reconfigure locales

# Singularity installation
ARG SINGULARITY_VERSION=2.6.0
RUN wget https://github.com/sylabs/singularity/releases/download/$SINGULARITY_VERSION/singularity-$SINGULARITY_VERSION.tar.gz && \
    tar xvf singularity-$SINGULARITY_VERSION.tar.gz && \
    cd singularity-$SINGULARITY_VERSION && \
    ./configure --prefix=/usr/local && \
    make && \
    make install

# Software-router Installation
ARG VERSION
ARG NAMESPACE=Cytomine-ULiege
ARG RELEASE_PATH=https://github.com/${NAMESPACE}/Cytomine-software-router/releases/download/v${VERSION}
ADD ${RELEASE_PATH}/cytomine-software-router-${VERSION}-jar-with-dependencies.jar /opt/cytomine-software-router.jar
COPY log4j.properties /opt/log4j.properties

COPY deploy.sh /tmp/deploy.sh
RUN chmod +x /tmp/deploy.sh

ENTRYPOINT ["/tmp/deploy.sh"]
