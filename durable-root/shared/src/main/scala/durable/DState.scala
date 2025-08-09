package durable

import scala.collection.mutable.Map
import scala.collection.mutable.Queue
import scala.collection.mutable.PriorityQueue
import scala.collection.mutable.ListBuffer

private[durable] case class DState(
    dFastQueue: Queue[DActorEvent],
    dSlowQueue: Queue[DActorEvent],
    dDelayedQueue: PriorityQueue[DActorDelaySend],
    dMap: Map[DActorRef[Any], DActorBehavior[Any]],
    dLog: ListBuffer[DActorLog]
)

private[durable] object DState {
  def empty(): DState = {
    DState(
      Queue.empty,
      Queue.empty,
      PriorityQueue.empty,
      Map.empty,
      ListBuffer.empty,
    )
  }

  // Used for the PriorityQueue
  private[durable] given Ordering[DActorDelaySend] = new Ordering[DActorDelaySend] {
    override def compare(x: DActorDelaySend, y: DActorDelaySend): Int = summon[Ordering[Long]].compare(y.until, x.until) // reversed order
  }

  // Used for the PriorityQueue
  import upickle.default.*
  private[durable] given ReadWriter[PriorityQueue[DActorDelaySend]] = summon[ReadWriter[Queue[DActorDelaySend]]].bimap(
    (q: PriorityQueue[DActorDelaySend]) => q.toQueue,
    (q: Queue[DActorDelaySend]) => PriorityQueue.from(q)
  )
}
