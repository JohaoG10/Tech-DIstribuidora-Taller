"""Prueba del JAR real; no reemplaza la demostración entre dos cuentas de Drive."""
import csv
import hashlib
import json
import subprocess
import tempfile
import time
from pathlib import Path

PROJECT = Path(__file__).resolve().parents[1]
JAR = PROJECT / 'target/techdistribuidora-1.0.0.jar'
results = []

def check(name, condition):
    results.append({'prueba': name, 'resultado': 'APROBADA' if condition else 'FALLIDA'})
    print(results[-1]['resultado'] + ': ' + name, flush=True)
    assert condition, name

def wait_until(predicate, timeout=35):
    until = time.monotonic() + timeout
    while time.monotonic() < until:
        if predicate(): return True
        time.sleep(.25)
    return False

def rows(path):
    if not path.exists(): return []
    with path.open(encoding='utf-8', newline='') as stream: return list(csv.DictReader(stream))

def digest(path): return hashlib.sha256(path.read_bytes()).hexdigest()

def main():
    assert JAR.exists(), 'Compila primero con Maven'
    with tempfile.TemporaryDirectory(prefix='techdistribuidora-') as directory:
        base = Path(directory)
        input_dir, output_dir, logs = [base / p for p in ('input', 'output', 'logs')]
        for folder in (input_dir, output_dir, logs): folder.mkdir()
        console = (base / 'camel-console.log').open('w', encoding='utf-8')
        def start(validate=False):
            return subprocess.Popen(['java', '-jar', str(JAR), f'--input={input_dir}',
                f'--output={output_dir}', f'--logs={logs}', f'--validate={str(validate).lower()}'],
                stdout=console, stderr=subprocess.STDOUT)
        def stop(proc):
            proc.terminate()
            try: proc.wait(timeout=20)
            except subprocess.TimeoutExpired: proc.kill(); proc.wait(timeout=10)
        proc = start()
        try:
            time.sleep(4)
            check('Camel arranca y permanece activo', proc.poll() is None)
            for filename in ('pedidos_001.csv', 'no_procesar.txt'):
                (input_dir / filename).write_bytes((PROJECT / 'ejemplos' / filename).read_bytes())
            check('Detecta un CSV colocado después del arranque', wait_until(lambda: (output_dir / 'pedidos_001.csv').exists()))
            check('Conserva el CSV original en input', (input_dir / 'pedidos_001.csv').exists())
            check('SHA-256 de entrada y salida coincide', digest(input_dir / 'pedidos_001.csv') == digest(output_dir / 'pedidos_001.csv'))
            check('Registra copia con archivo y estado', wait_until(lambda: any(r['archivo'] == 'pedidos_001.csv' and r['estado'] == 'COPIADO' for r in rows(logs / 'transferencias.csv'))))
            time.sleep(5)
            check('Excluye TXT y conserva el archivo de control', not (output_dir / 'no_procesar.txt').exists() and (input_dir / 'no_procesar.txt').exists())
            check('No repite el evento en sondeos sucesivos', len(rows(logs / 'transferencias.csv')) == 1)
        finally: stop(proc)
        proc = start(validate=True)
        try:
            time.sleep(7)
            check('Historial persiste tras reiniciar Camel', proc.poll() is None and len(rows(logs / 'transferencias.csv')) == 1)
            (input_dir / 'pedidos_invalidos.csv').write_bytes((PROJECT / 'ejemplos/pedidos_invalidos.csv').read_bytes())
            check('CSV inválido genera ERROR_VALIDACION', wait_until(lambda: any(r['archivo'] == 'pedidos_invalidos.csv' and r['estado'] == 'ERROR_VALIDACION' for r in rows(logs / 'errores.csv'))))
            check('CSV inválido no se copia y conserva original', not (output_dir / 'pedidos_invalidos.csv').exists() and (input_dir / 'pedidos_invalidos.csv').exists())
            (input_dir / 'pedidos_002.CSV').write_bytes((PROJECT / 'ejemplos/pedidos_002.csv').read_bytes())
            check('Acepta CSV válido y extensión en mayúsculas', wait_until(lambda: (output_dir / 'pedidos_002.CSV').exists()))
            time.sleep(1)
            (input_dir / 'pedidos_invalidos.csv').write_bytes((PROJECT / 'ejemplos/pedidos_002.csv').read_bytes())
            check('Una versión corregida del archivo se procesa', wait_until(lambda: (output_dir / 'pedidos_invalidos.csv').exists()))
            check('Copia corregida mantiene contenido idéntico', digest(input_dir / 'pedidos_invalidos.csv') == digest(output_dir / 'pedidos_invalidos.csv'))
        finally:
            stop(proc)
            console.close()
            evidence = PROJECT / 'evidencias'
            evidence.mkdir(exist_ok=True)
            for filename in ('transferencias.csv', 'errores.csv', 'processed-files.dat'):
                if (logs / filename).exists():
                    (evidence / ('local_' + filename)).write_bytes((logs / filename).read_bytes())
            (evidence / 'local_camel-console.log').write_bytes((base / 'camel-console.log').read_bytes())
            data = {'entorno': 'Prueba local con JAR Apache Camel real; no ejecución en Drive',
                'fecha': time.strftime('%Y-%m-%d %H:%M:%S %z'), 'resultados': results}
            (evidence / 'pruebas_locales.json').write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding='utf-8')
            (evidence / 'pruebas_locales.txt').write_text(data['entorno'] + '\n' + data['fecha'] + '\n\n' + '\n'.join(r['resultado'] + ': ' + r['prueba'] for r in results) + '\n', encoding='utf-8')

if __name__ == '__main__': main()
