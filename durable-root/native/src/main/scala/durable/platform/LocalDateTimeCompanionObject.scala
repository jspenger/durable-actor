package durable.platform

import scala.scalanative.unsafe.*
import scala.scalanative.posix.time.*
import scala.scalanative.posix.sys.time.*
import scala.scalanative.meta.LinktimeInfo.isWindows

private[durable] trait LocalDateTimeCompanionObject {

  def now(): LocalDateTime = Zone {

    val ts = stackalloc[timespec]()
    val tm = stackalloc[tm]()
    val tt = stackalloc[time_t]()

    if (!isWindows) {
      clock_gettime(0, ts)
    } else {
      ts._1 = time(null)
      ts._2 = 0
    }

    !tt = ts._1

    if (!isWindows) {
      localtime_r(tt, tm)
    } else {
      tm._1 = 0
      tm._2 = 0
      tm._3 = 0
      tm._4 = 0
      tm._5 = 0
      tm._6 = 0
      tm._7 = 0
      tm._8 = 0
      tm._9 = 0
    }

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
