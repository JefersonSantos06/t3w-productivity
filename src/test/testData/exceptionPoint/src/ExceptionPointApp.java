public class ExceptionPointApp {

    public static void main(String[] args) throws InterruptedException {
        // Thread virtual antes: depois do ponto de exceção a main fica interrompida e o join() falharia.
        Thread.ofVirtual().start(() -> run(3)).join();
        run(1);
        run(2);
    }

    static void run(int i) {
        try {
            work(i);
            System.out.println("ok " + i);
        } catch (Exception e) {
            System.out.println("capturada " + i + ": " + e + " interrompida=" + Thread.currentThread().isInterrupted());
        }
    }

    static void work(int i) {
        System.out.println("trabalhando " + i); // ponto de exceção
    }
}
