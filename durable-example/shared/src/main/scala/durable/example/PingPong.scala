package durable.example

import upickle.default.*

import spores.default.*
import spores.default.given
import durable.*
import durable.given
import durable.DActorBehaviors.*
import durable.example.utils.*

object PingPong:

  //////////////////////////////////////////////////////////////////////////////
  // PingPong Actor
  //////////////////////////////////////////////////////////////////////////////

  case class Message(n: Int, replyTo: DActorRef[Message])

  object Message:
    object MessageReadWriter extends SporeBuilder[ReadWriter[Message]](macroRW)
    given Spore[ReadWriter[Message]] = MessageReadWriter.build()

  // format: off
  object Behavior
      extends SporeBuilder[
        DActorContext[Message] ?=> Message => DActorBehavior[Message]
      ]({ msg => msg match
        
        case Message(n, replyTo) =>
          if n % 4096 == 0 then println(s"PingPong: $n messages remaining...")  

          if n > 1 then
            replyTo ! Message(n - 1, ctx.self)
            same
          else if n == 1 then
            replyTo ! Message(n - 1, ctx.self)
            stopped
          else
            ctx.log("PingPong finished!")
            stopped
      })
  // format: on

  //////////////////////////////////////////////////////////////////////////////
  // Initialization Actor
  //////////////////////////////////////////////////////////////////////////////

  object InitBehavior
      extends SporeBuilder[
        Int => DActorContext[Message] ?=> DActorBehavior[Message]
      ]({ n =>

        val pinger = ctx.spawn(
          receive2(Behavior.build()).withAnnotation(Optimistic)
        )

        val ponger = ctx.spawn(
          receive2(Behavior.build()).withAnnotation(Optimistic)
        )

        pinger ! Message(n, ponger)
        stopped
      })

  //////////////////////////////////////////////////////////////////////////////
  // Main
  //////////////////////////////////////////////////////////////////////////////

  final val N = 1024 * 1024
  final val CHECKPOINT_FILE = "pingpong.checkpoint.json"

  def main(args: Array[String]): Unit =
    val config = DActorConfig.empty.put("--force-restart", true)

    val runtime = DActorRuntime(config)

    runtime.schedule(CHECKPOINT_FILE)(
      init2(InitBehavior.build().withEnv(N)).withAnnotation(Optimistic)
    )

    AssertCheckpoint(CHECKPOINT_FILE).logContainsMessage("PingPong finished!")
