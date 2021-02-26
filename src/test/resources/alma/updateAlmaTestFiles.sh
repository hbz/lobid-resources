#!/bin/bash
# author: dr0i
# date: 2021-02-23

# lists all json test files, get the hbz id from their names, lookup
# to get the marc xml sources and builds a new test file archive

# set head of xml file
echo '<?xml version="1.0" encoding="UTF-8"?>
<collection>' > almaMarcXmlTestFiles

# get all JSON test files; filter names from then
for hbzId in $(ls *.json|cut -d '/' -f2|cut -d '.' -f1 ); do
	echo $hbzId
	# lookup and filter alma url
	almaXmlUrl=$(curl https://indexes.devel.digibib.net/export/$hbzId |jq .|grep -A2 -B2 '"type": "alma"'|grep url|cut -d '"' -f4)
	# get AlmaMarcXml
	curl --silent $almaXmlUrl | xmllint --format - | grep -v '<?xml version="1.0"?>' > $hbzId.xml
	cat $hbzId.xml >> almaMarcXmlTestFiles
done
echo "</collection>" >> almaMarcXmlTestFiles
tar cfj almaMarcXmlTestFiles.xml.tar.bz2 almaMarcXmlTestFiles
