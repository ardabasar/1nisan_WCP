package frc.robot.commands.auto;

import com.ctre.phoenix6.swerve.SwerveRequest;

import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.wpilibj.Timer;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
import edu.wpi.first.wpilibj2.command.Command;

import frc.robot.subsystems.CommandSwerveDrivetrain;
import frc.robot.subsystems.VisionSubsystem;

/**
 * ============================================================================
 * VISION CORRECT COMMAND - Otonom Path Arasi Odometry Duzeltme
 * ============================================================================
 *
 * Path'ler arasinda robotu DURDURUR ve en iyi veriyi almaya calisir:
 *   - AprilTag gorunuyorsa → vision ile odometry'i duzeltir (en dogru veri)
 *   - AprilTag gorunmuyorsa → odometry'nin stabilize olmasini bekler
 *
 * Her iki durumda da en dogru veriyle sonraki path'e baslar.
 *
 * ONCELIK SIRASI:
 *   1) Vision update geldi (tag goruldu) → EN IYI, odometry duzeltildi
 *   2) Vision yok ama odometry stabil → kabul edilebilir, devam et
 *   3) Timeout → ne olursa olsun devam et
 * ============================================================================
 */
public class VisionCorrectCommand extends Command {

    private final CommandSwerveDrivetrain drivetrain;
    private final VisionSubsystem vision;

    private final double minWaitSeconds;
    private final double maxWaitSeconds;

    // Odometry stability thresholds (vision yoksa kullanilir)
    private static final double POSITION_STABLE_THRESHOLD = 0.02; // metre
    private static final double HEADING_STABLE_THRESHOLD = 1.0;   // derece
    private static final int STABLE_CYCLES_REQUIRED = 5;

    private final SwerveRequest.Idle idle = new SwerveRequest.Idle();
    private final Timer timer = new Timer();

    private int initialUpdateCount;
    private Pose2d lastPose;
    private int stableCycleCount = 0;
    private boolean visionCorrected = false;
    private boolean odometryStable = false;

    public VisionCorrectCommand(
            CommandSwerveDrivetrain drivetrain,
            VisionSubsystem vision,
            double minWaitSeconds,
            double maxWaitSeconds) {
        this.drivetrain = drivetrain;
        this.vision = vision;
        this.minWaitSeconds = minWaitSeconds;
        this.maxWaitSeconds = maxWaitSeconds;

        addRequirements(drivetrain);
    }

    @Override
    public void initialize() {
        timer.restart();
        visionCorrected = false;
        odometryStable = false;
        stableCycleCount = 0;
        initialUpdateCount = vision.getVisionUpdateCount();
        lastPose = drivetrain.getState().Pose;

        drivetrain.setControl(idle);
        SmartDashboard.putString("VisionCorrect/Status", "Waiting...");
    }

    @Override
    public void execute() {
        drivetrain.setControl(idle);

        Pose2d currentPose = drivetrain.getState().Pose;

        // --- 1) AprilTag goruldu mu? (en iyi veri) ---
        int currentCount = vision.getVisionUpdateCount();
        int newUpdates = currentCount - initialUpdateCount;
        if (newUpdates >= 2 && timer.hasElapsed(minWaitSeconds)) {
            visionCorrected = true;
        }

        // --- 2) Odometry stabil mi? (tag yoksa fallback) ---
        double dx = currentPose.getX() - lastPose.getX();
        double dy = currentPose.getY() - lastPose.getY();
        double positionDelta = Math.sqrt(dx * dx + dy * dy);
        double headingDelta = Math.abs(
            currentPose.getRotation().getDegrees() - lastPose.getRotation().getDegrees());

        if (positionDelta < POSITION_STABLE_THRESHOLD && headingDelta < HEADING_STABLE_THRESHOLD) {
            stableCycleCount++;
        } else {
            stableCycleCount = 0;
        }

        if (stableCycleCount >= STABLE_CYCLES_REQUIRED && timer.hasElapsed(minWaitSeconds)) {
            odometryStable = true;
        }

        lastPose = currentPose;

        // Dashboard
        SmartDashboard.putNumber("VisionCorrect/VisionUpdates", newUpdates);
        SmartDashboard.putNumber("VisionCorrect/PosDelta", positionDelta);
        SmartDashboard.putNumber("VisionCorrect/StableCycles", stableCycleCount);
        SmartDashboard.putNumber("VisionCorrect/Time", timer.get());
    }

    @Override
    public boolean isFinished() {
        // Oncelik 1: Vision duzeltme geldi → en iyi sonuc
        if (visionCorrected) {
            SmartDashboard.putString("VisionCorrect/Status", "VISION CORRECTED");
            return true;
        }
        // Oncelik 2: Tag yok ama odometry stabil → kabul edilebilir
        if (odometryStable) {
            SmartDashboard.putString("VisionCorrect/Status", "ODOMETRY STABLE");
            return true;
        }
        // Oncelik 3: Timeout
        if (timer.hasElapsed(maxWaitSeconds)) {
            SmartDashboard.putString("VisionCorrect/Status", "TIMEOUT");
            return true;
        }
        return false;
    }

    @Override
    public void end(boolean interrupted) {
        timer.stop();
        String result = visionCorrected ? "DONE (vision)"
                       : odometryStable ? "DONE (odo stable)"
                       : interrupted ? "Interrupted"
                       : "DONE (timeout)";
        SmartDashboard.putString("VisionCorrect/Status", result);
    }
}
