echo "call start.sh"

UNAME=$(uname)
if [ "$UNAME" = 'Darwin' ] ; then
	java -Djava.net.preferIPv4Stack=true -Duser.timezone=Asia/Tokyo -Djava.util.logging.config.file=./logging.properties -Dvertx.hazelcast.config=./cluster-mac.xml -jar ../target/apis-main-3.0.0-fat.jar -conf ./config4.json -cluster -cluster-host 127.0.0.1
else
	java -Djava.net.preferIPv4Stack=true -Duser.timezone=Asia/Tokyo -Djava.util.logging.config.file=./logging.properties -Dvertx.hazelcast.config=./cluster.xml -jar ../target/apis-main-3.0.0-fat.jar -conf ./config4.json -cluster -cluster-host 127.0.0.1
fi

echo "... done"
