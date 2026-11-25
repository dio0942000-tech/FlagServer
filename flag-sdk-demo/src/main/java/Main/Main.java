package Main;
import com.dio.flagserver.client.FlagClient;

public class Main {
    public static void main(String[] args) throws Exception {
        FlagClient client = new FlagClient("http://localhost:8080");

        boolean enabled = client.evaluate("new-homepage", 1001L);

        System.out.println("是否开启功能：" + enabled);
    }
}