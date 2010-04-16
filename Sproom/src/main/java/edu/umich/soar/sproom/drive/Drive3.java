package edu.umich.soar.sproom.drive;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import april.jmat.LinAlg;
import april.jmat.MathUtil;
import april.lcmtypes.pose_t;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import edu.umich.soar.sproom.HzChecker;
import edu.umich.soar.sproom.command.CommandConfig;
import edu.umich.soar.sproom.command.CommandConfigListener;
import edu.umich.soar.sproom.command.Pose;
import edu.umich.soar.sproom.drive.DifferentialDriveCommand.CommandType;

/**
 * Higher level drive command abstraction adding heading input.
 *
 * @author voigtjr@gmail.com
 */
public class Drive3 implements DriveListener {
	private static final Log logger = LogFactory.getLog(Drive3.class);
	public static final String HEADING_PID_NAME = "heading";
	
	public static Drive3 newInstance(Pose poseProvider) {
		return new Drive3(poseProvider);
	}

	private final Drive2 drive2 = Drive2.newInstance();
	private final PIDController hController = new PIDController(HEADING_PID_NAME);
	private long utimePrev;
	private DifferentialDriveCommand.CommandType previousType;
	private double previousHeading;
	private final Pose poseProvider;
	private DifferentialDriveCommand currentCommand = DifferentialDriveCommand.newEStopCommand();
	private final ScheduledExecutorService schexec = Executors.newSingleThreadScheduledExecutor();
	private final HzChecker hzChecker = HzChecker.newInstance(Drive3.class.toString());

	private Drive3(Pose poseProvider) {
		this.poseProvider = poseProvider;
		
		updateGains();
		CommandConfig.CONFIG.addListener(new CommandConfigListener() {
			@Override
			public void configChanged() {
				updateGains();
			}
		});

		schexec.scheduleAtFixedRate(controlLoop, 0, 33, TimeUnit.MILLISECONDS); // 30 Hz
	}
 
	private Runnable controlLoop = new Runnable() {
		@Override
		public void run() {
			if (logger.isDebugEnabled()) {
				hzChecker.tick();
			}
			
			long utimeElapsed = 0;
			pose_t pose = poseProvider.getPose();
			if (pose != null) {
				utimeElapsed = pose.utime - utimePrev;
				utimePrev = pose.utime;
				
				if (currentCommand == null) {
					logger.warn("ddc null, sending estop");
					drive2.estop();
				} else {
					
					switch (currentCommand.getType()) {
					case ESTOP:
						drive2.estop();
						break;
						
					case MOTOR:
						drive2.setMotors(currentCommand.getLeft(), currentCommand.getRight());
						break;
						
					case VEL:
						drive2.setAngularVelocity(currentCommand.getAngularVelocity());
						drive2.setLinearVelocity(currentCommand.getLinearVelocity());
						break;
						
					case LINVEL:
						drive2.setAngularVelocity(0);
						drive2.setLinearVelocity(currentCommand.getLinearVelocity());
						break;
						
					case ANGVEL:
						drive2.setAngularVelocity(currentCommand.getAngularVelocity());
						drive2.setLinearVelocity(0);
						break;
						
					case HEADING_LINVEL:
						drive2.setLinearVelocity(currentCommand.getLinearVelocity());
						doHeading(utimeElapsed, pose);
						break;
						
					case HEADING:
						drive2.setLinearVelocity(0);
						doHeading(utimeElapsed, pose);
						break;
					}
				
					previousType = currentCommand.getType();
				}
			}

			drive2.update(pose, utimeElapsed);
		}
	};
	
	private void doHeading(long utimeElapsed, pose_t pose) {
		if (previousType != CommandType.HEADING || Double.compare(previousHeading, currentCommand.getHeading()) != 0) {
			hController.clearIntegral();
			previousHeading = currentCommand.getHeading();
		}
		
		double target = MathUtil.mod2pi(currentCommand.getHeading());
		double actual = MathUtil.mod2pi(LinAlg.quatToRollPitchYaw(pose.orientation)[2]);
		double dt = utimeElapsed / 1000000.0;
		double out = hController.computeMod2Pi(dt, target, actual);
		drive2.setAngularVelocity(out);
	}
	
	private void updateGains() {
		double[] pid = CommandConfig.CONFIG.getGains(hController.getName());
		hController.setGains(pid);
	}
	
	@Override
	public void handleDriveEvent(DifferentialDriveCommand ddc) {
		if (ddc == null) {
			DifferentialDriveCommand.newEStopCommand();
		}
		currentCommand = ddc;
		logger.debug("current: " + currentCommand);
	}
}
