import random
from enum import IntEnum

from battlecode25.stubs import *

# This is an example bot written by the developers!
# Use this to help write your own code, or run it against your bot to see how well you can do!

class MessageType(IntEnum):
    SAVE_CHIPS = 0

class RobotState(IntEnum):
    STARTING = 0
    PAINTING_PATTERN = 1
    EXPLORING = 2
    ATTACKING = 3

# Globals
turn_count = 0
directions = [
    Direction.NORTH,
    Direction.NORTHEAST,
    Direction.EAST,
    Direction.SOUTHEAST,
    Direction.SOUTH,
    Direction.SOUTHWEST,
    Direction.WEST,
    Direction.NORTHWEST,
]

# Variables for communication
known_towers = []
is_messenger = False
should_save = False
save_turns = 0

# Bug2 Variables
prev_dest = MapLocation(100000, 100000)
line = set()
is_tracing = False
obstacle_start_dist = 0
tracing_dir = None

state = RobotState.STARTING
target_enemy_ruin = None

paint_tower_pattern = None
money_tower_pattern = None

painting_ruin_loc = None
painting_tower_type = None
painting_turns = 0
turns_without_attack = 0

def turn():
    global paint_tower_pattern, money_tower_pattern
    """
    MUST be defined for robot to run
    This function will be called at the beginning of every turn and should contain the bulk of your robot commands
    """
    global turn_count
    global is_messenger

    turn_count += 1

    if paint_tower_pattern is None:
        paint_tower_pattern = get_tower_pattern(UnitType.LEVEL_ONE_PAINT_TOWER)
        money_tower_pattern = get_tower_pattern(UnitType.LEVEL_ONE_MONEY_TOWER)

    # Assign messenger to about half of our moppers
    if get_type() == UnitType.MOPPER and get_id() % 2 == 0:
        is_messenger = True

    if get_type() == UnitType.SOLDIER:
        run_soldier()
    elif get_type() == UnitType.MOPPER:
        run_mopper()
    elif get_type() == UnitType.SPLASHER:
        pass  # TODO
    elif get_type().is_tower_type():
        run_tower()
    else:
        pass  # Other robot types?

def get_new_tower_type():
    if get_num_towers() < 4:
        return UnitType.LEVEL_ONE_MONEY_TOWER
    return UnitType.LEVEL_ONE_MONEY_TOWER if get_num_towers() % 2 == 1 else UnitType.LEVEL_ONE_PAINT_TOWER

def get_is_secondary(ruin_loc, paint_loc, tower_type):
    global paint_tower_pattern, money_tower_pattern

    if not is_within_pattern(ruin_loc, paint_loc):
        return False
    col = paint_loc.x - ruin_loc.x + 2
    row = paint_loc.y - ruin_loc.y + 2
    if tower_type == UnitType.LEVEL_ONE_PAINT_TOWER:
        return paint_tower_pattern[row][col]
    return money_tower_pattern[row][col]

def is_within_pattern(ruin_loc, paint_loc):
    return (abs(paint_loc.x - ruin_loc.x) <= 2 and
            abs(paint_loc.y - ruin_loc.y) <= 2 and
            ruin_loc != paint_loc)

def run_tower():
    global save_turns
    global should_save

    if save_turns == 0:
        # If we have no save turns remaining, start building robots
        should_save = False

        # Pick a direction to build in.
        dir = directions[random.randint(0, len(directions) - 1)]
        next_loc = get_location().add(dir)

        # Pick a random robot type to build.
        robot_type = random.randint(0, 2)
        if robot_type <= 2 and can_build_robot(UnitType.SOLDIER, next_loc):
            build_robot(UnitType.SOLDIER, next_loc) #for now, always build soldiers
            log("BUILT A SOLDIER")
        # if robot_type == 1 and can_build_robot(UnitType.MOPPER, next_loc):
        #     build_robot(UnitType.MOPPER, next_loc)
        #     log("BUILT A MOPPER")
        # if robot_type == 2 and can_build_robot(UnitType.SPLASHER, next_loc):
        #     set_indicator_string("SPLASHER NOT IMPLEMENTED YET")
        #     #build_robot(RobotType.SPLASHER, next_loc)
        #     #log("BUILT A SPLASHER")
    else:
        # Otherwise, tick down the number of remaining save turns
        set_indicator_string(f"Saving for {save_turns} more turns")
        save_turns -= 1

    # Read incoming messages
    messages = read_messages()
    for m in messages:
        log(f"Tower received message: '#{m.get_sender_id()}: {m.get_bytes()}'")

        # If we are not currently saving and we receive the save chips message, start saving
        if not should_save and m.get_bytes() == int(MessageType.SAVE_CHIPS):
            broadcast_message(int(MessageType.SAVE_CHIPS))
            save_turns = 75
            should_save = True

    enemy_robots = sense_nearby_robots(team=get_team().opponent())
    for enemy in enemy_robots:
        if can_attack(enemy.location):
            attack(enemy.location)
            break

def run_paint_pattern():
    global painting_turns, turns_without_attack, state

    # Move in a circle around the ruin every 3 turns after painting some tiles
    if painting_turns % 3 == 0:
        to_ruin = get_location().direction_to(painting_ruin_loc)
        tangent = to_ruin.rotate_right().rotate_right()
        distance = get_location().distance_squared_to(painting_ruin_loc)

        if distance > 4:
            tangent = tangent.rotate_left()

        if can_move(tangent):
            move(tangent)

    # Use helper functions to determine primary/secondary and paint a tile if possible
    if is_action_ready():
        infos = sense_nearby_map_infos(radius_squared=3)
        attacked = False
        for info in infos:
            loc = info.get_map_location()
            is_secondary = get_is_secondary(painting_ruin_loc, loc, painting_tower_type)
            if (can_attack(loc) and
                (info.get_paint() == PaintType.EMPTY or info.get_paint().is_secondary() != is_secondary) and
                is_within_pattern(painting_ruin_loc, loc)):

                attack(loc, is_secondary)
                attacked = True
                turns_without_attack = 0
                break

        if not attacked:
            turns_without_attack += 1

    # Check if we can build the tower or if the pattern appears to be done
    if can_complete_tower_pattern(painting_tower_type, painting_ruin_loc):
        complete_tower_pattern(painting_tower_type, painting_ruin_loc)
        state = RobotState.EXPLORING
    elif turns_without_attack > 3:
        state = RobotState.EXPLORING

def run_soldier():
    global state, painting_tower_type, painting_turns, turns_without_attack, painting_ruin_loc, target_enemy_ruin

    if state == RobotState.STARTING:
        if get_id() % 2 == 1:
            state = RobotState.ATTACKING
        else:
            state = RobotState.EXPLORING

    if state == RobotState.PAINTING_PATTERN:
        run_paint_pattern()
        painting_turns += 1

    elif state == RobotState.EXPLORING:
        nearby_tiles = sense_nearby_map_infos()
        cur_ruin = None
        cur_dist = 9999999

        for tile in nearby_tiles:
            if tile.has_ruin() and sense_robot_at_location(tile.get_map_location()) is None:
                check_dist = tile.get_map_location().distance_squared_to(get_location())
                if check_dist < cur_dist:
                    cur_dist = check_dist
                    cur_ruin = tile

        if cur_ruin is not None:
            if cur_dist > 4:
                bug0(cur_ruin.get_map_location())
            else:
                state = RobotState.PAINTING_PATTERN
                painting_tower_type = get_new_tower_type()
                turns_without_attack = 0
                painting_turns = 0
                painting_ruin_loc = cur_ruin.get_map_location()

        else:
            dir = random.choice(directions)
            next_loc = get_location().add(dir)
            if can_move(dir):
                move(dir)

            current_tile = sense_map_info(get_location())
            if not current_tile.get_paint().is_ally() and can_attack(get_location()):
                attack(get_location())

        update_friendly_towers()
        check_nearby_ruins()

    elif state == RobotState.ATTACKING:
        if target_enemy_ruin is None:
            infos = sense_nearby_ruins()
            if infos:
                ruin = infos[0]
                enemy = MapLocation(ruin.x, get_map_height() - 1 - ruin.y)
                target_enemy_ruin = enemy

        if target_enemy_ruin is not None:
            dsquared = get_location().distance_squared_to(target_enemy_ruin)

            if dsquared <= 8:
                if can_attack(target_enemy_ruin):
                    attack(target_enemy_ruin)

                away = get_location().direction_to(target_enemy_ruin).opposite()
                if can_move(away):
                    move(away)
                elif can_move(away.rotate_left()):
                    move(away.rotate_left())
                elif can_move(away.rotate_right()):
                    move(away.rotate_right())

            else:
                for d in directions:
                    new_loc = get_location().add(d)
                    if new_loc.is_within_distance_squared(target_enemy_ruin, 8):
                        if can_move(d):
                            move(d)
                            if can_attack(target_enemy_ruin):
                                attack(target_enemy_ruin)
                            break
                else:
                    bug2(target_enemy_ruin)

            set_indicator_dot(target_enemy_ruin, 0, 255, 0)
            set_indicator_string(f"Moving to enemy ruin at {target_enemy_ruin}")

    current_tile = sense_map_info(get_location())
    if not current_tile.get_paint().is_ally() and can_attack(get_location()):
        attack(get_location())


def run_mopper():
    global should_save
    if should_save and len(known_towers) > 0:
        # Move to first known tower if we are saving
        dir = get_location().direction_to(known_towers[0])
        set_indicator_string(f"Returning to {known_towers[0]}")
        if can_move(dir):
            move(dir)

    # Move and attack randomly.
    dir = directions[random.randint(0, len(directions) - 1)]
    next_loc = get_location().add(dir)
    if can_move(dir):
        move(dir)
    if can_mop_swing(dir):
        mop_swing(dir)
        log("Mop Swing! Booyah!")
    elif can_attack(next_loc):
        attack(next_loc)

    # We can also move our code into different methods or classes to better organize it!
    update_enemy_robots()

    if is_messenger:
        # Set a useful indicator at this mopper's location so we can see who is a messenger
        set_indicator_dot(get_location(), 255, 0, 0)

        update_friendly_towers()
        check_nearby_ruins()


def update_friendly_towers():
    global should_save

    # Search for all nearby robots
    ally_robots  = sense_nearby_robots(team=get_team())
    for ally in ally_robots:
        # Only consider tower type
        if not ally.get_type().is_tower_type():
            continue

        ally_loc = ally.location
        if ally_loc in known_towers:
            # Send a message to the nearby tower
            if should_save and can_send_message(ally_loc):
                send_message(ally_loc, int(MessageType.SAVE_CHIPS))
                should_save = False

            # Skip adding to the known towers array
            continue

        # Add to our known towers array
        known_towers.append(ally_loc)
        set_indicator_string(f"Found tower {ally.get_id()}")


def check_nearby_ruins():
    global should_save

    # Search for nearby ruins
    nearby_tiles = sense_nearby_map_infos()
    for tile in nearby_tiles:
        tile_loc = tile.get_map_location()

        # Skip completed ruins
        if not tile.has_ruin() or sense_robot_at_location(tile_loc) != None:
            continue

        # Heuristic to see if the ruin is trying to be built on
        mark_loc = tile_loc.add(tile_loc.direction_to(get_location()))
        mark_info = sense_map_info(mark_loc)
        if not mark_info.get_mark().is_ally():
            continue

        should_save = True

        # Return early
        return


def update_enemy_robots():
    # Sensing methods can be passed in a radius of -1 to automatically 
    # use the largest possible value.
    enemy_robots = sense_nearby_robots(team=get_team().opponent())
    if len(enemy_robots) == 0:
        return

    set_indicator_string("There are nearby enemy robots! Scary!")

    # Save an array of locations with enemy robots in them for possible future use.
    enemy_locations = [None] * len(enemy_robots)
    for i in range(len(enemy_robots)):
        enemy_locations[i] = enemy_robots[i].get_location()

    # Occasionally try to tell nearby allies how many enemy robots we see.
    ally_robots = sense_nearby_robots(team=get_team())
    if get_round_num() % 20 == 0:
        for ally in ally_robots:
            if can_send_message(ally.location):
                send_message(ally.location, len(enemy_robots))


#Bug2

def bug2(target):
    global prev_dest, line, is_tracing, obstacle_start_dist, tracing_dir

    if target.compare_to(prev_dest) != 0:
        prev_dest = target
        line = create_line(get_location(), target)

    if not is_tracing:
        dir_to_target = Direction(get_direction_to(get_location(), target))

        if can_move(dir_to_target):
            move(dir_to_target)
        else:
            is_tracing = True
            obstacle_start_dist = get_location().distance_squared_to(target)
            tracing_dir = dir_to_target
    else:
        if (get_location() in line 
                and get_location().distance_squared_to(target) < obstacle_start_dist):
            is_tracing = False
            return

        for _ in range(9):
            if can_move(tracing_dir):
                move(tracing_dir)
                tracing_dir = tracing_dir.rotate_right()
                tracing_dir = tracing_dir.rotate_right()
                break
            else:
                tracing_dir = tracing_dir.rotate_left()

def create_line(a, b):
    locs = set()

    x, y = a.x, a.y
    dx = b.x - a.x
    dy = b.y - a.y
    sx = int(sign(dx))
    sy = int(sign(dy))
    dx = abs(dx)
    dy = abs(dy)

    d = d = dx if dx > dy else dy
    r = d // 2

    if dx > dy:
        for _ in range(d):
            locs.add(MapLocation(x, y))
            x += sx
            r += dy
            if r >= dx:
                locs.add(MapLocation(x, y))
                y += sy
                r -= dx
    else:
        for _ in range(d):
            locs.add(MapLocation(x, y))
            y += sy
            r += dx
            if r >= dy:
                locs.add(MapLocation(x, y))
                x += sx
                r -= dy

    locs.add(MapLocation(x, y))
    return locs

def sign(num):
    """Return the sign of num (-1, 0, or 1)."""
    if num > 0:
        return 1
    elif num < 0:
        return -1
    return 0

def get_direction_to(a, b):
    """Return a grid direction (dx, dy) from a to b."""
    dx = b.x - a.x
    dy = b.y - a.y
    return (sign(dx), sign(dy))
