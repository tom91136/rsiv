#include <Arduino.h>
#include <SPI.h>
#include "Adafruit_BLE.h"
#include "Adafruit_BluefruitLE_SPI.h"
#include "Adafruit_BluefruitLE_UART.h"
#include "BluefruitConfig.h"

#if SOFTWARE_SERIAL_AVAILABLE
#include <SoftwareSerial.h>
# endif

#define FACTORYRESET_ENABLE         1
#define MINIMUM_FIRMWARE_VERSION    "0.6.6"


Adafruit_BluefruitLE_SPI ble(BLUEFRUIT_SPI_CS, BLUEFRUIT_SPI_IRQ, BLUEFRUIT_SPI_RST);

typedef struct FlexSensorData {
  const float normal;
  const float range;
  float resistance;
  float flexDegree;
} FlexSensorData;

const float VCC = 3.3;
const float R_DIV = 46300.0;

double mapf(double val,
            double in_min, double in_max,
            double out_min, double out_max) {
  return (val - in_min) * (out_max - out_min) / (in_max - in_min) + out_min;
}

void readFlex(FlexSensorData *data, const int adcPin) {
  int a = analogRead(adcPin);
  float flexV = a * VCC / 1023.0;
  float flexR = R_DIV * (VCC / flexV - 1.0);
  data->resistance = flexR - data->normal ;
  data->flexDegree =
    mapf(data->resistance,
         data->normal - data->range,
         data->normal + data->range,
         -90.0, 90.0);
}

String printFlex(String name, FlexSensorData *data) {
  return name +  " " + String(data->flexDegree);
}

void error(const __FlashStringHelper * err) {
  Serial.println(err);
  while (1);
}


void setup() {

  Serial.begin(9600);
  Serial.print(F("Init BLE:"));

  pinMode(A1, INPUT);
  pinMode(A2, INPUT);
  pinMode(A3, INPUT);

  if (!ble.begin(VERBOSE_MODE)) {
    error(F("Couldn't find Bluefruit, make sure it's in CoMmanD mode & check wiring?"));
  }
  Serial.println(F("OK!"));

  if (FACTORYRESET_ENABLE) {
    Serial.println(F("Performing a factory reset: "));
    if (!ble.factoryReset())  error(F("Couldn't factory reset"));
  }

  ble.echo(false);

  Serial.println("BLE info:");
  ble.info();
  ble.verbose(false);
  Serial.println("Waiting for connection...");
  while (!ble.isConnected()) {
    delay(200);
  }

  if (ble.isVersionAtLeast(MINIMUM_FIRMWARE_VERSION))
    ble.sendCommandCheckOK("AT+HWModeLED=MODE");

}


void loop() {

  static FlexSensorData a = {.normal = 70800.0, .range = 220000.0, .resistance = 0.0, .flexDegree = 0.0};
  static FlexSensorData b = {.normal =  123920.0, .range = 220000.0, .resistance = 0.0, .flexDegree = 0.0};
  static FlexSensorData c = {.normal =  123920.0, .range = 220000.0, .resistance = 0.0, .flexDegree = 0.0};

  if (millis() % 32 != 0) return;

  readFlex(&a, A1);
  readFlex(&b, A2);
  readFlex(&c, A3);

  ble.print("AT+BLEUARTTX=");
  ble.print(printFlex("rel", &a));
  ble.print(";");
  ble.print(printFlex("rcl", &b));
  ble.print(";");
  ble.print(printFlex("rex", &c));
  ble.println(";"); // XXX newline doesn't work on the client end for some reason

  ble.waitForOK();


}
