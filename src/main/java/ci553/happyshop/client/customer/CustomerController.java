package ci553.happyshop.client.customer;

import java.io.IOException;
import java.sql.SQLException;

public class CustomerController {
    public CustomerModel cusModel;

    public void doAction(String action) throws SQLException, IOException {

        switch (action) {

            case "Search":
                cusModel.search();
                break;

            case "Add to Trolley":
                // check stock before adding
                if (cusModel.canAddSelectedItemToTrolley()) {
                    cusModel.addToTrolley();
                }
                break;

            case "+":
                // increase quantity of selected product
                cusModel.increaseQuantity();
                break;

            case "-":
                // decrease quantity of selected product
                cusModel.decreaseQuantity();
                break;

            case "Remove":
                // remove product from trolley
                cusModel.removeFromTrolley();
                break;

            case "Cancel":
                cusModel.cancel();
                break;

            case "Check Out":
                cusModel.checkOut();
                break;

            case "OK & Close":
                cusModel.closeReceipt();
                break;
        }
    }
}
