import sbt.Keys.scalacOptions

val defaultCompilerOptions = Seq(
  "-deprecation",
  "-explaintypes",
  "-feature",
  "-language:higherKinds",
  "-unchecked",
  "-Xfatal-warnings",
  "-Ywarn-extra-implicit",
  "-Yrangepos",
  "-Xlint:unused"
)

lazy val root: Project = (project in file("."))
  .settings(
    publishArtifact := false,
    publishLocal := {},
    publish := {}
  )
  .aggregate(
    play28Project
  )

val commonSettings: Seq[Def.Setting[_]] = inThisBuild(
  List(
    organization := "io.github.scalamania",
    scalaVersion := "2.12.11",
    organizationName := "scalamania",
    licenses += ("Apache-2.0", new URL("https://www.apache.org/licenses/LICENSE-2.0.txt")),
    homepage := Some(url("https://github.com/scalamania/sttp-play-ws")),
    sonatypeCredentialHost := "s01.oss.sonatype.org",
    developers := List(
      Developer(
        id = "veysiertekin",
        name = "scalamania",
        email = "info@test.com",
        url = new URL("https://github.com/scalamania/sttp-play-ws")
      )
    )
  )
) ++ Seq(
  scalaSource in Compile := (LocalProject("root") / baseDirectory).value / "common" / "src" / "main" / "scala",
  scalaSource in Test := (LocalProject("root") / baseDirectory).value / "common" / "src" / "test" / "scala",
  unmanagedResourceDirectories in Test ++= Seq(
    (LocalProject("root") / baseDirectory).value / "common" / "src" / "test" / "resources"
  ),
  fork in Test := true,
  libraryDependencies ++= Seq(
    "com.softwaremill.sttp.client3" %% "core" % "3.5.1",
    ("com.softwaremill.sttp.client3" %% "core" % "3.5.1" classifier "tests") % Test,
    "org.scalatest" %% "scalatest" % "3.2.11" % Test,
    "com.typesafe.akka" %% "akka-http" % "10.2.9" % Test,
    "com.typesafe.akka" %% "akka-stream" % "2.6.19" % Test,
    "ch.megard" %% "akka-http-cors" % "1.1.3" % Test,
    "ch.qos.logback" % "logback-classic" % "1.2.3" % Test
  )
)

lazy val play28Project = Project("play28", file("play28"))
  .settings(commonSettings)
  .settings(
    name := "sttp-play-ws",
    crossScalaVersions := Seq("2.12.15", "2.13.8"),
    libraryDependencies ++= Seq(
      "com.typesafe.play" %% "play-ws" % "2.8.14" % Provided,
      "com.typesafe.play" %% "play-ahc-ws" % "2.8.14" % Provided
    ),
    scalacOptions := defaultCompilerOptions
  )
