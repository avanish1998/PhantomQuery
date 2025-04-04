package com.aiinterview.ui;

import com.aiinterview.service.SystemAudioCaptureService;
import javafx.application.Platform;
import org.springframework.stereotype.Component;

import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import javax.swing.*;

@Component
public class SystemTrayUI {
    private final SystemAudioCaptureService audioCaptureService;
    private TrayIcon trayIcon;
    private final String ICON_STOPPED = "🔴"; // Red circle
    private final String ICON_RECORDING = "🟢"; // Green circle

    public SystemTrayUI(SystemAudioCaptureService audioCaptureService) {
        this.audioCaptureService = audioCaptureService;
    }

    public void initialize() {
        if (!SystemTray.isSupported()) {
            System.err.println("System tray is not supported");
            return;
        }

        SwingUtilities.invokeLater(() -> {
            try {
                // Create system tray menu
                PopupMenu popup = new PopupMenu();
                
                // Start/Stop menu item
                MenuItem startStopItem = new MenuItem("Start Recording");
                startStopItem.addActionListener(e -> toggleRecording(startStopItem));
                popup.add(startStopItem);
                
                // Exit menu item
                MenuItem exitItem = new MenuItem("Exit");
                exitItem.addActionListener(e -> exit());
                popup.add(exitItem);

                // Create tray icon
                trayIcon = new TrayIcon(createIcon(ICON_STOPPED), "AI Interview Assistant", popup);
                trayIcon.setImageAutoSize(true);

                // Add double-click behavior
                trayIcon.addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseClicked(MouseEvent e) {
                        if (e.getClickCount() == 2) {
                            toggleRecording(startStopItem);
                        }
                    }
                });

                // Add icon to system tray
                SystemTray.getSystemTray().add(trayIcon);
                
                trayIcon.displayMessage(
                    "AI Interview Assistant",
                    "Double-click the tray icon or use the menu to start/stop recording",
                    TrayIcon.MessageType.INFO
                );

            } catch (AWTException e) {
                System.err.println("Could not initialize system tray: " + e.getMessage());
            }
        });
    }

    private void toggleRecording(MenuItem menuItem) {
        if (!audioCaptureService.isCapturing()) {
            audioCaptureService.startCapture();
            trayIcon.setImage(createIcon(ICON_RECORDING));
            menuItem.setLabel("Stop Recording");
            trayIcon.displayMessage(
                "AI Interview Assistant",
                "Started recording system audio",
                TrayIcon.MessageType.INFO
            );
        } else {
            audioCaptureService.stopCapture();
            trayIcon.setImage(createIcon(ICON_STOPPED));
            menuItem.setLabel("Start Recording");
            trayIcon.displayMessage(
                "AI Interview Assistant",
                "Stopped recording system audio",
                TrayIcon.MessageType.INFO
            );
        }
    }

    private void exit() {
        if (audioCaptureService.isCapturing()) {
            audioCaptureService.stopCapture();
        }
        Platform.exit();
        System.exit(0);
    }

    private Image createIcon(String text) {
        // Create a simple text-based icon
        Font font = new Font("Dialog", Font.PLAIN, 24);
        BufferedImage image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = image.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2d.setFont(font);
        g2d.setColor(Color.BLACK);
        FontMetrics fm = g2d.getFontMetrics();
        int x = (32 - fm.stringWidth(text)) / 2;
        int y = ((32 - fm.getHeight()) / 2) + fm.getAscent();
        g2d.drawString(text, x, y);
        g2d.dispose();
        return image;
    }
} 