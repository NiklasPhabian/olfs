[![license](https://img.shields.io/github/license/ESIPFed/eskg.svg?maxAge=2592000?style=plastic)](http://www.apache.org/licenses/LICENSE-2.0)
[![Build Status](https://travis-ci.org/ESIPFed/eskg.svg?branch=master)](https://travis-ci.org/ESIPFed/eskg)

# Citation enabled OLFS

<img src="http://www.esipfed.org/sites/default/files/esip-logo.png" align="right" width="300" />

## Introduction
This repository holds the development of the citation-enabled OLFS of the OPeNDAP server Hyrax. The OLFS extrention is developed as an ESIP lab project.

The goal of this project is to extend the API of the Open-Source Project for a Network Data Access Protocol (OPeNDAP) to generate citations that precisely match the requested data. We have implemented these extentions in the pydap server [occur_pydap](https://github.com/NiklasPhabian/occur_pydap), and created a stand-alone server [occur](https://github.com/NiklasPhabian/occur) that intercepts requests to an OPeNDAP server and extends the API to allow to request citations. We are now extending the Open Leightweigt Frontend Server (OLFS) of the Hyrax Server. 

## Phase 1 Design:
In Phase 1, we leave the backend server (BES) unmodified and solely extend functionalities in the OLFS.

### Rest API
We are extending the OPeNDAP REST api to allow citation requests. E.g. a data request to data located at
https://host/opendap/data/dataset.nc.csv?Time[0:1:1459], can be accompanied by a request for a citation as https://host/opendap/data/dataset.nc.citation?Time[0:1:1459].

### Functional extentions to OLFS:
#### Data has cashing:
The OLFS hashes the datastream for every request and will log the REST request URL together with the hash and the datetime.

### Citation creation
The OLFS constructs the citation. A citation will be constructed from the following components:
* Dataset metadata
* Subsetting parameters / REST API request url
* Hash of requested data
The dataset metadata are extracted from the document attribute descriptor (DAS), which the OLFS requests from the BES. The hash of the requested data is read from the request/hash log. If there are several different hashes for the same request URL, the user will be provided with a selection of timestamped citations. If there is no entry in the has log for the REST API request url, the OLFS will calculate the hash and the user will be provided with a warning that there might be a difference between the data stored in the OPeNDAP server at this moment and the data that the citation is intended to be created for.

### Citation resolving
A citation will be resolved in two steps. 
Step 1: The data is re-requested by the client through the REST API request url in the citation. 
Step 2: The client sends the the hash of the citation to the OLFS. The OLFS will compare this has with the latest entry in the has log. If they do not equal each other, the OLFS will return a warning to the user that the requested data is different from the cited data.

### DOI resolving
The dataset metadata is obtained by querying the back-end-server for the DAS. If the DAS does not contain sufficient information, but contains a DOI, the OLFS will try to resolve the DOI and obtain the missing dataset metadata.

## Phase 2 Design:
In phase 2, we move the tasks of citation creation down to the BES.

### Fuctional extensions to the OLFS:
In phase 2, the OLFS will not create the citation itself, but merely translate the REST API citation request into an XML request to the BES.

### Functional extention to the BES:
The BES will be extended by the following feautures:

#### Versioning:
The BES 




# Hyrax/OLFS

Hyrax Version 1.14.0  (16 October 2017)
OLFS  Version 1.17.0  (16 October 2017)

[![DOI](https://www.zenodo.org/badge/26560831.svg)](https://www.zenodo.org/badge/latestdoi/26560831)

The file install.html or docs/index.html may have additional information...

### First:

Build and install bes and at minimum the netcdf_handler projects.

Launch the bes (you can use besctl to do that). 

Make sure there s a beslistener process running.

### Check it out:

    git clone https://github.com/OPENDAP/olfs.git


### Build it:

    ant server

(To make a distribution for release:  ant server -DHYRAX_VERSION=<num> -DOLFS_VERSION=<num> )

### Install it:

    rm -rf $CATALINA_HOME/webapps/opendap*
    cp build/dist/opendap.war $CATALINA_HOME/webapps

### Launch it:

    $CATALINA_HOME/bin/startup.sh

### Configure it:

By default the OLFS will utilize it's bundled default configuration in the directory
    $CATALINA_HOME/webapps/opendap/WEB-INF/conf

In order to configure your system so that your configuration changes are persistent 
you will need to do one of the following:

* For the user that will be running the OLFS (the Tomcat user), set
the environment variable OLFS_CONFIG_DIR to an existing directory to
which the Tomcat user has both read and write privileges.

OR

* Create the directory /etc/olfs and set it's permissions/ownership so
that the Tomcat user has both read and write permission.

If both of these steps are done then priority is given to the environment variable.

Restart Tomcat. When it starts the OLFS will check these locations and then install a copy of its default configuration into the new spot.

Edit the configuration files as needed.

If, for example, your beslistener is not running on localhost:10022
then you'll need to edit the olfs.xml file in the configuration
directory and adjust the <host> and <port> values to reflect your
situation.

### Relaunch it:

    $CATALINA_HOME/bin/shutdown.sh; $CATALINA_HOME/bin/startup.sh

For the configuration changes to take effect.

See http://docs.opendap.org/index.php/Hyrax for information about this software, Installation
instructions and NEWS.

### About the Aggregation servlet

In src/opendap/aggregation we have a servlet that performs aggregation for use,
initially, with NASA's EDSC (Earth Data Search Client). In that directory you
will find a README along with some help in testing the servlet using curl.
