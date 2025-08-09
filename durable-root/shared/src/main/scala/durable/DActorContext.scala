package durable

import upickle.default.*
import spores.default.*

sealed trait DActorContext[T] {

  def self: DActorRef[T]

  def send[U](aref: DActorRef[U], msg: U)(using Spore[ReadWriter[U]]): Unit

  def spawn[U](behavior: DActorBehavior[U]): DActorRef[U]

  def log(msg: String): Unit

  def delaySend[U](delay: Long, aref: DActorRef[U], msg: U)(using Spore[ReadWriter[U]]): Unit

}

private[durable] class DActorContextImpl(runtime: DActorRuntime) extends DActorContext[Any] {
  private[durable] var _self: DActorRef[Any] = null
  override def self: DActorRef[Any] = this._self

  override def send[U](aref: DActorRef[U], msg: U)(using Spore[ReadWriter[U]]): Unit =
    runtime.actorSend(aref, msg)

  override def spawn[U](behavior: DActorBehavior[U]): DActorRef[U] =
    runtime.actorSpawn(behavior)

  override def log(msg: String): Unit =
    runtime.actorLog(msg)

  override def delaySend[U](delay: Long, aref: DActorRef[U], msg: U)(using Spore[ReadWriter[U]]): Unit =
    runtime.actorDelaySend(delay, aref, msg)

}
