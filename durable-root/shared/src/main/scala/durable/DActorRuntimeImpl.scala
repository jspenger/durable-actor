package durable

import scala.util.*
import upickle.default.*
import spores.default.*

import durable.DActorBehaviors.*
import scala.collection.mutable.ArrayBuffer

private[durable] class DActorRuntimeImpl(config: DActorConfig) extends DActorRuntime {
  private var state = DState.empty()

  Logger.setRootLevel(config.get("--log-level").getOrElse(Logger.Level.DEBUG))
  private val logger = Logger("Durable Actor Runtime")
  private val appLogger = Logger(config.get("--app-name").getOrElse("DActor App"))

  private class ActorEventBuffer {
    private val buffer = ArrayBuffer.empty[DActorEvent]

    def append(event: DActorEvent): Unit =
      buffer.append(event)

    def flush(fastTrack: Boolean): Unit =
      if fastTrack then //
        state.dFastQueue.appendAll(buffer)
      else //
        state.dSlowQueue.appendAll(buffer)

    def clear(): Unit =
      buffer.clear()
  }
  private val actorEventBuffer = new ActorEventBuffer()

  override def schedule[T](fileName: String)(behavior: DActorBehavior[T]): Unit = {
    logger.info("\n" + DURABLE)
    logger.info("Starting Durable Actor runtime")

    if config.get("--force-restart").contains(true) then {
      // Clear existing checkpoint file if config `--force-restart=true`
      logger.info(s"Configured to `--force-restart`. Clearing any existing checkpoint at file: $fileName.")
      DFile.clear(fileName)

      // And spawn the initial behavior
      this.actorSpawn(behavior)
      this.actorEventBuffer.flush(behavior.isOptimistic)
      this.actorEventBuffer.clear()
      DFile.checkpoint(fileName, this.state)

    } else {
      // Restore from existing checkpoint file, throws exception if something goes wrong
      val restored = DFile.restore[DState](fileName)
      if (restored.isDefined) then {
        this.state = restored.get
      } else {
        // If checkpoint file is missing, spawn initial behavior
        this.actorSpawn(behavior)
        this.actorEventBuffer.flush(behavior.isOptimistic)
        this.actorEventBuffer.clear()
        DFile.checkpoint(fileName, this.state)
      }
    }

    // Run until completion
    this.run(fileName, behavior)
  }

  override def actorSend[U](aref: DActorRef[U], msg: U)(using Spore[ReadWriter[U]]): Unit = {
    val event = DActorSend(aref, Env(msg))
    this.actorEventBuffer.append(event)
  }

  override def actorSpawn[U](behavior: DActorBehavior[U]): DActorRef[U] = {
    val aref = DActorRef.fresh[U](behavior.isOptimistic)
    val event = DActorCreate(aref, behavior)
    this.actorEventBuffer.append(event)
    aref
  }

  override def actorLog(msg: String): Unit = {
    appLogger.info(msg)
    val event = DActorLog(System.currentTimeMillis(), msg)
    this.actorEventBuffer.append(event)
  }

  override def actorDelaySend[U](delay: Long, aref: DActorRef[U], msg: U)(using Spore[ReadWriter[U]]): Unit = {
    val event = DActorDelaySend(System.currentTimeMillis() + delay, DActorSend(aref, Env(msg)))
    this.actorEventBuffer.append(event)
  }

  private def initializeBehavior[T](behavior: DActorBehavior[T], ctx: DActorContextImpl): DActorBehavior[T] = {
    def rec(behavior: DActorBehavior[T], ctx: DActorContextImpl): DActorBehavior[T] = {
      behavior match
        case InitBehavior(initFactory, _) =>
          initializeBehavior(initFactory.unwrap().apply(using ctx.asInstanceOf), ctx)

        case _ =>
          behavior
    }

    val initialized = rec(behavior, ctx)

    // The `rec`usive call never returns an InitBehavior so we don't need to check for it
    if (initialized eq SameBehavior) {
      throw new Exception("Cannot return SameBehavior from InitBehavior")
    }

    initialized
  }

  private def updateBehaviorMap(aref: DActorRef[Any], behavior: DActorBehavior[Any]): Unit = {
    behavior match
      case b @ ReceiveBehavior(_, _) => this.state.dMap.put(aref, b) // replace existing behavior
      case b @ StoppedBehavior       => this.state.dMap.remove(aref) // remove stopped behavior
      case SameBehavior              => () // do nothing
      case InitBehavior(_, _)        => ??? // cannot happen, here for completeness check
  }

  private def handleActorSpawn(event: DActorCreate[Any], ctx: DActorContextImpl): Unit = {
    ctx._self = event.aref
    val initialized = this.initializeBehavior(event.behavior, ctx)
    this.updateBehaviorMap(event.aref, initialized)
    this.actorEventBuffer.flush(event.aref.asInstanceOf[DActorRefImpl[Any]].isOptimistic)
    this.actorEventBuffer.clear()
  }

  private def handleActorSend(event: DActorSend[Any], ctx: DActorContextImpl): Unit = {
    val behaviorOpt = this.state.dMap.get(event.aref)

    if (behaviorOpt.isEmpty) {
      return // message sent to an actor that does not exist is ignored
    }

    val behavior = behaviorOpt.get

    if (behavior.isInstanceOf[ReceiveBehavior[_]]) {
      ctx._self = event.aref

      val spore = behavior.asInstanceOf[ReceiveBehavior[Any]].fun
      val nextBehavior = spore.unwrap().apply(using ctx)(event.msg.unwrap())
      val initialized = nextBehavior match
        case b @ InitBehavior(_, _) => this.initializeBehavior(b, ctx)
        case b @ _                  => b
      this.updateBehaviorMap(event.aref, initialized)
      this.actorEventBuffer.flush(event.aref.asInstanceOf[DActorRefImpl[Any]].isOptimistic)
      this.actorEventBuffer.clear()
    } //
    else if (behavior eq StoppedBehavior) {
      throw new Exception("Cannot receive event for StoppedBehavior.")
    } //
    else if (behavior.isInstanceOf[InitBehavior[_]]) {
      throw new Exception("Cannot receive event for InitBehavior.")
    } //
    else if (behavior eq SameBehavior) {
      throw new Exception("Cannot receive event for SameBehavior.")
    }
  }

  private def handleActorDelaySend(event: DActorDelaySend, ctx: DActorContextImpl): Unit = {
    this.state.dDelayedQueue.enqueue(event)
  }

  private def handleActorLog(event: DActorLog, ctx: DActorContextImpl): Unit = {
    this.state.dLog.append(event)
  }

  private def safeHandleEvent(event: DActorEvent, ctx: DActorContextImpl): Unit = {
    event match {
      case e: DActorCreate[_] => this.handleActorSpawn(e.asInstanceOf, ctx)
      case e: DActorSend[_]   => this.handleActorSend(e.asInstanceOf, ctx)
      case e: DActorLog       => this.handleActorLog(e, ctx)
      case e: DActorDelaySend => this.handleActorDelaySend(e, ctx)
    }
  }

  private def receiverIsOptimistic(e: DActorEvent): Boolean = {
    e match
      case DActorCreate(aref, _)     => aref.asInstanceOf[DActorRefImpl[Any]].isOptimistic
      case DActorSend(aref, _)       => aref.asInstanceOf[DActorRefImpl[Any]].isOptimistic
      case DActorLog(_, _)           => true
      case DActorDelaySend(_, event) => receiverIsOptimistic(event) // no infinite loop as `event` is always DActorSend
  }

  private def run[T](fileName: String, behavior: DActorBehavior[T]): Unit = {
    logger.info("Running...")

    val ctx = new DActorContextImpl(this)

    val batchSize = config.get("--batch-size").getOrElse(1024 * 16)
    val batchTime = config.get("--batch-time").getOrElse(100) // milliseconds

    // process
    while (this.state.dFastQueue.nonEmpty || this.state.dSlowQueue.nonEmpty || this.state.dDelayedQueue.nonEmpty) {

      logger.debug("...Running batch...")

      // Process slow queue
      var i = 0
      val slowQueueSize = this.state.dSlowQueue.size
      var t0 = System.currentTimeMillis()
      while (i < slowQueueSize && i < batchSize && (System.currentTimeMillis() - t0) < batchTime) {
        i += 1
        val e = this.state.dSlowQueue.dequeue
        this.safeHandleEvent(e, ctx)
      }

      // Process fast queue
      i = 0
      t0 = System.currentTimeMillis()
      while (this.state.dFastQueue.nonEmpty && i < batchSize && (System.currentTimeMillis() - t0) < batchTime) {
        i += 1
        val e = this.state.dFastQueue.dequeue()
        if receiverIsOptimistic(e) then //
          this.safeHandleEvent(e, ctx)
        else //
          this.state.dSlowQueue.enqueue(e)
      }

      // Process delay queue
      i = 0
      t0 = System.currentTimeMillis()
      while (this.state.dDelayedQueue.nonEmpty && i < batchSize && (System.currentTimeMillis() - t0) < batchTime) {
        i += 1
        val e = this.state.dDelayedQueue.head
        if e.until < System.currentTimeMillis() then //
          if receiverIsOptimistic(e) then //
            this.state.dFastQueue.append(e.sendEvent)
          else //
            this.state.dSlowQueue.append(e.sendEvent)
          this.state.dDelayedQueue.dequeue()
        else //
          i = batchSize // no more events to process as `until` is >= currentTimeMillis()
      }

      logger.debug("...Batch finished")
      logger.debug("...Checkpointing batch...")
      DFile.checkpoint(fileName, this.state)
      logger.debug("...Checkpoint finished")
    }

    logger.info("Running finished")
  }
}
