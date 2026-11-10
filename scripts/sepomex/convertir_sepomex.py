"""Convierte el catálogo de SEPOMEX (CPdescarga.txt) al CSV que carga cargar_sepomex.sql.

Uso:
    python -I convertir_sepomex.py <ruta a CPdescarga.txt o CPdescargatxt.zip> <salida.csv>

El archivo se descarga del portal de Correos de México (CodigoPostal_Exportar.aspx, formato TXT, todos los estados).
No se guarda en el repositorio: su licencia prohíbe distribuirlo a terceros.
"""
import csv
import io
import sys
import zipfile

COLUMNAS = ["codigo_postal", "asentamiento", "tipo_asentamiento", "municipio", "estado", "ciudad", "zona"]
LIMITES = {"asentamiento": 150, "tipo_asentamiento": 60, "municipio": 100, "estado": 100, "ciudad": 100, "zona": 20}


def leer_lineas(ruta):
    if ruta.lower().endswith(".zip"):
        with zipfile.ZipFile(ruta) as z:
            nombre = next(n for n in z.namelist() if n.lower().endswith(".txt"))
            datos = z.read(nombre)
    else:
        with open(ruta, "rb") as f:
            datos = f.read()
    # El archivo oficial viene en ISO-8859-1 con fin de línea CRLF.
    return io.StringIO(datos.decode("latin-1")).read().splitlines()


def main(entrada, salida):
    lineas = leer_lineas(entrada)
    encabezado_idx = next(i for i, l in enumerate(lineas) if l.startswith("d_codigo|"))
    encabezado = lineas[encabezado_idx].split("|")
    idx = {nombre: i for i, nombre in enumerate(encabezado)}

    vistos = set()
    filas = 0
    with open(salida, "w", encoding="utf-8", newline="") as f:
        w = csv.writer(f)
        w.writerow(COLUMNAS)
        for linea in lineas[encabezado_idx + 1:]:
            if not linea.strip():
                continue
            c = linea.split("|")
            fila = {
                "codigo_postal": c[idx["d_codigo"]].strip().zfill(5),
                "asentamiento": c[idx["d_asenta"]].strip(),
                "tipo_asentamiento": c[idx["d_tipo_asenta"]].strip(),
                "municipio": c[idx["D_mnpio"]].strip(),
                "estado": c[idx["d_estado"]].strip(),
                "ciudad": c[idx["d_ciudad"]].strip(),
                "zona": c[idx["d_zona"]].strip(),
            }
            if len(fila["codigo_postal"]) != 5 or not fila["codigo_postal"].isdigit():
                raise ValueError(f"Código postal inválido: {linea!r}")
            for campo, maximo in LIMITES.items():
                if len(fila[campo]) > maximo:
                    raise ValueError(f"{campo} excede {maximo} caracteres: {fila[campo]!r}")
            clave = (fila["codigo_postal"], fila["asentamiento"].lower(), fila["municipio"], fila["estado"])
            if clave in vistos:  # el catálogo repite algunos asentamientos (mismo nombre, distinto tipo)
                continue
            vistos.add(clave)
            w.writerow([fila[col] for col in COLUMNAS])
            filas += 1
    print(f"{filas} asentamientos escritos en {salida}")


if __name__ == "__main__":
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    main(sys.argv[1], sys.argv[2])
