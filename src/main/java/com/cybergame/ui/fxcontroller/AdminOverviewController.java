package com.cybergame.ui.fxcontroller;

import com.cybergame.context.AccountContext;
import com.cybergame.model.entity.Computer;
import com.cybergame.model.entity.Invoice;
import com.cybergame.model.entity.OrderItem;
import com.cybergame.model.entity.TopUpHistory;
import com.cybergame.model.enums.ComputerStatus; // Import đúng Enum
import com.cybergame.model.enums.OrderStatus;
import com.cybergame.model.enums.PaymentSource;
import com.cybergame.repository.sql.*;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.util.Duration;

import java.net.URL;
import java.time.LocalDate;
import java.util.List;
import java.util.ResourceBundle;

public class AdminOverviewController implements Initializable {

    // --- FXML UI ---
    @FXML private Label lblActiveMachines; 
    @FXML private Label lblTodayRevenue;   
    @FXML private Label lblTodayTopUp;     
    @FXML private Label lblCustomers;      
    @FXML private Label lblTotalStaff;     

    // --- REPOSITORIES ---
    private final ComputerRepositorySQL computerRepo = new ComputerRepositorySQL();
    private final TopUpHistoryRepositorySQL topUpRepo = new TopUpHistoryRepositorySQL();
    private final InvoiceRepositorySQL invoiceRepo = new InvoiceRepositorySQL();
    private final AccountRepositorySQL accountRepo = new AccountRepositorySQL();
    private final EmployeeRepositorySQL employeeRepo = new EmployeeRepositorySQL();

    private Timeline autoRefreshTimer;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        loadData();
        setupAutoRefresh();
    }

    private void setupAutoRefresh() {
        autoRefreshTimer = new Timeline(new KeyFrame(Duration.seconds(3), e -> {
            loadData();
        }));
        autoRefreshTimer.setCycleCount(Animation.INDEFINITE);
        autoRefreshTimer.play();
    }

    public void stopTimer() {
        if (autoRefreshTimer != null) autoRefreshTimer.stop();
    }

    private void loadData() {
        new Thread(() -> {
            LocalDate today = LocalDate.now();

            // 1. MÁY TRẠM
            List<Computer> computers = computerRepo.findAll();
            long totalMachines = computers.size();
            
            // 🔥 SỬA 1: Dùng ComputerStatus.IN_USE
            long activeMachines = computers.stream()
                    .filter(c -> c.getStatus() == ComputerStatus.IN_USE) 
                    .count(); 

            // 2. KHÁCH HÀNG
            long totalMembers = accountRepo.findAll().size();
            
            // 🔥 SỬA 2: Dùng getAll().size() vì AccountContext của ông có hàm getAll()
            long onlineMembers = AccountContext.getInstance().getAll().size();

            // 3. NHÂN VIÊN
            long totalStaff = employeeRepo.findAll().size();

            // 4. DOANH THU
            double todayTopUp = topUpRepo.findAll().stream()
                    .filter(t -> t.getCreatedAt().toLocalDate().isEqual(today))
                    .mapToDouble(TopUpHistory::getAmount)
                    .sum();

            double todayServiceCash = 0;
            List<Invoice> invoices = invoiceRepo.findAll();
            for (Invoice inv : invoices) {
                if (inv.getCreatedAt().toLocalDate().isEqual(today) && inv.getOrderItems() != null) {
                    todayServiceCash += inv.getOrderItems().stream()
                            .filter(item -> item.getStatus() == OrderStatus.COMPLETED)
                            .filter(item -> item.getPaymentSource() == PaymentSource.CASH)
                            .mapToDouble(OrderItem::getCost)
                            .sum();
                }
            }
            double realRevenue = todayTopUp + todayServiceCash;

            long finalActive = activeMachines;
            long finalOnline = onlineMembers;

            Platform.runLater(() -> {
                lblActiveMachines.setText(String.format("%d / %d", finalActive, totalMachines));
                lblCustomers.setText(String.format("%d / %d", finalOnline, totalMembers));
                lblTotalStaff.setText(String.format("%02d", totalStaff));
                lblTodayTopUp.setText(String.format("%,.0f đ", todayTopUp));
                lblTodayRevenue.setText(String.format("%,.0f đ", realRevenue));
            });

        }).start();
    }
}