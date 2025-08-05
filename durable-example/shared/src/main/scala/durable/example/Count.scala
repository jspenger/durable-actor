package durable.example

import upickle.default.*

import spores.default.*
import spores.default.given

import durable.*
import durable.given
import durable.DActorBehaviors.*
import durable.example.utils.*

object Count:
  //////////////////////////////////////////////////////////////////////////////
  // Counting Actor
  //////////////////////////////////////////////////////////////////////////////

  sealed trait CountingActorMessage derives ReadWriter
  case class Increment() extends CountingActorMessage derives ReadWriter
  case class Retrieve(replyTo: DActorRef[ProducerActorMessage]) extends CountingActorMessage derives ReadWriter

  object CountingActorMessage:
    object RW extends SporeBuilder[ReadWriter[CountingActorMessage]](macroRW)
    given Spore[ReadWriter[CountingActorMessage]] = RW.build()

  object Increment:
    object RW extends SporeBuilder[ReadWriter[Increment]](macroRW)
    given Spore[ReadWriter[Increment]] = RW.build()

  object Retrieve:
    object RW extends SporeBuilder[ReadWriter[Retrieve]](macroRW)
    given Spore[ReadWriter[Retrieve]] = RW.build()

  // format: off
  object CountingActorBehavior
      extends SporeBuilder[
        Int => DActorContext[CountingActorMessage] ?=> CountingActorMessage => DActorBehavior[CountingActorMessage]
      ]({ count => msg => msg match

          case Increment() =>
            if count % 4096 == 0 then println(s"New count: ${count + 1}")

            receive2(CountingActorBehavior.build().withEnv(count + 1)).withAnnotation(Optimistic)

          case Retrieve(replyTo) =>
            replyTo ! RetrieveReply(count)
            stopped
      })
  // format: on

  //////////////////////////////////////////////////////////////////////////////
  // Producer Actor
  //////////////////////////////////////////////////////////////////////////////

  sealed trait ProducerActorMessage derives ReadWriter
  case class Start(n: Int) extends ProducerActorMessage derives ReadWriter
  case class RetrieveReply(count: Int) extends ProducerActorMessage derives ReadWriter

  object ProducerActorMessage:
    object RW extends SporeBuilder[ReadWriter[ProducerActorMessage]](macroRW)
    given Spore[ReadWriter[ProducerActorMessage]] = RW.build()

  object Start:
    object RW extends SporeBuilder[ReadWriter[Start]](macroRW)
    given Spore[ReadWriter[Start]] = RW.build()

  object RetrieveReply:
    object RW extends SporeBuilder[ReadWriter[RetrieveReply]](macroRW)
    given Spore[ReadWriter[RetrieveReply]] = RW.build()

  final val BATCH_SIZE = 1024

  // format: off
  object ProducerActorBehavior
      extends SporeBuilder[
        DActorRef[CountingActorMessage] => DActorContext[ProducerActorMessage] ?=> ProducerActorMessage => DActorBehavior[ProducerActorMessage]
      ]({ counter => msg => msg match

          case Start(n) =>
            for (i <- 0 until Math.min(n, BATCH_SIZE)) do
              // increment the counter min(n, BATCH_SIZE) times
              counter ! Increment()

            val nextN = n - BATCH_SIZE

            if (nextN > 0) then
              // continue next batch from nextN
              ctx.self ! Start(nextN)
            else
               // ask for the count
              counter ! Retrieve(ctx.self)
            same

          case RetrieveReply(count) =>
            ctx.log(s"Received count: $count")
            stopped
      })
  // format: on

  //////////////////////////////////////////////////////////////////////////////
  // Initialization Actor
  //////////////////////////////////////////////////////////////////////////////

  object InitActorBehavior
      extends SporeBuilder[
        Int => DActorContext[ProducerActorMessage] ?=> DActorBehavior[ProducerActorMessage]
      ]({ n =>

        val countingActor = ctx.spawn(
          receive2(CountingActorBehavior.build().withEnv(0)).withAnnotation(Optimistic)
        )

        val producerActor = ctx.spawn(
          receive2(ProducerActorBehavior.build().withEnv(countingActor)).withAnnotation(Optimistic)
        )

        producerActor ! Start(n) // start with captured `n`
        stopped
      })

  //////////////////////////////////////////////////////////////////////////////
  // Main
  //////////////////////////////////////////////////////////////////////////////

  final val N = 1024 * 1024
  final val CHECKPOINT_FILE = "count.checkpoint.json"

  def main(args: Array[String]): Unit =
    val config = DActorConfig.empty.put("--force-restart", true)

    val runtime = DActorRuntime(config)

    runtime.schedule(CHECKPOINT_FILE)(
      init2(InitActorBehavior.build().withEnv(N)).withAnnotation(Optimistic)
    )

    AssertCheckpoint(CHECKPOINT_FILE).logContainsMessage(s"Received count: $N")
