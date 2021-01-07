#!/bin/bash
mkdir metafacture-core
git clone https://github.com/metafacture/metafacture-core.git
cd metafacture-core
./gradlew install
cd ..
