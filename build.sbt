name := """no-exceptions"""

organization := "coop.plausible"

scalaVersion := "2.10.3"

crossScalaVersions := Seq("2.10.3", "2.11.0-M8")

libraryDependencies ++= Seq(
  "org.scala-lang"          %   "scala-compiler"    % scalaVersion.value % "provided",
  "org.scala-lang"          %   "scala-compiler"    % scalaVersion.value % "test", // We need to supply the library when testing
  "org.specs2"              %%  "specs2"            % "2.3.7"            % "test"
)

scalacOptions ++= Seq(
    "-unchecked",
    "-deprecation",
    "-feature",
    "-Xlint",
    "-Ywarn-dead-code",
    "-Xfatal-warnings",
    "-target:jvm-1.7",
    "-encoding", "UTF-8"
)

/*
 * If necessary for the given target Scala version, add a versioned scala_<major>.<minor> directory to the
 * unmanagedSourceDirectories list. This allows us to adapt to changes in the experimental macro APIs. We use the macro
 * APIs to implement unit testing, as well as to provide our own experimental no-exceptions macro.
 */
unmanagedSourceDirectories in Compile <<= (unmanagedSourceDirectories in Compile, sourceDirectory in Compile, scalaVersion) { (allDirs: Seq[File], scalaDir: File, version: String) =>
  /* Determine the target binary version */
  val binaryVersion = CrossVersion.scalaApiVersion(version).orElse(CrossVersion.partialVersion(version)).map {
    /* Handle 2.10; first version where macros appeared */
    case (2, 10) =>
      "2.10"
    /* Handle 2.11+; in theory, the API should be stable afterwards, so we map all later versions to '2.11' */
    case (major: Int, minor: Int) if major >= 2 && minor >= 11 =>
      "2.11"
    /* This will only be reachable if crossScalaVersions is updated to include a version < 2.10 (unlikely). */
    case _ =>
      throw new RuntimeException(s"Unsupported version: $version")
  }.getOrElse {
    throw new RuntimeException(s"Could not parse target Scala version $version")
  }
  /* Append the target version to the primary Scala source directory */
  val crossSourceDirectory = new File(scalaDir, "scala_" + binaryVersion)
  /* If the directory exists, add it to the ... */
  if (crossSourceDirectory.isDirectory) {
    allDirs :+ crossSourceDirectory
  } else {
    allDirs
  }
}

autoAPIMappings := true

// Fork when testing; this ensures that we get a useable classpath for our compiler plugin
fork in Test := true

// Load our plugin in the REPL
scalacOptions in console in Compile <+= (packageBin in Compile) map { p =>
  s"-Xplugin:$p"
}

// Provide the plugin path to our tests
testOptions <+= (packageBin in Compile) map { p =>
  Tests.Argument("nx-plugin-path", p.toString)
}


publishTo := {
  val nexus = "https://opensource.plausible.coop/nexus/"
  if (version.value.trim.endsWith("SNAPSHOT"))
    Some("snapshots" at nexus + "content/repositories/snapshots")
  else
    Some("releases"  at nexus + "content/repositories/releases")
}

publishMavenStyle := true

pomIncludeRepository := { _ => false }
