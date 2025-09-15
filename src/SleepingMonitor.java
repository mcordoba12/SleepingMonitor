import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Random;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

public class SleepingMonitor {

    // ====== Parámetros de la simulación ======
    static final int NUM_ESTUDIANTES = 8;  // cantidad de estudiantes (hilos)
    static final int SILLAS_CORREDOR = 3;  // sillas disponibles para espera
    static final int VISITAS_POR_ESTUDIANTE = 3; // veces que cada estudiante busca ayuda
    static final int PROG_MS_MIN = 400;    // tiempo mínimo programando
    static final int PROG_MS_MAX = 1200;   // tiempo máximo programando
    static final int AYUDA_MS_MIN = 300;   // tiempo mínimo de ayuda
    static final int AYUDA_MS_MAX = 900;   // tiempo máximo de ayuda

    // ====== Utilidad para tiempos aleatorios ======
    static int rndInRange(Random r, int a, int b) {
        return a + r.nextInt(Math.max(1, b - a + 1));
    }

    // ====== Estructura de turno del estudiante (para coordinar ayuda individual) ======
    static class Ticket {
        final int studentId;
        final Semaphore done = new Semaphore(0); // el estudiante espera aquí hasta que el monitor termine

        Ticket(int studentId) {
            this.studentId = studentId;
        }
    }

    // ====== Oficina coordinadora: gestiona cola FIFO, semáforos y política ======
    static class MonitorOffice {
        private final int chairsCapacity;
        private final Deque<Ticket> queue = new ArrayDeque<>();
        private final ReentrantLock queueLock = new ReentrantLock(true); // justo/FIFO en contención
        private final Semaphore studentsWaiting = new Semaphore(0, true); // cuenta estudiantes esperando (despierta al monitor)
        private final AtomicInteger ayudasRestantes; // total de atenciones a realizar en toda la simulación
        private volatile boolean monitorActivo = true;

        MonitorOffice(int chairsCapacity, int totalAyudas) {
            this.chairsCapacity = chairsCapacity;
            this.ayudasRestantes = new AtomicInteger(totalAyudas);
        }

        boolean tryGetHelp(int studentId, Random r) throws InterruptedException {
            Ticket t = new Ticket(studentId);

            // Intentar sentarse en el corredor (si hay silla)
            queueLock.lock();
            try {
                if (queue.size() >= chairsCapacity) {
                    log("Estudiante %d: corredor lleno (%d/%d). Se va a programar y volverá.",
                            studentId, queue.size(), chairsCapacity);
                    return false; // no alcanzó silla, se va a programar
                }
                boolean estabaVacio = queue.isEmpty();
                queue.addLast(t);
                log("Estudiante %d: se sienta en el corredor. En cola ahora: %d.",
                        studentId, queue.size());

                // Si estaba vacío, este estudiante "despierta" al monitor
                if (estabaVacio) {
                    studentsWaiting.release();
                    log("Estudiante %d: despierta al monitor (o lo notifica).", studentId);
                }
            } finally {
                queueLock.unlock();
            }

            // Esperar a que el monitor termine de ayudarlo
            t.done.acquire();
            // Simulación completada: ya recibió ayuda
            return true;
        }

        void atenderSiguiente(Random r) throws InterruptedException {
            // Si no hay nadie esperando, el monitor "duerme" hasta que haya
            log("Monitor: no hay estudiantes en la oficina. Zzz...");
            studentsWaiting.acquire(); // bloqueante hasta que alguien llegue

            // Alguien llegó: tomar al siguiente en orden FIFO
            Ticket t;
            queueLock.lock();
            try {
                t = queue.pollFirst();
                int quedan = queue.size();
                log("Monitor: llama al estudiante %d (quedan %d en el corredor).",
                        t.studentId, quedan);
            } finally {
                queueLock.unlock();
            }

            // Atender al estudiante
            int ayudaMs = rndInRange(r, AYUDA_MS_MIN, AYUDA_MS_MAX);
            log("Monitor: ayudando a estudiante %d por %d ms.", t.studentId, ayudaMs);
            Thread.sleep(ayudaMs);

            // Entregar la "señal" de que terminó su ayuda
            t.done.release();

            // Una ayuda menos pendiente en la simulación
            int left = ayudasRestantes.decrementAndGet();

            // Si aún quedan estudiantes esperando luego de atender a uno, volver a señalizarse
            // para seguir atendiendo en ráfaga sin dormirse entre cada uno.
            // (Esto emula revisar el corredor y seguir con el siguiente en orden de llegada)
            queueLock.lock();
            try {
                if (!queue.isEmpty()) {
                    // Hay más en la cola: auto-despierta para la siguiente atención
                    studentsWaiting.release();
                } else {
                    log("Monitor: corredor vacío tras atender a %d. Puede volver a dormirse.", t.studentId);
                }
            } finally {
                queueLock.unlock();
            }

            // Criterio de parada (se usa desde el hilo del monitor)
            if (left <= 0) {
                monitorActivo = false;
            }
        }

        boolean monitorSigueActivo() {
            return monitorActivo;
        }
    }

    // ====== Hilo del monitor ======
    static class MonitorRunnable implements Runnable {
        private final MonitorOffice office;
        private final Random r = new Random();

        MonitorRunnable(MonitorOffice office) {
            this.office = office;
        }

        @Override
        public void run() {
            try {
                while (office.monitorSigueActivo()) {
                    office.atenderSiguiente(r);
                }
                log("Monitor: terminó la jornada. ¡Listo!");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log("Monitor: interrumpido.");
            }
        }
    }

    // ====== Hilo del estudiante ======
    static class StudentRunnable implements Runnable {
        private final int id;
        private final MonitorOffice office;
        private final Random r = new Random();

        StudentRunnable(int id, MonitorOffice office) {
            this.id = id;
            this.office = office;
        }

        @Override
        public void run() {
            int recibidas = 0;
            try {
                while (recibidas < VISITAS_POR_ESTUDIANTE) {
                    // Programar en sala de cómputo
                    int progMs = rndInRange(r, PROG_MS_MIN, PROG_MS_MAX);
                    log("Estudiante %d: programando %d ms.", id, progMs);
                    Thread.sleep(progMs);

                    // Buscar ayuda
                    boolean atendido = office.tryGetHelp(id, r);
                    if (atendido) {
                        recibidas++;
                        log("Estudiante %d: recibió ayuda (%d/%d).", id, recibidas, VISITAS_POR_ESTUDIANTE);
                    } else {
                        // No alcanzó silla; reintenta luego de programar otro rato
                        int msExtra = rndInRange(r, 200, 600);
                        log("Estudiante %d: reintentará luego de %d ms.", id, msExtra);
                        Thread.sleep(msExtra);
                    }
                }
                log("Estudiante %d: COMPLETÓ sus consultas.", id);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log("Estudiante %d: interrumpido.", id);
            }
        }
    }

    // ====== Utilidad de logging con timestamp ======
    static synchronized void log(String fmt, Object... args) {
        String msg = String.format(fmt, args);
        long time = System.currentTimeMillis() % 100000;
        System.out.printf("[%6d ms] %s%n", time, msg);
    }

    // ====== main ======
    public static void main(String[] args) throws Exception {
        int totalAyudas = NUM_ESTUDIANTES * VISITAS_POR_ESTUDIANTE;
        MonitorOffice office = new MonitorOffice(SILLAS_CORREDOR, totalAyudas);

        Thread monitor = new Thread(new MonitorRunnable(office), "Monitor");
        monitor.start();

        Thread[] estudiantes = new Thread[NUM_ESTUDIANTES];
        for (int i = 0; i < NUM_ESTUDIANTES; i++) {
            estudiantes[i] = new Thread(new StudentRunnable(i + 1, office), "Est-" + (i + 1));
            estudiantes[i].start();
        }

        // Esperar a que todos los estudiantes terminen sus consultas
        for (Thread t : estudiantes) t.join();

        // Por si el monitor sigue esperando (no debería), interrumpirlo limpiamente
        if (monitor.isAlive()) {
            monitor.join(2000);
            if (monitor.isAlive()) monitor.interrupt();
        }

        log("Simulación finalizada.");
    }
}
