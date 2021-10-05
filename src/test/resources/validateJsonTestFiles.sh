#!/bin/bash
# Description: Tests generated JSON files against schemas
# Prerequisites: install 'ajv':$  npm install -g ajv-cli

# old transformation of Aleph data, known to be often invalid:
# DIRECTORY_OF_JSON_TO_VALIDATE="jsonld/"
# new transformation of Alma data:
DIRECTORY_OF_JSON_TO_VALIDATE="alma/"


for version in "draft"; do
	echo "Testing version: $version"
	ajv test -s ./src/test/resources/schemas/resource.json -r "./src/test/resources/schemas/*.json" -d "${DIRECTORY_OF_JSON_TO_VALIDATE}/*.json" --valid 2>&1
done

if [ $? -eq 0 ]
then
	echo -e "All tests \033[0;32mPASSED\033[0m\n"
	else
		echo -e "Test \033[0;31mFAILED\033[0m\n"
fi
