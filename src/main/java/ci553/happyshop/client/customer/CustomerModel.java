package ci553.happyshop.client.customer;

import ci553.happyshop.catalogue.Order;
import ci553.happyshop.catalogue.Product;
import ci553.happyshop.storageAccess.DatabaseRW;
import ci553.happyshop.orderManagement.OrderHub;
import ci553.happyshop.utility.StorageLocation;
import ci553.happyshop.utility.ProductListFormatter;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * TODO
 * You can either directly modify the CustomerModel class to implement the required tasks,
 * or create a subclass of CustomerModel and override specific methods where appropriate.
 */
public class CustomerModel {
    public CustomerView cusView;
    public DatabaseRW databaseRW; //Interface type, not specific implementation

    private Product theProduct = null; // product found from search
    private ArrayList<Product> trolley = new ArrayList<>(); // a list of products in trolley

    // Four UI elements to be passed to CustomerView for display updates.
    private String imageName = "imageHolder.jpg";                // Image to show in product preview (Search Page)
    private String displayLaSearchResult = "No Product was searched yet"; // Label showing search result message (Search Page)
    private String displayTaTrolley = "";                                // Text area content showing current trolley items (Trolley Page)
    private String displayTaReceipt = "";                                // Text area content showing receipt after checkout (Receipt Page)

    //SELECT productID, description, image, unitPrice,inStock quantity
    // search product by ID or Name (advanced search)
    void search() throws SQLException {

        String productId = cusView.tfId.getText().trim();
        String productName = cusView.tfName.getText().trim();

        ArrayList<Product> results = new ArrayList<>();

        // if user typed something in ID, search by ID first
        if (!productId.isEmpty()) {
            results = databaseRW.searchProduct(productId);
        }
        // if ID is empty but Name is typed, search by name
        else if (!productName.isEmpty()) {
            results = databaseRW.searchProduct(productName);
        }
        // if both fields are empty
        else {
            theProduct = null;
            displayLaSearchResult = "Please type Product ID or Product Name";
            updateView();
            return;
        }

        // if no products found
        if (results.isEmpty()) {
            theProduct = null;
            displayLaSearchResult = "No products found";
        }
        // if only one product found, treat it as normal search
        else if (results.size() == 1) {
            theProduct = results.get(0);
            displayLaSearchResult =
                    "Product_Id: " + theProduct.getProductId() + "\n" +
                            theProduct.getProductDescription() + "\n" +
                            "Price: £" + theProduct.getUnitPrice() + "\n" +
                            theProduct.getStockQuantity() + " units left.";
        }
        // if multiple products found, show them all
        else {
            theProduct = null; // user must choose, so no single product selected
            StringBuilder sb = new StringBuilder();
            sb.append("Multiple products found:\n");

            for (Product p : results) {
                sb.append(p.getProductId())
                        .append(" - ")
                        .append(p.getProductDescription())
                        .append(" (£")
                        .append(p.getUnitPrice())
                        .append(")\n");
            }

            displayLaSearchResult = sb.toString();
        }

        updateView();
    }


    /**
     * ✅ NEW: Stock validation before adding to trolley.
     * Rule: every click on "Add to Trolley" adds 1 unit of the currently selected product.
     * If trolley already contains the same product, total quantity must not exceed stock quantity.
     */
    public boolean canAddSelectedItemToTrolley() {
        if (theProduct == null) {
            displayLaSearchResult = "Please search for an available product before adding it to the trolley";
            updateView();
            return false;
        }

        int stock = theProduct.getStockQuantity();
        if (stock <= 0) {
            displayLaSearchResult = "This product is out of stock.";
            updateView();
            return false;
        }

        String id = theProduct.getProductId();

        // Count how many units of this product already in trolley
        int alreadyInTrolley = 0;
        for (Product p : trolley) {
            if (p.getProductId().equals(id)) {
                int q = p.getOrderedQuantity();
                if (q <= 0) q = 1; // safety
                alreadyInTrolley += q;
            }
        }

        int afterAdd = alreadyInTrolley + 1; // each click adds 1
        if (afterAdd > stock) {
            displayLaSearchResult = "Cannot add more. Only " + stock + " units available for this product.";
            updateView();
            return false;
        }

        return true;
    }

    void addToTrolley() {
        if (theProduct != null) {

            String id = theProduct.getProductId();

            // 1) Merge same product by increasing orderedQuantity
            boolean merged = false;
            for (Product p : trolley) {
                if (p.getProductId().equals(id)) {
                    int q = p.getOrderedQuantity();
                    if (q <= 0) q = 1;
                    p.setOrderedQuantity(q + 1);
                    merged = true;
                    break;
                }
            }

            // 2) If not found, add a NEW copy with orderedQuantity = 1
            if (!merged) {
                Product copy = new Product(
                        theProduct.getProductId(),
                        theProduct.getProductDescription(),
                        theProduct.getProductImageName(),
                        theProduct.getUnitPrice(),
                        theProduct.getStockQuantity()
                );
                copy.setOrderedQuantity(1);
                trolley.add(copy);
            }

            displayTaTrolley = ProductListFormatter.buildString(trolley); //build a String for trolley so that we can show it
        } else {
            displayLaSearchResult = "Please search for an available product before adding it to the trolley";
            System.out.println("must search and get an available product before add to trolley");
        }

        displayTaReceipt = ""; // Clear receipt to switch back to trolleyPage (receipt shows only when not empty)
        updateView();
    }

    void checkOut() throws IOException, SQLException {
        if (!trolley.isEmpty()) {
            ArrayList<Product> groupedTrolley = groupProductsById(trolley);
            ArrayList<Product> insufficientProducts = databaseRW.purchaseStocks(groupedTrolley);

            if (insufficientProducts.isEmpty()) { // If stock is sufficient for all products
                OrderHub orderHub = OrderHub.getOrderHub();
                Order theOrder = orderHub.newOrder(trolley);

                trolley.clear();
                displayTaTrolley = "";
                displayTaReceipt = String.format(
                        "Order_ID: %s\nOrdered_Date_Time: %s\n%s",
                        theOrder.getOrderId(),
                        theOrder.getOrderedDateTime(),
                        ProductListFormatter.buildString(theOrder.getProductList())
                );
                System.out.println(displayTaReceipt);
            } else {
                StringBuilder errorMsg = new StringBuilder();
                for (Product p : insufficientProducts) {
                    errorMsg.append("\u2022 ").append(p.getProductId()).append(", ")
                            .append(p.getProductDescription()).append(" (Only ")
                            .append(p.getStockQuantity()).append(" available, ")
                            .append(p.getOrderedQuantity()).append(" requested)\n");
                }
                theProduct = null;

                displayLaSearchResult = "Checkout failed due to insufficient stock for the following products:\n" + errorMsg;
                System.out.println("stock is not enough");
            }
        } else {
            displayTaTrolley = "Your trolley is empty";
            System.out.println("Your trolley is empty");
        }
        updateView();
    }

    /**
     * Groups products by their productId to optimize database queries and updates.
     */
    private ArrayList<Product> groupProductsById(ArrayList<Product> proList) {
        Map<String, Product> grouped = new HashMap<>();
        for (Product p : proList) {
            String id = p.getProductId();
            if (grouped.containsKey(id)) {
                Product existing = grouped.get(id);
                existing.setOrderedQuantity(existing.getOrderedQuantity() + p.getOrderedQuantity());
            } else {
                // Make a shallow copy to avoid modifying the original
                Product copy = new Product(
                        p.getProductId(),
                        p.getProductDescription(),
                        p.getProductImageName(),
                        p.getUnitPrice(),
                        p.getStockQuantity()
                );
                int q = p.getOrderedQuantity();
                if (q <= 0) q = 1;
                copy.setOrderedQuantity(q);
                grouped.put(id, copy);
            }
        }
        return new ArrayList<>(grouped.values());
    }

    void cancel() {
        trolley.clear();
        displayTaTrolley = "";
        updateView();
    }

    void closeReceipt() {
        displayTaReceipt = "";
    }

    void updateView() {
        if (theProduct != null) {
            imageName = theProduct.getProductImageName();
            String relativeImageUrl = StorageLocation.imageFolder + imageName;
            Path imageFullPath = Paths.get(relativeImageUrl).toAbsolutePath();
            imageName = imageFullPath.toUri().toString();
            System.out.println("Image absolute path: " + imageFullPath);
        } else {
            imageName = "imageHolder.jpg";
        }
        cusView.update(imageName, displayLaSearchResult, displayTaTrolley, displayTaReceipt);
    }

    //for test only
    public ArrayList<Product> getTrolley() {
        return trolley;
    }
}
