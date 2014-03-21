package seprini.models;

import java.util.ArrayList;
import java.util.Random;

import seprini.controllers.AircraftController;
import seprini.data.Config;
import seprini.data.Debug;
import seprini.models.types.AircraftType;
import seprini.screens.AbstractScreen;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;

public final class Aircraft extends Entity {

	private static final float SPEED_CHANGE = 6f;
	private static final int ALTITUDE_CHANGE = 5000;
	private static final Color LINE_COLOR = new Color(1, 0, 0, 0);
	private static final Color BREACHING_CIRCLE_COLOR = new Color(1, 0, 0, 0);
	private static final Vector2 TEXT_OFFSET = new Vector2(30, 20);
	private final int id;
	private AircraftController controller;
	public ArrayList<Waypoint> waypoints;
	private final AircraftType aircraftType;
	private int desiredAltitude;
	private int altitude;
	private Vector2 velocity = new Vector2(0, 0);
	private boolean breaching;
	private boolean breachingLastFrame;
	private boolean isActive = true;
	// When user has taken control of the aircraft
	private boolean ignorePath = false;
	// whether the aircraft is selected by the player
	private boolean selected;
	// Is the aircraft landed?
	private boolean landed = false;
	private boolean turnRight, turnLeft;
	// used for smooth turning - remember last angle to check if it's increasing
	// or not.
	private float previousAngle = 0;
	// if the above is increasing, switch rotation sides so it uses the
	// 'smaller' angle
	private boolean rotateRight = false;
	// Set and store aircrafts points.
	private int points = 10;

	public int getPoints() {
		return this.points;
	}

	public Aircraft(AircraftType aircraftType, ArrayList<Waypoint> flightPlan,
			int id, AircraftController controller) {

		// allows drawing debug shape of this entity
		debugShape = true;
		this.controller = controller;
		this.id = id;
		this.aircraftType = aircraftType;

		points = 50;

		// initialize entity
		texture = aircraftType.getTexture();

		// initialize velocity and altitude
		velocity = new Vector2(aircraftType.getInitialSpeed(), 0);

		Random rand = new Random();
		altitude = Config.ALTITUDES[rand.nextInt(Config.ALTITUDES.length)];
		desiredAltitude = altitude;

		// set the flightplan to the generated by the controller
		waypoints = flightPlan;

		// define the size of the aircraft.
		size = new Vector2(76, 63);

		// set the coords to the entry point, remove it from the flight plan
		Waypoint entryPoint = waypoints.get(0);
		coords = new Vector2(entryPoint.getX(), entryPoint.getY());
		waypoints.remove(0);

		// set origin to center of the aircraft, makes rotation more intuitive
		this.setOrigin(size.x / 2, size.y / 2);

		this.setScale(0.5f);

		// set bounds so the aircraft is clickable
		this.setBounds(getX() - getWidth() / 2, getY() - getWidth() / 2,
				getWidth(), getHeight());

		// set rotation & velocity angle to fit next waypoint
		float relativeAngle = relativeAngleToWaypoint();

		this.velocity.setAngle(relativeAngle);
		this.setRotation(relativeAngle);

		Debug.msg("||\nGenerated aircraft id " + id + "\nEntry point: "
				+ coords + "\nRelative angle to first waypoint: "
				+ relativeAngle + "\nVelocity" + velocity + "\nWaypoints: "
				+ waypoints + "\n||");
	}

	/**
	 * Additional drawing for if the aircraft is breaching or is required to
	 * land
	 * 
	 * @param batch
	 */
	@Override
	protected void additionalDraw(SpriteBatch batch) {

		// if the user takes control of the aircraft,
		// show full flight plan.
		if (selected) {
			// Initialises previous to plane's current position.
			Vector2 previous = coords;

			batch.end();

			// Loops through waypoints in flight plan drawing a line between
			// them
			for (Waypoint waypoint : waypoints) {
				Vector2 coords = waypoint.getCoords();

				AbstractScreen.drawLine(LINE_COLOR, previous.x, previous.y,
						coords.x, coords.y, null);

				previous = coords;
			}

			batch.begin();
		}

		// if the aircraft is either selected or is breaching, draw a circle
		// around it
		if (selected || breaching) {

			AbstractScreen.drawCircle(BREACHING_CIRCLE_COLOR, getX(), getY(),
					getSeparationRadius() * 0.5f, batch);

		}

		// draw the altitude for each aircraft
		Color color;

		if (getAltitude() <= 7500) {
			color = Color.GREEN;
		} else if (getAltitude() <= 12500) {
			color = Color.ORANGE;
		} else if (getAltitude() > 12500) {
			color = Color.RED;
		} else {
			color = Color.BLACK;
		}

		AbstractScreen.drawString("alt: " + getAltitude(), getX()
				- TEXT_OFFSET.x, getY() - TEXT_OFFSET.y, color, batch, true, 1);

		// debug line from aircraft centre to waypoint centre
		if (Config.DEBUG_UI) {
			if (waypoints.size() > 0) {
				Vector2 nextWaypoint = vectorToWaypoint();

				AbstractScreen.drawLine(LINE_COLOR, getX(), getY(),
						nextWaypoint.x, nextWaypoint.y, batch);

			}
		}

	}

	/**
	 * Update the aircraft rotation & position
	 * 
	 * @param
	 */
	public void act(float delta) {

		if (!isActive || landed)
			return;

		// handle aircraft rotation
		rotateAircraft(delta);

		// update altitude
		updateAltitude(delta);

		// finally updating coordinates
		coords.add(velocity.cpy().scl(delta));

		// updating bounds to make sure the aircraft is clickable
		this.setBounds(getX() - getWidth() / 2, getY() - getWidth() / 2,
				getWidth(), getHeight());

		// finally, test waypoint collisions using new coordinates
		testWaypointCollisions(controller);

		// test screen boundary
		if (isActive) {
			if (getX() < -10 || getY() < -10
					|| getX() > Config.SCREEN_WIDTH - 190
					|| getY() > Config.SCREEN_HEIGHT + 105) {

				isActive = false;
				Debug.msg("Aircraft id " + id
						+ ": Out of bounds, last coordinates: " + coords);
			}
		}

		checkBreaching();
	}

	/**
	 * Calculate the angle between the aircraft's coordinates and the vector the
	 * next waypoint
	 * 
	 * @param waypoint
	 * @return angle IN DEGREES, NOT RADIANS
	 */
	private float angleCoordsToWaypoint(Vector2 waypoint) {
		Vector2 way = new Vector2(waypoint.x - coords.x, waypoint.y - coords.y)
				.nor();
		Vector2 coord = velocity.cpy().nor();

		float angle = (float) Math.acos(way.dot(coord) / way.len()
				* coord.len())
				* MathUtils.radiansToDegrees;

		return angle;
	}

	/**
	 * Calculates the vector to the next waypoint
	 * 
	 * @return 3d vector to the next waypoint
	 */
	private Vector2 vectorToWaypoint() {
		// Creates a new vector to store the new velocity in temporarily
		Vector2 nextWaypoint = new Vector2();

		// round it to 2 points after decimal, makes it more manageable later
		nextWaypoint.x = (float) (Math
				.round(waypoints.get(0).getCoords().x * 100.0) / 100.0);
		nextWaypoint.y = (float) (Math
				.round(waypoints.get(0).getCoords().y * 100.0) / 100.0);

		return nextWaypoint;
	}

	/**
	 * Calculate relative angle of the aircraft to the next waypoint
	 * 
	 * @return relative angle in degrees, rounded to 2 points after decimal
	 */
	private float relativeAngleToWaypoint() {
		return relativeAngleToWaypoint(vectorToWaypoint());
	}

	/**
	 * Calculate relative angle of the aircraft to a waypoint
	 * 
	 * @param waypoint
	 * @return angle in degrees, rounded to 2 points after decimal
	 */
	private float relativeAngleToWaypoint(Vector2 waypoint) {
		return new Vector2(waypoint.x - getX(), waypoint.y - getY()).angle();
	}

	/**
	 * Handles aircraft rotation during the act method call
	 * 
	 * @param delta
	 *            time step
	 */
	private void rotateAircraft(float delta) {
		float baseRate = aircraftType.getMaxTurningSpeed() * delta;
		float rate = 0;

		// Calculate turning rate and give manual control to user
		if (turnRight) {
			ignorePath = true;
			rate = -baseRate;
		} else if (turnLeft) {
			ignorePath = true;
			rate = baseRate;
		} else if (!ignorePath && waypoints.size() > 0) {
			// Vector to next waypoint
			Vector2 nextWaypoint = vectorToWaypoint();

			// relative angle from the aircraft coordinates to the next waypoint
			float relativeAngle = angleCoordsToWaypoint(nextWaypoint);

			// smoothly rotate aircraft
			// sets a threshold due to float imprecision, should be generally
			// relativeAngle != 0
			if (relativeAngle > 1) {

				// if the current angle is bigger than the previous, it means we
				// are rotating towards the wrong side
				if (previousAngle < relativeAngle) {
					// switch to rotate to the other side
					rotateRight = (!rotateRight);
				}

				// instead of using two rotation variables, it is enough to
				// store one and just switch that one
				if (rotateRight) {
					rate = -baseRate;
				} else {
					rate = baseRate;
				}

				// save the current angle as the previous angle for the next
				// iteration
				previousAngle = relativeAngle;
			}
		}

		// Do the turning (while handling wraparound)
		if (rate != 0) {
			float newRotation = getRotation() + rate;
			if (newRotation < 0) {
				newRotation += 360;
			} else if (newRotation > 360) {
				newRotation -= 360;
			}

			setRotation(newRotation);
			velocity.setAngle(getRotation());
		}
	}

	/**
	 * Updates the altitude according to the current desiredAltitude value
	 */
	private void updateAltitude(float delta) {
		float maxAmount = aircraftType.getMaxClimbRate() * delta;

		// Move altitude value at most maxAmount units
		if (desiredAltitude > altitude) {
			altitude += maxAmount;
			if (altitude > desiredAltitude)
				altitude = desiredAltitude;
		} else if (desiredAltitude < altitude) {
			altitude -= maxAmount;
			if (altitude < desiredAltitude)
				altitude = desiredAltitude;
		}
	}

	/**
	 * Tests whether this aircraft has collided with any waypoints
	 */
	private void testWaypointCollisions(AircraftController controller) {
		int numWaypoints = waypoints.size();

		if (numWaypoints > 0) {
			float distanceToWaypoint = coords.cpy()
					.sub(waypoints.get(0).getCoords()).len();

			if (numWaypoints == 1) {
				if (distanceToWaypoint < Config.EXIT_WAYPOINT_SIZE.x / 2) {
					// Collided with exit point
					Debug.msg("Aircraft id " + id + ": Reached exit WP");

					// Test if exit point is an airport, and add aircraft into
					// airport
					if (getNextWaypoint() instanceof Airport) {
						try {
							((Airport) getNextWaypoint()).insertAircraft(this);
						} catch (IllegalStateException e) {
							controller.collisionHasOccured(this, this);
						}
						Waypoint airport = getNextWaypoint();
						ArrayList<Waypoint> newFlightPlan = controller.flightplan
								.generate(airport);
						waypoints.clear();
						waypoints = newFlightPlan;
					}

					isActive = false;
				}
			} else {
				if (distanceToWaypoint < Config.WAYPOINT_SIZE.x / 2) {
					// Collided with normal waypoint
					// AircraftController.score += 10;
					Debug.msg("Aircraft id " + id + ": Hit waypoint");
					// Sets aircraft speed to 0 if it has reached the middle of
					// runway.
					// Only occurs when landing as runwayMid can only be part of
					// a landing flight plan.

					// Waypoint runwayMid = new Waypoint(387, 335, false);
					// if (waypoints.get(0).getCoords()
					// .equals(runwayMid.getCoords())) {
					// this.setSpeed(0.00000000001f);
					// this.altitude = 0;
					// this.landed = true;
					// }

					waypoints.remove(0);
				}
			}
		}
	}

	/**
	 * Adding a new waypoint to the head of the arraylist
	 * 
	 * @param newWaypoint
	 */
	public void insertWaypoint(Waypoint newWaypoint) {
		waypoints.add(0, newWaypoint);
	}

	public Waypoint getNextWaypoint() {
		return waypoints.get(0);
	}

	/**
	 * Increase speed of the aircraft <br>
	 * Actually changes a scalar which is later multiplied by the velocity
	 * vector
	 */
	public void increaseSpeed() {

		float prevSpeed = getSpeed();
		float newSpeed = prevSpeed + SPEED_CHANGE;

		if (newSpeed > aircraftType.getMaxSpeed())
			newSpeed = aircraftType.getMaxSpeed();

		setSpeed(newSpeed);
		Debug.msg("Increasing speed; New Speed: " + newSpeed);
	}

	/**
	 * Decrease speed of the aircraft <br>
	 * Actually changes a scalar which is later multiplied by the velocity
	 * vector
	 */
	public void decreaseSpeed() {

		float prevSpeed = getSpeed();
		float newSpeed = prevSpeed - SPEED_CHANGE;

		if (newSpeed < aircraftType.getMinSpeed())
			newSpeed = aircraftType.getMinSpeed();

		setSpeed(newSpeed);
		Debug.msg("Decreasing speed; New Speed: " + newSpeed);
	}

	/**
	 * Increases altitude smoothly
	 */
	public void increaseAltitude() {
		if (desiredAltitude + ALTITUDE_CHANGE > 15000)
			return;

		this.desiredAltitude += ALTITUDE_CHANGE;
	}

	/**
	 * Decreasing altitude smoothly
	 */
	public void decreaseAltitude() {
		if (desiredAltitude - ALTITUDE_CHANGE < 5000)
			return;

		this.desiredAltitude -= ALTITUDE_CHANGE;
	}

	public boolean isTurningRight() {
		return turnRight;
	}

	public boolean isTurningLeft() {
		return turnLeft;
	}

	public void turnRight(boolean set) {
		if (set)
			turnLeft = false;
		turnRight = set;
	}

	public void turnLeft(boolean set) {
		if (set)
			turnRight = false;
		turnLeft = set;
	}

	/**
	 * Causes the aircraft to return to its flightplan after being manually
	 * controlled
	 */
	public void returnToPath() {
		turnLeft = false;
		turnRight = false;
		ignorePath = false;
	}

	public ArrayList<Waypoint> getFlightPlan() {
		return waypoints;
	}

	/**
	 * Regular regular getter for radius
	 * 
	 * @return int radius
	 */
	public float getRadius() {
		return aircraftType.getRadius();
	}

	public float getSeparationRadius() {
		return aircraftType.getSeparationRadius();
	}

	public boolean isBreaching() {
		return breaching;
	}

	public void setBreaching(boolean is) {
		this.breaching = is;
	}

	public int getAltitude() {
		return altitude;
	}

	/**
	 * Sets the speed of the aircraft (ignoring minimum and maximum speeds)
	 * 
	 * @param speed
	 *            new speed
	 */
	private void setSpeed(float speed) {
		if (speed == 0)
			throw new IllegalArgumentException("speed cannot be 0");

		velocity.clamp(speed, speed);
	}

	/**
	 * Returns aircraft speed (pixels per second)
	 * 
	 * @return the velocity scalar
	 */
	public float getSpeed() {
		return velocity.len();
	}

	/**
	 * Returns false if aircraft has hit the exit point or if it is off the
	 * screen
	 * 
	 * @return whether is active
	 */
	public boolean isActive() {
		return isActive;
	}

	/**
	 * Setter for selected
	 * 
	 * @param newSelected
	 * @return whether is selected
	 */
	public boolean selected(boolean newSelected) {
		return this.selected = newSelected;
	}

	@Override
	public String toString() {
		return "Aircraft - x: " + getX() + " y: " + getY()
				+ "\n\r flight plan: " + waypoints.toString();
	}

	public void checkBreaching() {
		if (isBreaching()) {
			if (!breachingLastFrame) {
				points -= 5;
			}
		}
		breachingLastFrame = isBreaching();
	}

	public void takingOff() {
		this.isActive = true;
		this.landed = false;
		this.altitude = 0;
		this.desiredAltitude = Config.ALTITUDES[1];
		this.setSpeed(400 / Config.AIRCRAFT_SPEED_MULTIPLIER);
	}
}
