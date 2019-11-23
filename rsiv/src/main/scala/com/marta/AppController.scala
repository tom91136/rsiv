package com.marta

import java.time.format.{DateTimeFormatter, FormatStyle}
import java.time.{Instant, ZoneId}
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.{ArrayBlockingQueue, ExecutorService, Executors}

import cats.implicits._
import cats.kernel.Monoid
import com.marta.BLE.launchReadThread
import scalafx.Includes._
import scalafx.application.Platform
import scalafx.beans.property.ObjectProperty
import scalafx.collections.ObservableBuffer
import scalafx.scene.Node
import scalafx.scene.chart.{AreaChart, XYChart}
import scalafx.scene.control.{CheckBox, Label}
import scalafx.scene.layout.{HBox, Pane, Priority, StackPane}
import scalafx.scene.paint.Color
import scalafx.scene.shape._
import scalafxml.core.macros.sfxml

import scala.collection.immutable.SortedMap


@sfxml class AppController(root: StackPane,
						   overlay: Pane,
						   body: SVGPath,
						   lel: SVGPath, lcl: SVGPath, lex: SVGPath,
						   rel: SVGPath, rcl: SVGPath, rex: SVGPath,
						   breakTime: Label,
						   lexLabel: Label, lexPercent: Label,
						   lclLabel: Label, lclPercent: Label,
						   lelLabel: Label, lelPercent: Label,
						   rexLabel: Label, rexPercent: Label,
						   rclLabel: Label, rclPercent: Label,
						   relLabel: Label, relPercent: Label,
						   chart: AreaChart[String, Number],
						   sensors: CheckBox, sensorPane: Pane, sensorContainer: HBox,
						   history: AreaChart[String, Number]) {


	private val rexB = new BendView("rex", 100)
	private val rclB = new BendView("rcl", 100)
	private val relB = new BendView("rel", 100)
	private val rexS = new XYChart.Series[String, Number]
	private val rclS = new XYChart.Series[String, Number]
	private val relS = new XYChart.Series[String, Number]


	sensorContainer.children = Seq(rexB.root, rclB.root, relB.root)
	sensorContainer.children.foreach(HBox.setHgrow(_, Priority.Always))

	sensorPane.visible <== sensors.selected
	sensorPane.managed <== sensorPane.visible

	history.data = Seq(rexS.delegate, rclS.delegate, relS.delegate)


	//	def mkData = {
	//		val series = List.tabulate(1000)(i => Instant.now().plusSeconds(i)).map { t =>
	//			val arm = Arm(math.sin(t.toEpochMilli), math.cos(t.toEpochMilli), math.tan(t.toEpochMilli))
	//			t -> (arm, arm)
	//		}.to(SortedMap)
	//		Model(series)
	//	}

	private val model = ObjectProperty(Model())

	private val binds = lel -> lelLabel :: lcl -> lclLabel :: lex -> lexLabel ::
						rel -> relLabel :: rcl -> rclLabel :: rex -> rexLabel ::
						Nil

	private val labels = binds.map(_._2)

	binds.foreach { case (figure, label) =>
		val (arrow, update) = mkLeftDiagramArrows(label, figure)
		val hover           = label.hover || figure.hover
		hover.onChange { (_, _, h) =>
			figure.styleClass = Seq(if (h) "patch-selected" else "patch")
			labels.filterNot(_ == label).foreach(_.opacity = if (h) 0.5 else 1)
			update()
		}
		arrow.visible <== hover
		overlay.children += arrow
	}
	private final val formatter: DateTimeFormatter =
		DateTimeFormatter.ofLocalizedTime(FormatStyle.MEDIUM)
			.withZone(ZoneId.systemDefault());


	private def update(m: Model): Unit = {

		val grouped = m.series.groupBy(x => formatter.format(x._1)).map { case (k, xs) =>
			val ys           = xs.values.to(List)
			val N            = ys.length
			val (lSum, rSum) = ys.combineAll
			k -> (lSum.map(_ / N), rSum.map(_ / N))
		}.to(SortedMap)


		def percentage[A](xs: Seq[A], x: A) = (xs.count(_ == x).toDouble / xs.size) * 100.0

		val (lp, rp) = grouped.toList.map { case (_, (l, r)) =>
			def lift(x: Arm[Double]) = x.map(v => (v > Arm.InjuryThresholdDegree) :: Nil)
			lift(l) -> lift(r)
		}.combineAll.bimap(_.map(z => percentage(z, true)), _.map(z => percentage(z, true)))


		def percent(x: Double) = s"${x.round}%"
		lexPercent.text = percent(lp.extensor)
		lclPercent.text = percent(lp.ligament)
		lelPercent.text = percent(lp.elbow)
		rexPercent.text = percent(rp.extensor)
		rclPercent.text = percent(rp.ligament)
		relPercent.text = percent(rp.elbow)


		val xs = grouped.map { case (t, (l, r)) =>
			XYChart.Data[String, Number](t, r.elbow)
		}.toSeq
		chart.data = Seq(XYChart.Series[String, Number](ObservableBuffer.from(xs)))

		val (lArm, rArm) = m.series.lastOption.map(_._2).getOrElse(Monoid[Arm[Double]].empty -> Monoid[Arm[Double]].empty)
		rexB.update(0, rArm.extensor)
		relB.update(0, rArm.elbow)
		rclB.update(0, rArm.ligament)

		def trimToLen[A](xs: ObservableBuffer[A], len: Int) = {
			if (xs.size > len) xs.remove(0, xs.size - len)
			xs
		}

		val now = Instant.now().toString

		trimToLen(rexS.getData, 128) += XYChart.Data[String, Number](now, rArm.extensor)
		trimToLen(relS.getData, 128) += XYChart.Data[String, Number](now, rArm.elbow)
		trimToLen(rclS.getData, 128) += XYChart.Data[String, Number](now, rArm.ligament)
	}

	update(model.value)
	model.onChange { (_, _, m) => update(m) }


	private val stop     = new AtomicBoolean(false)
	private val incoming = new ArrayBlockingQueue[FlexResult](10)

	private val ec: ExecutorService = Executors.newCachedThreadPool()
	ec.submit((() => {
		while (!stop.get()) {
			val r       = incoming.take()
			val current = model.value
			val update  = r.name match {
				case "rel" => (_: Arm[Double]).copy(elbow = r.degree)
				case "rcl" => (_: Arm[Double]).copy(ligament = r.degree)
				case "rex" => (_: Arm[Double]).copy(extensor = r.degree)
				case _     => (_: Arm[Double]).copy()
			}
			val now     = Instant.now()
			val next    = Model(
				series = (current.series + (current.series.lastOption match {
					case Some((_, (l, r))) => now -> (l -> update(r))
					case None              => now -> (Monoid[Arm[Double]].empty -> Monoid[Arm[Double]].empty)
				})).takeRight(5000)
			)
			Platform.runLater(model.update(next))
		}
	}): Runnable)
	launchReadThread(ec, stop, incoming)


	private def mkLeftDiagramArrows(from: Node, to: Node): (Node, () => Unit) = {
		def boundInScene(x: Node) = x.localToScene(x.getBoundsInLocal)
		val origin  = new MoveTo
		val hline   = new HLineTo {x <== origin.x - 30}
		val pointer = new LineTo
		def updatePath(): Unit = {
			origin.x = boundInScene(from).getMinX - 8
			origin.y = boundInScene(from).getCenterY
			pointer.x = boundInScene(to).getCenterX
			pointer.y = boundInScene(to).getCenterY
		}
		from.layoutBounds.onChange(updatePath())
		to.layoutBounds.onChange(updatePath())
		updatePath()
		new Path {
			elements = Seq(origin, hline, pointer)
			strokeWidth = 2
			stroke = Color.White
			fill = Color.Transparent
		} -> { () => updatePath() }
	}


}

