package io.scalechain.blockchain.chain.processor

import io.scalechain.blockchain.chain.Blockchain
import io.scalechain.blockchain.storage.index.KeyValueDatabase
import io.scalechain.blockchain.proto.Hash
import io.scalechain.blockchain.proto.Block
import io.scalechain.blockchain.script.hash
import org.slf4j.LoggerFactory

/** Process a received block.
  *
  * The block processor is responsible for following handlings.
  *
  * Case 1. Orphan block handling.
  *    - Need to put the block as an orphan blocks if the parent block does not exist yet.
  *    - Need to request to get parents of the orphan blocks to the sender of the block.
  *    - Need to recursively move orphan blocks into non-orphan blocks if the parent of them is newly added.
  *
  * Case 2. Block reorganization.
  *    - If a new best blockchain with greater chain work(=the estimated number of hash calculations) is found,
  *      We need to reorganize blocks and transactions in them.
  *
  * Case 3. Append to the best blockchain.
  *    - If the parent of the block is the current best block(=the tip of the best blockchain), put the block on top of the current best block.
  *
  */
class BlockProcessor(private val db : KeyValueDatabase, val chain : Blockchain) {
  private val logger = LoggerFactory.getLogger(BlockProcessor::class.java)

  /** Get a block.
    *
    * @param blockHash The hash of the block to get.
    * @return Some(block) if the block exists; None otherwise.
    */
  fun getBlock(blockHash : Hash) : Block? {
    return chain.getBlock(db, blockHash)?.second
  }

  /** Check if a block exists either as an orphan or non-orphan.
    * naming rule : 'exists' checks orphan blocks as well, whereas hasNonOrphan does not.
    *
    * @param blockHash The hash of the block to check.
    * @return true if the block exist; false otherwise.
    */
  fun exists(blockHash : Hash) : Boolean  {
    return hasNonOrphan(blockHash) || hasOrphan(blockHash)
  }

  /** Check if we have the given block as an orphan.
    *
    * @param blockHash The hash of the block to check.
    * @return true if the block exists as an orphan; false otherwise.
    */
  fun hasOrphan(blockHash : Hash) : Boolean {
    return chain.blockOrphanage.hasOrphan(db, blockHash)
  }

  /** Check if the block exists as a non-orphan block.
    *
    * @param blockHash the hash of the block to check.
    * @return true if the block exists as a non-orphan; false otherwise.
    */
  fun hasNonOrphan(blockHash : Hash) : Boolean {
    return chain.hasBlock(db, blockHash)
  }

  /** Put the block as an orphan block.
    *
    * @param block the block to put as an orphan block.
    */
  fun putOrphan(block : Block) : Unit {
    return chain.blockOrphanage.putOrphan(db, block)
  }

  /**
    * Get the root orphan that does not have its parent even in the orphan blocks.
    * Ex> When B1's parent is B0, B2's parent is B1 and B0 is missing, the orphan root of the B2 is B1.
    *
    * @param blockHash The block to find the root parent of it.
    * @return The hash of the orphan block whose parent is missing even in the orphan blocks list.
    */
  fun getOrphanRoot(blockHash : Hash) : Hash {
    return chain.blockOrphanage.getOrphanRoot(db, blockHash)
  }

  /**
    * Validate a block.
    *
    * @param block
    * @return
    */
  fun validateBlock(block : Block) : Unit {
    // Step 1. check the serialized block size.
    // Step 2. check the proof of work - block hash vs target hash
    // Step 3. check the block timestamp.
    // Step 4. check the first transaction is coinbase, and others are not.
    // Step 5. check each transaction in a block.
    // Step 6. check the number of script operations on the locking/unlocking script.
    // Step 7. Calculate the merkle root hash, compare it with the one in the block header.
    // TODO : Implement
    // Step 8. Make sure that the same hash with the genesis transaction does not exist. If exists, throw an error saying that the coinbase data needs to have random data to make generation transaction id different from already existing ones.
    //    assert(false)
/*
    val message = s"The block is invalid(${outPoint})."
    logger.warn(message)
    throw ChainException(ErrorCode.InvalidBlock, message)
*/
  }

  /**
    * Put the block into the blockchain. If a fork becomes the new best blockchain, do block reorganization.
    *
    * @param blockHash The hash of the block to accept.
    * @param block The block to accept.
    * @return true if the newly accepted block became the new best block.
    */
  fun acceptBlock(blockHash : Hash, block : Block) : Boolean {

    // Step 1. Need to check if the same blockheader hash exists by looking up mapBlockIndex
    // Step 2. Need to increase DoS score if an orphan block was received.
    // Step 3. Need to increase DoS score if the block hash does not meet the required difficulty.
    // Step 4. Need to get the median timestamp for the past N blocks.
    // Step 5. Need to check the lock time of all transactions.
    // Step 6. Need to check block hashes for checkpoint blocks.
    // Step 7. Write the block on the block database, reorganize blocks if necessary.
    //chain.withTransaction { implicit transactingDB =>
    //  chain.putBlock(blockHash, block)(transactingDB)
    //}

    //return chain.putBlock(db, blockHash, block)

    // Genesis block is never passed to acceptBlock, so no need to check if the block is genesis block.
    // It is passed to chain.putBlock upon startup of ScaleChainCli.
    assert( ! block.header.hashPrevBlock.isAllZero() )

    if (chain.hasBlock(db, block.header.hashPrevBlock)) { // The parent block exists
      // TODO : BUGBUG : Change to record level locking with atomic update.
      return chain.putBlock(db, blockHash, block)

    } else { // The parent block does not exist, the block is an orphan.
      chain.blockOrphanage.putOrphan(db, block)
      return false // The new block is not the best block
    }
  }

  /** Recursively accept orphan children blocks of the given block, if any.
    *
    * @param initialParentBlockHash The hash of the newly accepted parent block. As a result of it, we can accept the children of the newly accepted block.
    * @return A list of block hashes which were newly accepted.
    */
  fun acceptChildren(initialParentBlockHash : Hash) : List<Hash> {
    val acceptedChildren = arrayListOf<Hash>()

    var i = -1;
    do {
      val parentTxHash = if (acceptedChildren.size == 0) initialParentBlockHash else acceptedChildren[i]

      val dependentChildren : List<Hash> = chain.blockOrphanage.getOrphansDependingOn(db, parentTxHash)
      dependentChildren.forEach { dependentChildHash : Hash ->
        val dependentChild = chain.blockOrphanage.getOrphan(db, dependentChildHash)

        if (dependentChild != null) {
          // add to the transaction pool.
          acceptBlock(dependentChildHash, dependentChild)
          // add the hash to the acceptedChildren so that we can process children of the acceptedChildren as well.
          acceptedChildren.add(dependentChildHash)
          // delete the orphan
          chain.blockOrphanage.delOrphan(db, dependentChild)
        } else {
          // When two threads invoke acceptchildren at the same time, an orphan block might not exist because it was deleted by another thread.
          // Ex> When two peers send the same block to this node at the same time, this method can be called at the same time by two different threads
        }
      }
      chain.blockOrphanage.removeDependenciesOn(db, parentTxHash)

      i += 1
    } while( i < acceptedChildren.size)

    // Remove duplicate by converting to a set, and return as a list.
    return acceptedChildren.toSet().toList()
/*
    newly_added_blocks = listOf(block hash)
    LOOP newBlock := For each newly_added_blocks
      LOOP orphanBlock := For each orphan block which depends on the new Block as the parent of it
    // Store the block into the blockchain database.
    if (orphanBlock->AcceptBlock())
      newly_added_blocks += orphanBlock.hash
    remove the orphanBlock from mapOrphanBlocks
    remove all orphan blocks depending on newBlock from mapOrphanBlocksByPrev
*/
  }


/* TODO : Need to implement getBlockHeader and acceptBlockHeader when we implement the headers-first approach.


  /** Get a block header
    *
    * @param blockHash The hash of the block to get.
    * @return Some(blockHeader) if the block header exists; None otherwise.
    */
  fun getBlockHeader(blockHash : Hash) : Option<BlockHeader> {
    chain.getBlockHeader(blockHash)
  }

  /** Accept the block header to the blockchain.
    *
    * @param blockHeader The block header to accept.
    */
  fun acceptBlockHeader(blockHeader :BlockHeader ) : Unit {
    // Step 1 : Check if the block header already exists, return the block index of it if it already exists.
    // Step 2 : Check the proof of work and block timestamp.

    // Step 3 : Get the block index of the previous block.
    // Step 4 : Check proof of work, block timestamp, block checkpoint, block version based on majority of recent block versions.

    // Step 5 : Add the new block as a block index.
    // TODO : Implement
    assert(false)
  }
*/
  companion object {
    var theBlockProcessor : BlockProcessor? = null
    fun create(chain : Blockchain) {
      if (theBlockProcessor == null) {
        theBlockProcessor = BlockProcessor(chain.db, chain)
      }
      theBlockProcessor
    }

    fun get() : BlockProcessor {
      return theBlockProcessor!!
    }
  }
}
