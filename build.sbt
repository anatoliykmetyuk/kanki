val ScalaVer = "2.12.2"

val Cats          = "0.9.0"
val Shapeless     = "2.3.2"
val KindProjector = "0.9.3"

val Jsoup        = "1.10.2"
val ApacheIO     = "2.5"
val ApacheCodecs = "1.10"
val ScalaTest    = "3.0.3"

val Swing = "2.0.0"

val Akka  = "2.5.1"

lazy val commonSettings = Seq(
  name    := "krdic-scraper"
, version := "0.1.0"
, scalaVersion := ScalaVer
, libraryDependencies ++= Seq(
    "org.typelevel"  %% "cats"      % Cats
  , "com.chuusai"    %% "shapeless" % Shapeless

  , "org.jsoup"     % "jsoup"         % Jsoup
  , "commons-io"    % "commons-io"    % ApacheIO
  , "commons-codec" % "commons-codec" % ApacheCodecs

  , "org.scala-lang.modules" %% "scala-swing" % Swing

  , "com.typesafe.akka" %% "akka-actor" % Akka

  , "org.scalatest" %% "scalatest" % ScalaTest % "test"
  )
, addCompilerPlugin("org.spire-math" %% "kind-projector" % KindProjector)
, scalacOptions ++= Seq(
      "-deprecation",
      "-encoding", "UTF-8",
      "-feature",
      "-language:existentials",
      "-language:higherKinds",
      "-language:implicitConversions",
      "-language:experimental.macros",
      "-unchecked",
      // "-Xfatal-warnings",
      "-Xlint",
      // "-Yinline-warnings",
      "-Ywarn-dead-code",
      "-Xfuture",
      "-Ypartial-unification")
, fork := true

, assemblyOutputPath in assembly := file("./deploy/krdicscraper.jar")
)

lazy val root = (project in file("."))
  .settings(commonSettings)
  .settings(
    initialCommands := "import krdicscraper._"
  )
