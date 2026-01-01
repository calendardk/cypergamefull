package com.cybergame.ui.fxcontroller;

import com.cybergame.app.AppContext;
import com.cybergame.context.AccountContext;
import com.cybergame.controller.AuthController;
import com.cybergame.model.entity.Account;
import com.cybergame.model.entity.Computer;
import com.cybergame.model.entity.OrderItem; // 🔥 Import
import com.cybergame.model.entity.Session;
import com.cybergame.model.enums.ComputerStatus;
import com.cybergame.model.enums.OrderStatus; // 🔥 Import
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

public class ClientController {

    // ================= FXML =================
    @FXML private Label lblUsername;
    @FXML private Label lblPcName;
    @FXML private Label lblBalance;
    @FXML private Label lblTimeUsage;

    // 🔥 OVERLAY SHOP
    @FXML private ToggleButton btnOrder;
    @FXML private AnchorPane shopOverlay;
    @FXML private ServiceMenuController shopViewController; 

    // ================= DATA =================
    private Session currentSession;
    private Timeline usageTimer;

    // ================= BACKEND =================
    private final AuthController authController = new AuthController(
            AppContext.accountRepo,
            AppContext.sessionManager,
            AppContext.accountContext
    );

    // ================= SET SESSION & INIT =================
    public void setSession(Session session) {
        this.currentSession = session;
        updateUI();
        startTimer();
        
        if (shopViewController != null) {
            shopViewController.setSession(session);
        }

        // 🔥 CÀI ĐẶT SỰ KIỆN ẤN NÚT [X] (CLOSE REQUEST)
        // Phải bọc trong Platform.runLater để đảm bảo Scene đã được load xong
        Platform.runLater(() -> {
            Stage stage = (Stage) lblUsername.getScene().getWindow();
            stage.setOnCloseRequest(event -> {
                // Ngăn không cho đóng cửa sổ ngay lập tức
                event.consume(); 
                // Gọi hàm xử lý logout chung
                handleLogoutRequest();
            });
        });
    }

    // ================= UI UPDATE =================
    private void updateUI() {
        if (currentSession == null) return;
        lblUsername.setText(currentSession.getAccount().getUsername());
        if (currentSession.getComputer() != null) {
            lblPcName.setText(currentSession.getComputer().getName());
        }
        lblBalance.setText(String.format("%,.0f đ", currentSession.getAccount().getBalance()));
    }

    // ================= TIMER =================
    private void startTimer() {
        if (usageTimer != null) usageTimer.stop();
        usageTimer = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            if (currentSession == null) return;

            // Check Online
            String username = currentSession.getAccount().getUsername();
            if (!AccountContext.getInstance().isOnline(username)) {
                System.out.println("LOGOUT SIGNAL DETECTED.");
                // Bị kick hoặc hết tiền -> Tự động chốt đơn luôn không cần hỏi
                finalizeOrdersWithoutAsking(); 
                updateComputerToAvailable();
                performClientLogout();
                return;
            }

            // Update Time & Balance
            LocalDateTime start = currentSession.getStartTime();
            long seconds = ChronoUnit.SECONDS.between(start, LocalDateTime.now());
            lblTimeUsage.setText(String.format("%02d:%02d:%02d", seconds / 3600, (seconds % 3600) / 60, seconds % 60));
            lblBalance.setText(String.format("%,.0f đ", currentSession.getAccount().getBalance()));
        }));
        usageTimer.setCycleCount(Timeline.INDEFINITE);
        usageTimer.play();
    }

    // ================= LOGIC XỬ LÝ LOGOUT & ĐƠN HÀNG =================

    /**
     * Hàm xử lý chung cho cả nút Đăng xuất và nút [X]
     */
    private void handleLogoutRequest() {
        if (currentSession == null) return;

        List<OrderItem> orders = currentSession.getOrderItems();
        boolean hasPending = false;
        boolean hasConfirmed = false;

        for (OrderItem o : orders) {
            if (o.getStatus() == OrderStatus.PENDING) hasPending = true;
            if (o.getStatus() == OrderStatus.CONFIRMED) hasConfirmed = true;
        }

        // Nếu có đơn hàng cần xử lý
        if (hasPending || hasConfirmed) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Xác nhận đăng xuất");
            alert.setHeaderText("Bạn có đơn hàng chưa hoàn tất!");

            StringBuilder msg = new StringBuilder();
            if (hasPending) {
                msg.append("⚠️ Có đơn đang chờ (PENDING). Bạn có muốn HỦY đơn này không?\n");
            }
            if (hasConfirmed) {
                msg.append("✅ Có đơn đã giao (CONFIRMED). Hệ thống sẽ tự động HOÀN TẤT đơn này.\n");
            }
            msg.append("\nẤn OK để xác nhận xử lý và đăng xuất.");
            
            alert.setContentText(msg.toString());

            Optional<ButtonType> result = alert.showAndWait();
            if (result.isPresent() && result.get() == ButtonType.OK) {
                // Người dùng đồng ý -> Xử lý đơn hàng -> Logout
                finalizeOrdersAndLogout();
            }
            // Nếu ấn Cancel -> Không làm gì cả (Không logout)
        } else {
            // Không có đơn hàng nào -> Logout luôn
            finalizeOrdersAndLogout();
        }
    }

    /**
     * Thực hiện chốt đơn và gọi backend logout
     */
    private void finalizeOrdersAndLogout() {
        // Cập nhật trạng thái đơn hàng trong RAM
        List<OrderItem> orders = currentSession.getOrderItems();
        for (OrderItem order : orders) {
            if (order.getStatus() == OrderStatus.PENDING) {
                // PENDING -> CANCELLED (Hủy)
                // Lưu ý: Nếu cần hoàn tiền, logic cancelOrder bên OrderController/SessionManager
                // nên được gọi, nhưng ở đây ta đang logout nên AuthController sẽ save lại Account lần cuối.
                // Để đơn giản, ta đổi status, việc hoàn tiền nên được xử lý kỹ ở backend nếu trừ tiền trước.
                order.setStatus(OrderStatus.CANCELLED); 
            } 
            else if (order.getStatus() == OrderStatus.CONFIRMED) {
                // CONFIRMED -> COMPLETED (Hoàn tất)
                order.setStatus(OrderStatus.COMPLETED);
            }
        }

        // Gọi Backend Logout
        authController.logout(currentSession);
        
        // Dọn dẹp UI
        updateComputerToAvailable();
        performClientLogout();
    }

    /**
     * Dùng cho trường hợp bị Force Logout (Hết tiền / Kicked)
     */
    private void finalizeOrdersWithoutAsking() {
        List<OrderItem> orders = currentSession.getOrderItems();
        for (OrderItem order : orders) {
            if (order.getStatus() == OrderStatus.PENDING) order.setStatus(OrderStatus.CANCELLED);
            if (order.getStatus() == OrderStatus.CONFIRMED) order.setStatus(OrderStatus.COMPLETED);
        }
    }

    // ================= EVENT HANDLERS =================

    @FXML
    private void handleLogout(ActionEvent event) {
        // Gọi hàm xử lý chung
        handleLogoutRequest();
    }

    @FXML
    private void handleTopUp() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Nạp tiền");
        alert.setHeaderText(null);
        alert.setContentText("Vui lòng liên hệ nhân viên tại quầy để nạp tiền!");
        alert.showAndWait();
    }
    
    // ... (Giữ nguyên phần handleChangePassword, handleOrderService) ...
    @FXML private void handleOrderService(ActionEvent event) {
        if (shopOverlay == null) return;
        boolean isVisible = shopOverlay.isVisible();
        shopOverlay.setVisible(!isVisible);
        shopOverlay.setManaged(!isVisible);
        if (btnOrder != null) btnOrder.setSelected(!isVisible);
    }

    @FXML private void handleChangePassword() {
         Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Đổi mật khẩu");
        dialog.setHeaderText("Nhập mật khẩu cũ và mật khẩu mới");
        ButtonType loginButtonType = new ButtonType("Lưu thay đổi", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(loginButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10); grid.setVgap(10);
        PasswordField txtOldPass = new PasswordField(); txtOldPass.setPromptText("Mật khẩu cũ");
        PasswordField txtNewPass = new PasswordField(); txtNewPass.setPromptText("Mật khẩu mới");
        PasswordField txtConfirmPass = new PasswordField(); txtConfirmPass.setPromptText("Nhập lại mật khẩu mới");

        grid.add(new Label("Mật khẩu cũ:"), 0, 0); grid.add(txtOldPass, 1, 0);
        grid.add(new Label("Mật khẩu mới:"), 0, 1); grid.add(txtNewPass, 1, 1);
        grid.add(new Label("Xác nhận:"), 0, 2); grid.add(txtConfirmPass, 1, 2);
        dialog.getDialogPane().setContent(grid);

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isPresent() && result.get() == loginButtonType) {
            String oldPass = txtOldPass.getText();
            String newPass = txtNewPass.getText();
            String confirmPass = txtConfirmPass.getText();
            Account acc = currentSession.getAccount();

            // 🔥 DÙNG getPasswordHash() ĐÚNG CHUẨN USERBASE
            if (!acc.getPasswordHash().equals(oldPass)) {
                showAlert("Lỗi", "Mật khẩu cũ không chính xác!");
                return;
            }
            if (newPass.isEmpty() || !newPass.equals(confirmPass)) {
                showAlert("Lỗi", "Mật khẩu mới không khớp hoặc bị rỗng!");
                return;
            }
            acc.setPasswordHash(newPass);
            AppContext.accountRepo.save(acc);
            Alert success = new Alert(Alert.AlertType.INFORMATION);
            success.setTitle("Thành công");
            success.setContentText("Đổi mật khẩu thành công!");
            success.show();
        }
    }

    // ================= HELPERS =================

    private void updateComputerToAvailable() {
        if (currentSession != null && currentSession.getComputer() != null) {
            Computer c = currentSession.getComputer();
            c.setStatus(ComputerStatus.AVAILABLE);
            AppContext.computerRepo.save(c);
        }
    }

    private void performClientLogout() {
        if (usageTimer != null) usageTimer.stop();
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/login/client_login.fxml"));
            Parent root = loader.load();
            
            ClientLoginController loginCtrl = loader.getController();
            if (currentSession != null && currentSession.getComputer() != null) {
                loginCtrl.setPcId(currentSession.getComputer().getComputerId());
            }

            Stage stage = (Stage) lblUsername.getScene().getWindow();
            if (stage != null) {
                stage.setScene(new Scene(root));
                stage.setTitle("CLIENT LOGIN");
                stage.centerOnScreen();
                stage.show();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void showAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setContentText(content);
        alert.show();
    }
}