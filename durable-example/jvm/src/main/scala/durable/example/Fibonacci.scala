package durable.example

import upickle.default.*

import spores.default.*
import spores.default.given

import durable.*
import durable.given
import durable.example.utils.*

object Fibonacci:
  //////////////////////////////////////////////////////////////////////////////
  // Message types together with their serialization
  //////////////////////////////////////////////////////////////////////////////

  sealed trait FibMessage derives ReadWriter
  object FibMessage:
    given Spore[ReadWriter[FibMessage]] = Spore.apply(summon[ReadWriter[FibMessage]])

  case class Fib(n: Int, replyTo: DActorRef[FibResult]) extends FibMessage derives ReadWriter
  object Fib:
    given Spore[ReadWriter[Fib]] = Spore.apply(summon[ReadWriter[Fib]])

  case class FibResult(result: Int) extends FibMessage derives ReadWriter
  object FibResult:
    given Spore[ReadWriter[FibResult]] = Spore.apply(summon[ReadWriter[FibResult]])

  //////////////////////////////////////////////////////////////////////////////
  // Durable actor behaviors
  //////////////////////////////////////////////////////////////////////////////

  import durable.DActorBehaviors.*

  def fib(n: Int, replyTo: DActorRef[FibResult]): DActorBehavior[FibResult] =
    init:
      if n == 0 then // base case
        ctx.send(replyTo, FibResult(0))
        stopped
      else if n == 1 then // base case
        ctx.send(replyTo, FibResult(1))
        stopped
      else if n > 0 then // recursive case
        // spawn fib actor behaviors for `n-1` and `n-2`
        // comment: `n - 1`, `n - 2`, and `ctx.self` are safely captured here
        ctx.spawn(fib(n - 1, ctx.self))
        ctx.spawn(fib(n - 2, ctx.self))
        // wait for results from both actors
        receive[FibResult]: res1 =>
          // comment: `replyTo` is captured here as it is needed for the inner
          // `receive`
          receive[FibResult]: res2 =>
            val res = res1.result + res2.result
            // comment: `replyTo` and `res1` are safely captured from the outer
            // scope
            ctx.send(replyTo, FibResult(res))
            stopped
      else // n < 0
        throw new Exception(s"Invalid input: n = $n, replyTo = $replyTo")
        stopped

  def fibInit(n: Int): DActorBehavior[FibResult] =
    init:
      // start the Fibonacci computation for `n`
      ctx.spawn(fib(n, ctx.self))
      // wait for the final result
      receive[FibResult]: res =>
        ctx.log(s"Final result: ${res.result}")
        stopped

  //////////////////////////////////////////////////////////////////////////////
  // Main
  //////////////////////////////////////////////////////////////////////////////

  final val N = 20
  final val CHECKPOINT_FILE = "fibonacci.checkpoint.json"

  def main(args: Array[String]): Unit =
    val config = DActorConfig.empty
      .put("--force-restart", true)
      .put("--log-level", Logger.Level.INFO)

    val runtime = DActorRuntime(config)

    runtime.schedule(CHECKPOINT_FILE)(
      fibInit(N)
    )

    AssertCheckpoint(CHECKPOINT_FILE).logContainsMessage("Final result: 6765")
