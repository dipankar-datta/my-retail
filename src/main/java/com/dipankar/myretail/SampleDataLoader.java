package com.dipankar.myretail;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.sql.*;
import java.util.*;


public class SampleDataLoader {
    public static void main(String[] args) throws IOException {

        String filePath = "others"
                + File.separator
                + "Sample-Superstore.xls";
        System.out.println("File path: " + filePath);
        System.out.println(new File(filePath).exists());
        //loadExcelData(filePath);
        //loadUserRoles();
    }

    private static void loadExcelData(String filePath) throws IOException {

        Map<Category, Set<Category>> categoryMap = new HashMap<>();
        Set<Product> products = new HashSet<>();
        Set<Item> segments = new HashSet<>();
        Set<Item> shipmentModes = new HashSet<>();
        Map<Item, Map<Item, Set<Item>>>  countryStateCityTree = new HashMap<>();


        try (FileInputStream file = new FileInputStream(new File(filePath))){
            //Create Workbook instance holding reference to .xlsx file
            HSSFWorkbook workbook = new HSSFWorkbook(file);

            //Get first/desired sheet from the workbook
            HSSFSheet sheet = workbook.getSheetAt(0);

            //Iterate through each rows one by one
            Iterator<Row> rowIterator = sheet.iterator();

            rowIterator.next();

            while (rowIterator.hasNext())
            {
                Row row = rowIterator.next();

                Cell shipmentModeCell = row.getCell(4);
                Cell segmentModeCell = row.getCell(7);
                Cell countryCell = row.getCell(8);
                Cell cityCell = row.getCell(9);
                Cell stateCell = row.getCell(10);
                Cell productIdCell = row.getCell(13);
                Cell categoryCell = row.getCell(14);
                Cell subCategoryCell = row.getCell(15);
                Cell productNameCell = row.getCell(16);
                Cell priceCell = row.getCell(17);

                populateCountryStateCity(
                        countryCell.getStringCellValue(),
                        stateCell.getStringCellValue(),
                        cityCell.getStringCellValue(),
                        countryStateCityTree);

                segments.add(new Item(segmentModeCell.getStringCellValue()));
                shipmentModes.add(new Item(shipmentModeCell.getStringCellValue()));



                String productId = productIdCell.getStringCellValue();
                String[] cat_Subcat = productId.split("-");
                Category category = new Category(cat_Subcat[0], categoryCell.getStringCellValue());
                Category subCategory = new Category(cat_Subcat[1], subCategoryCell.getStringCellValue());
                Set<Category> subCategories = categoryMap.get(category);
                if (subCategories == null) {
                    subCategories = new HashSet<>();
                }
                subCategories.add(subCategory);
                categoryMap.put(category, subCategories);

                products.add(
                        new Product(
                                productNameCell.getStringCellValue(),
                                priceCell.getNumericCellValue(),
                                category,
                                subCategory,
                                new Item(segmentModeCell.getStringCellValue())
                        )
                );

            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        Set<Category> subCategories = new HashSet<>();

        categoryMap.forEach((key, value) -> subCategories.addAll(value));

        clearExistingData();
        saveCategories("categories", categoryMap.keySet());
        saveCategories("subcategories", subCategories);
        processCatSubcatMap(categoryMap, fetchCategories(), fetchSubCategories());
        processSegments(segments);
        processProducts(categoryMap, segments, products);
        processShipmentModes(shipmentModes);
        processCountryStateCity(countryStateCityTree);
        loadUserRoles();
    }

    private static void loadUserRoles() {
        StringBuffer sb = new StringBuffer();
        sb.append("INSERT INTO ROLES (code, name) VALUES ");
        sb.append("('ADMIN', 'Admin'),");
        sb.append("('CUSTOMER', 'Customer'),");
        sb.append("('EMPLOYEE', 'Employee'),");
        sb.append("('SUPPLIER', 'Supplier');");
        executeQuery(sb.toString());
    }

    private static void clearExistingData() {
        executeQuery("DELETE FROM category_subcategory_map");
        executeQuery("DELETE FROM products");
        executeQuery("DELETE FROM categories");
        executeQuery("DELETE FROM subcategories");
        executeQuery("DELETE FROM segments");
        executeQuery("DELETE FROM shipment_modes");
        executeQuery("DELETE FROM country_state_city_map");
        executeQuery("DELETE FROM countries");
        executeQuery("DELETE FROM states");
        executeQuery("DELETE FROM cities");
    }

    private static void processSegments(Set<Item> segments) {
        segments.forEach(segment -> {
            Integer id = executeQueryWithId("INSERT INTO segments (name) VALUES ('" + segment.getName() + "')");
            if (id != null) {
                segment.setId(id);;
            }
        });
    }

    private static void processShipmentModes(Set<Item> shipmentModes) {
        shipmentModes.forEach(shipmentMode -> {
            Integer id = executeQueryWithId("INSERT INTO shipment_modes (name) VALUES ('" + shipmentMode.getName() + "')");
            if (id != null) {
                shipmentMode.setId(id);;
            }
        });
    }

    public static void populateCountryStateCity(String country, String state, String city, Map<Item, Map<Item, Set<Item>>>  countryStateCityTree) {

        Item countryItem = new Item(country);
        Item stateItem = new Item(state);
        Item cityItem = new Item(city);

        Map<Item, Set<Item>> stateCityTree = countryStateCityTree.get(countryItem);
        if (stateCityTree != null) {
            Optional<Set<Item>> citiesOptional = stateCityTree.keySet()
                    .stream()
                    .filter(item -> item.getName().equals(stateItem.getName()))
                    .map(item -> stateCityTree.get(item))
                    .findFirst();
            if (citiesOptional.isPresent()) {
                citiesOptional.get().add(cityItem);
            } else {
                Set<Item> cities = new HashSet<>();
                cities.add(cityItem);
                stateCityTree.put(stateItem, cities);
            }
        } else {
            Map<Item, Set<Item>> newStateCityTree = new HashMap<>();
            Set<Item> cities = new HashSet<>();
            cities.add(cityItem);
            newStateCityTree.put(stateItem, cities);
            countryStateCityTree.put(countryItem, newStateCityTree);
        }

    }

    public static void processCountryStateCity(Map<Item, Map<Item, Set<Item>>>  countryStateCityTree) {
        executeQuery("DELETE FROM country_state_city_map");
        executeQuery("DELETE FROM countries");
        executeQuery("DELETE FROM states");
        executeQuery("DELETE FROM cities");


        System.out.println("Populated Country, State, City tree");
        Map<Item, Integer> uniqieSavedCityIds = new HashMap<>();
        countryStateCityTree.forEach((countryItem, stateCityMap) -> {

            Integer countryId = executeQueryWithId("INSERT INTO countries (name) VALUES ('" + countryItem.getName() + "')");
            if (countryId != null) {
                countryItem.setId(countryId);
            }
            stateCityMap.forEach((stateItem, citySet) -> {
                Integer stateId = executeQueryWithId("INSERT INTO states (name) VALUES ('" + stateItem.getName() + "')");
                if (stateId != null) {
                    stateItem.setId(stateId);
                }
                citySet.forEach(cityItem -> {
                    Integer cityId = null;
                    if (!uniqieSavedCityIds.containsKey(cityItem)) {
                        cityId = executeQueryWithId("INSERT INTO cities (name) VALUES ('" + cityItem.getName() + "')");
                        if (cityId != null) {
                            cityItem.setId(cityId);
                        }
                        uniqieSavedCityIds.put(cityItem, cityId);
                    } else {
                        cityId = uniqieSavedCityIds.get(cityItem);
                    }

                    if (countryId != null && stateId != null && cityId != null) {
                        executeQuery("INSERT INTO country_state_city_map (country, state, city) VALUES (" + countryId + ", " +stateId+ ", " + cityId + ")");
                        System.out.println(countryId + " >> " + stateId+ " >> " + cityId);
                    }
                    System.out.println(countryItem.getName() + " >> " + stateItem.getName() + " >> " + cityItem.getName());
                });
            });
        });
    }

    public static boolean processProducts(Map<Category, Set<Category>> catSubcatMap, Set<Item> segments, Set<Product> products){
        boolean success = false;

        StringBuffer sb = new StringBuffer();
        sb.append("INSERT INTO products (category, subcategory, name, description, creation_time, updation_time, segment) VALUES ");

        Map<Item, Integer> segmentIdMap = new HashMap<>();
        segments.forEach(segment -> segmentIdMap.put(segment, segment.getId()));

        products.stream()
                .forEach(unsavedProduct -> {
                    catSubcatMap.entrySet().forEach(categorySetEntry -> {
                        if (categorySetEntry.getKey().equals(unsavedProduct.getCategory())) {
                            unsavedProduct.setCategory(categorySetEntry.getKey());
                            categorySetEntry.getValue().forEach(savedSubCategory -> {
                                if (savedSubCategory.equals(unsavedProduct.getSubCategory())) {
                                    unsavedProduct.setSubCategory(savedSubCategory);
                                    String composedString = composeSqlValues(
                                            unsavedProduct.getCategory().getId(),
                                            unsavedProduct.getSubCategory().getId(),
                                            unsavedProduct.getName(),
                                            unsavedProduct.getName(),
                                            "NOW()",
                                            "NOW()",
                                            segmentIdMap.get(unsavedProduct.getSegment())
                                    );
                                    sb.append(composedString + ",");
                                }
                            });
                        }
                    });
                });


        String sql = sb.toString();
        sql = sql.substring(0, sql.length() - 1) + ";";
        executeQuery(sql);
        return success;
    }

    private static boolean processCatSubcatMap(Map<Category, Set<Category>> catSubcatMap, Set<Category> savedCategories, Set<Category> savedSubcategories) {
        StringBuffer sb = new StringBuffer();
        sb.append("INSERT INTO category_subcategory_map VALUES ");
        savedCategories.forEach(savedCategory -> {
            Set<Category> unsavedSubCategories = catSubcatMap.get(savedCategory);
            if (unsavedSubCategories != null && unsavedSubCategories.size() > 0) {
                unsavedSubCategories.stream()
                        .map(unsavedSubCategory -> {
                            return savedSubcategories.stream()
                                    .filter(savedSubCategory -> {
                                        boolean match = savedSubCategory.equals(unsavedSubCategory);
                                        if (match) {
                                            unsavedSubCategory.setId(savedSubCategory.getId());
                                        }
                                        return match;
                                    })
                                    .findFirst().get();
                        }).forEach(savedSubcategory -> {
                            sb.append("(" + savedCategory.getId() +", " +savedSubcategory.getId() + "),");
                        });
            }
        });

        catSubcatMap.keySet().forEach(unsavedCategory -> {
            savedCategories.forEach(savedCategory -> {
                if (unsavedCategory.equals(savedCategory)) {
                    unsavedCategory.setId(savedCategory.getId());
                }
            });
        });


        String query = sb.toString();
        executeQuery("DELETE FROM category_subcategory_map");
        executeQuery(query.substring(0, query.length() - 1));
        return true;
    }

    private static void saveCategories(String targetTable, Set<Category> items) {

        StringBuffer values = new StringBuffer();
        items.stream().forEach(item  -> {
            values.append("('"+ item.getCode() +"', '"+ item.getName() +"'),");
        });

        String sql = "INSERT INTO "+ targetTable +" (code, name) VALUES " + values.toString().substring(0, (values.length() - 1));
        System.out.println("SQL: " + sql);
        if (executeQuery(sql)) {
            System.out.println("SAVED");
        }
    }

    private static Set<Category> fetchCategories() {
        return fetchCategories("SELECT * FROM categories");
    }

    private static Set<Category> fetchSubCategories() {
        return fetchCategories("SELECT * FROM subcategories");
    }

    private static Set<Category> fetchCategories(String query) {
        System.out.println("SQL FETCH: " + query);
        Set<Category> categories = new HashSet<>();
        Connection connection = null;
        Statement statement = null;
        ResultSet resultSet = null;
        connection = getConnection();
        try {
            statement = connection.createStatement();
            resultSet = statement.executeQuery(query);
            if (resultSet != null) {
                while (resultSet.next()) {
                    categories.add(new Category(
                            resultSet.getInt("id"),
                            resultSet.getString("code"),
                            resultSet.getString("name"))
                    );
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            closeResources(connection, statement, resultSet);
        }
        return categories;
    }

    private static Integer executeQueryWithId(String query) {
        System.out.println("SQL EXECUTE: " + query);
        Connection connection = null;
        Statement statement = null;
        connection = getConnection();
        ResultSet resultSet = null;
        Integer insertedRecordId = null;
        try {
            connection.setAutoCommit(false);
            statement = connection.createStatement();
            statement.executeUpdate(query, Statement.RETURN_GENERATED_KEYS);
            connection.commit();
            resultSet = statement.getGeneratedKeys();
            if (resultSet.next()){
                insertedRecordId = resultSet.getInt(1);
            }
        } catch (Exception e) {
            rollbackWithExceptionTrace(connection, e);
        } finally {
            closeResources(connection, statement, resultSet);
        }
        return insertedRecordId;
    }



    private static boolean executeQuery(String query) {
        boolean saved = false;
        System.out.println("SQL EXECUTE: " + query);
        Connection connection = null;
        Statement statement = null;
        connection = getConnection();
        try {
            connection.setAutoCommit(false);
            statement = connection.createStatement();
            statement.executeUpdate(query);
            connection.commit();
            saved = true;
        } catch (Exception e) {
            rollbackWithExceptionTrace(connection, e);
        } finally {
           closeResources(connection, statement, null);
        }
        return saved;
    }

    private static Connection getConnection() {
        Connection connection = null;
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            connection = DriverManager.getConnection(
                    "jdbc:mysql://localhost:3306/myretail_users","root","root");
        } catch (Exception e) {
           e.printStackTrace();
        }
        return connection;
    }

    private static boolean rollbackWithExceptionTrace(Connection connection, Exception e) {
        boolean status = false;
        if (connection != null) {
            try {
                connection.rollback();
                status = true;
            } catch (SQLException ex) {
                ex.printStackTrace();
            }
        }
        e.printStackTrace();
        return status;
    }

    private static String composeSqlValues(Object... values) {
        String valueString = null;
        if (values.length > 0) {
            StringBuffer sb = new StringBuffer();
            for (Object value : values) {
                if (value instanceof String) {
                    String strValue = (String) value;
                    if (strValue.contains("NOW()")) {
                        sb.append("NOW()");
                    } else {
                        String escapedString = "'" + (!strValue.contains("'") ? strValue : strValue.replace("'", "''")) + "'";
                        if (escapedString.contains("'''")) {

                            escapedString = escapedString.replace("'''", "\\''");
                        }
                        sb.append(escapedString);
                    }
                    sb.append(",");
                } else {
                    sb.append(value.toString() + ",");
                }
            }

            valueString = sb.toString();
            valueString = valueString.substring(0, valueString.length() - 1);
            valueString = "(" + valueString + ")";
        }

        return valueString;
    }

    private static boolean closeResources(Connection connection, Statement statement, ResultSet resultSet) {
        boolean closed = false;
        try {
            if (connection != null) {
                connection.close();
            }
            if (statement != null) {
                statement.close();
            }
            if (resultSet != null) {
                resultSet.close();
            }
            closed = true;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return closed;
    }
}

class Category {
    private Integer id;
    private String code;
    private String name;

    public Category(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public Category(Integer id, String code, String name) {
        this.id = id;
        this.code = code;
        this.name = name;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Category category = (Category) o;
        return code.equals(category.code) &&
                name.equals(category.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(code, name);
    }

    @Override
    public String toString() {
        return "Category{" +
                "id=" + id +
                ", code='" + code + '\'' +
                ", name='" + name + '\'' +
                '}';
    }
}

class Product {

    private Integer id;
    private String name;
    private double price;
    private Category category;
    private Category subCategory;
    private Item segment;

    public Product(String name, double price, Category category, Category subCategory, Item segment) {
        this.name = name;
        this.price = price;
        this.category = category;
        this.subCategory = subCategory;
        this.segment = segment;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public Category getCategory() {
        return category;
    }

    public void setCategory(Category category) {
        this.category = category;
    }

    public Category getSubCategory() {
        return subCategory;
    }

    public void setSubCategory(Category subCategory) {
        this.subCategory = subCategory;
    }

    public Item getSegment() {
        return segment;
    }

    public void setSegment(Item segment) {
        this.segment = segment;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Product product = (Product) o;
        return Double.compare(product.price, price) == 0 &&
                name.equals(product.name) &&
                category.equals(product.category) &&
                subCategory.equals(product.subCategory) &&
                segment.equals(product.segment);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, price, category, subCategory, segment);
    }

    @Override
    public String toString() {
        return "Product{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", price=" + price +
                ", category=" + category +
                ", subCategory=" + subCategory +
                ", segment=" + segment +
                '}';
    }
}

class Item {
    private Integer id;
    private String name;

    public Item(Integer id, String name) {
        this.id = id;
        this.name = name;
    }

    public Item(String name) {
        this.name = name;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Item item = (Item) o;
        return name.equals(item.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    @Override
    public String toString() {
        return "Item{" +
                "id=" + id +
                ", name='" + name + '\'' +
                '}';
    }
}