ENV_FILE=.env
if [ ! -f $ENV_FILE ]; then
   echo "Copy the .env-template to .env, and edit .env file for your configuration."
   exit 
fi

source $ENV_FILE

echo "SBT_OPTS=$JAVA_OPTIONS"

# multithreadtestlayers node-count transaction-group-count node-index
#SBT_OPTS="$JAVA_OPTIONS" sbt "project scalechain-cli" "run-main io.scalechain.blockchain.cli.command.CommandExecutor multithreadtestrpc 5 10 x"

# multithreadtestlayers transaction-group-count
SBT_OPTS="$JAVA_OPTIONS" sbt "project scalechain-cli" "run-main io.scalechain.blockchain.cli.command.CommandExecutor multithreadtestlayers 40"

# multithreadtestlayers node-count transaction-group-count node-index
SBT_OPTS="$JAVA_OPTIONS" sbt "project scalechain-cli" "run-main io.scalechain.blockchain.cli.command.CommandExecutor multithreadtestrpc 5 5 $1"

java -cp ./scalechain-cli/build/libs/scalechain-cli-all.jar io.scalechain.blockchain.cli.command.CommandExecutor multithreadtestrpc 5 10 x
