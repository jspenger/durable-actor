package durable

import upickle.default.*
import spores.default.*

private[durable] sealed trait DActorEvent

private[durable] case class DActorSend[T](
    aref: DActorRef[T],
    msg: Spore[T],
) extends DActorEvent

private[durable] case class DActorCreate[T](
    aref: DActorRef[T],
    behavior: DActorBehavior[T],
) extends DActorEvent

private[durable] case class DActorLog(
    timestamp: Long,
    message: String,
) // Does not extend DActorEvent as it is not enqueued in mailbox
