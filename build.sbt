// format: off
val scala33               = "3.3.4"
val junitInterfaceVersion = "0.11"
val upickleVersion        = "3.3.1"
val sporesVersion         = "0.2.0-SNAPSHOT"

ThisBuild / organization     := "com.jspenger"
ThisBuild / organizationName := "Jonas Spenger"

ThisBuild / description := "Durable and fault tolerant actor library for Scala 3."
ThisBuild / licenses    := List("Apache-2.0" -> new URL("https://www.apache.org/licenses/LICENSE-2.0.txt"))

ThisBuild / scalaVersion := scala33
ThisBuild / version      := "0.1.0-SNAPSHOT"

ThisBuild / developers := List(
  Developer(
    id    = "jspenger",
    name  = "Jonas Spenger",
    email = "jonas.spenger@gmail.com",
    url   = url("https://github.com/jspenger")
  )
)
// format: on

lazy val root = crossProject(JVMPlatform, JSPlatform, NativePlatform)
  .in(file("durable-root"))
  .settings(
    name := "durable-actor",
    libraryDependencies += "com.phaller" %%% "spores3" % sporesVersion,
    libraryDependencies += "com.lihaoyi" %%% "upickle" % upickleVersion,
    libraryDependencies += "com.novocode" % "junit-interface" % junitInterfaceVersion % Test,
  )
  .jsConfigure(_.enablePlugins(ScalaJSJUnitPlugin))
  .nativeConfigure(_.enablePlugins(ScalaNativeJUnitPlugin))

lazy val commonSettings = Seq(
  organization := "com.jspenger",
  scalaVersion := scala33,
  libraryDependencies += "com.phaller" %%% "spores3" % sporesVersion,
  libraryDependencies += "com.lihaoyi" %%% "upickle" % upickleVersion,
  publish / skip := true,
)

lazy val nativeSettings = Seq(
  nativeConfig := nativeConfig.value.withMode(scala.scalanative.build.Mode.releaseFast),
)

lazy val jsSettings = Seq(
  scalaJSStage := FullOptStage,
  scalaJSUseMainModuleInitializer := true,
)

lazy val fibonacci = crossProject(JVMPlatform)
  .in(file("durable-example"))
  .settings(
    name := "durable-example-fibonacci",
    commonSettings,
    Compile / mainClass := Some("durable.example.Fibonacci"),
    target := baseDirectory.value / "target" / "fibonacci",
  )
  .dependsOn(root)

lazy val count = crossProject(JVMPlatform, JSPlatform, NativePlatform)
  .in(file("durable-example"))
  .settings(
    name := "durable-example-count",
    commonSettings,
    Compile / mainClass := Some("durable.example.Count"),
    target := baseDirectory.value / "target" / "count",
  )
  .jsSettings(jsSettings)
  .nativeSettings(nativeSettings)
  .dependsOn(root)

lazy val pingpong = crossProject(JVMPlatform, JSPlatform, NativePlatform)
  .in(file("durable-example"))
  .settings(
    name := "durable-example-pingpong",
    commonSettings,
    Compile / mainClass := Some("durable.example.PingPong"),
    target := baseDirectory.value / "target" / "pingpong",
  )
  .jsSettings(jsSettings)
  .nativeSettings(nativeSettings)
  .dependsOn(root)
