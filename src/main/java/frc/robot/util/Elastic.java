// Copyright (c) 2023-2026 Gold87 and other Elastic contributors
// This software can be modified and/or shared under the terms
// defined by the Elastic license:
// https://github.com/Gold872/elastic_dashboard/blob/main/LICENSE

package frc.robot.util;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.wpi.first.networktables.NetworkTableInstance;
import edu.wpi.first.networktables.PubSubOption;
import edu.wpi.first.networktables.StringPublisher;
import edu.wpi.first.networktables.StringTopic;
import edu.wpi.first.wpilibj.DriverStation;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * ============================================================================
 * ELASTIC DASHBOARD NOTIFICATION UTILITY
 * ============================================================================
 * 
 * ONEMLI: Ilk sendNotification() cagrisi JVM class loading ve JIT compilation
 * nedeniyle 200-500ms gecikmeye neden olabilir. Bu, robot ana dongusunu
 * bloklayarak loop overrun'a yol acar.
 * 
 * COZUM:
 *   1) warmUp() metodunu robotInit()'te cagirin - class loading'i onceden yapar
 *   2) Match sirasinda sendNotificationAsync() kullanin - ana donguyu bloklamaz
 * 
 * Kullanim:
 *   // robotInit()'te:
 *   Elastic.warmUp();
 * 
 *   // Match sirasinda:
 *   Elastic.sendNotificationAsync(new Notification(...));
 * ============================================================================
 */
public final class Elastic {
    private static final StringTopic notificationTopic =
        NetworkTableInstance.getDefault().getStringTopic("/Elastic/RobotNotifications");
    private static final StringPublisher notificationPublisher =
        notificationTopic.publish(PubSubOption.sendAll(true), PubSubOption.keepDuplicates(true));
    private static final StringTopic selectedTabTopic =
        NetworkTableInstance.getDefault().getStringTopic("/Elastic/SelectedTab");
    private static final StringPublisher selectedTabPublisher =
        selectedTabTopic.publish(PubSubOption.keepDuplicates(true));
    private static final ObjectMapper objectMapper = new ObjectMapper();

    // ========================================================================
    // ASYNC EXECUTOR - Ana donguyu bloklamadan bildirim gonderir
    // ========================================================================
    private static final ExecutorService asyncExecutor = 
        Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "ElasticNotifier");
            t.setDaemon(true); // Robot kodu kapanirken thread'i otomatik sonlandirir
            return t;
        });

    private static volatile boolean isWarmedUp = false;

    public enum NotificationLevel {
        INFO,
        WARNING,
        ERROR
    }

    // ========================================================================
    // WARM-UP - robotInit()'te cagirin, class loading gecikmesini onler
    // ========================================================================
    /**
     * Elastic bildirim sistemini onceden isitirir (warm-up).
     * JVM class loading ve JIT compilation'i boot sirasinda yapar,
     * boylece match sirasinda ilk bildirim gecikmesi olmaz.
     * 
     * Bu metodu robotInit()'te cagirin.
     */
    public static void warmUp() {
        if (isWarmedUp) return;
        
        // Dummy bildirim gonder - class loading + JSON serialization'i tetikler
        sendNotification(
            new Notification(NotificationLevel.INFO, "System", "Elastic initialized")
                .withDisplayMilliseconds(1000)
        );
        
        isWarmedUp = true;
        System.out.println("[Elastic] Warm-up tamamlandi - bildirim sistemi hazir");
    }

    // ========================================================================
    // ASYNC NOTIFICATION - Match sirasinda kullanin (ana donguyu bloklamaz)
    // ========================================================================
    /**
     * Bildirimi asenkron olarak gonderir - ana robot dongusunu BLOKLAMAZ.
     * Match sirasinda (teleop/auto periodic) kullanin.
     * 
     * Thread-safe: Herhangi bir thread'den guvenle cagrilabilir.
     * Bildirimler sirali olarak islenir (tek thread).
     */
    public static void sendNotificationAsync(Notification notification) {
        asyncExecutor.submit(() -> {
            try {
                sendNotification(notification);
            } catch (Exception e) {
                // Bildirim basarisiz olursa robot kodunu CRASH ETTIRME
                DriverStation.reportWarning(
                    "[Elastic] Async bildirim hatasi: " + e.getMessage(), 
                    false
                );
            }
        });
    }

    // ========================================================================
    // SYNC NOTIFICATION - Orijinal metod (warm-up icin veya kritik olmayan)
    // ========================================================================
    public static void sendNotification(Notification notification) {
        try {
            notificationPublisher.set(objectMapper.writeValueAsString(notification));
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
    }

    public static void selectTab(String tabName) {
        selectedTabPublisher.set(tabName);
    }

    public static void selectTab(int tabIndex) {
        selectTab(Integer.toString(tabIndex));
    }

    public static class Notification {
        @JsonProperty("level")
        private NotificationLevel level;

        @JsonProperty("title")
        private String title;

        @JsonProperty("description")
        private String description;

        @JsonProperty("displayTime")
        private int displayTimeMillis;

        @JsonProperty("width")
        private double width;

        @JsonProperty("height")
        private double height;

        public Notification() {
            this(NotificationLevel.INFO, "", "");
        }

        public Notification(NotificationLevel level, String title, String description,
                            int displayTimeMillis, double width, double height) {
            this.level = level;
            this.title = title;
            this.displayTimeMillis = displayTimeMillis;
            this.description = description;
            this.height = height;
            this.width = width;
        }

        public Notification(NotificationLevel level, String title, String description) {
            this(level, title, description, 3000, 350, -1);
        }

        public Notification(NotificationLevel level, String title, String description,
                            int displayTimeMillis) {
            this(level, title, description, displayTimeMillis, 350, -1);
        }

        public Notification withLevel(NotificationLevel level) {
            this.level = level;
            return this;
        }

        public Notification withTitle(String title) {
            this.title = title;
            return this;
        }

        public Notification withDescription(String description) {
            this.description = description;
            return this;
        }

        public Notification withDisplaySeconds(double seconds) {
            this.displayTimeMillis = (int) Math.round(seconds * 1000);
            return this;
        }

        public Notification withDisplayMilliseconds(int millis) {
            this.displayTimeMillis = millis;
            return this;
        }

        public Notification withWidth(double width) {
            this.width = width;
            return this;
        }

        public Notification withHeight(double height) {
            this.height = height;
            return this;
        }

        public Notification withAutomaticHeight() {
            this.height = -1;
            return this;
        }

        public Notification withNoAutoDismiss() {
            this.displayTimeMillis = 0;
            return this;
        }

        // Getters
        public NotificationLevel getLevel() { return level; }
        public String getTitle() { return title; }
        public String getDescription() { return description; }
        public int getDisplayTimeMillis() { return displayTimeMillis; }
        public double getWidth() { return width; }
        public double getHeight() { return height; }
    }
}
