import java.util.*;
import java.io.*;
import java.util.regex.*;

class Data_read {
    static final String FILE_NAME = "dbms_data";

    void data_read() {
        try (BufferedReader B_read = new BufferedReader(new FileReader(FILE_NAME))) {
            String str;
            System.out.println("Reading data from file: " + FILE_NAME);

            while ((str = B_read.readLine()) != null) {
                System.out.println(str);
            }
        } 
        catch (IOException e) {
            System.out.println(e);
        }
    }
}

// HashMap class for formatting 
class Mapping_task {
    static final String FILE_NAME = "dbms_data";
    private HashMap<String, HashMap<String, String>> Main_mapping = new HashMap<>();

    Mapping_task() {
        // Load data from the file into Main_mapping
        loadExistingData();
    }

    void loadExistingData() {
        try (BufferedReader reader = new BufferedReader(new FileReader(FILE_NAME))) {
            String line;
            String currentMainKey = null;

            while ((line = reader.readLine()) != null) {
                if (line.startsWith("main Key:")) {
                    currentMainKey = line.substring(line.indexOf(":") + 1).trim();
                    Main_mapping.put(currentMainKey, new HashMap<>());
                } 
                else if (line.startsWith("    sub-Key:") && currentMainKey != null) {
                    String[] parts = line.split(",");
                    String subKey = parts[0].split(":")[1].trim();
                    String value = parts[1].split(":")[1].trim();
                    Main_mapping.get(currentMainKey).put(subKey, value);
                }
            }
            System.out.println("Loaded existing data from file.");
        } 
        catch (FileNotFoundException e) {
            System.out.println("File not found. Starting with an empty database.");
        } 
        catch (IOException e) {
            System.out.println("An error occurred while reading the file.");
        }
    }

    void deleteMainKey(String mainKey) {
        if (Main_mapping.containsKey(mainKey)) {
            Main_mapping.remove(mainKey);
            System.out.println("Main key '" + mainKey + "' deleted.");
        } 
        else {
            System.out.println("Main key '" + mainKey + "' does not exist.");
        }
    }

    void deleteSubKey(String mainKey, List<String> subKeys) {
        if (Main_mapping.containsKey(mainKey)) {
            HashMap<String, String> subMap = Main_mapping.get(mainKey);
            for (String subKey : subKeys) {
                if (subMap.containsKey(subKey)) {
                    subMap.remove(subKey);
                    System.out.println("Sub-key '" + subKey + "' deleted from main key '" + mainKey + "'.");
                } else {
                    System.out.println("Sub-key '" + subKey + "' does not exist under main key '" + mainKey + "'.");
                }
            }
            if (subMap.isEmpty()) {
                Main_mapping.remove(mainKey);
                System.out.println("Main key '" + mainKey + "' deleted as it has no sub-keys left.");
            }
        } else {
            System.out.println("Main key '" + mainKey + "' does not exist.");
        }
    }

    String cleanStoredValue(String value) {
        return value == null ? "" : value.replace("\"", "").replace("{", "").replace("}", "").trim();
    }

    Integer getAgeForMainKey(String mainKey) {
        HashMap<String, String> subMap = Main_mapping.get(mainKey);
        if (subMap == null || !subMap.containsKey("age")) {
            return null;
        }

        try {
            return Integer.parseInt(cleanStoredValue(subMap.get("age")));
        }
        catch (NumberFormatException e) {
            System.out.println("Invalid age value for main key '" + mainKey + "': " + subMap.get("age"));
            return null;
        }
    }

    String getBatchForMainKey(String mainKey) {
        HashMap<String, String> subMap = Main_mapping.get(mainKey);
        if (subMap == null || !subMap.containsKey("batch")) {
            return null;
        }
        return cleanStoredValue(subMap.get("batch"));
    }

    double calculateAverageAge(List<String> mainKeys) {
        int totalAge = 0;
        int totalCount = 0;

        Collection<String> keysToRead = mainKeys.isEmpty() ? Main_mapping.keySet() : mainKeys;
        for (String mainKey : keysToRead) {
            Integer age = getAgeForMainKey(mainKey);
            if (age != null) {
                totalAge += age;
                totalCount++;
            }
        }

        return totalCount > 0 ? (double) totalAge / totalCount : 0.0;
    }

    double calculateBatchAverageAge(String batch) {
        int totalAge = 0;
        int totalCount = 0;

        for (String mainKey : Main_mapping.keySet()) {
            String storedBatch = getBatchForMainKey(mainKey);
            Integer age = getAgeForMainKey(mainKey);

            if (storedBatch != null && storedBatch.equals(batch) && age != null) {
                totalAge += age;
                totalCount++;
            }
        }

        return totalCount > 0 ? (double) totalAge / totalCount : 0.0;
    }

    void printAverageAgeGroupByBatch() {
        HashSet<String> batches = new HashSet<>();
        for (String mainKey : Main_mapping.keySet()) {
            String batch = getBatchForMainKey(mainKey);
            if (batch != null && !batch.isEmpty()) {
                batches.add(batch);
            }
        }

        System.out.println("Average age group by batch:");
        for (String batch : batches) {
            System.out.println("Batch " + batch + ": " + calculateBatchAverageAge(batch));
        }
    }

    void processAverageQuery(String query) {
        String lowerQuery = query.toLowerCase();

        if (lowerQuery.equals("average age main key:")) {
            System.out.println("Average age of all the main keys: " + calculateAverageAge(new ArrayList<>()));
        }
        else if (lowerQuery.startsWith("average age main key:")) {
            String keysPart = query.substring(query.indexOf(":") + 1).trim();
            String[] keys = keysPart.split(",");
            List<String> mainKeys = new ArrayList<>();

            for (String key : keys) {
                String mainKey = key.trim();
                if (!mainKey.isEmpty()) {
                    mainKeys.add(mainKey);
                }
            }

            System.out.println("Average age: " + calculateAverageAge(mainKeys));
        }
        else if (lowerQuery.equals("average age group by batch")) {
            printAverageAgeGroupByBatch();
        }
        else if (lowerQuery.startsWith("average batch age")) {
            String[] parts = query.split("\"");
            if (parts.length >= 2) {
                String batch = parts[1].trim();
                System.out.println("Average age for batch " + batch + ": " + calculateBatchAverageAge(batch));
            }
            else {
                System.out.println("Invalid query format for average batch age.");
            }
        }
        else {
            System.out.println("Invalid average query.");
        }
    }

    String normalizeSearchLabel(String label) {
        return cleanStoredValue(label).toLowerCase().replace(" ", "");
    }

    String getSubValue(HashMap<String, String> subMap, String subKey) {
        if (subMap.containsKey(subKey)) {
            return subMap.get(subKey);
        }

        for (Map.Entry<String, String> entry : subMap.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(subKey)) {
                return entry.getValue();
            }
        }

        return null;
    }

    void printMainKeyRecord(String mainKey) {
        HashMap<String, String> subMap = Main_mapping.get(mainKey);
        System.out.println("Main Key: " + mainKey);

        if (subMap == null || subMap.isEmpty()) {
            return;
        }

        for (Map.Entry<String, String> subEntry : subMap.entrySet()) {
            System.out.println("    sub-Key: " + subEntry.getKey() + ", Value: " + cleanStoredValue(subEntry.getValue()));
        }
    }

    void printAllRecords() {
        if (Main_mapping.isEmpty()) {
            System.out.println("Database is empty.");
            return;
        }

        for (String mainKey : Main_mapping.keySet()) {
            printMainKeyRecord(mainKey);
        }
    }

    String extractMainKeyFromSearch(String query) {
        String searchText = query.substring("search".length()).trim();
        searchText = cleanStoredValue(searchText);

        int colonIndex = searchText.indexOf(":");
        if (colonIndex >= 0) {
            searchText = searchText.substring(colonIndex + 1).trim();
        }

        return searchText;
    }

    Map<String, String> parseSearchConditions(String query) {
        LinkedHashMap<String, String> conditions = new LinkedHashMap<>();
        Matcher matcher = Pattern.compile("\\{([^}]*)\\}").matcher(query);

        while (matcher.find()) {
            String conditionText = matcher.group(1);
            String subKey = null;
            String value = null;
            String[] pieces = conditionText.split(",");

            for (String piece : pieces) {
                int colonIndex = piece.indexOf(":");
                if (colonIndex < 0) {
                    continue;
                }

                String label = normalizeSearchLabel(piece.substring(0, colonIndex));
                String conditionValue = cleanStoredValue(piece.substring(colonIndex + 1));

                if (label.equals("sub-key") || label.equals("subkey")) {
                    subKey = conditionValue;
                }
                else if (label.equals("value")) {
                    value = conditionValue;
                }
            }

            if (subKey == null || value == null) {
                System.out.println("Invalid search condition: " + conditionText);
                return null;
            }

            conditions.put(subKey, value);
        }

        if (conditions.isEmpty()) {
            System.out.println("Malformed search where query.");
            return null;
        }

        return conditions;
    }

    boolean matchesSearchConditions(HashMap<String, String> subMap, Map<String, String> conditions) {
        for (Map.Entry<String, String> condition : conditions.entrySet()) {
            String actualValue = getSubValue(subMap, condition.getKey());
            String expectedValue = condition.getValue();

            if (actualValue == null || !cleanStoredValue(actualValue).equalsIgnoreCase(expectedValue)) {
                return false;
            }
        }

        return true;
    }

    void processSearchQuery(String query) {
        String lowerQuery = query.toLowerCase();

        if (lowerQuery.equals("search") || lowerQuery.equals("search:")) {
            printAllRecords();
            return;
        }

        if (lowerQuery.startsWith("search where")) {
            Map<String, String> conditions = parseSearchConditions(query);
            if (conditions == null) {
                return;
            }

            boolean listMainKeyOnly = lowerQuery.contains("list mainkey");
            boolean found = false;

            for (Map.Entry<String, HashMap<String, String>> entry : Main_mapping.entrySet()) {
                if (matchesSearchConditions(entry.getValue(), conditions)) {
                    if (listMainKeyOnly) {
                        System.out.println("Main Key: " + entry.getKey());
                    }
                    else {
                        printMainKeyRecord(entry.getKey());
                    }
                    found = true;
                }
            }

            if (!found) {
                System.out.println("No main key found matching the given condition.");
            }
            return;
        }

        String mainKey = extractMainKeyFromSearch(query);
        if (mainKey.isEmpty()) {
            System.out.println("Invalid search query.");
        }
        else if (Main_mapping.containsKey(mainKey)) {
            printMainKeyRecord(mainKey);
        }
        else {
            System.out.println("Main key '" + mainKey + "' does not exist.");
        }
    }

    void processQuery(String query) {
        query = query.trim();
        if (query.toLowerCase().startsWith("delete")) {
            if (query.toLowerCase().startsWith("delete from")) {
                String mainKeyPart = query.substring(query.indexOf(":") + 1, query.indexOf("{")).trim().replace("\"", "").replace("delete from ", "").trim();
                String valuesPart = query.substring(query.indexOf("{") + 1, query.indexOf("}")).trim();
                String[] subKeys = valuesPart.split(",");

                List<String> subKeysList = new ArrayList<>();
                for (String key : subKeys) {
                    String[] keyParts = key.split(":");
                    if (keyParts.length > 1) {
                        subKeysList.add(keyParts[1].trim());
                    } 
                    else {
                        System.out.println("Invalid sub-key format: " + key);
                    }
                }

                deleteSubKey(mainKeyPart, subKeysList);
            } 
            else {
                String mainKey = query.substring(query.indexOf(":") + 1).trim().replace("\"", "");
                deleteMainKey(mainKey);
            }
        } 
        else if (query.toLowerCase().startsWith("create")) {
            String mainKey = query.substring(query.indexOf(":") + 1).trim().replace("\"", "");
            Main_mapping.putIfAbsent(mainKey, new HashMap<>());
            System.out.println("Main key '" + mainKey + "' created.");
        } 

        else if (query.toLowerCase().startsWith("average ")) {
            processAverageQuery(query);
            return;
        }

        else if (query.toLowerCase().startsWith("search")) {
            processSearchQuery(query);
            return;
        }

        
        else if (query.toLowerCase().startsWith("insert into")) {
            String[] parts = query.split("values", 2);
            if (parts.length < 2) {
                System.out.println("Invalid query format for insert.");
                return;
            }
            String mainKeyPart = parts[0].substring(query.indexOf(":") + 1).trim().replace("\"", "").replace("insert into ", "").trim();
            String valuesPart = parts[1].trim();

            if (!Main_mapping.containsKey(mainKeyPart)) {
                System.out.println("Main key '" + mainKeyPart + "' does not exist. Use CREATE first.");
                return;
            }

            valuesPart = valuesPart.substring(1, valuesPart.length() - 1);  
            String[] keyValuePairs = valuesPart.split("},\\s*\\{");  

            for (String pair : keyValuePairs) {
                pair = pair.replace("{", "").replace("}", "").trim();
                String[] keyValue = pair.split(",");
                if (keyValue.length != 2) {
                    System.out.println("Invalid key-value format: " + pair);
                    continue;
                }
                String[] subKeyParts = keyValue[0].split(":");
                String[] valueParts = keyValue[1].split(":");
                if (subKeyParts.length < 2 || valueParts.length < 2) {
                    System.out.println("Invalid key or value format in: " + pair);
                    continue;
                }
                String subKey = subKeyParts[1].trim();
                String value = valueParts[1].trim();
                Main_mapping.get(mainKeyPart).put(subKey, value);
            }
            System.out.println("\nInserted values into '" + mainKeyPart + "'.");
        }
        else if (query.toLowerCase().startsWith("update ")) {
            if (query.contains("to")) {
                // Update main key
                String[] parts = query.split("to", 2);
                if (parts.length < 2) {
                    System.out.println("Invalid update format for main key.");
                    return;
                }
                String oldMainKey = parts[0].substring(query.indexOf(":") + 1).trim().replace("\"", "").replace("update ", "").trim();
                String newMainKey = parts[1].trim().replace("\"", "");
                
                if (Main_mapping.containsKey(oldMainKey)) {
                    if (!Main_mapping.containsKey(newMainKey)) {
                        Main_mapping.put(newMainKey, Main_mapping.remove(oldMainKey));
                        System.out.println("Main key '" + oldMainKey + "' updated to '" + newMainKey + "'.");
                    } 
                    else {
                        System.out.println("Main key '" + newMainKey + "' already exists. Merging entries.");
                        Main_mapping.get(newMainKey).putAll(Main_mapping.remove(oldMainKey));
                    }
                } 
                else {
                    System.out.println("Main key '" + oldMainKey + "' does not exist.");
                }
            } 
            else {
                // Update sub-keys
                String[] parts = query.split("values", 2);
                if (parts.length < 2) {
                    System.out.println("Invalid update format for sub-keys.");
                    return;
                }
                String mainKey = parts[0].substring(query.indexOf(":") + 1).trim().replace("\"", "").replace("update ", "").trim();
                String valuesPart = parts[1].trim();
    
                if (!Main_mapping.containsKey(mainKey)) {
                    System.out.println("Main key '" + mainKey + "' does not exist. Use CREATE first.");
                    return;
                }
    
                valuesPart = valuesPart.substring(1, valuesPart.length() - 1);  
                String[] keyValuePairs = valuesPart.split("},\\s*\\{");  
    
                for (String pair : keyValuePairs) {
                    pair = pair.replace("{", "").replace("}", "").trim();
                    String[] keyValue = pair.split(",");
                    if (keyValue.length != 2) {
                        System.out.println("Invalid key-value format: " + pair);
                        continue;
                    }
                    String[] subKeyParts = keyValue[0].split(":");
                    String[] valueParts = keyValue[1].split(":");
                    if (subKeyParts.length < 2 || valueParts.length < 2) {
                        System.out.println("Invalid key or value format in: " + pair);
                        continue;
                    }
                    String subKey = subKeyParts[1].trim();
                    String value = valueParts[1].trim();
                    Main_mapping.get(mainKey).put(subKey, value);
                }
                System.out.println("\nUpdated values in '" + mainKey + "'.");
            }
        } 
        else {
            System.out.println("\nInvalid query.");
        }
        writeDataToFile();
    }

    void writeDataToFile() {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(FILE_NAME, false))) {
            for (Map.Entry<String, HashMap<String, String>> entry : Main_mapping.entrySet()) {
                String mainKey = entry.getKey();
                writer.write("main Key: " + mainKey + "\n");

                HashMap<String, String> subMap = entry.getValue();
                for (Map.Entry<String, String> subEntry : subMap.entrySet()) {
                    writer.write("    sub-Key: " + subEntry.getKey() + ", Value: " + subEntry.getValue() + "\n");
                }
            }
            System.out.println("\nData successfully updated in " + FILE_NAME);
        } catch (IOException e) {
            System.out.println("\nAn error occurred while writing to the file.");
            e.printStackTrace();
        }
    }

    void data_collection() {
        Scanner s1 = new Scanner(System.in);
        while (true) {
            System.out.println("\nEnter SQL-like query or type 'quit' to exit:");
            String query = s1.nextLine();

            if (query.equalsIgnoreCase("quit")) {
                writeDataToFile();
                break;
            }
            processQuery(query);
        }
    }
}

public class dbms {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);

        Data_read dr = new Data_read(); 
        Mapping_task mt = new Mapping_task(); 

        while (true) {
            System.out.println("\n\n\t\t<<===== no-SQL dbms =====>> ");
            System.out.println("\n\n\t\t Press 1 for data reading");
            System.out.println("\t\t Press 2 for dbms tasks ");
            System.out.println("\t\t Press 3 for exit\n\n");

            int i = sc.nextInt();
            sc.nextLine();

            switch (i) {
                case 1: 
                    dr.data_read();
                    break;

                case 2:
                    mt.data_collection();
                    break;

                case 3: 
                    System.out.println("<<===== Thanks =====>>");
                    sc.close();
                    System.exit(0);

                default:
                    System.out.println("\t\t<<===== Invalid choice =====>>\n\n");
            }
        }
    }
}
