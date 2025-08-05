package durable

import upickle.default.*

import durable.platform.UUID

sealed trait DActorRef[-T]

private[durable] case class DActorRefImpl[T](
    key1: Long,
    key2: Long,
) extends DActorRef[T]

private[durable] object DActorRef {
  def fresh[T](): DActorRef[T] = {
    val uuid = UUID.fresh()

    DActorRefImpl[T](
      uuid.getLeastSignificantBits(),
      uuid.getMostSignificantBits(),
    )
  }
}
