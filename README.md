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

## Getting Started
```bash

$ mkdir apis-common
$ cd apis-common
$ git clone https://github.com/SonyCSL/apis-common.git
$ mvn install
$ cd ..
$ mkdir apis-main
$ cd apis-main
$ git cone https://github.com/SonyCSL/apis-main.git
$ mvn package

```

## Usage


## Documentation
&emsp;[apis-main_specificaton(JP)](https://github.com/oes-github/apis-main/blob/master/doc/jp/apis-main_specification.md)


## License
&emsp;[Apache License Version 2.0](https://github.com/oes-github/apis-main/blob/master/LICENSE)


## Notice
&emsp;[Notice](https://github.com/oes-github/apis-main/blob/master/NOTICE.md)
