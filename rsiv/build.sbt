lazy val commonSettings = Seq(
	organization := "net.marta",
	version := "0.1.0-SNAPSHOT",
	scalaVersion := "2.13.1",
	javacOptions ++= Seq(
		"-target", "11",
		"-source", "11",
		"-Xlint:all"),
	scalacOptions ++= Seq(
		"-Ymacro-annotations",
		"-Ybackend-parallelism", "12",
		"-P:bm4:no-filtering:y",
		"-P:bm4:no-tupling:y",
		"-P:bm4:no-map-id:y",
	),
)

lazy val compilerPlugins = Seq(
	addCompilerPlugin("org.typelevel" %% "kind-projector" % "0.10.3"),
	addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1"),
)

val osName: SettingKey[String] = SettingKey[String]("osName")

osName := (System.getProperty("os.name") match {
	case name if name.startsWith("Linux")   => "linux"
	case name if name.startsWith("Mac")     => "mac"
	case name if name.startsWith("Windows") => "win"
	case _                                  => throw new Exception("Unknown platform!")
})

lazy val javaFxVersion = "13.0.1"

lazy val rsiv = project.in(file(".")).settings(
	compilerPlugins, commonSettings,
	scalacOptions ~= filterConsoleScalacOptions,
	name := "rsiv",
	resolvers ++= Seq(
		Resolver.mavenLocal,
		Resolver.jcenterRepo,
	),
	scalacOptions += "-Ymacro-annotations",
	libraryDependencies ++= Seq(

		"com.google.guava" % "guava" % "28.1-jre",
		"net.kurobako" % "gesturefx" % "0.5.0",
		"com.fazecast" % "jSerialComm" % "2.5.2",

		"org.sputnikdev" % "bluetooth-manager" % "1.5.3",
		"org.sputnikdev" % "bluetooth-manager-tinyb" % "1.3.3",
		"org.sputnikdev" % "bluetooth-manager-bluegiga" % "1.2.3"
		exclude("com.zsmartsystems.bluetooth.bluegiga", "com.zsmartsystems.bluetooth.bluegiga"),

		"ch.qos.logback" % "logback-classic" % "1.2.3",


		"org.scala-lang.modules" %% "scala-parallel-collections" % "0.2.0",

		"com.lihaoyi" %% "pprint" % "0.5.6",
		"com.lihaoyi" %% "upickle" % "0.8.0",


		"org.typelevel" %% "cats-core" % "2.0.0",
		"org.typelevel" %% "cats-free" % "2.0.0",
		"org.typelevel" %% "cats-effect" % "2.0.0",
		"org.typelevel" %% "kittens" % "2.0.0",
		"com.chuusai" %% "shapeless" % "2.3.3",

		"com.github.pathikrit" %% "better-files" % "3.8.0",


		"org.scalafx" %% "scalafx" % "12.0.2-R18",
		"org.scalafx" %% "scalafxml-core-sfx8" % "0.5",


		"org.fxmisc.easybind" % "easybind" % "1.0.3",

		"org.scalatest" %% "scalatest" % "3.0.8" % "test",

	) ++ Seq("controls", "graphics", "media", "web", "base", "fxml").map {
		module => "org.openjfx" % s"javafx-$module" % javaFxVersion classifier (osName).value
	},
)


 
