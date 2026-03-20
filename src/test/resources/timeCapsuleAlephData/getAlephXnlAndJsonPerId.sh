# Alle Einträge in ids.txt werden an die Funktion übergeben. Die macht dann zwei
# Lookups und speichert das json und xml.

getAlephData() {
  curl https://aleph.lobid.org/resources/$1.json > $1_aleph.json
  curl https://lobid.org/hbz01/$1 > $1_aleph.xml
}


 for i in $(cat ids.txt); do
   getAlephData $i
 done

