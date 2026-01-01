package com.cybergame.ui.fxcontroller;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.Optional;

public class ManagerController {

    @FXML
    private VBox sidebar;

    @FXML
    private StackPane contentArea;

    private boolean sidebarVisible = true;

    /* ====== LOAD MẶC ĐỊNH ====== */
    @FXML
    public void initialize() {
        showOverview();
    }

    /* ====== SIDEBAR TOGGLE ====== */
    @FXML
    private void handleToggleMenu() {
        sidebarVisible = !sidebarVisible;
        sidebar.setManaged(sidebarVisible);
        sidebar.setVisible(sidebarVisible);
    }

    /* ====== XỬ LÝ ĐĂNG XUẤT ====== */
    @FXML
    private void handleLogout() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Đăng xuất");
        alert.setHeaderText(null);
        alert.setContentText("Đại ca có chắc muốn đăng xuất khỏi quyền Admin không?");

        Optional<ButtonType> result = alert.showAndWait();
        
        if (result.isPresent() && result.get() == ButtonType.OK) {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/login/staff_login.fxml"));
                Parent loginRoot = loader.load();
                Stage stage = (Stage) contentArea.getScene().getWindow();
                Scene scene = new Scene(loginRoot);
                stage.setScene(scene);
                stage.centerOnScreen();
                stage.setTitle("Cyber Cafe Management - Login");
            } catch (IOException e) {
                e.printStackTrace();
                new Alert(Alert.AlertType.ERROR, "Lỗi: Không tìm thấy file login.fxml!").show();
            }
        }
    }

    /* ====== ĐIỀU HƯỚNG ====== */
    @FXML
    private void showOverview() {
        loadView("/fxml/admin/admin_overview.fxml");
    }

    @FXML
    private void showMachineMgmt() {
        loadView("/fxml/admin/machine_mgmt.fxml");
    }

    @FXML
    private void showEmployeeMgmt() {
        loadView("/fxml/admin/employee_mgmt.fxml");
    }

    @FXML
    private void showCustomerMgmt() {
        loadView("/fxml/admin/customer_mgmt.fxml");
    }

    @FXML
    private void showSessionMgmt() {
        loadView("/fxml/admin/session_mgmt.fxml");
    }

    @FXML
    private void showProductMgmt() {
        loadView("/fxml/admin/product_mgmt.fxml");
    }

    // 🔥 HÀM MỚI THÊM: HIỂN THỊ MÀN HÌNH ORDER REQUEST
    @FXML
    private void showOrderRequest() {
        loadView("/fxml/staff/order_request.fxml");
    }

    @FXML
    private void showRevenueReport() {
        loadView("/fxml/admin/revenue_report.fxml");
    }

    /* ====== CORE LOADER ====== */
    private void loadView(String path) {
        try {
            Parent view = FXMLLoader.load(getClass().getResource(path));
            contentArea.getChildren().setAll(view);
        } catch (Exception e) {
            System.err.println("Không thể tải view: " + path);
            e.printStackTrace();
        }
    }
}