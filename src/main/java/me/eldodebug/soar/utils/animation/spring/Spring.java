package me.eldodebug.soar.utils.animation.spring;

/**
 * Delta-time based spring used for motion that has to feel identical at any framerate.
 * The original SimpleAnimation steps by a fixed amount per frame, so the same animation
 * takes a different wall-clock time at 60 Hz and 240 Hz. This one integrates a damped
 * harmonic oscillator against real time instead.
 *
 * Integration is substepped so that low framerates cannot make the spring explode.
 */
public class Spring {

	private static final double MAX_STEP_SECONDS = 0.004D;
	private static final double MAX_FRAME_SECONDS = 0.1D;
	private static final double REST_EPSILON = 0.01D;

	private double value;
	private double velocity;
	private double target;

	private double omega = 22.0D;
	private double damping = 1.0D;

	public Spring() {
	}

	public Spring(double value) {
		this.value = value;
		this.target = value;
	}

	/**
	 * @param omega   angular frequency in radians per second (higher = faster)
	 * @param damping 1.0 is critically damped, below 1.0 overshoots slightly
	 */
	public Spring(double value, double omega, double damping) {
		this(value);
		this.omega = omega;
		this.damping = damping;
	}

	public void update(double deltaSeconds) {
		update(this.target, deltaSeconds);
	}

	public void update(double target, double deltaSeconds) {

		this.target = target;

		if (deltaSeconds <= 0.0D || Double.isNaN(deltaSeconds)) {
			return;
		}

		if (Double.isNaN(this.value) || Double.isNaN(this.velocity)) {
			this.value = target;
			this.velocity = 0.0D;
			return;
		}

		if (deltaSeconds > MAX_FRAME_SECONDS) {
			deltaSeconds = MAX_FRAME_SECONDS;
		}

		int steps = (int) Math.ceil(deltaSeconds / MAX_STEP_SECONDS);
		double step = deltaSeconds / Math.max(1, steps);

		for (int i = 0; i < steps; i++) {
			double acceleration = (-omega * omega * (value - target)) - (2.0D * damping * omega * velocity);

			velocity += acceleration * step;
			value += velocity * step;
		}

		if (Math.abs(value - target) < REST_EPSILON && Math.abs(velocity) < REST_EPSILON) {
			value = target;
			velocity = 0.0D;
		}
	}

	public float get() {
		return (float) value;
	}

	public double getValue() {
		return value;
	}

	public void setValue(double value) {
		this.value = value;
		this.target = value;
		this.velocity = 0.0D;
	}

	public double getTarget() {
		return target;
	}

	public void setTarget(double target) {
		this.target = target;
	}

	public void snap() {
		this.value = this.target;
		this.velocity = 0.0D;
	}

	public double getOmega() {
		return omega;
	}

	public void setOmega(double omega) {
		this.omega = omega;
	}

	public double getDamping() {
		return damping;
	}

	public void setDamping(double damping) {
		this.damping = damping;
	}
}
