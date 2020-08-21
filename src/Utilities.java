import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

public final class Utilities {

  public static String inputStreamToString(InputStream inputStream) {
    String generatorSourceString;
    try {

      final int bufferSize = 1024;
      final char[] buffer = new char[bufferSize];
      final StringBuilder out = new StringBuilder();
      Reader in = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
      int charsRead;
      while((charsRead = in.read(buffer, 0, buffer.length)) > 0) {
        out.append(buffer, 0, charsRead);
      }
      generatorSourceString = out.toString();
    } catch (IOException e) {
      generatorSourceString = null;
      e.printStackTrace();
    }
    return generatorSourceString;
  }

}
