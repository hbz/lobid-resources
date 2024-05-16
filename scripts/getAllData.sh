#!/bin/bash
# Description:
# Dump lobid-resources in smaller packages.
# Works around https://github.com/hbz/lobid-resources/issues/1048.
# Author: dr0i
# Date: 2020-06-16

FROM=19800101 #it all begins with the 80ties
INC=10000 # 1 year

### nothing to touch below this line
HITS=1

FROM_INITIAL="00000101"
TO_INITIAL="19800101"

curl --globoff --header "Accept-Encoding: gzip" "http://lobid.org/resources/search?q=describedBy.dateCreated:[$FROM_INITIAL%20TO%20$TO_INITIAL]&format=bulk" > $FROM_INITIAL-$TO_INITIAL.jsonl.gz

while [ $HITS != 0 ] ; do
	TO=$(expr $FROM + $INC )
	HITS=$(curl --globoff "http://lobid.org/resources/search?q=describedBy.dateCreated:[$FROM%20TO%20$TO]&size=1"|jq .totalItems)
	echo "getting $FROM to $TO with total items: $HITS"
	curl --globoff --header "Accept-Encoding: gzip" "http://lobid.org/resources/search?q=describedBy.dateCreated:[$FROM%20TO%20$TO]&format=bulk" > $FROM-$TO.jsonl.gz
	GOT_HITS=$(zcat $FROM-$TO.jsonl.gz |wc -l)
	echo "Got hits ($GOT_HITS), expected ($HITS)"
	if [ $HITS != $GOT_HITS ]; then
		echo "Got lesser hits than expected - cancelling !"
		echo "Wait a bit and set FROM manually to retry beginning with '$FROM'."
		exit
	fi
	ALL_HITS=$(expr $ALL_HITS + $HITS)
	FROM=$TO
done
