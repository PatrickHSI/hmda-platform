package hmda.messages.data.quality

import akka.actor.typed.ActorRef
import hmda.messages.CommonMessages.Command
import hmda.model.data.quality.{DataQuality, DataQualityDetail}

object DataQualityCommands {
  sealed trait DataQualityCommand extends Command

  final case class UpdateRegulatorTable(dataQuality: DataQuality,replyTo: ActorRef[DataQualityDetail]) extends DataQualityCommand

  final case class UpdatePublicTable(dataQuality: DataQuality,replyTo: ActorRef[Option[DataQualityDetail]]) extends DataQualityCommand
}
