<?php
/*
  Descarga del APK DIGIACCESS PDA — configuración
  ------------------------------------------------
  GITHUB_TOKEN: token "fine-grained" de GitHub, SOLO LECTURA y SOLO para este repositorio:
    github.com › Settings › Developer settings › Personal access tokens › Fine-grained tokens › Generate new token
    - Repository access: Only select repositories › juanpa35/digiaccess-pda
    - Permissions › Repository permissions › Contents: Read-only   (nada más)
    - Expiration: la que prefiera (al vencer, genere otro y péguelo aquí)
  Este archivo NO debe quedar accesible desde la web (el .htaccess de esta carpeta lo bloquea).
*/
define('GITHUB_TOKEN', 'PEGUE_AQUI_EL_TOKEN_DE_SOLO_LECTURA');
define('GITHUB_REPO', 'juanpa35/digiaccess-pda');
define('NOMBRE_APK', 'DIGIACCESS-PDA.apk');
