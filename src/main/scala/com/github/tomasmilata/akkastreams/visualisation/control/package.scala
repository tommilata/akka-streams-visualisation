package com.github.tomasmilata.akkastreams.visualisation

import scala.concurrent.duration.FiniteDuration

package object control {

  case class Speed(processingTime: FiniteDuration)

  case object GetSpeed

  case class SetSpeed(speed: Speed)

  object SetSpeed {
    def apply(processingTime: FiniteDuration): SetSpeed = SetSpeed(Speed(processingTime))
  }

}
