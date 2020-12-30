package hmda.publisher.scheduler

import java.time.Instant

import akka.actor.typed.ActorRef
import akka.stream.Materializer
import akka.stream.alpakka.s3.ApiVersion.ListBucketVersion2
import akka.stream.alpakka.s3._
import akka.stream.alpakka.s3.scaladsl.S3
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.typesafe.akka.extension.quartz.QuartzSchedulerExtension
import hmda.actor.HmdaActor
import hmda.publisher.helper.{PrivateAWSConfigLoader, PublicAWSConfigLoader, S3Archiver, S3Utils, SnapshotCheck, TSHeader}
import hmda.publisher.query.component.{PublisherComponent2018, PublisherComponent2019, PublisherComponent2020}
import hmda.publisher.scheduler.schedules.Schedules.{TsPublicScheduler2018, TsPublicScheduler2019}
import hmda.query.DbConfiguration.dbConfig
import hmda.query.ts._
import hmda.util.BankFilterUtils._
import akka.stream.alpakka.file.scaladsl.Archive
import akka.stream.alpakka.file.ArchiveMetadata
import hmda.publisher.scheduler.schedules.Schedule
import hmda.publisher.util.PublishingReporter
import hmda.publisher.util.PublishingReporter.Command.FilePublishingCompleted
import hmda.publisher.validation.PublishingGuard
import hmda.publisher.validation.PublishingGuard.{Period, Scope}

import scala.concurrent.Future
import scala.util.{Failure, Success}

class TsPublicScheduler(publishingReporter: ActorRef[PublishingReporter.Command])
  extends HmdaActor
    with PublisherComponent2018
    with PublisherComponent2019
    with PublisherComponent2020
    with TSHeader
    with PublicAWSConfigLoader
    with PrivateAWSConfigLoader {

  implicit val ec                      = context.system.dispatcher
  implicit val materializer            = Materializer(context)
  def tsRepository2018                 = new TransmittalSheetRepository2018(dbConfig)
  def tsRepository2019                 = new TransmittalSheetRepository2019(dbConfig)
  val publishingGuard: PublishingGuard = PublishingGuard.create(this)(context.system)

  val s3Settings =
    S3Settings(context.system)
      .withBufferType(MemoryBufferType)
      .withCredentialsProvider(awsCredentialsProviderPublic)
      .withS3RegionProvider(awsRegionProviderPublic)
      .withListBucketApiVersion(ListBucketVersion2)

  override def preStart(): Unit = {
    QuartzSchedulerExtension(context.system)
      .schedule("TsPublicScheduler2018", self, TsPublicScheduler2018)

    QuartzSchedulerExtension(context.system)
      .schedule("TsPublicScheduler2019", self, TsPublicScheduler2019)
  }

  override def postStop(): Unit = {
    QuartzSchedulerExtension(context.system).cancelJob("TsPublicScheduler2018")
    QuartzSchedulerExtension(context.system).cancelJob("TsPublicScheduler2019")
  }
  override def receive: Receive = {

    case schedule @ TsPublicScheduler2018 =>
      publishingGuard.runIfDataIsValid(Period.y2018, Scope.Public) {
        val fileName         = "2018_ts.txt"
        val zipDirectoryName = "2018_ts.zip"
        val s3Path           = s"$environmentPublic/dynamic-data/2018/"
        val fullFilePath     = SnapshotCheck.pathSelector(s3Path, zipDirectoryName)
        if (SnapshotCheck.snapshotActive) {
          tsPublicStream("2018", SnapshotCheck.snapshotBucket, fullFilePath, fileName, schedule)
        } else {
          tsPublicStream("2018", bucketPublic, fullFilePath, fileName, schedule)
        }
      }

    case schedule @ TsPublicScheduler2019 =>
      publishingGuard.runIfDataIsValid(Period.y2019, Scope.Public) {
        val fileName         = "2019_ts.txt"
        val zipDirectoryName = "2019_ts.zip"
        val s3Path           = s"$environmentPublic/dynamic-data/2019/"
        val fullFilePath     = SnapshotCheck.pathSelector(s3Path, zipDirectoryName)
        if (SnapshotCheck.snapshotActive) {
          tsPublicStream("2019", SnapshotCheck.snapshotBucket, fullFilePath, fileName, schedule)
        } else {
          tsPublicStream("2019", bucketPublic, fullFilePath, fileName, schedule)
        }
      }
  }
  private def tsPublicStream(year: String, bucket: String, key: String, fileName: String, schedule: Schedule): Unit = {

    val s3SinkPSV =
      S3.multipartUpload(bucket, key).withAttributes(S3Attributes.settings(s3Settings))

    val allResults: Future[Seq[TransmittalSheetEntity]] =
      year match {
        case "2018" => tsRepository2018.getAllSheets(getFilterList())
        case "2019" => tsRepository2019.getAllSheets(getFilterList())
        case _      => throw new IllegalArgumentException(s"Unknown year selector value:  [$year]")

      }
    //SYNC PSV
    val fileStream: Source[ByteString, Any] = Source
      .future(allResults)
      .mapConcat(seek => seek.toList)
      .zipWithIndex
      .map(transmittalSheet =>
        if (transmittalSheet._2 == 0) TSHeader.concat(transmittalSheet._1.toPublicPSV) + "\n"
        else transmittalSheet._1.toPublicPSV + "\n"
      )
      .map(s => ByteString(s))

    val zipStream = Source(
      List((ArchiveMetadata(fileName), fileStream))
    )

    val resultsPSV = for {
      _            <- S3Archiver.archiveFileIfExists(bucket, key, bucketPrivate, s3Settings)
      bytesStream = zipStream.via(Archive.zip())
      uploadResult <- S3Utils.uploadWithRetry(bytesStream, s3SinkPSV)
    } yield uploadResult

    resultsPSV onComplete {
      case Success(result) =>
        publishingReporter ! FilePublishingCompleted(schedule, key, None, Instant.now, FilePublishingCompleted.Status.Success)
        log.info("Pushed to S3: " + s"$bucket/$key" + ".")
      case Failure(t) =>
        publishingReporter ! FilePublishingCompleted(schedule, key, None, Instant.now, FilePublishingCompleted.Status.Error(t.getMessage))
        log.info("An error has occurred with: " + key + "; Getting Public TS Data in Future: " + t.getMessage)
    }
  }
}