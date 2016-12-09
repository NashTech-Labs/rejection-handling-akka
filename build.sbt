name := "sampleJson"

version := "1.0"

scalaVersion := "2.11.8"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-http" % "10.0.0",
  "net.liftweb"                %% "lift-json"                      % "2.6"
)