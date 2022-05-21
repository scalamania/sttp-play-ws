import sbt.Keys.scalacOptions

val scala2_12 = "2.12.15"
val scala2_13 = "2.13.8"

val scala2 = Seq(scala2_12, scala2_13)

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

lazy val testServerPort = settingKey[Int]("Port to run the http test server on")
lazy val startTestServer = taskKey[Unit]("Start a http server used by tests")

lazy val testServer = (project in file("testing-server"))
  .settings(
    name := "testing-server",
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-http" % "10.2.9",
      "ch.megard" %% "akka-http-cors" % "1.1.3",
      "com.typesafe.akka" %% "akka-stream" % "2.6.19",
      "com.softwaremill.sttp.client3" %% "testing-server" % "3.6.2"
    ),
    reStart / mainClass := Some("sttp.client3.testing.server.HttpServer"),
    reStart / reStartArgs := Seq(s"${(Test / testServerPort).value}"),
    reStart / fullClasspath := (Test / fullClasspath).value,
    testServerPort := 51823,
    startTestServer := reStart.toTask("").value,
    publishArtifact := false,
    publishLocal := {},
    publish := {}
  )

val testServerSettings = Seq(
  Test / test := (Test / test)
    .dependsOn(testServer / startTestServer)
    .value,
  Test / testOnly := (Test / testOnly)
    .dependsOn(testServer / startTestServer)
    .evaluated,
  Test / testOptions += Tests.Setup(() => {
    val port = (testServer / testServerPort).value
    PollingUtils.waitUntilServerAvailable(new URL(s"http://localhost:$port"))
  })
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
  Compile / scalaSource := (LocalProject("root") / baseDirectory).value / "src" / "main" / "scala",
  Test / scalaSource := (LocalProject("root") / baseDirectory).value / "src" / "test" / "scala",
  Test / unmanagedResourceDirectories ++= Seq(
    (LocalProject("root") / baseDirectory).value / "src" / "test" / "resources"
  ),
  Test / fork := true
)

lazy val root: Project = (project in file("."))
  .settings(commonSettings)
  .settings(testServerSettings)
  .settings(
    name := "sttp-play-ws",
    crossScalaVersions := scala2,
    libraryDependencies ++= Seq(
      "com.typesafe.play" %% "play-ws" % "2.8.15" % Provided,
      "com.typesafe.play" %% "play-ahc-ws" % "2.8.15" % Test,
      "com.softwaremill.sttp.client3" %% "core" % "3.6.2" % Provided,
      ("com.softwaremill.sttp.client3" %% "core" % "3.6.2" classifier "tests") % Test,
      "org.scalatest" %% "scalatest" % "3.2.12" % Test,
      "com.typesafe.akka" %% "akka-http" % "10.2.9" % Test,
      "com.typesafe.akka" %% "akka-stream" % "2.6.19" % Test,
      "ch.megard" %% "akka-http-cors" % "1.1.3" % Test,
      "ch.qos.logback" % "logback-classic" % "1.2.11" % Test
    ),
    scalacOptions := defaultCompilerOptions
  )
