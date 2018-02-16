package com.github.tomasmilata.akkastreams.visualisation

import akka.actor.{Actor, ActorRef}
import com.github.tomasmilata.akkastreams.visualisation.Events._
import com.github.tomasmilata.akkastreams.visualisation.MonitoringActor.Subscribe

object MonitoringActor {

  case class Subscribe(outgoingMessagesActor: ActorRef)

}

class MonitoringActor extends Actor {
  var subscribers: Set[ActorRef] = Set.empty

  override def receive: Receive = {
    case Subscribe(outgoingMessagesActor) =>
      subscribers = subscribers + outgoingMessagesActor

    case e: SourceEventStarted => publish(e)
    case e: SourceEventFinished => publish(e)
    case e: FlowEventStarted => publish(e)
    case e: FlowEventFinished => publish(e)
    case e: SinkEventStarted => publish(e)
    case e: SinkEventFinished => publish(e)
  }
  
  private def publish(e: StreamEvent): Unit = subscribers.foreach(_ ! e)
}