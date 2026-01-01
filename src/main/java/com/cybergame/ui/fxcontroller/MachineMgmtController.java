package com.cybergame.ui.fxcontroller;

import com.cybergame.controller.ComputerController;
import com.cybergame.model.entity.Computer;
import com.cybergame.model.enums.ComputerStatus;
import com.cybergame.repository.ComputerRepository;
import com.cybergame.repository.sql.ComputerRepositorySQL;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.Collectors;

public class MachineMgmtController {

    @FXML private FlowPane machineGrid;
    @FXML private TextField txtSearch;

    // Toggle Group để reset bộ lọc nếu cần
    @FXML private ToggleButton btnAll;
    @FXML private ToggleGroup filterGroup; // Để biết đang lọc theo cái nào

    private final ComputerRepository repo = new ComputerRepositorySQL();
    private final ComputerController controller = new ComputerController(repo);

    private final List<Computer> allMachines = new ArrayList<>();

    // Biến lưu trạng thái chọn
    private Computer selectedMachine;
    private VBox selectedCard;
    
    // Timer tự động refresh
    private Timeline refreshTimer;

    // ================= INIT =================
    @FXML
    public void initialize() {
        // Load lần đầu
        refreshData();
        
        // Bắt đầu auto-refresh (2 giây 1 lần để đỡ nặng DB hơn so với session list)
        startAutoRefresh();
    }
    
    // ================= AUTO REFRESH LOGIC =================
    
    private void startAutoRefresh() {
        refreshTimer = new Timeline(new KeyFrame(Duration.seconds(2), e -> {
            refreshData();
        }));
        refreshTimer.setCycleCount(Timeline.INDEFINITE);
        refreshTimer.play();
    }
    
    /**
     * Hàm trung tâm: Load DB -> Apply Filter -> Render
     * Giữ nguyên logic chọn (Selection)
     */
    private void refreshData() {
        // 1. Lưu lại ID máy đang chọn (nếu có)
        int selectedId = (selectedMachine != null) ? selectedMachine.getComputerId() : -1;

        // 2. Load lại toàn bộ từ DB
        allMachines.clear();
        allMachines.addAll(repo.findAll());

        // 3. Lọc lại theo UI hiện tại (Search text & Toggle Button)
        List<Computer> filteredList = applyCurrentFilters();
        
        // 4. Render lại giao diện
        render(filteredList);
        
        // 5. Khôi phục lại trạng thái chọn (nếu máy đó vẫn còn trong danh sách hiển thị)
        if (selectedId != -1) {
            restoreSelection(selectedId);
        }
    }

    // ================= RENDER =================
    private void render(List<Computer> list) {
        machineGrid.getChildren().clear();
        // Reset reference UI tạm thời (sẽ được restore ngay sau đó nếu tìm thấy ID)
        selectedCard = null; 

        for (Computer c : list) {
            VBox card = createCard(c);
            // Gắn ID vào UserData của Node để dễ tìm lại
            card.setUserData(c.getComputerId()); 
            machineGrid.getChildren().add(card);
        }
    }
    
    private void restoreSelection(int computerId) {
        for (javafx.scene.Node node : machineGrid.getChildren()) {
            if (node instanceof VBox && node.getUserData() instanceof Integer) {
                int id = (int) node.getUserData();
                if (id == computerId) {
                    // Tìm thấy máy cũ -> Set lại trạng thái chọn
                    VBox card = (VBox) node;
                    Computer c = allMachines.stream().filter(m -> m.getComputerId() == id).findFirst().orElse(null);
                    
                    if (c != null) {
                        selectedCard = card;
                        selectedMachine = c; // Update lại object mới nhất từ DB (để lỡ status thay đổi)
                        highlightCard(card);
                    }
                    break;
                }
            }
        }
    }

    // TẠO CARD GIAO DIỆN ĐẸP THEO CSS MACHINE
    private VBox createCard(Computer c) {
        VBox card = new VBox();
        card.getStyleClass().add("machine-card");

        // 1. Add class màu sắc dựa theo status
        String statusStyleClass = "status-offline"; // Default
        switch (c.getStatus()) {
            case AVAILABLE -> statusStyleClass = "status-available";
            case IN_USE -> statusStyleClass = "status-in_use";
            case PAUSED -> statusStyleClass = "status-paused";
            case MAINTENANCE -> statusStyleClass = "status-maintenance";
            case OFFLINE -> statusStyleClass = "status-offline";
        }
        card.getStyleClass().add(statusStyleClass);

        // 2. Icon to
        Label icon = new Label("🖥");
        icon.getStyleClass().add("big-icon");

        // 3. Tên máy
        Label name = new Label(c.getName());
        name.getStyleClass().add("machine-name");
        name.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");

        // 4. Trạng thái text
        Label statusLabel = new Label(c.getStatus().name());
        statusLabel.getStyleClass().add("status-label");
        statusLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 11px;");

        // 5. Giá tiền
        Label price = new Label(String.format("%,.0f đ/h", c.getPricePerHour()));
        price.getStyleClass().add("info-label");

        // Ghép vào card
        card.getChildren().addAll(name, icon, statusLabel, price);

        // Sự kiện click
        card.setOnMouseClicked(e -> selectCard(card, c));

        return card;
    }

    // XỬ LÝ CHỌN (TOGGLE)
    private void selectCard(VBox card, Computer c) {
        // Trường hợp 1: Click vào đúng cái đang chọn -> HỦY CHỌN
        if (selectedMachine != null && selectedMachine.getComputerId() == c.getComputerId()) {
            card.setStyle(""); 
            selectedMachine = null;
            selectedCard = null;
            return; 
        }

        // Trường hợp 2: Click vào cái mới
        if (selectedCard != null) {
            selectedCard.setStyle(""); // Reset cái cũ
        }

        selectedCard = card;
        selectedMachine = c;

        highlightCard(card);
    }
    
    private void highlightCard(VBox card) {
        // Highlight (Viền sáng màu trắng)
        card.setStyle("-fx-border-color: white; -fx-border-width: 3; -fx-background-color: #2c2d3b;");
    }

    // ================= FILTER LOGIC =================
    
    // Helper để lấy list đã lọc hiện tại
    private List<Computer> applyCurrentFilters() {
        String key = txtSearch.getText().trim().toLowerCase();
        
        // 1. Lọc theo text search trước
        List<Computer> list = allMachines.stream()
            .filter(c -> c.getName().toLowerCase().contains(key))
            .collect(Collectors.toList());

        // 2. Lọc theo Toggle Button
        ToggleButton selectedBtn = (ToggleButton) filterGroup.getSelectedToggle();
        if (selectedBtn != null) {
            String btnId = selectedBtn.getId();
            // Map ID button sang Status
            ComputerStatus targetStatus = null;
            if ("btnAvailable".equals(btnId)) targetStatus = ComputerStatus.AVAILABLE;
            else if ("btnInUse".equals(btnId)) targetStatus = ComputerStatus.IN_USE;
            else if ("btnPaused".equals(btnId)) targetStatus = ComputerStatus.PAUSED;
            else if ("btnMaintenance".equals(btnId)) targetStatus = ComputerStatus.MAINTENANCE;
            else if ("btnOffline".equals(btnId)) targetStatus = ComputerStatus.OFFLINE;
            
            // Nếu là btnAll hoặc null -> không lọc status
            if (targetStatus != null) {
                final ComputerStatus s = targetStatus;
                list = list.stream().filter(c -> c.getStatus() == s).collect(Collectors.toList());
            }
        }
        
        return list;
    }

    // Các hàm FXML chỉ cần gọi refreshData() là đủ, vì refreshData đã bao gồm logic filter
    @FXML private void filterAll() { refreshData(); }
    @FXML private void filterAvailable() { refreshData(); }
    @FXML private void filterInUse() { refreshData(); }
    @FXML private void filterPaused() { refreshData(); }
    @FXML private void filterMaintenance() { refreshData(); }
    @FXML private void filterOffline() { refreshData(); }
    @FXML private void handleSearch() { refreshData(); }

    // ================= ADD =================
    @FXML
    private void handleAdd() {
        Dialog<Computer> dialog = new Dialog<>();
        dialog.setTitle("Thêm máy");

        ButtonType btnAdd = new ButtonType("Tạo", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(btnAdd, ButtonType.CANCEL);

        TextField txtName = new TextField();
        TextField txtPrice = new TextField();

        GridPane grid = new GridPane();
        grid.setHgap(10); grid.setVgap(10);
        grid.addRow(0, new Label("Tên máy:"), txtName);
        grid.addRow(1, new Label("Giá / giờ:"), txtPrice);

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(btn -> {
            if (btn == btnAdd) {
                try {
                    Computer c = controller.createComputer(
                            txtName.getText(),
                            Double.parseDouble(txtPrice.getText())
                    );
                    c.setStatus(ComputerStatus.AVAILABLE);
                    return c;
                } catch (Exception e) {
                    alert("Lỗi nhập liệu: " + e.getMessage());
                }
            }
            return null;
        });

        dialog.showAndWait().ifPresent(c -> {
            // Sau khi thêm, gọi refresh để load lại từ DB
            refreshData();
        });
    }

    // ================= VIEW / EDIT =================
    @FXML
    private void handleView() {
        if (selectedMachine == null) {
            alert("Vui lòng chọn máy cần xem!");
            return;
        }

        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Thông tin: " + selectedMachine.getName());

        ButtonType btnSave = new ButtonType("Lưu", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(btnSave, ButtonType.CANCEL);

        TextField txtName = new TextField(selectedMachine.getName());
        TextField txtPrice = new TextField(String.valueOf(selectedMachine.getPricePerHour()));

        ComboBox<ComputerStatus> cbStatus = new ComboBox<>();
        cbStatus.getItems().addAll(EnumSet.complementOf(EnumSet.of(ComputerStatus.IN_USE)));
        cbStatus.setValue(selectedMachine.getStatus());

        GridPane grid = new GridPane();
        grid.setHgap(10); grid.setVgap(10);
        grid.addRow(0, new Label("Tên máy:"), txtName);
        grid.addRow(1, new Label("Giá / giờ:"), txtPrice);
        grid.addRow(2, new Label("Trạng thái:"), cbStatus);

        dialog.getDialogPane().setContent(grid);

        // NẾU ĐANG DÙNG -> CHỈ XEM
        if (selectedMachine.getStatus() == ComputerStatus.IN_USE) {
            txtName.setDisable(true);
            txtPrice.setDisable(true);
            cbStatus.setDisable(true);
            dialog.getDialogPane().lookupButton(btnSave).setDisable(true);
        }

        dialog.setResultConverter(btn -> {
            if (btn == btnSave) {
                selectedMachine.setName(txtName.getText());
                selectedMachine.setPricePerHour(Double.parseDouble(txtPrice.getText()));
                selectedMachine.setStatus(cbStatus.getValue());
                repo.save(selectedMachine);
                
                refreshData(); 
            }
            return null;
        });

        dialog.showAndWait();
    }

    // ================= DELETE =================
    @FXML
    private void handleDelete() {
        if (selectedMachine == null) {
            alert("Chưa chọn máy để xóa");
            return;
        }

        if (selectedMachine.getStatus() == ComputerStatus.IN_USE) {
            alert("Không thể xóa máy đang có khách chơi!");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setHeaderText("Xóa máy");
        confirm.setContentText("Bạn chắc chắn muốn xóa: " + selectedMachine.getName() + " ?");

        confirm.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.OK) {
                repo.delete(selectedMachine);
                refreshData();
            }
        });
    }

    private void alert(String msg) {
        Alert a = new Alert(Alert.AlertType.WARNING);
        a.setHeaderText(null);
        a.setContentText(msg);
        a.show();
    }
    
    public void stop() {
        if (refreshTimer != null) refreshTimer.stop();
    }
}