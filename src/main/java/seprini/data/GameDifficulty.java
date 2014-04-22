package seprini.data;

/**
 * Class containing fields configuring the game's difficulty
 */
public class GameDifficulty {
	// maxAircraft, timeBetweenGenerations, separationRadius, scoreMultiplier
	public static final GameDifficulty EASY = new GameDifficulty(10, 4, 150,
			0.5, 500, 10);
	public static final GameDifficulty MEDIUM = new GameDifficulty(10, 3, 100,
			1, 500, 5);
	public static final GameDifficulty HARD = new GameDifficulty(10, 2, 100,
			1.5, 500, 3);

	private final int maxAircraft, timeBetweenGenerations, separationRadius,
			verticalSeparationRadius, timeBetweenScoreDecrement;
	private final double scoreMultiplier;

	/**
	 * Initializes a new game difficulty
	 * 
	 * @param maxAircraft
	 *            maximum number of aircraft
	 * @param timeBetweenGenerations
	 *            minimum time between new aircraft
	 * @param separationRadius
	 *            separation radius between aircraft
	 * @param scoreMultiplier
	 *            score multiplier
	 * @param verticalSeparationRadius
	 *            vertical separation distance
	 */
	public GameDifficulty(int maxAircraft, int timeBetweenGenerations,
			int separationRadius, double scoreMultiplier,
			int verticalSeparationRadius, int timeBetweenScoreDecrement) {
		this.maxAircraft = maxAircraft;
		this.timeBetweenGenerations = timeBetweenGenerations;
		this.separationRadius = separationRadius;
		this.scoreMultiplier = scoreMultiplier;
		this.verticalSeparationRadius = verticalSeparationRadius;
		this.timeBetweenScoreDecrement = timeBetweenScoreDecrement;
	}

	/** Returns the maximum number of aircraft allowed */
	public int getMaxAircraft() {
		return maxAircraft;
	}

	/** Returns the minimum time between new aircraft */
	public int getTimeBetweenGenerations() {
		return timeBetweenGenerations;
	}

	/** Returns the separation radius between aircraft */
	public int getSeparationRadius() {
		return separationRadius;
	}

	/** Returns the score multiplier */
	public double getScoreMultiplier() {
		return scoreMultiplier;
	}

	/**
	 * @return the verticalSeparationRadius
	 */
	public int getVerticalSeparationRadius() {
		return verticalSeparationRadius;
	}

	/** Returns the timer interval for the score decrease in multiplayer */
	public int getTimeBetweenScoreDecrement() {
		return timeBetweenScoreDecrement;
	}
}
