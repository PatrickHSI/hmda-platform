package hmda.messages.data.quality

import hmda.messages.CommonMessages.Event

object DataQualityEvents {

  sealed trait DataQualityEvent                               extends Event
  final case class UpdateRegulatorTable(dataQuality: DataQualityEvent)         extends DataQualityEvent
  final case class UpdatePublicTable(dataQuality: DataQualityEvent)        extends DataQualityEvent
  final case class DataQualityKafkaEvent(eventType: String, institutionEvent: DataQualityEvent)
}
