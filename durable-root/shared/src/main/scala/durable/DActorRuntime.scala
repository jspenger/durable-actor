package durable

import upickle.default.*
import spores.default.*

trait DActorRuntime {

  /** Schedule the `behavior`. Resumes from checkpoint `fileName` if it exists.
    *
    * Throws an exception if the checkpoint is corrupted, or if recovering from
    * the checkpoint fails. If an exception is thrown, try again or inspect the
    * checkpoint files.
    */
  def schedule[T](fileName: String)(behavior: DActorBehavior[T]): Unit

  private[durable] def actorSend[U](aref: DActorRef[U], msg: U, fastTrack: Boolean)(using Spore[ReadWriter[U]]): Unit

  private[durable] def actorSpawn[U](behavior: DActorBehavior[U], fastTrack: Boolean): DActorRef[U]

  private[durable] def actorLog(msg: String): Unit
}

object DActorRuntime {
  def apply(config: DActorConfig): DActorRuntime = new DActorRuntimeImpl(config)
}
