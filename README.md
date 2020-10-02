# apis-main

## Introduction
apis-mainはSony CSLが開発した自律分散制御の電力相互融通ソフトウェアである。 
apis-mainは各ノード(双方向DC/DC Converter 経由でDC Gridと接続された蓄電システム)毎にインストールされ、 
Device Driver経由で定期的に自身のBattery残容量を取得し、Battery残容量によって決められた行動ルールに 
従ってapis-main間でネゴシエーションを行い自動でノード間の電力融通を実現するソフトウェアである。 
(この行動ルールとは他のapis-mainへ自ら充放電Requestを送るのか、他のapis-mainから受け取った充放電Requestに 
対してAcceptを返すのかなどの判断を自身のBatteryの残容量を元に決めるためのルールを指す。)  
apis-main間のネゴシエーションはEthernetなどのコミュニケーションラインが用いられ、電力融通はDC Grid上で 
直流にて行われる。apis-mainは集中管理制御のように中央で制御を行うソフトウェアは存在せず、すべて同一の 
ソフトウェアであるapis-mainのみで自律分散制御が行われる。 (apis-mainはGrid Masterと呼ばれるアドホックな 
集中管理ソフトウェアを立てて制御を行う。Grid Masterは予め設定されたルールに基づいてどのapis-mainでも立てられる。) 


## Getting Started


## Usage


## Documentation
[apis-main specification](https://github.com/oes-github/apis-main/blob/master/doc/jp/apis-main_specification.md)


## License
[Apache License Version 2.0](https://github.com/oes-github/apis-main/blob/master/LICENSE)


## Notice
[Notice](https://github.com/oes-github/apis-main/blob/master/NOTICE.md)
