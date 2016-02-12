#/bin/bash
# This diffs the jsonld documents using the the cli and copy the jsonld
# documents to be the new test files.
# You should manually outcomment the method call "testFiles(filename)"
# when testing the json because the junit test aborts when one document
# doesn't equal, and so it's not possible to get all the new documents
# at once. TODO Should be changed in the java sources, I guess.

for i in $(ls jsonld); do echo "diffing $i:"; diff jsonld/$i src/test/resources/jsonld/$i; done
rm -rf src/test/resources/jsonld; mv jsonld src/test/resources/

