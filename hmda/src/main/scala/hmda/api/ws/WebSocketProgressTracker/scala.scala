package hmda.api.ws

import akka.Done
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.{ ActorRef, ActorSystem, Behavior }
import akka.cluster.sharding.typed.scaladsl.{ ClusterSharding, EntityRef }
import akka.http.scaladsl.model.ws.{ BinaryMessage, Message, TextMessage }
import akka.stream.scaladsl.{ Flow, Sink, Source, SourceQueueWithComplete }
import akka.stream.{ Materializer, OverflowStrategy }
import akka.util.Timeout
import hmda.api.ws.WebSocketProgressTracker.Protocol._
import hmda.messages.submission.SubmissionProcessingCommands
import hmda.messages.submission.SubmissionProcessingCommands.TrackProgress
import hmda.messages.submission.ValidationProgressTrackerCommands.{ Poll, Subscribe, ValidationProgressTrackerCommand }
import hmda.model.filing.submission.SubmissionId
import hmda.model.processing.state.ValidationProgressTrackerState
import hmda.persistence.submission.HmdaValidationError

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success }

object WebSocketProgressTracker {
  sealed trait Protocol
  object Protocol {
    case class RegisterWsHandle(ref: SourceQueueWithComplete[Message])                                               extends Protocol
    case class IncomingMessage(msg: TextMessage.Strict)                                                              extends Protocol
    private[WebSocketProgressTracker] case class IncomingTrackerRef(ref: ActorRef[ValidationProgressTrackerCommand]) extends Protocol
    private[WebSocketProgressTracker] case class IncomingState(state: ValidationProgressTrackerState)                extends Protocol
    case object Dummy                                                                                                extends Protocol
    case class Fail(t: Throwable)                                                                                    extends Protocol
    case object Complete                                                                                             extends Protocol
  }

  def websocketFlow(system: ActorSystem[_], sharding: ClusterSharding, submissionId: SubmissionId)(
    implicit mat: Materializer,
    ec: ExecutionContext
  ): Flow[Message, Message, Future[Done]] = {
    val wsActor =
      system.toClassic.spawnAnonymous[Protocol](WebSocketProgressTracker(sharding, submissionId))

    val fromWebSocket =
      Sink.foreach[Message] {
        case text: TextMessage.Strict   => wsActor ! Protocol.IncomingMessage(text)
        case text: TextMessage.Streamed => text.textStream.runWith(Sink.ignore)
        case binary: BinaryMessage      => binary.dataStream.runWith(Sink.ignore)
      }

    val toWebSocket =
      Source
        .queue[Message](4, OverflowStrategy.dropHead)
        .mapMaterializedValue(ref => wsActor ! Protocol.RegisterWsHandle(ref))

    Flow
      .fromSinkAndSourceCoupled(fromWebSocket, toWebSocket)
      .watchTermination() {
        case (_, done) =>
          done.onComplete {
            case Failure(ex) => wsActor ! Protocol.Fail(ex)
            case Success(_)  => wsActor ! Protocol.Complete
          }
          done
      }
  }

  private def apply(
                     sharding: ClusterSharding,
                     submissionId: SubmissionId
                   ): Behavior[Protocol] =
    register(HmdaValidationError.selectHmdaValidationError(sharding, submissionId))

  private def register(entity: EntityRef[SubmissionProcessingCommands.SubmissionProcessingCommand]): Behavior[Protocol] =
    Behaviors.setup { ctx =>
      ctx.log.info(s"Starting websocket actor")
      implicit val timeout: Timeout     = Timeout(10.seconds)
      implicit val ec: ExecutionContext = ctx.executionContext
      val trackerF                      = entity ? TrackProgress
      trackerF.onComplete {
        case Failure(exception) =>
          ctx.log.error("Failed to obtain a reference to the tracker", exception)
          ctx.stop(ctx.self)

        case Success(tracker) =>
          ctx.self ! IncomingTrackerRef(tracker)
      }

      def inner(
                 tracker: Option[ActorRef[ValidationProgressTrackerCommand]],
                 websocket: Option[SourceQueueWithComplete[Message]]
               ): Behavior[Protocol] =
        Behaviors.receiveMessage {
          case IncomingTrackerRef(trackerRef) =>
            websocket match {
              case Some(websocketRef) => echo(trackerRef, websocketRef)
              case None               => inner(Some(trackerRef), None)
            }

          case RegisterWsHandle(toWebsocket) =>
            tracker match {
              case Some(trackerRef) => echo(trackerRef, toWebsocket)
              case None             => inner(None, Some(toWebsocket))
            }

          case Complete =>
            ctx.log.info("Websocket has finished")
            Behaviors.stopped

          case Fail(t) =>
            ctx.log.error("Received failure", t)
            Behaviors.stopped

          case IncomingMessage(fromWebSocket) =>
            ctx.log.debug(s"Websocket actor received: ${fromWebSocket.text}")
            Behaviors.same

          case _ =>
            Behaviors.same
        }
      inner(None, None)
    }

  private def echo(
                    tracker: ActorRef[ValidationProgressTrackerCommand],
                    websocket: SourceQueueWithComplete[Message]
                  ): Behavior[Protocol] =
    Behaviors.setup { ctx =>
      ctx.log.info(s"Websocket actor initialized")

      val trackerAdaptedRef: ActorRef[ValidationProgressTrackerState] = ctx.messageAdapter(s => IncomingState(s))
      tracker ! Subscribe(trackerAdaptedRef)

      Behaviors.receiveMessage {
        case IncomingState(state) =>
          ctx.log.debug(s"state received from tracker: $state")
          websocket offer TextMessage.Strict(state.toString)
          Behaviors.same

        case IncomingMessage(fromWebSocket) =>
          ctx.log.info(s"Websocket actor received: ${fromWebSocket.text}, manually polling")
          tracker ! Poll(trackerAdaptedRef)
          Behaviors.same

        case Complete =>
          ctx.log.info("Websocket has finished")
          Behaviors.stopped

        case Fail(t) =>
          ctx.log.error("Received failure", t)
          Behaviors.stopped

        case _ =>
          Behaviors.same
      }
    }
}