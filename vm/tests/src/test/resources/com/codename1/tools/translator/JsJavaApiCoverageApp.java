import java.lang.reflect.Array;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;

public class JsJavaApiCoverageApp {
    enum Mode {
        ALPHA,
        BETA
    }

    static int result;

    public static void main(String[] args) throws Exception {
        int mask = 0;

        HashMap map = new HashMap();
        map.put("key", "value");
        if ("value".equals(map.get("key"))) {
            mask |= 1;
        }

        Mode mode = Enum.valueOf(Mode.class, "BETA");
        if (mode == Mode.BETA) {
            mask |= 2;
        }

        Object reflectedArray = Array.newInstance(String.class, 2);
        ((String[]) reflectedArray)[0] = "cn1";
        ((String[]) reflectedArray)[1] = "vm";
        if ("[Ljava.lang.String;".equals(reflectedArray.getClass().getName())) {
            mask |= 4;
        }

        if (Class.forName("java.lang.String") == String.class) {
            mask |= 8;
        }

        String formatted = String.format("%s-%d-%c%%", "cn1", Integer.valueOf(7), Character.valueOf('A'));
        if ("cn1-7-A%".equals(formatted)) {
            mask |= 16;
        }
        
        byte[] utf8 = "cn1".getBytes(StandardCharsets.UTF_8);
        if (utf8.length == 3 && utf8[0] == 'c' && utf8[1] == 'n' && utf8[2] == '1') {
            mask |= 256;
        }

        int[] src = new int[] {1, 2, 3};
        int[] dst = new int[3];
        System.arraycopy(src, 0, dst, 0, src.length);
        if (dst[2] == 3) {
            mask |= 32;
        }

        try {
            throw new IllegalStateException("expected");
        } catch (RuntimeException ex) {
            if ("expected".equals(ex.getMessage())) {
                mask |= 64;
            }
        }

        StringBuilder builder = new StringBuilder();
        builder.append('o').append("k");
        if ("ok".equals(builder.toString())) {
            mask |= 128;
        }

        result = mask;
        System.exit(mask);
    }
}
