package durable.example

import upickle.default.*

import spores.default.*
import spores.default.given

import durable.*
import durable.given
import durable.DActorBehaviors.*
import durable.example.utils.*

object CountJVM:
  import durable.example.Count.*
  import durable.example.Count.given

  def counter(count: Int): DActorBehavior[CountingActorMessage] =
    receive:
      case Increment() =>
        if count % 4096 == 0 then println(s"New count: ${count + 1}")
        counter(count + 1)

      case Retrieve(replyTo) =>
        replyTo ! RetrieveReply(count)
        stopped

  final val BATCH_SIZE = 1024

  def producer(counter: DActorRef[CountingActorMessage]): DActorBehavior[ProducerActorMessage] =
    receive:
      case Start(n) =>
        // Note: writing `0 until Math.min(n, BATCH_SIZE)` will not compile here
        //                ^ Missing implicit for captured variable `intWrapper`
        for (i <- Range(0, Math.min(n, BATCH_SIZE))) do counter ! Increment()
        val nextN = n - BATCH_SIZE
        if (nextN > 0) then ctx.self ! Start(nextN)
        else counter ! Retrieve(ctx.self)
        same

      case RetrieveReply(count) =>
        ctx.log(s"Received count: $count")
        stopped

  def initialize(n: Int): DActorBehavior[Nothing] =
    init:
      val countingActor = ctx.spawn(
        counter(0).withAnnotation(Optimistic)
      )
      val producerActor = ctx.spawn(
        producer(countingActor).withAnnotation(Optimistic)
      )
      producerActor ! Start(n)
      stopped

  final val N = 1024 * 1024
  final val CHECKPOINT_FILE = "count.checkpoint.json"

  def main(args: Array[String]): Unit =
    val config = DActorConfig.empty.put("--force-restart", true)

    val runtime = DActorRuntime(config)

    runtime.schedule(CHECKPOINT_FILE)(
      initialize(N).withAnnotation(Optimistic)
    )

    AssertCheckpoint(CHECKPOINT_FILE)
      .logContainsMessage(s"Received count: $N")
