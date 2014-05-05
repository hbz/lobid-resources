name := "nwbib"

version := "0.1.0-SNAPSHOT"

libraryDependencies ++= Seq(
  cache,
  "com.typesafe.play" % "play-test_2.10" % "2.2.2",
  "org.elasticsearch" % "elasticsearch" % "1.1.0" withSources()
)     

play.Project.playJavaSettings

javacOptions ++= Seq("-source", "1.8", "-target", "1.8")
