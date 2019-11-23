package com.marta

import java.nio.charset.StandardCharsets
import java.util.Scanner
import java.util.concurrent.{ArrayBlockingQueue, BlockingQueue}

import com.fazecast.jSerialComm.SerialPort

object UART {


	def launchReadThread(xs: BlockingQueue[FlexResult], port: SerialPort) = {
		new Thread(() => {
			import com.fazecast.jSerialComm.SerialPort
			port.openPort()
			port.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 0, 0)
			val in      = port.getInputStream
			val scanner = new Scanner(in, StandardCharsets.US_ASCII)
			while (scanner.hasNextLine) {


				FlexResult(scanner.nextLine()) match {
					case Left(e)  => println(e)
					case Right(x) => xs.offer(x)
				}

			}
			in.close()
			println("Read ended")
		}).start()
	}

	def findFlexDevices() = {
		val ports = SerialPort.getCommPorts.toList
		println(s"ports = ${ports}")
		ports.find(_.getPortDescription.contains("Feather 32u4"))
	}

	def main(args: Array[String]): Unit = {
		findFlexDevices() match {
			case None       => println("Feather 32u4 not detected")
			case Some(port) =>
				val xs = new ArrayBlockingQueue[FlexResult](100)
				launchReadThread(xs, port)
				new Thread(() => {
					while (true) println(xs.take())
				}).start()


		}
	}


}
