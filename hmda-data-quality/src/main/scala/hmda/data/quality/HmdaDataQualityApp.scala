package hmda.data.quality

import akka.Done
import akka.actor.ActorSystem
import akka.kafka.scaladsl.Consumer
import akka.kafka.{ConsumerSettings, Subscriptions}
import akka.kafka.scaladsl.Consumer.DrainingControl
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Keep, Sink, Source}
import com.typesafe.config.ConfigFactory
import hmda.messages.data.quality.DataQualityEvents.{DataQualityKafkaEvent, UpdatePublicTable, UpdateRegulatorTable}
import hmda.messages.pubsub.{HmdaGroups, HmdaTopics}
import hmda.publication.KafkaUtils.kafkaHosts
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.slf4j.LoggerFactory

import scala.concurrent.Future

object HmdaDataQualityApp extends App {

  val log = LoggerFactory.getLogger("hmda")

  log.info(
    """
      | _    _ __  __ _____            _____        _           ____              _ _ _
      || |  | |  \/  |  __ \   /\     |  __ \      | |         / __ \            | (_) |
      || |__| | \  / | |  | | /  \    | |  | | __ _| |_ __ _  | |  | |_   _  __ _| |_| |_ _   _
      ||  __  | |\/| | |  | |/ /\ \   | |  | |/ _` | __/ _` | | |  | | | | |/ _` | | | __| | | |
      || |  | | |  | | |__| / ____ \  | |__| | (_| | || (_| | | |__| | |_| | (_| | | | |_| |_| |
      ||_|  |_|_|  |_|_____/_/    \_\ |_____/ \__,_|\__\__,_|  \___\_\\__,_|\__,_|_|_|\__|\__, |
      |                                                                                    __/ |
      |                                                                                   |___/
    """.stripMargin)

  val config = ConfigFactory.load()


  implicit val system = ActorSystem("hmda-data-quality")
  implicit val materializer = ActorMaterializer()
  implicit val ec = system.dispatcher

  val host = config.getString("hmda.data.quality.http.host")
  val port = config.getString("hmda.data.quality.http.port")

  val kafkaConfig = system.settings.config.getConfig("akka.kafka.consumer")

  val jdbcUrl = config.getString("db.db.url")
  log.info(s"Connection URL is \n\n$jdbcUrl\n")

 // system.actorOf(HmdaDataQualityApi.props(), "hmda-data-quality-api")

val consumerSettings: ConsumerSettings[String,DataQualityEventKafkaEvent] =
    ConsumerSettings(kafkaConfig,
      new StringDeserializer,
      new DataQualityKafkaEventsDeserializer)
      .withBootstrapServers(kafkaHosts)
      .withGroupId(HmdaGroups.dataQualityGroup)
      .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")

  Consumer
    .committableSource(consumerSettings,
      Subscriptions.topics(HmdaTopics.dataQualityTopic))
    .map { msg =>
      processData(msg.record.value()).map(_ => msg.committableOffset)
    }
    .map(offsetF => offsetF.map(offset => offset.commitScaladsl()))
    .toMat(Sink.seq)(Keep.both)
    .mapMaterializedValue(DrainingControl.apply)
    .run()

  val dataQualityDBProjector =
    system.spawn(DataQualityDBProjection.behavior, DataQualityDBProjection.name)

  def processData(evt: DataQualityKafkaEvent): Future[Done] = {
    Source
      .single(evt)
      .map { evt =>
        val evtType = evt.eventType match {
          case "UpdateRegulatorTable" =>
            evt.dataQualityEvent.asInstanceOf[UpdateRegulatorTable]
          case "UpdatePublicTable" =>
            evt.dataQualityEvent.asInstanceOf[UpdatePublicTable]
        case _ => evt.DataQualityEvent
        }
        dataQualityDBProjector ! ProjectEvent(evtType)
      }
      .toMat(Sink.ignore)(Keep.right)
      .run()
  }

}
