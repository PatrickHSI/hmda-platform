package hmda.model.data.quality



case class DataQuality(
                        period: String,
                        dataType: String,
                        s3Path: String) {
  def toCSV: String =
    s"$period|$dataType|$s3Path"
}
