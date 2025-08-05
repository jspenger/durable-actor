package durable.platform

import scala.scalanative.unsafe.*
import scala.scalanative.posix.time.*
import scala.scalanative.posix.sys.time.*
import scala.scalanative.runtime.Platform

private[durable] trait LocalDateTimeCompanionObject {
  private final val IS_WINDOWS = Platform.isWindows()

  def now(): LocalDateTime = Zone {

    val ts = stackalloc[timespec]()
    val tm = stackalloc[tm]()
    val tt = stackalloc[time_t]()

    if (!IS_WINDOWS) {
      clock_gettime(0, ts)
    } else {
      ts._1 = time(null)
      ts._2 = 0
    }

    !tt = ts._1

    localtime_r(tt, tm)

    // format: off
    val year = tm._6 + 1900
    val mon  = tm._5 + 1
    val day  = tm._3
    val hour = tm._3
    val min  = tm._2
    val sec  = tm._1
    val nano = ts._2.toInt
    // format: on

    LocalDateTime(
      // format: off
      year   = year,
      month  = mon,
      day    = day,
      hour   = hour,
      minute = min,
      second = sec,
      nano   = nano,
      // format: on
    )
  }
}
