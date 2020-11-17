// RabbitMQ Cytomine user (must be super admin)
cytomine.core.url = "http://localhost-core"
cytomine.core.publicKey = "VWX"
cytomine.core.privateKey = "STU"

rabbitmq.host = "rabbitmq"
rabbitmq.port = "5672"
rabbitmq.username = "router"
rabbitmq.password = "router"

cytomine.software.communication.exchange = "exchangeCommunication"
cytomine.software.communication.queue = "queueCommunication"

cytomine.software.path.jobs = "/data/jobs"
cytomine.software.path.softwareSources = "/data/softwares/code"
cytomine.software.path.softwareImages = "/data/softwares/images"
cytomine.software.sshKeysFile = "/data/ssh/id_rsa"
cytomine.software.descriptorFile = "descriptor.json"

cytomine.software.ssh.maxRetries = 3
cytomine.core.connectionRetries = 20

// In seconds
cytomine.software.repositoryManagerRefreshRate = 60
cytomine.software.job.logRefreshRate = 15
cytomine.software.pullingCheckRefreshRate = 20
cytomine.software.pullingCheckTimeout = 1800
cytomine.core.connectionRefreshRate = 30

cytomine.software.github.username=""
cytomine.software.github.token=""

cytomine.software.dockerhub.username=""
cytomine.software.dockerhub.password=""