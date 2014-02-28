name := """no-exceptions"""

organization := "coop.plausible"

version := "1.0"

scalaVersion := "2.10.3"

libraryDependencies ++= Seq(
  "org.scala-lang"          %   "scala-compiler"    % scalaVersion.value % "provided",
  "org.specs2"              %%  "specs2"            % "2.3.8"            % "test"
)

scalacOptions ++= Seq(
    "-unchecked",
    "-deprecation",
    "-Xlint",
    "-Ywarn-dead-code",
    "-Xfatal-warnings",
    "-target:jvm-1.7",
    "-encoding", "UTF-8"
)
