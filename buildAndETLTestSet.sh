#!/bin/bash
# Checks if all resources listed in the file "testIds.txt" are vailable as
# files in their raw format (that is: AlephMabXml). If not, get them. Add them
# to the resources serving as junit test. Then ETL the data reusing the script
# "startHbz01ToLobidResources.sh". See at the bottom of this script for the
# hardcoded server and cluster name etc.
# The stored files are reused so that following buildings of the test set
# will be fast.

# Adapt this path to your needs. Avoid /tmp and rebooting, see above.
TARGET=~/lobidTestResources
TEST_FILE="test.tar.bz2"
THIS="$(pwd)"
BRANCH=$1

if [ -z $1 ]; then
	echo "Please provide the branch name as first parameter "
	exit
fi

# build test set:
echo "building hbz01 test set ..."
mkdir $TARGET
# include unit test files
cd $TARGET

tar xfj $THIS/src/test/resources/hbz01XmlClobs.tar.bz2
cd -
# include list
for i in $(cat testIds.txt); do
        if [ ! -f $TARGET/$i ]
                then curl "http://lobid.org/resource?id=$i&format=source" > $TARGET/$i
                echo "Added new resource $i"
        fi
done

rm $TEST_FILE
tar cfj $TEST_FILE $TARGET

# index test resources:
mkdir log
echo "indexing hbz01 test set ..."
bash startHbz01ToLobidResources.sh $BRANCH test.tar.bz2 resources "-staging" gaia.hbz-nrw.de lobid-gaia create > log/testSet-startHbz01ToLobidResources.sh.log

