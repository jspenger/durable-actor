package durable

import scala.util.Try

import upickle.default.*

import durable.platform.Path
import durable.platform.FileSystem

private[durable] object DFile {
  private final val TMP_SUFFIX = ".tmp"

  def checkpoint[T](fileName: String, obj: T)(using ReadWriter[T]): Unit = {
    this.deleteCheckpoint(fileName + TMP_SUFFIX)
    this.checkpointToFile(fileName + TMP_SUFFIX, obj)
    this.deleteCheckpoint(fileName)
    this.renameCheckpoint(fileName + TMP_SUFFIX, fileName)
  }

  /** Restore a `T` from checkpoints at `fileName` and `fileName` +
    * `TMP_SUFFIX`.
    *
    * Returns `None` if the files do not exist. Returns `Some[T]` if the
    * recovery was successful. Otherwise, throws an exception. If an exception
    * is thrown, then the checkpoint files may be corrupted.
    */
  def restore[T](fileName: String)(using ReadWriter[T]): Option[T] = {
    if (!FileSystem.fileExists(Path(fileName)) && !FileSystem.fileExists(Path(fileName + TMP_SUFFIX))) {
      return None
    }

    val res = this.restoreFromFile(fileName).orElse {
      // It is always safe to try to restore from either checkpoint file. The
      // TMP file is guaranteed to be "newer" than the main file. However, it is
      // safe to restore from the main file even if the TMP file exists.
      this.restoreFromFile(fileName + TMP_SUFFIX)
    }
    if res.isFailure then {
      // If the restore fails, then the checkpoint files may be corrupted.
      // However, it may also be the case that there was a transient error.
      // The user should try again or manually inspect the checkpoint files.
      throw new Exception(s"Failed to restore from: $fileName and ${fileName + TMP_SUFFIX}. Files may be corrupted.")
    }
    res.toOption
  }

  def clear(fileName: String): Unit = {
    this.deleteCheckpoint(fileName)
    this.deleteCheckpoint(fileName + TMP_SUFFIX)
  }

  private def checkpointToFile[T](fileName: String, obj: T)(using ReadWriter[T]): Unit = {
    val data = write(obj).getBytes()
    val path = Path(fileName)
    FileSystem.writeFile(path, data)
  }

  private def restoreFromFile[T](fileName: String)(using ReadWriter[T]): Try[T] = {
    Try {
      val path = Path(fileName)
      val data = FileSystem.readFile(path).get
      read[T](data)
    }
  }

  private def deleteCheckpoint(fileName: String): Unit = {
    val path = Path(fileName)
    FileSystem.deleteFile(path)
  }

  private def renameCheckpoint(fromFileName: String, toFileName: String): Unit = {
    val from = Path(fromFileName)
    val to = Path(toFileName)
    FileSystem.moveFile(from, to)
  }
}
