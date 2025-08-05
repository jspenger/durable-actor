package durable

import durable.platform.LocalDateTime

private[durable] class Logger(name: String) {

  private def msgFactory(level: Logger.Level, msg: Any): String =
    Logger.getTimestamp
      + " "
      + level.toString().padTo(5, ' ')
      + " "
      + name
      + " - "
      + msg.toString()

  private def printLevel(level: Logger.Level, msg: Any): Unit =
    if Logger._rootLevel.lvl <= level.lvl then println(msgFactory(level, msg))

  def verbose(msg: Any): Unit =
    printLevel(Logger.Level.VERBOSE, msg)

  def debug(msg: Any): Unit =
    printLevel(Logger.Level.DEBUG, msg)

  def info(msg: Any): Unit =
    printLevel(Logger.Level.INFO, msg)

  def warn(msg: Any): Unit =
    printLevel(Logger.Level.WARN, msg)

  def error(msg: Any): Unit =
    printLevel(Logger.Level.ERROR, msg)

}

object Logger {

  // format: off
  enum Level(val lvl: Int):
    case VERBOSE extends Level(0)
    case DEBUG   extends Level(1)
    case INFO    extends Level(2)
    case WARN    extends Level(3)
    case ERROR   extends Level(4)
  // format: on

  private var _rootLevel = Level.INFO

  private[durable] def apply(name: String): Logger =
    new Logger(name)

  private[durable] def setRootLevel(level: Level): Unit =
    _rootLevel = level

  private def formatNumber(number: Int, digits: Int): String =
    val padded = "0" * (digits - number.toString().length) + number.toString()
    padded.substring(0, digits)

  private def getTimestamp: String =
    // format: off
    val now = LocalDateTime.now()
    val hours =        formatNumber(now.hour,   2)
    val minutes =      formatNumber(now.minute, 2)
    val seconds =      formatNumber(now.second, 2)
    val milliseconds = formatNumber(now.nano / 1_000_000, 3)
    s"$hours:$minutes:$seconds.$milliseconds"
    // format: on

}
