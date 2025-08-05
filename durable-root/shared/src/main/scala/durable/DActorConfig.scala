package durable

/** Immutable config. See docs for configurable options. */
class DActorConfig private[durable] (config: Map[String, Any]) {

  def get[T](key: String): Option[T] =
    config.get(key).asInstanceOf[Option[T]]

  def put[T](key: String, value: T): DActorConfig =
    DActorConfig(config.updated(key, value))

  override def toString(): String =
    s"DActorConfig($config)"
}

object DActorConfig {
  def empty: DActorConfig = new DActorConfig(Map.empty)
}
