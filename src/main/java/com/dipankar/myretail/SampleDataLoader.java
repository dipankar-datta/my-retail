package com.dipankar.myretail;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.sql.*;
import java.util.*;
import java.util.Date;


public class SampleDataLoader {
    public static void main(String[] args) throws IOException {

        String filePath = "others"
                + File.separator
                + "Sample-Superstore.xls";


        System.out.println("File path: " + filePath);
        System.out.println(new File(filePath).exists());
        printProducts(filePath);

    }

    private static void printProducts(String filePath) throws IOException {

        Map<Category, Set<Category>> categoryMap = new HashMap<>();
        Set<Product> products = new HashSet<>();


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

                Cell productIdCell = row.getCell(13);
                Cell categoryCell = row.getCell(14);
                Cell subCategoryCell = row.getCell(15);
                Cell productNameCell = row.getCell(16);
                Cell priceCell = row.getCell(17);

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

                products.add(new Product(productNameCell.getStringCellValue(), priceCell.getNumericCellValue(), category, subCategory));

            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        Set<Category> subCategories = new HashSet<>();

        categoryMap.forEach((key, value) -> {
         //   System.out.println("KEY: " + key.toString());
         //   System.out.println("    VALUE: " + value.toString());
            subCategories.addAll(value);
        });

        //saveCategories("categories", categoryMap.keySet());
        //saveCategories("subcategories", subCategories);
        processCatSubcatMap(categoryMap, fetchCategories(), fetchSubCategories());
        processProducts(categoryMap,  products);


    }

    public static boolean processProducts(Map<Category, Set<Category>> catSubcatMap, Set<Product> products){
        boolean success = false;

        StringBuffer sb = new StringBuffer();
        sb.append("INSERT INTO products (category, subcategory, name, description, creation_time, updation_time) VALUES ");

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
                                            "NOW()"
                                    );
                                    sb.append(composedString + ",");
                                }
                            });
                        }
                    });
                });


        String sql = sb.toString();
        sql = sql.substring(0, sql.length() - 1) + ";";
       // executeQuery(sql);
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
        //executeQuery("DELETE * FROM category_subcategory_map");
        //executeQuery(query.substring(0, query.length() - 1));
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
        return fetchQuery("SELECT * FROM categories");
    }

    private static Set<Category> fetchSubCategories() {
        return fetchQuery("SELECT * FROM subcategories");
    }

    private static Set<Category> fetchQuery(String query) {
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
            if (connection != null) {
                try {
                    connection.rollback();
                } catch (SQLException ex) {
                    ex.printStackTrace();
                }
            }
            e.printStackTrace();
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
                    "jdbc:mysql://localhost:3306/myretail","root","root");
        } catch (Exception e) {
           e.printStackTrace();
        }
        return connection;
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

    public Product(String name, double price, Category category, Category subCategory) {
        this.name = name;
        this.price = price;
        this.category = category;
        this.subCategory = subCategory;
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

    @Override
    public String toString() {
        return "Product{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", price=" + price +
                ", category=" + category +
                ", subCategory=" + subCategory +
                '}';
    }
}
