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

// In seconds
cytomine.software.repositoryManagerRefreshRate = 300