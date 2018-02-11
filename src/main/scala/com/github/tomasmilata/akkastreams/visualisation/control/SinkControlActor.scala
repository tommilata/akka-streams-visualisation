package com.github.tomasmilata.akkastreams.visualisation.control

import scala.concurrent.duration._

import akka.actor.Actor

class SinkControlActor extends Actor {
  var speed: Speed = Speed(processingTime = 10.millis)

  override def receive: Receive = {
    case SetSpeed(s) =>
      speed = s
      println(s"Set sleep time to ${speed.processingTime}.")
    case GetSpeed => sender ! speed
  }
}