package io.scalechain.blockchain.net.handler

import io.kotlintest.matchers.Matchers
import java.io.File

class VersionMessageHandlerSpec : MessageHandlerTestTrait(), Matchers {

  override val testPath = File("./target/unittests-VersionMessageHandlerSpec/")

  init {
    "handle" should "" {
    }
  }
}