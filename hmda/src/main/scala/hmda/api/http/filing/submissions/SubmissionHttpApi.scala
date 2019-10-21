package hmda.api.http.filing.submissions

import akka.actor.ActorSystem
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.event.LoggingAdapter
import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.model.{ StatusCodes, Uri }
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink
import akka.util.{ ByteString, Timeout }
import ch.megard.akka.http.cors.scaladsl.CorsDirectives._
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import hmda.api.http.directives.HmdaTimeDirectives
import hmda.api.http.model.ErrorResponse
import hmda.api.http.model.filing.submissions.SubmissionResponse
import hmda.auth.OAuth2Authorization
import hmda.messages.filing.FilingCommands._
import hmda.messages.institution.InstitutionCommands.GetInstitution
import hmda.messages.submission.SubmissionCommands.CreateSubmission
import hmda.messages.submission.SubmissionEvents.SubmissionCreated
import hmda.messages.submission.SubmissionProcessingCommands.{ GetHmdaValidationErrorState, GetVerificationStatus }
import hmda.model.filing.Filing
import hmda.model.filing.submission._
import hmda.model.filing.ts.TransmittalSheet
import hmda.model.institution.Institution
import hmda.model.processing.state.HmdaValidationErrorState
import hmda.parser.filing.ts.TsCsvParser
import hmda.persistence.filing.FilingPersistence
import hmda.persistence.institution.InstitutionPersistence
import hmda.persistence.submission.HmdaProcessingUtils._
import hmda.persistence.submission._
import hmda.util.http.FilingResponseUtils._
import hmda.util.streams.FlowUtils.framing

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success }

trait SubmissionHttpApi extends HmdaTimeDirectives {

  implicit val system: ActorSystem
  implicit val materializer: ActorMaterializer
  val log: LoggingAdapter
  implicit val ec: ExecutionContext
  implicit val timeout: Timeout
  val sharding: ClusterSharding

  //institutions/<lei>/filings/<period>/submissions
  def submissionCreatePath(oAuth2Authorization: OAuth2Authorization): Route =
    path("institutions" / Segment / "filings" / Segment / "submissions") { (lei, period) =>
      oAuth2Authorization.authorizeTokenWithLei(lei) { _ =>
        respondWithHeader(RawHeader("Cache-Control", "no-cache")) {
          timedPost { uri =>
            val institutionPersistence = {
              if (period == "2018") {
                sharding.entityRefFor(InstitutionPersistence.typeKey, s"${InstitutionPersistence.name}-$lei")
              } else {
                sharding.entityRefFor(InstitutionPersistence.typeKey, s"${InstitutionPersistence.name}-$lei-$period")
              }
            }

            val fInstitution: Future[Option[Institution]] = institutionPersistence ? (
              ref => GetInstitution(ref)
            )

            val filingPersistence =
              sharding.entityRefFor(FilingPersistence.typeKey, s"${FilingPersistence.name}-$lei-$period")

            val filingF: Future[Option[Filing]] = filingPersistence ? (ref => GetFiling(ref))

            val latestSubmissionF: Future[Option[Submission]] = filingPersistence ? (ref => GetLatestSubmission(ref))

            val fCheck = for {
              i <- fInstitution
              f <- filingF
              l <- latestSubmissionF
            } yield (i, f, l)

            onComplete(fCheck) {
              case Success(check) =>
                check match {
                  case (None, _, _) =>
                    entityNotPresentResponse("institution", lei, uri)
                  case (_, None, _) =>
                    entityNotPresentResponse("filing", s"$lei-$period", uri)
                  case (_, _, maybeLatest) =>
                    maybeLatest match {
                      case None =>
                        val submissionId = SubmissionId(lei, period, 1)
                        createSubmission(uri, submissionId)
                      case Some(submission) =>
                        val submissionId =
                          SubmissionId(lei, period, submission.id.sequenceNumber + 1)
                        createSubmission(uri, submissionId)
                    }
                }

              case Failure(error) =>
                failedResponse(StatusCodes.InternalServerError, uri, error)
            }
          }
        }
      }
    }

  def submissionSummary(oAuth2Authorization: OAuth2Authorization): Route =
    path("institutions" / Segment / "filings" / Segment / "submissions" / IntNumber / "summary") { (lei, period, seqNr) =>
      oAuth2Authorization.authorizeTokenWithLei(lei) { _ =>
        timedGet { uri =>
          val submissionId = SubmissionId(lei, period, seqNr)

          val filingPersistence =
            sharding.entityRefFor(FilingPersistence.typeKey, s"${FilingPersistence.name}-$lei-$period")

          val fSummary: Future[Option[Submission]] = filingPersistence ? (ref => GetSubmissionSummary(submissionId, ref))

          val fTs: Future[Option[TransmittalSheet]] =
            readRawData(submissionId)
              .map(line => line.data)
              .map(ByteString(_))
              .take(1)
              .via(framing("\n"))
              .map(_.utf8String)
              .map(_.trim)
              .map(s => TsCsvParser(s))
              .map { s =>
                s.getOrElse(TransmittalSheet())
              }
              .runWith(Sink.seq)
              .map(xs => xs.headOption)

          val fCheck = for {
            t <- fTs
            s <- fSummary
          } yield SubmissionSummary(s, t)

          onComplete(fCheck) {
            case Success(check) =>
              check match {
                case SubmissionSummary(None, _) =>
                  val errorResponse = ErrorResponse(404, s"Submission ${submissionId.toString} not available", uri.path)
                  complete(ToResponseMarshallable(StatusCodes.NotFound -> errorResponse))
                case SubmissionSummary(_, None) =>
                  val errorResponse =
                    ErrorResponse(404, s"Transmittal Sheet not found", uri.path)
                  complete(StatusCodes.NotFound -> errorResponse)
                case _ =>
                  complete(ToResponseMarshallable(check))
              }
            case Failure(error) =>
              failedResponse(StatusCodes.InternalServerError, uri, error)
          }
        }
      }
    }

  //institutions/<lei>/filings/<period>/submissions/latest
  def submissionLatestPath(oAuth2Authorization: OAuth2Authorization): Route =
    path("institutions" / Segment / "filings" / Segment / "submissions" / "latest") { (lei, period) =>
      oAuth2Authorization.authorizeTokenWithLei(lei) { _ =>
        respondWithHeader(RawHeader("Cache-Control", "no-cache")) {
          timedGet { uri =>
            val filingPersistence =
              sharding.entityRefFor(FilingPersistence.typeKey, s"${FilingPersistence.name}-$lei-$period")

            val fLatest: Future[Option[Submission]] = filingPersistence ? (ref => GetLatestSubmission(ref))

            val fResponse = fLatest.flatMap {
              case Some(s) =>
                val entity =
                  sharding.entityRefFor(HmdaValidationError.typeKey, s"${HmdaValidationError.name}-${s.id}")
                val fStatus: Future[VerificationStatus]      = entity ? (reply => GetVerificationStatus(reply))
                val fEdits: Future[HmdaValidationErrorState] = entity ? (reply => GetHmdaValidationErrorState(s.id, reply))

                val fSubmissionAndVerified = fStatus.map(v => (s, v))

                val fQMExists = fEdits.map(r => QualityMacroExists(!r.quality.isEmpty, !r.`macro`.isEmpty))

                for {
                  submissionAndVerified <- fSubmissionAndVerified
                  (submision, verified) = submissionAndVerified
                  qmExists              <- fQMExists
                } yield Option(SubmissionResponse(submision, verified, qmExists))
              case None =>
                Future.successful(None)
            }

            onComplete(fResponse) {
              case Success(maybeLatest) =>
                maybeLatest match {
                  case Some(latest) =>
                    complete(latest)

                  case None =>
                    complete(StatusCodes.NotFound)
                }
              case Failure(error) =>
                failedResponse(StatusCodes.InternalServerError, uri, error)
            }
          }
        }
      }
    }

  def submissionRoutes(oAuth2Authorization: OAuth2Authorization): Route =
    handleRejections(corsRejectionHandler) {
      cors() {
        encodeResponse {
          submissionCreatePath(oAuth2Authorization) ~ submissionLatestPath(oAuth2Authorization) ~ submissionSummary(oAuth2Authorization)
        }
      }
    }

  private def createSubmission(uri: Uri, submissionId: SubmissionId): Route = {
    val submissionPersistence =
      sharding.entityRefFor(SubmissionPersistence.typeKey, s"${SubmissionPersistence.name}-${submissionId.toString}")

    val createdF: Future[SubmissionCreated] = submissionPersistence ? (ref => CreateSubmission(submissionId, ref))

    onComplete(createdF) {
      case Success(created) =>
        complete(StatusCodes.Created -> created.submission)
      case Failure(error) =>
        failedResponse(StatusCodes.InternalServerError, uri, error)
    }
  }

}
