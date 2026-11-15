<?php
/*
  DIGIACCESS PDA — enlace corto de descarga
  -----------------------------------------
  https://web.agenteia.cc/pda            -> última versión del APK
  https://web.agenteia.cc/pda/?v=1.0.3   -> una versión específica
  https://web.agenteia.cc/pda/?info=1    -> muestra la versión disponible (sin descargar)

  Pide a GitHub (repositorio privado) la versión publicada en Releases y redirige al
  enlace temporal de descarga que entrega GitHub. El token nunca llega al navegador.
*/
require __DIR__ . '/config.php';
if (!defined('GITHUB_API')) define('GITHUB_API', 'https://api.github.com');
header('Cache-Control: no-store');

function github($ruta, $aceptar = 'application/vnd.github+json', &$codigo = null, &$ubicacion = null)
{
	$c = curl_init(GITHUB_API . $ruta);
	curl_setopt_array($c, array(
		CURLOPT_RETURNTRANSFER => true,
		CURLOPT_FOLLOWLOCATION => false,      // queremos la URL temporal, no el archivo
		CURLOPT_HEADER => true,
		CURLOPT_TIMEOUT => 20,
		CURLOPT_HTTPHEADER => array(
			'Authorization: Bearer ' . GITHUB_TOKEN,
			'Accept: ' . $aceptar,
			'X-GitHub-Api-Version: 2022-11-28',
			'User-Agent: digiaccess-pda-descarga',
		),
	));
	$resp = curl_exec($c);
	if ($resp === false) {
		$codigo = 0;
		curl_close($c);
		return null;
	}
	$codigo = curl_getinfo($c, CURLINFO_HTTP_CODE);
	$tamEnc = curl_getinfo($c, CURLINFO_HEADER_SIZE);
	curl_close($c);
	$encabezados = substr($resp, 0, $tamEnc);
	if (preg_match('/^location:\s*(\S+)/mi', $encabezados, $m)) $ubicacion = trim($m[1]);
	return substr($resp, $tamEnc);
}

function error_pagina($mensaje, $codigo = 503)
{
	http_response_code($codigo);
	header('Content-Type: text/html; charset=utf-8');
	echo '<!doctype html><meta name="viewport" content="width=device-width,initial-scale=1">'
		. '<body style="font-family:Inter,Arial,sans-serif;background:#F9FAFB;color:#1F2937;padding:40px 20px;text-align:center">'
		. '<h2 style="margin:0 0 8px">DIGIACCESS PDA</h2><p style="color:#6B7280">' . htmlspecialchars($mensaje) . '</p></body>';
	exit();
}

// 1) Versión: la última o la pedida (?v=1.0.3)
$v = isset($_GET['v']) ? preg_replace('/[^0-9.]/', '', $_GET['v']) : '';
$ruta = $v !== '' ? '/repos/' . GITHUB_REPO . '/releases/tags/v' . $v : '/repos/' . GITHUB_REPO . '/releases/latest';
$cuerpo = github($ruta, 'application/vnd.github+json', $codigo);
if ($codigo === 401) error_pagina('El token de descarga no es válido o venció. Actualícelo en config.php.');
if ($codigo === 404) error_pagina($v !== '' ? 'No existe la versión ' . $v . '.' : 'Aún no hay versiones publicadas.', 404);
if ($codigo !== 200 || !$cuerpo) error_pagina('No se pudo consultar GitHub en este momento. Intente de nuevo.');

$release = json_decode($cuerpo, true);
$asset = null;
foreach ((array)$release['assets'] as $a) {
	if ($a['name'] === NOMBRE_APK || substr($a['name'], -4) === '.apk') { $asset = $a; break; }
}
if (!$asset) error_pagina('La versión ' . $release['tag_name'] . ' no tiene APK adjunto.', 404);

// 2) Solo información
if (isset($_GET['info'])) {
	header('Content-Type: application/json; charset=utf-8');
	echo json_encode(array(
		'version' => ltrim($release['tag_name'], 'v'),
		'publicada' => $release['published_at'],
		'tamano_mb' => round($asset['size'] / 1048576, 1),
		'descarga' => 'https://' . $_SERVER['HTTP_HOST'] . strtok($_SERVER['REQUEST_URI'], '?'),
	), JSON_UNESCAPED_SLASHES | JSON_UNESCAPED_UNICODE);
	exit();
}

// 3) Enlace temporal de GitHub (válido unos minutos) y redirección
github('/repos/' . GITHUB_REPO . '/releases/assets/' . $asset['id'], 'application/octet-stream', $codigo, $ubicacion);
if (($codigo === 302 || $codigo === 301) && $ubicacion) {
	header('Location: ' . $ubicacion, true, 302);
	exit();
}
error_pagina('No se pudo generar el enlace de descarga. Intente de nuevo.');
