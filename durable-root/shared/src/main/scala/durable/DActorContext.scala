package durable

import upickle.default.*
import spores.default.*

sealed trait DActorContext[T] {

  def self: DActorRef[T]

  def send[U](aref: DActorRef[U], msg: U)(using Spore[ReadWriter[U]]): Unit

  def spawn[U](behavior: DActorBehavior[U]): DActorRef[U]

  def log(msg: String): Unit
}

private[durable] class DActorContextImpl(runtime: DActorRuntime) extends DActorContext[Any] {
  private[durable] var _optimistic: Boolean = false

  private[durable] var _self: DActorRef[Any] = null
  override def self: DActorRef[Any] = this._self

  override def send[U](aref: DActorRef[U], msg: U)(using Spore[ReadWriter[U]]): Unit =
    runtime.actorSend(aref, msg, _optimistic)

  override def spawn[U](behavior: DActorBehavior[U]): DActorRef[U] =
    runtime.actorSpawn(behavior, _optimistic)

  override def log(msg: String): Unit =
    runtime.actorLog(msg)
}
