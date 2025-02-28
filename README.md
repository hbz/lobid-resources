# README

## About

Transform Alma MARC-XML to JSON for Elasticsearch indexing with
[Metafacture](https://github.com/culturegraph/metafacture-core/wiki),
serve API and UI with [Play Framework](https://playframework.com/).

Aleph MAB-XML was supported up to tag \`0.5.0\`.

This repo replaces the lobid-resources part of
<https://github.com/lobid/lodmill>.

For information about the Lobid architecture and development process,
see <http://hbz.github.io/#lobid>.

## Build

[![Build No Status](https://github.com/hbz/lobid-resources/workflows/Build/badge.svg?branch=master)](https://github.com/hbz/lobid-resources/actions?query=branch%3Amaster)

Prerequisites: Java 11, Maven 3; verify with `mvn -version`

Create and change into a folder where you want to store the projects:

- `mkdir ~/git ; cd ~/git`

Build lobid-resources:

- `git clone https://github.com/hbz/lobid-resources.git`
- `cd lobid-resources`
- `mvn clean install`

Build the web application:

- `cd web`
- Make sure you have the proper version of sbt:
- `sbt --version sbt`
- `version in this project: 1.8.2`

Then follow the script in `web/monit_restart.sh`.

See the `.github/workflows/build.yml` file for details on the CI config
used by Github Actions.

## Eclipse setup

Replace `test` with other Play commands, e.g.
`"eclipse with-source=true"` (generate Eclipse project config files,
then import as existing project in Eclipse), `~ run` (run in test mode,
recompiles changed files on save, use this to keep your Eclipse project
in sync while working, make sure to enable automatic workspace refresh
in Eclipse: `Preferences` \> `General` \> `Workspace` \>
`Refresh using native hooks or polling`).

## Production

Copy `web/conf/resources.conf_template` to `conf/resources.conf` and
configure that file to your need.

Use `"start 8000"` to run in production background mode on port 8000
(hit Ctrl+D to exit logs). To restart a production instance running in
the background, you can use the included `restart.sh` script (configured
to use port 8000). For more information, see the [Play
documentation](https://playframework.com/documentation/2.4.x/Home).

## Example of getting the data

In the online test the data is indexed into a living elasticsearch
instance.
This instance is only reachable within our internal network, thus this
test
must be executed manually. Then elasticsearch can be looked up like
this:

<https://lobid.org/resources/990054215550206441>

For querying it you can use the elasticsearch query DSL, like:

<https://lobid.org/resources/search?q=title:%22Moby%20dick%22>

## Developer instructions

This section explains how to make a successful build after changing the
transformations,
how to update the JSON-LD and its context, and how to index the data.

## Changing transformations

After changing the
[fix](https://github.com/hbz/lobid-resources/blob/master/src/main/resources/alma/alma.fix)
the build must be executed:

`mvn clean install`

Two possible outcomes:

- **BUILD SUCCESS**: the tested resources don't reflect the changes.
  In this case you should add an Alma-MARC-XML resource to
  [src/test/resources/alma-fix/](https://github.com/hbz/lobid-resources/blob/master/src/test/resources/alma-fix)
  that *would* reflect your changes.

<!-- -->

- **BUILD FAILURE**: the newly generated data isn't equal to the test
  resources.
  This is a good thing because you wanted the change.

Doing `mvn test -DgenerateTestData=true` the test data is generated and
also updated in the filesystem.
These new data will now act as the template for sucessful tests. So, if
you would rebuild now, the build will pass successfully.
You just must approve the new outcome by committing it.

Now you must approve the new outcome.
Let's see what has changed:

`git status`

Let's make a diff on the changes, e.g. all JSON-LD documents:

`git diff src/test/resources/alma-fix/`

You can validate the generated JSON-LD documents with the provided
schemas:

`cd src/test/resources; bash validateJsonTestFiles.sh`

If you are satisfied with the changes, go ahead and add and commit them:

`git add src/test/resources/alma-fix/; git commit`

Do this respectivly for all other test files (Ntriples …).
If you've added and commited everything, check again if all is ok:

`mvn clean install`

This should result in **BUILD SUCCESS**. Push your changes.

Check if the play tests work, e.g.:

`cd web; sbt "test:testOnly *IntegrationTest"`

If that fails, check the tests. Most of the time the "fix" is to update
the test
as new data introduce more/less hits.
Then, at last:

You're done :)

## Tables as gitsubmodules

Some lookup tables are provided through gitsubmodules (s.
`.gitmodules`).
To initialize the submodules do
`git submodule update --init --remote`.
To add a submodule do `git submodule add $repoUrl`.
To make a `git pull` also
update these tables you can e.g. do
`git config --local submodule.recurse true` once and
`git submodule update --recursive --remote` after every `git pull` !
This is necessary
to be on the HEAD of the master of the submodules.

## Propagate the context.json to lobid-resources-web

The generated *context.jsonld* is automatically written to the proper
directory
so that it is automatically deployed when the web application is
deployed.

When the small test set is indexed by using *buildAndETLTestAlmaFix.sh*
deploy your branch in
the staging directory of the web application. The *context* for the
resources is adapted
to use the "staging.lobid.org"-domain and thus the
staging-*context.jsonld* will resolve using the one in that directory.

### Elasticsearch index

We use the plugin
[org.xbib.elasticsearch:elasticsearch-plugin-bundle:5.4.1.0](https://github.com/jprante/elasticsearch-plugin-bundle#elasticsearch-5x)
Follow the [installation guide for this
plugin.](https://github.com/hbz/lobid-resources/issues/1615#issuecomment-1516331254)

Have a look at the [maintaining
guide.](https://github.com/hbz/lobid-resources/wiki/Maintaining-lobid-API)

## License

Eclipse Public License: <http://www.eclipse.org/legal/epl-v10.html>
