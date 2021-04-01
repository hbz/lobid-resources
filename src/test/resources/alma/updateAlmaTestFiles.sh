#!/bin/bash
# Author: dr0i
# Date: 2021-02-23
# Description:
# Updates the test files by getting the Alma Xml source(s) and creating an archive.
# This archive is used by the AlmaMarc21XmlToLobidJsonTest to create the json files.
#
# Parameters: {all , $hbzId, $almaId}
# If parameter is 'all' all the test files found in the filesystem will be updated.
# If parameter is a hbzId this hbz Id is lookuped and appended to the archive.
# If parameter is a almaId this alma Id is lookuped and appended to the archive.
#
# Example: "bash updateAlmaTestFiles.sh HT017664407"

TEST_FILE="almaMarcXmlTestFiles"

tar xfj almaMarcXmlTestFiles.xml.tar.bz2

function getAlmaXmlAndAppendItToArchive() {
	hbzId=$1
	almaXmlUrl="curl https://indexes.devel.digibib.net/export/$hbzId"
	echo "getting Alma Xml for $hbzId ..."
	# if it's a hbzId, we have to lookup the almaId first
	if [[ $hbzId =~ ^[A-Z] ]]; then
		# lookup and filter alma url
		almaXmlUrl=$(curl $almaXmlUrl |jq .|grep -A2 -B2 '"type": "alma"'|grep url|cut -d '"' -f4)
	fi
	# get AlmaMarcXml
	curl --silent $almaXmlUrl | xmllint --format - | grep -v '<?xml version="1.0"?>' > $hbzId.xml
	cat $hbzId.xml >> $TEST_FILE
	rm $hbzId.xml
	rm $hbzId.json
}

function appendClosingColletionTagToArchive() {
	echo "</collection>" >> $TEST_FILE
}

case $1 in
	all)
		# set head of xml file
		echo '<?xml version="1.0" encoding="UTF-8"?>
<collection>' > $TEST_FILE
		# get all JSON test files; filter names from then
		for hbzId in $(ls *.json|cut -d '/' -f2|cut -d '.' -f1 ); do
			getAlmaXmlAndAppendItToArchive $hbzId
		done
		appendClosingColletionTagToArchive
	;;
	*)
		sed -i '$d' almaMarcXmlTestFiles
		getAlmaXmlAndAppendItToArchive $1
		appendClosingColletionTagToArchive
esac
tar cfj almaMarcXmlTestFiles.xml.tar.bz2 $TEST_FILE
cd ../../../../
mvn failsafe:integration-test -Dit.test=AlmaMarc21XmlToLobidJsonTest
