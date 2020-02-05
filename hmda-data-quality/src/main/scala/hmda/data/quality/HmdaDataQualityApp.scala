package hmda.data.quality

import com.typesafe.config.ConfigFactory
import org.slf4j.LoggerFactory

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


}
