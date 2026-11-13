import java.io.*;

public class program3 {
    public static void main(String[] args) {
        int wordCount = 0;

        try (BufferedReader reader = new BufferedReader(new FileReader("input.txt"));
             BufferedWriter writer = new BufferedWriter(new FileWriter("output.txt"))) {

            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) {
                    String[] words = line.split("\\s+");
                    wordCount += words.length;
                }
            }

            writer.write("Total words: " + wordCount);
            System.out.println("Done. Total words: " + wordCount);

        } catch (IOException e) {
            System.out.println("Error: " + e.getMessage());
        }
    }
}