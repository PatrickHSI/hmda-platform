package hmda.publisher.scheduler

import java.time.format.DateTimeFormatter
import java.time.{Clock, LocalDateTime}

import akka.NotUsed
import akka.stream.Materializer
import akka.stream.alpakka.s3.ApiVersion.ListBucketVersion2
import akka.stream.alpakka.s3.scaladsl.S3
import akka.stream.alpakka.s3.{MemoryBufferType, MetaHeaders, S3Attributes, S3Settings}
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.typesafe.akka.extension.quartz.QuartzSchedulerExtension
import hmda.actor.HmdaActor
import hmda.census.records.CensusRecords
import hmda.model.census.Census
import hmda.publisher.helper._
import hmda.publisher.query.component.{PublisherComponent2018, PublisherComponent2019, PublisherComponent2020}
import hmda.publisher.scheduler.schedules.Schedules._
import hmda.publisher.validation.PublishingGuard
import hmda.publisher.validation.PublishingGuard.{Period, Scope}
import hmda.query.DbConfiguration.dbConfig
import hmda.util.BankFilterUtils._

import scala.concurrent.Future
import scala.util.{Failure, Success}

class LarScheduler
  extends HmdaActor
    with PublisherComponent2018
    with PublisherComponent2019
    with PublisherComponent2020
    with LoanLimitLarHeader
    with PrivateAWSConfigLoader {

  implicit val ec               = context.system.dispatcher
  implicit val materializer     = Materializer(context)
  private val fullDate          = DateTimeFormatter.ofPattern("yyyy-MM-dd-")
  private val fullDateQuarterly = DateTimeFormatter.ofPattern("yyyy-MM-dd_")

  def larRepository2018                = new LarRepository2018(dbConfig)
  def larRepository2019                = new LarRepository2019(dbConfig)
  def larRepository2020                = new LarRepository2020(dbConfig)
  def larRepository2020Q1              = new LarRepository2020Q1(dbConfig)
  def larRepository2020Q2              = new LarRepository2020Q2(dbConfig)
  def larRepository2020Q3              = new LarRepository2020Q3(dbConfig)
  val publishingGuard: PublishingGuard = PublishingGuard.create(this)(context.system)
  val timeBarrier: QuarterTimeBarrier = new QuarterTimeBarrier(Clock.systemDefaultZone())

  val indexTractMap2018: Map[String, Census] = CensusRecords.indexedTract2018
  val indexTractMap2019: Map[String, Census] = CensusRecords.indexedTract2019

  val s3Settings = S3Settings(context.system)
    .withBufferType(MemoryBufferType)
    .withCredentialsProvider(awsCredentialsProviderPrivate)
    .withS3RegionProvider(awsRegionProviderPrivate)
    .withListBucketApiVersion(ListBucketVersion2)

  override def preStart() = {
    QuartzSchedulerExtension(context.system)
      .schedule("LarScheduler2018", self, LarScheduler2018)
    QuartzSchedulerExtension(context.system)
      .schedule("LarScheduler2019", self, LarScheduler2019)
    QuartzSchedulerExtension(context.system)
    QuartzSchedulerExtension(context.system)
      .schedule("LarScheduler2020", self, LarScheduler2020)
    QuartzSchedulerExtension(context.system)
      .schedule("LarSchedulerLoanLimit2019", self, LarSchedulerLoanLimit2019)
    QuartzSchedulerExtension(context.system)
      .schedule("LarSchedulerLoanLimit2020", self, LarSchedulerLoanLimit2020)
    QuartzSchedulerExtension(context.system)
      .schedule("LarSchedulerQuarterly2020", self, LarSchedulerQuarterly2020)
  }

  override def postStop() = {
    QuartzSchedulerExtension(context.system).cancelJob("LarScheduler2018")
    QuartzSchedulerExtension(context.system).cancelJob("LarScheduler2019")
    QuartzSchedulerExtension(context.system).cancelJob("LarScheduler2020")
    QuartzSchedulerExtension(context.system).cancelJob("LarSchedulerLoanLimit2019")
    QuartzSchedulerExtension(context.system).cancelJob("LarSchedulerLoanLimit2020")
    QuartzSchedulerExtension(context.system).cancelJob("LarSchedulerQuarterly2020")
  }

  override def receive: Receive = {

    case LarScheduler2018 =>
      publishingGuard.runIfDataIsValid(Period.y2018, Scope.Private) {
        val now           = LocalDateTime.now().minusDays(1)
        val formattedDate = fullDate.format(now)
        val fileName      = s"$formattedDate" + "2018_lar.txt"

        val allResultsSource: Source[String, NotUsed] =
          Source
            .fromPublisher(larRepository2018.getAllLARs(getFilterList()))
            .map(larEntity => larEntity.toRegulatorPSV)

        def countF: Future[Int] = larRepository2018.getAllLARsCount(getFilterList())

        publishPSVtoS3(fileName, allResultsSource, countF)
      }

    case LarScheduler2019 =>
      publishingGuard.runIfDataIsValid(Period.y2019, Scope.Private) {
        val now           = LocalDateTime.now().minusDays(1)
        val formattedDate = fullDate.format(now)
        val fileName      = s"$formattedDate" + "2019_lar.txt"
        val allResultsSource: Source[String, NotUsed] =
          Source
            .fromPublisher(larRepository2019.getAllLARs(getFilterList()))
            .map(larEntity => larEntity.toRegulatorPSV)

        def countF: Future[Int] = larRepository2019.getAllLARsCount(getFilterList())

        publishPSVtoS3(fileName, allResultsSource, countF)
      }

    case LarScheduler2020 =>
      publishingGuard.runIfDataIsValid(Period.y2020, Scope.Private) {
        val now           = LocalDateTime.now().minusDays(1)
        val formattedDate = fullDate.format(now)
        val fileName      = s"$formattedDate" + "2020_lar.txt"
        val allResultsSource: Source[String, NotUsed] =
          Source
            .fromPublisher(larRepository2020.getAllLARs(getFilterList()))
            .map(larEntity => larEntity.toRegulatorPSV)

        def countF: Future[Int] = larRepository2020.getAllLARsCount(getFilterList())

        publishPSVtoS3(fileName, allResultsSource, countF)
      }

    case LarSchedulerLoanLimit2019 =>
      publishingGuard.runIfDataIsValid(Period.y2019, Scope.Private) {
        val now           = LocalDateTime.now().minusDays(1)
        val formattedDate = fullDate.format(now)
        val fileName      = "2019F_AGY_LAR_withFlag_" + s"$formattedDate" + "2019_lar.txt"
        val allResultsSource: Source[String, NotUsed] =
          Source
            .fromPublisher(larRepository2019.getAllLARs(getFilterList()))
            .map(larEntity => larEntity.toConformingLoanLimitPSV)
            .prepend(Source(List(LoanLimitHeader)))

        def countF: Future[Int] = larRepository2019.getAllLARsCount(getFilterList())

        publishPSVtoS3(fileName, allResultsSource, countF)
      }

    case LarSchedulerLoanLimit2020 =>
      publishingGuard.runIfDataIsValid(Period.y2020, Scope.Private) {
        val now           = LocalDateTime.now().minusDays(1)
        val formattedDate = fullDate.format(now)
        val fileName      = "2020F_AGY_LAR_withFlag_" + s"$formattedDate" + "2020_lar.txt"
        val allResultsSource: Source[String, NotUsed] =
          Source
            .fromPublisher(larRepository2020.getAllLARs(getFilterList()))
            .map(larEntity => larEntity.toConformingLoanLimitPSV)
            .prepend(Source(List(LoanLimitHeader)))

        def countF: Future[Int] = larRepository2019.getAllLARsCount(getFilterList())

        publishPSVtoS3(fileName, allResultsSource, countF)
      }

    case LarSchedulerQuarterly2020 =>
      val includeQuarterly = true
      val now              = LocalDateTime.now().minusDays(1)
      val formattedDate    = fullDateQuarterly.format(now)

      def publishQuarter[Table <: LarTableBase](quarter: Period.Quarter, fileNameSuffix: String, repo: LarRepository2020Base[Table]) = {
        timeBarrier.runIfStillRelevant(quarter) {
          publishingGuard.runIfDataIsValid(quarter, Scope.Private) {
            val fileName = formattedDate + fileNameSuffix

            val allResultsSource: Source[String, NotUsed] = Source
              .fromPublisher(repo.getAllLARs(getFilterList()))
              .map(larEntity => larEntity.toRegulatorPSV)

            def countF: Future[Int] = repo.getAllLARsCount(getFilterList())

            publishPSVtoS3(fileName, allResultsSource, countF)
          }
        }
      }

      publishQuarter(Period.y2020Q1, "quarter_1_2020_lar.txt", larRepository2020Q1)
      publishQuarter(Period.y2020Q2, "quarter_2_2020_lar.txt", larRepository2020Q2)
      publishQuarter(Period.y2020Q3, "quarter_3_2020_lar.txt", larRepository2020Q3)
  }

  def publishPSVtoS3(fileName: String, rows: Source[String, NotUsed], countF: => Future[Int]): Unit = {
    val s3Path       = s"$environmentPrivate/lar/"
    val fullFilePath = SnapshotCheck.pathSelector(s3Path, fileName)

    val bytesStream: Source[ByteString, NotUsed] =
      rows
        .map(_ + "\n")
        .map(s => ByteString(s))

    val results = for {
      count <- countF
      s3Sink = S3
        .multipartUpload(bucketPrivate, fullFilePath, metaHeaders = MetaHeaders(Map(LarScheduler.entriesCountMetaName -> count.toString)))
        .withAttributes(S3Attributes.settings(s3Settings))
      result <- S3Utils.uploadWithRetry(bytesStream, s3Sink)
    } yield result

    results onComplete {
      case Success(result) =>
        log.info(s"Pushed to S3: $bucketPrivate/$fullFilePath.")
      case Failure(t) =>
        log.info(s"An error has occurred pushing LAR Data to $bucketPrivate/$fullFilePath: ${t.getMessage}")
    }

  }
}

object LarScheduler {
  val entriesCountMetaName = "entries-count"
}