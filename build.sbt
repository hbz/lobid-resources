name := "nwbib"

version := "0.1.0-SNAPSHOT"

libraryDependencies ++= Seq(
  cache,
  "org.elasticsearch" % "elasticsearch" % "1.1.0" withSources()
)     

play.Project.playJavaSettings
