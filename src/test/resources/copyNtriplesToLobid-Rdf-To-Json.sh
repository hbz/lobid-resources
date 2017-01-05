# update test files:
# make the output of lobid-resources the input of lobid-rdf-to-json
cd nt
# assumes that both repos reside in the same directory
for i in $(find -type f); do cp -p $i ../../../../../lobid-rdf-to-json/src/test/resources/input/nt/$i ; done

