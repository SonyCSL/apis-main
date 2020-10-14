echo "call start.sh"

java -XX:OnOutOfMemoryError="'kill -KILL %p'"  -Duser.timezone=Asia/Tokyo -Djava.util.logging.config.file=./logging.properties -jar ./apis-main-3.0.0-a01-fat.jar -conf ./config.json -cp ./ -cluster  -cluster-host 127.0.0.1 &

echo "... done"
