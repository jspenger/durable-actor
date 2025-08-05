import scala.util.*

import upickle.default.*

import spores.default.*
import spores.default.given

/** Durable and fault tolerant actor library for Scala 3. */
package object durable {

  //////////////////////////////////////////////////////////////////////////////
  // Constants
  //////////////////////////////////////////////////////////////////////////////

  private[durable] final val DURABLE = """
       __                 __    __   
  ____/ /_  ___________ _/ /_  / /__ 
 / __  / / / / ___/ __ `/ __ \/ / _ \
/ /_/ / /_/ / /  / /_/ / /_/ / /  __/
\__,_/\__,_/_/   \__,_/_.___/_/\___/ 
""".stripPrefix("\n")

  //////////////////////////////////////////////////////////////////////////////
  // ReadWriter[T]s and Spore[ReadWriter[T]]s
  //////////////////////////////////////////////////////////////////////////////

  import durable.DActorBehaviors.*
  private[durable] given [T]: ReadWriter[DActorBehavior[T]] = macroRW
  private[durable] given [T]: ReadWriter[ReceiveBehavior[T]] = macroRW
  private[durable] given [T]: ReadWriter[InitBehavior[T]] = macroRW
  private[durable] given [T]: ReadWriter[SameBehavior.type] = macroRW
  private[durable] given [T]: ReadWriter[StoppedBehavior.type] = macroRW

  given [T]: ReadWriter[DActorRef[T]] = macroRW
  private[durable] given [T]: ReadWriter[DActorRefImpl[T]] = macroRW

  private[durable] given [T]: ReadWriter[DActorEvent] = macroRW
  private[durable] given [T]: ReadWriter[DActorSend[T]] = macroRW
  private[durable] given [T]: ReadWriter[DActorCreate[T]] = macroRW
  private[durable] given [T]: ReadWriter[DActorLog] = macroRW

  private[durable] given ReadWriter[DState] = macroRW

  private[durable] object DActorBehaviorRW extends SporeBuilder[ReadWriter[DActorBehavior[?]]](macroRW)
  given dActorBehaviorRW[T]: Spore[ReadWriter[DActorBehavior[T]]] = DActorBehaviorRW.build().asInstanceOf

  private[durable] object DActorRefRW extends SporeBuilder[ReadWriter[DActorRef[?]]](macroRW)
  given dActorRefRW[T]: Spore[ReadWriter[DActorRef[T]]] = DActorRefRW.build().asInstanceOf

  //////////////////////////////////////////////////////////////////////////////
  // Convenient syntactic extensions
  //////////////////////////////////////////////////////////////////////////////

  def ctx[T](using DActorContext[T]): DActorContext[T] = summon[DActorContext[T]]

  extension [U](aref: DActorRef[U]) {
    infix def !(msg: U)(using DActorContext[?], Spore[ReadWriter[U]]): Unit = ctx.send[U](aref, msg)
  }

}
