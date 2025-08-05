import scala.util.*

import java.lang.StackTraceElement
import java.lang.Throwable

import upickle.default.*

import spores.default.*
import spores.default.given

/** JVM specific package content. */
package durable {

  //////////////////////////////////////////////////////////////////////////////
  // ReadWriter[Try[T]] and Spore[ReadWriter[Try[T]]]
  //////////////////////////////////////////////////////////////////////////////

  private case class StackTraceElementRepr(
      declaringClass: String,
      methodName: String,
      fileName: String,
      lineNumber: Int
  ) derives ReadWriter

  private object StackTraceElementRepr {

    def toJava(x: StackTraceElementRepr): StackTraceElement = {
      new StackTraceElement(
        x.declaringClass,
        x.methodName,
        x.fileName,
        x.lineNumber
      )
    }

    def fromJava(x: StackTraceElement): StackTraceElementRepr = {
      StackTraceElementRepr(
        x.getClassName(),
        x.getMethodName(),
        x.getFileName(),
        x.getLineNumber()
      )
    }
  }

  private case class ThrowableRepr(
      className: String,
      message: String,
      stack: Array[StackTraceElementRepr]
  ) derives ReadWriter

  private object ThrowableRepr {

    def toJava(x: ThrowableRepr): Throwable = {
      val clazz = Class.forName(x.className)
      val constructor = clazz.getConstructor(classOf[String])
      val instance = constructor.newInstance(x.message).asInstanceOf[Throwable]
      instance.setStackTrace(x.stack.map(StackTraceElementRepr.toJava))
      instance
    }

    def fromJava(x: Throwable): ThrowableRepr = {
      ThrowableRepr(
        x.getClass.getName(),
        x.getMessage(),
        x.getStackTrace().map(StackTraceElementRepr.fromJava)
      )
    }
  }

  // format: off
  private given [T: ReadWriter]: ReadWriter[Try[T]]     = macroRW
  private given [T: ReadWriter]: ReadWriter[Failure[T]] = macroRW
  private given [T: ReadWriter]: ReadWriter[Success[T]] = macroRW
  // format: on
  private given [T: ReadWriter]: ReadWriter[Throwable] = readwriter[String].bimap[Throwable](
    t => write(ThrowableRepr.fromJava(t)),
    str => ThrowableRepr.toJava(read[ThrowableRepr](str))
  )

  private class TryRW[T] extends SporeClassBuilder[ReadWriter[T] ?=> ReadWriter[Try[T]]](macroRW)

}
