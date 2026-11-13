<?php
/*
  DIGIACCESS - Sincronización de PDAs (app DIGIACCESS en terminal Android)
  ------------------------------------------------------------------------
  Va en la MISMA carpeta de bdd.php y validar_acceso.php.

  La validación en línea (cédula o rostro) la sigue haciendo validar_acceso.php.
  Este script solo atiende lo que el PDA necesita además de eso:

    GET  pda_sync.php?accion=ping          -> prueba de conexión y token
    GET  pda_sync.php?accion=autorizados   -> autorizaciones vigentes (sin fotos) para validar sin conexión
    POST pda_sync.php?accion=marcas        -> marcas hechas sin conexión; se registran como validar_acceso.php
                                              (activos, ingresos, ingresosdiarios) con la fecha/hora del PDA

  Autenticación: encabezado X-PDA-Token con un token de pda_config.php.
  La llave de todo es el número de identificación (autorizaciones.identificacion).
*/

require_once('bdd.php');
require_once('pda_config.php');
date_default_timezone_set('America/Bogota');
header('Content-Type: application/json; charset=utf-8');
header('Cache-Control: no-store');

function salir($codigo, $datos)
{
	http_response_code($codigo);
	echo json_encode($datos, JSON_UNESCAPED_UNICODE);
	exit();
}

// ---------------------------------------------------------------- autenticación
$token = '';
if (isset($_SERVER['HTTP_X_PDA_TOKEN'])) {
	$token = trim($_SERVER['HTTP_X_PDA_TOKEN']);
} elseif (function_exists('getallheaders')) {
	foreach (getallheaders() as $k => $v) {
		if (strtolower($k) === 'x-pda-token') $token = trim($v);
	}
}
$dispositivo = null;
if ($token !== '' && isset($PDA_TOKENS) && is_array($PDA_TOKENS)) {
	foreach ($PDA_TOKENS as $t => $nombre) {
		if (hash_equals((string)$t, $token)) {
			$dispositivo = $nombre;
			break;
		}
	}
}
if ($dispositivo === null) {
	salir(401, array('ok' => false, 'error' => 'Token no autorizado'));
}

$accion = isset($_GET['accion']) ? $_GET['accion'] : '';

try {
	$bdd->setAttribute(PDO::ATTR_ERRMODE, PDO::ERRMODE_EXCEPTION);

	// ------------------------------------------------------------ ping
	if ($accion === 'ping') {
		salir(200, array('ok' => true, 'ahora' => date('Y/m/d H:i:s'), 'dispositivo' => $dispositivo));
	}

	// ------------------------------------------------------------ autorizados vigentes
	if ($accion === 'autorizados') {
		$ahora  = date('Y/m/d H:i:s');
		$manana = date('Y/m/d H:i:s', time() + 86400);   // incluye las que empiezan en las próximas 24 h
		$sql = "SELECT identificacion, nombre, iddpto, tipo, dias, VEHICULAR, fechainicio, fechaterminacion
		        FROM autorizaciones
		        WHERE fechaterminacion >= ? AND fechainicio <= ?
		          AND identificacion NOT IN (SELECT identificacion FROM listanegra)
		        ORDER BY id";
		$req = $bdd->prepare($sql);
		$req->execute(array($ahora, $manana));

		// Se envía fila por fila para no agotar la memoria con muchas autorizaciones
		echo '{"ok":true,"ahora":' . json_encode($ahora) . ',"autorizados":[';
		$primero = true;
		while ($f = $req->fetch(PDO::FETCH_ASSOC)) {
			$fila = array(
				'identificacion'   => (string)$f['identificacion'],
				'nombre'           => (string)$f['nombre'],
				'iddpto'           => (string)$f['iddpto'],
				'tipo'             => (string)$f['tipo'],
				'dias'             => (string)$f['dias'],
				'vehicular'        => (string)$f['VEHICULAR'],
				'fechainicio'      => (string)$f['fechainicio'],
				'fechaterminacion' => (string)$f['fechaterminacion'],
			);
			echo ($primero ? '' : ',') . json_encode($fila, JSON_UNESCAPED_UNICODE);
			$primero = false;
		}
		echo ']}';
		exit();
	}

	// ------------------------------------------------------------ marcas hechas sin conexión
	if ($accion === 'marcas') {
		$entrada = json_decode(file_get_contents('php://input'), true);
		if (!is_array($entrada) || !isset($entrada['marcas']) || !is_array($entrada['marcas'])) {
			salir(400, array('ok' => false, 'error' => 'JSON inválido'));
		}

		// Control de duplicados: si el PDA reenvía una marca (se cortó la red antes de la respuesta) no se repite
		$bdd->exec("CREATE TABLE IF NOT EXISTS pda_marcas_recibidas (
			uid VARCHAR(40) NOT NULL PRIMARY KEY,
			identificacion VARCHAR(30) NOT NULL,
			dispositivo VARCHAR(100) NOT NULL,
			recibido DATETIME NOT NULL)");

		$qYa       = $bdd->prepare("SELECT uid FROM pda_marcas_recibidas WHERE uid = ?");
		$qAut      = $bdd->prepare("SELECT nombre, iddpto, tipo FROM autorizaciones WHERE identificacion = ? ORDER BY id DESC LIMIT 1");
		$qActivo   = $bdd->prepare("SELECT nombre, iddpto FROM activos WHERE identificacion = ? LIMIT 1");
		$qDiario   = $bdd->prepare("SELECT id FROM ingresosdiarios WHERE identificacion = ? AND fecha = ? LIMIT 1");
		$insActivo = $bdd->prepare("INSERT INTO activos (nombre, identificacion, iddpto, visitante) VALUES (?, ?, ?, 'SI')");
		$delActivo = $bdd->prepare("DELETE FROM activos WHERE identificacion = ?");
		$insIngr   = $bdd->prepare("INSERT INTO ingresos (nombre, identificacion, fecha, hora, iddpto, punto, tipo, tipoautorizado) VALUES (?, ?, ?, ?, ?, ?, ?, ?)");
		$insDiario = $bdd->prepare("INSERT INTO ingresosdiarios (nombre, identificacion, fecha, iddpto, tipo) VALUES (?, ?, ?, ?, ?)");
		$insRecib  = $bdd->prepare("INSERT INTO pda_marcas_recibidas (uid, identificacion, dispositivo, recibido) VALUES (?, ?, ?, ?)");

		$recibidas = array();
		$errores   = array();
		foreach ($entrada['marcas'] as $m) {
			$uid            = isset($m['uid']) ? substr(trim((string)$m['uid']), 0, 40) : '';
			$identificacion = isset($m['identificacion']) ? preg_replace('/\D/', '', (string)$m['identificacion']) : '';
			$tipo           = isset($m['tipo']) ? strtoupper(trim((string)$m['tipo'])) : '';
			$punto          = isset($m['punto']) ? substr(trim((string)$m['punto']), 0, 200) : 'PDA';
			$fechaPda       = isset($m['fecha']) ? trim((string)$m['fecha']) : '';

			if ($uid === '' || $identificacion === '' || $identificacion === '0' || ($tipo !== 'ENTRADA' && $tipo !== 'SALIDA')) {
				$errores[] = $uid;
				$recibidas[] = $uid;          // dato inválido: se descarta para que el PDA no lo reenvíe siempre
				continue;
			}

			$qYa->execute(array($uid));
			$ya = $qYa->fetch(PDO::FETCH_ASSOC);
			$qYa->closeCursor();
			if ($ya) {                        // ya registrada antes
				$recibidas[] = $uid;
				continue;
			}

			// Fecha y hora de la marca en el PDA (mismo formato que validar_acceso.php)
			$ts = strtotime(str_replace('/', '-', $fechaPda));
			if ($ts === false || $ts > time() + 600) $ts = time();
			$fecha      = date('Y/m/d H:i:s', $ts);
			$hora       = date(' H:i:s', $ts);
			$fechaDiaria = date('Y/m/d', $ts);

			// Nombre, empresa y tipo desde la última autorización (o desde activos / lo que envió el PDA)
			$qAut->execute(array($identificacion));
			$a = $qAut->fetch(PDO::FETCH_ASSOC);
			$qAut->closeCursor();
			$qActivo->execute(array($identificacion));
			$act = $qActivo->fetch(PDO::FETCH_ASSOC);
			$qActivo->closeCursor();

			$nombre = $a ? $a['nombre'] : ($act ? $act['nombre'] : (isset($m['nombre']) ? (string)$m['nombre'] : ''));
			$iddpto = $a ? $a['iddpto'] : ($act ? $act['iddpto'] : '');
			$tipoAutorizado = $a ? $a['tipo'] : '';

			$bdd->beginTransaction();
			try {
				if ($tipo === 'ENTRADA') {
					if (!$act) $insActivo->execute(array((string)$nombre, $identificacion, (string)$iddpto));
				} else {
					$delActivo->execute(array($identificacion));
				}
				$insIngr->execute(array((string)$nombre, $identificacion, $fecha, $hora, (string)$iddpto, $punto, $tipo, (string)$tipoAutorizado));

				$qDiario->execute(array($identificacion, $fechaDiaria));
				$hayDiario = $qDiario->fetch(PDO::FETCH_ASSOC);
				$qDiario->closeCursor();
				if (!$hayDiario) {
					$insDiario->execute(array((string)$nombre, $identificacion, $fechaDiaria, (string)$iddpto, (string)$tipoAutorizado));
				}
				$insRecib->execute(array($uid, $identificacion, (string)$dispositivo, date('Y-m-d H:i:s')));
				$bdd->commit();
				$recibidas[] = $uid;
			} catch (Exception $e) {
				$bdd->rollBack();
				error_log('DIGIACCESS pda_sync marca ' . $uid . ': ' . $e->getMessage());
				$errores[] = $uid;             // no se confirma: el PDA la reintenta en la próxima sincronización
			}
		}
		salir(200, array('ok' => true, 'recibidas' => $recibidas, 'errores' => count($errores)));
	}

	salir(400, array('ok' => false, 'error' => 'Acción no válida (use ping, autorizados o marcas)'));

} catch (Exception $e) {
	if (isset($bdd) && $bdd->inTransaction()) $bdd->rollBack();
	error_log('DIGIACCESS pda_sync: ' . $e->getMessage());
	salir(500, array('ok' => false, 'error' => 'Error del servidor'));
}
