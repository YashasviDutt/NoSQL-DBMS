import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

public class AverageAgeCalculator {
    private Map<String, Integer> ageMap = new HashMap<>();
    private Map<String, Integer> countMap = new HashMap<>();
    private Map<String, Integer> batchAgeMap = new HashMap<>();
    private Map<String, Integer> batchCountMap = new HashMap<>();

    public void loadData(String filePath) throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader(filePath));
        String line;
        String currentMainKey = null;
        String currentBatch = null;

        while ((line = reader.readLine()) != null) {
            line = line.trim();

            if (line.startsWith("main Key:")) {
                String[] parts = line.split(":", 2);
                if (parts.length == 2) {
                    currentMainKey = parts[1].trim();
                }
            } else if (line.startsWith("sub-Key: batch, Value:") && currentMainKey != null) {
                String[] parts = line.split(":", 3);
                if (parts.length == 3) {
                    currentBatch = parts[2].replace("\"", "").trim();
                }
            } else if (line.startsWith("sub-Key: age, Value:") && currentMainKey != null && currentBatch != null) {
                String[] parts = line.split(":", 3);
                if (parts.length == 3) {
                    try {
                        int age = Integer.parseInt(parts[2].replace("\"", "").trim());

                        // Update the age for the main key
                        ageMap.put(currentMainKey, ageMap.getOrDefault(currentMainKey, 0) + age);
                        countMap.put(currentMainKey, countMap.getOrDefault(currentMainKey, 0) + 1);

                        // Update the batch age and count
                        batchAgeMap.put(currentBatch, batchAgeMap.getOrDefault(currentBatch, 0) + age);
                        batchCountMap.put(currentBatch, batchCountMap.getOrDefault(currentBatch, 0) + 1);
                    } catch (NumberFormatException e) {
                        System.err.println("Invalid age format in line: " + line);
                    }
                }
            }
        }
        reader.close();
    }

    public double calculateAverageAge(String... mainKeys) {
        int totalAge = 0;
        int totalCount = 0;

        if (mainKeys.length == 0) {
            for (String key : ageMap.keySet()) {
                totalAge += ageMap.get(key);
                totalCount += countMap.get(key);
            }
        } else {
            for (String key : mainKeys) {
                if (ageMap.containsKey(key)) {
                    totalAge += ageMap.get(key);
                    totalCount += countMap.get(key);
                }
            }
        }

        return totalCount > 0 ? (double) totalAge / totalCount : 0.0;
    }

    public double calculateBatchAverageAge(String batch) {
        int totalAge = batchAgeMap.getOrDefault(batch, 0);
        int totalCount = batchCountMap.getOrDefault(batch, 0);
        return totalCount > 0 ? (double) totalAge / totalCount : 0.0;
    }

    public static void main(String[] args) {
        AverageAgeCalculator calculator = new AverageAgeCalculator();
        Scanner scanner = new Scanner(System.in);

        try {
            calculator.loadData("dbms_data");

            while (true) {
                System.out.println("Enter query (or type 'exit' to quit):");
                String query = scanner.nextLine().trim();

                if (query.equalsIgnoreCase("exit")) {
                    System.out.println("Exiting program.");
                    break;
                }

                if (query.equalsIgnoreCase("average age main Key:")) {
                    // Calculate average age for all main keys
                    double avgAge = calculator.calculateAverageAge();
                    System.out.println("Average age of all the main keys: " + avgAge);
                } else if (query.startsWith("average age main Key:")) {
                    // Handle specific main keys
                    String[] keys = query.replace("average age main Key:", "").split(",");
                    for (int i = 0; i < keys.length; i++) {
                        keys[i] = keys[i].trim();
                    }
                    double avgAge = calculator.calculateAverageAge(keys);
                    System.out.println("Average age: " + avgAge);
                } else if (query.equalsIgnoreCase("average age group by batch")) {
                    // Group by batch
                    System.out.println("Average age group by batch:");
                    for (Map.Entry<String, Integer> entry : calculator.batchAgeMap.entrySet()) {
                        String batch = entry.getKey();
                        double avgAge = calculator.calculateBatchAverageAge(batch);
                        System.out.println("Batch " + batch + ": " + avgAge);
                    }
                } else if (query.startsWith("average batch age")) {
                    // Handle specific batch average age query
                    String[] parts = query.split("\"");
                    if (parts.length == 2) {
                        String batch = parts[1].trim();
                        double avgAge = calculator.calculateBatchAverageAge(batch);
                        System.out.println("Average age for batch " + batch + ": " + avgAge);
                    } else {
                        System.out.println("Invalid query format for average batch age.");
                    }
                } else {
                    System.out.println("Invalid query format. Please try again.");
                }
            }

        } catch (IOException e) {
            System.err.println("Error reading the file: " + e.getMessage());
        } finally {
            scanner.close();
        }
    }
}
