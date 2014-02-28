name := """no-exceptions"""

organization := "coop.plausible"

version := "1.0"

scalaVersion := "2.10.3"

crossScalaVersions := Seq("2.10.3", "2.11.0-M8")

libraryDependencies ++= Seq(
  "org.scala-lang"          %   "scala-compiler"    % scalaVersion.value % "provided",
  "org.specs2"              %%  "specs2"            % "2.3.7"            % "test"
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

// Load our plugin in the REPL
scalacOptions in console in Compile <+= (packageBin in Compile) map { p =>
  s"-Xplugin:$p"
}