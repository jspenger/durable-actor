package durable

import upickle.default.*
import spores.default.*

sealed trait DActorBehavior[T] {
  private[durable] def isOptimistic: Boolean
}

object DActorBehaviors extends DActorBehaviorsJVM {

  def receive2[T](fun: Spore[DActorContext[T] ?=> T => DActorBehavior[T]]) = {
    ReceiveBehavior(fun, false)
  }

  def init2[T](fun: Spore[DActorContext[T] ?=> DActorBehavior[T]]) = {
    InitBehavior[T](fun, false)
  }

  def same[T]: DActorBehavior[T] = {
    SameBehavior.asInstanceOf[DActorBehavior[T]]
  }

  def same2[T]: DActorBehavior[T] = {
    SameBehavior.asInstanceOf[DActorBehavior[T]]
  }

  def stopped[T]: DActorBehavior[T] = {
    StoppedBehavior.asInstanceOf[DActorBehavior[T]]
  }

  def stopped2[T]: DActorBehavior[T] = {
    StoppedBehavior.asInstanceOf[DActorBehavior[T]]
  }

  private[durable] case class ReceiveBehavior[T](
      fun: Spore[DActorContext[T] ?=> T => DActorBehavior[T]],
      isOptimistic: Boolean = false,
  ) extends DActorBehavior[T]

  private[durable] case class InitBehavior[T](
      fun: Spore[DActorContext[T] ?=> DActorBehavior[T]],
      isOptimistic: Boolean = false,
  ) extends DActorBehavior[T]

  private[durable] case object SameBehavior extends DActorBehavior[Any] {
    override final def isOptimistic: Boolean = ???
  }

  private[durable] case object StoppedBehavior extends DActorBehavior[Any] {
    override final def isOptimistic: Boolean = ???
  }

  extension [T](thiz: DActorBehavior[T]) {
    def copy(isOptimistic: Boolean = thiz.isOptimistic): DActorBehavior[T] = thiz match {
      case b @ ReceiveBehavior(_, _) => b.copy(isOptimistic = isOptimistic)
      case b @ InitBehavior(_, _)    => b.copy(isOptimistic = isOptimistic)
      case _                         => thiz
    }
  }

  sealed trait Annotation

  /** If the results of executing this actor can be executed before
    * checkpointed.
    *
    * This behavior flag is only relevant during the `spawn` operation. Once
    * spawned, it will remain the same. Any further calls to change the flag
    * will not have any effect.
    */
  case object Optimistic extends Annotation

  extension [T](behavior: DActorBehavior[T]) {
    def withAnnotation(annotation: Annotation): DActorBehavior[T] =
      annotation match
        case Optimistic =>
          behavior.copy(isOptimistic = true)
  }
}
