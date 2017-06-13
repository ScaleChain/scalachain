package io.scalechain.blockchain.net.handler

import io.kotlintest.KTestJUnitRunner
import io.kotlintest.matchers.Matchers
import org.junit.runner.RunWith
import java.io.File

@RunWith(KTestJUnitRunner::class)
class PongMessageHandlerSpec : MessageHandlerTestTrait(), Matchers {

  override val testPath = File("./build/unittests-PongMessageHandlerSpec/")

  init {
    "handle" should "" {
    }
  }
}