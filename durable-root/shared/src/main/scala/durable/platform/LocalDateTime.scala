package durable.platform

private[durable] case class LocalDateTime(
    year: Int,
    month: Int,
    day: Int,
    hour: Int,
    minute: Int,
    second: Int,
    nano: Int,
)

private[durable] object LocalDateTime extends LocalDateTimeCompanionObject
