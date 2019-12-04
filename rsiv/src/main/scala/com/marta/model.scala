package com.marta

import java.time.Instant

import cats.Traverse
import cats.kernel.Monoid

import scala.collection.immutable.{SortedMap, TreeMap}


case class Model(series: SortedMap[Instant, (Arm[Double], Arm[Double])] = TreeMap())
case class Arm[A](elbow: A, extensor: A, ligament: A) {
	def -(that: Arm[A])(implicit ev: Numeric[A]): Arm[A] = {
		import ev._
		Arm(
			elbow = elbow - that.elbow,
			extensor = extensor - that.extensor,
			ligament = ligament - that.ligament)
	}
}
object Arm {

	val InjuryThresholdDegree: Double = 30d

	implicit val traverse: Traverse[Arm] = cats.derived.semi.traverse
	implicit def monoid[A: Monoid]: Monoid[Arm[A]] = cats.derived.semi.monoid
}

case class FlexResult(name: String, degree: Double, resistance: Double)
object FlexResult {
	def apply(line: String): Either[String, FlexResult] =
		line.trim.split(" ").toList match {
			case name :: degree :: Nil =>
				(for {
					d <- degree.trim.toDoubleOption
					//						r <- resistance.trim.toDoubleOption
				} yield FlexResult(name.trim, d, 0)) match {
					case None    => Left(s"No decode $line")
					case Some(x) => Right(x)
				}
			case bad                   => Left(s"No parse $line => $bad")
		}
}
