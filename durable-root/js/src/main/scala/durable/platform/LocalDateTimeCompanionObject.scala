package durable.platform

import scala.scalajs.js

private[durable] trait LocalDateTimeCompanionObject {

  def now(): LocalDateTime = {
    val t = new js.Date()

    // format: off
    LocalDateTime(
      year   = t.getFullYear().toInt,
      month  = t.getMonth().toInt,
      day    = t.getDay().toInt,
      hour   = t.getHours().toInt,
      minute = t.getMinutes().toInt,
      second = t.getSeconds().toInt,
      nano   = t.getMilliseconds().toInt * 1_000_000,
    )
    // format: on
  }
}
