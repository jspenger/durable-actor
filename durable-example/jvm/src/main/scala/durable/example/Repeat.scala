package durable.example

import upickle.default.*

import spores.default.*
import spores.default.given

import durable.*
import durable.given
import durable.example.utils.*
import durable.DActorBehaviors.*

object Repeat:
  case object NextRepeat derives ReadWriter:
    given Spore[ReadWriter[NextRepeat.type]] = Spore.apply(summon)

  def repeat(repeatEveryMillis: Long, untilMillis: Long): DActorBehavior[NextRepeat.type] =
    init:
      ctx.send(ctx.self, NextRepeat)
      receive[NextRepeat.type]: _ =>
        val now = System.currentTimeMillis()
        if now < untilMillis then
          ctx.log(s"Repeat")
          ctx.delaySend(repeatEveryMillis, ctx.self, NextRepeat)
          same
        else
          ctx.log(s"Stopped")
          stopped

  final val CHECKPOINT_FILE = "repeat.checkpoint.json"
  final val REPEAT_EVERY_MILLIS = 1_000L
  final val UNTIL_MILLIS = System.currentTimeMillis() + 10_000L

  def main(args: Array[String]): Unit =
    val config = DActorConfig.empty
      .put("--force-restart", true)
      .put("--log-level", Logger.Level.INFO)

    val runtime = DActorRuntime(config)

    runtime.schedule(CHECKPOINT_FILE)(
      repeat(REPEAT_EVERY_MILLIS, UNTIL_MILLIS)
    )

    AssertCheckpoint(CHECKPOINT_FILE).logContainsMessage("Stopped")
