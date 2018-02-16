package com.github.tomasmilata.akkastreams.visualisation

import java.util.UUID

object Events {

  sealed trait StreamEvent {
    def value: String
    def id: UUID
  }

  case class SourceEventStarted(override val value: String, override val id: UUID) extends StreamEvent
  case class SourceEventFinished(override val value: String, override val id: UUID) extends StreamEvent

  case class FlowEventStarted(override val value: String, override val id: UUID) extends StreamEvent
  case class FlowEventFinished(override val value: String, override val id: UUID) extends StreamEvent

  case class SinkEventStarted(override val value: String, override val id: UUID) extends StreamEvent
  case class SinkEventFinished(override val value: String, override val id: UUID) extends StreamEvent

}
