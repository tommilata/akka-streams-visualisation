name := "akka-streams"

version := "1.0"

scalaVersion := "2.12.4"

libraryDependencies ++= Seq( 
  "com.typesafe.akka" %% "akka-actor" % "2.5.9",
  "com.typesafe.akka" %% "akka-http" % "10.0.11",
  "com.typesafe.akka" %% "akka-stream" % "2.5.9",
  "com.typesafe.akka" %% "akka-stream-testkit" % "2.5.9" % Test
)

