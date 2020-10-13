# apis-main

## Introduction
apis-mainはSony CSLが開発した自律分散制御の電力相互融通ソフトウェアである。    
apis-mainは各ノード毎にインストールされ、定期的に自身のBattery残容量を取得し、バッテリ残容量に  
よって決められた行動ルールに従ってapis-main間でネゴシエーションを  行って自動でノード間の  
電力融通を実現するソフトウェアである。  
apis-main間のネゴシエーションはイーサネットなどのコミュニケーションラインが用いられ電力融通は  
DC Grid上で直流にて行われる。  
apis-mainは集中管理制御のように中央で制御を行うソフトウェアは存在せず、すべて同一のソフトウェア  
であるapis-mainのみで自律分散制御が行われる。  

![キャプチャ](https://user-images.githubusercontent.com/71874910/94899039-87ea0600-04cd-11eb-96a0-afa5466b3742.PNG)

## Installation
```bash
$ git clone https://github.com/SonyCSL/apis-bom.git
$ cd apis-bom
$ mvn install
$ cd ../
$ git clone https://github.com/SonyCSL/apis-common.git
$ cd apis-common
$ mvn install
$ cd ../
$ git cone https://github.com/SonyCSL/apis-main.git
$ cd apis-main
$ mvn package
$ cd ../
$ mkdir apis-main_exe
$ cp ./apis-main/target/apis-main-*-fat.jar ./apis-main_exe
$ cp ./apis-main/setting_files/* ./apis-main_exe
```

## Parameter Setting
Set the following file parameters in the apis-main_exe at least to suit your environment.   
Refer to "Chapter 6, About Configuration Files" in the [apis-main_specification](#anchor1) for more information.

&emsp;config.json   
&emsp;&emsp;&emsp;- communityId   &emsp;(default : oss_communityId)  
&emsp;&emsp;&emsp;- clusterId     &emsp;(default : oss_clusterId)  
&emsp;&emsp;&emsp;- unitId        &emsp;(default : E001)  
&emsp;&emsp;&emsp;- unitName      &emsp;(default : E001)  
&emsp;&emsp;&emsp;- systemType    &emsp;(default : dcdc_emulator)  

&emsp;policy.json    
&emsp;&emsp;&emsp;- memberUnitIds  &emsp;(default : "E001", "E002", "E003", "E004")

&emsp;cluster.xml  
&emsp;&emsp;&emsp;- \<interface\>  &emsp;(default : 127.0.0.1)

&emsp;start.sh  
&emsp;&emsp;&emsp;- java arguments &emsp;(default : 127.0.0.1) 


## Running

```bash
$ cd apis-main_exe
$ bash start.sh
```

## Tips
In order to run multiple apis-mains on the same PC, multiple config.json and start.sh are needed.

### \<Example\>
To run 3 apis-mains on the same PC.

[Parameter Setting Example]  

&emsp;config1.json     
&emsp;&emsp;&emsp;- unitId     : E001  
&emsp;&emsp;&emsp;- unitName   : E001  
&emsp;&emsp;&emsp;- stateFileFormat   : "{tmpdir}/apis/state/%s"  
&emsp;&emsp;&emsp;- dealLogDirFormat   : "{tmpdir}/apis/dealLog/'uuuu'/'MM'/'dd'" 

&emsp;start1.sh  
&emsp;&emsp;&emsp;- java arguments "-conf ./config1.json"  
<br />

&emsp;config2.json   
&emsp;&emsp;&emsp;- unitId : E002  
&emsp;&emsp;&emsp;- unitName   : E002  
&emsp;&emsp;&emsp;- stateFileFormat   : "{tmpdir}/apis/state2/%s"  
&emsp;&emsp;&emsp;- dealLogDirFormat   : "{tmpdir}/apis/dealLog2/'uuuu'/'MM'/'dd'"  

&emsp;start2.sh  
&emsp;&emsp;&emsp;- java arguments "-conf ./config2.json"  
<br />

&emsp;config3.json    
&emsp;&emsp;&emsp;- unitId : E003  
&emsp;&emsp;&emsp;- unitName   : E003  
&emsp;&emsp;&emsp;- stateFileFormat   : "{tmpdir}/apis/state3/%s"  
&emsp;&emsp;&emsp;- dealLogDirFormat   : "{tmpdir}/apis/dealLog3/'uuuu'/'MM'/'dd'"  

&emsp;start3.sh  
&emsp;&emsp;&emsp;- java arguments "-conf ./config3.json"  
<br />

&emsp;config4.json  
&emsp;&emsp;&emsp;- unitId : E004  
&emsp;&emsp;&emsp;- unitName   : E004  
&emsp;&emsp;&emsp;- stateFileFormat   : "{tmpdir}/apis/state4/%s"  
&emsp;&emsp;&emsp;- dealLogDirFormat   : "{tmpdir}/apis/dealLog4/'uuuu'/'MM'/'dd'"  

&emsp;start4.sh  
&emsp;&emsp;&emsp;- java arguments "-conf ./config4.json" 

All other files are used in common.

<br />

[Running]  
```bash
$ cd apis-main_exe
$ bash start1.sh
$ bash start2.sh
$ bash start3.sh
```
  
<a id="anchor1"></a>
## Documentation
&emsp;[apis-main_specificaton(JP)](https://github.com/oes-github/apis-main/blob/master/doc/jp/apis-main_specification.md)


## License
&emsp;[Apache License Version 2.0](https://github.com/oes-github/apis-main/blob/master/LICENSE)


## Notice
&emsp;[Notice](https://github.com/oes-github/apis-main/blob/master/NOTICE.md)
