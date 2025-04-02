import java.io.FileWriter;
import java.io.IOException;

public class FixProperties {
    public static void main(String[] args) {
        try {
            FileWriter writer = new FileWriter("gradle.properties");
            writer.write("org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8\n");
            writer.write("org.gradle.java.home=C:\\\\Program Files\\\\Java\\\\jdk-17\n");
            writer.write("android.useAndroidX=true\n");
            writer.write("kotlin.code.style=official\n");
            writer.write("android.nonTransitiveRClass=true\n");
            writer.close();
            System.out.println("Successfully fixed gradle.properties file");
        } catch (IOException e) {
            System.out.println("Error writing to file: " + e.getMessage());
        }
    }
} 