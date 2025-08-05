package durable

import scala.util.*
import upickle.default.*
import spores.default.*

import durable.DActorBehaviors.*

private[durable] class DActorRuntimeImpl(
    config: DActorConfig
) extends DActorRuntime {

  private var dState = DState.empty()

  Logger.setRootLevel(config.get("--log-level").getOrElse(Logger.Level.DEBUG))
  private val logger = Logger("Durable Actor Runtime")
  private val appLogger = Logger(config.get("--app-name").getOrElse("DActor App"))

  override def schedule[T](fileName: String)(behavior: DActorBehavior[T]): Unit = {
    logger.info("\n" + DURABLE)
    logger.info("Starting Durable Actor runtime")

    if config.get("--force-restart").contains(true) then {
      // Clear existing checkpoint file if config --force-restart
      logger.info(s"Configured to `--force-restart`. Clearing any existing checkpoint at file: $fileName.")
      DFile.clear(fileName)

      // And spawn the initial behavior
      actorSpawn(behavior, false)
      DFile.checkpoint(fileName, this.dState)

    } else {
      // Restore from existing checkpoint file, throws exception if something goes wrong
      val restored = DFile.restore[DState](fileName)
      if (restored.isDefined) then {
        dState = restored.get
      } else {
        // If checkpoint file is missing, spawn initial behavior
        actorSpawn(behavior, false)
        DFile.checkpoint(fileName, this.dState)
      }
    }

    // Run until completion
    this.run(fileName, behavior)
  }

  private def enqueueEvent[T](event: DActorEvent, fastTrack: Boolean): Unit = {
    if fastTrack then {
      this.dState.dFastQueue.enqueue(event)
    } else {
      this.dState.dSlowQueue.enqueue(event)
    }
  }

  override def actorSend[U](aref: DActorRef[U], msg: U, fastTrack: Boolean)(using Spore[ReadWriter[U]]): Unit = {
    val event = DActorSend(aref, Env(msg))
    enqueueEvent(event, fastTrack)
  }

  override def actorSpawn[U](behavior: DActorBehavior[U], fastTrack: Boolean): DActorRef[U] = {
    val aref = DActorRef.fresh[U]()
    val event = DActorCreate(aref, behavior)
    enqueueEvent(event, fastTrack)
    aref
  }

  override def actorLog(msg: String): Unit = {
    appLogger.info(msg)
    val entry = DActorLog(System.currentTimeMillis(), msg)
    this.dState.dLog.append(entry)
  }

  private def initializeBehavior[T](behavior: DActorBehavior[T], dCtx: DActorContextImpl): DActorBehavior[T] = {
    def rec(behavior: DActorBehavior[T], dCtx: DActorContextImpl): DActorBehavior[T] = {
      behavior match
        case InitBehavior(initFactory, _) =>
          initializeBehavior(initFactory.unwrap().apply(using dCtx.asInstanceOf), dCtx)

        case _ =>
          behavior
    }

    val initialized = rec(behavior, dCtx)

    if (initialized eq InitBehavior) {
      throw new Exception("Cannot return InitBehavior from InitBehavior")
    }
    if (initialized eq SameBehavior) {
      throw new Exception("Cannot return SameBehavior from InitBehavior")
    }

    initialized
  }

  private def handleUpdateBehaviorMap(aref: DActorRef[Any], behavior: DActorBehavior[Any], dCtx: DActorContextImpl): Unit = {
    behavior match {

      // ReceiveBehavior replaces the existing behavior
      case b @ ReceiveBehavior(_, _) =>
        this.dState.dMap.put(aref, b)

      // Stopped behaviors are discarded
      case b @ StoppedBehavior =>
        this.dState.dMap.remove(aref)

      // SameBehavior does nothing
      case SameBehavior => ()

      // InitBehavior
      case b @ InitBehavior(_, _) =>
        // The returned `initialized` behavior is guaranteed to not be a
        // InitBehavior, so this cannot cause an infinite recursion
        val initialized = initializeBehavior(b, dCtx)

        this.handleUpdateBehaviorMap(aref, initialized, dCtx)
    }
  }

  private def handleDActorCreateEvent(aref: DActorRef[Any], behavior: DActorBehavior[Any], dCtx: DActorContextImpl): Unit = {
    dCtx._self = aref.asInstanceOf[DActorRef[Any]]
    dCtx._optimistic = behavior.isOptimistic

    this.handleUpdateBehaviorMap(aref, behavior, dCtx)
  }

  private def handleDActorSendEvent(aref: DActorRef[Any], msg: Spore[Any], dCtx: DActorContextImpl): Unit = {
    val behaviorOpt = this.dState.dMap.get(aref)

    if (behaviorOpt.isEmpty) {
      return
    }

    val behavior = behaviorOpt.get

    if (behavior.isInstanceOf[ReceiveBehavior[_]]) {
      dCtx._self = aref.asInstanceOf[DActorRef[Any]]
      dCtx._optimistic = behavior.isOptimistic

      val b = behavior.asInstanceOf[ReceiveBehavior[Any]]
      val spore = b.fun
      val nextBehavior = spore.unwrap().apply(using dCtx)(msg.unwrap())

      this.handleUpdateBehaviorMap(aref, nextBehavior, dCtx)
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

  private def handleEvent(e: DActorEvent, dCtx: DActorContextImpl): Unit = e match {
    case DActorCreate(aref, behavior) =>
      this.handleDActorCreateEvent(aref.asInstanceOf, behavior.asInstanceOf, dCtx)

    case DActorSend(aref, msg) =>
      this.handleDActorSendEvent(aref.asInstanceOf, msg, dCtx)
  }

  private def receiverIsOptimistic(e: DActorEvent): Boolean = e match {
    case DActorCreate(_, behavior) =>
      behavior.isOptimistic

    case DActorSend(aref, _) =>
      dState.dMap.get(aref.asInstanceOf[DActorRef[Any]]) match
        case Some(behavior) => behavior.isOptimistic
        case None           => false // actor not found, treat as non-optimistic
  }

  private def run[T](fileName: String, behavior: DActorBehavior[T]): Unit = {
    logger.info("Running...")

    val dCtx = new DActorContextImpl(this)

    val batchSize = config.get("--batch-size").getOrElse(1024 * 16)
    val batchTime = config.get("--batch-time").getOrElse(100) // milliseconds

    // process
    while (this.dState.dFastQueue.nonEmpty || this.dState.dSlowQueue.nonEmpty) {

      logger.debug("...Running batch...")

      // Process slow queue
      var i = 0
      val slowQueueSize = this.dState.dSlowQueue.size
      var t0 = System.currentTimeMillis()
      while (i < slowQueueSize && i < batchSize && (System.currentTimeMillis() - t0) < batchTime) {
        i += 1
        val e = this.dState.dSlowQueue.dequeue
        handleEvent(e, dCtx)
      }

      // Process fast queue
      i = 0
      t0 = System.currentTimeMillis()
      while (this.dState.dFastQueue.nonEmpty && i < batchSize && (System.currentTimeMillis() - t0) < batchTime) {
        i += 1
        val e = this.dState.dFastQueue.dequeue()
        if receiverIsOptimistic(e) then //
          handleEvent(e, dCtx)
        else //
          this.dState.dSlowQueue.enqueue(e)
      }

      logger.debug("...Batch finished")
      logger.debug("...Checkpointing batch...")
      DFile.checkpoint(fileName, this.dState)
      logger.debug("...Checkpoint finished")
    }

    logger.info("Running finished")
  }
}
