
if [ $# -ne 1 ]; then
  echo "Usage: bash verify.sh verify.info"
  exit 1
fi

informationFile=$1

cat ${informationFile} | while read line
do
  IFS=':'
  set -- ${line}
  fileName=$1
  checkType=$2
  checkValue=$3

  if [ -f ${fileName} ]; then
    if [ "md5hash" = "${checkType}" ]; then
      checkResult=`md5sum ${fileName}`
      IFS=' '
      set -- ${checkResult}
      checkValueNow=$1
      if [ "${checkValue}" != "${checkValueNow}" ]; then
        echo "${fileName} is md5 ERROR."
        exit 3
      fi
    fi
  else
    echo "${fileName} is NOT FOUND."
    exit 2
  fi
done

echo "Verification has been completed."
exit 0
