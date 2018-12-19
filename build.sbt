name := "marketDataService"

version := "0.1"

scalaVersion := "2.12.8"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-stream" % "2.5.19",
  "com.typesafe.akka" %% "akka-stream-testkit" % "2.5.19" % Test
)
resolvers += "Sonatype Public" at "https://oss.sonatype.org/content/groups/public/"
libraryDependencies ++= Seq(
  "org.scodec" %% "scodec-bits" % "1.1.6",
  "org.scodec" %% "scodec-core" % "1.10.3"
)

val circeVersion = "0.10.0"

libraryDependencies ++= Seq(
  "io.circe" %% "circe-core",
  "io.circe" %% "circe-generic",
  "io.circe" %% "circe-parser"
).map(_ % circeVersion)