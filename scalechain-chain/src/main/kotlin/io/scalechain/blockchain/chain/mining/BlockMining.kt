package io.scalechain.blockchain.chain.mining

import io.scalechain.blockchain.chain.TransactionMagnet
import io.scalechain.blockchain.chain.TransactionBuilder
import io.scalechain.blockchain.chain.TransactionPool
import io.scalechain.blockchain.storage.index.DB
import io.scalechain.blockchain.ChainException
import io.scalechain.blockchain.chain.TransactionPriorityQueue
import io.scalechain.blockchain.proto.codec.TransactionCodec
import io.scalechain.blockchain.proto.*
import io.scalechain.blockchain.script.hash
import io.scalechain.blockchain.storage.index.TransactionTimeIndex
import io.scalechain.blockchain.storage.index.TransactionPoolIndex
import io.scalechain.blockchain.storage.index.KeyValueDatabase
import io.scalechain.blockchain.storage.index.TransactionDescriptorIndex
import io.scalechain.blockchain.transaction.CoinsView
import io.scalechain.blockchain.transaction.CoinAddress
import io.scalechain.util.StopWatch
import org.slf4j.LoggerFactory

interface TemporaryTransactionPoolIndex : TransactionPoolIndex {
  override fun getTxPoolPrefix() : Byte = DB.TEMP_TRANSACTION_POOL
}

interface TemporaryTransactionTimeIndex : TransactionTimeIndex {
  override fun getTxTimePrefix() : Byte = DB.TEMP_TRANSACTION_TIME
}

class TemporaryCoinsView(private val coinsView : CoinsView) : CoinsView {

  val tempTranasctionPoolIndex = object : TemporaryTransactionPoolIndex {}
  val tempTranasctionTimeIndex = object : TemporaryTransactionTimeIndex {}


  /** Return a transaction output specified by a give out point.
    *
    * @param outPoint The outpoint that points to the transaction output.
    * @return The transaction output we found.
    */
  override fun getTransactionOutput(db : KeyValueDatabase, outPoint : OutPoint) : TransactionOutput {
    // Find from the temporary transaction pool index first, and then find from the transactions in a block.
    return tempTranasctionPoolIndex.getTransactionFromPool(db, outPoint.transactionHash)?.transaction?.outputs?.get(outPoint.outputIndex) ?:
           // This is called by TransactionPriorityQueue, which already checked if the transaction is attachable.
           coinsView.getTransactionOutput(db, outPoint)
  }
}


/**
  * Created by kangmo on 6/9/16.
  */
class BlockMining(private val db: KeyValueDatabase, private val txDescIndex : TransactionDescriptorIndex, private val transactionPool : TransactionPool, private val coinsView : CoinsView) {
  private val logger = LoggerFactory.getLogger(BlockMining::class.java)

  //val watch = StopWatch()
  /*
    /** Calculate the (encoded) difficulty bits that should be in the block header.
      *
      * @param prevBlockDesc The descriptor of the previous block. This method calculates the difficulty of the next block of the previous block.
      * @return
      */
    fun calculateDifficulty(prevBlockDesc : BlockInfo) : Long {
      if (prevBlockDesc.height == 0) { // The genesis block
        GenesisBlock.BLOCK.header.target
      } else {
        // BUGBUG : Make sure that the difficulty calculation is same to the one in the Bitcoin reference implementation.
        val currentBlockHeight = prevBlockDesc.height + 1
        if (currentBlockHeight % 2016 == 0) {
          // TODO : Calculate the new difficulty bits.
          assert(false)
          -1L
        } else {
          prevBlockDesc.blockHeader.target
        }
      }
    }
  */

  /** Get the template for creating a block containing a list of transactions.
    *
    * @return The block template which has a sorted list of transactions to include into a block.
    */
  fun getBlockTemplate(coinbaseData : CoinbaseData, minerAddress : CoinAddress, maxBlockSize : Int) : BlockTemplate {
    // TODO : P1 - Use difficulty bits
    //val difficultyBits = getDifficulty()
    val difficultyBits = 10


    val bytesPerTransaction = 128
    val estimatedTransactionCount = maxBlockSize / bytesPerTransaction

//    watch.start("candidateTransactions")

    val candidateTransactions : List<Pair<Hash, Transaction>> = transactionPool.getOldestTransactions(db, estimatedTransactionCount)

//    watch.stop("candidateTransactions")

//    val newCandidates0 = transactionPool.storage.getOldestTransactionHashes(1)(db)
//    val newFirstCandidateHash0 = if (newCandidates0.isEmpty) None else Some(newCandidates0.head)


//    watch.start("validTransactions")

    var candidateTxCount = 0
    var validTxCount = 0
    val validTransactions : List<Transaction> = candidateTransactions.filter{ pair ->
      val txHash = pair.first
      //val transaction = pair.second

      candidateTxCount += 1
      if ( txDescIndex.getTransactionDescriptor(db, txHash) == null ) {
        true
      } else {
        // Remove transactions from the pool if it is in a block as well.
        //transactionPool.removeTransactionFromPool(txHash)(db)
        false
      }
    }.map { pair ->
      //val txHash = pair.first
      val transaction = pair.second

      validTxCount += 1
      transaction
    }

    // Remove transactions from the pool if it is in a block as well.
    // BUGBUG : Can we make sure that a transaction is not in the pool if it is in a block?
    candidateTransactions.filter{ pair ->
      val txHash = pair.first
      //val transaction = pair.second

      // Because we are concurrently putting transactions into the pool while putting blocks,
      // There can be some transactions in the pool as well as on txDescIndex, where only transactions in a block is stored.
      // Skip all transactions that has the transaction descriptor.

      // If the transaction descriptor exists, it means the transaction is in a block.
      txDescIndex.getTransactionDescriptor(db, txHash) != null
    }.forEach { pair ->
      val txHash = pair.first
      //val transaction = pair.second

//        logger.info(s"A Transaction in a block removed from pool. Hash : ${txHash} ")
      transactionPool.removeTransactionFromPool(db, txHash)
    }

//    val newCandidates1 = transactionPool.storage.getOldestTransactionHashes(1)(db)
//    val newFirstCandidateHash1 = if (newCandidates1.isEmpty) None else Some(newCandidates1.head)


    val generationTransaction =
      TransactionBuilder.newGenerationTransaction(coinbaseData, minerAddress)
//    watch.stop("validTransactions")


//    watch.start("selectTx")
    // Select transactions by priority and fee. Also, sort them.
    val (txCount, sortedTransactions) = selectTransactions(generationTransaction, validTransactions, maxBlockSize)
//    watch.stop("selectTx")

//    val firstCandidateHash = if (candidateTransactions.isEmpty) None else Some(candidateTransactions.head.*1)
//    val newCandidates2 = transactionPool.storage.getOldestTransactionHashes(1)(db)
 //   val newFirstCandidateHash2 = if (newCandidates2.isEmpty) None else Some(newCandidates2.head)

//    logger.info("Coin Miner stats : ${watch.toString()}, Candidate Tx Count : ${candidateTxCount}, Valid Tx Count : ${validTxCount}, Attachable Tx Count : ${txCount}")
    logger.info("Coin Miner stats : Candidate Tx Count : ${candidateTxCount}, Valid Tx Count : ${validTxCount}, Attachable Tx Count : ${txCount}")
    return BlockTemplate(difficultyBits.toLong(), sortedTransactions)
  }


  /** Select transactions to include into a block.
    *
    *  Order transactions by dependency and by fee(in descending order).
    *  List N transactions based on the priority and fee so that the serialzied size of block
    *  does not exceed the max size. (ex> 1MB)
    *
    *  <Called by>
    *  When a miner tries to create a block, we have to create a block template first.
    *  The block template has the transactions to keep in the block.
    *  In the block template, it has all fields set except the nonce and the timestamp.
    *
    *  The first criteria for ordering transactions in a block is the transaction dependency.
    *
    *  Why is ordering transactions in a block based on dependency is necessary?
    *    When blocks are reorganized, transactions in the block are detached the reverse order of the transactions stored in a block.
    *    Also, they are attached in the same order of the transactions stored in a block.
    *    The order of transactions in a block should be based on the dependency, otherwise, an outpoint in an input of a transaction may point to a non-existent transaction by the time it is attached.    *
    *
    *
    *  How?
    *    1. Create a priority queue that has complete(= all required transactions exist) transactions.
    *    2. The priority is based on the transaction fee, for now. In the future, we need to improve the priority to consider the amount of coin to transfer.
    *    3. Prepare a temporary transaction pool. The pool will be used to look up dependent transactions while trying to attach transactions.
    *    4. Try to attach each transaction in the input list depending on transactions on the temporary transaction pool instead of the transaction pool in Blockchain. (We should not actually attach the transaction, but just 'try to' attach the transaction without changing the "spent" in-point of UTXO.)
    *    5. For all complete transactions that can be attached, move from the input list to the priority queue.
    *    6. If there is any transaction in the priority queue, pick the best transaction with the highest priority into the temporary transaction pool, and Go to step 4. Otherwise, stop iteration.
    *
    * @param transactions The candidate transactions
    * @param maxBlockSize The maximum block size. The serialized block size including the block header and transactions should not exceed the size.
    * @return The count and list of transactions to put into a block.
    */
  fun selectTransactions(generationTransaction:Transaction, transactions : List<Transaction>, maxBlockSize : Int) : Pair<Int, List<Transaction>> {

    val candidateTransactions = mutableListOf<Transaction>()
    candidateTransactions.addAll( transactions )
    val selectedTransactions = arrayListOf<Transaction>()

    val BLOCK_HEADER_SIZE = 80
    val MAX_TRANSACTION_LENGTH_SIZE = 9 // The max size of variable int encoding.
    var serializedBlockSize = BLOCK_HEADER_SIZE + MAX_TRANSACTION_LENGTH_SIZE


    serializedBlockSize += TransactionCodec.encode(generationTransaction).size
    selectedTransactions.add(generationTransaction)


    // Create a temporary database just for checking if transactions can be attached.
    // We should never commit the tempDB.
    val tempDB = db.transacting()
    tempDB.beginTransaction()


    // Remove all transactions in the pool


    // For all attachable transactions, attach them, and move to the priority queue.
    //    val tempPoolDbPath = File(s"target/temp-tx-pool-for-mining-${Random.nextLong}")
    //    tempPoolDbPath.mkdir
    val tempCoinsView = TemporaryCoinsView(coinsView)

    try {
      // The TemporaryCoinsView with additional transactions in the temporary transaction pool.
      // TemporaryCoinsView returns coins in the transaction pool of the coinsView, which may not be included in tempTranasctionPoolIndex,
      // But this should be fine, because we are checking if a transaction can be attached without including the transaction pool of the coinsView.
      val txQueue = TransactionPriorityQueue(tempCoinsView)

      val txMagnet = TransactionMagnet(txDescIndex, tempCoinsView.tempTranasctionPoolIndex, tempCoinsView.tempTranasctionTimeIndex )

      var txCount = 0
      var newlySelectedTransaction : Transaction?
      do {
        val iter = candidateTransactions.listIterator()
        var consequentNonAttachableTx = 0
        // If only some of transaction is attachable, (ex> 1 out of 4000), the loop takes too long.
        // So get out of the loop if N consecutive transactions are not attachable.
        // BUGBUG : Need to Use a random value instead of 16
        while( consequentNonAttachableTx < 16 && iter.hasNext()) {
          val tx: Transaction = iter.next()
          val txHash = tx.hash()

          // Test if it can be attached.
          val isTxAttachable = try {
            txMagnet.attachTransaction(tempDB, txHash, tx, checkOnly = true)
            true
          } catch(e: ChainException) {
            // The transaction can't be attached.
            false
          }

          if (isTxAttachable) {
            //println("attachable : ${txHash}")
            // move the the transaction queue
            iter.remove()
            txQueue.enqueue(tempDB, tx)

            consequentNonAttachableTx = 0
          } else {
            consequentNonAttachableTx += 1
          }
        }

        newlySelectedTransaction = txQueue.dequeue()
        //println("fromQueue : ${newlySelectedTransaction?.hash()}")

        //        println(s"newlySelectedTransaction ${newlySelectedTransaction}")

        if (newlySelectedTransaction != null) {
          val newTx = newlySelectedTransaction
          serializedBlockSize += TransactionCodec.encode(newTx).size
          if (serializedBlockSize <= maxBlockSize) {
            // Attach the transaction
            txMagnet.attachTransaction(tempDB, newTx.hash(), newTx, checkOnly = false)
            selectedTransactions += newTx
          }
        }

      } while(newlySelectedTransaction != null && (serializedBlockSize <= maxBlockSize) )
      // Caution : serializedBlockSize is greater than the actual block size


// Test code is only for debugging purpose. never uncomment this code block
/*
      var txCount = 0

      val iter = transactions.iterator()

      println("all txs ${transactions}")
      while( iter.hasNext() && (serializedBlockSize <= maxBlockSize) ) {
        val tx: Transaction = iter.next()
//        println("selected tx : ${tx}, ${serializedBlockSize}, ${maxBlockSize}, ${iter.hasNext()}")
        val txHash = tx.hash()

        // Test if it can be atached.
        try {
          txMagnet.attachTransaction(tempDB, txHash, tx, checkOnly = true)
          serializedBlockSize += TransactionCodec.encode(tx).size

          if (serializedBlockSize <= maxBlockSize) {
            // Attach the transaction
            txMagnet.attachTransaction(tempDB, txHash, tx, checkOnly = false)

            selectedTransactions += tx
            txCount += 1

          }

        } catch(e: ChainException) {
          // The transaction can't be attached.
        }
      }
*/


/*
      if (selectedTransactions.size != selectedTransactions.toSet.size) {
        logger.error(s"Duplicate transactions found while creating a block : ${selectedTransactions.map(_.hash).mkString("\n")}")
        assert(false)
      }
*/
      return Pair(txCount, selectedTransactions)

    } finally {
      tempDB.abortTransaction()
    }
  }
}
