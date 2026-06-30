package samples.java7;

import java.io.BufferedReader;
import java.io.StringReader;

public class TryWithResources {

    public String readFirstLine(String data) throws Exception {
        try (BufferedReader br = new BufferedReader(new StringReader(data))) {
            return br.readLine();
        }
    }
}
