# Angela Maria Gonzalez 


# Ejecución del programa SleepingMonitor en Java

Este proyecto utiliza la estructura clásica de carpetas:

```
Tarea Hilos/
 ├── src/
 │    └── SleepingMonitor.java
 └── bin/
```

## Compilación

Para compilar el programa y guardar los archivos `.class` dentro de la carpeta `bin`, utiliza el siguiente comando:

```powershell
javac -d bin src\SleepingMonitor.java
```

- `javac` → es el compilador de Java.  
- `-d bin` → indica que los archivos compilados deben ir a la carpeta `bin`.  
- `src\SleepingMonitor.java` → es la ruta del archivo fuente a compilar.

---

## Ejecución

Una vez compilado, ejecuta el programa con:

```powershell
java -cp bin SleepingMonitor
```

- `java` → ejecuta la máquina virtual de Java.  
- `-cp bin` → le dice a Java dónde buscar los archivos compilados.  
- `SleepingMonitor` → es el nombre de la clase que contiene el método `main`.

---

