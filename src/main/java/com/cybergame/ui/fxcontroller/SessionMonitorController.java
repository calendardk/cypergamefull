package com.cybergame.ui.fxcontroller;

import com.cybergame.app.AppContext;
import com.cybergame.model.entity.Session;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.util.Duration;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;

public class SessionMonitorController {

    @FXML private TableView<Session> sessionTable;

    @FXML private TableColumn<Session, String> colPc;
    @FXML private TableColumn<Session, String> colAccount;
    @FXML private TableColumn<Session, String> colStart;
    @FXML private TableColumn<Session, String> colDuration;
    @FXML private TableColumn<Session, String> colTotal;
    @FXML private TableColumn<Session, String> colStatus;
    
    // 🔥 Cột hành động chứa nút Đăng xuất
    @FXML private TableColumn<Session, Void> colAction;

    @FXML private Label lblTotalSession;

    private final DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("HH:mm:ss");
    private final ObservableList<Session> tableData = FXCollections.observableArrayList();
    private Timeline refreshTimer;

    @FXML
    public void initialize() {
        setupColumns();
        sessionTable.setItems(tableData);
        startAutoRefresh();

        // --- [NEW] CẤU HÌNH GIAO DIỆN DARK MODE & TOGGLE CLICK ---
        setupRowSelection();
        Platform.runLater(() -> sessionTable.getSelectionModel().clearSelection());
    }

    private void setupRowSelection() {
        // 1. Set màu nền tối cho cả bảng
        sessionTable.setStyle("-fx-control-inner-background: #0d1b2a; -fx-base: #0d1b2a; -fx-background-color: #0d1b2a;");

        sessionTable.setRowFactory(tv -> {
            TableRow<Session> row = new TableRow<>();

            // 2. Logic Toggle: Ấn lại vào dòng đang chọn thì bỏ chọn
            row.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
                if (!row.isEmpty() && row.isSelected() && e.getButton() == MouseButton.PRIMARY) {
                    sessionTable.getSelectionModel().clearSelection();
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
        colPc.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().getComputer().getName()));
        colPc.setCellFactory(tc -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null); setStyle("");
                } else {
                    setText(item);
                    // Màu tím nhạt cho tên máy (dễ nhìn)
                    setStyle("-fx-text-fill: #a78bfa; -fx-font-weight: bold; -fx-alignment: CENTER;");
                }
            }
        });

        colAccount.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().getAccount().getUsername()));
        
        colStart.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().getStartTime().format(timeFmt)));

        colDuration.setCellValueFactory(c -> {
            long seconds = ChronoUnit.SECONDS.between(c.getValue().getStartTime(), LocalDateTime.now());
            return new ReadOnlyStringWrapper(String.format("%02d:%02d:%02d", seconds / 3600, (seconds % 3600) / 60, seconds % 60));
        });

        colTotal.setCellValueFactory(c -> {
            Session s = c.getValue();
            double total = s.calcTimeCost() + s.calcServiceTotalFromAccount() + s.calcServiceTotalCash();
            return new ReadOnlyStringWrapper(String.format("%,.0f đ", total));
        });
        colTotal.setCellFactory(tc -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null); setStyle("");
                } else {
                    setText(item);
                    // Màu vàng cho Tổng tiền (nổi trên nền tối và nền xanh dương)
                    setStyle("-fx-text-fill: #fbbf24; -fx-font-weight: bold; -fx-alignment: CENTER-RIGHT;");
                }
            }
        });

        colStatus.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().getStatus().name()));
        colStatus.setCellFactory(tc -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null); setStyle("");
                } else {
                    setText(item);
                    // Màu xanh lá cho Status đang chạy
                    setStyle("-fx-text-fill: #2ecc71; -fx-font-weight: bold; -fx-alignment: CENTER;");
                }
            }
        });

        // 🔥 TẠO NÚT ĐĂNG XUẤT (FORCE LOGOUT)
        colAction.setCellFactory(param -> new TableCell<>() {
            private final Button btnLogout = new Button("Đăng Xuất");

            {
                btnLogout.setStyle("-fx-background-color: #ef4444; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 11px; -fx-cursor: hand;");
                btnLogout.setOnAction(event -> {
                    Session session = getTableView().getItems().get(getIndex());
                    handleForceLogout(session);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    setGraphic(btnLogout);
                    setAlignment(javafx.geometry.Pos.CENTER);
                }
            }
        });
    }

    private void handleForceLogout(Session session) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Xác nhận đăng xuất");
        alert.setHeaderText("Bạn có chắc muốn đăng xuất máy: " + session.getComputer().getName() + "?");
        alert.setContentText("Tài khoản khách sẽ bị đăng xuất ngay lập tức.");

        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                // Gọi hàm nghiệp vụ Force Logout
                AppContext.sessionManager.forceLogout(session);
                
                // Refresh ngay lập tức để người dùng thấy máy biến mất
                refresh();
            }
        });
    }

    private void startAutoRefresh() {
        refreshTimer = new Timeline(new KeyFrame(Duration.seconds(1), e -> refresh()));
        refreshTimer.setCycleCount(Timeline.INDEFINITE);
        refreshTimer.play();
    }

    private void refresh() {
        List<Session> sessions = AppContext.sessionRepo.findRunningSessions();
        // Lưu dòng đang chọn
        Session selected = sessionTable.getSelectionModel().getSelectedItem();
        
        tableData.setAll(sessions);
        sessionTable.refresh();
        
        // Cố gắng chọn lại dòng cũ nếu nó vẫn còn trong danh sách (để không bị mất focus khi auto refresh)
        if (selected != null) {
            if (sessions.contains(selected)) {
                sessionTable.getSelectionModel().select(selected);
            }
        }
        
        if (lblTotalSession != null) {
            lblTotalSession.setText(String.valueOf(sessions.size()));
        }
    }
    
    public void stop() {
        if (refreshTimer != null) refreshTimer.stop();
    }
}