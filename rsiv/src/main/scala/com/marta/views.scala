package com.marta

import scalafx.geometry.{Insets, Point2D, Pos}
import scalafx.scene.control.Label
import scalafx.scene.layout.VBox
import scalafx.scene.paint.Color
import scalafx.scene.shape.QuadCurve

class FlexLine(length: Double) {
	val root = new QuadCurve {
		fill = Color.Transparent
		stroke = Color.White
		strokeWidth = 2
		startX = 0
		startY = 0
		controlX = 0

	}
	def bend(deg: Double): Unit = {
		val rad      = (deg - 90.0).toRadians
		val endPoint = new Point2D(math.cos(rad) * length, math.sin(rad) * length)
		root.controlY = -length * (2.0 / 3)
		root.endX = endPoint.x
		root.endY = endPoint.y
	}
	bend(0)
}


class BendView(name: String, length: Double) {
	private val line  = new FlexLine(length)
	private val label = new Label(name)
	val root = new VBox(
		line.root,
		new Label(name),
		label,
	) {
		alignment = Pos.BottomLeft
		minWidth = length
		minHeight = length * 2
		padding = Insets(4)
	}

	def update(resistance: Double, bend: Double): Unit = {
		label.text = f"$bend%.2f° ($resistance%.2fΩ)"
		line.bend(bend)
	}
}