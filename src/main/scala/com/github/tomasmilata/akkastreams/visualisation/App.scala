package com.github.tomasmilata.akkastreams.visualisation

import java.util.UUID

import akka.actor.{ActorSystem, Props, _}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.http.scaladsl.server.Directives._
import akka.pattern.ask
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.stream.{ActorMaterializer, OverflowStrategy}
import akka.util.Timeout
import akka.{Done, NotUsed}
import com.github.tomasmilata.akkastreams.visualisation.Events._
import com.github.tomasmilata.akkastreams.visualisation.MonitoringActor.Subscribe
import com.github.tomasmilata.akkastreams.visualisation.control.{ControlActor, _}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}


case class Letter(value: Char, id: UUID = UUID.randomUUID)
case class Word(value: String, id: UUID = UUID.randomUUID)

object App extends App with RandomChars {

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()

  implicit val askTimeout = Timeout(30.seconds)

  val sourceControlActor = system.actorOf(Props(new ControlActor(Speed(100.millis))))
  val flowControlActor = system.actorOf(Props(new ControlActor(Speed(50.millis))))
  val sinkControlActor = system.actorOf(Props(new ControlActor(Speed(500.millis))))

  val monitoringActor = system.actorOf(Props[MonitoringActor])

  val randomCharSource = Source(
    Stream.continually {
      (sourceControlActor ? GetSpeed).mapTo[Speed] // get current speed
        .map { speed =>
          val letter = Letter(randomChar)
          monitoringActor ! SourceEventStarted(letter.value.toString, letter.id)
          Thread.sleep(speed.processingTime.toMillis) // simulate work by sleeping
          monitoringActor ! SourceEventFinished(letter.value.toString, letter.id)
          letter
        }
    }
  ).mapAsync(1)(identity)

  val wordFlow: Flow[Letter, Word, NotUsed] =
    Flow[Letter].grouped(10).map { letters =>
      (flowControlActor ? GetSpeed).mapTo[Speed] // get current speed
        .map { speed =>
          val word = Word(letters.map(_.value).mkString("'", "", "'"))
          monitoringActor ! FlowEventStarted(word.value, word.id)
          Thread.sleep(speed.processingTime.toMillis) // simulate work by sleeping
          monitoringActor ! FlowEventFinished(word.value, word.id)
          word
        }
    }.mapAsync(1)(identity)

  val wordSink: Sink[Word, Future[Done]] = Sink.foreach { word =>
    (sinkControlActor ? GetSpeed).mapTo[Speed] // get current speed
      .map { speed =>
        monitoringActor ! SinkEventStarted(word.value, word.id)
        Thread.sleep(speed.processingTime.toMillis) // simulate work by sleeping
        monitoringActor ! SinkEventFinished(word.value, word.id)
      }
  }

  // Stream of incoming and outgoing WS messages is represented as an Akka Stream,
  // but that's unrelated to the stream above.
  def websocketFlow() = {

    val incomingWebsocketMessages: Sink[Message, NotUsed] =
      Flow[Message].map {
        case TextMessage.Strict(text) => text
      }.to(Sink.foreach {
        case text if text.startsWith("set-source-processing-time:") =>
          val setSpeed = SetSpeed(text.split(':')(1).toInt.millis)
          println(s"Sending $setSpeed to randomCharSource")
          sourceControlActor ! setSpeed
        case text if text.startsWith("set-flow-processing-time:") =>
          val setSpeed = SetSpeed(text.split(':')(1).toInt.millis)
          println(s"Sending $setSpeed to the flow")
          flowControlActor ! setSpeed
        case text if text.startsWith("set-sink-processing-time:") =>
          val setSpeed = SetSpeed(text.split(':')(1).toInt.millis)
          println(s"Sending $setSpeed to wordSink")
          sinkControlActor ! setSpeed
      })

    val outgoingWebsocketMessages: Source[Message, _] =
      Source.actorRef[StreamEvent](1, OverflowStrategy.dropHead)
        .mapMaterializedValue { outgoingMessagesActor =>
          monitoringActor ! Subscribe(outgoingMessagesActor)
          NotUsed
        }.map { streamEvent =>
        TextMessage.Strict(
          // I'm just lazy to use a proper JSON library
          s"""
             |{
             |  "type": "${streamEvent.getClass.getSimpleName}",
             |  "id": "${streamEvent.id}",
             |  "value": "${streamEvent.value}"
             |}
           """.stripMargin)
      }

    Flow.fromSinkAndSource(
      incomingWebsocketMessages,
      outgoingWebsocketMessages
    )
  }

  val route = // set up Akka HTTP route
    path("stream-control") {
      get {
        handleWebSocketMessages(
          websocketFlow()
        )
      }
    }

  // materialize (i.e. actually run) the stream
  wordFlow.runWith(
    randomCharSource,
    wordSink
  )

  Await.result(Http().bindAndHandle(route, "127.0.0.1", 8080), 3.seconds)
}