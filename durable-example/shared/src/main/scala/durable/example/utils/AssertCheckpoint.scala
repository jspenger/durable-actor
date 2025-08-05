package durable.example.utils

import upickle.default.*

import durable.*
import durable.given

class AssertCheckpoint(checkpointFileName: String) {
  lazy val restored: DState = DFile.restore[DState](checkpointFileName).get

  def logContainsMessage(message: String): AssertCheckpoint =
    assert:
      restored.dLog.exists: //
        entry => entry.message.contains(message)
    this

}
