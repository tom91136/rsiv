package com.marta

import cats.implicits._
import com.google.common.io.Resources
import scalafx.Includes._
import scalafx.application.JFXApp
import scalafx.application.JFXApp.PrimaryStage
import scalafx.scene.Scene
import scalafxml.core.{FXMLView, NoDependencyResolver}


object Main extends JFXApp {
	stage = new PrimaryStage {
		title = "RSIV"
		width = 1200
		height = 900
		scene = new Scene(FXMLView(Resources.getResource("App.fxml"), NoDependencyResolver))
	}

}
