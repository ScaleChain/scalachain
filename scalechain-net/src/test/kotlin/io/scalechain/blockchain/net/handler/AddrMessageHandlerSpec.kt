package io.scalechain.blockchain.net.handler

import io.kotlintest.matchers.Matchers
import java.io.File

class AddrMessageHandlerSpec : MessageHandlerTestTrait(), Matchers {

  override val testPath = File("./target/unittests-AddrMessageHandlerSpec/")

  init {
    "handle" should "" {
    }
  }
}