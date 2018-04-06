package com.github.tomasmilata.akkastreams.visualisation.control

import akka.actor.Actor

class ControlActor(initialSpeed: Speed) extends Actor {
  var speed: Speed = initialSpeed

  override def receive: Receive = {
    case SetSpeed(s) =>
      speed = s
      println(s"Set sleep time to ${speed.processingTime}.")
    case GetSpeed =>
      sender ! speed
  }
}