
val clientVersion = "0.0.21"

lazy val root = (project in file(".")).
  settings(
    name := "client",
    version := "1.0",
    scalaVersion := "2.11.8",
    retrieveManaged := true,
    libraryDependencies ++= Seq(
      "io.prometheus" % "simpleclient" % clientVersion,
      "io.prometheus" % "simpleclient_hotspot" % clientVersion,
      "io.prometheus" % "simpleclient_servlet" % clientVersion,
      "io.prometheus" % "simpleclient_pushgateway" % clientVersion,
      "org.eclipse.jetty" % "jetty-servlet" % "9.4.2.v20170220",
      "com.typesafe.akka" % "akka-actor_2.11" % "2.4.17"
    )
  ).
  settings(
    assemblyJarName in assembly := "dummy_client.jar"
  )

