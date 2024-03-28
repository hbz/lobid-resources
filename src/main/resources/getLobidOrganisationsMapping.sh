# description: get cron scheduled  lobid-organisations labels map. See #1903.
# checks whether lines > 18k before overriding tsv
# author: dr0i
# date: 2023-11-27

FNAME="lobidOrganisationsMapping.tsv"
curl "https://lobid.org/organisations/search?q=_exists_%3Aisil+OR+type%3ACollection&format=tsv:id,isil,sigel,name&size=25000" > ${FNAME}.tmp
if [ $(wc -l ${FNAME}.tmp|cut -d ' ' -f1) -gt 18000 ]; then
  mv ${FNAME}.tmp alma/maps/$FNAME
fi
