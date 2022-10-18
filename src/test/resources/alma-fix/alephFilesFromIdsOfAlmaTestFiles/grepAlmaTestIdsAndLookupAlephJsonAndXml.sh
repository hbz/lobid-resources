# invoke like "$ bash grepAlmaTestIdsAndLookupAlephJsonAndXml.sh"
# see #1313

for almaMmsId in $(ls ../*.json); do
  hbzId=$(grep hbzId "$almaMmsId" | cut -d '"' -f4 )
  if [ -n "${hbzId}" -a "${hbzId}" != "null" ]; then
    almaMmsId=$(echo "$almaMmsId" | sed -s 's/..\/\(.*\).json/\1/g')
    echo "almaMmsId:$almaMmsId hbzId:$hbzId"
    json=$(curl --silent "http://lobid.org/resources/search?q=hbzId:${hbzId}&format=json"| jq .member[])
    xml=$(curl --silent "http://lobid.org/hbz01/${hbzId}" | xmllint --format -)
    echo "$json" > "${almaMmsId}_${hbzId}-aleph.json"
    echo "$xml" > "${almaMmsId}_${hbzId}-aleph.xml"
  else
    echo "No hbzId for $almaMmsId"
  fi
done

