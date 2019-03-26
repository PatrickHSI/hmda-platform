package hmda.calculator.scheduler

import akka.NotUsed
import akka.stream.ActorMaterializer
import akka.stream.alpakka.s3.impl.ListBucketVersion2
import akka.stream.alpakka.s3.scaladsl.S3Client
import akka.stream.alpakka.s3.{MemoryBufferType, S3Settings}
import akka.stream.scaladsl.{Flow, Framing, Sink}
import akka.util.ByteString
import com.amazonaws.auth.{AWSStaticCredentialsProvider, BasicAWSCredentials}
import com.amazonaws.regions.AwsRegionProvider
import com.typesafe.akka.extension.quartz.QuartzSchedulerExtension
import com.typesafe.config.ConfigFactory
import hmda.actor.HmdaActor
import hmda.calculator.entity.{AporEntity, FixedRate, VariableRate}
import hmda.calculator.parser.APORCsvParser
import hmda.calculator.scheduler.schedules.Schedules.APORScheduler

class APORScheduler extends HmdaActor {

  implicit val ec = context.system.dispatcher
  implicit val materializer = ActorMaterializer()

  override def preStart() = {
    QuartzSchedulerExtension(context.system)
      .schedule("APORScheduler", self, APORScheduler)
  }

  override def postStop() = {
    QuartzSchedulerExtension(context.system).cancelJob("APORScheduler")
  }

  override def receive: Receive = {

    case APORScheduler =>
      val awsConfig = ConfigFactory.load("application.conf").getConfig("aws")
      val accessKeyId = awsConfig.getString("access-key-id")
      val secretAccess = awsConfig.getString("secret-access-key")
      val region = awsConfig.getString("region")
      val bucket = awsConfig.getString("public-bucket")
      val environment = awsConfig.getString("environment")
      val parallelism = awsConfig.getInt("actor-flow-parallelism")

      val aporConfig =
        ConfigFactory.load("application.conf").getConfig("hmda.apor")
      val fixedRateFileName = aporConfig.getString("fixed.rate.fileName")
      val variableRateFileName = aporConfig.getString("variable.rate.fileName ")

      val awsCredentialsProvider = new AWSStaticCredentialsProvider(
        new BasicAWSCredentials(accessKeyId, secretAccess))

      val awsRegionProvider = new AwsRegionProvider {
        override def getRegion: String = region
      }

      val s3Settings = new S3Settings(
        MemoryBufferType,
        None,
        awsCredentialsProvider,
        awsRegionProvider,
        false,
        None,
        ListBucketVersion2
      )
      val s3Client = new S3Client(s3Settings)(context.system, materializer)

      log.info("Loading APOR data from S3")
      val fixedBucketKey = s"$environment/apor/$fixedRateFileName"

      val s3FixedSource = s3Client.download(bucket, fixedBucketKey)
      s3FixedSource._1
        .via(framing)
        .drop(1)
        .map(s => s.utf8String)
        .map(s => APORCsvParser(s))
        .map(apor => AporEntity.AporOperation(apor, FixedRate))
        .runWith(Sink.ignore)

      val variableBucketKey = s"$environment/apor/$variableRateFileName"
      val s3VariableSource = s3Client.download(bucket, variableBucketKey)
      s3VariableSource._1
        .via(framing)
        .drop(1)
        .map(s => s.utf8String)
        .map(s => APORCsvParser(s))
        .map(apor => AporEntity.AporOperation(apor, VariableRate))
        .runWith(Sink.ignore)

      println(variableBucketKey)
      println(fixedBucketKey)
  }

  def framing: Flow[ByteString, ByteString, NotUsed] = {
    Framing.delimiter(ByteString("\n"),
                      maximumFrameLength = 65536,
                      allowTruncation = true)
  }

}
