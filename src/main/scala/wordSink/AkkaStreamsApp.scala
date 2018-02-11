package wordSink

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.io.StdIn
import scala.util.Random
import scala.concurrent.ExecutionContext.Implicits.global

import akka.actor._
import akka.stream._
import akka.stream.scaladsl._
import akka.http.scaladsl._
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.http.scaladsl.server.Directives._
import akka.{Done, NotUsed}
import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.pattern.ask
import akka.stream.actor.ActorPublisher
import akka.stream.{ActorMaterializer, ThrottleMode}
import akka.util.Timeout

case class Speed(processingTime: FiniteDuration)

case class SetSpeed(speed: Speed)
object SetSpeed {
  def apply(processingTime: FiniteDuration): SetSpeed = SetSpeed(Speed(processingTime))
}

case object GetSpeed

class SourceControlActor extends Actor {
  var speed: Speed = Speed(processingTime = 100.millis)

  override def receive: Receive = {
    case SetSpeed(s) =>
      speed = s
      println(s"Set sleep time to ${speed.processingTime}.")
    case GetSpeed =>
      sender ! speed
  }

}

class SinkControlActor extends Actor {
  var speed: Speed = Speed(processingTime = 10.millis)

  override def receive: Receive = {
    case SetSpeed(s) =>
      speed = s
      println(s"Set sleep time to ${speed.processingTime}.")
    case GetSpeed => sender ! speed
  }
}

object MonitoringActor {
}

class MonitoringActor extends Actor {
  override def receive: Receive = {
    case any: Any => println(s"Received $any.")
  }
}

object AkkaStreamsApp extends App {

  private def randomChar = {
    val chars = "abcdefghijklmnopqrstuvwxyz"
    chars.charAt(Random.nextInt(chars.length))
  }

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()

  implicit val askTimeout = Timeout(30.seconds)

  val sourceControlActor = system.actorOf(Props[SourceControlActor])
  val wordSinkControlActor = system.actorOf(Props[SinkControlActor])
  val monitoringActor = system.actorOf(Props[MonitoringActor])

  val randomCharSource = Source(
    Stream.continually {
      (sourceControlActor ? GetSpeed).mapTo[Speed]
        .map { speed =>
          Thread.sleep(speed.processingTime.toMillis)
          val c = randomChar
          println(s"Generated char $c in ${speed.processingTime}.")
          c
        }
    }
  ).mapAsync(1)(identity)

  val wordFlow: Flow[Char, String, NotUsed] = Flow[Char].grouped(10).map(_.mkString)

  val wordSink: Sink[String, Future[Done]] = Sink.foreach { word =>
    (wordSinkControlActor ? GetSpeed).mapTo[Speed]
      .map { speed =>
        Thread.sleep(speed.processingTime.toMillis)
        println(s"Processed word $word in ${speed.processingTime}.")
      }
  }

  val runningFlow = wordFlow.runWith(
    randomCharSource,
    wordSink
  )

  runningFlow


  val incomingWebsocketMessages: Sink[Message, NotUsed] =
    Flow[Message].map {
      case TextMessage.Strict(text) => text
    }.to(Sink.foreach {
      case text if text.startsWith("set-source-processing-time:") =>
        val setSpeed = SetSpeed(text.split(':')(1).toInt.millis)
        println(s"Sending $setSpeed to randomCharSource")
        sourceControlActor ! setSpeed
      case text if text.startsWith("set-sink-processing-time:") =>
        val setSpeed = SetSpeed(text.split(':')(1).toInt.millis)
        println(s"Sending $setSpeed to wordSink")
        wordSinkControlActor ! setSpeed
    })

  val outgoingWebsocketMessages: Source[Message, _] =
    Source.tick(1.second, 10.seconds, TextMessage("hello"))

  val route =
    path("stream-control") {
      get {
        handleWebSocketMessages(
          Flow.fromSinkAndSource(
            incomingWebsocketMessages,
            outgoingWebsocketMessages
          )
        )
      }
    }

  Await.result(Http().bindAndHandle(route, "127.0.0.1", 8080), 3.seconds)
}