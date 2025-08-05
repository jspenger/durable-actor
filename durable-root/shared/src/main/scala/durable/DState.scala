package durable

import scala.collection.mutable.Map
import scala.collection.mutable.Queue
import scala.collection.mutable.ListBuffer

private[durable] case class DState(
    dFastQueue: Queue[DActorEvent],
    dSlowQueue: Queue[DActorEvent],
    dMap: Map[DActorRef[Any], DActorBehavior[Any]],
    dLog: ListBuffer[DActorLog]
)

private[durable] object DState {

  def empty(): DState = {
    DState(
      Queue.empty,
      Queue.empty,
      Map.empty,
      ListBuffer.empty,
    )
  }
}
