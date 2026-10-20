/**
 * Type definitions for ClassicCraft server plugins.
 *
 * The server evaluates each `*.js` file as a script, so these are globals:
 * declare the hooks you need at the top level. `server` is installed before
 * the plugin runs.
 */

interface PluginPlayer {
    /** Player's login name. */
    readonly name: string;
    /** Server-assigned entity id. */
    readonly entityId: number;
    /** Camera position, when the player has been placed in a world. */
    readonly x?: number;
    readonly y?: number;
    readonly z?: number;
}

interface PlayerJoinEvent {
    readonly player: PluginPlayer;
}

interface PlayerDisconnectEvent {
    readonly player: PluginPlayer;
}

interface PlayerBreakBlockEvent {
    readonly player: PluginPlayer;
    /** World block coordinates (not camera coordinates). */
    readonly x: number;
    readonly y: number;
    readonly z: number;
}

interface PlayerPlaceBlockEvent extends PlayerBreakBlockEvent {
    /** Numeric block id that was placed. See {@link BlockId}. */
    readonly blockId: number;
}

interface PluginPosition {
    readonly x: number;
    readonly y: number;
    readonly z: number;
}

interface PlayerMoveEvent {
    readonly player: PluginPlayer;
    readonly from: PluginPosition;
    readonly to: PluginPosition;
}

interface ServerTickEvent {
    /** Starts at 0 and increments once per simulation tick. */
    readonly tick: number;
    /** Fixed server rate (20 today). */
    readonly ticksPerSecond: number;
}

interface PluginServer {
    /** Send a chat line to every connected player. `&` colour codes work. */
    broadcast(message: string): void;

    /** Send a private chat line. Returns false if that player is offline. */
    send(playerName: string, message: string): boolean;

    /** Names of every connected player, in no particular order. */
    players(): string[];

    /** Write to the server console with a `[plugin:<name>]` prefix. */
    log(message: string): void;
}

/** Block ids from `com.craftclassic.common.blocks.Block`. */
declare const BlockId: {
    readonly AIR: 0;
    readonly DIRT: 1;
    readonly GRASS: 2;
    readonly STONE: 3;
    readonly LAVA: 4;
    readonly WATER: 5;
    readonly BEDROCK: 6;
    readonly LOG: 7;
    readonly LEAVES: 8;
    readonly BRICK: 9;
    readonly COBBLESTONE: 10;
    readonly GLASS: 11;
    readonly TNT: 12;
    readonly SAND: 13;
};

/** The host API, also passed to `onEnable`. */
declare const server: PluginServer;

/** Called once after the file is evaluated. */
declare function onEnable(api: PluginServer): void;

/** Called when a player finishes logging in. */
declare function onPlayerJoin(event: PlayerJoinEvent): void;

/** Called when a player's connection closes. */
declare function onPlayerDisconnect(event: PlayerDisconnectEvent): void;

/**
 * Called after a client reports breaking a block. The break is
 * client-authoritative, so this is a notification, not a veto.
 */
declare function onPlayerBreakBlock(event: PlayerBreakBlockEvent): void;

/**
 * Called after a client reports placing a block. Like breaking, placement is
 * client-authoritative; `blockId` is the numeric id that was placed.
 */
declare function onPlayerPlaceBlock(event: PlayerPlaceBlockEvent): void;

/**
 * Called for every position update, before it is relayed to other players.
 * High frequency: clients send this every tick.
 */
declare function onPlayerMove(event: PlayerMoveEvent): void;

/**
 * Called once per server simulation tick (20 TPS). Use it for timers:
 * `event.tick % (event.ticksPerSecond * seconds) === 0`.
 */
declare function onServerTick(event: ServerTickEvent): void;

/**
 * Called for every chat line before built-in commands and broadcast.
 * Return false to cancel the line. Return true or nothing to let it through.
 */
declare function onChat(player: PluginPlayer, message: string): boolean | void;

/** Called once when the server shuts down. */
declare function onDisable(): void;
