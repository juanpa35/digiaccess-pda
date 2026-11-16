<?php
/*
  DIGIACCESS - validar_acceso.php  (variante facial / Oikos)
  ----------------------------------------------------------
  Validacion en linea para la app DIGIACCESS PDA (rostro o cedula).
  Va en la MISMA carpeta de bdd.php (y de pda_sync.php).

  El PDA envia por POST:
    identificacion, punto, tipo (ENTRADA|SALIDA), vehicular (PEATONAL|VEHICULAR),
    formato=lcd, medio (CEDULA|DIGITADA|FACIAL, informativo)
    [prueba=1]  -> valida pero NO escribe (dry-run, lo usa soporte; el PDA nunca lo envia)

  Responde texto plano:  codigo|motivo|nombre
    codigo: 1=permitido, 2=rechazado, 4=antipassback, 0=sin validar
    motivo: OK | NO_REGISTRADO | LISTA_NEGRA | FUERA_DIA | SIN_VEHICULAR
            | ANTIPASSBACK_ENTRADA | ANTIPASSBACK_SALIDA | ID_INVALIDO | ERROR_SERVIDOR

  Reglas (las mismas de las controladoras y de la decision offline del PDA):
    vigencia por fecha, dias de la semana, lista negra y permiso vehicular.
    Quien ya esta adentro (tabla activos) siempre puede SALIR.
    Antipassback: desactivado por defecto (las controladoras actuales no lo aplican);
    poner $ANTIPASSBACK = true para activarlo.
*/

require_once('bdd.php');
date_default_timezone_set('America/Bogota');
header('Content-Type: text/plain; charset=utf-8');
header('Cache-Control: no-store');

$ANTIPASSBACK = true;

function respon($cod, $motivo, $nombre = '')
{
    $nombre = str_replace(array('|', "\r", "\n"), ' ', (string)$nombre);
    echo $cod . '|' . $motivo . '|' . $nombre;
    exit();
}

$identificacion = isset($_POST['identificacion']) ? preg_replace('/\D/', '', (string)$_POST['identificacion']) : '';
$tipo      = isset($_POST['tipo']) ? strtoupper(trim((string)$_POST['tipo'])) : 'ENTRADA';
$vehicular = isset($_POST['vehicular']) ? strtoupper(trim((string)$_POST['vehicular'])) : 'PEATONAL';
$punto     = isset($_POST['punto']) ? substr(trim((string)$_POST['punto']), 0, 200) : 'PDA';
$prueba    = isset($_POST['prueba']) && (string)$_POST['prueba'] === '1';

if ($identificacion === '' || $identificacion === '0') respon(2, 'ID_INVALIDO');
if ($tipo !== 'ENTRADA' && $tipo !== 'SALIDA') $tipo = 'ENTRADA';
if ($punto === '') $punto = 'PDA';

try {
    $bdd->setAttribute(PDO::ATTR_ERRMODE, PDO::ERRMODE_EXCEPTION);
    $hoy = date('Y-m-d');

    // Autorizacion vigente (por fecha), la mas reciente
    $q = $bdd->prepare("SELECT nombre, iddpto, tipo, dias, VEHICULAR
                        FROM autorizaciones
                        WHERE identificacion = ? AND DATE(fechainicio) <= ? AND DATE(fechaterminacion) >= ?
                        ORDER BY id DESC LIMIT 1");
    $q->execute(array($identificacion, $hoy, $hoy));
    $a = $q->fetch(PDO::FETCH_ASSOC);

    // Esta adentro?
    $qa = $bdd->prepare("SELECT id, nombre, iddpto FROM activos WHERE identificacion = ? LIMIT 1");
    $qa->execute(array($identificacion));
    $activo = $qa->fetch(PDO::FETCH_ASSOC);

    $nombre = $a ? (string)$a['nombre'] : ($activo ? (string)$activo['nombre'] : '');
    $iddpto = $a ? (string)$a['iddpto'] : ($activo ? (string)$activo['iddpto'] : '');
    $tipoAut = $a ? (string)$a['tipo'] : '';

    // Quien ya esta adentro siempre puede salir (sin cotejar vigencia/dias/vehicular/lista negra)
    $salidaLibre = ($tipo === 'SALIDA' && $activo);

    if (!$salidaLibre) {
        if (!$a) respon(2, 'NO_REGISTRADO', $nombre);

        // Lista negra
        $qn = $bdd->prepare("SELECT id FROM listanegra WHERE identificacion = ? LIMIT 1");
        $qn->execute(array($identificacion));
        if ($qn->fetch(PDO::FETCH_ASSOC)) respon(2, 'LISTA_NEGRA', $nombre);

        if ($tipo === 'ENTRADA') {
            // Dias de la semana (1=lunes .. 7=domingo)
            $dow  = (int)date('N');
            $dias = strtolower((string)$a['dias']);
            $fueraDia = ($dias === 'lunesaviernes' && !($dow >= 1 && $dow <= 5))
                     || ($dias === 'lunesasabado' && !($dow >= 1 && $dow <= 6))
                     || ($dias === 'findesemana'  && !($dow === 6 || $dow === 7));
            if ($fueraDia) respon(2, 'FUERA_DIA', $nombre);

            // Permiso vehicular (igual que la decision offline del PDA: SI/BICICLETA/MOTO)
            if ($vehicular === 'VEHICULAR') {
                $v = strtoupper(trim((string)$a['VEHICULAR']));
                if (!($v === 'SI' || $v === 'BICICLETA' || $v === 'MOTO')) respon(2, 'SIN_VEHICULAR', $nombre);
            }

            if ($ANTIPASSBACK && $activo) respon(4, 'ANTIPASSBACK_ENTRADA', $nombre);
        } else { // SALIDA sin estar adentro
            if ($ANTIPASSBACK) respon(4, 'ANTIPASSBACK_SALIDA', $nombre);
        }
    }

    // Permitido
    if ($prueba) respon(1, 'OK', $nombre);

    $fecha = date('Y-m-d');
    $hora  = date('H:i:s');
    $bdd->beginTransaction();
    try {
        if ($tipo === 'ENTRADA') {
            if (!$activo) {
                $ins = $bdd->prepare("INSERT INTO activos (nombre, identificacion, iddpto, visitante) VALUES (?, ?, ?, 'SI')");
                $ins->execute(array($nombre, $identificacion, $iddpto));
            }
        } else {
            $del = $bdd->prepare("DELETE FROM activos WHERE identificacion = ?");
            $del->execute(array($identificacion));
        }

        $insIng = $bdd->prepare("INSERT INTO ingresos (nombre, identificacion, fecha, hora, iddpto, punto, tipo, tipoautorizado)
                                 VALUES (?, ?, ?, ?, ?, ?, ?, ?)");
        $insIng->execute(array($nombre, $identificacion, $fecha, $hora, $iddpto, $punto, $tipo, $tipoAut));

        $qd = $bdd->prepare("SELECT id FROM ingresosdiarios WHERE identificacion = ? AND fecha = ? LIMIT 1");
        $qd->execute(array($identificacion, $fecha));
        if (!$qd->fetch(PDO::FETCH_ASSOC)) {
            $insDia = $bdd->prepare("INSERT INTO ingresosdiarios (nombre, identificacion, fecha, iddpto, tipo) VALUES (?, ?, ?, ?, ?)");
            $insDia->execute(array($nombre, $identificacion, $fecha, $iddpto, $tipoAut));
        }
        $bdd->commit();
    } catch (Exception $e) {
        if ($bdd->inTransaction()) $bdd->rollBack();
        error_log('validar_acceso: ' . $e->getMessage());
        respon(2, 'ERROR_SERVIDOR', $nombre);
    }

    respon(1, 'OK', $nombre);

} catch (Exception $e) {
    error_log('validar_acceso: ' . $e->getMessage());
    respon(2, 'ERROR_SERVIDOR');
}
