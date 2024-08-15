#!/bin/bash
# Description: Tests generated JSON files against schemas
# Prerequisites: install ajv and ajv-formats
#	$ npm install -g ajv-cli
#	$ npm install -g ajv-formats

# old transformation of Aleph data, known to be often invalid:
# DIRECTORY_OF_JSON_TO_VALIDATE="jsonld/"
# new transformation of Alma data:
DIRECTORY_OF_JSON_TO_VALIDATE="alma-fix/"


for version in "draft"; do
	echo "Testing version: $version"
	ajv test -s "schemas/resource.json" -r "schemas/!(resource).json" -d "${DIRECTORY_OF_JSON_TO_VALIDATE}/*.json" -c ajv-formats --strict=log --all-errors --valid 2>&1
done

if [ $? -eq 0 ]
then
	echo -e "All tests \033[0;32mPASSED\033[0m\n"
	else
		echo -e "Test \033[0;31mFAILED\033[0m\n"
fi
