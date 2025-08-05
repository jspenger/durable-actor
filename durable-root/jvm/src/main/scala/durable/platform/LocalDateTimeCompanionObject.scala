package durable.platform

private[durable] trait LocalDateTimeCompanionObject {

  def now(): LocalDateTime = {
    val t = java.time.LocalDateTime.now()

    // format: off
    LocalDateTime(
      year   = t.getYear(),
      month  = t.getMonthValue(),
      day    = t.getDayOfMonth(),
      hour   = t.getHour(),
      minute = t.getMinute(),
      second = t.getSecond(),
      nano   = t.getNano(),
    )
    // format: on
  }
}
