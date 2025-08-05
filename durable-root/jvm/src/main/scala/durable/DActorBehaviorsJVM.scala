package durable

import spores.default.*

/** Internal API. Extended from by the [[durable.DActorBehaviors]] companion
  * object. This is a hack for having platform-specific operations in the
  * companion object.
  */
private[durable] trait DActorBehaviorsJVM {

  inline def receive[T](inline fun: DActorContext[T] ?=> T => DActorBehavior[T]) = {
    DActorBehaviors.receive2(Spore.auto(fun))
  }

  inline def init[T](inline fun: DActorContext[T] ?=> DActorBehavior[T]) = {
    DActorBehaviors.init2(Spore.auto(fun))
  }

}
