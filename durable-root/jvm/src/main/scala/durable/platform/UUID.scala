package durable.platform

private[durable] object UUID {

  def fresh(): java.util.UUID = {
    java.util.UUID.randomUUID()
  }
}
