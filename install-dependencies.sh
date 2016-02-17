#!/bin/sh
git clone https://github.com/hbz/metafacture-core.git
cd metafacture-core
git pull
mvn clean install -DskipTests=true
cd ..
git clone https://github.com/hbz/lobid-rdf-to-json.git
cd lobid-rdf-to-json
git pull
mvn clean install -DskipTests=true
