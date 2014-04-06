package seprini.models;

import java.util.ArrayList;
import java.util.Random;

import seprini.controllers.AircraftController;
import seprini.controllers.MultiplayerController;
import seprini.data.Config;
import seprini.data.Debug;
import seprini.data.GameMode;
import seprini.models.types.AircraftType;
import seprini.models.types.Player;
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
	private static final int INITIAL_POINTS = 50;
	private static final Vector2 SIZE = new Vector2(76, 63);
	private static final float SCALE = 0.5f;

	private final int id;

	private AircraftController controller;
	public ArrayList<Waypoint> waypoints;

	private final AircraftType aircraftType;

	private int desiredAltitude, altitude;

	private Vector2 velocity = new Vector2(0, 0);
	private boolean breaching, isActive, ignorePath, selected, landed,
			turnRight, turnLeft, rotateRight, breachingLastFrame;

	// used for smooth turning - remember last angle to check if it's increasing
	// or not.
	private float previousAngle = 0;

	// Set and store aircrafts points.
	private int points;

	private Player player;

	public Aircraft(AircraftType aircraftType, ArrayList<Waypoint> flightPlan,
			int id, AircraftController controller) {
		// allows drawing debug shape of this entity
		debugShape = true;

		this.controller = controller;
		this.id = id;
		this.aircraftType = aircraftType;

		Random rand = new Random();

		points = INITIAL_POINTS;

		// initialize entity
		texture = aircraftType.getTexture();

		// initialize velocity and altitude
		velocity = new Vector2(aircraftType.getInitialSpeed(), 0);

		altitude = Config.ALTITUDES[rand.nextInt(Config.ALTITUDES.length)];
		desiredAltitude = altitude;

		// set the flightplan to the generated by the controller
		waypoints = flightPlan;

		// define the size of the aircraft.
		size = SIZE;

		// set the coords to the entry point, remove it from the flight plan
		Waypoint entryPoint = waypoints.get(0);

		// set the player, depends on gamemode and where it spawns
		if (controller.getGameMode() == GameMode.SINGLE) {
			this.player = controller.getPlayers()[Player.ONE];
		} else {
			if (entryPoint.getCoords().x < Config.NO_MAN_LAND[0]) {
				this.player = controller.getPlayers()[Player.ONE];
			} else {
				this.player = controller.getPlayers()[Player.TWO];
			}
		}

		coords = new Vector2(entryPoint.getX(), entryPoint.getY());
		waypoints.remove(0);

		// set origin to center of the aircraft, makes rotation more intuitive
		this.setOrigin(size.x / 2, size.y / 2);

		this.setScale(SCALE);

		// set bounds so the aircraft is clickable
		this.setBounds(getX() - getWidth() / 2, getY() - getWidth() / 2,
				getWidth(), getHeight());

		// set rotation & velocity angle to fit next waypoint
		float relativeAngle = relativeAngleToWaypoint();

		this.velocity.setAngle(relativeAngle);
		this.setRotation(relativeAngle);

		// switch rotation sides so it uses the 'smaller' angle
		rotateRight = false;

		// When user has taken control of the aircraft
		ignorePath = false;

		landed = false;
		isActive = true;

		Debug.msg("||\nGenerated aircraft id " + id + "\nEntry point: "
				+ getCoords() + "\nRelative angle to first waypoint: "
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
			Vector2 previous = getCoords();

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

			AbstractScreen.drawCircle(getPlayer().getColor(), getX(), getY(),
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
	@Override
	public void act(float delta) {

		if (!isActive || landed)
			return;

		if (getCoords().x >= 540 && getCoords().x <= 740) {
			if (controller instanceof MultiplayerController) {
				((MultiplayerController) controller).deselectAircraft(this);
			}

		}
		// Handing over control from player one to player 2
		if (getCoords().x < 640) {
			this.player = controller.getPlayers()[Player.ONE];
		} else {
			this.player = controller.getPlayers()[Player.TWO];
		}

		// handle aircraft rotation
		rotateAircraft(delta);

		// update altitude
		updateAltitude(delta);

		// finally updating coordinates
		getCoords().add(velocity.cpy().scl(delta));

		// updating bounds to make sure the aircraft is clickable
		this.setBounds(getX() - getWidth() / 2, getY() - getWidth() / 2,
				getWidth(), getHeight());

		// test waypoint collisions
		try {
			testWaypointCollisions(controller);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		// test screen boundary
		if (isActive) {
			isOutOfBounds();
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
		Vector2 way = new Vector2(waypoint.x - getCoords().x, waypoint.y
				- getCoords().y).nor();
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

	public int getPoints() {
		return this.points;
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
	 * Tests whether this aircraft has collided with any waypoints and take
	 * appropriate action
	 * 
	 * @throws InterruptedException
	 */
	private void testWaypointCollisions(AircraftController controller)
			throws InterruptedException {

		if (getCoords().cpy().sub(getLastWaypoint().getCoords()).len() < Config.EXIT_WAYPOINT_SIZE.x / 2) {

			// Test if exit point is an airport, and add aircraft into
			// airport while removing it from the airspace.
			if (getLastWaypoint() instanceof Airport) {
				if (getLastWaypoint() instanceof Airport
						&& this.altitude > 1000 && this.waypoints.size() == 1) {
					// TODO: Reset flightplan and add landing waypoints to
					// flightplan if the flightplan is empty (?).
					this.insertWaypoint(((Airport) getLastWaypoint()).runwayStart);
					this.insertWaypoint(((Airport) getLastWaypoint()).runwayLeft);
					this.insertWaypoint(((Airport) getLastWaypoint()).runwayEnd);
					return;
				}

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
				this.isActive = false;
				return;

			}
			waypoints.remove(0);
			if (waypoints.isEmpty()) {
				this.isActive = false;
			}
			return;
		}
		if (getCoords().cpy().sub(getNextWaypoint().getCoords()).len() < Config.WAYPOINT_SIZE.x / 2) {
			// These checks concern the stages of the aircrafts approach to
			// the airport, incrementally decreasing speed and altitude.
			if (getLastWaypoint() instanceof Airport) {
				if (getNextWaypoint().equals(
						waypoints.get(waypoints.size() - 2))) {
					this.desiredAltitude = 0;
				} else if (getNextWaypoint().equals(
						waypoints.get(waypoints.size() - 3))) {
					this.desiredAltitude = 1000;
					this.setSpeed(200 / Config.AIRCRAFT_SPEED_MULTIPLIER);
				} else if (getNextWaypoint().equals(
						waypoints.get(waypoints.size() - 4))) {
					this.desiredAltitude = 3000;
					this.setSpeed(350 / Config.AIRCRAFT_SPEED_MULTIPLIER);
				} else if (getNextWaypoint().equals(
						waypoints.get(waypoints.size() - 5))) {
					this.desiredAltitude = 5000;
					this.setSpeed(350 / Config.AIRCRAFT_SPEED_MULTIPLIER);
				}
			}
			// for when aircraft is at any other waypoint.

			waypoints.remove(0);
			if (waypoints.isEmpty()) {
				this.isActive = false;
			}
		}

	}

	/**
	 * Checks whether the aircraft is out of bounds depending on game mode (due
	 * to the sidebar in SP)
	 * 
	 * @return
	 */
	private boolean isOutOfBounds() {

		int leftX = 0, leftY = 0, rightX = 0, rightY = 0;

		switch (controller.getGameMode()) {
		case SINGLE:

			leftX = -10;
			leftY = -10;
			rightX = -190;
			rightY = 105;

			break;
		case MULTI:

			leftX = -10;
			leftY = -10;
			rightX = 10;
			rightY = 10;

			break;
		}

		if (getX() < leftX || getY() < leftY
				|| getX() > Config.SCREEN_WIDTH + rightX
				|| getY() > Config.SCREEN_HEIGHT + rightY) {

			isActive = false;

			return true;

		}

		return false;
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

	private Waypoint getLastWaypoint() {
		return waypoints.get(waypoints.size() - 1);
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

	/**
	 * Setter for selected
	 * 
	 * @param newSelected
	 * @return whether is selected
	 */
	public boolean selected(boolean newSelected) {
		return this.selected = newSelected;
	}

	public void checkBreaching() {
		if (isBreaching()) {
			if (!breachingLastFrame) {
				points -= 5;
			}
		}
		breachingLastFrame = isBreaching();
	}

	public Player getPlayer() {
		return player;
	}

	public void takingOff() {
		this.isActive = true;
		this.landed = false;
		this.altitude = 0;
		this.desiredAltitude = Config.ALTITUDES[1];
		this.setSpeed(400 / Config.AIRCRAFT_SPEED_MULTIPLIER);
	}

	public boolean isTurningRight() {
		return turnRight;
	}

	public boolean isTurningLeft() {
		return turnLeft;
	}

	private int getId() {
		return id;
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

	@Override
	public boolean equals(Object object) {

		if (!(object instanceof Aircraft))
			return false;

		return this.id == ((Aircraft) object).getId();
	}

	@Override
	public String toString() {
		return "Aircraft " + id + " x: " + getX() + " y: " + getY()
				+ "\n\r flight plan: " + waypoints.toString();
	}

}
