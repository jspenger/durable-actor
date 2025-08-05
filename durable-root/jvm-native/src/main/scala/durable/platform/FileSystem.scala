package durable.platform

import java.nio.file.*
import java.nio.channels.*
import java.util.*

import durable.platform.Path

/** File system operations for the JVM and Native platform. */
private[durable] object FileSystem {
  private final val OS_NAME = System.getProperty("os.name").toLowerCase()
  private final val IS_WINDOWS = OS_NAME.contains("windows")

  private given Conversion[Path, java.nio.file.Path] with {
    def apply(path: Path): java.nio.file.Path = Paths.get(path.pathStr)
  }

  private def flushDir(path: java.nio.file.Path): Unit = {
    if (path != null && !IS_WINDOWS) {
      val ch = FileChannel.open(path, StandardOpenOption.READ)
      try
        ch.force(true)
      finally
        ch.close()
    }
  }

  private def parentOpt(path: Path): Option[java.nio.file.Path] = {
    Option(
      path.toAbsolutePath().normalize().getParent()
    )
  }

  def fileExists(path: Path): Boolean = {
    Files.exists(path)
  }

  def readFile(path: Path): Option[Array[Byte]] = {
    if (Files.isReadable(path) && !Files.isDirectory(path)) {
      Some(Files.readAllBytes(path))
    } else {
      None
    }
  }

  def deleteFile(path: Path): Unit = {
    Files.deleteIfExists(path)
    flushDir(parentOpt(path).get)
  }

  def writeFile(path: Path, data: Array[Byte]): Unit = {
    val ch = FileChannel.open(
      path,
      StandardOpenOption.WRITE,
      StandardOpenOption.CREATE_NEW
    )
    try
      ch.write(java.nio.ByteBuffer.wrap(data))
      if (!IS_WINDOWS) {
        ch.force(true)
      }
    finally //
      ch.close()
  }

  def moveFile(from: Path, to: Path): Unit = {
    Files.move(
      from,
      to,
      StandardCopyOption.ATOMIC_MOVE,
      StandardCopyOption.REPLACE_EXISTING
    )
    flushDir(parentOpt(to).get)
  }
}
