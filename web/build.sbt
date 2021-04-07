name := "lobid-resources-web"

version := "0.2.1-SNAPSHOT"

scalaVersion := "2.11.11"

// used by the webhook listener invoking the ETL
unmanagedResourceDirectories in Compile += baseDirectory.value / "../src/main/resources/alma"

libraryDependencies ++= Seq(
  cache,
  javaWs,
  "com.typesafe.play" % "play-test_2.11" % "2.4.11",
  "org.elasticsearch.plugin" % "parent-join-client" % "5.6.3" withSources(),
  "org.elasticsearch" % "elasticsearch" % "5.6.3" withSources(),
  "org.elasticsearch.client" % "transport" % "5.6.3" withSources()
    // otherwise javaWs won't work
    exclude ("io.netty", "netty"),
  "org.mockito" % "mockito-core" % "1.9.5",
  "com.github.jsonld-java" % "jsonld-java" % "0.4.1",
  "com.github.jsonld-java" % "jsonld-java-jena" % "0.4.1",
  "org.apache.jena" % "jena-arq" % "2.9.3",
  "com.google.gdata" % "core" % "1.47.1" exclude ("com.google.guava", "guava"),
  "org.apache.logging.log4j" % "log4j-core" % "2.9.1",
  "org.apache.logging.log4j" % "log4j-api" % "2.9.1",
  "org.apache.logging.log4j" % "log4j-1.2-api" % "2.9.1",
  "org.easytesting" % "fest-assert" % "1.4" % "test"
)

resolvers += "Local Maven Repository" at Path.userHome.asFile.toURI.toURL + ".m2/repository"

libraryDependencies += "org.lobid" % "lobid-resources" % "0.4-SNAPSHOT" changing()

lazy val root = (project in file(".")).enablePlugins(PlayJava)

javacOptions ++= Seq("-source", "1.8", "-target", "1.8")

import com.typesafe.sbteclipse.core.EclipsePlugin.EclipseKeys

EclipseKeys.projectFlavor := EclipseProjectFlavor.Java // Java project. Don't expect Scala IDE
EclipseKeys.createSrc := EclipseCreateSrc.ValueSet(EclipseCreateSrc.ManagedClasses, EclipseCreateSrc.ManagedResources) // Use .class files instead of generated .scala files for views and routes
EclipseKeys.preTasks := Seq(compile in Compile) // Compile the project before generating Eclipse files, so that .class files for views and routes are present
