<?php
/*
  DIGIACCESS PDA - tokens autorizados para pda_sync.php
  -----------------------------------------------------
  Cada PDA tiene su propio token (se escribe en el PDA: Funciones > DIGIACCESS > Token del PDA).
  Para generar uno nuevo:   php -r 'echo bin2hex(random_bytes(20)), PHP_EOL;'
  Para dar de baja un PDA perdido o robado, borre su línea.
*/
$PDA_TOKENS = array(
	'PEGUE_AQUI_EL_TOKEN_DEL_PDA' => 'PDA 1',
);
