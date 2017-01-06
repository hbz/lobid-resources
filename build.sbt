name := "lobid-resources-web"

version := "0.1.0-SNAPSHOT"

scalaVersion := "2.11.2"

libraryDependencies ++= Seq(
  cache,
  javaWs,
  "com.typesafe.play" % "play-test_2.11" % "2.3.10",
  "org.elasticsearch" % "elasticsearch" % "2.3.3" withSources()
    // otherwise javaWs won't work
    exclude ("io.netty", "netty"),
  "org.mockito" % "mockito-core" % "1.9.5",
  "com.github.jsonld-java" % "jsonld-java" % "0.3",
  "com.github.jsonld-java" % "jsonld-java-jena" % "0.3",
  "org.apache.jena" % "jena-arq" % "2.9.3"
)

lazy val root = (project in file(".")).enablePlugins(PlayJava)

javacOptions ++= Seq("-source", "1.8", "-target", "1.8")
