package com.marta

import java.nio.ByteBuffer
import java.nio.channels.ReadableByteChannel
import java.nio.charset.StandardCharsets
import java.util
import java.util.Scanner
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.{ArrayBlockingQueue, BlockingQueue, ExecutorService}

import org.sputnikdev.bluetooth.manager.{GattService, GenericBluetoothDeviceListener}

object BLE {

	val DeviceMAC  = "E2:D3:BB:D6:66:A6"
	val GattUart   = "6e400001-b5a3-f393-e0a9-e50e24dcca9e"
	val GattUartTx = "6e400002-b5a3-f393-e0a9-e50e24dcca9e"
	val GattUartRx = "6e400003-b5a3-f393-e0a9-e50e24dcca9e"

	def launchReadThread(ec: ExecutorService, stop: AtomicBoolean, xs: BlockingQueue[FlexResult]) = {

		import org.sputnikdev.bluetooth.manager.impl.BluetoothManagerBuilder

		import scala.jdk.CollectionConverters._

		var connected = false
		while (!stop.get() && !connected) {
			val man     = new BluetoothManagerBuilder()
				.withTinyBTransport(true)
				.build()
			val devices = man.getDiscoveredDevices.asScala.toList
			println(s"Devices: $devices")
			devices.find(_.getURL.getDeviceAddress == DeviceMAC).foreach { d =>
				println(d)
				connected = true
				val governor = man.getDeviceGovernor(d.getURL, true)

				governor.addGenericBluetoothDeviceListener(new GenericBluetoothDeviceListener {
					override def online(): Unit = println(s"$d: online")
					override def offline(): Unit = println(s"$d: offline")
					override def blocked(blocked: Boolean): Unit = println(s"$d: blocked=$blocked")
					override def rssiChanged(rssi: Short): Unit = println(s"$d: rssi=$rssi")
				})
				governor.addBluetoothSmartDeviceListener((services: util.List[GattService]) => {
					println(s"$d: Services: \n" + services.asScala.map(_.getURL).mkString("\n"))

					services.asScala.find(_.getURL.getServiceUUID == GattUart) match {
						case None       => println("GATT UART not supported")
						case Some(gatt) =>
							println(s"$d: Joining Gatt `${gatt.getURL}`")
							val uart = man.getCharacteristicGovernor(gatt.getURL.copyWithCharacteristic(GattUartRx), true)

							val buffer  = new ArrayBlockingQueue[Array[Byte]](10)
							val scanner = new Scanner(
								new ReadableByteChannel {
									override def read(dst: ByteBuffer) = {
										val xs = buffer.take()
										dst.put(xs)
										xs.length
									}
									override def isOpen = true
									override def close(): Unit = ()
								}, StandardCharsets.US_ASCII).useDelimiter(";")

							uart.addGovernorListener { ready => println(s"$d: Gatt ready=$ready") }
							uart.addValueListener { xs => ec.submit(() => buffer.offer(xs)) }
							ec.submit((() => {
								while (scanner.hasNext && !stop.get()) {
									FlexResult(scanner.next()) match {
										case Left(e)  => println(e)
										case Right(x) => xs.offer(x)
									}
								}
								man.dispose()
							}): Runnable)

					}
				})
			}
			Thread.sleep(500)
		}
	}

}
