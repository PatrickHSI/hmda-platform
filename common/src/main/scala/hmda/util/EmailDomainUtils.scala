package hmda.util

import com.typesafe.config.ConfigFactory

object EmailDomainUtils {
   val config  = ConfigFactory.load()

  def getBannedDomains(): Array[String] = {
    val bannedDomainConfig = config.getConfig("filter")
    bannedDomainConfig.getString("banned-domains").toUpperCase.split(",")
  }

  def bannedDomainCheck(institutionLei: String): Boolean = {
    if (getBannedDomains().contains(institutionLei.toUpperCase)) {
      true
    } else false
  }
}
