#!/bin/sh
cd ../..
git clone https://github.com/hbz/metafacture-core.git
cd metafacture-core
git pull
mvn clean install -DskipTests=true
