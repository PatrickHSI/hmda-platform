package hmda.publisher.scheduler

import akka.NotUsed
import akka.stream.Materializer
import akka.stream.alpakka.file.ArchiveMetadata
import akka.stream.alpakka.file.scaladsl.Archive
import akka.stream.alpakka.s3.ApiVersion.ListBucketVersion2
import akka.stream.alpakka.s3._
import akka.stream.alpakka.s3.scaladsl.S3
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.typesafe.akka.extension.quartz.QuartzSchedulerExtension
import hmda.actor.HmdaActor
import hmda.publisher.helper._
import hmda.publisher.query.component.{PublisherComponent2018, PublisherComponent2019, PublisherComponent2020}
import hmda.publisher.query.lar.ModifiedLarEntityImpl
import hmda.publisher.scheduler.schedules.Schedules.{LarPublicScheduler2018, LarPublicScheduler2019}
import hmda.publisher.validation.PublishingGuard
import hmda.publisher.validation.PublishingGuard.{Period, Scope}
import hmda.query.DbConfiguration.dbConfig
import hmda.util.BankFilterUtils._
import slick.basic.DatabasePublisher

import scala.util.{Failure, Success}

class LarPublicScheduler
  extends HmdaActor
    with PublisherComponent2018
    with PublisherComponent2019
    with PublisherComponent2020
    with ModifiedLarHeader
    with PGTableNameLoader
    with PublicAWSConfigLoader
    with PrivateAWSConfigLoader {

  implicit val ec           = context.system.dispatcher
  implicit val materializer = Materializer(context)

  def mlarRepository2018               = new ModifiedLarRepository2018(dbConfig)
  def mlarRepository2019               = new ModifiedLarRepository2019(dbConfig)
  val publishingGuard: PublishingGuard = PublishingGuard.create(this)(context.system)

  val s3Settings = S3Settings(context.system)
    .withBufferType(MemoryBufferType)
    .withCredentialsProvider(awsCredentialsProviderPublic)
    .withS3RegionProvider(awsRegionProviderPublic)
    .withListBucketApiVersion(ListBucketVersion2)

  override def preStart(): Unit = {
    QuartzSchedulerExtension(context.system)
      .schedule("LarPublicScheduler2018", self, LarPublicScheduler2018)
    QuartzSchedulerExtension(context.system)
      .schedule("LarPublicScheduler2019", self, LarPublicScheduler2019)
  }
  override def postStop(): Unit = {
    QuartzSchedulerExtension(context.system).cancelJob("LarPublicScheduler2018")
    QuartzSchedulerExtension(context.system).cancelJob("LarPublicScheduler2019")
  }
  override def receive: Receive = {
    case LarPublicScheduler2018 =>
      publishingGuard.runIfDataIsValid(Period.y2018, Scope.Public) {
        val fileName         = "2018_lar.txt"
        val zipDirectoryName = "2018_lar.zip"
        val s3Path           = s"$environmentPublic/dynamic-data/2018/"
        val fullFilePath     = SnapshotCheck.pathSelector(s3Path, zipDirectoryName)
        if (SnapshotCheck.snapshotActive) {
          larPublicStream("2018", SnapshotCheck.snapshotBucket, fullFilePath, fileName)
        } else {
          larPublicStream("2018", bucketPublic, fullFilePath, fileName)
        }
      }

    case LarPublicScheduler2019 =>
      publishingGuard.runIfDataIsValid(Period.y2019, Scope.Public) {
        val fileName         = "2019_lar.txt"
        val zipDirectoryName = "2019_lar.zip"
        val s3Path           = s"$environmentPublic/dynamic-data/2019/"
        val fullFilePath     = SnapshotCheck.pathSelector(s3Path, zipDirectoryName)
        if (SnapshotCheck.snapshotActive) {
          larPublicStream("2019", SnapshotCheck.snapshotBucket, fullFilePath, fileName)
        } else {
          larPublicStream("2019", bucketPublic, fullFilePath, fileName)
        }
      }

  }

  private def larPublicStream(year: String, bucket: String, key: String, fileName: String): Unit = {

    val allResultsPublisher: DatabasePublisher[ModifiedLarEntityImpl] =
      year match {
        case "2018" => mlarRepository2018.getAllLARs(getFilterList())
        case "2019" => mlarRepository2019.getAllLARs(getFilterList())
        case _      => throw new IllegalArgumentException(s"Unknown year selector value:  [$year]")
      }

    val allResultsSource: Source[ModifiedLarEntityImpl, NotUsed] =
      Source.fromPublisher(allResultsPublisher)

    //PSV Sync
    val s3SinkPSV = S3
      .multipartUpload(bucket, key)
      .withAttributes(S3Attributes.settings(s3Settings))

    val fileStream: Source[ByteString, Any] =
      allResultsSource.zipWithIndex
        .map(mlarEntity =>
          if (mlarEntity._2 == 0)
            MLARHeader.concat(mlarEntity._1.toPublicPSV) + "\n"
          else mlarEntity._1.toPublicPSV + "\n"
        )
        .map(s => ByteString(s))

    val zipStream = Source(List((ArchiveMetadata(fileName), fileStream)))

    val resultsPSV = for {
      _            <- S3Archiver.archiveFileIfExists(bucket, key, bucketPrivate, s3Settings)
      source       = zipStream.via(Archive.zip())
      uploadResult <- S3Utils.uploadWithRetry(source, s3SinkPSV)
    } yield uploadResult

    resultsPSV onComplete {
      case Success(result) =>
        log.info("Pushed to S3: " + s"$bucket/$key" + ".")
      case Failure(t) =>
        log.info("An error has occurred with: " + key + "; Getting Public LAR Data in Future: " + t.getMessage)
    }
  }

}