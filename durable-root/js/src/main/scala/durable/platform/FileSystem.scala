package durable.platform

import scala.scalajs.js
import scala.scalajs.js.Dynamic.global
import scala.scalajs.js.JSConverters.*
import scala.scalajs.js.typedarray.Uint8Array

import durable.platform.Path

/** File system operations for the JS platform. */
private[durable] object FileSystem {
  lazy val fs = global.require("fs")

  private def flush(path: Path, mode: String): Unit = {
    val fd = fs.openSync(path.pathStr, mode)
    try
      fs.fsyncSync(fd)
    finally
      fs.closeSync(fd)
  }

  val _path = js.Dynamic.global.require("path")
  private def parentOpt(path: Path): Option[Path] = {
    Option(
      Path(
        _path.dirname(path.pathStr).asInstanceOf[String]
      )
    )
  }

  def fileExists(path: Path): Boolean = {
    fs.existsSync(path.pathStr).asInstanceOf[Boolean]
  }

  def readFile(path: Path): Option[Array[Byte]] = {
    Option {
      val buffer = fs.readFileSync(path.pathStr)
      js
        .constructorOf[Uint8Array]
        .from(buffer)
        .asInstanceOf[Uint8Array]
        .toArray
        .map(_.toByte)
    }
  }

  def deleteFile(path: Path): Unit = {
    if (fileExists(path)) {
      fs.unlinkSync(path.pathStr)
      flush(parentOpt(path).get, "r")
    }
  }

  def writeFile(path: Path, data: Array[Byte]): Unit = {
    val uint8Array = new Uint8Array(data.map(b => (b & 0xff).toShort).toJSArray)
    fs.writeFileSync(path.pathStr, uint8Array)
    flush(path, "r+")
  }

  def moveFile(from: Path, to: Path): Unit = {
    fs.renameSync(from.pathStr, to.pathStr)
    flush(parentOpt(to).get, "r")
  }
}
