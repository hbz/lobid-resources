#!/bin/bash
# Author: dr0i
# Date: 2021-02-23
# Description:
# Updates the test files by getting the Alma Xml source(s).
#
# Parameters: {all , $hbzId, $almaId}
# If parameter is 'all' all the test files found in the filesystem will be updated.
# If parameter is a hbzId this hbz Id is lookuped
# If parameter is a almaId this alma Id is lookuped
#
# Example: "bash updateAlmaTestFiles.sh HT017664407"

XML_HEAD='<?xml version="1.0" encoding="UTF-8"?>'

function getAlmaXmlWriteFile() {
	resourceId=$1
	URL_BASE="https://lobid.org/marcxml/"
	echo "getting Alma Xml for $hbzId ..."
	# if it's a hbzId, we have to lookup the almaId first
	if [[ $resourceId =~ ^[A-Z\(] ]]; then
		# lookup and filter alma url
		resourceId=$(curl $URL_BASE$resourceId |jq .|grep -A2 -B2 '"type": "alma"'|grep id|cut -d '"' -f4)
	fi
	# get AlmaMarcXml
	curl --silent $URL_BASE$resourceId | xmllint --format - > $resourceId.xml.tmp
	#set proper XML header
	sed -i "1s/.*/$XML_HEAD/" $resourceId.xml.tmp
	xmllint $resourceId.xml.tmp > $resourceId.xml
	rm *.tmp
  if [ ! -s "$resourceId.xml" ]; then
  echo "ALMA XML for $resourceId is not available anymore" ; rm "$resourceId.xml" ; rm "$resourceId.json"
  fi
}

case $1 in
	all)
		# get all JSON test files; filter names from then
		for hbzId in $(ls *.json|cut -d '/' -f2|cut -d '.' -f1 ); do
			getAlmaXmlWriteFile $hbzId
		done
	;;
	*)
		getAlmaXmlWriteFile $1
esac

cd ../../../../
mvn -DgenerateTestData=true failsafe:integration-test -Dit.test=AlmaMarc21XmlToLobidJsonMetafixTest
