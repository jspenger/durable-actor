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
      isOptimistic: Boolean,
  ) extends DActorBehavior[T]

  private[durable] case class InitBehavior[T](
      fun: Spore[DActorContext[T] ?=> DActorBehavior[T]],
      isOptimistic: Boolean,
  ) extends DActorBehavior[T]

  private[durable] case object SameBehavior extends DActorBehavior[Any] {
    override final def isOptimistic: Boolean = false
  }

  private[durable] case object StoppedBehavior extends DActorBehavior[Any] {
    override final def isOptimistic: Boolean = false
  }

  sealed trait Annotation
  case object Optimistic extends Annotation

  extension [T](behavior: DActorBehavior[T]) {
    def withAnnotation(annotation: Annotation): DActorBehavior[T] =
      annotation match
        case Optimistic =>
          behavior match
            case ReceiveBehavior(fun, _) =>
              ReceiveBehavior(fun, true)
            case InitBehavior(fun, _) =>
              InitBehavior(fun, true)
            case SameBehavior =>
              SameBehavior
            case StoppedBehavior =>
              StoppedBehavior
  }
}
