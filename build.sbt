name := "nwbib"

version := "0.1.0-SNAPSHOT"

scalaVersion := "2.11.2"

libraryDependencies ++= Seq(
  cache,
  javaWs,
  "com.typesafe.play" % "play-test_2.11" % "2.3.10",
  "org.elasticsearch" % "elasticsearch" % "1.3.2" withSources(),
  "org.mockito" % "mockito-core" % "1.9.5"
)

lazy val root = (project in file(".")).enablePlugins(PlayJava)

javacOptions ++= Seq("-source", "1.8", "-target", "1.8")
