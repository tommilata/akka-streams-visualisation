package com.github.tomasmilata.akkastreams.visualisation

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

import akka.actor.{ActorSystem, Props, _}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.http.scaladsl.server.Directives._
import akka.pattern.ask
import akka.stream.scaladsl.{Flow, Merge, Sink, Source}
import akka.stream.{ActorMaterializer, OverflowStrategy}
import akka.util.Timeout
import akka.{Done, NotUsed}
import com.github.tomasmilata.akkastreams.visualisation.Events.{CharGenerated, WordGenerated}
import com.github.tomasmilata.akkastreams.visualisation.MonitoringActor.{SubscribeChars, SubscribeWords}
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
          monitoringActor ! CharGenerated(c)
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
        monitoringActor ! WordGenerated(word)
        println(s"Processed word $word in ${speed.processingTime}.")
      }
  }

  def websocketFlow() = {

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

    val outgoingChars: Source[Message, _] =
      Source.actorRef[CharGenerated](1, OverflowStrategy.fail)
        .mapMaterializedValue { outgoingMessagesActor =>
          monitoringActor ! SubscribeChars(outgoingMessagesActor)
          NotUsed
        }.map { charGenerated =>
        TextMessage.Strict(charGenerated.c.toString)
      }

    val outgoingWords: Source[Message, _] =
      Source.actorRef[WordGenerated](1, OverflowStrategy.fail)
        .mapMaterializedValue { outgoingMessagesActor =>
          monitoringActor ! SubscribeWords(outgoingMessagesActor)
          NotUsed
        }.map { wordGenerated =>
        TextMessage.Strict(wordGenerated.word)
      }


    Flow.fromSinkAndSource(
      incomingWebsocketMessages,
      Source.combine(outgoingWords, outgoingChars)(Merge(_))
    )
  }

  val route =
    path("stream-control") {
      get {
        handleWebSocketMessages(
          websocketFlow()
        )
      }
    }

  wordFlow.runWith(
    randomCharSource,
    wordSink
  )

  Await.result(Http().bindAndHandle(route, "127.0.0.1", 8080), 3.seconds)
}