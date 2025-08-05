package durable.platform

private[durable] object UUID {

  def fresh(): java.util.UUID = {

    // FIXME: Use a cryptographically secure number generator instead.
    // See https://github.com/scala-js/scala-js/issues/4657.
    val rng = new java.util.Random()

    java.util.UUID.apply(
      rng.nextLong(),
      rng.nextLong(),
    )
  }
}
