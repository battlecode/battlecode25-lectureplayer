package lectureplayer;

import battlecode.common.*;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;

// import java.time.Clock;
// import apple.laf.JRSUIConstants.Direction;

/**
 * RobotPlayer is the class that describes your main robot strategy.
 * The run() method inside this class is like your main function: this is what we'll call once your robot
 * is created!
 */
public class RobotPlayer {
    private enum MessageType {
        SAVE_CHIPS
    }

    private enum RobotState {
        STARTING,
        PAINTING_PATTERN,
        EXPLORING,
        ATTACKING
    }

    /**
     * We will use this variable to count the number of turns this robot has been alive.
     * You can use static variables like this to save any information you want. Keep in mind that even though
     * these variables are static, in Battlecode they aren't actually shared between your robots.
     */
    static int turnCount = 0;
    
    /* Variables for pathfinding bug algorithms */
    //bug 1
    static boolean isTracing = false;
    static int smallestDistance = 10000000;
    static MapLocation closestLocation = null;
    static Direction tracingDir= null;
    //bug 2
    static MapLocation prevDest = null;
    static HashSet<MapLocation> line = null;
    static int obstacleStartDist = 0;

    static RobotState state = RobotState.STARTING;
    static MapLocation targetEnemyRuin = null;

    /* Variables for communication */
    static ArrayList<MapLocation> knownTowers = new ArrayList<>();
    static boolean isMessenger = false;
    static boolean shouldSave = false;
    static int saveTurns = 0;

    static boolean[][] paintTowerPattern = null;
    static boolean[][] moneyTowerPattern = null;

    static MapLocation paintingRuinLoc = null;
    static UnitType paintingTowerType = null;
    static int paintingTurns = 0;
    static int turnsWithoutAttack = 0;

    /**
     * A random number generator.
     * We will use this RNG to make some random moves. The Random class is provided by the java.util.Random
     * import at the top of this file. Here, we *seed* the RNG with a constant number (6147); this makes sure
     * we get the same sequence of numbers every time this code is run. This is very useful for debugging!
     */
    static final Random rng = new Random(6147);

    /** Array containing all the possible movement directions. */
    static final Direction[] directions = {
        Direction.NORTH,
        Direction.NORTHEAST,
        Direction.EAST,
        Direction.SOUTHEAST,
        Direction.SOUTH,
        Direction.SOUTHWEST,
        Direction.WEST,
        Direction.NORTHWEST,
    };

    /**
     * run() is the method that is called when a robot is instantiated in the Battlecode world.
     * It is like the main function for your robot. If this method returns, the robot dies!
     *
     * @param rc  The RobotController object. You use it to perform actions from this robot, and to get
     *            information on its current status. Essentially your portal to interacting with the world.
     **/
    @SuppressWarnings("unused")
    public static void run(RobotController rc) throws GameActionException {
        // Hello world! Standard output is very useful for debugging.
        // Everything you say here will be directly viewable in your terminal when you run a match!
        System.out.println("I'm alive");

        // You can also use indicators to save debug notes in replays.
        rc.setIndicatorString("Hello world!");

        // Assign messenger to about half of our moppers
        if (rc.getType() == UnitType.MOPPER && rc.getID() % 2 == 0) {
            isMessenger = true;
        }

        paintTowerPattern = rc.getTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER);
        moneyTowerPattern = rc.getTowerPattern(UnitType.LEVEL_ONE_MONEY_TOWER);

        while (true) {
            // This code runs during the entire lifespan of the robot, which is why it is in an infinite
            // loop. If we ever leave this loop and return from run(), the robot dies! At the end of the
            // loop, we call Clock.yield(), signifying that we've done everything we want to do.

            turnCount += 1;  // We have now been alive for one more turn!

            // Try/catch blocks stop unhandled exceptions, which cause your robot to explode.
            try {
                // The same run() function is called for every robot on your team, even if they are
                // different types. Here, we separate the control depending on the UnitType, so we can
                // use different strategies on different robots. If you wish, you are free to rewrite
                // this into a different control structure!
                switch (rc.getType()){
                    case SOLDIER: runSoldier(rc); break; 
                    case MOPPER: runMopper(rc); break;
                    case SPLASHER: break; // Consider upgrading examplefuncsplayer to use splashers!
                    default: runTower(rc); break;
                }
            }
             catch (GameActionException e) {
                // Oh no! It looks like we did something illegal in the Battlecode world. You should
                // handle GameActionExceptions judiciously, in case unexpected events occur in the game
                // world. Remember, uncaught exceptions cause your robot to explode!
                System.out.println("GameActionException");
                e.printStackTrace();

            } catch (Exception e) {
                // Oh no! It looks like our code tried to do something bad. This isn't a
                // GameActionException, so it's more likely to be a bug in our code.
                System.out.println("Exception");
                e.printStackTrace();

            } finally {
                // Signify we've done everything we want to do, thereby ending our turn.
                // This will make our code wait until the next turn, and then perform this loop again.
                Clock.yield();
            }
            // End of loop: go back to the top. Clock.yield() has ended, so it's time for another turn!
        }

        // Your code should never reach here (unless it's intentional)! Self-destruction imminent...
    }

    public static UnitType getNewTowerType(RobotController rc) {
        if(rc.getNumberTowers() < 4)
            return UnitType.LEVEL_ONE_MONEY_TOWER;
        return rc.getNumberTowers() % 2 == 1 ? UnitType.LEVEL_ONE_MONEY_TOWER : UnitType.LEVEL_ONE_PAINT_TOWER;
    }

    public static boolean getIsSecondary(MapLocation ruinLoc, MapLocation paintLoc, UnitType towerType) {
        if(!isWithinPattern(ruinLoc, paintLoc)) return false;
        int col = paintLoc.x - ruinLoc.x + 2;
        int row = paintLoc.y - ruinLoc.y + 2;
        return towerType == UnitType.LEVEL_ONE_PAINT_TOWER ? paintTowerPattern[row][col] : moneyTowerPattern[row][col];
    }

    public static boolean isWithinPattern(MapLocation ruinLoc, MapLocation paintLoc) {
        return Math.abs(paintLoc.x - ruinLoc.x) <= 2 && Math.abs(paintLoc.y - ruinLoc.y) <= 2 && !ruinLoc.equals(paintLoc);
    }

    /**
     * Run a single turn for towers.
     * This code is wrapped inside the infinite loop in run(), so it is called once per turn.
     */
    public static void runTower(RobotController rc) throws GameActionException{
        rc.setIndicatorString("SAVETURNS " + saveTurns);

        if (saveTurns == 0) {
            // If we have no save turns remaining, start building robots

            shouldSave = false;

            // Pick a direction to build in.
            Direction dir = directions[rng.nextInt(directions.length)];
            MapLocation nextLoc = rc.getLocation().add(dir);
            // Pick a random robot type to build.
            int robotType = rng.nextInt(3);

            rc.setIndicatorString("trying to build at " + nextLoc);

            if (robotType <= 2 && rc.canBuildRobot(UnitType.SOLDIER, nextLoc)){
                rc.buildRobot(UnitType.SOLDIER, nextLoc);
                System.out.println("BUILT A SOLDIER"); // for now, always build soldiers
            }
            // else if (robotType == 1 && rc.canBuildRobot(UnitType.MOPPER, nextLoc)){
            //     rc.buildRobot(UnitType.MOPPER, nextLoc);
            //     System.out.println("BUILT A MOPPER");
            // }
            // else if (robotType == 2 && rc.canBuildRobot(UnitType.SPLASHER, nextLoc)){
            //     // rc.buildRobot(UnitType.SPLASHER, nextLoc);
            //     // System.out.println("BUILT A SPLASHER");
            //     rc.setIndicatorString("SPLASHER NOT IMPLEMENTED YET");
            // }
        } else {
            // Otherwise, tick down the number of remaining save turns

            rc.setIndicatorString("Saving for " + saveTurns + " more turns");
            saveTurns--;
        }

        // Read incoming messages
        Message[] messages = rc.readMessages(-1);
        for (Message m : messages) {
            System.out.println("Tower received message: '#" + m.getSenderID() + " " + m.getBytes());

            // If we are not currently saving and we receive the save chips message, start saving
            if (!shouldSave && m.getBytes() == MessageType.SAVE_CHIPS.ordinal()) {
                // broadcast to other towers to save paint
                rc.broadcastMessage(MessageType.SAVE_CHIPS.ordinal());
                saveTurns = 75;
                shouldSave = true;
            }
        }

        // attack any nearby enemy robots
        RobotInfo[] enemyRobots = rc.senseNearbyRobots(-1, rc.getTeam().opponent());

        for (RobotInfo enemy : enemyRobots){
            if (rc.canAttack(enemy.location)){
                rc.attack(enemy.location);
                break;
            }
        }
    }


    /**
     * Run a single turn for a Soldier.
     * This code is wrapped inside the infinite loop in run(), so it is called once per turn.
     */
    public static void runSoldier(RobotController rc) throws GameActionException{
        if (state == RobotState.STARTING) {
            if(rc.getID() % 2 == 1) {
                state = RobotState.ATTACKING;
            }
            else {
                state = RobotState.EXPLORING;
            }
        }

        if(state == RobotState.PAINTING_PATTERN) {
            runPaintPattern(rc);
            paintingTurns++;
        }
        else if(state == RobotState.EXPLORING) {
            // Sense information about all visible nearby tiles.
            MapInfo[] nearbyTiles = rc.senseNearbyMapInfos();
            // Search for the closest nearby ruin to complete.
            MapInfo curRuin = null;
            int curDist = 9999999;
            for (MapInfo tile : nearbyTiles){
                // Make sure the ruin is not already complete (has no tower on it)
                if (tile.hasRuin() && rc.senseRobotAtLocation(tile.getMapLocation()) == null) {
                    int checkDist = tile.getMapLocation().distanceSquaredTo(rc.getLocation());
                    if (checkDist < curDist) {
                        curDist = checkDist;
                        curRuin = tile;
                    }
                }
            }
            
            // Based on ruin locations, move towards ruins and decide if we should try to start building a tower
            if(curRuin != null) {
                if(curDist > 4) bug0(rc, curRuin.getMapLocation());
                else {
                    state = RobotState.PAINTING_PATTERN;
                    paintingTowerType = getNewTowerType(rc);
                    turnsWithoutAttack = 0;
                    paintingTurns = 0;
                    paintingRuinLoc = curRuin.getMapLocation();
                }
            }

            // Move and attack randomly if no objective.
            Direction dir = directions[rng.nextInt(directions.length)];
            MapLocation nextLoc = rc.getLocation().add(dir);
            if (rc.canMove(dir)){
                rc.move(dir);
            }
            // Try to paint beneath us as we walk to avoid paint penalties.
            // Avoiding wasting paint by re-painting our own tiles.
            MapInfo currentTile = rc.senseMapInfo(rc.getLocation());
            if (!currentTile.getPaint().isAlly() && rc.canAttack(rc.getLocation())){
                rc.attack(rc.getLocation());
            }

            updateFriendlyTowers(rc);
            checkNearbyRuins(rc);
        }
        else if (state == RobotState.ATTACKING) {

            // If currently not tracking an enemy tower to attack, choose an enemy ruin to investigate

            if (targetEnemyRuin == null) {
                MapLocation[] infos = rc.senseNearbyRuins(-1);

                if (infos.length > 0) {
                    MapLocation ruin = infos[0];

                    MapLocation enemy = new MapLocation(ruin.x, rc.getMapHeight() - 1 - ruin.y);

                    targetEnemyRuin = enemy;
                }
            }

            if (targetEnemyRuin != null) {
                int dsquared = rc.getLocation().distanceSquaredTo(targetEnemyRuin);

                if (dsquared <= 8) {
                    // Attack the enemy
                    if (rc.canAttack(targetEnemyRuin)) {
                        rc.attack(targetEnemyRuin);
                    }

                    // Move away from the enemy ruin
                    
                    Direction away = rc.getLocation().directionTo(targetEnemyRuin).opposite();

                    if (rc.canMove(away)) {
                        rc.move(away);
                    } else if (rc.canMove(away.rotateLeft())) {
                        rc.move(away.rotateLeft());
                    } else if (rc.canMove(away.rotateRight())) {
                        rc.move(away.rotateRight());
                    }

                } else {
                    // Check if any adjacent tiles are within attack radius of the tower
                   
                    for (Direction d : directions) {
                        MapLocation newLoc = rc.getLocation().add(d);

                        if (newLoc.isWithinDistanceSquared(targetEnemyRuin, 8)) {
                            if (rc.canMove(d)) {
                                rc.move(d);

                                if (rc.canAttack(targetEnemyRuin)) {
                                    rc.attack(targetEnemyRuin);
                                }

                                break;
                            }
                        }
                    }

                    // Else too far, so move towards the enemy ruin
                    bug2(rc, targetEnemyRuin);
                }

                rc.setIndicatorDot(targetEnemyRuin, 0, 255, 0);
                rc.setIndicatorString("Moving to enemy ruin at " + targetEnemyRuin);
            }
        }

        // // Move and attack randomly if no objective.
        // Direction dir = directions[rng.nextInt(directions.length)];
        // MapLocation nextLoc = rc.getLocation().add(dir);
        // if (rc.canMove(dir)){
        //     rc.move(dir);
        // }

        

        // Try to paint beneath us as we walk to avoid paint penalties.
        // Avoiding wasting paint by re-painting our own tiles.
        MapInfo currentTile = rc.senseMapInfo(rc.getLocation());
        if (!currentTile.getPaint().isAlly() && rc.canAttack(rc.getLocation())){
            rc.attack(rc.getLocation());
        }
    }

    public static void runPaintPattern(RobotController rc) throws GameActionException {
           
        // Move in a circle around the ruin. Move every 3 turns after you have a chance to paint some tiles
        if(paintingTurns % 3 == 0) {
            Direction toRuin = rc.getLocation().directionTo(paintingRuinLoc);
            Direction tangent = toRuin.rotateRight().rotateRight();
            int distance = rc.getLocation().distanceSquaredTo(paintingRuinLoc);

            if(distance > 4) {
                tangent = tangent.rotateLeft();
            }

            if(rc.canMove(tangent)) rc.move(tangent);
        }

        // use helper functions to determine primary / secondary and paint a tile if possible
        if(rc.isActionReady()) {
            MapInfo[] infos = rc.senseNearbyMapInfos(3);
            boolean attacked = false;
            for(MapInfo info : infos) {
                MapLocation loc = info.getMapLocation();
                boolean isSecondary = getIsSecondary(paintingRuinLoc, loc, paintingTowerType);
                if(rc.canAttack(loc)
                    && (info.getPaint() == PaintType.EMPTY || info.getPaint().isSecondary() != isSecondary)
                    && isWithinPattern(paintingRuinLoc, loc)){

                    rc.attack(loc, isSecondary);
                    attacked = true;
                    turnsWithoutAttack = 0;
                    break;
                }
            }
            if(!attacked) turnsWithoutAttack++;
        }
        

        // check if we can build the tower, or if the pattern appears to be done. If so, transition back to exploring state.
        
        if (rc.canCompleteTowerPattern(paintingTowerType, paintingRuinLoc)) {
            rc.completeTowerPattern(paintingTowerType, paintingRuinLoc);
            state = RobotState.EXPLORING;
        }
        if (turnsWithoutAttack > 3) {
            state = RobotState.EXPLORING;
        }

    }


    /**
     * Run a single turn for a Mopper.
     * This code is wrapped inside the infinite loop in run(), so it is called once per turn.
     */
    public static void runMopper(RobotController rc) throws GameActionException{
        if (shouldSave && knownTowers.size() > 0) {
            // Move to first known tower if we are saving
            Direction dir = rc.getLocation().directionTo(knownTowers.get(0));
            rc.setIndicatorString("Returning to " + knownTowers.get(0));
            if (rc.canMove(dir)){
                rc.move(dir);
            }
        }

        // Move randomly if we haven't already
        Direction dir = directions[rng.nextInt(directions.length)];
        MapLocation nextLoc = rc.getLocation().add(dir);
        if (rc.canMove(dir)){
            rc.move(dir);
        }

        // Attack randomly
        if (rc.canMopSwing(dir)){
            rc.mopSwing(dir);
            System.out.println("Mop Swing! Booyah!");
        }
        else if (rc.canAttack(nextLoc)){
            rc.attack(nextLoc);
        }

        // We can also move our code into different methods or classes to better organize it!
        updateEnemyRobots(rc);

        if (isMessenger) {
            // Set a useful indicator at this mopper's location so we can see who is a messenger
            rc.setIndicatorDot(rc.getLocation(), 255, 0, 0);

            updateFriendlyTowers(rc);
            checkNearbyRuins(rc);
        }
    }

    public static void updateFriendlyTowers(RobotController rc) throws GameActionException{
        // Search for all nearby robots
        RobotInfo[] allyRobots = rc.senseNearbyRobots(-1, rc.getTeam());
        for (RobotInfo ally : allyRobots){
            // Only consider tower type
            if (!ally.getType().isTowerType())
                continue;

            MapLocation allyLoc = ally.location;
            if (knownTowers.contains(allyLoc)) {
                // Send a message to the nearby tower
                if (shouldSave && rc.canSendMessage(allyLoc)) {
                    rc.sendMessage(allyLoc, MessageType.SAVE_CHIPS.ordinal());
                    shouldSave = false;
                }

                // Skip adding to the known towers array
                continue;
            }

            // Add to our known towers array
            knownTowers.add(allyLoc);
            rc.setIndicatorString("Found tower " + ally.getID());
        }
    }

    public static void checkNearbyRuins(RobotController rc) throws GameActionException{
        // Search for nearby ruins
        MapInfo[] nearbyTiles = rc.senseNearbyMapInfos();
        for (MapInfo tile : nearbyTiles){
            MapLocation tileLoc = tile.getMapLocation();
            if (!tile.hasRuin() || rc.senseRobotAtLocation(tileLoc) != null)
                continue;

            // Heuristic to see if the ruin is trying to be built on
            MapLocation markLoc = tileLoc.add(tileLoc.directionTo(rc.getLocation()));
            MapInfo markInfo = rc.senseMapInfo(markLoc);
            if (!markInfo.getMark().isAlly()) 
                continue;

            shouldSave = true;

            // Return early
            return;
        }
    }

    public static void updateEnemyRobots(RobotController rc) throws GameActionException{
        // Sensing methods can be passed in a radius of -1 to automatically 
        // use the largest possible value.
        RobotInfo[] enemyRobots = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        if (enemyRobots.length != 0){
            rc.setIndicatorString("There are nearby enemy robots! Scary!");
            // Save an array of locations with enemy robots in them for possible future use.
            MapLocation[] enemyLocations = new MapLocation[enemyRobots.length];
            for (int i = 0; i < enemyRobots.length; i++){
                enemyLocations[i] = enemyRobots[i].getLocation();
            }
            RobotInfo[] allyRobots = rc.senseNearbyRobots(-1, rc.getTeam());
            // Occasionally try to tell nearby allies how many enemy robots we see.
            /*
            if (rc.getRoundNum() % 20 == 0){
                for (RobotInfo ally : allyRobots){
                    if (rc.canSendMessage(ally.location, enemyRobots.length)){
                        rc.sendMessage(ally.location, enemyRobots.length);
                    }
                }
            }
            */
        }
    }


    /*
     * Pathfinding algorithms: Bug0, Bug1, Bug2 (None are currently being called in the run function)
     * Navigates robot to desired target destination in the presence of obstacles
     */

    public static void bug0(RobotController rc, MapLocation target) throws GameActionException{
        // get direction from current location to target
        Direction dir = rc.getLocation().directionTo(target);

        MapLocation nextLoc = rc.getLocation().add(dir);
        rc.setIndicatorDot(nextLoc, 255, 0, 0);

        // try to move in the target direction
        if(rc.canMove(dir)){
            rc.move(dir);
        }

        // keep turning left until we can move
        for (int i=0; i<8; i++){
            dir = dir.rotateLeft();
            if(rc.canMove(dir)){
                rc.move(dir);
                break;
            }
        }
    }

    public static void bug1(RobotController rc, MapLocation target) throws GameActionException{
        if (!isTracing){ 
            //proceed as normal
            Direction dir = rc.getLocation().directionTo(target);
            MapLocation nextLoc = rc.getLocation().add(dir);
            rc.setIndicatorDot(nextLoc, 255, 0, 0);

            // try to move in the target direction
            if(rc.canMove(dir)){
                rc.move(dir);
            }
            else{
                isTracing = true;
                tracingDir = dir;
            }
        }
        else{
            // tracing mode
            
            // need a stopping condition - this will be when we see the closestLocation again
            if (rc.getLocation().equals(closestLocation)){
                // returned to closest location along perimeter of the obstacle
                isTracing = false;
                smallestDistance = 10000000;
                closestLocation = null;
                tracingDir= null;
            }
            else{
                // keep tracing

                // update closestLocation and smallestDistance
                int distToTarget = rc.getLocation().distanceSquaredTo(target);
                if(distToTarget < smallestDistance){
                    smallestDistance = distToTarget;
                    closestLocation = rc.getLocation();
                }

                // go along perimeter of obstacle
                if(rc.canMove(tracingDir)){
                    //move forward and try to turn right
                    rc.move(tracingDir);
                    tracingDir = tracingDir.rotateRight();
                    tracingDir = tracingDir.rotateRight();
                }
                else{
                    // turn left because we cannot proceed forward
                    // keep turning left until we can move again
                    for (int i=0; i<8; i++){
                        tracingDir = tracingDir.rotateLeft();
                        if(rc.canMove(tracingDir)){
                            rc.move(tracingDir);
                            tracingDir = tracingDir.rotateRight();
                            tracingDir = tracingDir.rotateRight();
                            break;
                        }
                    }
                }

                MapLocation nextLoc = rc.getLocation().add(tracingDir);
                rc.setIndicatorDot(nextLoc, 255, 0, 0);
            }
        }
    }

    public static void bug2(RobotController rc, MapLocation target) throws GameActionException{
        
        if(!target.equals(prevDest)) {
            prevDest = target;
            line = createLine(rc.getLocation(), target);
        }

        for(MapLocation loc : line) {
            rc.setIndicatorDot(loc, 255, 0, 0);
        }

        if(!isTracing) {
            Direction dir = rc.getLocation().directionTo(target);
            rc.setIndicatorDot(rc.getLocation().add(dir), 255, 0, 0);

            if(rc.canMove(dir)){
                rc.move(dir);
            } else {
                isTracing = true;
                obstacleStartDist = rc.getLocation().distanceSquaredTo(target);
                tracingDir = dir;
            }
        } else {
            if(line.contains(rc.getLocation()) && rc.getLocation().distanceSquaredTo(target) < obstacleStartDist) {
                isTracing = false;
            }

            for(int i = 0; i < 9; i++){
                if(rc.canMove(tracingDir)){
                    rc.move(tracingDir);
                    tracingDir = tracingDir.rotateRight();
                    tracingDir = tracingDir.rotateRight();
                    break;
                } else {
                    tracingDir = tracingDir.rotateLeft();
                }
            }
        }
    }

    // Bresenham's line algorithm for bug2
    public static HashSet<MapLocation> createLine(MapLocation a, MapLocation b) {
        HashSet<MapLocation> locs = new HashSet<>();
        int x = a.x, y = a.y;
        int dx = b.x - a.x;
        int dy = b.y - a.y;
        int sx = (int) Math.signum(dx);
        int sy = (int) Math.signum(dy);
        dx = Math.abs(dx);
        dy = Math.abs(dy);
        int d = Math.max(dx,dy);
        int r = d/2;
        if (dx > dy) {
            for (int i = 0; i < d; i++) {
                locs.add(new MapLocation(x, y));
                x += sx;
                r += dy;
                if (r >= dx) {
                    locs.add(new MapLocation(x, y));
                    y += sy;
                    r -= dx;
                }
            }
        }
        else {
            for (int i = 0; i < d; i++) {
                locs.add(new MapLocation(x, y));
                y += sy;
                r += dx;
                if (r >= dy) {
                    locs.add(new MapLocation(x, y));
                    x += sx;
                    r -= dy;
                }
            }
        }
        locs.add(new MapLocation(x, y));
        return locs;
    }
}
