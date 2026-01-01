package com.cybergame.ui.fxcontroller;

import com.cybergame.app.AppContext;
import com.cybergame.controller.OrderController;
import com.cybergame.model.entity.OrderItem;
import com.cybergame.model.entity.ServiceItem;
import com.cybergame.model.entity.Session;
import com.cybergame.model.enums.OrderStatus;
import com.cybergame.model.enums.PaymentSource;
import com.cybergame.model.enums.ServiceCategory;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.List;

public class ServiceMenuController {

    // ===== PHẦN GỌI MÓN =====
    @FXML private FlowPane pnlItems;
    @FXML private ComboBox<String> cbCategory;
    
    @FXML private TableView<CartItem> tblCart;
    @FXML private TableColumn<CartItem, String> colName;
    @FXML private TableColumn<CartItem, Number> colQty;
    @FXML private TableColumn<CartItem, String> colPrice;
    @FXML private Label lblTotal;

    // ===== PHẦN LỊCH SỬ =====
    @FXML private TableView<OrderItem> tblHistory;
    @FXML private TableColumn<OrderItem, String> colHistName;
    @FXML private TableColumn<OrderItem, Number> colHistQty;
    @FXML private TableColumn<OrderItem, String> colHistTotal;
    @FXML private TableColumn<OrderItem, String> colHistStatus;
    
    // 🔥 Cột Hành Động (Chứa nút Hủy / Hoàn tất)
    @FXML private TableColumn<OrderItem, Void> colHistAction;

    private Session currentSession;
    
    // Controller xử lý logic đặt hàng/hủy đơn
    private final OrderController orderController = new OrderController(AppContext.accountRepo);
    private final ObservableList<CartItem> cartList = FXCollections.observableArrayList();
    
    // Timer để tự động cập nhật trạng thái đơn
    private Timeline refreshTimer;

    @FXML
    public void initialize() {
        setupCartTable();
        setupHistoryTable();
        loadCategories();
        
        // Load lần đầu
        loadItems(null);
        
        // 🔥 Bắt đầu chạy Timer tự động cập nhật
        startAutoRefresh();
    }

    public void setSession(Session session) {
        this.currentSession = session;
        refreshHistory();
    }

    // ================== 1. AUTO REFRESH (TỰ ĐỘNG CẬP NHẬT) ==================
    // 🔥 ĐÂY LÀ PHẦN TÔI ĐÃ SỬA GIÚP ÔNG
    private void startAutoRefresh() {
        // Cứ 3 giây sẽ chạy code trong này một lần
        refreshTimer = new Timeline(new KeyFrame(Duration.seconds(3), e -> {
            
            // 1. Cập nhật lại danh sách món ăn (Để ẩn món bị khóa hoặc hiện món mới)
            // Lấy category hiện tại đang chọn để load đúng loại
            String currentCat = cbCategory.getValue(); 
            loadItems(currentCat);

            // 2. Cập nhật lịch sử đơn hàng (nếu đã đăng nhập)
            if (currentSession != null) {
                refreshHistory();
            }
        }));
        
        refreshTimer.setCycleCount(Timeline.INDEFINITE);
        refreshTimer.play();
    }

    // Hàm làm mới bảng lịch sử
    @FXML 
    public void refreshHistory() {
        if (currentSession != null && tblHistory != null) {
            List<OrderItem> items = currentSession.getOrderItems();
            int selectedIndex = tblHistory.getSelectionModel().getSelectedIndex();
            
            tblHistory.getItems().setAll(items);
            
            if (selectedIndex >= 0 && selectedIndex < items.size()) {
                tblHistory.getSelectionModel().select(selectedIndex);
            }
        }
    }

    // ================== 2. SETUP GIAO DIỆN BẢNG ==================

    private void setupCartTable() {
        colName.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().service.getName()));
        colQty.setCellValueFactory(c -> new SimpleIntegerProperty(c.getValue().quantity));
        colPrice.setCellValueFactory(c -> new SimpleStringProperty(String.format("%,.0f", c.getValue().getTotal())));
        tblCart.setItems(cartList);
    }

    private void setupHistoryTable() {
        colHistName.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getServiceItem().getName()));
        colHistQty.setCellValueFactory(cell -> new SimpleIntegerProperty(cell.getValue().getQuantity()));
        colHistTotal.setCellValueFactory(cell -> new SimpleStringProperty(String.format("%,.0f đ", cell.getValue().getCost())));
        colHistStatus.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getStatus().name()));

        colHistStatus.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);
                if (empty || status == null) {
                    setText(null); setStyle("");
                } else {
                    setText(status);
                    if (status.equals("PENDING")) setStyle("-fx-text-fill: #f59e0b; -fx-font-weight: bold;"); 
                    else if (status.equals("CONFIRMED") || status.equals("COMPLETED")) setStyle("-fx-text-fill: #4ade80; -fx-font-weight: bold;"); 
                    else if (status.equals("CANCELLED")) setStyle("-fx-text-fill: #ef4444; -fx-font-weight: bold;"); 
                    else setStyle("-fx-text-fill: white;");
                }
            }
        });

        colHistAction.setCellFactory(param -> new TableCell<>() {
            private final Button btnAction = new Button();
            {
                btnAction.setStyle("-fx-font-weight: bold; -fx-cursor: hand; -fx-font-size: 11px;");
                btnAction.setOnAction(event -> {
                    OrderItem order = getTableView().getItems().get(getIndex());
                    handleOrderAction(order);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    OrderItem order = getTableView().getItems().get(getIndex());
                    OrderStatus status = order.getStatus();

                    if (status == OrderStatus.PENDING) {
                        btnAction.setText("Hủy đơn");
                        btnAction.setStyle("-fx-background-color: #ef4444; -fx-text-fill: white;");
                        setGraphic(btnAction);
                    } 
                    else if (status == OrderStatus.CONFIRMED) {
                        btnAction.setText("Hoàn tất");
                        btnAction.setStyle("-fx-background-color: #22c55e; -fx-text-fill: white;");
                        setGraphic(btnAction);
                    } 
                    else {
                        setGraphic(null);
                    }
                }
            }
        });
    }

    // ================== 3. XỬ LÝ LOGIC NÚT BẤM ==================

    private void handleOrderAction(OrderItem order) {
        if (order.getStatus() == OrderStatus.PENDING) {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("Hủy đơn hàng");
            confirm.setHeaderText("Bạn có chắc muốn hủy món: " + order.getServiceItem().getName() + "?");
            confirm.setContentText("Tiền sẽ được hoàn lại vào tài khoản (nếu đã trừ).");

            confirm.showAndWait().ifPresent(type -> {
                if (type == ButtonType.OK) {
                    try {
                        orderController.cancelOrder(order, currentSession);
                        refreshHistory(); 
                    } catch (Exception e) {
                        e.printStackTrace();
                        showAlert("Lỗi hủy đơn: " + e.getMessage());
                    }
                }
            });
        } 
        else if (order.getStatus() == OrderStatus.CONFIRMED) {
            orderController.completeOrder(order);
            refreshHistory();
            Alert info = new Alert(Alert.AlertType.INFORMATION);
            info.setTitle("Cảm ơn");
            info.setHeaderText(null);
            info.setContentText("Cảm ơn quý khách! Chúc quý khách ngon miệng.");
            info.show();
        }
    }

    // ================== 4. LOGIC ĐẶT HÀNG ==================

    @FXML
    private void handleOrder() {
        if (cartList.isEmpty()) { showAlert("Giỏ hàng trống!"); return; }
        if (currentSession == null) { showAlert("Lỗi: Không tìm thấy phiên chơi!"); return; }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Thanh toán");
        confirm.setHeaderText("Chọn phương thức thanh toán:");
        ButtonType btnAcc = new ButtonType("Tài khoản");
        ButtonType btnCash = new ButtonType("Tiền mặt");
        ButtonType btnCancel = new ButtonType("Hủy", ButtonBar.ButtonData.CANCEL_CLOSE);
        confirm.getButtonTypes().setAll(btnAcc, btnCash, btnCancel);

        confirm.showAndWait().ifPresent(type -> {
            PaymentSource source = PaymentSource.CASH;
            if (type == btnAcc) source = PaymentSource.ACCOUNT;
            else if (type == btnCancel) return;

            boolean hasError = false;

            for (CartItem c : cartList) {
                try {
                    OrderItem result = orderController.addOrder(currentSession, c.service, c.quantity, source);
                    if (result == null) {
                        hasError = true;
                        showAlert("Đặt thất bại món: " + c.service.getName() + " (Hết tiền hoặc bị khóa)");
                    }
                } catch (Exception e) {
                    hasError = true;
                    showAlert("Lỗi hệ thống: " + e.getMessage());
                }
            }
            
            if (!hasError) {
                cartList.clear();
                updateTotal();
                refreshHistory();
                new Alert(Alert.AlertType.INFORMATION, "Đặt món thành công!").show();
            } else {
                refreshHistory(); 
            }
        });
    }

    private void loadCategories() {
        cbCategory.getItems().add("Tất cả");
        for (ServiceCategory cat : ServiceCategory.values()) cbCategory.getItems().add(cat.name());
        cbCategory.getSelectionModel().selectFirst();
        cbCategory.setOnAction(e -> loadItems(cbCategory.getValue()));
    }

    // 🔥 HÀM NÀY SẼ ĐƯỢC TIMER GỌI LẠI MỖI 3 GIÂY
    private void loadItems(String category) {
        // Lưu ý: Việc clear và add lại liên tục có thể làm thanh cuộn bị nhảy lên đầu
        // Nhưng đây là cách dễ nhất để cập nhật real-time.
        pnlItems.getChildren().clear();
        
        // Gọi xuống DB lấy danh sách mới nhất (đã cập nhật trạng thái Locked)
        List<ServiceItem> items = AppContext.serviceRepo.findAll();
        
        for (ServiceItem item : items) {
            
            // 🔥 Món bị khóa sẽ bị continue (bỏ qua) -> Biến mất khỏi màn hình
            if (item.isLocked()) {
                continue;
            }

            if (category != null && !category.equals("Tất cả") && !item.getCategory().name().equals(category)) {
                continue;
            }
            
            pnlItems.getChildren().add(createItemCard(item));
        }
    }

    private VBox createItemCard(ServiceItem item) {
        VBox card = new VBox(5);
        card.setStyle("-fx-background-color: #1e293b; -fx-padding: 10; -fx-background-radius: 5;");
        card.setPrefSize(140, 140);
        Label lblName = new Label(item.getName());
        lblName.setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-wrap-text: true; -fx-alignment: CENTER;");
        lblName.setPrefWidth(120);
        Label lblPrice = new Label(String.format("%,.0f đ", item.getUnitPrice()));
        lblPrice.setStyle("-fx-text-fill: #4ade80;");
        Button btnAdd = new Button("Chọn");
        btnAdd.setStyle("-fx-background-color: #3b82f6; -fx-text-fill: white; -fx-cursor: hand;");
        btnAdd.setOnAction(e -> addToCart(item));
        card.getChildren().addAll(new Label("🍔"), lblName, lblPrice, btnAdd);
        card.setAlignment(javafx.geometry.Pos.CENTER);
        return card;
    }

    private void addToCart(ServiceItem item) {
        for (CartItem c : cartList) {
            if (c.service.getServiceId() == item.getServiceId()) {
                c.quantity++; tblCart.refresh(); updateTotal(); return;
            }
        }
        cartList.add(new CartItem(item, 1));
        updateTotal();
    }

    @FXML private void increaseQty() { CartItem s = tblCart.getSelectionModel().getSelectedItem(); if (s!=null) {s.quantity++; tblCart.refresh(); updateTotal();} }
    @FXML private void decreaseQty() { CartItem s = tblCart.getSelectionModel().getSelectedItem(); if (s!=null && s.quantity > 1) {s.quantity--; tblCart.refresh(); updateTotal();} }
    @FXML private void removeItem() { CartItem s = tblCart.getSelectionModel().getSelectedItem(); if (s!=null) {cartList.remove(s); updateTotal();} }
    private void updateTotal() { lblTotal.setText(String.format("%,.0f đ", cartList.stream().mapToDouble(CartItem::getTotal).sum())); }

    private void showAlert(String msg) {
        Alert a = new Alert(Alert.AlertType.WARNING); a.setContentText(msg); a.show();
    }

    public static class CartItem {
        ServiceItem service; int quantity;
        public CartItem(ServiceItem s, int q) { this.service = s; this.quantity = q; }
        public double getTotal() { return service.getUnitPrice() * quantity; }
    }
}