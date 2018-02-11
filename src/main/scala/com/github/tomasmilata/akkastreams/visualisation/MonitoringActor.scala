package com.github.tomasmilata.akkastreams.visualisation

import akka.actor.Actor

class MonitoringActor extends Actor {
  override def receive: Receive = {
    case any: Any => println(s"Received $any.")
  }
}