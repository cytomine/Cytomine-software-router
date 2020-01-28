# Cytomine software router


> Cytomine software router is an application responsible of installing Cytomine softwares (i.e. plugins) and run these plugins on remote processing servers (HPC, GPU, ...).

[![Build Status](https://travis-ci.com/Cytomine-ULiege/Cytomine-software-router.svg?branch=master)](https://travis-ci.com/Cytomine-ULiege/Cytomine-software-router)
[![GitHub release](https://img.shields.io/github/release/Cytomine-ULiege/Cytomine-software-router.svg)](https://github.com/Cytomine-ULiege/Cytomine-software-router/releases)
[![GitHub](https://img.shields.io/github/license/Cytomine-ULiege/Cytomine-software-router.svg)](https://github.com/Cytomine-ULiege/Cytomine-software-router/blob/master/LICENSE)

## Overview

Cytomine-software-router maintains the communication between Cytomine-core & a rabbitMQ server to install Cytomine softwares from remote providers and launch & track jobs on remote processing servers.

## Install

**To install *official* release of Cytomine-software-router, see @cytomine. Follow this guide to install forked version by ULiege.** 

It is automatically installed with the [Cytomine-bootstrap](https://github.com/Cytomine-ULiege/Cytomine-bootstrap) procedure using Docker. See [Cytomine installation documentation](http://doc.cytomine.be/pages/viewpage.action?pageId=10715266) for more details.

## Develop
Check [how to install a development environment for Cytomine](http://doc.cytomine.be/display/DEVDOC/How+to+install+a+development+environment+for+Cytomine+ULiege+with+Docker).

## References
When using our software, we kindly ask you to cite our website url and related publications in all your work (publications, studies, oral presentations,...). In particular, we recommend to cite (Marée et al., Bioinformatics 2016) paper, and to use our logo when appropriate. See our license files for additional details.

- URL: http://www.cytomine.org/
- Logo: [Available here](https://cytomine.coop/sites/cytomine.coop/files/inline-images/logo-300-org.png)
- Scientific paper: Raphaël Marée, Loïc Rollus, Benjamin Stévens, Renaud Hoyoux, Gilles Louppe, Rémy Vandaele, Jean-Michel Begon, Philipp Kainz, Pierre Geurts and Louis Wehenkel. Collaborative analysis of multi-gigapixel imaging data using Cytomine, Bioinformatics, DOI: [10.1093/bioinformatics/btw013](http://dx.doi.org/10.1093/bioinformatics/btw013), 2016. 

## License

Apache 2.0