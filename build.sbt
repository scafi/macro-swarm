// Constants
val defaultScalaVersion = "2.13.6"

inThisBuild(
  List(
    sonatypeProfileName := "it.unibo.scafi", // Your profile name of the sonatype account
    Test / publishArtifact := false,
    pomIncludeRepository := { _ => false }, // no repositories show up in the POM file
    licenses := Seq("Apache-2.0" -> url("https://www.apache.org/licenses/LICENSE-2.0")),
    /*homepage := Some(url("https://scafi.github.io/")),
    scmInfo := Some(
      ScmInfo(
        url("https://github.com/scafi/scafi"),
        "scm:git:git@github.org:scafi/scafi.git"
      )
    ),*/
    developers := List(
      Developer(
        id = "cric96",
        name = "Gianluca Aguzzi",
        email = "gianluca.aguzzi@unibo.it",
        url = url("https://cric96.github.io/")
      )
    ),
    scalaVersion := defaultScalaVersion
  )
)

lazy val core = project.settings(
  organization := "it.unibo.scafi",
  name := "macro-swarm-core",
  scalaVersion := defaultScalaVersion,
  libraryDependencies += "it.unibo.scafi" %% "scafi-core" % "1.1.7"
)

lazy val alchemist = project
  .dependsOn(core)
  .settings(
    scalaVersion := defaultScalaVersion,
    organization := "it.unibo.scafi",
    name := "macro-swarm-alchemist",
    libraryDependencies += "it.unibo.alchemist" % "alchemist-incarnation-scafi" % "28.5.4"
  )

lazy val `macro-swarm` = project
  .in(file("."))
  .aggregate(core, alchemist)
  .settings(
    organization := "it.unibo.scafi",
    name := "macro-swarm",
    scalaVersion := defaultScalaVersion,
    // Avoid double publishing
    publishArtifact := false,
    publish := {},
    publishLocal := {},
    packagedArtifacts := Map.empty,

  )
