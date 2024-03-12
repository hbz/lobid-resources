name := "lobid-resources-web"

version := "1.0.1-SNAPSHOT"

scalaVersion := "2.11.12"

// used by the webhook listener invoking the ETL
unmanagedResourceDirectories in Compile += baseDirectory.value / "../src/main/resources/"

libraryDependencies ++= Seq(
  cache,
  javaWs,
  "com.typesafe.play" % "play-test_2.11" % "2.4.11",
  "org.apache.logging.log4j" % "log4j-core" % "2.9.1",
  "org.elasticsearch.plugin" % "parent-join-client" % "5.6.3",
  "org.mockito" % "mockito-core" % "1.9.5" %Test,
  "com.google.gdata" % "core" % "1.47.1" exclude ("com.google.guava", "guava"),
  "org.easytesting" % "fest-assert" % "1.4" %Test,
  "org.xbib.elasticsearch.plugin" % "elasticsearch-plugin-bundle" % "5.4.1.0",
  "org.lobid" % "lobid-resources" % "1.0.0" changing()
)

resolvers += "Local Maven Repository" at Path.userHome.asFile.toURI.toURL + ".m2/repository"

lazy val root = (project in file(".")).enablePlugins(PlayJava)

javacOptions ++= Seq("-source", "11", "-target", "11")

import com.typesafe.sbteclipse.core.EclipsePlugin.EclipseKeys

EclipseKeys.projectFlavor := EclipseProjectFlavor.Java // Java project. Don't expect Scala IDE
EclipseKeys.createSrc := EclipseCreateSrc.ValueSet(EclipseCreateSrc.ManagedClasses, EclipseCreateSrc.ManagedResources) // Use .class files instead of generated .scala files for views and routes
EclipseKeys.preTasks := Seq(compile in Compile) // Compile the project before generating Eclipse files, so that .class files for views and routes are present

