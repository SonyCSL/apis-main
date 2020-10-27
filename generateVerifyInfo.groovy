def version = args[0]
def outFileName = args[1]

def files = [
  [path:"target/apis-main-${version}-fat.jar", name:"apis-main-${version}-fat.jar", md5:"yes"],
  [path:"src/main/resources/stop-kill.sh", name:"stop-kill.sh", md5:"yes"],
  [path:"src/main/resources/verify.sh", name:"verify.sh", md5:"yes"],
  [path:"start.sh", name:"start.sh", md5:"no"],
  [path:"stop.sh", name:"stop.sh", md5:"no"],
  [path:"kill.sh", name:"kill.sh", md5:"no"],
  [path:"config.json", name:"config.json", md5:"no"],
  [path:"logging.properties", name:"logging.properties", md5:"no"],
  [path:"cluster.xml.encrypted", name:"cluster.xml.encrypted", md5:"no"],
  [path:"key.pem", name:"key.pem", md5:"no"],
  [path:"cert.pem", name:"cert.pem", md5:"no"],
  [path:"hwConfig.json", name:"hwConfig.json", md5:"no"],
  [path:"policy.json", name:"policy.json", md5:"no"],
  [path:"scenario.json", name:"scenario.json", md5:"no"],
  [path:"verify.info", name:"verify.info", md5:"no"]
]

def MD5 = 'md5sum'
if ('type md5sum'.execute().waitFor() == 1 && 'type md5'.execute().waitFor() == 0) {
  MD5 = 'md5 -r'
}

def outFile = new File("${outFileName}")
outFile.withWriter { writer->
  files.each {
    if (it["md5"] == "yes") {
      def execCommand = "${MD5} ${it["path"]}"
      def execResult = "${execCommand}".execute().text
      def md5value = "${execResult}".split(/\s+/)[0]
      writer.write("${it["name"]}:md5hash:${md5value}\n")
    } else {
      writer.write("${it["name"]}:\n")
    }
  }
}
