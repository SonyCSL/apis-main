echo "call start.sh"

get_pids() {
 ps -f -U oesunit | grep apis-main | grep java | while read _USER_ _PID_ _OTHERS_; do
  echo $_PID_
 done
}

_PIDS_=`get_pids`
if [ -n "$_PIDS_" ]; then
 echo "... already running"
 exit 1
fi

java -XX:OnOutOfMemoryError="'kill -KILL %p'"  -Duser.timezone=Asia/Tokyo -Djava.util.logging.config.file=./logging.properties -jar ./apis-main-2.27.0-a01-fat.jar -conf ./config.json -cp ./ -cluster  -cluster-host 192.168.0.1 &

echo "... done"