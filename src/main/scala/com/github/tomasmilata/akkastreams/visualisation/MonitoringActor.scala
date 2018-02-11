package com.github.tomasmilata.akkastreams.visualisation

import akka.actor.{Actor, ActorRef}
import com.github.tomasmilata.akkastreams.visualisation.Events.{CharGenerated, WordGenerated}
import com.github.tomasmilata.akkastreams.visualisation.MonitoringActor.{SubscribeChars, SubscribeWords}

object MonitoringActor {

  case class SubscribeChars(outgoingMessagesActor: ActorRef)
  case class SubscribeWords(outgoingMessagesActor: ActorRef)

}

class MonitoringActor extends Actor {
  var wordSubscribers: Set[ActorRef] = Set.empty
  var charSubscribers: Set[ActorRef] = Set.empty

  override def receive: Receive = {
    case SubscribeWords(outgoingMessagesActor) =>
      wordSubscribers = wordSubscribers + outgoingMessagesActor
    case SubscribeChars(outgoingMessagesActor) =>
      charSubscribers = charSubscribers + outgoingMessagesActor
    case wordGenerated: WordGenerated =>
      wordSubscribers.foreach(_ ! wordGenerated)
    case charGenerated: CharGenerated =>
      charSubscribers.foreach(_ ! charGenerated)
  }
}