package com.github.tomasmilata.akkastreams.visualisation

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

import akka.actor.{ActorSystem, Props, _}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.http.scaladsl.server.Directives._
import akka.pattern.ask
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.util.Timeout
import akka.{Done, NotUsed}
import com.github.tomasmilata.akkastreams.visualisation.control.{GetSpeed, SetSpeed, SinkControlActor, SourceControlActor, Speed}


object App extends App with RandomChars {

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

  wordFlow.runWith(
    randomCharSource,
    wordSink
  )

  Await.result(Http().bindAndHandle(route, "127.0.0.1", 8080), 3.seconds)
}