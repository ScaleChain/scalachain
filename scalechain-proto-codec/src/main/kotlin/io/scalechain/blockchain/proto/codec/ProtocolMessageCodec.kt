package io.scalechain.blockchain.proto.codec

import java.nio.charset.StandardCharsets


import java.io.EOFException

import io.scalechain.blockchain.{ErrorCode, ProtocolCodecException}
import io.scalechain.blockchain.proto._
import scodec.bits.BitVector
import scodec.codecs._
import scodec.{DecodeResult, Attempt, Codec}


/** Write/read a protocol message to/from a byte array stream.
  *
  * Source : https://en.bitcoin.it/wiki/Protocol_documentation
  */
trait ProtocolMessageCodec<T <: ProtocolMessage> : MessagePartCodec<T> {

  val command : String
  val clazz : Class<T>

  // BUGBUG : Simply use T?
  fun encode( message : ProtocolMessage ) {
    val castedMessage = clazz.cast(message)
    codec.encode(castedMessage)
  }
}

trait NetworkProtocol {
  fun getCommand(message : ProtocolMessage) : String
  // BUGBUG : USe netty's byte array instead of BitVector of scodec.
  fun encode(message : ProtocolMessage) : BitVector
  // BUGBUG : USe netty's byte array instead of BitVector of scodec.
  fun decode(command:String, bitVector:BitVector) : ProtocolMessage
}



/** Encode or decode bitcoin protocol messages to/from on-the-wire format.
  * Specification of Protocol Messages : https://en.bitcoin.it/wiki/Protocol_documentation
  *
  * Design decisions made to have only minimal amount of maintenance cost.
  * 1. Implement protocol in only one method.
  * No need to implement both encoder and decoder for each message even though we have only one on-the-wire format for a message.
  * 2. No need to change codes here and there to modify the protocol or add a protocol message.
  * Just add a data class for the message and add an entry on codecs array of ProtocolMessageCodecs class.
  */
class BitcoinProtocol : NetworkProtocol {
  val codecs = Seq(
    VersionCodec,
    VerackCodec,
    AddrCodec,
    InvCodec,
    GetDataCodec,
    NotFoundCodec,
    GetBlocksCodec,
    GetHeadersCodec,
    TransactionCodec,
    BlockCodec,
    HeadersCodec,
    GetAddrCodec,
    MempoolCodec,
    PingCodec,
    PongCodec,
    RejectCodec,
    FilterLoadCodec,
    FilterAddCodec,
    FilterClearCodec,
    MerkleBlockCodec,
    AlertCodec )

  val codecMapByCommand = (codecs.map(_.command) zip codecs).toMap
  val codecMapByClass   = (codecs.map(_.clazz) zip codecs).toMap<Class<_ <:ProtocolMessage>, ProtocolMessageCodec<_ <: ProtocolMessage>>

  fun getCommand(message : ProtocolMessage) : String {
    val codec = codecMapByClass(message.getClass)
    codec.command
  }

  fun encode(message : ProtocolMessage) : BitVector {
    val codec = codecMapByClass(message.getClass)

    codec.encode(message) match {
      case Attempt.Successful(bitVector) => {
        bitVector
      }
      case Attempt.Failure(err) => {
        throw ProtocolCodecException(ErrorCode.EncodeFailure, err.toString)
      }
    }
  }

  fun decode(command:String, bitVector: BitVector) : ProtocolMessage {
    val codec = codecMapByCommand(command).codec
    val message = codec.decode(bitVector) match {
      case Attempt.Successful(DecodeResult(decoded, remainder)) => {
        if ( remainder.isEmpty ) {
          decoded
        } else {
          throw ProtocolCodecException(ErrorCode.RemainingNotEmptyAfterDecoding)
        }
      }
      case Attempt.Failure(err) => {
        throw ProtocolCodecException(ErrorCode.DecodeFailure, err.toString)
      }
    }
    message
  }
}
