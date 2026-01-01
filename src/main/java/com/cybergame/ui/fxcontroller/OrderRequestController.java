package com.cybergame.ui.fxcontroller;

import com.cybergame.app.AppContext;
import com.cybergame.controller.OrderController;
import com.cybergame.model.entity.OrderItem;
import com.cybergame.model.entity.Session;
import com.cybergame.model.enums.OrderStatus;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.util.Duration;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class OrderRequestController {

    @FXML private TableView<OrderViewModel> tblOrders;
    @FXML private TableColumn<OrderViewModel, String> colTime;
    @FXML private TableColumn<OrderViewModel, String> colComputer;
    @FXML private TableColumn<OrderViewModel, String> colService;
    @FXML private TableColumn<OrderViewModel, Number> colQty;
    @FXML private TableColumn<OrderViewModel, String> colPayment;
    @FXML private TableColumn<OrderViewModel, String> colStatus;
    @FXML private TableColumn<OrderViewModel, Void> colAction;
    
    @FXML private Label lblPendingCount;

    // Dùng OrderController để xử lý logic Hủy (hoàn tiền)
    private final OrderController orderController = new OrderController(AppContext.accountRepo);
    private final ObservableList<OrderViewModel> tableData = FXCollections.observableArrayList();
    private Timeline refreshTimer;
    private final DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("HH:mm:ss");

    @FXML
    public void initialize() {
        setupColumns();
        refreshData();
        startAutoRefresh();

        // --- [NEW] CẤU HÌNH GIAO DIỆN DARK MODE & TOGGLE CLICK ---
        setupRowSelection();
        Platform.runLater(() -> tblOrders.getSelectionModel().clearSelection());
    }

    private void setupRowSelection() {
        // 1. Set màu nền tối cho cả bảng
        tblOrders.setStyle("-fx-control-inner-background: #0d1b2a; -fx-base: #0d1b2a; -fx-background-color: #0d1b2a;");

        tblOrders.setRowFactory(tv -> {
            TableRow<OrderViewModel> row = new TableRow<>();

            // 2. Logic Toggle: Ấn lại vào dòng đang chọn thì bỏ chọn
            row.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
                if (!row.isEmpty() && row.isSelected() && e.getButton() == MouseButton.PRIMARY) {
                    tblOrders.getSelectionModel().clearSelection();
                    e.consume();
                }
            });

            // 3. Logic Tô màu: Chọn -> Xanh Dương (#007bff)
            row.selectedProperty().addListener((obs, wasSelected, isSelected) -> {
                if (!row.isEmpty()) {
                    if (isSelected) {
                        // Nền Xanh Dương - Chữ Trắng
                        row.setStyle("-fx-background-color: #007bff; -fx-text-fill: white; -fx-font-weight: bold;");
                    } else {
                        // Nền Trong suốt (mặc định) - Chữ Trắng (để nổi trên nền tối)
                        row.setStyle("-fx-background-color: transparent; -fx-text-fill: white;");
                    }
                }
            });

            // 4. Cập nhật lại màu khi dữ liệu thay đổi (do auto refresh)
            row.itemProperty().addListener((obs, oldItem, newItem) -> {
                if (newItem == null) {
                    row.setStyle("-fx-background-color: transparent;");
                } else {
                    if (row.isSelected()) {
                        row.setStyle("-fx-background-color: #007bff; -fx-text-fill: white; -fx-font-weight: bold;");
                    } else {
                        row.setStyle("-fx-background-color: transparent; -fx-text-fill: white;");
                    }
                }
            });

            return row;
        });
    }

    private void setupColumns() {
        colTime.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().order.getOrderedAt().format(timeFmt)
        ));
        colComputer.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().session.getComputer().getName()
        ));
        colService.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().order.getServiceItem().getName()
        ));
        colQty.setCellValueFactory(c -> new SimpleIntegerProperty(
                c.getValue().order.getQuantity()
        ));
        colPayment.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().order.getPaymentSource().name()
        ));
        
        // Màu sắc trạng thái
        colStatus.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().order.getStatus().name()
        ));
        colStatus.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);
                if (empty || status == null) {
                    setText(null); setStyle("");
                } else {
                    setText(status);
                    // Giữ nguyên logic màu trạng thái của đại ca (nó sẽ đè lên màu chữ trắng của Row -> Tốt)
                    if (status.equals("PENDING")) setStyle("-fx-text-fill: #f59e0b; -fx-font-weight: bold; -fx-alignment: CENTER;"); // Cam
                    else if (status.equals("CONFIRMED")) setStyle("-fx-text-fill: #3b82f6; -fx-font-weight: bold; -fx-alignment: CENTER;"); // Xanh dương
                    else if (status.equals("COMPLETED")) setStyle("-fx-text-fill: #22c55e; -fx-font-weight: bold; -fx-alignment: CENTER;"); // Xanh lá
                    else if (status.equals("CANCELLED")) setStyle("-fx-text-fill: #ef4444; -fx-font-weight: bold; -fx-alignment: CENTER;"); // Đỏ
                    else setStyle("-fx-text-fill: white; -fx-alignment: CENTER;");
                }
            }
        });

        // 🔥 CỘT HÀNH ĐỘNG CỦA ADMIN
        colAction.setCellFactory(param -> new TableCell<>() {
            private final Button btnConfirm = new Button("✔");
            private final Button btnCancel = new Button("✖");
            private final HBox pane = new HBox(10, btnConfirm, btnCancel);

            {
                pane.setAlignment(javafx.geometry.Pos.CENTER);
                
                // Style nút nhỏ gọn hơn chút
                btnConfirm.setStyle("-fx-background-color: #3b82f6; -fx-text-fill: white; -fx-font-weight: bold; -fx-cursor: hand; -fx-min-width: 30px;");
                btnCancel.setStyle("-fx-background-color: #ef4444; -fx-text-fill: white; -fx-font-weight: bold; -fx-cursor: hand; -fx-min-width: 30px;");

                btnConfirm.setOnAction(e -> handleConfirm(getTableView().getItems().get(getIndex())));
                btnCancel.setOnAction(e -> handleCancel(getTableView().getItems().get(getIndex())));
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    OrderViewModel vm = getTableView().getItems().get(getIndex());
                    // Chỉ hiện nút nếu đơn đang PENDING
                    if (vm.order.getStatus() == OrderStatus.PENDING) {
                        setGraphic(pane);
                    } else {
                        setGraphic(null);
                    }
                }
            }
        });
        
        tblOrders.setItems(tableData);
    }

    // --- XỬ LÝ NÚT BẤM ---

    private void handleConfirm(OrderViewModel vm) {
        orderController.confirmOrder(vm.order);
        refreshData();
    }

    private void handleCancel(OrderViewModel vm) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Hủy đơn hàng");
        confirm.setHeaderText("Bạn có chắc muốn hủy đơn của " + vm.session.getComputer().getName() + "?");
        confirm.setContentText("Tiền sẽ được hoàn lại cho khách.");

        confirm.showAndWait().ifPresent(type -> {
            if (type == ButtonType.OK) {
                try {
                    orderController.cancelOrder(vm.order, vm.session);
                    refreshData();
                } catch (Exception e) {
                    e.printStackTrace();
                    new Alert(Alert.AlertType.ERROR, "Lỗi: " + e.getMessage()).show();
                }
            }
        });
    }

    // --- LOGIC CẬP NHẬT DỮ LIỆU ---

    private void startAutoRefresh() {
        // Cứ 2 giây quét lại danh sách 1 lần
        refreshTimer = new Timeline(new KeyFrame(Duration.seconds(2), e -> refreshData()));
        refreshTimer.setCycleCount(Timeline.INDEFINITE);
        refreshTimer.play();
    }

    @FXML
    public void refreshData() {
        List<OrderViewModel> list = new ArrayList<>();
        int pendingCount = 0;

        List<Session> runningSessions = AppContext.sessionRepo.findRunningSessions(); 

        for (Session session : runningSessions) {
            for (OrderItem order : session.getOrderItems()) {
                list.add(new OrderViewModel(session, order));
                
                if (order.getStatus() == OrderStatus.PENDING) {
                    pendingCount++;
                }
            }
        }

        // Sắp xếp: PENDING lên đầu, sau đó sắp xếp theo thời gian mới nhất
        list.sort(Comparator.comparing((OrderViewModel vm) -> vm.order.getStatus() == OrderStatus.PENDING ? 0 : 1)
                .thenComparing(vm -> vm.order.getOrderedAt(), Comparator.reverseOrder()));

        // Lưu lại dòng đang chọn (nếu có) để sau khi refresh không bị mất chọn (tùy ý đại ca, ở đây em cứ setAll)
        tableData.setAll(list);
        lblPendingCount.setText(String.valueOf(pendingCount));
    }
    
    public static class OrderViewModel {
        public Session session;
        public OrderItem order;

        public OrderViewModel(Session session, OrderItem order) {
            this.session = session;
            this.order = order;
        }
    }
}