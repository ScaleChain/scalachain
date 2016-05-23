package io.scalechain.blockchain.api.command.wallet

import io.scalechain.blockchain.chain.Blockchain
import io.scalechain.blockchain.transaction.{CoinAmount, CoinAddress}
import io.scalechain.blockchain.{ErrorCode, UnsupportedFeature}
import io.scalechain.blockchain.api.command.RpcCommand
import io.scalechain.blockchain.api.domain.{NumberResult, RpcError, RpcRequest, RpcResult}
import io.scalechain.wallet.Wallet
import spray.json.DefaultJsonProtocol._

/*
  CLI command :
    # Get the coins received for a particular address, only counting transactions with six or more confirmations.
    bitcoin-cli -testnet getreceivedbyaddress mjSk1Ny9spzU2fouzYgLqGUD8U41iR35QN 6

  CLI output :
    0.30000000

  Json-RPC request :
    {"jsonrpc": "1.0", "id":"curltest", "method": "getreceivedbyaddress", "params": ["mjSk1Ny9spzU2fouzYgLqGUD8U41iR35QN", 6] }

  Json-RPC response :
    {
      "result": << Same to CLI Output >> ,
      "error": null,
      "id": "curltest"
    }
*/

/** GetReceivedByAddress: returns the total amount received by the specified address
  * in transactions with the specified number of confirmations.
  * It does not count coinbase transactions.
  *
  * Parameter #1 : Address (String, Required)
  *   The address whose transactions should be tallied.
  *
  * Parameter #2 : Confirmations (Number;int, Optional)
  *   The minimum number of confirmations an externally-generated transaction must have
  *   before it is counted towards the balance.
  *
  *   Transactions generated by this node are counted immediately.
  *   Typically, externally-generated transactions are payments to this wallet
  *   and transactions generated by this node are payments to other wallets.
  *
  *   Use 0 to count unconfirmed transactions. Default is 1
  *
  * Result: (Number;coins)
  *   The number of bitcoins received by the address, excluding coinbase transactions. May be 0.
  *
  * https://bitcoin.org/en/developer-reference#getreceivedbyaddress
  */
object GetReceivedByAddress extends RpcCommand {
  def invoke(request : RpcRequest) : Either[RpcError, Option[RpcResult]] = {
    handlingException {
      // Convert request.params.paramValues, which List[JsValue] to SignRawTransactionParams instance.
      val addressString      : String                = request.params.get[String]("Address", 0)
      val confirmation : Long = request.params.getOption[Long]("Confirmations", 1).getOrElse(1L)

      val address = CoinAddress.from(addressString)

      val amount : CoinAmount = Wallet.get.getReceivedByAddress(Blockchain.get, address, confirmation)

      Right(Some(NumberResult(amount.value)))

    }
  }
  def help() : String =
    """getreceivedbyaddress "bitcoinaddress" ( minconf )
      |
      |Returns the total amount received by the given bitcoinaddress in transactions with at least minconf confirmations.
      |
      |Arguments:
      |1. "bitcoinaddress"  (string, required) The bitcoin address for transactions.
      |2. minconf             (numeric, optional, default=1) Only include transactions confirmed at least this many times.
      |
      |Result:
      |amount   (numeric) The total amount in BTC received at this address.
      |
      |Examples:
      |
      |The amount from transactions with at least 1 confirmation
      |> bitcoin-cli getreceivedbyaddress "1D1ZrZNe3JUo7ZycKEYQQiQAWd9y54F4XZ"
      |
      |The amount including unconfirmed transactions, zero confirmations
      |> bitcoin-cli getreceivedbyaddress "1D1ZrZNe3JUo7ZycKEYQQiQAWd9y54F4XZ" 0
      |
      |The amount with at least 6 confirmation, very safe
      |> bitcoin-cli getreceivedbyaddress "1D1ZrZNe3JUo7ZycKEYQQiQAWd9y54F4XZ" 6
      |
      |As a json rpc call
      |> curl --user myusername --data-binary '{"jsonrpc": "1.0", "id":"curltest", "method": "getreceivedbyaddress", "params": ["1D1ZrZNe3JUo7ZycKEYQQiQAWd9y54F4XZ", 6] }' -H 'content-type: text/plain;' http://127.0.0.1:8332/
    """.stripMargin
}


